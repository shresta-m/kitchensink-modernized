# kitchensink-modernized

The JBoss EAP `kitchensink` quickstart, migrated to Spring Boot 4.1 (Java 21), MongoDB and a React frontend, with security, validation, error handling and tests added.

`kitchensink` is the product name (repo, Java package, database, UI). `member-service` is the deployable Spring Boot service; `member-frontend` is the React app.

| Folder | What it is |
|---|---|
| `member-service/` | Spring Boot 4.1.1, Java 21, Spring Data MongoDB, JWT security |
| `member-frontend/` | React 19, Vite, Tailwind |
| `docs/` | Migration approach and GenAI stretch design |

## Prerequisites

Java 21, Maven 3.9+, Node 22+, and a MongoDB on `localhost:27017` (no authentication needed locally).

## Build and run

1. Set the environment variables (the app will not start without `JWT_SECRET`).

   macOS / Linux:
   ```
   export JWT_SECRET='<at least 32 characters>'
   export ADMIN_CODE='XXXX-XXXX-XXXX'     # optional: lets you register the first admin
   export SWAGGER_ENABLED=true            # optional: API docs for development
   ```
   Windows PowerShell:
   ```
   $env:JWT_SECRET='<at least 32 characters>'
   $env:ADMIN_CODE='XXXX-XXXX-XXXX'
   $env:SWAGGER_ENABLED='true'
   ```
2. Backend (http://localhost:8080):
   ```
   cd member-service
   mvn clean verify
   mvn spring-boot:run
   ```
3. Frontend (http://localhost:5173, proxies `/api` to port 8080):
   ```
   cd member-frontend
   npm install
   npm run dev
   ```
4. Create the first admin: open http://localhost:5173/register and enter the `ADMIN_CODE` value in the admin code field. That account is active immediately. Everyone else who registers is **pending** until an admin approves them.
5. API docs (when `SWAGGER_ENABLED=true`): http://localhost:8080/swagger-ui.html

The access token is kept in memory, so a page reload requires signing in again. Signing out, changing a password or an admin reset ends every session of that member on the server.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | none, required | HMAC signing key, at least 32 characters |
| `ADMIN_CODE` | empty | Grants ADMIN at registration. Empty disables admin self-registration |
| `SPRING_MONGODB_URI` | local database | Connection string for any other environment. Keep `w=majority` |
| `AUTO_INDEX` | `true` | Creates the `@Indexed` indexes at startup (unique email, name, phone). Set `false` only if the indexes are created separately |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | CORS allow-list, comma separated |
| `FORWARD_HEADERS` | `none` | Set to `native` behind a trusted reverse proxy |
| `SWAGGER_ENABLED` | `false` | API docs, development only |
| `LOGIN_MAX_FAILURES` / `LOGIN_WINDOW` / `LOGIN_LOCKOUT` | 5 / 15m / 15m | Per-email login lockout |
| `IP_MAX_FAILURES` | 50 | Per-address failed logins in the window |
| `TRACING_SAMPLING` | 0.0 | Trace sampling probability |

## API summary

All paths are under `/api/v1`. Errors always use one shape with a stable `code` (see the `ErrorCode` enum) and a `traceId`.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/logout`, `/auth/change-password`, `GET /auth/me` |
| Members | `GET /members` (search, filter, sort, page), `GET /members/{id}`, `POST /members`, `PUT /members/{id}`, `PUT /members/{id}/role`, `POST /members/{id}/approve`, `POST /members/{id}/reset-password`, `DELETE /members/{id}`, `POST /members/bulk-delete`, `GET /members/export` |
| Lookups | `GET /lookups/roles`, `GET /lookups/statuses` |
| Health | `GET /actuator/health` (public, no details), `/liveness`, `/readiness` |

Roles: **ADMIN** can manage everyone and export CSV. **VIEWER** can read and search only. The role is read from the database on every request, never from the token.

## Tests

```
cd member-service
mvn clean verify
```
Runs the unit and MockMvc tests and enforces a JaCoCo gate of 95% line and branch coverage. The report is in `member-service/target/site/jacoco/index.html`. The repository layer is tested with mocks; a test against a real MongoDB is listed as a next step in [docs/MIGRATION.md](docs/MIGRATION.md).

## Production notes

- Point `SPRING_MONGODB_URI` at a database user with read/write access to the `members` collection, with `w=majority` in the URI. For a hardened setup, create the indexes separately and set `AUTO_INDEX=false`.
- Behind a reverse proxy set `FORWARD_HEADERS=native`. Terminate TLS in front of the service; HSTS is sent on HTTPS requests.
- Leave `SWAGGER_ENABLED` unset. Only `/actuator/health` is exposed.
- Logs are plain text. Set `logging.structured.format.console=ecs` for JSON (ECS) logs once a collector is in place.

## Documentation

- [docs/MIGRATION.md](docs/MIGRATION.md): how the migration was approached, data migration, decisions and trade-offs
- [docs/GENAI.md](docs/GENAI.md): GenAI stretch goal (design only, not implemented)

## Package structure

Package-by-feature (`auth`, `member`, `lookup`, `security`, `common`); inside a feature the layers are `controller`, `dto`, `model`, `service`, `service/impl`, `repository`, `exception`, `validation`. Controllers implement an interface that carries the OpenAPI and validation annotations; services are interfaces with one implementation each; cross-cutting code (error handling, API logging, configuration) lives in `common`. A feature can be lifted into its own service by moving its package.

## Legacy to new

| JBoss kitchensink | This project |
|---|---|
| `Member` JPA entity (`@Entity`, Bean Validation) | `Member` document and request DTOs with Bean Validation; unique email index and search indexes declared on the entity with `@Indexed` |
| `MemberRepository` (EntityManager) | Spring Data MongoDB repository and `MemberQueryBuilder` |
| `MemberRegistration` EJB / CDI | `AuthService` and `MemberService` (with `impl` classes) |
| JAX-RS `MemberResourceRESTService` | `MemberController` under `/api/v1`: versioned, paged, role-protected |
| JSF/Faces UI | React single-page app |
| `Resources` producers, `persistence.xml`, datasource XML | Spring Boot auto-configuration and `application.properties` |
| Arquillian tests | JUnit 5, Mockito, MockMvc, JaCoCo gate |
| No authentication | JWT, roles, admin approval, throttling, session revocation |

## Health, logging and tracing

- Tracing uses the Micrometer Tracing API with the OpenTelemetry bridge and W3C `traceparent` propagation. The browser starts the trace (`member-frontend/src/api/client.ts`); the backend continues it with one span per HTTP request and one per MongoDB command.
- The trace id is returned in the `X-Trace-Id` response header, in the `traceId` field of error bodies, and in every log line as `[member-service,<traceId>,<spanId>]`.
- No OTLP exporter is on the classpath, so nothing is exported. To view traces, add an exporter dependency, point it at a collector and raise `TRACING_SAMPLING`.
- `ApiLoggingAspect` (Spring AOP) writes one line around every controller call: `API PUT /api/v1/members/42/role actor=<id> ip=<addr> outcome=OK|FAILED|ERROR error=<code> ms=<n>`. No bodies, parameters, query strings or tokens are logged. Requests rejected by the security filter (401 / 403) are logged in the same shape.
- To follow a failed request, take the `traceId` from the error response and search the logs for it.
