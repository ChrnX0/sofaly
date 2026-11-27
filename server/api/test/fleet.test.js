// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cleanPacket, minHomes, summarize, MAX_CASES } from '../src/fleet.js';

const device = { maker: 'Google', model: 'Chromecast HD', platform: 'googletv', sdk: 31, abi: '32', ram: '1-2', disk: '4-8' };
const caso = (o = {}) => ({
  type: 'CRASH', area: 'app', target: 'com.app.x', cause: 'update', before: ['app_updated'],
  state: { uptime: '1-24h', memory: 'ok', net: 'wifi', period: 'evening' },
  tries: [{ tool: 'CLEAR_APP_CACHE', by: 'ai', result: 'fixed' }], outcome: 'fixed', relapse: 'none', count: 2, ...o,
});
const pacote = (o = {}) => ({ v: 1, code: '0123456789abcdef', app: 474900, device, cases: [caso()], ...o });

test('aceita o pacote certo', () => {
  const p = cleanPacket(pacote());
  assert.equal(p.cases.length, 1);
  assert.equal(p.cases[0].target, 'com.app.x');
  assert.deepEqual(cleanPacket(pacote({ cases: [] })).cases, []);
});

test('qualquer campo a mais recusa o pacote inteiro (nada passa calado)', () => {
  assert.equal(cleanPacket({ ...pacote(), wifi: 'Casa do Paulo' }), null);
  assert.equal(cleanPacket(pacote({ device: { ...device, serial: 'X1' } })), null);
  assert.equal(cleanPacket(pacote({ cases: [{ ...caso(), detail: 'Jornal Nacional' }] })), null);
  assert.equal(cleanPacket(pacote({ cases: [caso({ state: { ...caso().state, hour: 21 } })] })), null);
  assert.equal(cleanPacket(pacote({ cases: [caso({ tries: [{ tool: 'REBOOT', by: 'rule', result: 'fixed', why: 'x' }] })] })), null);
});

test('valor fora da lista fechada recusa', () => {
  assert.equal(cleanPacket(pacote({ cases: [caso({ cause: 'o vizinho' })] })), null);
  assert.equal(cleanPacket(pacote({ cases: [caso({ before: ['assistiu novela'] })] })), null);
  assert.equal(cleanPacket(pacote({ cases: [caso({ tries: [{ tool: 'rm -rf', by: 'ai', result: 'fixed' }] })] })), null);
  assert.equal(cleanPacket(pacote({ device: { ...device, ram: '1536 MB' } })), null);
});

test('o alvo só pode ser pacote (área app) ou nome do nosso código', () => {
  assert.equal(cleanPacket(pacote({ cases: [caso({ target: 'Paulo Eduardo' })] })), null);
  assert.equal(cleanPacket(pacote({ cases: [caso({ area: 'feature', target: 'mcp' })] })).cases[0].target, 'mcp');
  assert.equal(cleanPacket(pacote({ cases: [caso({ area: 'feature', target: 'Sala da Ana' })] })), null);
  assert.equal(cleanPacket(pacote({ device: { ...device, model: 'TV da <script>' } })), null);
});

test('código do dia, versão e tamanho', () => {
  assert.equal(cleanPacket(pacote({ code: '192.168.1.10' })), null);
  assert.equal(cleanPacket(pacote({ v: 2 })), null);
  assert.equal(cleanPacket(pacote({ cases: Array.from({ length: MAX_CASES + 1 }, () => caso()) })), null);
  assert.equal(cleanPacket(null), null);
  assert.equal(cleanPacket([]), null);
});

test('limiar de casas: começa em 1 e sobe com a frota até 5; variável vence', () => {
  assert.equal(minHomes(0), 1);
  assert.equal(minHomes(1), 1);
  assert.equal(minHomes(20), 1);
  assert.equal(minHomes(21), 2);
  assert.equal(minHomes(100), 5);
  assert.equal(minHomes(100000), 5);
  assert.equal(minHomes(1, '5'), 5);
  assert.equal(minHomes(500, '1'), 1);
  assert.equal(minHomes(100, 'lixo'), 5);
});

const linha = (day, code, c = caso(), dev = device) => ({ day, code, app: 474900, device: dev, c: cleanPacket(pacote({ cases: [c] })).cases[0] });

test('resumo: grupo só aparece com k casas; o mesmo código em dias diferentes não vira várias casas', () => {
  const rows = [linha(1, 'aaaaaaaaaaaaaaaa'), linha(2, 'bbbbbbbbbbbbbbbb'), linha(3, 'cccccccccccccccc')];
  assert.equal(summarize(rows, 2).length, 0);   // uma casa por dia (pode ser a mesma TV trocando o código)
  const g = summarize(rows, 1)[0];
  assert.equal(g.homes, 1);
  assert.equal(g.cases, 3);
  assert.equal(g.tools.CLEAR_APP_CACHE.fixed, 3);
  assert.equal(g.tools.CLEAR_APP_CACHE.by_ai, 3);
  const dia = [linha(5, '1111111111111111'), linha(5, '2222222222222222')];
  assert.equal(summarize(dia, 2)[0].homes, 2);
});

test('resumo: aparelho raro vira "other" abaixo de k casas', () => {
  const raro = { ...device, maker: 'Marca', model: 'Rara 1' };
  const rows = [linha(5, '1111111111111111'), linha(5, '2222222222222222'), linha(5, '3333333333333333', caso(), raro)];
  const g = summarize(rows, 2)[0];
  assert.equal(g.devices['Google Chromecast HD|googletv|32'], 2);
  assert.equal(g.devices.other, 1);
  assert.ok(!JSON.stringify(g).includes('Rara'));
});
