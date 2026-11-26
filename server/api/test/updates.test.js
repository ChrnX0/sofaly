// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseHold, holdBody } from '../src/updates.js';

test('lista de versões seguradas: só números, sem repetir', () => {
  assert.deepEqual(parseHold('475010, 475020,lixo,475010,, -3, 1e5'), [475010, 475020]);
  assert.deepEqual(parseHold(''), []);
  assert.deepEqual(parseHold(undefined), []);
  assert.deepEqual(parseHold('0'), []);
});

test('no máximo 50', () => {
  const muitos = Array.from({ length: 80 }, (_, i) => 1000 + i).join(',');
  assert.equal(parseHold(muitos).length, 50);
});

test('corpo da rota', () => {
  assert.deepEqual(holdBody({ UPDATES_HOLD: '475010' }), { hold: [475010] });
  assert.deepEqual(holdBody({}), { hold: [] });
});
