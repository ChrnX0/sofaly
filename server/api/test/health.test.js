// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { healthBody } from '../src/health.js';

test('health simples continua igual (monitor externo)', () => {
  assert.deepEqual(healthBody({}, false), { ok: true });
});

test('health profundo diz qual serviço falta, sem mostrar chave', () => {
  const env = { PODCASTINDEX_API_KEY: 'k', PODCASTINDEX_API_SECRET: 's', OPEN_MODELS_KEY: '', WEATHERAPI_KEY: 'w',
    AI: {}, COMMUNITY: {}, COMMUNITY_SALT: 'x', MAILBOX: {}, PAIRING: {} };
  const b = healthBody(env, true);
  assert.equal(b.ok, false);
  assert.equal(b.services.weather_open, false);
  assert.equal(b.services.podcasts, true);
  assert.ok(!JSON.stringify(b).includes('"k"'));
  assert.equal(healthBody({ ...env, OPEN_MODELS_KEY: 'o' }, true).ok, true);
});
