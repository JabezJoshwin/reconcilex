const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/presentation.js'), 'utf8');

function setup(reducedMotion = false, withGsap = false) {
  const nodes = new Map();
  const get = id => {
    if (!nodes.has(id)) nodes.set(id, {events: {}, attributes: {}, addEventListener(type, handler) { this.events[type] = handler; }, setAttribute(key, value) { this.attributes[key] = value; }});
    return nodes.get(id);
  };
  const preference = {matches: reducedMotion, addEventListener(type, handler) { this.change = handler; }};
  let matchMediaCalls = 0, reverts = 0;
  const gsap = {registerPlugin() {}, matchMedia() { matchMediaCalls++; return {add() {}, revert() { reverts++; }}; }};
  const window = {matchMedia: () => preference, addEventListener() {}};
  if (withGsap) Object.assign(window, {gsap, ScrollTrigger: {refresh() {}}});
  const classes = {};
  const document = {getElementById: get, querySelectorAll: () => [], documentElement: {classList: {toggle(key, value) { classes[key] = value; }}}};
  vm.runInNewContext(source, {window, document, gsap, ScrollTrigger: window.ScrollTrigger});
  return {get, preference, classes, calls: () => matchMediaCalls, reverts: () => reverts};
}

test('carousel works without animation libraries and wraps in both directions', () => {
  const ui = setup();
  ui.get('decision-prev').events.click();
  assert.equal(ui.get('decision-topic').textContent, 'Scope with intent');
  ui.get('decision-next').events.click();
  assert.equal(ui.get('decision-topic').textContent, 'Money representation');
  assert.doesNotMatch(source, /innerHTML/);
});

test('system reduced motion never creates animations', () => {
  const ui = setup(true, true);
  assert.equal(ui.calls(), 0);
  assert.equal(ui.get('motion-toggle').disabled, true);
  assert.equal(ui.classes['motion-paused'], true);
});

test('pause reverts animations and resume restores them', () => {
  const ui = setup(false, true);
  assert.equal(ui.calls(), 1);
  ui.get('motion-toggle').events.click();
  assert.equal(ui.reverts(), 1);
  assert.equal(ui.calls(), 1);
  assert.equal(ui.get('motion-toggle').attributes['aria-pressed'], 'true');
  ui.get('motion-toggle').events.click();
  assert.equal(ui.calls(), 2);
  assert.equal(ui.get('motion-toggle').attributes['aria-pressed'], 'false');
});

test('changing the system motion preference tears down running animations', () => {
  const ui = setup(false, true);
  ui.preference.matches = true;
  ui.preference.change();
  assert.equal(ui.reverts(), 1);
  assert.equal(ui.calls(), 1);
  assert.equal(ui.get('motion-toggle').textContent, 'Reduced motion enabled');
});
