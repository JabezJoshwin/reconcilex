// Dependency-free DOM contract checks; real browser layout is verified separately.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8');

function setup() {
  const nodes = new Map();
  class Element {
    constructor() { this.children = []; this.events = {}; this.dataset = {}; this.attributes = {}; this.value = ''; this.files = []; this.isConnected = true; this.classList = {toggle() {}}; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(key, value) { this.attributes[key] = value; }
    removeAttribute(key) { delete this.attributes[key]; }
    addEventListener(type, handler) { this.events[type] = handler; }
    querySelector() { return get('detail-note'); }
    get options() { return this.children; }
    showModal() { this.open = true; }
    close() { this.open = false; this.events.close?.(); }
    focus() { this.focused = true; }
    scrollIntoView() {}
    reset() { get('internal-file').files = []; get('processor-file').files = []; }
  }
  function get(id) { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); }
  get('classification-filter').append(Object.assign(new Element(), {value: ''}));
  const context = vm.createContext({document: {getElementById: get, createElement: () => new Element(), querySelectorAll: () => []}, Intl, Date, URLSearchParams, FormData: class {append() {}}, fetch: async () => {throw Error('offline');}});
  vm.runInContext(source, context);
  return {get, context, call: (code) => vm.runInContext(code, context)};
}
const finding = {transactionId: '<img src=x onerror=alert(1)>', classification: 'AMOUNT_AND_CURRENCY_MISMATCH', internalAmountMinor: '9223372036854775807', processorAmountMinor: '9223372036854775806', internalCurrency: 'USD', processorCurrency: 'INR', internalOccurrences: 1, processorOccurrences: 1};
function report(findings = [finding]) {
  return {id: '4d9e6849-27c0-41bc-839e-55f596113ee1', createdAt: '2026-10-13T06:59:30Z', status: 'COMPLETED', page: 0, totalFindings: findings.length, findings, summary: {internalRows: findings.length, processorRows: findings.length, distinctIds: findings.length, counts: Object.fromEntries(['MATCHED','AMOUNT_MISMATCH','CURRENCY_MISMATCH','AMOUNT_AND_CURRENCY_MISMATCH','MISSING_IN_PROCESSOR','UNEXPECTED_IN_PROCESSOR','DUPLICATE_INTERNAL','DUPLICATE_PROCESSOR','DUPLICATE_BOTH'].map(c => [c, c === 'AMOUNT_AND_CURRENCY_MISMATCH' ? findings.length : 0]))}};
}
test('renders untrusted references as text and preserves long amounts exactly', () => {
  const ui = setup(); ui.context.run = report(); ui.call('renderDetails(run)');
  const row = ui.get('findings-body').children[0];
  assert.equal(row.children[0].textContent, finding.transactionId);
  assert.equal(row.children[2].children[0].textContent, finding.internalAmountMinor);
  assert.equal(ui.get('export-link').href, `/api/reconciliations/${report().id}/export`);
  assert.equal(ui.get('results').hidden, false);
  assert.doesNotMatch(source, /innerHTML/);
});
test('detail compares exact source values and identifies both differing fields', () => {
  const ui = setup(); ui.context.finding = finding; ui.call('openFinding(finding, null)');
  assert.equal(ui.get('transaction-dialog').open, true);
  assert.equal(ui.get('detail-internal-amount').textContent, finding.internalAmountMinor);
  assert.equal(ui.get('detail-internal-amount').dataset.different, 'true');
  assert.equal(ui.get('detail-processor-currency').dataset.different, 'true');
});
test('duplicates remain ambiguous with occurrence counts, not chosen source amounts', () => {
  const ui = setup(); ui.context.finding = {...finding, classification: 'DUPLICATE_BOTH', internalOccurrences: 2, processorOccurrences: 3}; ui.call('openFinding(finding, null)');
  assert.equal(ui.get('detail-internal-amount').textContent, 'Ambiguous');
  assert.equal(ui.get('detail-processor-occurrences').textContent, '3');
  assert.equal(ui.get('detail-note').hidden, false);
});
test('header-only runs show genuine empty state and disable pagination', () => {
  const ui = setup(); ui.context.run = report([]); ui.call('renderDetails(run)');
  assert.equal(ui.get('empty-title').textContent, 'No transactions in this run');
  assert.equal(ui.get('page-description').textContent, '0–0 of 0 findings');
  assert.equal(ui.get('next-page').disabled, true);
});
test('missing and oversized uploads fail before any request and focus invalid field', async () => {
  const ui = setup();
  await ui.get('upload-form').events.submit({preventDefault() {}});
  assert.equal(ui.get('internal-file').attributes['aria-invalid'], 'true');
  assert.equal(ui.get('internal-file').focused, true);
  ui.get('internal-file').files = [{name: 'huge.csv', size: 5242881}];
  await ui.get('upload-form').events.submit({preventDefault() {}});
  assert.match(ui.get('feedback').textContent, /exceeds/);
  assert.equal(ui.get('feedback').attributes.role, 'alert');
});
test('filter requests use existing API and failed requests restore selected filter', async () => {
  const ui = setup(); ui.context.run = report(); ui.call('renderDetails(run)');
  let url; ui.context.fetch = async value => {url = value; return {ok: true, json: async () => report()};};
  await ui.call("changePage(0, 'AMOUNT_AND_CURRENCY_MISMATCH')");
  assert.match(url, /size=100&classification=AMOUNT_AND_CURRENCY_MISMATCH/);
  assert.equal(ui.get('clear-filter').hidden, false);
  ui.context.fetch = async () => {throw Error('offline');};
  await ui.call("changePage(0, 'MATCHED')");
  assert.equal(ui.get('classification-filter').value, 'AMOUNT_AND_CURRENCY_MISMATCH');
  assert.equal(ui.get('classification-filter').disabled, false);
  assert.match(ui.get('feedback').textContent, /Cannot reach/);
});
test('pending upload disables controls and recovers after network failure', async () => {
  const ui = setup();
  for (const source of ['internal', 'processor']) ui.get(`${source}-file`).files = [{name: 'valid.csv', size: 100}];
  let reject; ui.context.fetch = () => new Promise((resolve, fail) => {reject = fail;});
  const pending = ui.get('upload-form').events.submit({preventDefault() {}});
  assert.equal(ui.get('reconcile-button').disabled, true);
  assert.equal(ui.get('reconcile-button').textContent, 'Reconciling…');
  assert.equal(ui.get('feedback').dataset.state, 'loading');
  reject(Error('offline')); await pending;
  assert.equal(ui.get('reconcile-button').disabled, false);
  assert.equal(ui.get('feedback').dataset.state, 'error');
});
