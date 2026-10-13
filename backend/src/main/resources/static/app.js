'use strict';

const classifications = [
  'MATCHED', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH', 'AMOUNT_AND_CURRENCY_MISMATCH',
  'MISSING_IN_PROCESSOR', 'UNEXPECTED_IN_PROCESSOR', 'DUPLICATE_INTERNAL',
  'DUPLICATE_PROCESSOR', 'DUPLICATE_BOTH'
];
const labels = {
  MATCHED: 'Matched', AMOUNT_MISMATCH: 'Amount mismatch', CURRENCY_MISMATCH: 'Currency mismatch',
  AMOUNT_AND_CURRENCY_MISMATCH: 'Amount & currency mismatch', MISSING_IN_PROCESSOR: 'Missing in processor',
  UNEXPECTED_IN_PROCESSOR: 'Unexpected in processor', DUPLICATE_INTERNAL: 'Duplicate internal',
  DUPLICATE_PROCESSOR: 'Duplicate processor', DUPLICATE_BOTH: 'Duplicate both'
};
const $ = (id) => document.getElementById(id);
const pageSize = 100;
const maxFileBytes = 5 * 1024 * 1024;
let activeRun = null;
let currentPage = 0;
let totalFindings = 0;
let busy = false;
let renderedClassification = '';

for (const classification of classifications) {
  const option = document.createElement('option');
  option.value = classification;
  option.textContent = labels[classification];
  $('classification-filter').append(option);
}

function setFeedback(message, error = false) {
  $('feedback').textContent = message;
  $('feedback').classList.toggle('error', error);
  $('feedback').hidden = !message;
}

function setBusy(value) {
  busy = value;
  for (const id of ['reconcile-button', 'retrieve-button', 'classification-filter', 'internal-file', 'processor-file', 'run-id']) {
    $(id).disabled = value;
  }
  $('reconcile-button').textContent = value ? 'Working…' : 'Run reconciliation →';
  $('previous-page').disabled = value || currentPage === 0;
  $('next-page').disabled = value || (currentPage + 1) * pageSize >= totalFindings;
  $('results').setAttribute('aria-busy', String(value));
}

async function request(url, options) {
  let response;
  try { response = await fetch(url, options); }
  catch { throw new Error('Cannot reach ReconcileX. Check that the application is running, then retry.'); }
  let data;
  try { data = await response.json(); }
  catch { throw new Error(`The server returned an unreadable response (HTTP ${response.status}).`); }
  if (!response.ok) throw new Error(data.message || `Request failed (HTTP ${response.status}).`);
  return data;
}

function number(value) { return new Intl.NumberFormat().format(value); }

function renderSummary(run) {
  $('internal-count').textContent = number(run.summary.internalRows);
  $('processor-count').textContent = number(run.summary.processorRows);
  $('matched-count').textContent = number(run.summary.counts.MATCHED);
  $('discrepancy-count').textContent = number(run.summary.distinctIds - run.summary.counts.MATCHED);
  $('distinct-count').textContent = `${number(run.summary.distinctIds)} distinct payment IDs`;
  $('run-metadata').textContent = `Run ${run.id} · ${new Date(run.createdAt).toLocaleString()} · ${run.status}`;
  $('run-id').value = run.id;
  $('export-link').href = `/api/reconciliations/${encodeURIComponent(run.id)}/export`;
  $('classification-counts').replaceChildren();
  for (const classification of classifications) {
    const chip = document.createElement('span');
    chip.className = 'count-chip';
    chip.textContent = labels[classification];
    const count = document.createElement('strong');
    count.textContent = number(run.summary.counts[classification]);
    chip.append(count);
    $('classification-counts').append(chip);
  }
}

function sourceValue(amount, currency, occurrences) {
  if (occurrences > 1) return 'Ambiguous duplicate';
  if (amount === null || amount === undefined) return '—';
  // Amounts arrive as decimal strings: never convert money to a JS Number.
  return `${amount} ${currency}`;
}

function renderDetails(run) {
  activeRun = run.id;
  currentPage = run.page;
  totalFindings = run.totalFindings;
  renderSummary(run);
  $('findings-body').replaceChildren();
  for (const finding of run.findings) {
    const row = document.createElement('tr');
    const values = [finding.transactionId, finding.classification,
      sourceValue(finding.internalAmountMinor, finding.internalCurrency, finding.internalOccurrences),
      sourceValue(finding.processorAmountMinor, finding.processorCurrency, finding.processorOccurrences),
      `${finding.internalOccurrences} / ${finding.processorOccurrences}`];
    for (let index = 0; index < values.length; index++) {
      const cell = document.createElement('td');
      if (index === 1) {
        const badge = document.createElement('span');
        badge.className = 'status-badge';
        if (finding.classification === 'MATCHED') badge.classList.add('match');
        if (finding.classification.startsWith('DUPLICATE_')) badge.classList.add('duplicate');
        badge.textContent = labels[finding.classification] || finding.classification;
        cell.append(badge);
      } else { cell.textContent = values[index]; }
      row.append(cell);
    }
    $('findings-body').append(row);
  }
  $('empty-message').hidden = run.findings.length !== 0;
  const first = totalFindings === 0 ? 0 : currentPage * pageSize + 1;
  const last = currentPage * pageSize + run.findings.length;
  $('page-description').textContent = `${number(first)}–${number(last)} of ${number(totalFindings)} findings`;
  $('results').hidden = false;
  setBusy(false);
}

async function loadRun(id, page = 0, classification = $('classification-filter').value) {
  const params = new URLSearchParams({page: String(page), size: String(pageSize)});
  if (classification) params.set('classification', classification);
  const run = await request(`/api/reconciliations/${encodeURIComponent(id)}?${params}`);
  renderDetails(run);
  renderedClassification = classification;
  $('classification-filter').value = classification;
}

for (const source of ['internal', 'processor']) {
  $(`${source}-file`).addEventListener('change', () => {
    const file = $(`${source}-file`).files[0];
    $(`${source}-description`).textContent = file ? `${file.name} · ${number(file.size)} bytes` : 'Choose a CSV file · up to 5 MB';
  });
}

$('upload-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const data = new FormData();
  for (const source of ['internal', 'processor']) {
    const file = $(`${source}-file`).files[0];
    if (!file) { setFeedback('Choose both CSV files before starting.', true); return; }
    if (file.size > maxFileBytes) { setFeedback(`The ${source} file exceeds the 5 MB limit.`, true); return; }
    data.append(`${source}File`, file);
  }
  setBusy(true);
  setFeedback('Validating files and reconciling payment references…');
  try {
    const run = await request('/api/reconciliations', {method: 'POST', body: data});
    $('classification-filter').value = '';
    // Clear the previous report immediately so a failed reload cannot show stale findings.
    $('results').hidden = true;
    activeRun = run.id;
    $('run-id').value = run.id;
    await loadRun(run.id, 0, '');
    setFeedback('Reconciliation complete. Your report has been saved.');
    $('results').scrollIntoView({behavior: 'smooth', block: 'start'});
  } catch (error) { setFeedback(error.message, true); }
  finally { setBusy(false); }
});

$('retrieve-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const id = $('run-id').value.trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
    setFeedback('Enter a valid UUID run ID.', true); return;
  }
  setBusy(true);
  setFeedback('Loading saved report…');
  try { await loadRun(id, 0, ''); $('classification-filter').value = ''; setFeedback('Saved report loaded.'); }
  catch (error) { $('classification-filter').value = renderedClassification; setFeedback(error.message, true); }
  finally { setBusy(false); }
});

async function changePage(page) {
  if (busy || !activeRun) return;
  setBusy(true);
  setFeedback('Loading findings…');
  try { await loadRun(activeRun, page); setFeedback(''); }
  catch (error) { $('classification-filter').value = renderedClassification; setFeedback(error.message, true); }
  finally { setBusy(false); }
}
$('previous-page').addEventListener('click', () => changePage(currentPage - 1));
$('next-page').addEventListener('click', () => changePage(currentPage + 1));
$('classification-filter').addEventListener('change', () => changePage(0));
