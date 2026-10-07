# Distributed Job Aggregator

A distributed job-feed aggregator that polls **111 US company job boards** across 5 applicant-tracking systems (Greenhouse, Ashby, Lever, SmartRecruiters, Workday) plus Amazon's and Netflix's self-hosted boards, surfacing fresh software-engineering openings on a live dashboard.

Every posting is filtered down to US-located software roles posted within the last
7 days, then pushed to the browser the moment a worker stores it.

## Architecture

![Architecture diagram](docs/architecture.jpg)

## Sources

| Source | Type | Boards |
|---|---:|---:|
| Greenhouse | ATS | 64 |
| Ashby | ATS | 39 |
| Lever | ATS | 3 |
| SmartRecruiters | ATS | 2 |
| Workday | ATS | 1 |
| Amazon | self-hosted | 1 |
| Netflix | self-hosted | 1 |
| **Total** | | **111** |

Each board is polled every `POLL_MINUTES` (default 30). A full cycle takes 3–5
minutes; failures are isolated per board, so one dead slug never stops a run.

## Tech stack

| Layer | Technology |
|---|---|
| Services | Spring Boot (`backend`, `poller`, `worker`) |
| Messaging | RabbitMQ (competing consumers via Spring AMQP) |
| Database | PostgreSQL |
| Live updates | STOMP over WebSockets (per-user queues via `SimpMessagingTemplate`) |
| Frontend | React 19, Vite |


## API

| Endpoint | Purpose |
|---|---|
| `POST /api/users/signup` | Create an account (returns a JWT — user lands on the dashboard signed in) |
| `POST /api/users/login` | Authenticate, returns a JWT |
| `GET /api/dashboard` | Recent jobs for the authenticated user's subscriptions |
| `POST /api/subscribe` | Update which ATS platforms the user watches |


## Running locally

Requires Docker with Compose. Create a `.env` in the project root:

```properties
SITE_ADDRESS=localhost
APP_ALLOWED_ORIGIN=https://localhost
APP_JWT_SECRET=<any long random string — generate with: openssl rand -base64 32>
POLL_MINUTES=30
```

Then bring up the whole stack:

```bash
docker compose up -d --build
```

## Next Steps

- Add more ATS sources 
- Migrate application to AWS