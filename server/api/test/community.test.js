// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cleanReport, homeKey, parseKey, MAX_LESSONS } from '../src/community.js';

test('confere o que a TV manda', () => {
  assert.deepEqual(cleanReport({ lessons: [{ pkg: 'com.app.x', ver: 50, kind: 'crashy' }, { pkg: 'com.app.x', ver: 50, kind: 'crashy' }] }), ['com.app.x@50:crashy']);
  assert.deepEqual(cleanReport({ lessons: [] }), []);
  assert.equal(cleanReport({ lessons: [{ pkg: 'rm -rf', ver: 1, kind: 'crashy' }] }), null);
  assert.equal(cleanReport({ lessons: [{ pkg: 'com.app.x', ver: 0, kind: 'crashy' }] }), null);
  assert.equal(cleanReport({ lessons: [{ pkg: 'com.app.x', ver: 1, kind: 'apagar' }] }), null);
  assert.equal(cleanReport({ lessons: Array.from({ length: MAX_LESSONS + 1 }, (_, i) => ({ pkg: `com.a.b${i}`, ver: 1, kind: 'crashy' })) }), null);
  assert.equal(cleanReport(null), null);
  assert.equal(cleanReport({}), null);
});

test('a casa vira um código curto que não mostra o IP', async () => {
  const a = await homeKey('203.0.113.7', 'sal');
  assert.match(a, /^[0-9a-f]{16}$/);
  assert.equal(a, await homeKey('203.0.113.7', 'sal'));
  assert.notEqual(a, await homeKey('203.0.113.8', 'sal'));
  assert.notEqual(a, await homeKey('203.0.113.7', 'outro sal'));
  assert.ok(!a.includes('203'));
});

test('lê a chave da lição', () => {
  assert.deepEqual(parseKey('com.app.x@50:slower'), { pkg: 'com.app.x', ver: 50, kind: 'slower' });
  assert.equal(parseKey('lixo'), null);
});

test('a casa no IPv6 é a /64 (um aparelho não vira 5 casas); IPv4 inteiro', async () => {
  const { homeNet } = await import('../src/community.js');
  assert.equal(homeNet('2804:14c:65:8000::1'), '2804:14c:65:8000::/64');
  assert.equal(homeNet('2804:14c:65:8000:abcd:1:2:3'), '2804:14c:65:8000::/64');
  assert.equal(homeNet('2804:014c:0065:8000::ffff'), '2804:14c:65:8000::/64');
  assert.equal(homeNet('2001:db8::1'), '2001:db8:0:0::/64');
  assert.equal(homeNet('::1'), '0:0:0:0::/64');
  assert.equal(homeNet('203.0.113.7'), '203.0.113.7');
  assert.equal(homeNet(''), 'none');
  assert.equal(homeNet(undefined), 'none');
});
