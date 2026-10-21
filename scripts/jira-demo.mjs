#!/usr/bin/env node
// Builds a fictional five-project portfolio in your own Jira Cloud site, one "week" at a time, so every
// DeliveryPulse risk rule can be shown from Jira data. The projects and weekly changes are in jira-demo-data.mjs.
//
//   node scripts/jira-demo.mjs week1   # create projects PAY, MOB, DATA, CRMX, OPS with releases, components, issues
//   node scripts/jira-demo.mjs week2   # unblock, finish and add issues
//   node scripts/jira-demo.mjs week3   # block again (recurring blockers), fix bugs, add a critical bug
//   node scripts/jira-demo.mjs week4   # demo day: Ops fixes its critical bug and gets the firewall approved
//   node scripts/jira-demo.mjs descriptions   # set the issue descriptions on existing issues (safe to rerun)
//
// Click "Sync now" in DeliveryPulse after each week. Reads JIRA_BASE_URL, JIRA_EMAIL and JIRA_API_TOKEN
// from the repo-root .env (or the environment). It only touches these projects; the token is never printed.

import { readFileSync } from 'node:fs';
import { DESCRIPTIONS, ISSUES, PROJECTS, WEEKS } from './jira-demo-data.mjs';

const env = { ...loadEnv(new URL('../.env', import.meta.url)), ...process.env };
const BASE = (env.JIRA_BASE_URL ?? '').replace(/\/+$/, '');
const BLOCKED_LABEL = env.JIRA_BLOCKED_LABEL || 'blocked';
const AUTH = 'Basic ' + Buffer.from(`${env.JIRA_EMAIL ?? ''}:${env.JIRA_API_TOKEN ?? ''}`).toString('base64');

const TODAY = new Date();
const day = (offset) => offset == null ? undefined
  : new Date(TODAY.getFullYear(), TODAY.getMonth(), TODAY.getDate() + offset).toLocaleDateString('en-CA');

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
  const typesByProject = {};
  for (const spec of PROJECTS) {
    const project = await ensureProject(spec, me.accountId);
    const types = new Set(project.issueTypes.map((type) => type.name));
    if (!types.has('Bug')) fail(`Project ${spec.key} has no "Bug" issue type. Use a Scrum or Kanban software project.`);
    typesByProject[spec.key] = types;
    await ensureNamed('version', spec.versions.map((version) => ({ name: version.name, releaseDate: day(version.release) })),
      `/rest/api/3/project/${spec.key}/versions`, (version) => ({ ...version, projectId: Number(project.id) }));
    await ensureNamed('component', spec.components.map((name) => ({ name })), `/rest/api/3/project/${spec.key}/components`,
      (component) => ({ ...component, project: spec.key }));
  }

  const keys = await demoIssueKeys();
  console.log('Issues:');
  for (const ref of plan.create ?? []) {
    if (keys[ref]) { console.log(`  ${ref} already exists as ${keys[ref]}`); continue; }
    keys[ref] = await createIssue(ref, typesByProject[ISSUES[ref][0]], me.accountId);
  }
  for (const [ref, category] of Object.entries(plan.status ?? {})) await transition(need(keys, ref), category);
  for (const ref of plan.block ?? []) await setLabel(need(keys, ref), 'add');
  for (const ref of plan.unblock ?? []) await setLabel(need(keys, ref), 'remove');
  for (const [dependent, blocker] of plan.links ?? []) await ensureBlockedBy(need(keys, dependent), need(keys, blocker));
  for (const ref of plan.describe ?? []) {
    if (!keys[ref] || !DESCRIPTIONS[ref]) continue;
    await jira('PUT', `/rest/api/3/issue/${keys[ref]}`, { fields: { description: adf(DESCRIPTIONS[ref]) } });
    console.log(`  ${keys[ref]} description set`);
  }
}

/** Plain text to Atlassian Document Format: "- " lines become a bullet list, other lines become paragraphs. */
function adf(text) {
  const content = [];
  for (const line of text.split('\n').map((value) => value.trim()).filter(Boolean)) {
    const item = line.startsWith('- ') ? line.slice(2) : null;
    const paragraph = (value) => ({ type: 'paragraph', content: [{ type: 'text', text: value }] });
    if (item === null) { content.push(paragraph(line)); continue; }
    if (content.at(-1)?.type !== 'bulletList') content.push({ type: 'bulletList', content: [] });
    content.at(-1).content.push({ type: 'listItem', content: [paragraph(item)] });
  }
  return { type: 'doc', version: 1, content };
}

async function ensureProject({ key, name }, leadAccountId) {
  const existing = await jira('GET', `/rest/api/3/project/${key}`, undefined, { allow404: true });
  if (existing) { console.log(`Project ${key} exists.`); return existing; }
  try {
    await jira('POST', '/rest/api/3/project', {
      key, name, projectTypeKey: 'software', leadAccountId,
      projectTemplateKey: 'com.pyxis.greenhopper.jira:gh-simplified-kanban-classic', assigneeType: 'PROJECT_LEAD',
      description: 'Fictional demo data created by DeliveryPulse scripts/jira-demo.mjs',
    });
  } catch (error) {
    throw new Error(`${error.message}\nCould not create the project. Create a company-managed Kanban software project `
      + `named "${name}" with key ${key} in Jira yourself, then run this command again.`);
  }
  console.log(`Created project ${key} (${name}).`);
  return jira('GET', `/rest/api/3/project/${key}`);
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
  const jql = `project in (${PROJECTS.map((project) => project.key).join(', ')}) `
    + `AND labels in (${labels.map((label) => `"${label}"`).join(', ')})`;
  const keys = {};
  const issues = [];
  let token;
  do {
    const page = await jira('GET', `/rest/api/3/search/jql?jql=${encodeURIComponent(jql)}&fields=labels&maxResults=100`
      + (token ? `&nextPageToken=${encodeURIComponent(token)}` : ''));
    issues.push(...(page.issues ?? []));
    token = page.isLast === false ? page.nextPageToken : undefined;
  } while (token);
  for (const issue of issues) {
    const ref = Object.keys(ISSUES).find((candidate) => issue.fields.labels.includes(demoLabel(candidate)));
    if (ref) keys[ref] = issue.key;
  }
  return keys;
}

async function createIssue(ref, types, accountId) {
  const [project, type, priority, summary, component, version, due, assigned] = ISSUES[ref];
  const fields = {
    project: { key: project }, summary, issuetype: { name: types.has(type) ? type : 'Task' },
    priority: { name: priority }, components: [{ name: component }], fixVersions: [{ name: version }],
    labels: [demoLabel(ref)],
    ...(DESCRIPTIONS[ref] ? { description: adf(DESCRIPTIONS[ref]) } : {}),
    ...(due == null ? {} : { duedate: day(due) }),
    ...(assigned ? { assignee: { accountId } } : {}),
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
  console.log(`  created ${created.key} (${summary})`);
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
