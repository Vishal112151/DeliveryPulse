#!/usr/bin/env node
// Builds a fictional demo project in your own Jira Cloud site, one "week" at a time, so DeliveryPulse's
// Jira sync can be shown end to end. It mirrors sample-data/release-demo.
//
//   node scripts/jira-demo.mjs week1   # create project DPDEMO, releases, components and issues RD-1..RD-6
//   node scripts/jira-demo.mjs week2   # unblock RD-2, start RD-4, add bug RD-7
//   node scripts/jira-demo.mjs week3   # block RD-2 again, fix RD-3, start RD-7
//
// Click "Sync now" in DeliveryPulse after each week. Reads JIRA_BASE_URL, JIRA_EMAIL and JIRA_API_TOKEN
// from the repo-root .env (or the environment). It only touches the demo project; the token is never printed.

import { readFileSync } from 'node:fs';

const env = { ...loadEnv(new URL('../.env', import.meta.url)), ...process.env };
const BASE = (env.JIRA_BASE_URL ?? '').replace(/\/+$/, '');
const PROJECT = (env.JIRA_DEMO_PROJECT_KEY ?? 'DPDEMO').toUpperCase();
const BLOCKED_LABEL = env.JIRA_BLOCKED_LABEL || 'blocked';
const AUTH = 'Basic ' + Buffer.from(`${env.JIRA_EMAIL ?? ''}:${env.JIRA_API_TOKEN ?? ''}`).toString('base64');

const VERSIONS = [
  { name: 'Alpha', releaseDate: '2026-10-16' },
  { name: 'Beta', releaseDate: '2026-10-30' },
  { name: 'GA', releaseDate: '2026-12-15' },
];
const COMPONENTS = ['Backend', 'Mobile', 'Web', 'QA', 'Docs'];
const ISSUES = {
  'RD-1': { summary: 'Checkout API', type: 'Story', priority: 'High', component: 'Backend', version: 'Alpha', due: '2026-10-15' },
  'RD-2': { summary: 'Payment provider setup', type: 'Task', priority: 'High', component: 'Backend', version: 'Beta', due: '2026-10-25' },
  'RD-3': { summary: 'Login crash on Android', type: 'Bug', priority: 'Highest', component: 'Mobile', version: 'Beta', due: '2026-10-24' },
  'RD-4': { summary: 'Order history page', type: 'Story', priority: 'Medium', component: 'Web', version: 'Beta', due: '2026-11-05' },
  'RD-5': { summary: 'Load testing', type: 'Task', priority: 'Medium', component: 'QA', version: 'GA', due: '2026-12-01' },
  'RD-6': { summary: 'Release notes', type: 'Task', priority: 'Low', component: 'Docs', version: 'GA', due: '2026-12-10' },
  'RD-7': { summary: 'Wrong totals in cart', type: 'Bug', priority: 'High', component: 'Web', version: 'Beta', due: '2026-10-28' },
};
// Status targets are Jira status categories: "indeterminate" = In Progress, "done" = Done.
const WEEKS = {
  week1: { create: ['RD-1', 'RD-2', 'RD-3', 'RD-4', 'RD-5', 'RD-6'], status: { 'RD-1': 'indeterminate', 'RD-2': 'indeterminate', 'RD-3': 'indeterminate' }, block: ['RD-2'], links: [['RD-5', 'RD-2']] },
  week2: { create: ['RD-7'], status: { 'RD-4': 'indeterminate' }, unblock: ['RD-2'] },
  week3: { status: { 'RD-3': 'done', 'RD-7': 'indeterminate' }, block: ['RD-2'] },
};

const week = process.argv[2];
if (!WEEKS[week]) fail(`Usage: node scripts/jira-demo.mjs ${Object.keys(WEEKS).join('|')}`);
if (!BASE || !env.JIRA_EMAIL || !env.JIRA_API_TOKEN) fail('Set JIRA_BASE_URL, JIRA_EMAIL and JIRA_API_TOKEN in .env first.');

try {
  await run(WEEKS[week]);
  console.log(`\n${week} done. Now click "Sync now" in DeliveryPulse (Upload data page), then Run analysis.`);
} catch (error) {
  fail(error.message);
}

async function run(plan) {
  const me = await jira('GET', '/rest/api/3/myself');
  console.log(`Connected to ${BASE} as ${me.displayName}.`);
  const project = await ensureProject(me.accountId);
  const types = new Set(project.issueTypes.map((type) => type.name));
  if (!types.has('Bug')) fail(`Project ${PROJECT} has no "Bug" issue type. Use a Scrum or Kanban software project.`);
  await ensureNamed('version', VERSIONS, `/rest/api/3/project/${PROJECT}/versions`,
    (version) => ({ ...version, projectId: Number(project.id) }));
  await ensureNamed('component', COMPONENTS.map((name) => ({ name })), `/rest/api/3/project/${PROJECT}/components`,
    (component) => ({ ...component, project: PROJECT }));

  const keys = await demoIssueKeys();
  for (const ref of plan.create ?? []) {
    if (keys[ref]) { console.log(`  ${ref} already exists as ${keys[ref]}`); continue; }
    keys[ref] = await createIssue(ref, types, me.accountId);
  }
  for (const [ref, category] of Object.entries(plan.status ?? {})) await transition(need(keys, ref), category);
  for (const ref of plan.block ?? []) await setLabel(need(keys, ref), 'add');
  for (const ref of plan.unblock ?? []) await setLabel(need(keys, ref), 'remove');
  for (const [dependent, blocker] of plan.links ?? []) await ensureBlockedBy(need(keys, dependent), need(keys, blocker));
}

async function ensureProject(leadAccountId) {
  const existing = await jira('GET', `/rest/api/3/project/${PROJECT}`, undefined, { allow404: true });
  if (existing) { console.log(`Project ${PROJECT} exists.`); return existing; }
  try {
    await jira('POST', '/rest/api/3/project', {
      key: PROJECT, name: 'DeliveryPulse Demo', projectTypeKey: 'software', leadAccountId,
      projectTemplateKey: 'com.pyxis.greenhopper.jira:gh-simplified-kanban-classic', assigneeType: 'PROJECT_LEAD',
      description: 'Fictional demo data created by DeliveryPulse scripts/jira-demo.mjs',
    });
  } catch (error) {
    throw new Error(`${error.message}\nCould not create the project. Create a company-managed Kanban software project `
      + `with key ${PROJECT} in Jira yourself, then run this command again.`);
  }
  console.log(`Created project ${PROJECT}.`);
  return jira('GET', `/rest/api/3/project/${PROJECT}`);
}

async function ensureNamed(kind, wanted, listPath, body) {
  const existing = new Set((await jira('GET', listPath)).map((item) => item.name));
  for (const item of wanted.filter((candidate) => !existing.has(candidate.name))) {
    await jira('POST', `/rest/api/3/${kind}`, body(item));
    console.log(`  created ${kind} ${item.name}`);
  }
}

/** Demo issues carry a label like "dp-rd-2", so reruns find them instead of creating duplicates. */
async function demoIssueKeys() {
  const labels = Object.keys(ISSUES).map(demoLabel);
  const jql = `project = ${PROJECT} AND labels in (${labels.map((label) => `"${label}"`).join(', ')})`;
  const page = await jira('GET', `/rest/api/3/search/jql?jql=${encodeURIComponent(jql)}&fields=labels&maxResults=100`);
  const keys = {};
  for (const issue of page.issues ?? []) {
    const ref = Object.keys(ISSUES).find((candidate) => issue.fields.labels.includes(demoLabel(candidate)));
    if (ref) keys[ref] = issue.key;
  }
  return keys;
}

async function createIssue(ref, types, accountId) {
  const spec = ISSUES[ref];
  const fields = {
    project: { key: PROJECT }, summary: spec.summary,
    issuetype: { name: types.has(spec.type) ? spec.type : 'Task' },
    priority: { name: spec.priority }, duedate: spec.due, components: [{ name: spec.component }],
    fixVersions: [{ name: spec.version }], labels: [demoLabel(ref)], assignee: { accountId },
  };
  let created;
  try {
    created = await jira('POST', '/rest/api/3/issue', { fields });
  } catch (error) {
    // Some project screens hide fields such as priority or due date; retry without the ones Jira rejected.
    const rejected = Object.keys(error.fieldErrors ?? {}).filter((field) => !['project', 'summary', 'issuetype'].includes(field));
    if (!rejected.length) throw error;
    console.warn(`  Jira did not accept ${rejected.join(', ')} on ${ref}; creating it without them.`);
    rejected.forEach((field) => delete fields[field]);
    created = await jira('POST', '/rest/api/3/issue', { fields });
  }
  console.log(`  created ${created.key} (${ref} ${spec.summary})`);
  return created.key;
}

async function transition(key, category) {
  const issue = await jira('GET', `/rest/api/3/issue/${key}?fields=status`);
  if (issue.fields.status.statusCategory.key === category) return;
  const { transitions } = await jira('GET', `/rest/api/3/issue/${key}/transitions`);
  const target = transitions.find((candidate) => candidate.to.statusCategory.key === category);
  if (!target) throw new Error(`No transition from "${issue.fields.status.name}" to a ${category} status on ${key}.`);
  await jira('POST', `/rest/api/3/issue/${key}/transitions`, { transition: { id: target.id } });
  console.log(`  ${key} -> ${target.to.name}`);
}

async function setLabel(key, operation) {
  await jira('PUT', `/rest/api/3/issue/${key}`, { update: { labels: [{ [operation]: BLOCKED_LABEL }] } });
  console.log(`  ${key} ${operation === 'add' ? 'labelled' : 'unlabelled'} "${BLOCKED_LABEL}"`);
}

/** Makes "dependent is blocked by blocker". The POST direction is easy to get backwards, so it is checked. */
async function ensureBlockedBy(dependent, blocker) {
  const blockedBy = async () => (await jira('GET', `/rest/api/3/issue/${dependent}?fields=issuelinks`)).fields.issuelinks;
  const isRight = (link) => link.type.name === 'Blocks' && link.inwardIssue?.key === blocker;
  if ((await blockedBy()).some(isRight)) return;
  await jira('POST', '/rest/api/3/issueLink', { type: { name: 'Blocks' }, inwardIssue: { key: blocker }, outwardIssue: { key: dependent } });
  const links = await blockedBy();
  if (!links.some(isRight)) {
    const wrong = links.find((link) => link.type.name === 'Blocks' && link.outwardIssue?.key === blocker);
    if (wrong) await jira('DELETE', `/rest/api/3/issueLink/${wrong.id}`);
    await jira('POST', '/rest/api/3/issueLink', { type: { name: 'Blocks' }, inwardIssue: { key: dependent }, outwardIssue: { key: blocker } });
  }
  console.log(`  ${dependent} is blocked by ${blocker}`);
}

async function jira(method, path, body, { allow404 = false } = {}) {
  const response = await fetch(BASE + path, {
    method,
    headers: { Authorization: AUTH, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (allow404 && response.status === 404) return null;
  const text = await response.text();
  const json = text ? safeJson(text) : null;
  if (!response.ok) {
    const details = [...(json?.errorMessages ?? []), ...Object.entries(json?.errors ?? {}).map(([field, message]) => `${field}: ${message}`)];
    const hint = response.status === 401 || response.status === 403 ? ' Check JIRA_EMAIL and JIRA_API_TOKEN.' : '';
    const error = new Error(`${method} ${path.split('?')[0]} failed with HTTP ${response.status}. ${details.join('; ')}${hint}`);
    error.fieldErrors = json?.errors;
    throw error;
  }
  return json;
}

function need(keys, ref) {
  if (!keys[ref]) throw new Error(`${ref} does not exist yet. Run the earlier weeks first (week1, then week2, then week3).`);
  return keys[ref];
}

function demoLabel(ref) {
  return `dp-${ref.toLowerCase()}`;
}

function safeJson(text) {
  try { return JSON.parse(text); } catch { return null; }
}

function loadEnv(url) {
  try {
    return Object.fromEntries(readFileSync(url, 'utf8').split(/\r?\n/)
      .map((line) => line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/))
      .filter(Boolean)
      .map(([, key, value]) => [key, value.replace(/^(['"])(.*)\1$/, '$2')]));
  } catch {
    return {};
  }
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
