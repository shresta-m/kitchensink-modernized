# kitchensink-modernized

- `backend/`  Spring Boot 4.1.1, Java 21, MongoDB, JWT security. NOT yet compiled in the authoring sandbox: run `mvn clean verify` first.
- `frontend/` React 19 + Vite + Tailwind.

## Run
```
export JWT_SECRET='<at least 32 chars>'   # required
export ADMIN_CODE='XXXX-XXXX-XXXX'        # optional, enables admin self-registration
cd backend && mvn clean verify && mvn spring-boot:run     # needs MongoDB on localhost:27017
cd frontend && npm install && npm run dev                 # http://localhost:5173 (proxies /api to :8080)
```
Token is kept in memory: a page reload requires signing in again. Signing out, changing a password or an admin reset ends every session of that member on the server.

## Security and database

- Migration approach: [docs/MIGRATION.md](docs/MIGRATION.md). GenAI stretch design: [docs/GENAI.md](docs/GENAI.md).
- Design, threat-to-test table, limits and a "verify it yourself" playbook: [docs/SECURITY.md](docs/SECURITY.md).
- Production database: run `scripts/mongo/setup.js` as an administrator (schema validators, indexes, least-privilege user), then start the app with `AUTO_INDEX=false`.
- Behind a reverse proxy set `FORWARD_HEADERS=native`. API docs are off unless `SWAGGER_ENABLED=true`.
- Viewer accounts can read and search; CSV export is ADMIN only.
- Registration: self-registered members are **pending** until an admin approves them (`POST /api/v1/members/{id}/approve`; reject = delete). Pending members get 403 `ACCOUNT_PENDING` at login (after a correct password). Registering with `ADMIN_CODE` is active immediately, which is how the first admin is created.

## Package structure

Package-by-feature (`auth`, `member`, `lookup`, `security`, `common`); inside a feature the layers are `controller`, `dto`, `model`, `service`, `service/impl`, `repository`, `exception`, `validation`. Controllers implement an interface that carries the OpenAPI and validation annotations; services are interfaces with one implementation each; cross-cutting code (error handling, API logging, config) lives in `common`. A feature can be lifted into its own service by moving its package.

## Legacy to new

| JBoss kitchensink | This project |
|---|---|
| `Member` JPA entity (`@Entity`, Bean Validation) | `Member` document + request DTOs with Bean Validation; indexes and `$jsonSchema` in `scripts/mongo/setup.js` |
| `MemberRepository` (EntityManager) | Spring Data MongoDB repository + `MemberQueryBuilder` |
| `MemberRegistration` EJB / CDI | `AuthService` / `MemberService` (+ `impl`) |
| JAX-RS `MemberResourceRESTService` | `MemberController` under `/api/v1`, versioned, paged, role-protected |
| JSF/Faces UI | React SPA |
| `Resources` producers, `persistence.xml`, datasource XML | Spring Boot auto-configuration, `application.properties` |
| Arquillian tests | JUnit 5 + Mockito, MockMvc, Testcontainers IT, JaCoCo 100% gate |
| No authentication | JWT, roles, admin approval, throttling, session revocation |

## Health and distributed tracing

- `GET /actuator/health` (public, no details), `/actuator/health/liveness`, `/actuator/health/readiness` (includes MongoDB). Nothing else under `/actuator` is exposed.
- Tracing uses Micrometer Tracing with the OpenTelemetry bridge (`spring-boot-starter-opentelemetry`), W3C `traceparent` propagation.
- The browser starts the trace (`traceparent` header added in `frontend/src/api/client.ts`). The backend continues it with one span per HTTP request and one per MongoDB command, so a single trace shows browser call → API → database. A request without the header starts a new trace.
- The trace id is returned in the `X-Trace-Id` response header, in the `traceId` field of error bodies, and in every log line as `[kitchensink,<traceId>,<spanId>]`, plus one access-log line per request.
- Span export is off by default (`TRACING_SAMPLING=0.0`), so no collector is needed. The trace id still shows in logs, the `X-Trace-Id` header and error bodies. To export spans, start the app with `TRACING_SAMPLING=1.0` and point the standard Boot property `management.opentelemetry.tracing.export.otlp.endpoint` (env `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`) at an OTLP/HTTP collector. Lower the sampling in production.
- API log: `ApiLoggingAspect` (Spring AOP) writes one line around every controller method: `API PUT /api/v1/members/42/role actor=<id> ip=<addr> outcome=OK|FAILED|ERROR error=<code> ms=<n>`. No bodies, parameters, query strings or tokens; the trace id is added by the log pattern. Nothing is stored in MongoDB: ship the logs to Elasticsearch/Kibana. In production run with `--spring.profiles.active=prod` for JSON (ECS) logs and graceful shutdown.
- To follow a failed request: take the `traceId` from the error response and search the logs or the trace UI for it.
