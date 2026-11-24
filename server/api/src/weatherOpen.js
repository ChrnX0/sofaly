// Clima do plano Básico (24/11/2026, aprovado pelo Paulo): consenso de 4 modelos ABERTOS.
// Rota pública: GET /v1/weather/open?lat=<-90..90>&lon=<-180..180>
//
// Por quê: no teste para trás de 24/11 (30 aeroportos x 60 dias, conferido com o METAR; ver
// /mnt/project-files/tv-launcher/venda/clima-alternativas.md, seção 11) o "consenso" de ECMWF IFS, ECMWF AIFS, GFS e
// ICON acertou mais "chove amanhã?" (HSS 0,55) do que qualquer modelo sozinho, inclusive o best_match da Open-Meteo
// (0,44). Os 4 têm dados liberados para uso comercial: ECMWF (CC BY 4.0, catálogo todo aberto desde 01/10/2025),
// GFS/NOAA (domínio público) e ICON/DWD (dados abertos do DWD). A TV mostra o crédito.
//
// De onde vêm: de qualquer servidor que fale a API da Open-Meteo (secret/var OPEN_MODELS_URL = endereço do
// /v1/forecast). Pode ser a nossa própria Open-Meteo (código AGPL, dados abertos: só o custo da máquina) ou a
// customer-api paga da Open-Meteo (OPEN_MODELS_KEY = apikey). Sem OPEN_MODELS_URL: 503 e a TV fica no MET Norway.
// A Open-Meteo GRÁTIS não serve aqui: os termos dela proíbem uso comercial (open-meteo.com/en/terms).
//
// O que sai: o formato que a TV já lê (current / hourly / daily da Open-Meteo), com temperatura, céu, vento etc. do
// ECMWF IFS (o de menor erro na temperatura no teste: 1,2 °C na máxima) e a CHANCE DE CHUVA = fração dos modelos que
// preveem chuva (dia: >= 1 mm, a regra testada; hora: >= 0,1 mm). O UV vem do GFS (o ECMWF aberto não tem).
// Cache: grade de 0,1° (~11 km), 30 min na borda: o custo é por lugar, não por TV.

export const MODELS = ['ecmwf_ifs', 'ecmwf_aifs025_single', 'gfs_seamless', 'icon_global'];
export const BASE_MODEL = 'ecmwf_ifs';
export const UV_MODEL = 'gfs_seamless';
export const DAYS = 10;
export const GRID = 0.1;
export const CACHE_SEC = 30 * 60;
export const RAIN_DAY_MM = 1.0;
export const RAIN_HOUR_MM = 0.1;
export const CREDIT = 'ECMWF (CC BY 4.0) · NOAA GFS · DWD ICON';
const USER_AGENT = 'Sofaly/1 (+https://sofaly.tv)';

const CURRENT = 'temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,precipitation,pressure_msl,wind_speed_10m,wind_direction_10m,is_day';
const HOURLY = 'temperature_2m,precipitation,weather_code,wind_speed_10m,visibility,dew_point_2m';
const DAILY = 'temperature_2m_max,temperature_2m_min,weather_code,precipitation_sum,sunrise,sunset';

export function snap(v, limit) {
  const n = Number.parseFloat(v);
  if (!Number.isFinite(n) || Math.abs(n) > limit) return null;
  return (Math.round(n / GRID) * GRID).toFixed(1);
}

/** Os 2 pedidos ao servidor de modelos: (1) tudo do ECMWF IFS; (2) só chuva e UV dos 4 modelos (conta menos chamadas). */
export function upstreamUrls(base, key, lat, lon) {
  const k = key ? `&apikey=${encodeURIComponent(key)}` : '';
  const comum = `latitude=${lat}&longitude=${lon}&timezone=auto&forecast_days=${DAYS}`;
  return [
    `${base}?${comum}&models=${BASE_MODEL}&current=${CURRENT}&hourly=${HOURLY}&daily=${DAILY}${k}`,
    `${base}?${comum}&models=${MODELS.join(',')}&hourly=precipitation,uv_index&daily=precipitation_sum${k}`,
  ];
}

/** Fração (0-100) dos modelos com valor >= limite; null se nenhum modelo tem o dado nessa posição. */
export function vote(values, limit) {
  const v = values.filter((x) => typeof x === 'number' && Number.isFinite(x));
  if (v.length === 0) return null;
  return Math.round((100 * v.filter((x) => x >= limit).length) / v.length);
}

export function median(values) {
  const v = values.filter((x) => typeof x === 'number' && Number.isFinite(x)).sort((a, b) => a - b);
  if (v.length === 0) return null;
  const m = Math.floor(v.length / 2);
  return v.length % 2 ? v[m] : Math.round(((v[m - 1] + v[m]) / 2) * 10) / 10;
}

/**
 * Ícone do dia coerente com a chance: o ECMWF sozinho às vezes põe chuva quando a maioria não vê, e o contrário.
 * Maioria vê chuva e o código é seco -> 80 (pancadas); maioria NÃO vê e o código é de chuva -> 3 (nublado).
 * Neve, granizo e trovoada (>= 71 e != 80-82) ficam como vieram: são raros e o ECMWF é o melhor nisso.
 */
export function dayCode(code, chance) {
  if (typeof code !== 'number' || chance === null) return code;
  const chuva = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
  if (chance >= 50 && code < 51) return 80;
  if (chance < 50 && chuva) return 3;
  return code;
}

/** Junta as 2 respostas no formato da TV. Erro de formato sobe como exceção. */
export function combine(base, multi) {
  const h = base.hourly, d = base.daily, mh = multi.hourly, md = multi.daily;
  if (!h?.time || !d?.time || !mh?.time || !md?.time || !base.current) throw new Error('bad shape');
  const col = (blk, name, m, i) => blk[`${name}_${m}`]?.[i];
  const mhIdx = new Map(mh.time.map((t, i) => [t, i]));
  const mdIdx = new Map(md.time.map((t, i) => [t, i]));

  const hourlyChance = h.time.map((t) => {
    const i = mhIdx.get(t);
    return i === undefined ? null : vote(MODELS.map((m) => col(mh, 'precipitation', m, i)), RAIN_HOUR_MM);
  });
  const uv = h.time.map((t) => {
    const i = mhIdx.get(t);
    const v = i === undefined ? null : col(mh, 'uv_index', UV_MODEL, i);
    return typeof v === 'number' ? v : 0;
  });
  const dayChance = d.time.map((t) => {
    const i = mdIdx.get(t);
    return i === undefined ? null : vote(MODELS.map((m) => col(md, 'precipitation_sum', m, i)), RAIN_DAY_MM);
  });
  const dayMm = d.time.map((t, j) => {
    const i = mdIdx.get(t);
    const v = i === undefined ? null : median(MODELS.map((m) => col(md, 'precipitation_sum', m, i)));
    return v ?? d.precipitation_sum?.[j] ?? 0;
  });
  const models = MODELS.filter((m) => Array.isArray(md[`precipitation_sum_${m}`]));
  const { interval, ...current } = base.current;

  return {
    latitude: base.latitude,
    longitude: base.longitude,
    timezone: base.timezone,
    utc_offset_seconds: base.utc_offset_seconds,
    current,
    hourly: {
      time: h.time,
      temperature_2m: h.temperature_2m,
      precipitation: h.precipitation,
      precipitation_probability: hourlyChance,
      weather_code: h.weather_code,
      wind_speed_10m: h.wind_speed_10m,
      visibility: h.visibility,
      dew_point_2m: h.dew_point_2m,
      uv_index: uv,
    },
    daily: {
      time: d.time,
      temperature_2m_max: d.temperature_2m_max,
      temperature_2m_min: d.temperature_2m_min,
      weather_code: d.weather_code.map((c, j) => dayCode(c, dayChance[j])),
      precipitation_probability_max: dayChance,
      precipitation_sum: dayMm,
      sunrise: d.sunrise,
      sunset: d.sunset,
    },
    models,
    credit: CREDIT,
  };
}

/** Atende /v1/weather/open. `fetchFn` e `cache` entram por parâmetro para os testes. */
export async function handleWeatherOpen(url, env, { fetchFn = fetch, cache = globalThis.caches?.default, ip = '' } = {}) {
  const base = env.OPEN_MODELS_URL;
  if (!base) return reply({ error: 'open models not configured' }, 503, 0);
  if (!(await allowed(env.WEATHER_IP_LIMITER, `ip:${ip}`, true))) return reply({ error: 'rate limited' }, 429, 0);
  const lat = snap(url.searchParams.get('lat'), 90);
  const lon = snap(url.searchParams.get('lon'), 180);
  if (lat === null || lon === null) return reply({ error: 'bad request' }, 400, 0);

  const cacheKey = new Request(`https://cache.sofaly.internal/weather-open?lat=${lat}&lon=${lon}`);
  const hit = cache ? await cache.match(cacheKey) : null;
  if (hit) return hit;

  if (!(await allowed(env.OPEN_MODELS_LIMITER, 'upstream', false))) return reply({ error: 'rate limited' }, 429, 0);
  let datas;
  try {
    const res = await Promise.all(upstreamUrls(base, env.OPEN_MODELS_KEY, lat, lon)
      .map((u) => fetchFn(u, { headers: { 'User-Agent': USER_AGENT } })));
    const bad = res.find((r) => !r.ok);
    if (bad) return reply({ error: `open models ${bad.status}` }, 502, 0);
    datas = await Promise.all(res.map((r) => r.json()));
  } catch {
    return reply({ error: 'open models unreachable' }, 502, 0);
  }
  let body;
  try { body = combine(datas[0], datas[1]); } catch { return reply({ error: 'open models bad shape' }, 502, 0); }
  const out = reply(body, 200, CACHE_SEC);
  if (cache) await cache.put(cacheKey, out.clone());
  return out;
}

async function allowed(limiter, key, failOpen) {
  if (!limiter) return true;
  try { return (await limiter.limit({ key })).success === true; } catch { return failOpen; }
}

function reply(obj, status, maxAge) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': maxAge > 0 ? `public, max-age=${maxAge}` : 'no-store' },
  });
}
