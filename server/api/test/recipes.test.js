// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { summarize, DAY_MS } from '../src/fleet.js';
import { deriveRecipes, recipeOf, MAX_TOOLS } from '../src/recipes.js';

const device = { maker: 'Google', model: 'Chromecast HD', platform: 'googletv', sdk: 31, abi: '32', ram: '1-2', disk: '4-8' };
const linha = (code, tries, day = 100, o = {}) => ({
  day, code, app: 1, device,
  c: { type: 'CRASH', area: 'app', target: 'com.app.x', cause: 'update', before: [], state: {}, tries, outcome: 'fixed', relapse: 'none', count: 1, ...o },
});
const t = (tool, result) => ({ tool, by: 'rule', result });

test('a ferramenta que conserta nas casas vira receita, a que falha não', () => {
  const rows = [
    linha('a', [t('REFRESH_DATA', 'not_fixed'), t('CLEAR_APP_CACHE', 'fixed')]),
    linha('b', [t('REFRESH_DATA', 'failed'), t('CLEAR_APP_CACHE', 'fixed')]),
    linha('c', [t('CLEAR_APP_CACHE', 'fixed')]),
  ];
  const [r] = deriveRecipes(summarize(rows, 1), 100 * DAY_MS);
  assert.deepEqual(r.tools, ['CLEAR_APP_CACHE']);
  assert.deepEqual(r.match, { type: 'CRASH', area: 'app', target: 'com.app.x', cause: 'update' });
  assert.equal(r.evidence.homes, 3);
  assert.equal(r.evidence.rates.CLEAR_APP_CACHE, 1);
  assert.equal(r.day, 100);
});

test('uma tentativa só não basta (MIN_TRIES)', () => {
  assert.deepEqual(deriveRecipes(summarize([linha('a', [t('CLEAR_APP_CACHE', 'fixed')])], 1), 0), []);
});

test('abaixo do limiar de casas o grupo nem chega às receitas', () => {
  const rows = [linha('a', [t('CLEAR_APP_CACHE', 'fixed')]), linha('a', [t('CLEAR_APP_CACHE', 'fixed')], 101)];
  assert.equal(deriveRecipes(summarize(rows, 2), 0).length, 0);
  assert.equal(deriveRecipes(summarize(rows, 1), 0).length, 1);
});

test('ordem pela taxa de conserto, no máximo 3, e nada fora da lista fechada', () => {
  const g = {
    type: 'ERROR', area: 'feature', target: 'mcp', cause: 'network', homes: 1, cases: 9,
    tools: {
      RESTART_FEATURE: { fixed: 2, not_fixed: 1, failed: 0 }, REFRESH_DATA: { fixed: 3, not_fixed: 0, failed: 0 },
      RECONNECT_SHELL: { fixed: 4, not_fixed: 2, failed: 0 }, REBOOT: { fixed: 2, not_fixed: 1, failed: 1 },
      rm_rf: { fixed: 9, not_fixed: 0, failed: 0 }, ASK_AI: { fixed: 9, not_fixed: 0, failed: 0 },
    },
  };
  const r = recipeOf(g, 5);
  assert.ok(r.tools.length <= MAX_TOOLS);
  assert.equal(r.tools[0], 'REFRESH_DATA');
  assert.ok(!r.tools.includes('rm_rf'));
  assert.ok(!r.tools.includes('ASK_AI'));   // pedir à IA não é conserto
  assert.ok(!r.tools.includes('REBOOT'));   // 2 de 4 = 50%, abaixo de MIN_RATE
  assert.equal(recipeOf({ ...g, type: 'HACK' }, 5), null);
});
