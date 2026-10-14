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
const explanations = {
  MATCHED: 'Both sources contain one record with this payment reference. The amount and currency agree.',
  AMOUNT_MISMATCH: 'Both sources contain one record. The amounts differ, while the currency agrees.',
  CURRENCY_MISMATCH: 'Both sources contain one record. The currency codes differ, while the integer amounts agree. No currency conversion is performed.',
  AMOUNT_AND_CURRENCY_MISMATCH: 'Both sources contain one record. Both the amount and the currency differ. No currency conversion is performed.',
  MISSING_IN_PROCESSOR: 'The internal record has no corresponding payment reference in the processor file.',
  UNEXPECTED_IN_PROCESSOR: 'The processor record has no corresponding payment reference in the internal file.'
};
const $ = (id) => document.getElementById(id);
const pageSize = 100;
const maxFileBytes = 5 * 1024 * 1024;
const number = (value) => new Intl.NumberFormat().format(value);
let activeRun = null;
let currentPage = 0;
let totalFindings = 0;
let busy = false;
let renderedClassification = '';
let detailTrigger = null;

for (const classification of classifications) {
  const option = document.createElement('option');
  option.value = classification;
  option.textContent = labels[classification];
  $('classification-filter').append(option);
}

function tone(classification) {
  if (classification === 'MATCHED') return 'success';
  if (classification.includes('MISMATCH')) return 'error';
  return 'warning';
}

function setFeedback(message, state = 'info') {
  const feedback = $('feedback');
  feedback.textContent = message;
  feedback.dataset.state = state;
  feedback.setAttribute('role', state === 'error' ? 'alert' : 'status');
  feedback.setAttribute('aria-live', state === 'error' ? 'assertive' : 'polite');
  feedback.hidden = !message;
}

function updatePagination() {
  $('previous-page').disabled = busy || currentPage === 0;
  $('next-page').disabled = busy || (currentPage + 1) * pageSize >= totalFindings;
}

function setBusy(value, action = '') {
  busy = value;
  for (const id of ['reconcile-button', 'retrieve-button', 'classification-filter', 'internal-file', 'processor-file', 'run-id', 'new-reconciliation', 'clear-filter']) $(id).disabled = value;
  for (const button of document.querySelectorAll('.detail-button')) button.disabled = value;
  $('reconcile-button').textContent = value && action === 'upload' ? 'Reconciling…' : 'Run reconciliation';
  $('retrieve-button').textContent = value && action === 'retrieve' ? 'Opening…' : 'Open run';
  $('results').setAttribute('aria-busy', String(value));
  updatePagination();
}

async function request(url, options) {
  let response;
  try {
    if (options && !['GET','HEAD','OPTIONS'].includes(options.method || 'GET')) {
      const tokenResponse = await fetch('/api/auth/csrf');
      if (!tokenResponse.ok) throw new Error('Cannot obtain request protection');
      const csrf = await tokenResponse.json();
      options = {...options, headers: {...options.headers, [csrf.headerName]: csrf.token}};
    }
    response = await (typeof window !== 'undefined' && window.ReconcileXAuth ? window.ReconcileXAuth.fetch(url, options) : fetch(url, options));
  }
  catch { throw new Error('Cannot reach ReconcileX. Check that the application is running, then retry.'); }
  if (response.status === 401) {
    if (typeof window !== 'undefined') window.location.assign('/login');
    throw new Error('Your session expired. Sign in again.');
  }
  let data;
  try { data = await response.json(); }
  catch { throw new Error(`The server returned an unreadable response (HTTP ${response.status}).`); }
  if (!response.ok) throw new Error(data.message || `Request failed (HTTP ${response.status}).`);
  return data;
}

function renderSummary(run) {
  $('internal-count').textContent = number(run.summary.internalRows);
  $('processor-count').textContent = number(run.summary.processorRows);
  $('matched-count').textContent = number(run.summary.counts.MATCHED);
  $('discrepancy-count').textContent = number(run.summary.distinctIds - run.summary.counts.MATCHED);
  $('distinct-count').textContent = `${number(run.summary.distinctIds)} distinct payment references`;
  const date = new Date(run.createdAt).toLocaleString(undefined, {timeZone: 'UTC'});
  $('run-metadata').textContent = `${run.id} · ${date} UTC · ${run.status}`;
  $('run-id').value = run.id;
  $('run-id').removeAttribute('aria-invalid');
  $('export-link').href = `/api/reconciliations/${encodeURIComponent(run.id)}/export`;
  $('input-summary').textContent = `${number(run.summary.internalRows)} internal rows · ${number(run.summary.processorRows)} processor rows`;
  $('classification-counts').replaceChildren();
  for (const classification of classifications) {
    const item = document.createElement('div');
    const label = document.createElement('dt');
    const count = document.createElement('dd');
    label.textContent = labels[classification];
    count.textContent = number(run.summary.counts[classification]);
    item.append(label, count);
    $('classification-counts').append(item);
  }
  for (const option of $('classification-filter').options) {
    option.textContent = option.value ? `${labels[option.value]} (${number(run.summary.counts[option.value])})` : `All classifications (${number(run.summary.distinctIds)})`;
  }
}

function appendSourceValue(cell, amount, currency, occurrences) {
  cell.className = 'numeric';
  if (occurrences > 1) {
    const ambiguous = document.createElement('span');
    ambiguous.className = 'source-ambiguous';
    ambiguous.textContent = 'Ambiguous';
    cell.append(ambiguous);
  } else if (amount === null || amount === undefined) {
    cell.textContent = '—';
  } else {
    // Monetary values are server-provided decimal strings. Never convert them to Number.
    const value = document.createElement('span');
    value.textContent = amount;
    const code = document.createElement('span');
    code.className = 'currency-code';
    code.textContent = currency;
    cell.append(value, code);
  }
}

function renderDetails(run) {
  if ($('transaction-dialog').open) $('transaction-dialog').close();
  activeRun = run.id;
  currentPage = run.page;
  totalFindings = run.totalFindings;
  renderSummary(run);
  $('findings-body').replaceChildren();
  for (const finding of run.findings) {
    const row = document.createElement('tr');
    const reference = document.createElement('td');
    reference.className = 'reference-cell';
    reference.textContent = finding.transactionId;
    reference.title = finding.transactionId;
    const statusCell = document.createElement('td');
    const status = document.createElement('span');
    status.className = 'finding-status';
    status.dataset.tone = tone(finding.classification);
    status.textContent = labels[finding.classification] || finding.classification;
    statusCell.append(status);
    const internal = document.createElement('td');
    const processor = document.createElement('td');
    appendSourceValue(internal, finding.internalAmountMinor, finding.internalCurrency, finding.internalOccurrences);
    appendSourceValue(processor, finding.processorAmountMinor, finding.processorCurrency, finding.processorOccurrences);
    const occurrences = document.createElement('td');
    occurrences.className = 'numeric';
    occurrences.textContent = `${finding.internalOccurrences} / ${finding.processorOccurrences}`;
    const action = document.createElement('td');
    const detail = document.createElement('button');
    detail.type = 'button';
    detail.className = 'detail-button';
    detail.textContent = 'Details';
    detail.disabled = busy;
    detail.setAttribute('aria-label', `View details for ${finding.transactionId}`);
    detail.addEventListener('click', () => openFinding(finding, detail));
    action.append(detail);
    row.append(reference, statusCell, internal, processor, occurrences, action);
    $('findings-body').append(row);
  }
  const empty = run.findings.length === 0;
  $('empty-message').hidden = !empty;
  $('empty-title').textContent = run.summary.distinctIds === 0 ? 'No transactions in this run' : 'No findings in this view';
  $('empty-description').textContent = run.summary.distinctIds === 0 ? 'Both source files contain headers only. The empty reconciliation was saved successfully.' : 'Choose another classification or clear the filter to see all findings.';
  const first = empty ? 0 : currentPage * pageSize + 1;
  const last = empty ? 0 : currentPage * pageSize + run.findings.length;
  $('page-description').textContent = `${number(first)}–${number(last)} of ${number(totalFindings)} findings`;
  $('initial-state').hidden = true;
  $('results').hidden = false;
  updatePagination();
}

async function loadRun(id, page = 0, classification = $('classification-filter').value) {
  const params = new URLSearchParams({page: String(page), size: String(pageSize)});
  if (classification) params.set('classification', classification);
  const run = await request(`/api/reconciliations/${encodeURIComponent(id)}?${params}`);
  renderDetails(run);
  renderedClassification = classification;
  $('classification-filter').value = classification;
  $('classification-filter').classList.toggle('has-filter', Boolean(classification));
  $('clear-filter').hidden = !classification;
}

function openFinding(finding, trigger) {
  detailTrigger = trigger;
  $('detail-reference').textContent = finding.transactionId;
  $('detail-classification').textContent = labels[finding.classification] || finding.classification;
  $('detail-classification').dataset.tone = tone(finding.classification);
  const duplicate = finding.classification.startsWith('DUPLICATE_');
  $('detail-explanation').textContent = duplicate ? `This reference occurs ${finding.internalOccurrences} time(s) internally and ${finding.processorOccurrences} time(s) in the processor file. The reference is ambiguous and cannot be counted as matched.` : explanations[finding.classification];
  $('transaction-dialog').querySelector('.detail-note').hidden = !duplicate;
  for (const source of ['internal', 'processor']) {
    const amount = finding[`${source}AmountMinor`];
    const currency = finding[`${source}Currency`];
    const occurrences = finding[`${source}Occurrences`];
    $(`detail-${source}-amount`).textContent = occurrences > 1 ? 'Ambiguous' : (amount ?? '—');
    $(`detail-${source}-currency`).textContent = occurrences > 1 ? 'Ambiguous' : (currency ?? '—');
    $(`detail-${source}-occurrences`).textContent = number(occurrences);
    $(`detail-${source}-amount`).dataset.different = String(finding.classification === 'AMOUNT_MISMATCH' || finding.classification === 'AMOUNT_AND_CURRENCY_MISMATCH');
    $(`detail-${source}-currency`).dataset.different = String(finding.classification === 'CURRENCY_MISMATCH' || finding.classification === 'AMOUNT_AND_CURRENCY_MISMATCH');
  }
  $('transaction-dialog').showModal();
}

$('close-detail').addEventListener('click', () => $('transaction-dialog').close());
$('transaction-dialog').addEventListener('close', () => {
  if (detailTrigger?.isConnected) detailTrigger.focus();
  detailTrigger = null;
});

function updateFile(source) {
  const input = $(`${source}-file`);
  const file = input.files[0];
  input.removeAttribute('aria-invalid');
  $(`${source}-field`).classList.toggle('is-selected', Boolean(file));
  $(`${source}-description`).textContent = file ? `${file.name} · ${number(file.size)} bytes` : 'No file selected · CSV, up to 5 MiB';
}
for (const source of ['internal', 'processor']) $(`${source}-file`).addEventListener('change', () => updateFile(source));

$('new-reconciliation').addEventListener('click', () => {
  if (busy) return;
  $('upload-form').reset();
  for (const source of ['internal', 'processor']) updateFile(source);
  $('upload-panel').open = true;
  $('input-summary').textContent = 'Internal and processor CSV exports';
  setFeedback('');
  $('internal-file').focus();
  $('upload-panel').scrollIntoView({block: 'nearest'});
});

$('upload-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const data = new FormData();
  for (const source of ['internal', 'processor']) {
    const input = $(`${source}-file`);
    const file = input.files[0];
    input.removeAttribute('aria-invalid');
    if (!file || file.size > maxFileBytes) {
      input.setAttribute('aria-invalid', 'true');
      setFeedback(!file ? 'Choose both CSV files before starting.' : `The ${source} file exceeds the 5 MiB limit.`, 'error');
      input.focus();
      return;
    }
    data.append(`${source}File`, file);
  }
  setBusy(true, 'upload');
  setFeedback('Validating files and reconciling payment references…', 'loading');
  let savedId = null;
  try {
    const run = await request('/api/reconciliations', {method: 'POST', body: data});
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('reconciliation-saved'));
    savedId = run.id;
    activeRun = run.id;
    $('run-id').value = run.id;
    $('classification-filter').value = '';
    $('results').hidden = true;
    $('initial-state').hidden = false;
    await loadRun(run.id, 0, '');
    $('upload-panel').open = false;
    setFeedback('Reconciliation complete. The report has been saved.', 'success');
    $('results').scrollIntoView({block: 'nearest'});
  } catch (error) {
    setFeedback(savedId ? `Run ${savedId} was saved, but the report could not be loaded. Open this run ID to retry. ${error.message}` : error.message, 'error');
    if (savedId) $('retrieve-panel').open = true;
  } finally { setBusy(false); }
});

$('retrieve-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const id = $('run-id').value.trim();
  $('run-id').removeAttribute('aria-invalid');
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
    $('run-id').setAttribute('aria-invalid', 'true');
    setFeedback('Enter a valid UUID run ID.', 'error');
    $('run-id').focus();
    return;
  }
  setBusy(true, 'retrieve');
  setFeedback('Loading saved report…', 'loading');
  try {
    await loadRun(id, 0, '');
    $('upload-panel').open = false;
    setFeedback('Saved report loaded.', 'success');
  } catch (error) {
    $('classification-filter').value = renderedClassification;
    setFeedback(error.message, 'error');
  } finally { setBusy(false); }
});

async function changePage(page, classification = $('classification-filter').value) {
  if (busy || !activeRun) return;
  setBusy(true);
  setFeedback('Loading findings…', 'loading');
  try { await loadRun(activeRun, page, classification); setFeedback(''); }
  catch (error) { $('classification-filter').value = renderedClassification; setFeedback(error.message, 'error'); }
  finally { setBusy(false); }
}
$('previous-page').addEventListener('click', () => changePage(currentPage - 1));
$('next-page').addEventListener('click', () => changePage(currentPage + 1));
$('classification-filter').addEventListener('change', () => changePage(0));
$('clear-filter').addEventListener('click', () => changePage(0, ''));
