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
};
