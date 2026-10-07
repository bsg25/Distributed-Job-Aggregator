package com.example.processing;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.util.ArrayList;
// import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.example.model.JobBatch;
import com.example.model.JobFound;

@Component
public class JobProcessing {

    private static final Logger log = LoggerFactory.getLogger(JobProcessing.class);
    private static final int CHUNK_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate jsonRabbitTemplate;

    public JobProcessing(JdbcTemplate jdbctemplate, RabbitTemplate jsonRabbitTemplate) {
        this.jdbcTemplate = jdbctemplate;
        this.jsonRabbitTemplate = jsonRabbitTemplate;
    }

    @RabbitListener(queues = "job.queue")
    public void processJob(JobBatch batch) {

        log.info("Batch received: {} job(s), {} board failure(s), polled at {}", batch.foundJobs().size(), batch.failures().size(), batch.polledAt());

        for (String f : batch.failures()) {
            log.warn("Board failed during poll: {}", f);
        }

        List<JobFound> freshJobs = insertAll(batch.foundJobs());

        if (freshJobs.isEmpty()) {
            log.info("No new jobs found this cycle...");
        } else {
            log.info("{} new job(s)", freshJobs.size());

            for (JobFound job : freshJobs) {
                jsonRabbitTemplate.convertAndSend("new-jobs", job);
            }
        }
    }

    // Two statements per chunk instead of one or two round trips per job:
    // bump lastSeen for everything already stored, then insert the rest.
    private List<JobFound> insertAll(List<JobFound> newJobs) {

        // A board can list the same posting twice; keep the first occurrence.
        Map<String, JobFound> unique = new LinkedHashMap<>();
        for (JobFound j : newJobs) {
            unique.putIfAbsent(j.jobId(), j);
        }

        List<JobFound> jobs = new ArrayList<>(unique.values());
        List<JobFound> freshJobs = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i += CHUNK_SIZE) {
            freshJobs.addAll(insertChunk(jobs.subList(i, Math.min(i + CHUNK_SIZE, jobs.size()))));
        }
        return freshJobs;
    }

    private List<JobFound> insertChunk(List<JobFound> chunk) {

        Set<String> seen = new HashSet<>(jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(
                "UPDATE dist_jobs_scheduler.found_jobs SET lastSeen = now() WHERE jobId = ANY(?::TEXT[]) RETURNING jobId");
            ps.setArray(1, con.createArrayOf("text", column(chunk, JobFound::jobId)));
            return ps;
        }, (rs, n) -> rs.getString(1)));

        List<JobFound> unseen = chunk.stream().filter(j -> !seen.contains(j.jobId())).toList();
        if (unseen.isEmpty()) {
            return unseen;
        }

        // DO NOTHING + RETURNING: if another worker inserted the same job in the
        // meantime, only the one that actually inserted it reports it as fresh.
        Set<String> inserted = new HashSet<>(jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO dist_jobs_scheduler.found_jobs
                (company, ats, jobId, title, location, department, url, posted, firstSeen, lastSeen)
                SELECT company, ats, jobId, title, location, department, url, posted, now(), now()
                FROM unnest(?::TEXT[], ?::TEXT[], ?::TEXT[], ?::TEXT[], ?::TEXT[], ?::TEXT[], ?::TEXT[], ?::TEXT[])
                    AS j(company, ats, jobId, title, location, department, url, posted)
                ON CONFLICT (jobId) DO NOTHING
                RETURNING jobId""");
            List<Function<JobFound, String>> columns = List.of(
                JobFound::company, JobFound::ats, JobFound::jobId, JobFound::title,
                JobFound::location, JobFound::department, JobFound::url, JobFound::posted);
            for (int c = 0; c < columns.size(); c++) {
                ps.setArray(c + 1, con.createArrayOf("text", column(unseen, columns.get(c))));
            }
            return ps;
        }, (rs, n) -> rs.getString(1)));

        return unseen.stream().filter(j -> inserted.contains(j.jobId())).toList();
    }

    private static String[] column(List<JobFound> jobs, Function<JobFound, String> field) {
        return jobs.stream().map(field).toArray(String[]::new);
    }
}
