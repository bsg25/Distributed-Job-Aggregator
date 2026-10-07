CREATE SCHEMA IF NOT EXISTS dist_jobs_scheduler;

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.users(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.found_jobs(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company VARCHAR(255),
    ats VARCHAR(32),
    jobId VARCHAR(255) UNIQUE,
    title TEXT,
    location TEXT,
    department TEXT,
    url TEXT,
    posted VARCHAR(64),
    firstSeen TIMESTAMPTZ NOT NULL DEFAULT now(),
    lastSeen TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.watched_companies(
    user_id BIGINT NOT NULL REFERENCES dist_jobs_scheduler.users(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (company_name, user_id)
);

CREATE INDEX IF NOT EXISTS watched_companies_user_id_idx
    ON dist_jobs_scheduler.watched_companies (user_id);
