# Kitchensink backend (Spring Boot 4 + MongoDB)

Member management API: register, list/search/sort/paginate, edit, delete, bulk delete, Excel bulk import,
CSV export. JWT auth with `ADMIN` and `VIEWER` roles. Java 21.

## Run it

Prerequisites: JDK 21, Maven 3.9+, and a MongoDB 6+ you can reach (local `mongod` or an Atlas cluster).

```bash
export MONGODB_URI='mongodb://localhost:27017/kitchensink'   # default if unset
export JWT_SECRET="$(openssl rand -base64 48)"
export BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD='choose-12+-chars'
mvn spring-boot:run -Dspring-boot.run.profiles=dev      # dev: public Swagger UI at /swagger-ui.html
```

API on http://localhost:8080. `.env.example` lists every variable.

First login:

```bash
curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<your bootstrap password>"}'
```

There are no built-in accounts. Users exist only if you set the `BOOTSTRAP_*` variables.

## Test

```bash
mvn test       # unit + architecture tests (no Docker)
mvn verify     # + integration tests against a real MongoDB via Testcontainers (skipped if Docker is not running) + JaCoCo report
```

## Quality: coverage and Sonar

```bash
mvn verify                                   # tests, JaCoCo report at target/site/jacoco/index.html, 95% line gate
mvn verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>   # token from the Sonar UI, never committed
```

Unit and integration coverage are merged into one JaCoCo report that Sonar reads (see the `sonar.*` properties in `pom.xml`).
Code that needs a live MongoDB or the full security chain (`SecurityConfig.filterChain`, real queries) is covered by
`MemberControllerIT`, which needs Docker.

## Endpoints

| Method + path | Role |
|---|---|
| `POST /api/v1/auth/login`, `POST /api/v1/auth/register` | public (rate limited) |
| `POST /api/v1/auth/register-admin` (username, password, adminCode) | public, needs the admin sign-up code; 404 if none is configured |
| `GET /api/v1/auth/me` | any signed-in user |
| `POST /api/v1/admin/users` (username, password, role), `GET /api/v1/admin/users` | ADMIN |
| `GET /api/v1/members?q=&page=&size=&sort=` , `GET /api/v1/members/{id}` | ADMIN, VIEWER |
| `GET /api/v1/members/export?q=&sort=` (CSV) | ADMIN, VIEWER |
| `POST /api/v1/members`, `PUT /api/v1/members/{id}`, `DELETE /api/v1/members/{id}`, `POST /api/v1/members/bulk-delete` | ADMIN |
| `POST /api/v1/members/import` (multipart `file`), `GET /api/v1/members/import/template` | ADMIN |
| `GET /actuator/health[/liveness\|/readiness]` | public (status only) |
| `GET /actuator/metrics`, `/actuator/prometheus` | ADMIN |
| `GET /v3/api-docs`, `/swagger-ui.html` | ADMIN (public with `dev` profile) |

Errors are RFC 9457 `application/problem+json` with `code`, `traceId` and `errors[]`. The `traceId` equals the
`X-Request-Id` response header and appears in every log line for that request.

## Architecture

Packages by function under `com.kitchensink`:

```
controller                 *Controller implementations (HTTP translation only)
controller.contract        *ControllerInterface (mappings, OpenAPI, @Valid) - one per controller
service                    use-case interfaces: MemberService, MemberImportService, MemberExportService,
                           AuthService, UserService
service.port               what services depend on: MemberStore, UserStore, AdminSignupGate, MemberFileReader,
                           RowValidator, ImportBatchWriter, ImportTemplateProvider, MemberRowFormatter, metrics ports
service.impl               use-case implementations
service.imports            spreadsheet import: ExcelMemberReader, ExcelTemplateWriter, ImportRowValidator,
                           InFileDuplicateTracker, ParallelImportBatchWriter (batching + concurrency)
service.export             MemberCsvWriter
repository                 MemberRepository (Spring Data) + MemberRepositoryCustom/Impl, UserRepository
repository.adapter         MongoMemberStore, MongoUserStore (implement the ports, translate Mongo exceptions)
security                   SecurityConfig, JwtConfig, CorsConfig, JwtTokenService, BootstrapUserSeeder,
                           ProblemResponseWriter, AuthRateLimitFilter, FixedWindowRateLimiter
observability              CorrelationIdFilter, AccessLogFilter, MdcTaskDecorator, MicrometerMemberMetrics
config                     AppProperties, AppConfig (clock, import executor, OpenAPI)
model                      Member, User, Role (@Document entities)
dto.{auth,user,member,common,imports}   request/response records and value objects, grouped by feature
validation                 @MemberName, @MemberEmail, @MemberPhone, MemberRules, UserRules, XlsxUploadGuard
exception                  ErrorCode, FieldViolation
exception.domain           DomainException and its subclasses
exception.handler          GlobalExceptionHandler, ApiProblem
```

`ArchitectureTest` enforces the direction: controller -> service (ports) <- repository adapters, nothing calls upward, and the
model depends on nothing above it.

- **SRP:** controllers translate HTTP; services hold use cases; repositories hold queries.
- **DIP / OCP:** controllers depend on service interfaces, and services depend on ports they own (file reader,
  row validator, batch writer, metrics), so a CSV reader or another metrics backend is a new class, not an edit.
- **Import SRP:** `MemberImportServiceImpl` only orchestrates (read, validate, de-duplicate, report);
  `ParallelImportBatchWriter` owns batching and concurrency.
- **DRY validation:** each field rule is a composed annotation (`@MemberName`, `@MemberEmail`, `@MemberPhone`)
  declared once and reused by the REST requests and by every imported spreadsheet row.
- **Experience layer:** the `controller` package is the experience layer. It is a role, not a separate
  deployable; it becomes a BFF only if a second client or domain appears.

## Bulk import design

1. Read rows (max 5,000, `.xlsx` only, 5 MB; extension and ZIP magic bytes checked).
2. Validate every row with the same constraints as the API; drop in-file duplicates (first occurrence wins).
   Sequential: cheap and order-dependent, so it stays deterministic.
3. Split survivors into batches of 500 and write them **in parallel** on a bounded pool (4 workers, bounded
   queue, caller-runs back-pressure). Parallelism goes where the time is: MongoDB I/O.
4. Each batch is an unordered `bulkWrite`; the unique email index is the final arbiter, so races with
   concurrent creates become reported duplicates, not failures.
5. A batch that fails for infrastructure reasons is isolated: its rows are reported as `PROCESSING_ERROR`,
   other batches are unaffected.

Tune with `app.bulk-import.*`. The request-id is propagated into worker threads for log correlation.

## Observability

Structured per-request access log (method, path, status, duration, user; no bodies or query strings), `prod`
profile switches to ECS JSON. Metrics: Spring defaults plus `members.operations{operation}`,
`members.import.rows{outcome}`, `members.import.duration`, `members.import.batch.duration`,
`members.import.pool.active|size`, scraped at `/actuator/prometheus`.

## Security notes

Stateless JWT (HS256, 30 min), BCrypt(12), role-based URL rules with default deny, CORS limited to
`CORS_ALLOWED_ORIGINS`, unknown JSON fields rejected, sort fields whitelisted, search input regex-quoted,
CSV export neutralizes spreadsheet formulas, generic login failure with timing equalization, no secrets in
source (JWT secret and passwords come from the environment; the app refuses to start without a 32+ char secret).
Sign-up (`/auth/register`) has no role field and always creates a `VIEWER`; unknown fields are rejected with 400.
An `ADMIN` is created either by an existing admin through `/admin/users`, or through `/auth/register-admin`, which
requires `app.security.admin-signup-code` (env `ADMIN_SIGNUP_CODE`, 16+ characters). It is blank by default, which
disables the endpoint (404). The code is compared in constant time and checked before the username is looked at;
it is rate limited like login and sign-up and does not sign the new admin in.
Only those two routes can create an `ADMIN`. Login and sign-up are limited to `app.security.auth-requests-per-minute`
(default 10) per client address, in memory per instance; behind a proxy set `server.forward-headers-strategy`.
Sign-up answers 409 for a taken username, so it reveals which usernames exist.

Not included: refresh tokens, token revocation, account disable or password change.

## Error handling

Every failure is an `application/problem+json` body (RFC 9457 shape) with a stable `code`, a `traceId`
(the request id, also in the logs) and, for field problems, `errors[{field,message}]`.

- **Domain exceptions**: `DomainException` is the root; each carries its `ErrorCode` and a client-safe message
  (`NotFoundException`, `ConflictException`, plus the concrete ones). One handler maps the whole hierarchy.
- **No leaks**: services depend on the `MemberStore`/`UserStore` ports. The Mongo adapters (`MongoMemberStore`,
  `MongoUserStore`) are the only code that sees `DuplicateKeyException` / `OptimisticLockingFailureException`
  and translate them into domain exceptions. ArchUnit fails the build if a service imports Spring Data, the
  driver or the repository package.
- **Framework errors** (404, 405, 415, 413, malformed JSON, unknown fields) keep their status and headers but
  use our body. Anything unexpected is logged with its stack trace and answered with a generic 500.
- Filter-level 401/403/429 are written in the same format by `ProblemResponseWriter` and `AuthRateLimitFilter`.

## Version note

Spring Boot **4.1.2** (Spring Framework 7, Spring Security 7, Spring Data MongoDB 5, Jackson 3, Java 21).
This tree was upgraded from 3.5 without access to Maven Central, so run `mvn verify` first and fix anything the
compiler reports. Likely spots: starter names in `pom.xml`, the `spring.mongodb.uri` property,
`EndpointRequest` and `@AutoConfigureMockMvc` package names, Testcontainers 2 artifact/class names.
See `../MIGRATION.md` for the approach used to migrate the legacy app.
