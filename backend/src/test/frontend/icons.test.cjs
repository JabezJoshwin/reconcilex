const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../../main/resources/static');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const sprite = fs.readFileSync(path.join(root, 'assets/phosphor.svg'), 'utf8');

test('every UI icon references a locally vendored Phosphor symbol', () => {
  const symbols = new Set([...sprite.matchAll(/<symbol id="([^"]+)"/g)].map(match => match[1]));
  const uses = [...html.matchAll(/<use href="\/assets\/phosphor\.svg#([^"]+)"><\/use>/g)];
  assert.equal(symbols.size, 9);
  assert.equal(uses.length, 16);
  for (const [, id] of uses) assert.ok(symbols.has(id), `Missing symbol: ${id}`);
  assert.doesNotMatch(sprite, /<script|on\w+=|https?:\/\/(?!www\.w3\.org)/);
  assert.match(fs.readFileSync(path.join(root, 'assets/phosphor-LICENSE.txt'), 'utf8'), /MIT License/);
});

test('decorative icons are hidden and icon-only controls retain accessible names', () => {
  const icons = [...html.matchAll(/<svg class="ph-icon"([^>]*)>/g)];
  assert.equal(icons.length, 16);
  for (const [, attributes] of icons) {
    assert.match(attributes, /aria-hidden="true"/);
    assert.match(attributes, /focusable="false"/);
  }
  for (const direction of ['Previous', 'Next']) {
    assert.match(html, new RegExp(`aria-label="${direction} design decision"><svg class="ph-icon"`));
  }
  assert.doesNotMatch(html, /aria-hidden="true">[↗↓↑←→≠↳]/);
});
