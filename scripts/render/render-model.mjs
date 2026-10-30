// Renders a .bpmn or .dmn file to PNG with bpmn-js / dmn-js in headless Microsoft Edge.
// Usage: node render-model.mjs <model-file> <output.png>
import { chromium } from 'playwright-core';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const [, , modelPath, outPath] = process.argv;
if (!modelPath || !outPath) {
  console.error('Usage: node render-model.mjs <model-file> <output.png>');
  process.exit(1);
}
const xml = readFileSync(resolve(modelPath), 'utf8');
const nm = (p) => resolve(here, 'node_modules', p);

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 2400, height: 1400 }, deviceScaleFactor: 1.5 });
await page.setContent('<html><body style="margin:0;background:#fff"><div id="canvas" style="width:100vw;height:100vh"></div></body></html>');

if (modelPath.endsWith('.dmn')) {
  for (const css of ['dmn-js-shared.css', 'dmn-js-decision-table.css', 'dmn-js-drd.css', 'diagram-js.css', 'dmn-font/css/dmn-embedded.css']) {
    await page.addStyleTag({ path: nm(`dmn-js/dist/assets/${css}`) });
  }
  await page.addScriptTag({ path: nm('dmn-js/dist/dmn-viewer.production.min.js') });
  await page.evaluate(async (xml) => {
    const viewer = new window.DmnJS({ container: '#canvas' });
    await viewer.importXML(xml);
    await viewer.open(viewer.getViews().find((v) => v.type === 'decisionTable'));
  }, xml);
  await page.locator('.tjs-container').screenshot({ path: resolve(outPath) });
} else {
  for (const css of ['diagram-js.css', 'bpmn-js.css', 'bpmn-font/css/bpmn-embedded.css']) {
    await page.addStyleTag({ path: nm(`bpmn-js/dist/assets/${css}`) });
  }
  await page.addScriptTag({ path: nm('bpmn-js/dist/bpmn-viewer.production.min.js') });
  const box = await page.evaluate(async (xml) => {
    const viewer = new window.BpmnJS({ container: '#canvas' });
    window.__viewer = viewer;
    const { warnings } = await viewer.importXML(xml);
    const b = viewer.get('canvas').getActiveLayer().getBBox();
    return { x: b.x, y: b.y, w: b.width + 60, h: b.height + 60, warnings: warnings.map((w) => w.message) };
  }, xml);
  if (box.warnings.length) console.warn('import warnings:', box.warnings);
  await page.setViewportSize({ width: Math.ceil(box.w), height: Math.ceil(box.h) });
  await page.waitForTimeout(100);
  await page.evaluate((box) => {
    const canvas = window.__viewer.get('canvas');
    canvas.resized();
    // 1:1 zoom with a 30 px margin around the diagram
    canvas.viewbox({ x: box.x - 30, y: box.y - 30, width: box.w, height: box.h });
  }, box);
  await page.waitForTimeout(200);
  await page.screenshot({ path: resolve(outPath) });
}
await browser.close();
console.log('rendered', outPath);
