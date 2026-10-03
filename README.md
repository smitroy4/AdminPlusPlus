# Admin++

A production-ready MVP **task portal**: authentication, task CRUD, assignment,
comment threading and role-based access control.

* **Backend** — Spring Boot 4.0.8, Java 21, Spring Data JPA, Spring Security 7
* **Database** — PostgreSQL (Neon by default, local Postgres works too)
* **Frontend** — vanilla HTML / CSS / JavaScript. No frameworks, no bundler, no npm.

**Deployed** to an Oracle Cloud VM — Canonical Ubuntu 24.04, 1 OCPU, 1 GB RAM —
available at <https://adminpp.smitroy.com>. Pushes to `main` build a Docker
image (GHCR) and roll it out over SSH by `.github/workflows/deploy.yml`.

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

No credentials are checked in. The datasource is read from
`TASKPORTAL_DB_URL`, `TASKPORTAL_DB_USERNAME` and `TASKPORTAL_DB_PASSWORD`,
which Spring resolves from real environment variables **or** from the
git-ignored `.env` file in the project root (imported automatically by
`application.yml`):

```bash
# .env  (create it next to pom.xml - it is ignored by git and .dockerignore)
TASKPORTAL_DB_URL=jdbc:postgresql://localhost:5432/taskportal_db
TASKPORTAL_DB_USERNAME=postgres
TASKPORTAL_DB_PASSWORD=secret
TASKPORTAL_PORT=8080

.\mvnw.cmd spring-boot:run
```

Real environment variables always win over `.env`, so in Docker/CI pass them
directly (`docker run --env-file .env ...` or your orchestrator's secret store).

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
│   ├── DashboardStatsDto, CsrfDto, NotificationDto
│   ├── AssociateSubmissionDto, ReviewSubmissionRequest
│   └── *Request              Validated inbound payloads
├── bootstrap/
│   └── DataInitializer        Seeds the demo accounts and sample tasks
├── config/
│   ├── SecurityConfig         Form login, CSRF, URL authorisation rules
│   └── AppProperties          Typed view of the app.* block
├── controller/
│   ├── AuthController         /api/me, /api/csrf, /api/auth/change-password
│   ├── DashboardController    /api/dashboard/*, /api/tasks/*
│   ├── TaskController         /api/task/{id}, POST /api/task, PATCH …/status, POST …/escalate
│   ├── TaskMessageController  /api/task/{id}/message[s]
│   ├── TaskSubmissionController /api/task/{id}/associate-submission[s]
│   ├── AssociateSubmissionController  alias at /api/task/{id}/submissions (unused by the UI)
│   ├── NotificationController /api/notifications (navbar bell feed)
│   ├── ClientController       /api/clients[/id] (details: MANAGER, ADMIN)
│   ├── ManagerController      /api/manager/**   (MANAGER, ADMIN)
│   └── AdminUserController    /api/admin/**     (ADMIN)
├── domain/                    User, Task, TaskMessage, Client, AssociateSubmission + enums
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
└── service/                   UserService, TaskService, TaskMessageService, ClientService, NotificationService, AssociateSubmissionService, TaskNoGenerator

src/main/resources
├── application.yml
└── static/
    ├── index.html             redirects to /dashboard.html or /login.html
    ├── login.html
    ├── dashboard.html         stats, my open tasks, shared open backlog
    ├── my-tasks.html          everything assigned to me, any status
    ├── all-tasks.html         full backlog: sort any column, filter client/agent/status
    ├── task-detail.html       header, status/assign controls, thread, then escalation
    ├── profile.html           own details + password change
    ├── users.html             admin-only account management (staff roles only)
    ├── clients.html           manager/admin directory of client companies
    ├── css/style.css          the single stylesheet (light default, dark toggle)
    └── js/
        ├── app.js             fetch + CSRF + escaping + formatting + theme
        ├── auth.js            page guard, header chrome, notifications bell, logout
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
client_id     FK → clients    priority      NN (enum)        is_escalation NN, default false
                                                             created_at    NN
  (CLIENT accounts only)      assigned_to   FK → users (nullable)
                             client_id     FK → clients (nullable)
                              created_by    FK → users
                              escalated_by  FK → users (nullable)
                              created_at /  NN
                              updated_at    NN
                              assigned_at   when the current assignee took it
                              status_changed_at   last status move (clears the bell)

associate_submissions
---------------------
id            PK
task_id       FK → tasks        employee_id  FK → users
content       NN (TEXT)         status       NN (enum PENDING | REVIEWED)
reviewed_by   FK → users, reviewed_at
created_at /  updated_at        append-only, never overwritten

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
| `TaskStatus`   | `OPEN`, `IN_PROGRESS`, `QUALITY`, `SUBMITTED`, `CLOSED`     |
| `TaskPriority` | `NORMAL`, `URGENT`                                        |

Role hierarchy (least to most privileged): `CLIENT` < `ASSOCIATE` <
`COORDINATOR` < `MANAGER` < `ADMIN`. Coordinators cannot create tasks or reach
the manager endpoints; they additionally see the workload metrics of every
`ASSOCIATE`.

`CLIENT` is an external account: it is linked to one row of `clients`, can only
ever see that customer's tasks (name of the customer on each task; the contact
block stays reserved for MANAGER / ADMIN), and may only comment on those tasks —
never claim, assign or change status. An `ASSOCIATE` never writes in the thread
either: its composer turns the text into a submission (see below).

Allowed transitions:

```
OPEN ──▶ IN_PROGRESS ──▶ QUALITY ──▶ SUBMITTED ──▶ CLOSED
  ▲            │            │            │           │
  └────────────┴────────────┴────────────┴───────────┘
```

`QUALITY` is where an associate's work waits for review: submitting an update
sets the task to `QUALITY` (unless it is already `QUALITY` or `CLOSED`) and
bumps `updatedAt`.
Answering that submission — or a coordinator/manager/admin posting any reply on
a `QUALITY` task — pushes it to `SUBMITTED`. Moving a task *out* of `CLOSED`
requires MANAGER or ADMIN.

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
| GET    | `/api/notifications`              | authenticated     | Activity feed for the navbar bell (role-scoped; a task's status move drops every entry about it) |
| PUT    | `/api/me/profile`                 | authenticated     | Change own email                     |
| POST   | `/api/auth/change-password`       | authenticated     | Change own password                  |
| GET    | `/api/dashboard/stats`            | authenticated     | Role-scoped tile counters + breakdowns |
| GET    | `/api/tasks/my-open`              | authenticated     | Active tasks assigned to me (client: own customer) |
| GET    | `/api/tasks/my-created`           | authenticated     | Active tasks I raised                |
| GET    | `/api/tasks/mine`                 | authenticated     | Every task assigned to me            |
| GET    | `/api/tasks/all-open`             | authenticated     | Active backlog (client: own customer only) |
| GET    | `/api/tasks/all`                  | authenticated     | Every task, any status (same scoping) |
| GET    | `/api/tasks/by-status?status=`    | authenticated     | Filter by a single status            |
| GET    | `/api/task/{id}`                  | any internal role (CLIENT: own customer) | Task + visible thread + escalation conversation (participants) |
| POST   | `/api/task`                       | MANAGER, ADMIN    | Create a task                        |
| PUT    | `/api/task/{id}`                  | creator / manager | Replace title, description, priority (`title` required) |
| PATCH  | `/api/task/{id}/status`           | assignee / creator / manager | Move along the life-cycle |
| POST   | `/api/task/{id}/assign`           | manager, or self on a free task | Assign            |
| DELETE | `/api/task/{id}/assign`           | MANAGER, ADMIN    | Release back to the pool             |
| GET    | `/api/task/{id}/messages`         | any internal role (CLIENT: own customer) | Visible thread               |
| POST   | `/api/task/{id}/message`          | any internal role except ASSOCIATE, or client on its own customer | Post a reply (never internal for CLIENT; an ASSOCIATE is told to submit an update instead) |
| POST   | `/api/task/{id}/escalate`         | CLIENT opens, MANAGER / ADMIN reply | Private escalation conversation (201) |
| DELETE | `/api/task/{id}/message/{mid}`    | ADMIN only        | Remove a message from the thread (and the escalation conversation) |
| GET    | `/api/task/{id}/associate-submissions` | task visibility (CLIENT: empty list) | Every submission, newest first |
| POST   | `/api/task/{id}/associate-submission`  | ASSOCIATE      | Post an update for review (201, sets `QUALITY`, bumps `updatedAt`) |
| POST   | `/api/task/{id}/associate-submission/{sid}/review` | COORDINATOR, MANAGER, ADMIN | API-only reply path (the UI replies through the composer): appends the answer, marks the submission `REVIEWED`, sets `SUBMITTED` |
| GET    | `/api/clients`                    | authenticated     | Client list (CLIENT: own customer only; contact block: MANAGER, ADMIN) |
| GET    | `/api/clients/{id}`               | authenticated     | One client (CLIENT: own customer only, else 404; same detail rule) |
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
     -d '{"title":"Ship the MVP","description":"Cut 0.1.0","priority":"NORMAL"}'
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
| Open any task detail + thread           | own customer's | ✅ | ✅ | ✅ | ✅ |
| Create a task                           | ❌ | ❌ | ❌ | ✅ | ✅ |
| Comment on a task                       | own customer's | ❌ (submit an update instead) | ✅ | ✅ | ✅ |
| Submit an update for review             | ❌ | ✅ | ❌ | ❌ | ❌ |
| Review an associate's submission        | ❌ | ❌ | ✅ | ✅ | ✅ |
| Claim an unassigned task                | ❌ | ✅ | ✅ | ✅ | ✅ |
| Assign a task to somebody else          | ❌ | ❌ | ❌ | ✅ | ✅ |
| Edit title / description / priority     | ❌ | own only | own only | ✅ | ✅ |
| Post internal notes                     | ❌ | ❌ | ❌ | ✅ | ✅ |
| Escalate a task to a manager            | ✅ | ❌ | ❌ | reply only | reply only |
| Delete a message                        | ❌ | ❌ | ❌ | ❌ | ✅ |
| Unassign a task                         | ❌ | ❌ | ❌ | ✅ | ✅ |
| See associate workload metrics          | ❌ | ❌ | ✅ | ✅ | ✅ |
| See client contact details              | ❌ | ❌ | ❌ | ✅ | ✅ |
| Edit own email / password               | ✅ | ✅ | ✅ | ✅ | ✅ |
| Manage accounts                         | ❌ | ❌ | ❌ | ❌ | ✅ |

**Associate submissions.** An ASSOCIATE cannot use the reply composer at all
(`POST /api/task/{id}/message` answers 403 with a pointer to the flow). Its
composer posts to `/api/task/{id}/associate-submission` instead, which stores an
`associate_submissions` row, sets the task to `QUALITY` and bumps `updatedAt`.
Every submission stays for good — nothing is overwritten. The task page lists
them newest first as collapsed cards *Submitted by {Employee Name} | {Time}* in
their own **Submitted for Quality Approval** section below the composer
(clients, and an empty list, hide that section).

Those cards are read only: a reviewer copies the text into the **Add a message**
box above and replies on the ordinary thread. A COORDINATOR, MANAGER or ADMIN
doing so while the task sits in `QUALITY` moves it to `SUBMITTED`. The same
answer can also be filed through
`POST /api/task/{id}/associate-submission/{sid}/review` — an API path the UI
does not use — which additionally marks the submission `REVIEWED`. CLIENT
accounts get an empty list.

**Deleting messages** is an ADMIN-only power, on the server
(`TaskMessageService.deleteMessage`) and in the UI: the trash icon never renders
for any other role, even for a message they wrote themselves.

**Internal notes** are visible to MANAGER and ADMIN, plus their own author.
**Client accounts** share the message composer with everybody else but never
claim, reassign or change status. Their composer carries a single switch instead
of the staff's *Internal note* one: ticking **Escalate to Manager** routes the
message into a separate, private conversation (the task is flagged and its
priority raised to URGENT) that only MANAGER, ADMIN **and the client who
escalated** can read and reply to; associates and coordinators never see it and
it never appears in the regular thread. The escalation card on the task page is
display-only — it renders that conversation once it exists.

### Metrics visibility

`GET /api/dashboard/stats` scopes every counter to the caller's role:

| Caller      | `myXxx`      | `teamXxx` (associate workload) | `allXxx` (company-wide) | Breakdown charts |
|-------------|--------------|--------------------------------|-------------------------|------------------|
| CLIENT      | own customer's tasks | —                   | —                       | own customer's tasks |
| ASSOCIATE   | own tasks    | —                              | —                       | own tasks        |
| COORDINATOR | own tasks    | every task touching an ASSOCIATE | —                     | associate tasks  |
| MANAGER     | own tasks    | every task touching an ASSOCIATE | ✅                    | everything       |
| ADMIN       | own tasks    | every task touching an ASSOCIATE | ✅                    | everything       |

Counters arrive as `myXxx` / `teamXxx` / `allXxx` sets of `Open`, `InProgress`,
`Quality`, `Submitted` and `Total`, plus `statusBucket` / `priorityBucket`
breakdowns (label = enum name, rendered through `App.statusLabel`). The
dashboard prints the `tile-my-*` / `tile-all-*` tiles Open, In Progress, Quality
and Total; `/client-detail.html` adds Quality **and** Submitted tiles from
`ClientProfileDto.TaskStats`.

Client accounts are never the assignee, so their `myXxx` counters — and the
`/api/tasks/my-open` list beneath the tiles — key on `task.client` instead of
`task.assignedTo`. The total tile is relabelled **Total** for them.

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
answers `400` for an illegal jump such as `OPEN → QUALITY` — reach it through
`IN_PROGRESS` first (`IN_PROGRESS → QUALITY → SUBMITTED` are the forward moves).
Re-opening a `CLOSED` task is allowed by the domain and is restricted to
MANAGER / ADMIN by `TaskService`.

The review flow moves the status on its own as well: posting an associate
submission sets `QUALITY`, and answering one — or any COORDINATOR / MANAGER /
ADMIN reply while the task sits in `QUALITY` — sets `SUBMITTED`. Those two paths
deliberately bypass `canTransitionTo`, never touch a `CLOSED` task, and call
`task.touch()` so "last activity" stays truthful.

Every one of these moves goes through `Task.changeStatus`, which also stamps
`status_changed_at` — the moment from which that task stops appearing in
anybody's bell (see *Frontend notes*).

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
* Priority is deliberately binary: `NORMAL` and `URGENT`. One dropdown in the
  New-task dialog, two badge styles, two bars in the "Tasks by priority"
  chart, and `URGENT` is also what an escalation promotes a task to.
* The dashboard tables end with a **Last updated** column rendered through
  `App.formatRelative()` — `just now`, `5 minutes ago`, `3 hours ago`,
  `4 days ago` — with the exact timestamp in the tooltip. `/all-tasks.html`
  and `/my-tasks.html` instead show the full detail as two columns,
  **Created** and **Updated**, both absolute. `Task.touch()` is called from
  `TaskMessageService.addMessage` / `escalate`, so thread activity bumps the
  clock too (`@UpdateTimestamp` already covers every direct task edit).
* **All Tasks** (`/all-tasks.html`) lists the full backlog for every role with
  client-side filters (free-text search, client, agent, status) and a sortable
  column per field; rows that fail the filters are hidden, not re-fetched.
  For a client account the **Client** column and the matching filter are both
  dropped — every row already belongs to their own customer — and the server
  only ever returns that customer's tasks anyway.
* Client names appear as their own column in the task tables for staff; the
  column carries `data-hide-when-client` and is removed for client accounts
  (the renderers skip the body cell and size their empty state from
  `App.columnCount()`). Task detail shows
  a **Client details** card (contact, email, phone, notes) that the API only
  sends to MANAGER / ADMIN. Client sign-ins get a detail page without the
  status and assignment controls.
* The dashboard stacks **My open tasks** over a shared **All open tasks**
  backlog. The shared section also carries `data-hide-when-client`, so a
  client only ever sees their own work table plus the tiles.
* Task detail hosts one shared **Add a message** composer, placed *below* the
  regular **Conversation** thread so both mediums stay visible at once. The
  single switch under it is role-dependent: managers/admins get **Internal
  note**, client accounts get **Escalate to Manager**, and associates and
  coordinators get no switch at all. Ticking *Escalate to Manager* posts into
  the private escalation conversation instead of the shared thread. Beneath the
  composer sits the display-only **Escalation conversation** card, rendered for
  managers/admins and the escalating client once that conversation exists.
  Everybody else only sees the orange "Escalated" badge.
* Every page shares one nav bar. **My Tasks** carries `data-hide-when-client`
  and is dropped for client accounts (they are never assigned anything); the
  existing `data-role="MANAGER"` / `data-role="ADMIN"` entries work the same
  way. All of this is applied by `Auth.applyRoleVisibility()`.
* The header action row reads **user chip · bell · logout · theme toggle**.
  The **bell** opens a notifications dropdown fed by `GET /api/notifications` —
  tasks assigned to you, new thread messages on tasks you own or are assigned
  (clients: on their customer's tasks) and escalation messages. Unread count
  lives in `localStorage` (`admin++-notif-read`) keyed by stable ids
  (`assigned-7`, `message-42`, `escalation-43`), so nothing about read state is
  stored server side. Clicking an item marks it read and opens the task.
  The feed is recomputed on every read, and moving a task's status wipes
  everything reported about it before that instant — assignment, thread message
  or escalation, whoever was told — so those items simply stop being returned;
  anything that happens afterwards is news again.
* The footer on every authenticated page reads
  `Admin++ | Logged in as <name> (<role>)`, filled from the same
  `data-auth-footer-user` span by `Auth.renderHeader()`.
* `App.esc()` / `App.escMultiline()` are mandatory for any server value; this is
  the only XSS defence and it is applied everywhere.
* Desktop-first (≥1024px), as specified. The layout uses CSS grid/flex so it
  degrades reasonably on smaller screens, but mobile is out of scope for the MVP.

---

## 7. Configuration reference

| Property                | Default (env var override)                        |
|-------------------------|----------------------------------------------------|
| `spring.datasource.url`         | from `.env` / `TASKPORTAL_DB_URL` (required)         |
| `spring.datasource.username`    | from `.env` / `TASKPORTAL_DB_USERNAME` (required)    |
| `spring.datasource.password`    | from `.env` / `TASKPORTAL_DB_PASSWORD` (required)    |
| `spring.jpa.hibernate.ddl-auto` | `create` (drop + recreate on every start)           |
| `spring.jpa.open-in-view`       | `false`                                             |
| `server.port`                   | `8080` (`TASKPORTAL_PORT`)                          |
| `app.seed.enabled`              | `true`                                              |
| `app.seed.sample-tasks`         | `true`                                              |
| `app.seed.admin-password`       | `Admin@12345` (`TASKPORTAL_ADMIN_PASSWORD`)         |
| `app.seed.coordinator-password` | `Coordinator@12345` (`TASKPORTAL_COORDINATOR_PASSWORD`) |
| `app.seed.client-password`      | `Client@12345` (`TASKPORTAL_CLIENT_PASSWORD`)       |

> Database credentials live only in the git-ignored `.env` (local dev) or in
> real environment variables. Never commit them or bake them into an image;
> rotate the existing Neon key since it was previously committed.

---

## 8. Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## 9. Deliberately out of scope (MVP)

Email ingestion, file attachments, a points/gamification system, notifications,
task templates, pagination on the list endpoints, and a localisation layer.
