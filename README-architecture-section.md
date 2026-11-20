## Architecture and flows

All diagrams are [Mermaid](https://mermaid.js.org/), so they render on GitHub and in the IntelliJ / VS Code Markdown preview.

### 1. System architecture

```mermaid
flowchart TB
    U["Browser<br/>React 19 SPA<br/>token kept in memory"] -->|"HTTP /api/v1<br/>Bearer JWT + traceparent"| P["Vite dev proxy<br/>:5173 to :8080"]
    P --> F1
    subgraph S["member-service (Spring Boot 4.1, Java 21)"]
        F1["TraceIdFilter<br/>X-Trace-Id"] --> F2["JWT check<br/>live role from MongoDB"]
        F2 --> F3["Route rules<br/>denyAll by default"]
        F3 --> C["Controllers<br/>Bean Validation"]
        C --> SV["Services and helpers"]
        SV --> R["Repository<br/>MemberQueryBuilder"]
    end
    R -->|"Spring Data MongoDB"| M[("MongoDB<br/>members collection")]
```

Transport is plain HTTP locally; terminate TLS in front of the service in production (see Production notes).

### 2. Backend layers

```mermaid
flowchart TB
    subgraph Web["Controllers (HTTP only)"]
        MC["MemberController"]
        AC["AuthController"]
    end
    subgraph Svc["Services (rules and orchestration)"]
        MS["MemberServiceImpl"]
        AS["AuthServiceImpl"]
        H["MemberCreator, AdminGuard<br/>MemberSortParser, MemberCsvWriter"]
    end
    subgraph Data["Repository (queries)"]
        MR["MemberRepository"]
        QB["MemberRepositoryCustomImpl<br/>MemberQueryBuilder"]
    end
    subgraph Cross["Cross-cutting"]
        GE["GlobalExceptionHandler<br/>ErrorCode"]
        LG["ApiLoggingAspect<br/>TraceIdFilter"]
        SEC["SecurityConfig<br/>JWT converter, AuthThrottle"]
        VAL["MemberRules<br/>validation annotations<br/>PhoneRules"]
    end
    MC --> MS
    AC --> AS
    MS --> H
    MS --> MR
    AS --> MR
    MR --> QB
    QB --> DB[("MongoDB")]
```

### 3. Entities and role types

```mermaid
classDiagram
    class Member {
        String id
        String name
        String nameLower
        String email
        String countryCode
        String phoneNumber
        String passwordHash
        Role role
        boolean pendingApproval
        boolean mustChangePassword
        int tokenVersion
        Long version
        Instant createdAt
        Instant updatedAt
        String createdBy
        String updatedBy
        getStatus() MemberStatus
    }
    class Role {
        <<enumeration>>
        ADMIN
        VIEWER
    }
    class MemberStatus {
        <<enumeration>>
        PENDING_APPROVAL
        TEMP_PASSWORD
        ACTIVE
    }
    Member --> Role : has one
    Member ..> MemberStatus : derived from two flags
```

Notes:
- `Member` is the entity; the only roles are **ADMIN** and **VIEWER**.
- Status has no stored field. `pendingApproval` first, then `mustChangePassword`, decide it.
- `pendingApproval` is stored so that `false` means approved: documents saved before approval existed have no such field and read as approved.
- `version` (`@Version`) detects edit conflicts. `tokenVersion` revokes sessions. They are different counters.

How data changes shape on the way in and out:

```mermaid
flowchart LR
    A["Request DTO<br/>name, email, countryCode, phone"] --> B["MemberDetails<br/>record from toDetails()"]
    B --> C["Member<br/>normalised and stored"]
    C --> D["MemberResponse<br/>no password hash"]
```

### 4. UI flow

```mermaid
flowchart LR
    subgraph Self["Self-registration"]
        R1["Register<br/>admin code optional"] --> R2{"Valid admin code?"}
        R2 -->|"yes"| R3["ADMIN, active at once"]
        R2 -->|"no"| R4["VIEWER, pending approval<br/>cannot sign in"]
        R4 --> R5["Admin approves"]
    end
    subgraph ByAdmin["Admin-created member"]
        A1["Admin adds member"] --> A2["Temporary password<br/>shown once"]
        A2 --> A3["First sign-in<br/>forced to change password"]
    end
    R3 --> MP["Members page"]
    R5 --> MP
    A3 --> MP
    MP --> V["VIEWER: read-only directory"]
    MP --> AD["ADMIN: create, edit, role,<br/>approve, reset, delete, export"]
```

### 5. Page routing by role (React)

```mermaid
flowchart TD
    Q["Page requested"] --> S{"Signed in?"}
    S -->|"no"| L["Redirect to /login"]
    S -->|"yes"| M{"Must change password?"}
    M -->|"yes"| CP["Redirect to /change-password"]
    M -->|"no"| AO{"Admin-only page?"}
    AO -->|"no"| OK["Render page"]
    AO -->|"yes, and ADMIN"| OK
    AO -->|"yes, but not ADMIN"| MB["Redirect to /members"]
```

These guards only shape the experience. The server enforces access on every call, so a viewer who forces an admin request still gets 403.

### 6. Sign-in, token and session

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as AuthController
    participant T as AuthThrottle
    participant DB as MongoDB
    participant J as JwtTokenService
    B->>A: POST /auth/login (email, password)
    A->>T: beforeLogin(email, ip)
    Note over A,T: locked key gives 429 with Retry-After
    A->>DB: find member by email
    A->>A: BCrypt check (dummy hash if email unknown)
    alt wrong password
        A->>T: loginFailed
        A-->>B: 401
    else correct but pending approval
        A-->>B: 403
    else correct
        A->>T: loginSucceeded
        A->>J: issue(member)
        J-->>A: token with sub, ver, iat, exp (30 min)
        A-->>B: token in response body
    end
    Note over B: token kept in memory only, reload signs out
    B->>A: any call with Authorization Bearer token
    A->>DB: load member by sub
    A->>A: ver must equal tokenVersion, role read live
    B->>A: POST /auth/logout
    A->>DB: tokenVersion + 1 (all tokens of this member die)
```

The token carries no role. A demotion, deletion, sign-out, password change or admin reset takes effect on the next request.

### 7. Search flow

```mermaid
sequenceDiagram
    participant UI as Members page
    participant C as MemberController
    participant S as MemberServiceImpl
    participant R as MemberRepositoryCustomImpl
    participant Q as MemberQueryBuilder
    participant DB as MongoDB
    UI->>C: GET /members?search=ali&role=VIEWER&sort&page
    Note over UI: 300 ms debounce
    C->>S: list(criteria, sort, page, size)
    S->>S: page * size above 10,000 gives PAGE_TOO_DEEP
    S->>R: search(criteria, pageable)
    R->>Q: build(criteria)
    Q-->>R: filter
    R->>DB: find with sort, skip, limit
    R->>DB: count, only if the page cannot tell the total
    DB-->>UI: Page of members
```

Example filter for `search=ali`, `role=VIEWER`:

```json
{ "$and": [
    { "$or": [ { "nameLower": { "$regex": "^ali" } },
               { "email":     { "$regex": "^ali" } } ] },
    { "role": "VIEWER" }
] }
```

The search term is lower-cased and regex-escaped, so it is always a literal prefix match.

### 8. Data integrity: edit conflicts and the last admin

Two admins edit the same member (optimistic locking, two layers):

```mermaid
sequenceDiagram
    participant A as Admin A (form at version 3)
    participant B as Admin B (form at version 3)
    participant S as MemberServiceImpl
    participant DB as MongoDB
    A->>S: PUT /members/42 with version 3
    S->>DB: load (version 3), check passes
    S->>DB: save, filter on id and version 3
    DB-->>A: ok, version is now 4
    B->>S: PUT /members/42 with version 3
    S->>DB: load (version 4)
    S-->>B: 409 stale version, database not written
    Note over S,DB: if two requests both load version 4 and both save,<br/>the second save matches nothing and fails with a 409
```

Demoting an admin (best-effort protection for the last admin):

```mermaid
flowchart TD
    D["Change role of an ADMIN to VIEWER"] --> V{"Version current?"}
    V -->|"no"| E1["409 stale version"]
    V -->|"yes"| G{"Another admin exists?"}
    G -->|"no"| E2["Rejected"]
    G -->|"yes"| SV["Save with @Version"]
    SV --> VR{"An admin still remains?"}
    VR -->|"no, two demotions raced"| RS["Restore role and reject"]
    VR -->|"yes"| OK["Done"]
```

Related rules in `AdminGuard`: admins cannot be deleted, bulk delete never removes admins (the query itself excludes them), and an admin's password cannot be reset through the admin flow.
