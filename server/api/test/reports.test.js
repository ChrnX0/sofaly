// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scrub, cleanReport, cleanFrame, deviceTag, sameToken, MAX_TEXT, MAX_ERRORS, MAX_FRAMES } from '../src/reports.js';

test('tira e-mail, link, IP, MAC, telefone e coordenada do texto livre', () => {
  const t = scrub('Sou fulano@exemplo.com.br, liguem 11 98765-4321. TV em 192.168.1.153 (aa:bb:cc:dd:ee:ff), ' +
    'veja https://x.com/a?token=1 e 2001:db8::1, estou em -23.5505,-46.6333');
  for (const ruim of ['fulano@', '98765', '192.168', 'aa:bb', 'token', '2001:db8', '23.5505', '46.6333']) assert.ok(!t.includes(ruim), ruim);
  for (const m of ['<email>', '<num>', '<ip>', '<mac>', '<link>', '<coord>']) assert.ok(t.includes(m), m);
});

test('texto comum passa', () => {
  assert.equal(scrub('A HOME travou depois de abrir o YouTube às 21:30, versão 475033.'),
    'A HOME travou depois de abrir o YouTube às 21:30, versão 475033.');
  assert.equal(scrub('linha\u0000com\u0007controle'), 'linhacomcontrole');
});

test('quadro da pilha: só o lugar do código, nunca a mensagem', () => {
  assert.equal(cleanFrame('at br.paulo.tvlauncher.MainActivity.onCreate(MainActivity.kt:120)'), 'br.paulo.tvlauncher.MainActivity.onCreate(MainActivity.kt:120)');
  assert.equal(cleanFrame('java.lang.IllegalStateException: senha 123 do Paulo'), null);
  assert.equal(cleanFrame('android.os.Handler.dispatchMessage'), 'android.os.Handler.dispatchMessage');
});

test('o relato conferido guarda só os campos conhecidos, limpos e cortados', () => {
  const r = cleanReport({
    kind: 'errors', version: '3.4 (475033)', code: 475033, android: '14', model: 'Chromecast', mode: 'Completo',
    crashes: 2, freezes: 1, text: 'x'.repeat(MAX_TEXT + 50) + ' a@b.co', extra: 'não guarda',
    errors: Array.from({ length: MAX_ERRORS + 2 }, () => ({
      type: 'NullPointerException', day: '2026-11-26', version: 475033,
      stack: Array.from({ length: MAX_FRAMES + 5 }, (_, i) => `at a.B.c(B.kt:${i})`).concat(['mensagem com segredo']),
    })),
  });
  assert.equal(r.kind, 'errors');
  assert.equal(r.version, '', 'versão com espaço e parênteses não é aceita');
  assert.equal(r.text.length, MAX_TEXT);
  assert.equal(r.errors.length, MAX_ERRORS);
  assert.equal(r.errors[0].stack.length, MAX_FRAMES);
  assert.equal(r.extra, undefined);
  assert.equal(cleanReport({ kind: 'apagar' }), null);
  assert.equal(cleanReport(null), null);
  assert.equal(cleanReport({ kind: 'tv', errors: [{ type: 'rm -rf /' }] }).errors[0].type, 'error');
});

test('o id da TV vira um código curto que não volta ao id', async () => {
  const a = await deviceTag('abcdefghijklmnopqrst', 'sal');
  assert.match(a, /^[0-9a-f]{12}$/);
  assert.equal(a, await deviceTag('abcdefghijklmnopqrst', 'sal'));
  assert.notEqual(a, await deviceTag('abcdefghijklmnopqrsu', 'sal'));
});

test('token da fila', () => {
  assert.equal(sameToken('abc', 'abc'), true);
  assert.equal(sameToken('abc', 'abd'), false);
  assert.equal(sameToken('', ''), false);
  assert.equal(sameToken(undefined, 'abc'), false);
});
