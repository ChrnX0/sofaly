// Roda com: cd server/api && node --test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { vote, median, dayCode, combine, upstreamUrls, handleWeatherOpen, MODELS, CREDIT } from '../src/weatherOpen.js';

class FakeCache {
  constructor() { this.m = new Map(); }
  async match(req) { return this.m.get(req.url)?.clone(); }
  async put(req, res) { this.m.set(req.url, res); }
}

const base = {
  latitude: -23.5, longitude: -46.6, timezone: 'America/Sao_Paulo', utc_offset_seconds: -10800,
  current: { time: '2026-11-24T10:15', interval: 900, temperature_2m: 29, weather_code: 3 },
  hourly: { time: ['2026-11-24T00:00', '2026-11-24T01:00'], temperature_2m: [21, 20], precipitation: [3.4, 0],
    weather_code: [95, 3], wind_speed_10m: [8, 6], visibility: [5000, 9000], dew_point_2m: [20, 19] },
  daily: { time: ['2026-11-24', '2026-11-25'], temperature_2m_max: [30, 31], temperature_2m_min: [20, 21],
    weather_code: [1, 61], precipitation_sum: [0.2, 5], sunrise: ['a', 'b'], sunset: ['c', 'd'] },
};
const multi = {
  hourly: { time: ['2026-11-24T00:00', '2026-11-24T01:00'],
    precipitation_ecmwf_ifs: [3.4, 0], precipitation_ecmwf_aifs025_single: [1, 0], precipitation_gfs_seamless: [0, 0],
    precipitation_icon_global: [0.2, null], uv_index_gfs_seamless: [0, 2.5] },
  daily: { time: ['2026-11-24', '2026-11-25'],
    precipitation_sum_ecmwf_ifs: [0.2, 5], precipitation_sum_ecmwf_aifs025_single: [3, 0], precipitation_sum_gfs_seamless: [2, 0],
    precipitation_sum_icon_global: [1, null] },
};

test('voto: fração dos modelos com dado que passam do limite', () => {
  assert.equal(vote([3, 1, 0, 0.2], 0.1), 75);
  assert.equal(vote([0, 0, null], 0.1), 0);
  assert.equal(vote([5, 0, 0], 1), 33);   // modelo sem dado (ICON depois do 7º dia) não conta
  assert.equal(vote([null, undefined], 1), null);
});

test('mediana', () => {
  assert.equal(median([3, 1, 2]), 2);
  assert.equal(median([0.2, 3, 2, 1]), 1.5);
  assert.equal(median([null]), null);
});

test('ícone do dia acompanha a maioria', () => {
  assert.equal(dayCode(1, 75), 80);
  assert.equal(dayCode(61, 25), 3);
  assert.equal(dayCode(95, 25), 95);   // trovoada fica
  assert.equal(dayCode(61, 75), 61);
  assert.equal(dayCode(2, null), 2);
});

test('junta ECMWF com a chance dos 4 modelos no formato da TV', () => {
  const o = combine(base, multi);
  assert.deepEqual(o.hourly.precipitation_probability, [75, 0]);
  assert.deepEqual(o.hourly.uv_index, [0, 2.5]);
  assert.deepEqual(o.daily.precipitation_probability_max, [75, 33]);
  assert.deepEqual(o.daily.weather_code, [80, 3]);
  assert.deepEqual(o.daily.precipitation_sum, [1.5, 0]);
  assert.equal(o.current.interval, undefined);
  assert.equal(o.current.temperature_2m, 29);
  assert.deepEqual(o.models, MODELS);
  assert.equal(o.credit, CREDIT);
});

test('formato estranho sobe como erro', () => {
  assert.throws(() => combine({}, multi));
});

test('2 pedidos, chave só quando existe', () => {
  const [a, b] = upstreamUrls('https://x/v1/forecast', '', '-23.5', '-46.6');
  assert.match(a, /models=ecmwf_ifs&current=/);
  assert.match(b, /models=ecmwf_ifs,ecmwf_aifs025_single,gfs_seamless,icon_global&hourly=precipitation,uv_index&daily=precipitation_sum$/);
  assert.doesNotMatch(a, /apikey/);
  assert.match(upstreamUrls('https://x', 'K 1', '0.0', '0.0')[1], /&apikey=K%201$/);
});

test('sem servidor de modelos: 503 (a TV fica no MET)', async () => {
  const r = await handleWeatherOpen(new URL('https://api.sofaly.tv/v1/weather/open?lat=1&lon=1'), {});
  assert.equal(r.status, 503);
});

test('coordenada inválida: 400', async () => {
  const r = await handleWeatherOpen(new URL('https://a/v1/weather/open?lat=99&lon=1'), { OPEN_MODELS_URL: 'https://x' });
  assert.equal(r.status, 400);
});

test('busca uma vez por lugar e guarda', async () => {
  let n = 0;
  const fetchFn = async (u) => { n++; return new Response(JSON.stringify(u.includes('current=') ? base : multi)); };
  const cache = new FakeCache();
  const env = { OPEN_MODELS_URL: 'https://x/v1/forecast' };
  const r1 = await handleWeatherOpen(new URL('https://a/v1/weather/open?lat=-23.61&lon=-46.63'), env, { fetchFn, cache });
  assert.equal(r1.status, 200);
  assert.equal((await r1.json()).daily.precipitation_probability_max[0], 75);
  const r2 = await handleWeatherOpen(new URL('https://a/v1/weather/open?lat=-23.58&lon=-46.61'), env, { fetchFn, cache });
  assert.equal(r2.status, 200);
  assert.equal(n, 2);   // 2 pedidos na 1ª vez, nenhum na 2ª (mesma célula da grade)
});

test('servidor de modelos falhou: 502', async () => {
  const fetchFn = async () => new Response('x', { status: 500 });
  const r = await handleWeatherOpen(new URL('https://a/v1/weather/open?lat=1&lon=1'), { OPEN_MODELS_URL: 'https://x' }, { fetchFn });
  assert.equal(r.status, 502);
});
