// Fictional demo portfolio for scripts/jira-demo.mjs. Dates are days from the day each week is run (T).
// Together the five projects exercise every DeliveryPulse rule:
//   PAY  at-risk release: blocked Critical task (recurring), overdue work, critical/high bugs, dependency on a blocked task
//   MOB  missed milestone: overdue Critical bug, overdue story, defects opened and closed
//   DATA healthy: a completed release, an on-track release, a low-priority bug that closes
//   CRMX data quality: no due dates, no assignee, a release without a date, a link to another project
//   OPS  dependency chain: overdue and incomplete prerequisites, recurring blocker, task due after its milestone

export const PROJECTS = [
  {
    key: 'PAY', name: 'Payments Revamp', components: ['API', 'Checkout', 'Fraud'],
    versions: [{ name: 'Pilot', release: -20 }, { name: 'Launch', release: 10 }, { name: 'Scale', release: 60 }],
  },
  {
    key: 'MOB', name: 'Mobile App 3.0', components: ['iOS', 'Android', 'Design'],
    versions: [{ name: 'Beta', release: -5 }, { name: 'GA', release: 25 }],
  },
  {
    key: 'DATA', name: 'Data Platform', components: ['Ingestion', 'Warehouse', 'BI'],
    versions: [{ name: 'Q3 Release', release: -30 }, { name: 'Q4 Release', release: 45 }],
  },
  {
    key: 'CRMX', name: 'CRM Migration', components: ['Data', 'Sales'],
    versions: [{ name: 'Cutover', release: null }, { name: 'Hypercare', release: 50 }],
  },
  {
    key: 'OPS', name: 'Ops & Infra', components: ['Platform', 'Network', 'Monitoring'],
    versions: [{ name: 'Cloud Move', release: 40 }],
  },
];

// ref: [project, type, priority, summary, component, version, due (days from T or null), assigned]
export const ISSUES = {
  'PAY-1': ['PAY', 'Story', 'Highest', 'Payment gateway integration', 'API', 'Launch', 6, true],
  'PAY-2': ['PAY', 'Task', 'High', '3-D Secure challenge flow', 'Checkout', 'Launch', -2, true],
  'PAY-3': ['PAY', 'Bug', 'Highest', 'Duplicate charge on payment retry', 'Checkout', 'Launch', 3, true],
  'PAY-4': ['PAY', 'Task', 'Medium', 'Refund API', 'API', 'Launch', 14, true],
  'PAY-5': ['PAY', 'Story', 'Medium', 'Fraud rules engine', 'Fraud', 'Scale', 40, true],
  'PAY-6': ['PAY', 'Task', 'Medium', 'Pilot merchant onboarding', 'Checkout', 'Pilot', -22, true],
  'PAY-7': ['PAY', 'Task', 'Low', 'Pilot load test', 'API', 'Pilot', -21, true],
  'PAY-8': ['PAY', 'Bug', 'High', 'Webhook signature mismatch', 'API', 'Launch', 8, true],

  'MOB-1': ['MOB', 'Story', 'High', 'Redesigned onboarding', 'Design', 'Beta', -8, true],
  'MOB-2': ['MOB', 'Bug', 'Highest', 'Crash on login (Android 15)', 'Android', 'Beta', -3, true],
  'MOB-3': ['MOB', 'Story', 'Medium', 'Dark mode', 'Design', 'Beta', -10, true],
  'MOB-4': ['MOB', 'Bug', 'High', 'Push notifications delayed', 'iOS', 'GA', 12, true],
  'MOB-5': ['MOB', 'Task', 'Low', 'App Store screenshots', 'Design', 'GA', 20, true],
  'MOB-6': ['MOB', 'Story', 'High', 'Offline mode', 'Android', 'GA', 20, true],
  'MOB-7': ['MOB', 'Bug', 'Medium', 'Wrong currency format in receipts', 'iOS', 'GA', 15, true],

  'DATA-1': ['DATA', 'Story', 'High', 'Batch ingestion from ERP', 'Ingestion', 'Q3 Release', -35, true],
  'DATA-2': ['DATA', 'Task', 'Medium', 'Warehouse schema v2', 'Warehouse', 'Q3 Release', -33, true],
  'DATA-3': ['DATA', 'Task', 'Medium', 'Sales KPI dashboard', 'BI', 'Q3 Release', -32, true],
  'DATA-4': ['DATA', 'Task', 'Low', 'Data catalogue entries', 'Warehouse', 'Q3 Release', -31, true],
  'DATA-5': ['DATA', 'Story', 'Medium', 'Streaming ingestion', 'Ingestion', 'Q4 Release', 30, true],
  'DATA-6': ['DATA', 'Task', 'Low', 'Warehouse cost dashboard', 'BI', 'Q4 Release', 40, true],
  'DATA-7': ['DATA', 'Bug', 'Low', 'Null dates in CSV export', 'BI', 'Q4 Release', 20, true],

  'CRMX-1': ['CRMX', 'Task', 'Medium', 'Map legacy CRM fields', 'Data', 'Cutover', null, false],
  'CRMX-2': ['CRMX', 'Task', 'High', 'Migrate contacts and accounts', 'Data', 'Cutover', 5, true],
  'CRMX-3': ['CRMX', 'Story', 'Medium', 'Sales pipeline dashboard', 'Sales', 'Hypercare', null, true],
  'CRMX-4': ['CRMX', 'Bug', 'High', 'Duplicate accounts after test import', 'Data', 'Cutover', 7, false],
  'CRMX-5': ['CRMX', 'Task', 'Low', 'Train the sales team', 'Sales', 'Hypercare', 45, true],

  'OPS-1': ['OPS', 'Task', 'High', 'Provision Kubernetes cluster', 'Platform', 'Cloud Move', -1, true],
  'OPS-2': ['OPS', 'Task', 'Medium', 'Set up CI/CD pipelines', 'Platform', 'Cloud Move', 10, true],
  'OPS-3': ['OPS', 'Task', 'High', 'Migrate production databases', 'Platform', 'Cloud Move', 20, true],
  'OPS-4': ['OPS', 'Task', 'Highest', 'Firewall rules approval', 'Network', 'Cloud Move', 5, true],
  'OPS-5': ['OPS', 'Task', 'Low', 'Decommission old servers', 'Platform', 'Cloud Move', 50, true],
  'OPS-6': ['OPS', 'Bug', 'Medium', 'Monitoring alerts missing for queues', 'Monitoring', 'Cloud Move', 15, true],
  'OPS-7': ['OPS', 'Bug', 'Highest', 'Disk full on build agents', 'Platform', 'Cloud Move', 2, true],
};

// Issue descriptions. Lines starting with "- " become a bullet list in Jira; other lines become paragraphs.
export const DESCRIPTIONS = {
  'PAY-1': `Integrate the new payment gateway (card authorisation, capture and void) behind our Payments API so checkout can move off the legacy processor before Launch.
Blocked: the provider has not issued production sandbox credentials. This is the second time the request has stalled; escalated to the vendor account manager.
Acceptance criteria:
- Authorise, capture and void work end to end in the provider sandbox
- Idempotency keys on every request
- Gateway errors mapped to our standard error codes`,
  'PAY-2': `Support the 3-D Secure 2 challenge flow (frictionless and challenge) in web checkout, required for EU cards under PSD2 strong customer authentication.
Acceptance criteria:
- Challenge iframe renders and returns the result to checkout
- Frictionless approvals complete without a challenge
- Abandoned challenges time out after 10 minutes and release the order`,
  'PAY-3': `Customers are charged twice when a payment request times out and the client retries. Seen 14 times in the pilot; finance has refunded all of them manually.
Likely cause: retries are sent without the original idempotency key.
Acceptance criteria:
- Retries reuse the original idempotency key
- Regression test reproduces the timeout and retry
- Reconciliation report shows no duplicate captures for a week`,
  'PAY-4': `Expose full and partial refunds through the Payments API so support no longer issues refunds in the provider console.
Depends on the gateway integration (PAY-1).
Acceptance criteria:
- Full and partial refunds, with partials capped at the captured amount
- Refund status available by webhook and by polling
- Every refund recorded in the audit log with the agent who issued it`,
  'PAY-5': `Rules engine that scores each payment and holds risky ones for review before capture (velocity, BIN country mismatch, amount thresholds).
Waits on the 3-D Secure flow (PAY-2), which supplies the authentication result used as a signal.
Acceptance criteria:
- Rules configurable without a deployment
- Held payments appear in the review queue within 5 seconds`,
  'PAY-6': `Onboard the three pilot merchants: KYC checks, settlement accounts and webhook endpoints.
Done: all three merchants are live and processed real transactions in the pilot.`,
  'PAY-7': `Load test the payment API at 3x pilot peak (450 requests per second) before widening the rollout.
Done: p95 latency 210 ms, no errors. Report attached to the Pilot release.`,
  'PAY-8': `Webhook signature verification fails for about 2% of provider callbacks, so those payment updates are dropped.
Suspected cause: the body is re-serialised before the signature check instead of verifying the raw bytes.
Acceptance criteria:
- Verify against the raw request body
- Failed verifications logged with the provider event id
- Replay of last week's failed callbacks succeeds`,

  'MOB-1': `New 3-step onboarding (welcome, permissions, account setup) replacing the 6-screen flow. Target: lift day-1 activation from 41% to 50%.
Was planned for Beta and is still open, so Beta shipped without it.
Acceptance criteria:
- Matches the approved Figma designs on iOS and Android
- Permission prompts appear only when the feature needs them
- Analytics events for every step`,
  'MOB-2': `App crashes on the login screen on Android 15 devices (Pixel 8, Galaxy S24). Crash-free sessions on Android 15 fell to 92%.
Fixed in week 3: the deprecated biometric API is replaced; crash-free sessions back above 99.5%.`,
  'MOB-3': `Dark mode across all screens, following the system setting with a manual override in Settings.
Done and shipped in Beta.`,
  'MOB-4': `Push notifications arrive 5 to 20 minutes late on iOS when the app is in the background.
Investigating APNs priority and the background refresh settings.
Acceptance criteria:
- 95% of notifications delivered within 30 seconds in the test cohort
- Order-status notifications sent with high priority`,
  'MOB-5': `Produce App Store and Play Store screenshots for the GA listing in 6 languages, using the new design system.`,
  'MOB-6': `Let users browse their last 30 days of orders and receipts offline, and sync changes when the connection returns.
Blocked: waiting for the API team to add delta-sync endpoints; no date committed yet.
Acceptance criteria:
- Orders and receipts readable in airplane mode
- Changes made offline sync without duplicates`,
  'MOB-7': `Receipts show amounts in USD format for every locale (for example $1,234.50 for euro customers).
Acceptance criteria:
- Amounts use the order's currency and the device locale
- Unit tests for EUR, GBP, INR and JPY`,

  'DATA-1': `Nightly batch ingestion of orders, invoices and inventory from the ERP into the raw zone of the warehouse.
Done: running nightly since the Q3 release; 99.8% of runs on time.`,
  'DATA-2': `Version 2 of the warehouse schema: conformed customer and product dimensions, and partitioned fact tables.
Done: all reports migrated to v2.`,
  'DATA-3': `Executive sales KPI dashboard: revenue, orders, average order value and conversion by region, refreshed hourly.
Done and adopted by the sales leadership team.`,
  'DATA-4': `Catalogue entries (owner, description, refresh schedule, PII flags) for every warehouse table.
Done: 124 tables documented.`,
  'DATA-5': `Stream order events from Kafka into the warehouse so dashboards are at most 5 minutes behind, instead of nightly.
On track for the Q4 release; the proof of concept handled 3x expected peak.
Acceptance criteria:
- End-to-end latency under 5 minutes at p95
- Exactly-once loading, with no duplicate orders
- Alerting when lag exceeds 15 minutes`,
  'DATA-6': `Dashboard showing warehouse spend by team and by query, so teams can see and reduce their costs.
Acceptance criteria:
- Daily cost per team and per top-20 queries
- Alert when a team exceeds its monthly budget`,
  'DATA-7': `The CSV export writes "null" instead of an empty value for missing dates, which breaks imports into finance tools.
Fixed: missing dates are now exported as empty fields.`,

  'CRMX-1': `Map every field in the legacy CRM to the new CRM, including custom fields, picklists and owner mappings.
No owner or due date yet; needs a business analyst from the sales operations team.`,
  'CRMX-2': `Migrate about 180,000 contacts and 42,000 accounts with their relationships and activity history.
Depends on PAY-2 in the Payments project: migrated accounts must carry the new 3-D Secure authentication flags.
Acceptance criteria:
- Record counts reconcile between the old and new CRM
- Owners and account hierarchies preserved
- Test import signed off by sales operations`,
  'CRMX-3': `Pipeline dashboard for sales managers: opportunities by stage, forecast versus target, and stalled deals.
Scope still being agreed with sales leadership; no due date set.`,
  'CRMX-4': `The test import created duplicate accounts where the legacy CRM had the same company under different spellings ("ACME Ltd" and "Acme Limited").
Acceptance criteria:
- Fuzzy matching on name, domain and address before import
- Duplicates found in the test import merged
- Merge report reviewed by sales operations`,
  'CRMX-5': `Train 60 sales reps on the new CRM: two live sessions per region plus recorded videos and a quick-reference guide.`,

  'OPS-1': `Provision the production Kubernetes cluster for the cloud move: three node pools, autoscaling, network policies and secrets management.
Done: cluster live and passing the security baseline.`,
  'OPS-2': `Build CI/CD pipelines for the 12 services moving to the cloud: build, test, scan and deploy to staging and production with approvals.
Waits on the cluster (OPS-1).`,
  'OPS-3': `Migrate the three production databases to managed Postgres with less than 15 minutes of downtime each.
Waits on the CI/CD pipelines (OPS-2) so schema changes can be deployed safely.
Acceptance criteria:
- Rehearsed twice in staging
- Rollback plan tested
- Data verified with row counts and checksums`,
  'OPS-4': `Security must approve the firewall rules that let the cloud cluster reach on-premise systems (ERP, LDAP, file shares).
This request has been blocked twice waiting for security review. Approval arrived this week, but the rules still need to be applied and tested.
Acceptance criteria:
- Rules applied in the staging and production firewalls
- Connectivity tests pass for every on-premise dependency`,
  'OPS-5': `Decommission the 22 legacy servers after the migration: final backups, wipe disks and cancel the hosting contracts.
Waits on the database migration (OPS-3).`,
  'OPS-6': `No alerts fire when the order queue backs up; the last incident was noticed by customers first.
Acceptance criteria:
- Alerts on queue depth and message age for every production queue
- Alerts routed to the on-call rota`,
  'OPS-7': `Build agents ran out of disk space, failing every pipeline for 3 hours.
Fixed: nightly cleanup of Docker images and build caches, plus a disk-usage alert at 80%.`,
};

// status: Jira status category per ref ("new" = To Do, "indeterminate" = In Progress, "done" = Done).
// links: [dependent, blocker] means "dependent is blocked by blocker".
export const WEEKS = {
  week1: {
    create: Object.keys(ISSUES).filter((ref) => !['PAY-8', 'MOB-7', 'OPS-7'].includes(ref)),
    status: {
      'PAY-1': 'indeterminate', 'PAY-2': 'indeterminate', 'PAY-6': 'done', 'PAY-7': 'done',
      'MOB-1': 'indeterminate', 'MOB-2': 'indeterminate', 'MOB-3': 'done', 'MOB-6': 'indeterminate',
      'DATA-1': 'done', 'DATA-2': 'done', 'DATA-3': 'done', 'DATA-4': 'done', 'DATA-5': 'indeterminate',
      'DATA-7': 'indeterminate',
      'CRMX-2': 'indeterminate',
      'OPS-1': 'indeterminate', 'OPS-4': 'indeterminate',
    },
    block: ['PAY-1', 'OPS-4'],
    links: [['PAY-4', 'PAY-1'], ['PAY-5', 'PAY-2'], ['CRMX-2', 'PAY-2'], ['OPS-2', 'OPS-1'], ['OPS-3', 'OPS-2'],
      ['OPS-5', 'OPS-3']],
  },
  week2: {
    create: ['PAY-8', 'MOB-7'],
    status: { 'DATA-7': 'done', 'CRMX-5': 'indeterminate', 'OPS-1': 'done', 'OPS-2': 'indeterminate' },
    unblock: ['PAY-1', 'OPS-4'],
  },
  week3: {
    create: ['OPS-7'],
    status: { 'PAY-3': 'indeterminate', 'PAY-8': 'indeterminate', 'MOB-2': 'done', 'DATA-6': 'indeterminate',
      'CRMX-4': 'indeterminate', 'OPS-7': 'indeterminate' },
    block: ['PAY-1', 'OPS-4', 'MOB-6'],
  },
  // Demo day: Ops recovers (critical bug fixed, firewall approved) so the portfolio shows a spread of
  // health: PAY and MOB off track, OPS and CRMX at risk (OPS-4 still flagged as a recurring blocker), DATA on track.
  week4: {
    status: { 'OPS-7': 'done' },
    unblock: ['OPS-4'],
  },
  // Sets the descriptions on issues created before they were added to this file. Safe to rerun.
  descriptions: {
    describe: Object.keys(ISSUES),
  },
};
