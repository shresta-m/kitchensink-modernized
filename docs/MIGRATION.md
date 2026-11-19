# Migration approach: JBoss EAP kitchensink to Spring Boot + MongoDB

## 1. Start from behaviour, not code
Legacy app: JSF page, `Member` JPA entity (name, email, phone), CDI/EJB registration, JAX-RS `/rest/members`, H2 datasource, Arquillian tests. Behaviour to keep: register a member, list members, validation rules (name without digits, valid email, 10 to 12 digit phone), unique email.

## 2. Steps taken
1. **Characterise.** Write down the rules the old app enforces (Bean Validation annotations, unique email constraint, REST contract).
2. **Choose the target shape.** Spring Boot 4.1 and Java 21, MongoDB, React SPA, JWT security. Package-by-feature with controller, dto, model, service, repository layers.
3. **Map the model.** JPA entity becomes a `Member` document. Relational constraints become: a unique index on `email`, a `$jsonSchema` validator, and Bean Validation on DTOs. Email is stored lower-case; `nameLower` supports anchored prefix search.
4. **Port behaviour behind interfaces.** EJB/CDI services become `AuthService` / `MemberService` with `impl` classes. JAX-RS becomes versioned `/api/v1` controllers with paging, sorting and typed errors (`ErrorCode`).
5. **Replace the UI.** JSF becomes a React app that talks only to the API.
6. **Add what the legacy app lacked.** Authentication, roles, admin approval, throttling, session revocation, optimistic locking, CSV export (admin only), health and tracing, API logging.
7. **Prove parity.** Unit tests per class, MockMvc controller tests, Testcontainers integration tests on real MongoDB, JaCoCo 100% line and branch gate in `mvn verify`.

## 3. Mapping
See the "Legacy to new" table in the README.

## 4. Data migration (if real data existed)
- Export members from the SQL database as JSON, lower-case emails, drop duplicates (first wins, report the rest).
- Import with `mongoimport` into `members`, set `passwordHash` only for accounts that get credentials; others have no login until an admin issues a temporary password.
- Imported documents have no `pendingApproval` field and therefore count as approved (the flag is stored inverted for this reason).
- Run `scripts/mongo/setup.js` after import to enforce validators and indexes; reconcile row counts and a sample of records.
- Roll back by keeping the SQL database read-only until sign-off.

## 5. Decisions and trade-offs
| Decision | Why | Cost |
|---|---|---|
| Self-issued JWT, role read live from Mongo | Immediate demotion and revocation | One indexed read per request |
| Admin approval for self-registration | Open registration is unsafe in production | An admin must act on each sign-up |
| API log to stdout, shipped to Elasticsearch | Standard; no audit collection to secure | Needs a log pipeline in production |
| In-memory throttle | Single instance, no extra infrastructure | Per instance; Redis or gateway for scale-out |
| Package-by-feature | A feature can become its own service | Slightly more packages |

## 6. Risks seen and handled
Validation lived in the DB schema and UI only (now enforced in DTOs and Mongo); no authentication on the REST endpoint (now JWT); lost updates (now `@Version`); injection through search (typed, escaped prefix queries).

## 7. Next
OIDC with MFA, refresh tokens, Redis-backed throttle, email verification, gateway rate limits.
