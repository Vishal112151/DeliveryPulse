# DeliveryPulse Frontend

Angular 22 standalone application for the DeliveryPulse project-delivery dashboard. Node 22.22.3 or later in the supported Node 22 line and npm are required.

## Run locally

Start PostgreSQL and the Spring Boot backend first. Then run from this directory:

```powershell
npm install
npm start
```

Open <http://localhost:4200>. The Angular dev server proxies `/api` and `/actuator` to `http://localhost:8080` using `proxy.conf.mjs` (which also strips the browser `Origin` header so proxied POSTs pass backend CORS). Set `apiBaseUrl` in `src/environments/environment.ts` for another development API location. Production uses `src/environments/environment.production.ts`.

## Test and build

```powershell
npm test -- --watch=false
npm run build
```

The production build is written to `dist/frontend/`.

## Backend API notes

The UI uses real portfolio, project, task, analysis, risk, upload, and weekly-report endpoints. The backend currently does not expose import-run history or a structured dependency-list endpoint. The dashboard therefore displays the latest portfolio analysis timestamp instead of recent import history. Project detail displays source blocker/dependency text and task IDs attached to risk findings; it does not construct a dependency graph.
