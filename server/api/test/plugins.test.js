// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PLUGINS, BLOCKED, pluginsBody } from '../src/plugins.js';

const kt = readFileSync(new URL('../../../app/src/main/kotlin/br/paulo/tvlauncher/screensaver/PluginCatalog.kt', import.meta.url), 'utf8');
const noApp = [...kt.matchAll(/Entrada\("([a-z0-9-]+)", Tipo\./g)].map((m) => m[1]);

test('vitrine: ids únicos no formato que a TV aceita, no máximo 40', () => {
  const ids = new Set();
  for (const p of PLUGINS) {
    assert.match(p.id, /^[a-z0-9][a-z0-9-]{0,39}$/);
    assert.ok(!ids.has(p.id), `repetido: ${p.id}`);
    ids.add(p.id);
  }
  assert.ok(PLUGINS.length > 0 && PLUGINS.length <= 40);
  assert.deepEqual(pluginsBody().plugins, PLUGINS);
});

test('entrada nova (fora do app) só com Play, nome limpo e descrição em inglês curta', () => {
  const pacote = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;
  for (const p of PLUGINS.filter((x) => !noApp.includes(x.id))) {
    assert.ok(['wallpaper', 'icons'].includes(p.type), p.id);
    assert.match(p.play ?? '', pacote, p.id);
    assert.ok(p.name && p.name.length <= 40 && !/projectivy/i.test(p.name), p.id);
    assert.ok(p.description?.en && p.description.en.length <= 120, p.id);
    assert.equal(p.repo, undefined, `${p.id}: repositório só no app`);
  }
});

test('hoje a vitrine do servidor é a mesma do app (mesma ordem)', () => {
  assert.ok(noApp.length > 0);
  assert.deepEqual(PLUGINS.map((p) => p.id), noApp);
});

test('bloqueio: só pacotes no formato Android, sem repetir, e a lista vai na resposta', () => {
  const pacote = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;
  const vistos = new Set();
  for (const b of BLOCKED) { assert.match(b.package, pacote); assert.ok(!vistos.has(b.package)); vistos.add(b.package); assert.ok(b.reason, b.package); }
  assert.deepEqual(pluginsBody().blocked, BLOCKED);
});
