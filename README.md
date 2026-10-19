# DeliveryPulse — Project Delivery Risk Radar

DeliveryPulse is a locally run proof of concept that imports project task data from Excel/CSV, runs deterministic delivery-risk rules, and shows project- and portfolio-level dashboards and a weekly report.

- **Backend:** Java 21, Spring Boot 3.5.6, Spring Data JPA, Flyway, PostgreSQL, Apache POI 5.4.1, Apache Commons CSV 1.14.1, springdoc-openapi
- **Frontend:** Angular 22, TypeScript 6, Chart.js 4, Lucide icons, Vitest
- **Sign-in and access control:** Admin, Manager and Viewer roles with per-project access (section 15)
- **Scheduled jobs:** daily refresh and weekly report snapshots (section 14)
- **Optional:** AI insights via Google Gemini (REST) or Anthropic Claude (`anthropic-java` 2.34.0), off by default (section 12)
- **Optional:** read-only Jira Cloud sync, off by default (section 13)
- **Not included:** Planner integration, single sign-on (SSO)

Every API call needs a signed-in user. Before exposing it beyond your machine, also put it behind HTTPS and review the deployment settings.

---

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| Java JDK | 21 | `java -version` |
| Maven | none needed — use the included wrapper `backend\mvnw.cmd` (downloads Maven 3.9.16 on first use) | `.\mvnw.cmd -v` |
| Node.js | 22.22.3 or later in the Node 22 line | `node -v` |
| npm | 10.x | `npm -v` |
| PostgreSQL | 14 or later | `psql --version` |

All commands below are PowerShell, run from the repository root unless stated otherwise.

---

## 2. Database setup (one time)

Start the PostgreSQL service, then connect as an administrator (for example `postgres`):

```powershell
& "C:\Program Files\PostgreSQL\14\bin\psql.exe" -h localhost -U postgres
```

Create the application role and database. Replace the password with your own local secret:

```sql
CREATE ROLE deliverypulse_app WITH LOGIN PASSWORD 'replace-with-a-local-password';
CREATE DATABASE deliverypulse OWNER deliverypulse_app;
```

That is all the manual SQL needed. Flyway creates and upgrades the tables when the backend starts (migrations `V1`–`V4` in `backend/src/main/resources/db/migration/`), and Hibernate then *validates* the schema — it never creates or alters tables itself. Do not edit an applied migration; add a new `V5__...sql` instead.

To start over with an empty database (destroys all imported data):

```sql
DROP DATABASE deliverypulse;
CREATE DATABASE deliverypulse OWNER deliverypulse_app;
```

---

## 3. Configuration

Copy `.env.example` to `.env` in the repository root and fill in your values. The backend imports that file on startup (`spring.config.import` in `application.yml`), whether you start it from `backend/` or the root, so you don't need to set anything in the terminal. `.env` is git-ignored; never commit it.

```powershell
Copy-Item .env.example .env   # then edit DB_PASSWORD (required) and APP_TIME_ZONE (recommended)
```

A variable set in the terminal (`$env:NAME = "..."`) or an IDE run configuration overrides the same key in `.env`. The file is read as Java `.properties`, so write a backslash in a value as `\\`. Tests ignore your `.env` settings: the `test` profile pins its own database, time zone, CORS origin and AI settings.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `deliverypulse` | Database name |
| `DB_USERNAME` | `deliverypulse_app` | PostgreSQL login |
| `DB_PASSWORD` | empty | PostgreSQL password — must be set |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `FRONTEND_ORIGIN` | `http://localhost:4200` | Browser origin allowed by CORS on `/api/**` when the frontend calls the API directly (not via the dev proxy) |
| `APP_TIME_ZONE` | `UTC` | Zone used to decide "today" when no `analysisDate` is given (overdue checks, weekly window). Use an IANA ID such as `Asia/Kolkata`. Stored/returned timestamps are always UTC instants. |

The frontend calls the API at the relative path `/api` (`frontend/src/environments/environment*.ts`). In development, `frontend/proxy.conf.mjs` forwards `/api` and `/actuator` to `http://localhost:8080` (override with `$env:API_PROXY_TARGET`). The proxy strips the browser `Origin` header, so the dev server works on any host/port without CORS changes. For a deployed build, serve the frontend and route `/api` (and `/actuator/health`, used by the sidebar's API-connection indicator) to the Spring service on the same origin, or set `FRONTEND_ORIGIN` to the exact frontend origin.

---

## 4. Start the application

**Terminal 1 — backend**

```powershell
cd backend
.\mvnw.cmd spring-boot:run   # reads ..\.env (section 3)
```

Wait for `Started DeliveryPulseApplication`, then check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health   # -> status: UP
```

**Terminal 2 — frontend**

```powershell
cd frontend
npm install        # first time only
npm start
```

Open <http://localhost:4200>. Routes: `/dashboard`, `/upload`, `/projects`, `/projects/{id}`, `/risks`, `/reports`.

**API documentation:** Swagger UI at <http://localhost:8080/swagger-ui.html>, OpenAPI JSON at <http://localhost:8080/v3/api-docs>.

Alternatively, run the packaged JAR:

```powershell
cd backend
.\mvnw.cmd clean package
java -jar target\deliverypulse-api-0.1.0-SNAPSHOT.jar
```

---

## 5. Tests and builds

```powershell
cd backend
.\mvnw.cmd test            # 90 JUnit tests; in-memory H2, no PostgreSQL or AI API key needed

cd ..\frontend
npm test -- --watch=false  # 31 Vitest tests
npm run build              # production build -> frontend\dist\frontend
```

The backend tests use H2 in PostgreSQL mode, so they do not prove the PostgreSQL migrations. To check those, start the backend against PostgreSQL and look for `Successfully applied 4 migrations` (first run) or `Schema "public" is up to date` in the log.

---

## 6. Sample workbook

The demo workbook is `DeliveryPulse_Multi_Project_Sample.xlsx` (fictional data). It is **not** checked into the repository — keep it somewhere local, for example your Downloads folder. It contains:

| Sheet | Tasks | ID prefix |
| --- | --- | --- |
| ECommerce | 8 | EC-101…108 |
| CRM | 6 | CRM-201…206 |
| MobileApp | 6 | MA-301…306 |
| DataPlatform | 5 | DP-401…405 |
| Read Me | — | skipped (instructions) |

Expected import result: **4 projects, 25 tasks, 19 task-to-task dependencies, `Read Me` skipped, 0 warnings.**

If you do not have that file, generate a structurally equivalent synthetic workbook (same sheet names and task counts, simpler data):

```powershell
cd backend
.\mvnw.cmd -Dtest=SampleWorkbookGeneratorTest test
# -> backend\target\sample-data\deliverypulse-portfolio.xlsx
```

Upload from the UI (**Upload data → Browse files → Import file**) or from the command line:

```powershell
curl.exe -X POST http://localhost:8080/api/uploads/excel -F "file=@$env:USERPROFILE\Downloads\DeliveryPulse_Multi_Project_Sample.xlsx"
```

### Release demo (milestones, defects, history)

[`sample-data/release-demo`](sample-data/release-demo) holds three weekly exports of one project, `ReleaseDemo` (7 tasks, three milestones, two bugs). Upload them in order: `week1` as a normal import, then `week2` and `week3` with **Update existing projects** ticked.

```powershell
curl.exe -X POST http://localhost:8080/api/uploads/csv -F "file=@sample-data\release-demo\week1\ReleaseDemo.csv"
curl.exe -X POST "http://localhost:8080/api/uploads/csv?update=true" -F "file=@sample-data\release-demo\week2\ReleaseDemo.csv"
curl.exe -X POST "http://localhost:8080/api/uploads/csv?update=true" -F "file=@sample-data\release-demo\week3\ReleaseDemo.csv"
```

Between the weeks, RD-2 goes Blocked → In Progress → Blocked, the RD-3 crash bug is fixed, and bug RD-7 is added. Analysed with `analysisDate=2026-10-19`, the project has **7 active findings (1 Critical, 6 High)**:

- **Milestone missed: Alpha** (Critical): its date was 2026-10-16 and RD-1 is still open.
- **Milestone at risk: Beta** (High, 11 days left): RD-2 is blocked and RD-4 is due after the milestone.
- **Recurring blocker: RD-2**: blocked in week 1 and again in week 3.
- **Open high-priority defect: RD-7**.
- The overdue RD-1, blocked RD-2 and RD-5's dependency on RD-2 come from the existing rules.

The Milestones table on the project page shows Alpha Missed, Beta At Risk and GA On Track, with 1 open defect. The weekly report shows the week's movement. Because all three files are uploaded on the same day, it counts them as one week. `UploadControllerTests.releaseDemoWeeksProduceTheDocumentedFindings` checks these numbers.

### Workbook format

- One worksheet per project; the worksheet name becomes the project name. Sheets named `Read Me`, `Instructions`, `How To`, `Guide` or `About` are skipped, and so are hidden worksheets (for example dropdown-list helper sheets), with a warning. Sheet names are trimmed.
- A CSV upload is one project named after the file (for example `Payments.csv` → `Payments`).
- Columns (matching is case/space/punctuation-insensitive): **Task ID**, **Task Name**, **Priority**, **Status** (required); Team/Module, Assignee, Start Date, Due Date, Blocker / Dependency, Description, Type, Milestone, Milestone Date (optional). Unknown columns are ignored with a warning.
- Priority: Low, Medium, High, Critical. Status: Not Started, In Progress, Blocked, Done (also Todo, Active, On Hold, Completed, …).
- Formula cells use the value Excel saved with the file (the formula is not recalculated).
- Dates: Excel date cells, or text in `yyyy-MM-dd`, `M/d/yyyy`, `M/d/yy`, `d-MMM-yyyy`, `yyyy/M/d`.
- `Type`: Task, Story, Feature, Subtask or Epic (all stored as Task), or Bug/Defect (stored as Defect). Blank means Task. Also read from `Issue Type`.
- `Milestone` (also `Release`, `Fix Version`) and `Milestone Date` (also `Release Date`, `Target Date`): group tasks into milestones. Give the date on at least one row of a milestone and every row of that milestone gets it; two different dates for one milestone, or a date without a milestone name, is a validation error.
- `Blocker / Dependency`: task IDs from the same project, separated by semicolons, commas or line breaks, become dependency records (a repeated ID creates one record); any other text is kept as descriptive blocker text.

### Upload outcomes

| Situation | HTTP | What the user sees |
| --- | --- | --- |
| Valid file | `201` | Import summary: projects, tasks, skipped sheets, warnings |
| Row/column validation errors | `422` | Every error with worksheet, row, column and message; **nothing is imported** |
| Project name already exists (e.g. same workbook uploaded twice) | `409` | "No records were imported because one or more project names already exist." Nothing is imported or duplicated |
| Project name already exists, uploaded with **Update existing projects** (`update=true`) | `201` | Tasks are updated by Task ID, new tasks are added, and each status change is recorded. Tasks missing from the file are left unchanged, with a `TASK_NOT_IN_UPLOAD` warning |
| Corrupt/non-Excel content | `400` | "The Excel file is malformed or uses an unsupported workbook format." |
| Wrong file extension | `415` | Supported-type message |
| Larger than 10 MB | `413` | Size-limit message |

Uploads are atomic: one bad row rejects the whole file. Project names are unique case-insensitively (enforced by a database index). There is no API to delete a project; to reload the demo data from scratch, reset the database (section 2).

### Status history

Every import records the status each task enters (`task_status_history`): the first time a task appears, and every time a later update upload changes its status. Upload the latest export with **Update existing projects** regularly (for example weekly). The recurring-blocker rule and the weekly report's activity section are built from this history. A change is timestamped when it is uploaded, so changes that happened and reverted between two uploads are not seen. On upgrade, existing tasks get one history row each with their current status.

---

## 7. REST API summary

| Method & path | Purpose |
| --- | --- |
| `POST /api/uploads/excel?update=false` | Import `.xlsx`/`.xls` (multipart field `file`); `update=true` updates projects that already exist |
| `POST /api/uploads/csv?update=false` | Import one `.csv` project; `update=true` as above |
| `POST /api/auth/login` | Sign in with `{username, password}`; returns a bearer token. Every other `/api` call needs `Authorization: Bearer <token>` |
| `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/password` | End the session, current user and their project ids, change your own password |
| `GET/POST /api/users`, `PUT /api/users/{id}` | Admin: list, create and update users (role, active, password, project access) |
| `GET /api/jobs`, `POST /api/jobs/{refresh\|weekly-report}/run` | Job schedules, next and last runs; admin: run a job now |
| `GET /api/reports/weekly/snapshots`, `GET /api/reports/weekly/snapshots/{id}` | Admin: saved weekly reports (same shape as the live report) |
| `GET /api/projects` | Projects the caller may see, with task metrics and risk counts by severity (sorted by name) |
| `GET /api/projects/{id}` | One project's metrics |
| `GET /api/projects/{id}/tasks` | Paged tasks; filters `status`, `priority`, `assignee`, `overdue`, `analysisDate` (defaults to the project's last analysis date, or today if never analyzed); `page`, `size` (1–100), `sortBy`, `sortDirection` |
| `POST /api/projects/{id}/analyze?analysisDate=yyyy-MM-dd` | Run the risk rules (date optional; defaults to today in `APP_TIME_ZONE`) |
| `GET /api/projects/{id}/risks` | Latest active findings with evidence and supporting task IDs, most severe first |
| `GET /api/dashboard/portfolio` | Portfolio totals and per-project summaries |
| `GET /api/reports/weekly` | Seven-day window ending today: the current snapshot plus `activity` and `activityByProject` (tasks added, completed, newly blocked, unblocked, defects opened and closed, from the status history) |
| `GET /api/ai/status` | Whether AI insights are enabled, configured and authorized (never returns the key) |
| `GET /api/integrations/jira/status` | Whether Jira sync is configured, the site URL and project keys (never the email or token) |
| `POST /api/integrations/jira/sync` | Import the configured Jira projects as an update (same response as an upload) |
| `POST /api/projects/{id}/insights` | Executive summary, top-risk explanations, actions and a weekly status draft — AI-generated when available, deterministic otherwise (section 12) |
| `POST /api/portfolio/insights` | The same four sections across all projects (`projectId` is `null`, `projectName` is `All projects`) |

Access rules: Viewers can read; Managers can also upload, analyze and generate insights; Admins can do everything, including Jira sync, jobs, snapshots and users. Managers and Viewers see only their assigned projects, and any other project answers `404`. Missing or expired sign-in answers `401`; a role that may not do something answers `403`.

Errors use RFC 7807 Problem Details (`{"status":404,"title":"Not Found","detail":"Project not found",...}`). Upload validation responses (`409`/`422`) return the import summary with an `errors` array instead.

Example:

```powershell
curl.exe "http://localhost:8080/api/projects/<project-id>/tasks?status=IN_PROGRESS&overdue=true&analysisDate=2026-10-15"
```

---

## 8. Risk rules

Analysis is deterministic and evidence-based. Every finding has a severity, a certainty (`CONFIRMED`, `POTENTIAL` or `DATA_QUALITY`), an explanation, evidence, supporting task IDs and a recommended action.

| Finding | Severity | Certainty |
| --- | --- | --- |
| Incomplete task is past its due date | High | Confirmed |
| Task status is Blocked | High | Confirmed |
| Descriptive blocker text without Blocked status (task not Done) | High | Potential |
| Critical-priority task is overdue or explicitly blocked | Critical | Confirmed |
| Critical-priority task (not Done) has descriptive blocker text only | Critical | Potential |
| Open dependency on an incomplete prerequisite | Medium | Potential |
| Open dependency on an overdue or blocked prerequisite | High | Potential |
| Missing due date/assignee, start after due, unresolved task-ID reference | Low | Data quality |
| Milestone date has passed and some of its tasks are not Done (`DELIVERY_RISK`) | Critical | Confirmed |
| Upcoming milestone has blocked or overdue tasks, or tasks due after the milestone date (`DELIVERY_RISK`) | High within 14 days, else Medium | Potential |
| Open Critical/High priority defect (`DEFECT_RISK`) | Same as priority | Confirmed |
| Task (not Done) has entered Blocked 2+ times in its status history (`RECURRING_BLOCKER`) | High; Critical at 3+ | Confirmed |

"Overdue" means due **strictly before** the analysis date and not Done. A task due on the analysis date is not overdue. Re-running analysis updates existing findings (by stable fingerprint) instead of duplicating them, resolves findings whose condition no longer holds, and reopens findings when the condition returns. Milestone checks need a `Milestone Date`; milestones without one are listed on the project page as "No Date" but produce no finding. The project risk response also lists each milestone's progress and status (`COMPLETE`, `MISSED`, `AT_RISK`, `ON_TRACK`, `NO_DATE`) and the open defect count.

Risk counts are counts of **findings**, not of tasks, and there is no weighted score. One task can produce several findings: for example EC-102 (Critical, Blocked, overdue) produces an overdue, a blocked and a critical-at-risk finding. Findings are listed most severe first.

**Reference result** for the sample workbook analysed with `analysisDate=2026-10-15`: 5 overdue tasks (EC-101, EC-102, CRM-202, MA-303, DP-401), 4 blocked (EC-102, CRM-204, MA-303, DP-404), and 30 active findings — 2 Critical, 18 High, 10 Medium, 0 Low (ECommerce 11, CRM 7, MobileApp 6, DataPlatform 6). Because the overdue rules depend on the date, a different analysis date gives different numbers.

---

## 9. Live demonstration script

1. Reset the database if it already contains the sample projects (section 2).
2. Start the backend (it reads `DB_PASSWORD` and `APP_TIME_ZONE` from `.env`), then start the frontend (section 4).
3. Open <http://localhost:4200> → sign in as the admin (section 15) → the **Dashboard** shows the empty state.
4. **Upload data** → choose `DeliveryPulse_Multi_Project_Sample.xlsx` → **Import file** → summary shows 4 projects, 25 tasks, `Read Me` skipped.
5. **Projects** → four projects (CRM, DataPlatform, ECommerce, MobileApp), each "Not analyzed".
6. Open **ECommerce** → **Run analysis** → 11 active risks, including the Critical "EC-102 Payment Gateway Integration" (blocked and overdue). Each risk card shows its evidence and supporting task IDs, which can be checked against the task table. For the other projects, click **Analyze all projects** on the **Dashboard** or **Risk analysis** page (it analyzes each project in turn and shows progress).
7. **Dashboard** → totals (4 projects, 25 tasks, 5 overdue, 4 blocked, 30 risks) and the severity chart.
8. **Reports** → weekly report; **Print report** or **Download CSV**.
9. **Upload data** → upload the same workbook again → rejected with "project names already exist"; nothing is duplicated.
10. Upload an invalid file (for example a CSV with `Priority = Urgentish` and a malformed date) → row-level validation errors, nothing imported.

The numbers in steps 6–7 assume the demo runs on 2026-10-15. On a different day the overdue-based counts will change, because analysis uses today's date.

---

## 10. Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `mvn` is not recognized | Use `.\mvnw.cmd` in `backend` (no Maven install needed). |
| Backend fails with `password authentication failed` or `no password supplied` | `DB_PASSWORD` is missing from `.env` (or `.env` is not in the repository root), a terminal variable is overriding it, or the role password differs. Reset it as an admin with `ALTER ROLE deliverypulse_app WITH PASSWORD '...';`. |
| `Connection refused` on port 5432 | PostgreSQL service is stopped (`Get-Service postgresql*`, then `Start-Service <name>`), or `DB_HOST`/`DB_PORT` are wrong. |
| Flyway `Validate failed` / Hibernate `Schema-validation` errors | An applied migration was edited, or tables were changed by hand. Restore the migration file, or reset the database in development. |
| `Port 8080 was already in use` | Stop the other process (`netstat -ano \| findstr :8080`) or set `SERVER_PORT` and `API_PROXY_TARGET` to match. |
| UI shows "Cannot reach the DeliveryPulse API" | Backend not running, or the dev server was started without the proxy. Use `npm start`, which applies `proxy.conf.mjs`. |
| Uploads or "Run analysis" return 403, but pages load | CORS rejected a request that was not proxied. Use the dev proxy (`npm start`), or set `FRONTEND_ORIGIN` to the exact scheme://host:port the browser uses and restart the backend. |
| Dev server on 4200 stops responding | Stop it (Ctrl+C) and run `npm start` again. To use another port: `npx ng serve --port 4300`. |
| Upload returns 409 | Those project names already exist. Tick **Update existing projects** (or pass `update=true`) to update them, or reset the database to start over. |
| Overdue counts look off by one day | Set `APP_TIME_ZONE` to your local zone, or pass `analysisDate` explicitly. |

---

## 11. Known limitations

- Users are local to DeliveryPulse (no SSO or LDAP). Sign-in lockout and job run history are kept in memory and reset when the backend restarts.
- Jira sync reads only Jira Cloud. It runs with the daily refresh job, or when an admin clicks **Sync now**.
- Saved weekly report snapshots are admin-only, because a snapshot contains every project.
- No project delete API; resetting the database is the only way to remove a project. Update uploads never remove tasks that are missing from the file.
- No import-history endpoint (import runs are recorded in `import_runs` but not exposed); no dependency-graph endpoint (project detail shows the original blocker text and the risk supporting task IDs).
- Each project's metrics and risks are as of its own last analysis date. The portfolio, dashboard and weekly report add those up while labelling the snapshot with today's date, so a project analyzed days ago contributes its older numbers. Re-run analysis on each project for a same-day picture.
- `import_runs` records only successful imports; rejected uploads are logged but not stored.
- The weekly report's activity counts only what update uploads recorded. Without regular update uploads it shows tasks as added on their first import and nothing else. Status history covers status only, not due dates, priorities or blocker text.
- Backend automated tests run on H2. PostgreSQL behaviour was verified manually (section 5).
- AI insights are generated on demand and not stored; each click is a new provider call.

---

## 12. Optional AI insights

The project detail page has an **Insights** panel, and the Dashboard has a **Portfolio briefing** that covers every project at once. Each produces four things:

- an executive summary
- explanations of the highest-priority risks
- recommended actions
- a draft weekly status report (for the portfolio: one line per project, worst first)

The portfolio briefing sends each project's metrics, its five highest-priority risks and the tasks those risks cite. Top risks are chosen so that each project's worst risk is considered before any project's second, and a project that has not been analyzed is reported as unknown, not healthy. Without AI, the same sections are built from the stored findings.

Every statement is labelled **FACT** (stated by the records, with supporting task IDs) or **ASSUMPTION** (an inference).

AI is **off by default**. The panel then builds the same sections from the deterministic findings ("Deterministic summary"), and no data leaves the server. The upload → analyze → dashboard → report workflow never depends on AI.

### Turning it on

AI runs only when **all three** of the switch, a key and the data-sharing authorization are set. Put them in `.env` (git-ignored) and restart the backend.

**Google Gemini:**
```
AI_INSIGHTS_ENABLED=true
AI_PROVIDER=gemini
AI_API_KEY=<your Gemini API key from https://aistudio.google.com/apikey>
AI_MODEL=gemini-flash-latest   # Google's alias for the current Flash model
AI_EXTERNAL_DATA_SHARING_AUTHORIZED=true   # confirms you may send this project data to Google
```

**Anthropic Claude:** `AI_PROVIDER=anthropic`, `AI_API_KEY=<key>` (the older `ANTHROPIC_API_KEY` still works), `AI_MODEL=claude-opus-5-5`.

Restart the backend, then check `GET /api/ai/status`: `"available": true` means it is ready.

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_INSIGHTS_ENABLED` | `false` | Master switch |
| `AI_PROVIDER` | `anthropic` | `gemini` or `anthropic` |
| `AI_API_KEY` | empty | Provider credential (never logged or returned). `ANTHROPIC_API_KEY` is read when this is empty |
| `AI_EXTERNAL_DATA_SHARING_AUTHORIZED` | `false` | Explicit authorization to send project data to the external provider |
| `AI_MODEL` | provider default | `gemini-flash-latest` for Gemini (an alias that follows Google's current Flash model; pin a version such as `gemini-3.8-flash` for repeatable results), `claude-opus-5-5` for Anthropic |
| `AI_TIMEOUT` | `60s` | Per-request timeout |
| `AI_MAX_RETRIES` | `2` | Retries for 429/5xx errors (Gemini: 429/500/503) |
| `AI_MAX_OUTPUT_TOKENS` | `16000` | Output cap per request |
| `AI_SERVER_SIDE_FALLBACKS` | `true` | If the model declines a request, the provider retries it on another model (sends the `server-side-fallback-2026-07-01` beta header) |
| `AI_INCLUDE_ASSIGNEE_NAMES` | `false` | Send real assignee names (otherwise "Person 1", "Person 2", …) |
| `AI_INCLUDE_TASK_DESCRIPTIONS` | `false` | Send task descriptions |
| `AI_MAX_TASKS` / `AI_MAX_RISKS` / `AI_MAX_EXPLAINED_RISKS` / `AI_MAX_TEXT_LENGTH` | `200` / `10` / `5` / `500` | Bounds on what is sent; longer text fields are cut and marked `[truncated]` |

**To disable:** unset `AI_INSIGHTS_ENABLED` (or set it to `false`) and restart. Removing the key or the authorization flag also disables it.

### How it works and what is guaranteed

- **Read-only.** The backend reads one project's tasks and its already-stored risk findings. The model never queries the database and never sees other projects. Nothing it returns is written back: severity, certainty, task status and deadlines are never changed by AI (covered by a test that compares stored data before and after).
- **Deterministic findings stay authoritative.** Risks are sent ranked by the rules engine (Critical first). Severity, category and certainty shown in the panel always come from the engine, not from the model.
- **Untrusted input.** Task names, blocker text and descriptions are sent as escaped JSON inside a `<project_data>` block. The system prompt tells the model that this content is data, never instructions. Text such as `</project_data>` in a spreadsheet cannot break out of the block (covered by tests).
- **Validated output.** The model must return a fixed JSON schema (structured outputs). The backend then checks it:
  - Statements that cite task IDs or risk refs missing from the data are removed.
  - A FACT with no supporting task ID is relabelled ASSUMPTION.
  - Overlong or excess items are dropped.
  - Everything removed is listed under "removed or relabelled by validation" in the panel.
  - If no valid executive summary remains, the whole response is rejected.
- **Graceful failure.** On a timeout, rate limit (429), overload (529/5xx), exhausted credit (402), rejected key (401/403), refusal, truncated or malformed output, or a validation failure, the panel shows the deterministic summary with the reason. The API still returns HTTP 200. Logs record only the failure category, model, token counts and timing — never prompts, responses or the key.
- **Analysis first.** Until the project has been analyzed, the provider is not called (`ANALYSIS_REQUIRED`).

### Privacy

When AI is enabled and authorized, each request sends to Anthropic: the project name, metrics, and the task ID, name, team, priority, status, dates and blocker text of up to `AI_MAX_TASKS` tasks, plus the stored risk findings. Assignee names and task descriptions are excluded unless you opt in. Only set `AI_EXTERNAL_DATA_SHARING_AUTHORIZED=true` if your organisation permits sending that data to Anthropic under your account's data-retention terms. Leave AI off for confidential projects.

### Cost

Pricing for `claude-opus-5-5`: $4 per million input tokens, $20 per million output tokens. Thinking runs at the model's default effort (`medium`) and is billed as output.

Estimate for one sample-workbook project (about 8 tasks and 10 risks, not measured against the live API): a few thousand input tokens plus a few thousand output tokens, so **roughly $0.05–$0.15 per click**. Larger projects cost proportionally more. Generation happens only when someone clicks the button; results are not cached. Each successful call logs `inputTokens`/`outputTokens`, so you can track actual spend.

### Providers

The service depends only on `com.deliverypulse.insights.llm.LlmClient`. `AnthropicLlmClient` uses the Anthropic SDK with structured outputs. `GeminiLlmClient` calls `models/{model}:generateContent` over plain HTTPS with a JSON response schema built from the response records (`JsonSchemas`). The prompt, validation and deterministic fallback are the same for both. To add another provider, implement `LlmClient` and select it in `LlmConfiguration`.

**Gemini privacy note:** on Google's free tier, prompts may be used to improve Google's products. Use the free tier only with fictional or non-confidential data, such as the demo projects.

### Tests

All AI tests use mocked or faked providers; no API key or paid access is needed:

- **Service:** every availability state and every failure type.
- **Validation:** hallucinated task IDs and risk refs, FACT relabelling, size caps.
- **Prompt:** injection containment and privacy defaults.
- **Provider:** the real SDK client against a local fake Messages API (429/529/500/402/401, refusal, truncation, malformed JSON, timeout).
- **Endpoints:** both configurations through the Spring context.
- **Panel:** Angular component tests.

A live call to Anthropic has not been exercised because no key is configured in this environment.


---

## 13. Optional Jira sync

DeliveryPulse can read issues from **Jira Cloud** and import them like an upload with **Update existing projects** ticked: each Jira project becomes a DeliveryPulse project named after it, issues are matched by key, and every sync records status changes. Milestones, defects, recurring blockers and the weekly activity then work for Jira data too. The sync only reads from Jira; it never writes to it.

### Turning it on

1. Create an API token at <https://id.atlassian.com/manage-profile/security/api-tokens>.
2. Add to `.env` and restart the backend:
   ```
   JIRA_BASE_URL=https://yourname.atlassian.net
   JIRA_EMAIL=the-email-you-log-in-with
   JIRA_API_TOKEN=...
   JIRA_PROJECT_KEYS=PAY,MOB,DATA,CRMX,OPS   # comma-separated
   ```
3. **Upload data** now shows a **Sync from Jira** panel. Click **Sync now**, then **Run analysis** on the synced projects.

Optional: `JIRA_BLOCKED_LABEL` (default `blocked`), `JIRA_TIMEOUT` (default `30s`), `JIRA_MAX_ISSUES` (default `5000`; extra issues are skipped with a warning).

### Field mapping

| DeliveryPulse | Jira |
| --- | --- |
| Project | The Jira project (its name) |
| Task ID / Task Name | Issue key / Summary |
| Type | Issue type `Bug` or `Defect` → Defect; anything else → Task |
| Priority | Highest/Blocker/Critical → Critical, High/Major → High, Medium (or none) → Medium, Low/Lowest/Minor/Trivial → Low; others → Medium with a warning |
| Status | Done category → Done; else the blocked label or a status named `Blocked` → Blocked; else In Progress / To Do by status category |
| Team/Module, Assignee | First component, assignee display name |
| Due date | Due date (Start date is not read, because its field id differs per site) |
| Milestone + date | Fix version with the earliest release date |
| Dependencies | "is blocked by" links. Blockers in the same project become dependency records; all blocker keys are kept as blocker text |

Descriptions are not imported. Issues deleted in Jira stay in DeliveryPulse with a `TASK_NOT_IN_UPLOAD` warning, as with uploads. If a Jira project has the same name as an uploaded project, the sync updates that project.

### Demo data in your own Jira

[`scripts/jira-demo.mjs`](scripts/jira-demo.mjs) builds a fictional five-project portfolio (34 issues) in your Jira site through the REST API. The data is in [`scripts/jira-demo-data.mjs`](scripts/jira-demo-data.mjs), and dates are relative to the day you run it, so the demo never goes stale. Between them, the projects trigger every rule:

| Project | Story | Shows |
| --- | --- | --- |
| `PAY` Payments Revamp | Launch in 10 days is in trouble | Blocked Critical task that recurs, overdue task, Critical/High bugs, dependency on a blocked and an overdue task, task due after the milestone, a completed Pilot release |
| `MOB` Mobile App 3.0 | Beta date has passed | Missed milestone, overdue Critical bug (fixed in week 3), overdue story, GA at risk (Medium) once Offline mode is blocked |
| `DATA` Data Platform | Healthy | Completed Q3 release, on-track Q4 release, a Low bug that closes |
| `CRMX` CRM Migration | Messy data | Missing due dates and assignees, a release without a date ("No Date"), a link to an issue in another project |
| `OPS` Ops & Infra | Dependency chain | Prerequisite overdue (High) or incomplete (Medium), recurring Critical blocker, a new Critical bug in week 3, task due after the milestone |

Set `JIRA_PROJECT_KEYS=PAY,MOB,DATA,CRMX,OPS`, then run it one week at a time and sync after each:

```powershell
node scripts/jira-demo.mjs week1   # 5 projects with releases, components and 31 issues
# Sync now in DeliveryPulse
node scripts/jira-demo.mjs week2   # PAY-1 and OPS-4 unblocked, work finished, bugs PAY-8 and MOB-7 added
# Sync now
node scripts/jira-demo.mjs week3   # PAY-1 and OPS-4 blocked again, MOB-2 fixed, bug OPS-7 added
# Sync now, then Analyze all projects
```

It reads the same `.env`, needs Node 22 (already required by the frontend), and only touches these five projects. Reruns are safe: existing demo issues are found by their `dp-<ref>` label. If your site cannot create projects through the API, create company-managed Kanban software projects with those keys and names yourself and run `week1` again. If a field such as priority is hidden on a project's create screen, the script creates the issue without it and says so.

Use only fictional data in a personal Jira site. Don't copy real company projects there without permission.

### Tests

The client is tested against a mocked HTTP server (paging, Basic auth, query, error handling, key validation), the field mapping against a recorded-style search response, and the sync end to end through the Spring context with only the Jira HTTP call mocked. The seeding script and the sync have not yet been run against a live Jira site.

---

## 14. Scheduled jobs

Two jobs run inside the backend, in `APP_TIME_ZONE`:

| Job | Default schedule | What it does |
| --- | --- | --- |
| `refresh` | Every day 06:00 (`SCHEDULE_REFRESH_CRON=0 0 6 * * *`) | Jira sync if Jira is configured, then analysis of every project as of today |
| `weekly-report` | Monday 07:00 (`SCHEDULE_WEEKLY_REPORT_CRON=0 0 7 * * MON`) | Saves the weekly report as a snapshot (`weekly_report_snapshots`) |

Cron fields are `second minute hour day-of-month month day-of-week`. `SCHEDULE_ENABLED=false` turns the timers off; admins can still run each job from the **Automation** panel on **Upload data**, which also shows the next run and the last result. On **Reports**, admins can pick a saved report from the **Report** list to reopen an earlier week exactly as it was.

If the backend is not running at the scheduled time, that run is skipped (no catch-up). Last-run results are kept in memory.

---

## 15. Users and access

| Role | Can see | Can do |
| --- | --- | --- |
| Admin | Every project | Everything, including users, Jira sync, jobs and saved reports |
| Manager | Assigned projects | Upload (new projects become theirs), update their projects, run analysis, generate insights |
| Viewer | Assigned projects | Read only |

**First admin:** when there are no users, the backend creates `APP_ADMIN_USERNAME` (default `admin`) with `APP_ADMIN_PASSWORD`. If that is empty, it generates a password and writes it **once** to the backend log (`Created the first admin user ... with generated password`). Sign in and change it on the **Users** page.

**Users page** (admins): create users, set role and status, reset passwords and tick the projects a Manager or Viewer can see. Disabling a user or resetting their password signs them out.

**Sessions:** signing in returns a random bearer token that lasts `APP_SESSION_HOURS` (default 12). The database stores only a SHA-256 hash of it. The browser keeps it in session storage, so closing the tab signs you out. Passwords are stored with BCrypt. Five wrong passwords lock a username for five minutes. The API answers `404` for projects outside your access, so their existence is not revealed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_ADMIN_USERNAME` | `admin` | First admin's username |
| `APP_ADMIN_PASSWORD` | empty (generated) | First admin's password; used only when no users exist |
| `APP_SESSION_HOURS` | `12` | Session length |
| `APP_ADMIN_RESET` | `false` | Break-glass password reset for `APP_ADMIN_USERNAME` (below) |

### Forgot the admin password

Another admin can set a new password on the **Users** page, so it is worth having a second admin. If no admin can sign in:

1. In `.env`, set `APP_ADMIN_PASSWORD=<new password, at least 8 characters>` and `APP_ADMIN_RESET=true`.
2. Restart the backend. On startup it sets that password on `APP_ADMIN_USERNAME` (default `admin`), makes the account an active Admin, signs it out everywhere and logs `APP_ADMIN_RESET: reset the password of admin user ...`. The password itself is never logged. If the user does not exist, it is created.
3. Sign in, then **set `APP_ADMIN_RESET=false` (or remove it) and restart**. While it is on, every restart resets the password again.

Only someone who can edit the server's `.env` and restart it can do this. If the password is missing or shorter than 8 characters, the backend logs an error and changes nothing.
