// Captures evidence screenshots from the locally running Operate / Tasklist (Camunda 8 Run)
// with headless Microsoft Edge.
//
// Usage: node capture.mjs <view> <arg> <output.png> [json]
//   operate-processes  <processId>            process instance list (all states) for one process
//   operate-instance   <processInstanceKey>   instance detail: diagram with token path + variables
//   operate-incident   <processInstanceKey>   instance detail with the incidents table opened
//   operate-decisions  <decisionId|->         evaluated decision instances
//   tasklist-list      <processId|->          Tasklist inbox
//   tasklist-task      <processInstanceKey>   the open user task of the instance, form visible
//   tasklist-complete  <processInstanceKey> <out> '{"radio":"Approve loan","textarea":"text", ...}'
//                      assigns the task to the demo user, fills the form in the Tasklist UI,
//                      screenshots it (<out>) and clicks "Complete Task" (<out>-completed.png)
//
// Credentials: the default local demo user of Camunda 8 Run (configuration/application.yaml).
import { chromium } from 'playwright-core';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const BASE = 'http://localhost:8080';
const AUTH = join(tmpdir(), 'pbj2-camunda-auth.json');
const [, , view, arg, out, extra] = process.argv;
if (!view || !out) {
  console.error('Usage: node capture.mjs <view> <arg> <output.png> [json]');
  process.exit(1);
}

async function api(path, body) {
  const r = await fetch(`${BASE}/v2${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`${path}: HTTP ${r.status} ${await r.text()}`);
  return r.json();
}

async function openTaskKey(processInstanceKey) {
  for (let i = 0; i < 30; i++) {
    const { items } = await api('/user-tasks/search', { filter: { processInstanceKey, state: 'CREATED' } });
    if (items.length) return items[0].userTaskKey;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`no open user task for instance ${processInstanceKey}`);
}

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  deviceScaleFactor: 1.25,
  storageState: existsSync(AUTH) ? AUTH : undefined,
});
const page = await context.newPage();

async function go(url) {
  await page.goto(BASE + url);
  await page.waitForLoadState('networkidle').catch(() => {});
  if (page.url().includes('/login')) {
    await page.fill('#username', 'demo');
    await page.fill('#password', 'demo');
    await page.click('button[type=submit]');
    await page.waitForURL((u) => !u.toString().includes('/login'), { timeout: 20000 });
    await context.storageState({ path: AUTH });
    if (!page.url().startsWith(BASE + url)) await page.goto(BASE + url);
    await page.waitForLoadState('networkidle').catch(() => {});
  }
}

async function dismissBanners() {
  for (const sel of ['button[aria-label="close notification"]', 'button[title="close notification"]']) {
    const b = page.locator(sel);
    for (let i = 0; i < (await b.count()); i++) await b.nth(i).click().catch(() => {});
  }
}

async function settle(ms = 2500) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(ms);
}

switch (view) {
  case 'operate-processes': {
    let q = '';
    if (arg && arg !== '-') {
      // latest deployed version, so Operate shows that definition's diagram with instance counts
      const { items } = await api('/process-definitions/search', {
        filter: { processDefinitionId: arg }, sort: [{ field: 'version', order: 'DESC' }], page: { limit: 1 },
      });
      q = `&process=${arg}&version=${items[0].version}`;
    }
    await go(`/operate/processes?active=true&incidents=true&completed=true&canceled=true${q}`);
    await page.waitForSelector('svg .djs-element', { timeout: 20000 }).catch(() => {});
    await settle(4000);
    break;
  }
  case 'operate-instance':
  case 'operate-variables': // same page: the variables panel of the root scope is shown by default
  case 'operate-incident':
  case 'operate-incidents':
  case 'operate-incident-message': {
    await go(`/operate/processes/${arg}`);
    await page.waitForSelector('svg .djs-element', { timeout: 30000 }).catch(() => {});
    await settle(3500);
    if (view.startsWith('operate-incident')) {
      const banner = page.locator('[data-testid="incidents-banner"]').or(page.getByRole('button', { name: /incident/i })).first();
      await banner.click({ timeout: 10000 }).catch(() => console.warn('incident banner not found'));
      await settle(2500);
    }
    if (view === 'operate-incident-message') {
      // open the full error message of the first incident
      await page.getByRole('button', { name: 'More' }).first().click({ timeout: 10000 })
        .catch(() => console.warn('"More" button not found'));
      await settle(1500);
    }
    break;
  }
  case 'operate-decisions': {
    const q = arg && arg !== '-' ? `&name=${arg}&version=all` : '';
    await go(`/operate/decisions?evaluated=true&failed=true${q}`);
    await settle(4000);
    break;
  }
  case 'tasklist-list': {
    await go('/tasklist');
    await settle(3000);
    await dismissBanners();
    await settle(800);
    break;
  }
  case 'tasklist-task':
  case 'tasklist-complete': {
    const taskKey = await openTaskKey(arg);
    await page.setViewportSize({ width: 1600, height: 1900 });
    await go(`/tasklist/${taskKey}`);
    await settle(3000);
    await dismissBanners();
    if (view === 'tasklist-complete') {
      const values = JSON.parse(extra || '{}');
      const assign = page.getByRole('button', { name: 'Assign to me' });
      if (await assign.count()) {
        await assign.click();
        await page.getByRole('button', { name: 'Unassign' }).waitFor({ timeout: 15000 }).catch(() => {});
        await settle(1500);
      }
      for (const [field, value] of Object.entries(values)) {
        if (field === 'radio' || field.startsWith('radio:')) {
          // the form lives in its own scroll container; click through the DOM so form-js gets the change event
          const radio = page.getByLabel(value, { exact: true });
          await radio.evaluate((el) => { el.scrollIntoView({ block: 'center' }); el.click(); });
        } else if (field === 'textarea') {
          await page.locator('textarea').first().fill(value);
        } else {
          await page.getByLabel(field, { exact: false }).fill(String(value));
        }
      }
      // keep the filled-in decision fields in view for the evidence screenshot
      await page.locator('textarea').first().evaluate((el) => el.scrollIntoView({ block: 'center' })).catch(() => {});
      await settle(800);
      await page.screenshot({ path: out });
      await page.getByRole('button', { name: 'Complete Task' }).click();
      await page.getByText(/completed/i).first().waitFor({ timeout: 15000 }).catch(() => {});
      await settle(2000);
      await page.screenshot({ path: out.replace(/\.png$/, '-completed.png') });
      console.log('captured', out, '(+ -completed)');
      await browser.close();
      process.exit(0);
    }
    break;
  }
  default:
    console.error('unknown view', view);
    process.exit(1);
}

await page.screenshot({ path: out });
console.log('captured', out);
await browser.close();
