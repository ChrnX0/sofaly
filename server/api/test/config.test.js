// O wrangler.toml da raiz (usado pelo Workers Builds da Cloudflare a cada merge) tem que ser igual ao da API.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const limpo = (caminho) => readFileSync(new URL(caminho, import.meta.url), 'utf8')
  .split('\n')
  .map((l) => l.replace(/\s+#.*$/, '').trimEnd())            // tira comentário no fim da linha
  .filter((l) => l && !l.startsWith('#') && !l.startsWith('main =') && !l.startsWith('routes ='))
  .map((l) => l.startsWith('workers_dev =') ? 'workers_dev = false' : l)
  .join('\n');

test('wrangler.toml da raiz é igual ao de server/api (menos o caminho do main e a rota)', () => {
  assert.equal(limpo('../../../wrangler.toml'), limpo('../wrangler.toml'));
  assert.match(readFileSync(new URL('../../../wrangler.toml', import.meta.url), 'utf8'), /^main = "server\/api\/src\/index\.js"$/m);
});

// No Workers Builds o wrangler roda sem terminal e, com `routes`, move o api.sofaly.tv para o Worker que fizer o deploy
// por último, sem perguntar (wrangler 4.169, publishCustomDomains). Por isso só server/api/wrangler.toml tem a rota.
test('wrangler.toml da raiz não declara rota nem domínio; o de server/api declara o api.sofaly.tv', () => {
  const raiz = readFileSync(new URL('../../../wrangler.toml', import.meta.url), 'utf8');
  assert.doesNotMatch(raiz, /^\s*(routes|route)\s*=/m);
  assert.doesNotMatch(raiz, /^\s*custom_domain/m);
  assert.match(readFileSync(new URL('../wrangler.toml', import.meta.url), 'utf8'),
    /^routes = \[\{ pattern = "api\.sofaly\.tv", custom_domain = true \}\]$/m);
});
