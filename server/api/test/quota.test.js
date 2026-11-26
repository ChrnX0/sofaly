// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DAILY, NET_FACTOR, keysFor, limitFor, identify, quotaFor, utcDayMs } from '../src/quota.js';
import { signedText, idFromKey } from '../src/rules.js';

const b64u = (bytes) => Buffer.from(bytes).toString('base64url');

async function pedidoAssinado(method, url, body = new Uint8Array(0), skew = 0) {
  const par = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);
  const publica = new Uint8Array(await crypto.subtle.exportKey('raw', par.publicKey));
  const time = Math.floor(Date.now() / 1000) + skew;
  const texto = await signedText(method, new URL(url).pathname, time, body);
  const sig = new Uint8Array(await crypto.subtle.sign({ name: 'Ed25519' }, par.privateKey, texto));
  const req = new Request(url, { method, headers: { 'x-sofaly-key': b64u(publica), 'x-sofaly-time': String(time), 'x-sofaly-signature': b64u(sig) } });
  return { req, id: await idFromKey(publica) };
}

// Durable Object de mentira: um contador por chave, com a mesma regra do quotaStore.js.
function envFalso() {
  const dados = new Map();
  return {
    dados,
    QUOTA: {
      idFromName: (k) => k,
      get: (k) => ({
        take: async (bucket, limit, day) => {
          const c = `${k}|${bucket}|${day}`;
          const n = dados.get(c) ?? 0;
          if (n >= limit) return false;
          dados.set(c, n + 1);
          return true;
        },
      }),
    },
  };
}

test('quem assinou paga no aparelho e na rede; sem assinatura, só na rede', () => {
  assert.deepEqual(keysFor('a'.repeat(20), '203.0.113.7'), [`id:${'a'.repeat(20)}`, 'net:203.0.113.7']);
  assert.deepEqual(keysFor(null, '203.0.113.7'), ['net:203.0.113.7']);
  // IPv6: a casa é a /64, trocar o fim do endereço não dá cota nova.
  assert.deepEqual(keysFor(null, '2001:db8:1:2::99'), keysFor(null, '2001:db8:1:2::1'));
});

test('a rede ganha mais que um aparelho (várias casas atrás do mesmo IP)', () => {
  assert.equal(limitFor('weather', 'id:x'), DAILY.weather);
  assert.equal(limitFor('weather', 'net:1.2.3.4'), DAILY.weather * NET_FACTOR);
  assert.equal(limitFor('desconhecido', 'id:x'), 0);
});

test('reconhece o aparelho pela assinatura, e ignora assinatura ruim', async () => {
  const { req, id } = await pedidoAssinado('GET', 'https://api.sofaly.tv/v1/weather?lat=1&lon=2');
  assert.equal(await identify(req), id);
  const { req: velho } = await pedidoAssinado('GET', 'https://api.sofaly.tv/v1/weather', undefined, -3600);
  assert.equal(await identify(velho), null, 'relógio muito fora');
  assert.equal(await identify(new Request('https://api.sofaly.tv/v1/weather')), null, 'sem assinatura');
  const corpo = new TextEncoder().encode('{"a":1}');
  const { req: post } = await pedidoAssinado('POST', 'https://api.sofaly.tv/v1/summary', corpo);
  assert.equal(await identify(post, new TextEncoder().encode('{"a":2}')), null, 'corpo trocado');
});

test('uma TV gasta a cota dela, não a das outras', async () => {
  const env = envFalso();
  const agora = () => Date.UTC(2026, 10, 26, 12);
  const a = quotaFor(env, 'a'.repeat(20), '198.51.100.1', agora);
  for (let i = 0; i < DAILY.summary; i++) assert.equal(await a('summary'), true);
  assert.equal(await a('summary'), false, 'a TV A passou do teto');
  const b = quotaFor(env, 'b'.repeat(20), '198.51.100.1', agora);
  assert.equal(await b('summary'), true, 'a TV B, mesma rede, continua');
  // Outro dia, cota nova.
  const amanha = quotaFor(env, 'a'.repeat(20), '198.51.100.1', () => agora() + 86_400_000);
  assert.equal(await amanha('summary'), true);
  assert.equal(utcDayMs(agora()) + 1, utcDayMs(agora() + 86_400_000));
});

test('criar ids novos não fura o teto da rede', async () => {
  const env = envFalso();
  const agora = () => Date.UTC(2026, 10, 26, 12);
  let ok = 0;
  for (let i = 0; i < DAILY.report * NET_FACTOR + 20; i++) {
    const id = String(i).padStart(20, 'a').slice(-20);
    if (await quotaFor(env, id, '192.0.2.9', agora)('report')) ok++;
  }
  assert.equal(ok, DAILY.report * NET_FACTOR);
});

test('sem o contador (testes, preview), sempre pode; contador com defeito não derruba', async () => {
  assert.equal(await quotaFor({}, null, '1.2.3.4')('weather'), true);
  const quebrado = { QUOTA: { idFromName: (k) => k, get: () => ({ take: async () => { throw new Error('fora'); } }) } };
  assert.equal(await quotaFor(quebrado, null, '1.2.3.4')('weather'), true);
});
