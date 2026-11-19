# kitchensink-modernized

- `member-service/`  Spring Boot 4.1.1, Java 21, MongoDB, JWT security (migrated from the JBoss EAP kitchensink quickstart).
- `frontend/` React 19 + Vite + Tailwind.

## Run
```
export JWT_SECRET='<at least 32 chars>'   # required, no default
export ADMIN_CODE='XXXX-XXXX-XXXX'        # required to create the first admin; leave unset to disable admin self-registration
export SWAGGER_ENABLED=true               # optional, API docs for development
cd member-service && mvn clean verify && mvn spring-boot:run     # needs MongoDB on localhost:27017 (Java 21, Maven 3.9+)
cd frontend && npm install && npm run dev                 # http://localhost:5173 (proxies /api to :8080)
```
Token is kept in memory: a page reload requires signing in again. Signing out, changing a password or an admin reset ends every session of that member on the server.

## Security and database

- Migration approach: [docs/MIGRATION.md](docs/MIGRATION.md). GenAI stretch design: [docs/GENAI.md](docs/GENAI.md).
- Design, threat-to-test table, limits and a "verify it yourself" playbook: [docs/SECURITY.md](docs/SECURITY.md).
- Production database: run `scripts/mongo/setup.js` as an administrator (schema validators, indexes, least-privilege user), then start the app with `AUTO_INDEX=false`.
- Behind a reverse proxy set `FORWARD_HEADERS=native`. Set `SPRING_MONGODB_URI` for a non-local database (keep `w=majority`). API docs are off unless `SWAGGER_ENABLED=true`.
- Viewer accounts can read and search; CSV export is ADMIN only.
- Registration: self-registered members are **pending** until an admin approves them (`POST /api/v1/members/{id}/approve`; reject = delete). Pending members get 403 `ACCOUNT_PENDING` at login (after a correct password). Registering with `ADMIN_CODE` is active immediately, which is how the first admin is created.

## Tests

`cd member-service && mvn clean verify` runs 279 tests and enforces the JaCoCo gate (95% line and branch). The tests mock the database; there is no live-MongoDB integration test yet (listed as the next step in [docs/MIGRATION.md](docs/MIGRATION.md)).

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
| Arquillian tests | JUnit 5 + Mockito, MockMvc, 279 tests, JaCoCo 95% line and branch gate |
| No authentication | JWT, roles, admin approval, throttling, session revocation |

## Health and distributed tracing

- `GET /actuator/health` (public, no details), `/actuator/health/liveness`, `/actuator/health/readiness` (includes MongoDB). Nothing else under `/actuator` is exposed.
- Tracing uses the Micrometer Tracing API with the OpenTelemetry bridge (`spring-boot-micrometer-tracing-opentelemetry` + `micrometer-tracing-bridge-otel`), W3C `traceparent` propagation.
- The browser starts the trace (`traceparent` header added in `frontend/src/api/client.ts`). The backend continues it with one span per HTTP request and one per MongoDB command, so a single trace shows browser call → API → database. A request without the header starts a new trace.
- The trace id is returned in the `X-Trace-Id` response header, in the `traceId` field of error bodies, and in every log line as `[member-service,<traceId>,<spanId>]`, plus one access-log line per request.
- No OTLP exporter is on the classpath, so nothing is exported and nothing tries `localhost:4318`. The trace id still shows in logs, the `X-Trace-Id` header and error bodies. To view traces, add an OTLP exporter dependency and point it at a collector, and raise `TRACING_SAMPLING` above 0.
- API log: `ApiLoggingAspect` (Spring AOP) writes one line around every controller method: `API PUT /api/v1/members/42/role actor=<id> ip=<addr> outcome=OK|FAILED|ERROR error=<code> ms=<n>`. No bodies, parameters, query strings or tokens; the trace id is added by the log pattern. Nothing is stored in MongoDB: ship the logs to Elasticsearch/Kibana. Logs are plain text by default; set `logging.structured.format.console=ecs` for JSON (ECS) once a collector exists.
- To follow a failed request: take the `traceId` from the error response and search the logs or the trace UI for it.
