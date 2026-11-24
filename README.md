# ClaimsHub

A small insurance back office made of **two cooperating Spring Boot services**: one manages policies, the other manages claims filed against them. It is a practice project that puts a typical enterprise Java stack to work end to end: REST APIs, JWT security, SQL, reactive programming, testing, containers and CI.

> This project was built with the help of Claude, an AI assistant from Anthropic, as a learning exercise. I reviewed, ran and tested the result.

## Architecture

```
            ┌──────────────────────────┐        ┌───────────────────────────┐
  client ──►│  policy-service  :8080   │◄───────│   claims-service  :8081   │◄── client
            │  Spring MVC + JDBC       │  REST  │   Spring WebFlux          │
            │  H2 (Oracle mode)        │  + JWT │   in-memory adapters      │
            │  issues the JWT          │        │   (ports & adapters)      │
            └──────────────────────────┘        └───────────────────────────┘
```

| | policy-service | claims-service |
|---|---|---|
| Style | Spring MVC (blocking) | Spring WebFlux (reactive) |
| Build | Maven | Gradle |
| Data | H2 in Oracle mode, `NamedParameterJdbcTemplate`, sequence + `DUAL`, `OFFSET/FETCH` | in-memory repositories behind ports |
| Role | owns policies, issues JWTs, SQL reports | owns claims, calls policy-service to validate policies |

**Stack:** Java 21, Spring Boot 4.1, Spring Security (resource server), springdoc OpenAPI, JUnit 5, Mockito, MockMvc, WebTestClient, Reactor Test, MockWebServer, JaCoCo, Docker, Jenkins.

## Quick start

Requirements: JDK 21. Both services must share the same signing secret (at least 32 characters).

```bash
export JWT_SECRET="change-me-to-a-long-random-string-123456"

# terminal 1
cd policy-service && mvn spring-boot:run

# terminal 2
cd claims-service && ./gradlew bootRun
```

With Docker:

```bash
echo "JWT_SECRET=change-me-to-a-long-random-string-123456" > .env
docker compose up --build
```

Try it (demo users: `agent` / `agent123` with role `AGENT`, `admin` / `admin123` with role `ADMIN`):

```bash
# 1. log in: the response contains the JWT
curl -X POST http://localhost:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"agent","password":"agent123"}'

# 2. call the API with the token (the X-Client-App header is mandatory on /api/**)
curl http://localhost:8080/api/policies \
     -H "Authorization: Bearer <token>" -H "X-Client-App: demo"
```

OpenAPI documentation is served by policy-service through springdoc (`/swagger-ui.html`). Health checks are at `/actuator/health`.

## API

### policy-service (`:8080`)

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/auth/login` | public | returns a signed JWT |
| GET | `/api/policies` | AGENT, ADMIN | search with filters (`type`, `status`, `minPremium`, `maxPremium`, `holderName`, `startFrom`, `startTo` as `YYYY-MM-DD`), sorting (`sortBy`: `ID`, `PREMIUM`, `START_DATE`, `HOLDER_NAME`; `desc`) and pagination (`page`, `size`) |
| GET | `/api/policies/{id}` | AGENT, ADMIN | one policy |
| GET | `/api/policies/stats` | AGENT, ADMIN | count and total premium by type and status |
| POST | `/api/policies` | ADMIN | create a policy (status starts as `ACTIVE`) |
| PATCH | `/api/policies/{id}/cancel` | ADMIN | cancel an active policy (409 otherwise) |
| PATCH | `/api/policies/{id}/renew` | ADMIN | renew for one year (409 if cancelled or if the new end date is already past) |
| GET | `/api/reports/premiums-by-type` | ADMIN | `GROUP BY` / `HAVING`: count, sum, average, min, max per type (`status`, `minPolicies`) |
| GET | `/api/reports/premium-ranking` | ADMIN | window functions: `RANK() OVER (PARTITION BY ...)` and each policy's share of its type |
| GET | `/api/reports/monthly-new-policies` | ADMIN | new policies per month with a running total (`SUM(SUM(...)) OVER`) |

Policy lifecycle: `ACTIVE` → `CANCELLED` or `EXPIRED`; a cancelled policy cannot be renewed.

### claims-service (`:8081`)

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/claims` | AGENT, ADMIN | open a claim (the policy must exist and be active in policy-service) |
| GET | `/api/claims` | AGENT, ADMIN | search with filters (`status`, `policyId`, `minAmount`, `maxAmount`, `createdFrom`, `createdTo`), sorting (`sortBy`: `CREATED_AT`, `AMOUNT`; `desc`) and pagination (`page`, `size`, max 100) |
| GET | `/api/claims/{id}` | AGENT, ADMIN | one claim |
| PATCH | `/api/claims/{id}/status` | ADMIN | change status (409 if the transition is not allowed) |
| GET | `/api/claims/{id}/history` | AGENT, ADMIN | audit trail of the claim |
| GET | `/api/claims/policy/{policyId}/summary` | AGENT, ADMIN | the policy (from policy-service) plus claim statistics, fetched in parallel with `Mono.zip` |
| POST / GET | `/api/claims/{id}/documents`, `/{docId}` | AGENT, ADMIN | upload (type and size checks), list and download documents |
| GET | `/api/claims/whoami` | AGENT, ADMIN | the authenticated user and roles |

Claim lifecycle, enforced by a state machine inside the `ClaimStatus` enum:

```
OPEN ──► IN_REVIEW ──► APPROVED   (final)
                  └──► REJECTED   (final)
```

Errors use one consistent JSON body (`timestamp`, `status`, `error`, `message`) and a deliberate set of codes: 400 malformed request, 401 not authenticated, 403 not allowed, 404 not found, 409 conflict with the current state, 413 payload too large, 502 policy-service unavailable.

## Security

- **JWT (HS256)** issued by policy-service and validated by both services as OAuth2 resource servers; the secret comes from the `JWT_SECRET` environment variable.
- **Roles** `AGENT` (read, open claims, upload documents) and `ADMIN` (write operations and reports).
- **Deny by default:** any path not explicitly allowed answers 403.
- **JSON 401 and 403** responses instead of empty bodies.
- **`X-Client-App`** is required on `/api/**` (400 otherwise).
- **`X-Request-Id`** is generated or accepted (after a format check, to prevent log injection), put in the log context, returned in the response and propagated from claims-service to policy-service together with the bearer token.
- **SQL injection:** values always travel as named parameters; the only dynamic SQL fragments are constants chosen in code (sort columns come from an enum).
- **Uploads:** type and size are validated and downloads are served with `X-Content-Type-Options: nosniff` and `Content-Disposition: attachment`.

## Design notes

- **Ports and adapters in claims-service.** Repository, history, notifications and document storage are interfaces; the running adapters are in memory. AWS adapters (S3, DynamoDB, SQS) exist for a separate `aws` profile, so the business code never knows which one it uses.
- **Lazy reactive adapters.** Blocking work is wrapped (`Mono.fromSupplier`, `Flux.defer`) so nothing runs until someone subscribes.
- **Read/write split.** `ClaimQueryService` has no side effects; `ClaimService` writes, records history and notifies.
- **Calling policy-service.** `WebClient` with a timeout; HTTP errors are translated into domain exceptions (404 for an unknown policy, 502 when the service is down).
- **Optimistic concurrency.** Policy transitions use `UPDATE ... WHERE status = :expected`, so a concurrent change fails instead of being overwritten.
- **Injected `Clock`.** Business rules that depend on today's date receive a `Clock`, so tests use a fixed instant and never depend on the calendar.
- **Enums instead of free strings** for sorting and status, so invalid values are rejected before reaching the business code.
- **No shared library** between the two services: they only share the contract (header names, JWT format), so they can be released independently.

## Testing

```bash
cd policy-service && mvn test
cd claims-service && ./gradlew test
```

- Unit tests with JUnit 5 and Mockito; reactive code with `StepVerifier`.
- Repository tests on H2 using the real SQL, including the reports.
- API tests with MockMvc (policy-service) and WebTestClient (claims-service), covering 401, 403, validation, state conflicts and the happy paths.
- `MockWebServer` for the HTTP client that calls policy-service (timeouts, 404, 5xx).
- JaCoCo coverage reports for both services.

## Containers and CI

- Multi-stage Dockerfiles using Spring Boot layered jars.
- `docker-compose.yml` runs both services; the secret comes from `.env`.
- A `Jenkinsfile` builds and tests both services in parallel, publishes the test and coverage reports and builds the Docker images. An OWASP Dependency-Check stage is available as a switchable option.

## Known limitations

- **AWS adapters** are implemented and tested with mocks, but have **not been verified against an emulator or real AWS**; by default the service runs on the in-memory adapters.
- Claims and their history live in memory and are lost on restart. The policy summary counts in memory; with a real database it would be an aggregate query.
- Users are in memory and the JWT secret is shared. In production: an identity provider, asymmetric keys and rotation.
- Not implemented: GraphQL, SOAP and SonarQube.

## Possible next steps

- Persist claims in a relational database (Flyway migrations).
- Verify the AWS profile against an emulator.
- Add SonarQube analysis to the pipeline.
- Idempotency keys on claim creation; a server-sent-events stream of claim changes.