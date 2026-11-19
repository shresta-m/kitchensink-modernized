# Security design

Scope: what this system defends against, how, where the code is, which test proves it, and what is deliberately left out.

## 1. Model

| Item | Decision |
|---|---|
| Identity | Self-issued HS256 JWT, 30 min, claims `sub` (member id), `iss`, `iat`, `exp`, `ver` (session generation). |
| Authorization | Role is read from MongoDB on **every request**, never from the token. Demotion and deletion apply at once. |
| Session end | `POST /auth/logout`, any password change, any admin reset: bump `Member.tokenVersion`. Every older token fails its next request (`ver` mismatch). No blacklist to store or expire. |
| Transport | TLS is terminated in front of the app. HSTS (1 year, subdomains) is sent on HTTPS requests. |
| Browser | Token in memory only (no localStorage), no cookies, so no CSRF. `frame-ancestors 'none'`, `nosniff`, `no-referrer`, `no-store`. |
| Registration | Self-registered members are **pending** and cannot sign in until an admin approves (`POST /members/{id}/approve`); reject is delete. Registering with the admin code is active at once. Pending is revealed only after the correct password (403 `ACCOUNT_PENDING`); wrong password stays 401. Documents without the field count as approved (field stored inverted). |
| Passwords | BCrypt (strength 12), 8 to 72 characters (BCrypt's limit), letter + number + special. Admin-issued passwords are temporary and force a change. |
| Secrets | `JWT_SECRET` (>= 32 bytes, app refuses to start otherwise), `ADMIN_CODE`, Mongo password: environment only, never in the repo. |

## 2. Threats, controls, proof

| Threat | Control | Code | Test |
|---|---|---|---|
| Password guessing on one account | 5 failures / 15 min lock the **email** for 15 min, 429 + `Retry-After` | `InMemoryAttemptLimiter`, `AuthThrottle`, `AuthServiceImpl.login` | `InMemoryAttemptLimiterTest`, `AuthServiceImplTest`, IT `repeatedWrongPasswords...` |
| One address spraying many accounts | 50 failures / 15 min lock the **address** | `AuthThrottle.byIp` | `AuthThrottleTest.anAddressIsLocked...` |
| Oversized login input | `email` max 254, `password` max 72, rejected before BCrypt runs | `LoginRequest` | `RequestValidationTest` |
| Unapproved self-registration | Cannot sign in until approved by an admin | `AuthServiceImpl.login`, `Member.approve` | `AuthServiceImplTest`, IT `selfRegisteredMembers...` |
| Account enumeration | Same 401 text for unknown email and wrong password; BCrypt runs against a dummy hash of the same strength for unknown emails; registering an existing email returns 409 (known trade-off: reveals that the email exists; mitigated by the register throttle, removable only with email verification); lockout counts unknown emails too, so the 429 reveals nothing | `AuthServiceImpl.DUMMY_HASH` | `AuthServiceImplTest`, IT |
| Guessing the admin code (shared secret that grants ADMIN) | 5 wrong codes / 15 min lock the **address** for 15 min; constant-time compare | `AuthServiceImpl.resolveRole` | `AuthServiceImplTest`, `AuthThrottleTest`, IT `wrongAdminCodes...` |
| Stolen token used to brute-force the current password | Same budget on `/auth/change-password`, per member | `AuthThrottle.beforePasswordChange` | `AuthServiceImplTest` |
| Stolen / copied token after the user leaves | Sign-out revokes every session server side | `AuthServiceImpl.logout`, `MemberJwtAuthenticationConverter` | `MemberJwtAuthenticationConverterTest`, IT `signingOut...` |
| Stolen password, then password changed | Change or reset bumps `tokenVersion`: old tokens die with the old password | `Member.changePassword`, `issueTemporaryPassword` | `MemberTest`, IT `anAdminReset...` |
| Privilege escalation by demotion lag | Role read live per request | `MemberJwtAuthenticationConverter` | `SecurityRulesTest` |
| Viewer mass-exports PII | Export is ADMIN only (whole list with phones in one file) | `SecurityConfig` | `SecurityRulesTest.viewerCanListSearchAndViewButNotExport` |
| One admin takes over another | Admin passwords cannot be reset by another admin: demote first (logged), reset, promote | `AdminGuard.rejectAdminPasswordReset` | `AdminGuardTest`, IT |
| Last admin removed (also by a race) | Pre-check, post-write verify, restore | `AdminGuard` | `AdminGuardTest`, `MemberServiceImplTest` |
| Lost update | `@Version` + `version` echoed by the client, 409 | `Member`, `MemberServiceImpl` | `MemberServiceImplTest`, IT |
| NoSQL injection via search | Anchored, escaped prefix regex; typed DTOs; no operator keys accepted | `MemberQueryBuilder` | `MemberRepository` tests |
| CSV formula injection | Cells starting with `= + - @` are neutralised | `MemberCsvWriter` | `MemberCsvWriterTest` |
| Deny by default | `anyRequest().denyAll()`; only health is public under `/actuator` | `SecurityConfig` | `SecurityRulesTest` |
| Browser feature abuse | `Permissions-Policy` denies camera, microphone, geolocation, payment | `SecurityConfig` | `SecurityRulesTest` |
| Slow or dead database ties up threads | Mongo connect, socket and server-selection timeouts, bounded pool | `application.properties` | manual |
| API map leaking | Swagger and `/v3/api-docs` off unless `SWAGGER_ENABLED=true` | `application.properties` | manual (section 6) |
| No record of who did what | One log line per API call (actor, method and path, address, outcome, duration), no bodies or query strings, shipped to Elasticsearch/Kibana (retention and tamper resistance live there) | `ApiLoggingAspect` | `ApiLoggingAspectTest`, IT `everyApiCallIsLogged...` |
| DB compromise via the app account | App role can `find/insert/update/remove` members only; no DDL | `scripts/mongo/setup.js` | manual (section 6) |
| Plaintext password written by a bug | `$jsonSchema` validator requires a BCrypt-shaped hash; lower-case email; role enum | `scripts/mongo/setup.js` | manual (section 6) |

## 3. Session model in one paragraph

Login issues a token with `ver = member.tokenVersion`. Every request loads the member (one indexed `_id` read) and compares `ver`. Equal: authenticated with the member's current role. Different or missing: 401 `UNAUTHENTICATED`. Sign-out, password change and admin reset increment `tokenVersion` inside the same single-document write as the rest of the change, so revocation is atomic with the event that caused it. The cost is one database read per request, which the role check already needed.

## 4. API log (audit view)

Rejections raised by the security filter (401 / 403) never reach a controller, so the entry point and access-denied handler log the same line shape through `ApiLoggingAspect.logRejected`. `ApiLoggingAspect` is a Spring AOP `@Around` advice on every `@RestController` method, so no controller or service carries logging code. One line per call: `API <METHOD> <path> actor=<memberId|anonymous> ip=<addr> outcome=OK|FAILED|ERROR [error=<ErrorCode|ExceptionClass>] ms=<n>`. Business errors log at WARN with their error code, unexpected failures at ERROR with the class name only (the stack trace is logged once by `GlobalExceptionHandler`). The path holds ids; the query string, parameters, bodies, headers and tokens are never logged.

Nothing is stored in MongoDB. The `prod` profile switches the console to JSON (ECS) logs; ship them with Filebeat or the OpenTelemetry Collector to Elasticsearch, where retention and access are set once for all services and a compromised application cannot rewrite its own trail. In Kibana, failed logins are `message:"/api/v1/auth/login" and message:"outcome=FAILED"`, and the trace id on each line jumps to the request.

## 5. Known limits and the next step for each

| Limit | Why it is acceptable here | Next step |
|---|---|---|
| Throttle state is per instance and in memory | Single instance, idle entries are swept when the table is large | Implement `AttemptLimiter` over Redis or a Mongo TTL collection, or enforce at the gateway. No caller changes. |
| Every client behind one proxy looks like the proxy | Per-address limit is generous (50) | Set `FORWARD_HEADERS=native` behind a trusted proxy. |
| Wrong admin codes lock the address, so an attacker can block admin self-registration from that address | Admin self-registration is rare and `ADMIN_CODE` can be unset | Remove self-registration of admins; create the first admin from a seed job. |
| No refresh token, 30 min hard expiry | Smaller blast radius, simpler | Short access token + rotating refresh token in an httpOnly cookie, reuse detection. |
| Local identity | Scope of the exercise | OIDC / enterprise IdP, MFA, RS256 with `kid` rotation and JWKS. |
| No email: forgotten password needs an admin | Same | Reset-by-email flow, or IdP. |
| No gateway rate limit or body size limit | Out of scope | WAF / gateway: global rate limit, max body, bot rules. |
| Deploying this version invalidates all existing tokens (they carry no `ver`) | One-time, 30 min at most | None needed. |

Admin recovery (forgotten admin password): another admin demotes them to Viewer, resets the password, promotes them back. Each step is logged. If there is no other admin: a break-glass script run by someone with database admin rights, which is itself a controlled operation.

## 6. Verifying it yourself (finding a gap)

Start the app with `SWAGGER_ENABLED=` unset, `JWT_SECRET`, `ADMIN_CODE=TEST-CODE`, then:

```bash
B=http://localhost:8080/api/v1
# 1. lockout: 5 wrong passwords, then the RIGHT password is refused with 429 + Retry-After
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "%{http_code} " -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"a@x.com","password":"wrong"}'; done
curl -si -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"a@x.com","password":"RightOne@1"}' | grep -iE "HTTP/|retry-after"

# 2. unknown email behaves identically (compare status, body, timing)
curl -si -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"ghost@x.com","password":"wrong"}'

# 3. sign-out kills the token everywhere
T=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"a@x.com","password":"RightOne@1"}' | jq -r .accessToken)
curl -s -o /dev/null -w "%{http_code}\n" $B/auth/me -H "Authorization: Bearer $T"     # 200
curl -s -o /dev/null -w "%{http_code}\n" -X POST $B/auth/logout -H "Authorization: Bearer $T"   # 204
curl -s -o /dev/null -w "%{http_code}\n" $B/auth/me -H "Authorization: Bearer $T"     # 401

# 4. forged token: change the payload, keep the signature -> 401
# 5. viewer export -> 403; admin export -> 200
# 6. admin resets an admin -> 409 ADMIN_PASSWORD_RESET_FORBIDDEN
# 7. docs are off: curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v3/api-docs   -> 404 (401/403 if not permitted)
```

Database checks (mongosh as the app user):

```js
db.members.insertOne({name:"x",nameLower:"x",email:"X@A.COM",passwordHash:"plaintext",role:"BOSS",mustChangePassword:false,createdAt:new Date()})   // rejected by the validator
```

## 7. Configuration

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | required | HMAC key, >= 32 characters |
| `ADMIN_CODE` | empty | Grants ADMIN at registration; empty disables it |
| `LOGIN_MAX_FAILURES` / `LOGIN_WINDOW` / `LOGIN_LOCKOUT` | 5 / 15m / 15m | Per-email budget (also used for wrong admin codes, per address) |
| `IP_MAX_FAILURES` | 50 | Per-address failed logins in the window |
| `FORWARD_HEADERS` | `none` | `native` behind a trusted reverse proxy |
| `SWAGGER_ENABLED` | `false` | API docs, development only |
| `AUTO_INDEX` | `true` | `false` in production, indexes come from `scripts/mongo/setup.js` |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | CORS |
