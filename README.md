# DeliveryPulse — Project Delivery Risk Radar

DeliveryPulse is a locally run proof of concept that imports project task data from Excel/CSV, runs deterministic delivery-risk rules, and shows project- and portfolio-level dashboards and a weekly report.

- **Backend:** Java 21, Spring Boot 3.5.6, Spring Data JPA, Flyway, PostgreSQL, Apache POI 5.4.1, Apache Commons CSV 1.14.1, springdoc-openapi
- **Frontend:** Angular 22, TypeScript 6, Chart.js 4, Lucide icons, Vitest
- **Optional:** AI insights via the Anthropic Java SDK (`anthropic-java` 2.34.0), off by default — see section 12
- **Not included:** authentication, Jira/Planner integration

The API is intended for trusted local use. Do not expose it publicly without adding authentication, authorization and deployment security controls.

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

The backend reads environment variables. Spring Boot does **not** read `.env` files; `.env.example` only documents the names. Set them in the PowerShell session that starts the backend (or in your IDE run configuration). Never commit real credentials.

```powershell
$env:DB_PASSWORD   = "replace-with-a-local-password"   # required
$env:APP_TIME_ZONE = "Asia/Kolkata"                     # recommended: your local zone
```

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
$env:DB_PASSWORD = "replace-with-a-local-password"
$env:APP_TIME_ZONE = "Asia/Kolkata"
.\mvnw.cmd spring-boot:run
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
.\mvnw.cmd test            # 81 JUnit tests; in-memory H2, no PostgreSQL or AI API key needed

cd ..\frontend
npm test -- --watch=false  # 20 Vitest tests
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

### Workbook format

- One worksheet per project; the worksheet name becomes the project name. Sheets named `Read Me`, `Instructions`, `How To`, `Guide` or `About` are skipped, and so are hidden worksheets (for example dropdown-list helper sheets), with a warning. Sheet names are trimmed.
- A CSV upload is one project named after the file (for example `Payments.csv` → `Payments`).
- Columns (matching is case/space/punctuation-insensitive): **Task ID**, **Task Name**, **Priority**, **Status** (required); Team/Module, Assignee, Start Date, Due Date, Blocker / Dependency, Description (optional). Unknown columns are ignored with a warning.
- Priority: Low, Medium, High, Critical. Status: Not Started, In Progress, Blocked, Done (also Todo, Active, On Hold, Completed, …).
- Formula cells use the value Excel saved with the file (the formula is not recalculated).
- Dates: Excel date cells, or text in `yyyy-MM-dd`, `M/d/yyyy`, `M/d/yy`, `d-MMM-yyyy`, `yyyy/M/d`.
- `Blocker / Dependency`: task IDs from the same project, separated by semicolons or line breaks, become dependency records (a repeated ID creates one record); any other text is kept as descriptive blocker text.

### Upload outcomes

| Situation | HTTP | What the user sees |
| --- | --- | --- |
| Valid file | `201` | Import summary: projects, tasks, skipped sheets, warnings |
| Row/column validation errors | `422` | Every error with worksheet, row, column and message; **nothing is imported** |
| Project name already exists (e.g. same workbook uploaded twice) | `409` | "No records were imported because one or more project names already exist." Nothing is imported or duplicated |
| Corrupt/non-Excel content | `400` | "The Excel file is malformed or uses an unsupported workbook format." |
| Wrong file extension | `415` | Supported-type message |
| Larger than 10 MB | `413` | Size-limit message |

Uploads are atomic: one bad row rejects the whole file. Project names are unique case-insensitively (enforced by a database index). There is currently no API to delete or re-import a project; to reload the demo data, reset the database (section 2).

---

## 7. REST API summary

| Method & path | Purpose |
| --- | --- |
| `POST /api/uploads/excel` | Import `.xlsx`/`.xls` (multipart field `file`) |
| `POST /api/uploads/csv` | Import one `.csv` project |
| `GET /api/projects` | Projects with task metrics and risk counts by severity (sorted by name) |
| `GET /api/projects/{id}` | One project's metrics |
| `GET /api/projects/{id}/tasks` | Paged tasks; filters `status`, `priority`, `assignee`, `overdue`, `analysisDate` (defaults to the project's last analysis date, or today if never analyzed); `page`, `size` (1–100), `sortBy`, `sortDirection` |
| `POST /api/projects/{id}/analyze?analysisDate=yyyy-MM-dd` | Run the risk rules (date optional; defaults to today in `APP_TIME_ZONE`) |
| `GET /api/projects/{id}/risks` | Latest active findings with evidence and supporting task IDs, most severe first |
| `GET /api/dashboard/portfolio` | Portfolio totals and per-project summaries |
| `GET /api/reports/weekly` | Seven-day window ending today plus the current snapshot |
| `GET /api/ai/status` | Whether AI insights are enabled, configured and authorized (never returns the key) |
| `POST /api/projects/{id}/insights` | Executive summary, top-risk explanations, actions and a weekly status draft — AI-generated when available, deterministic otherwise (section 12) |

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

"Overdue" means due **strictly before** the analysis date and not Done. A task due on the analysis date is not overdue. Re-running analysis updates existing findings (by stable fingerprint) instead of duplicating them, resolves findings whose condition no longer holds, and reopens findings when the condition returns. There is no milestone/release field, so the engine does not predict project-level deadline misses.

Risk counts are counts of **findings**, not of tasks, and there is no weighted score. One task can produce several findings: for example EC-102 (Critical, Blocked, overdue) produces an overdue, a blocked and a critical-at-risk finding. Findings are listed most severe first.

**Reference result** for the sample workbook analysed with `analysisDate=2026-10-15`: 5 overdue tasks (EC-101, EC-102, CRM-202, MA-303, DP-401), 4 blocked (EC-102, CRM-204, MA-303, DP-404), and 30 active findings — 2 Critical, 18 High, 10 Medium, 0 Low (ECommerce 11, CRM 7, MobileApp 6, DataPlatform 6). Because the overdue rules depend on the date, a different analysis date gives different numbers.

---

## 9. Live demonstration script

1. Reset the database if it already contains the sample projects (section 2).
2. Start the backend with `DB_PASSWORD` and `APP_TIME_ZONE` set, then start the frontend (section 4).
3. Open <http://localhost:4200> → the **Dashboard** shows the empty state.
4. **Upload data** → choose `DeliveryPulse_Multi_Project_Sample.xlsx` → **Import file** → summary shows 4 projects, 25 tasks, `Read Me` skipped.
5. **Projects** → four projects (CRM, DataPlatform, ECommerce, MobileApp), each "Not analyzed".
6. Open **ECommerce** → **Run analysis** → 11 active risks, including the Critical "EC-102 Payment Gateway Integration" (blocked and overdue). Each risk card shows its evidence and supporting task IDs, which can be checked against the task table. Repeat for the other projects, or use **Run analysis** per project on the **Risk analysis** page.
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
| Backend fails with `password authentication failed` or `no password supplied` | `DB_PASSWORD` is not set in the terminal that started the backend, or the role password differs. Reset it as an admin with `ALTER ROLE deliverypulse_app WITH PASSWORD '...';`. |
| `Connection refused` on port 5432 | PostgreSQL service is stopped (`Get-Service postgresql*`, then `Start-Service <name>`), or `DB_HOST`/`DB_PORT` are wrong. |
| Flyway `Validate failed` / Hibernate `Schema-validation` errors | An applied migration was edited, or tables were changed by hand. Restore the migration file, or reset the database in development. |
| `Port 8080 was already in use` | Stop the other process (`netstat -ano \| findstr :8080`) or set `SERVER_PORT` and `API_PROXY_TARGET` to match. |
| UI shows "Cannot reach the DeliveryPulse API" | Backend not running, or the dev server was started without the proxy. Use `npm start`, which applies `proxy.conf.mjs`. |
| Uploads or "Run analysis" return 403, but pages load | CORS rejected a request that was not proxied. Use the dev proxy (`npm start`), or set `FRONTEND_ORIGIN` to the exact scheme://host:port the browser uses and restart the backend. |
| Dev server on 4200 stops responding | Stop it (Ctrl+C) and run `npm start` again. To use another port: `npx ng serve --port 4300`. |
| Upload returns 409 | Those project names already exist. Reset the database to re-import. |
| Overdue counts look off by one day | Set `APP_TIME_ZONE` to your local zone, or pass `analysisDate` explicitly. |

---

## 11. Known limitations

- No authentication or authorization.
- No project delete/re-import API; resetting the database is the only way to reload the same project names.
- No import-history endpoint (import runs are recorded in `import_runs` but not exposed); no dependency-graph endpoint (project detail shows the original blocker text and the risk supporting task IDs).
- Each project's metrics and risks are as of its own last analysis date. The portfolio, dashboard and weekly report add those up while labelling the snapshot with today's date, so a project analyzed days ago contributes its older numbers. Re-run analysis on each project for a same-day picture.
- `import_runs` records only successful imports; rejected uploads are logged but not stored.
- The weekly report is a current snapshot over a seven-day window, not a historical trend, because task-status history is not stored.
- Backend automated tests run on H2. PostgreSQL behaviour was verified manually (section 5).
- AI insights are generated on demand and not stored; each click is a new provider call.

---

## 12. Optional AI insights

The project detail page has an **Insights** panel. It produces four things:

- an executive summary
- explanations of the highest-priority risks
- recommended actions
- a draft weekly status report

Every statement is labelled **FACT** (stated by the records, with supporting task IDs) or **ASSUMPTION** (an inference).

AI is **off by default**. The panel then builds the same sections from the deterministic findings ("Deterministic summary"), and no data leaves the server. The upload → analyze → dashboard → report workflow never depends on AI.

### Turning it on

AI runs only when **all three** of these are set in the backend's environment:

```powershell
$env:AI_INSIGHTS_ENABLED = "true"
$env:ANTHROPIC_API_KEY = "<your key>"                 # environment only — never in files or source control
$env:AI_EXTERNAL_DATA_SHARING_AUTHORIZED = "true"     # confirms you may send this project data to Anthropic
```

Restart the backend, then check `GET /api/ai/status`: `"available": true` means it is ready.

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_INSIGHTS_ENABLED` | `false` | Master switch |
| `ANTHROPIC_API_KEY` | empty | Provider credential (read from the environment only; never logged or returned) |
| `AI_EXTERNAL_DATA_SHARING_AUTHORIZED` | `false` | Explicit authorization to send project data to the external provider |
| `AI_MODEL` | `claude-opus-5-5` | Model ID |
| `AI_TIMEOUT` | `60s` | Per-request timeout |
| `AI_MAX_RETRIES` | `2` | SDK retries for 429/5xx/connection errors |
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

### Swapping the provider

The service depends only on `com.deliverypulse.insights.llm.LlmClient`. To use another provider, add a new implementation and select it in `LlmConfiguration` with `AI_PROVIDER`. The prompt, validation and fallback stay unchanged.

### Tests

All AI tests use mocked or faked providers; no API key or paid access is needed:

- **Service:** every availability state and every failure type.
- **Validation:** hallucinated task IDs and risk refs, FACT relabelling, size caps.
- **Prompt:** injection containment and privacy defaults.
- **Provider:** the real SDK client against a local fake Messages API (429/529/500/402/401, refusal, truncation, malformed JSON, timeout).
- **Endpoints:** both configurations through the Spring context.
- **Panel:** Angular component tests.

A live call to Anthropic has not been exercised because no key is configured in this environment.

