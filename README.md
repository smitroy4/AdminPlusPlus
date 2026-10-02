# Admin++

A production-ready MVP **task portal**: authentication, task CRUD, assignment,
comment threading and role-based access control.

* **Backend** — Spring Boot 4.0.8, Java 21, Spring Data JPA, Spring Security 7
* **Database** — PostgreSQL (Neon by default, local Postgres works too)
* **Frontend** — vanilla HTML / CSS / JavaScript. No frameworks, no bundler, no npm.

---

## 1. Quick start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK         | 21+ (the build targets 21) |
| Maven       | 3.9+ (a wrapper `./mvnw` is included) |
| PostgreSQL  | 14+ (optional — the default config points at a hosted Neon database) |

### Run

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Then open <http://localhost:8080/>.

The schema is created automatically (`spring.jpa.hibernate.ddl-auto=create`) and
five demo accounts plus three dummy customers are seeded on first start:

| Role      | Username     | Password             |
|-----------|--------------|----------------------|
| ADMIN     | `admin`      | `Admin@12345`        |
| MANAGER   | `manager`    | `Manager@12345`      |
| COORDINATOR | `coordinator` | `Coordinator@12345` |
| ASSOCIATE | `employee`   | `Employee@12345`     |
| CLIENT    | `client`     | `Client@12345`       |

The `client` login is linked to the seeded **Acme Corporation** customer and
only ever sees Acme's tasks (details seeded: Acme Corporation, Globex
Industries, Initech Ltd).

Seeding can be turned off with `app.seed.enabled=false`.

### Point it at your own database

Every datasource value is overridable through an environment variable, so the
checked-in defaults never have to change:

```bash
export TASKPORTAL_DB_URL=jdbc:postgresql://localhost:5432/taskportal_db
export TASKPORTAL_DB_USERNAME=postgres
export TASKPORTAL_DB_PASSWORD=secret
export TASKPORTAL_PORT=8080
.\mvnw.cmd spring-boot:run
```

To use a local database, create it first:

```sql
CREATE DATABASE taskportal_db;
```

### Build a jar

```bash
.\mvnw.cmd clean package
java -jar target/taskportal-0.0.1-SNAPSHOT.jar
```

---

## 2. Project layout

```
src/main/java/com/smit/taskportal
├── TaskportalApplication.java
├── api/dto/                  Request + response records (no entities leak out)
│   ├── ApiResponse           { success, message, data }
│   ├── TaskDto, TaskDetailDto, TaskMessageDto
│   ├── UserDto, UserSummaryDto, ClientDto, ClientSummaryDto
│   ├── DashboardStatsDto, CsrfDto
│   └── *Request              Validated inbound payloads
├── bootstrap/
│   └── DataInitializer        Seeds the demo accounts and sample tasks
├── config/
│   ├── SecurityConfig         Form login, CSRF, URL authorisation rules
│   └── AppProperties          Typed view of the app.* block
├── controller/
│   ├── AuthController         /api/me, /api/csrf, /api/auth/change-password
│   ├── DashboardController    /api/dashboard/*, /api/tasks/*
│   ├── TaskController         /api/task/{id}, POST /api/task, PATCH …/status
│   ├── TaskMessageController  /api/task/{id}/message[s]
│   ├── ClientController       /api/clients[/id] (details: MANAGER, ADMIN)
│   ├── ManagerController      /api/manager/**   (MANAGER, ADMIN)
│   └── AdminUserController    /api/admin/**     (ADMIN)
├── domain/                    User, Task, TaskMessage, Client + Role/TaskStatus/TaskPriority
├── exception/
│   ├── AppException           sealed root of expected failures
│   ├── ResourceNotFoundException / UnauthorizedException / ForbiddenException
│   ├── BadRequestException / DuplicateResourceException
│   └── GlobalExceptionHandler @RestControllerAdvice for all of the above
├── repository/                Spring Data JPA + @EntityGraph, no N+1
├── security/
│   ├── AppUserPrincipal       record implementing UserDetails
│   ├── CurrentUserHolder      resolves the principal from the security context
│   └── RestAware{AuthenticationEntryPoint,AccessDeniedHandler}
└── service/                   UserService, TaskService, TaskMessageService, ClientService, TaskNoGenerator

src/main/resources
├── application.yml
└── static/
    ├── index.html             redirects to /dashboard.html or /login.html
    ├── login.html
    ├── dashboard.html         stats, my open tasks, shared open backlog
    ├── my-tasks.html          everything assigned to me, any status
    ├── all-tasks.html         full backlog: sort any column, filter client/agent/status
    ├── task-detail.html       header, status/assign controls, message thread
    ├── profile.html           own details + password change
    ├── users.html             admin-only account management (staff roles only)
    ├── clients.html           manager/admin directory of client companies
    ├── css/style.css          the single stylesheet (light default, dark toggle)
    └── js/
        ├── app.js             fetch + CSRF + escaping + formatting + theme
        ├── auth.js            page guard, header chrome, logout
        ├── index.js / login.js / dashboard.js / my-tasks.js / all-tasks.js
        ├── task-detail.js
        ├── profile.js
        ├── users.js
        └── clients.js
```

---

## 3. Domain model

```
users                          tasks                          task_messages
------                         -----                          -------------
id            PK              id            PK               id            PK
username      UQ, NN          task_no       UQ, NN           task_id       FK → tasks
password      NN (BCrypt)     title         NN               from_user_id  FK → users
email         UQ, NN          description                    message_body  NN (TEXT)
role          NN (enum)       status        NN (enum)        is_internal   NN, default false
client_id     FK → clients    priority      NN (enum)        created_at    NN
  (CLIENT accounts only)      assigned_to   FK → users (nullable)
                             client_id     FK → clients (nullable)
                             created_by    FK → users
                             created_at /  NN
                             updated_at    NN

clients
-------
id            PK
name          UQ, NN           every task carries the customer it is for
contact_name / email / phone / notes   MANAGER + ADMIN only
```

`task_no` is generated from a dedicated PostgreSQL sequence (`task_no_seq`) so
concurrent task creation can never collide, yielding `TASK-00001`, `TASK-00002`, …

### Enumerations

| Enum           | Values                                                    |
|----------------|-----------------------------------------------------------|
| `Role`         | `CLIENT`, `ASSOCIATE`, `COORDINATOR`, `MANAGER`, `ADMIN`     |
| `TaskStatus`   | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CLOSED`                 |
| `TaskPriority` | `LOW`, `MEDIUM`, `HIGH`, `URGENT`                            |

Role hierarchy (least to most privileged): `CLIENT` < `ASSOCIATE` <
`COORDINATOR` < `MANAGER` < `ADMIN`. Coordinators cannot create tasks or reach
the manager endpoints; they additionally see the workload metrics of every
`ASSOCIATE`.

`CLIENT` is an external, read-only account: it is linked to one row of
`clients` and can only ever see that customer's tasks (name of the customer on
each task; the contact block stays reserved for MANAGER / ADMIN).

Allowed transitions:

```
OPEN ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ CLOSED
  ▲            │              │           │
  └────────────┴──────────────┴───────────┘
```

Moving a task *out* of `CLOSED` requires MANAGER or ADMIN.

---

## 4. API

All responses share one envelope:

```json
{ "success": true,  "message": "OK",   "data": { } }
{ "success": false, "message": "…",    "data": null }
```

| Method | Path                              | Access            | Purpose                              |
|--------|-----------------------------------|-------------------|--------------------------------------|
| GET    | `/api/csrf`                       | public            | Issues the CSRF token/cookie          |
| GET    | `/api/me`                         | authenticated     | Current user                         |
| PUT    | `/api/me/profile`                 | authenticated     | Change own email                     |
| POST   | `/api/auth/change-password`       | authenticated     | Change own password                  |
| GET    | `/api/dashboard/stats`            | authenticated     | Role-scoped tile counters + breakdowns |
| GET    | `/api/tasks/my-open`              | authenticated     | Active tasks assigned to me          |
| GET    | `/api/tasks/my-created`           | authenticated     | Active tasks I raised                |
| GET    | `/api/tasks/mine`                 | authenticated     | Every task assigned to me            |
| GET    | `/api/tasks/all-open`             | authenticated     | Active backlog (client: own customer only) |
| GET    | `/api/tasks/all`                  | authenticated     | Every task, any status (same scoping) |
| GET    | `/api/tasks/by-status?status=`    | authenticated     | Filter by a single status            |
| GET    | `/api/task/{id}`                  | owner / assignee / manager | Task + visible thread      |
| POST   | `/api/task`                       | MANAGER, ADMIN    | Create a task                        |
| PUT    | `/api/task/{id}`                  | creator / manager | Replace title, description, priority (`title` required) |
| PATCH  | `/api/task/{id}/status`           | assignee / creator / manager | Move along the life-cycle |
| POST   | `/api/task/{id}/assign`           | manager, or self on a free task | Assign            |
| DELETE | `/api/task/{id}/assign`           | MANAGER, ADMIN    | Release back to the pool             |
| GET    | `/api/task/{id}/messages`         | owner / assignee / manager | Visible thread               |
| POST   | `/api/task/{id}/message`          | owner / assignee / manager (not CLIENT) | Post a reply          |
| DELETE | `/api/task/{id}/message/{mid}`    | author, or ADMIN   | Retract a comment                  |
| GET    | `/api/clients`                    | authenticated     | Client list (contact block: MANAGER, ADMIN) |
| GET    | `/api/clients/{id}`               | authenticated     | One client (same detail rule)        |
| GET    | `/api/manager/users`              | MANAGER, ADMIN    | Users for the assign dropdown (clients excluded) |
| GET    | `/api/manager/tasks`              | MANAGER, ADMIN    | Filtered backlog                    |
| GET    | `/api/admin/users`                | ADMIN             | List accounts                        |
| POST   | `/api/admin/users`                | ADMIN             | Create an account                    |
| PATCH  | `/api/admin/users/{id}`           | ADMIN             | Edit a user's username / email       |
| PATCH  | `/api/admin/users/{id}/password`  | ADMIN             | Reset any user's password            |
| PATCH  | `/api/admin/users/{id}/role`      | ADMIN             | Change a role                        |

Status codes: `400` bad input (including an illegal status jump), `401` not
authenticated, `403` not permitted **or CSRF token not delivered**, `404` not
found, `409` duplicate, `500` unexpected (logged server-side, generic
message returned).

### Examples

```bash
# 1. grab a CSRF token (sets the XSRF-TOKEN cookie)
curl -c cookies.txt http://localhost:8080/api/csrf

# 2. sign in (keep the session cookie)
CSRF=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/login \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -H "X-XSRF-TOKEN: $CSRF" \
     --data-urlencode "username=manager" \
     --data-urlencode "password=Manager@12345"

# 3. create a task
curl -b cookies.txt -X POST http://localhost:8080/api/task \
     -H "Content-Type: application/json" \
     -H "X-XSRF-TOKEN: $CSRF" \
     -d '{"title":"Ship the MVP","description":"Cut 0.1.0","priority":"HIGH"}'
```

---

## 5. Security model

| Concern        | Approach                                                                                     |
|----------------|----------------------------------------------------------------------------------------------|
| Passwords      | BCrypt (`BCryptPasswordEncoder`), never serialised out — DTOs only                              |
| Sessions       | Server-side `HttpSession`, `HttpOnly` + `SameSite=Lax` cookie, 30-minute timeout                |
| CSRF           | **Enabled everywhere.** Token in a JS-readable `XSRF-TOKEN` cookie, and it must be echoed back in the `X-XSRF-TOKEN` header or a `_csrf` form field — see below |
| Unauthenticated API calls | JSON `401`, never an HTML redirect                                                        |
| Authorisation  | URL rules in `SecurityConfig` + `@PreAuthorize` on manager/admin controllers + service-level ownership checks |
| XSS            | Every value interpolated into the DOM goes through `App.esc()`; no `innerHTML` on raw input    |
| Client storage | Nothing sensitive in `localStorage` — the session lives in an `HttpOnly` cookie                  |
| Login UX       | Works with plain form submission; the SPA adds an `X-Requested-With` header to get JSON errors    |
| Errors         | 500s are logged with a stack trace but reported as a generic message                             |

### Role permissions

| Action                                  | CLIENT | ASSOCIATE | COORDINATOR | MANAGER | ADMIN |
|-----------------------------------------|:------:|:---------:|:-----------:|:-------:|:-----:|
| Sign in, view own tasks                 | ✅ | ✅ | ✅ | ✅ | ✅ |
| See the open / all tasks backlog        | own customer's | ✅ | ✅ | ✅ | ✅ |
| Create a task                           | ❌ | ❌ | ❌ | ✅ | ✅ |
| Comment on a task you own/are assigned  | ❌ | ✅ | ✅ | ✅ | ✅ |
| Claim an unassigned task                | ❌ | ✅ | ✅ | ✅ | ✅ |
| Assign a task to somebody else          | ❌ | ❌ | ❌ | ✅ | ✅ |
| Edit title / description / priority     | ❌ | own only | own only | ✅ | ✅ |
| Post internal notes                     | ❌ | ❌ | ❌ | ✅ | ✅ |
| Unassign a task                         | ❌ | ❌ | ❌ | ✅ | ✅ |
| See associate workload metrics          | ❌ | ❌ | ✅ | ✅ | ✅ |
| See client contact details              | ❌ | ❌ | ❌ | ✅ | ✅ |
| Edit own email / password               | ✅ | ✅ | ✅ | ✅ | ✅ |
| Manage accounts                         | ❌ | ❌ | ❌ | ❌ | ✅ |

**Internal notes** are visible to MANAGER and ADMIN, plus their own author.
**Client accounts** are read-only: they can browse their own customer's tasks
but never comment, claim, reassign or change status.

### Metrics visibility

`GET /api/dashboard/stats` scopes every counter to the caller's role:

| Caller      | `myXxx`      | `teamXxx` (associate workload) | `allXxx` (company-wide) | Breakdown charts |
|-------------|--------------|--------------------------------|-------------------------|------------------|
| CLIENT      | — (no assigned tasks) | —                      | —                       | —                |
| ASSOCIATE   | own tasks    | —                              | —                       | own tasks        |
| COORDINATOR | own tasks    | every task touching an ASSOCIATE | —                     | associate tasks  |
| MANAGER     | own tasks    | every task touching an ASSOCIATE | ✅                    | everything       |
| ADMIN       | own tasks    | every task touching an ASSOCIATE | ✅                    | everything       |

The booleans `canViewTeam` / `canViewAll` in the payload tell the SPA which
tile groups to render.

### CSRF delivery is mandatory, not just the cookie

A bare cookie based token repository has a well-known hole: the browser attaches
`XSRF-TOKEN` to cross-site requests too, so the cookie on its own proves nothing.
`SecurityConfig.CsrfTokenDeliveryFilter` therefore runs ahead of `CsrfFilter` and
rejects every unsafe request (`POST`, `PUT`, `PATCH`, `DELETE`) that does not
deliver the token in the `X-XSRF-TOKEN` header or the `_csrf` field — with `403`.

That restores the double-submit guarantee, because a third-party page can neither
read the cookie nor add a custom header without a CORS pre-flight it will not be
granted. `App.request()` does the echo for you and, because Spring Security
rotates the token on sign-in and sign-out, transparently re-fetches it and
replays the request once if the server rejects it.

```bash
# rejected: the cookie is present but the token is not echoed back
curl -b cookies.txt -X POST http://localhost:8080/api/task \
     -H "Content-Type: application/json" -d '{"title":"nope"}'   # 403

# accepted: the token is echoed in the header
curl -b cookies.txt -X POST http://localhost:8080/api/task \
     -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
     -d '{"title":"accepted"}'                                     # 201
```

### Status transitions

`TaskStatus.canTransitionTo` guards the lifecycle, so `PATCH /api/task/{id}/status`
answers `400` for an illegal jump such as `OPEN → COMPLETED`. Move through
`IN_PROGRESS` instead. Re-opening a `CLOSED` task is allowed.

---

## 6. Frontend notes

* One stylesheet (`/css/style.css`) with CSS custom properties for the palette.
* **Light is the default**; the moon/sun button in the header (or the login
  card) flips to dark mode. The choice is stored in `localStorage`
  (`admin++-theme`) and applied before first paint by a tiny inline script,
  so there is no flash of the wrong theme.
* Dark header, light content, alternating table rows, colour-coded status and
  priority badges, alternating left/right message bubbles.
* `/profile.html` lets every signed-in user update their email and change
  their password; `/users.html` (admin only) lists the staff accounts —
  associate, coordinator and manager — and can only create or assign those
  three roles. The **Clients** nav entry (managers and admins) opens
  `/clients.html`, the directory of every client company with its contact
  details.
* Tables are sortable — click a column header to toggle ascending/descending.
* **All Tasks** (`/all-tasks.html`) lists the full backlog for every role with
  client-side filters (free-text search, client, agent, status) and a sortable
  column per field; rows that fail the filters are hidden, not re-fetched.
* Client names appear as their own column in the task tables; task detail shows
  a **Client details** card (contact, email, phone, notes) that the API only
  sends to MANAGER / ADMIN. Client sign-ins get a read-only detail page.
* `App.esc()` / `App.escMultiline()` are mandatory for any server value; this is
  the only XSS defence and it is applied everywhere.
* Desktop-first (≥1024px), as specified. The layout uses CSS grid/flex so it
  degrades reasonably on smaller screens, but mobile is out of scope for the MVP.

---

## 7. Configuration reference

| Property                | Default (env var override)                        |
|-------------------------|----------------------------------------------------|
| `spring.datasource.url`         | Neon pooled endpoint (`TASKPORTAL_DB_URL`)           |
| `spring.datasource.username`    | `taskportal-db_owner` (`TASKPORTAL_DB_USERNAME`)     |
| `spring.datasource.password`    | Neon key (`TASKPORTAL_DB_PASSWORD`)                 |
| `spring.jpa.hibernate.ddl-auto` | `create` (drop + recreate on every start)           |
| `spring.jpa.open-in-view`       | `false`                                             |
| `server.port`                   | `8080` (`TASKPORTAL_PORT`)                          |
| `app.seed.enabled`              | `true`                                              |
| `app.seed.sample-tasks`         | `true`                                              |
| `app.seed.admin-password`       | `Admin@12345` (`TASKPORTAL_ADMIN_PASSWORD`)         |
| `app.seed.coordinator-password` | `Coordinator@12345` (`TASKPORTAL_COORDINATOR_PASSWORD`) |
| `app.seed.client-password`      | `Client@12345` (`TASKPORTAL_CLIENT_PASSWORD`)       |

> The checked-in datasource defaults are the development credentials supplied for
> this project. Rotate them and pass real values through environment variables for
> any shared or deployed environment.

---

## 8. Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## 9. Deliberately out of scope (MVP)

Email ingestion, file attachments, a points/gamification system, notifications,
task templates, pagination on the list endpoints, and a localisation layer.
