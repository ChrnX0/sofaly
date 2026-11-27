// A rede de Sofys (Paulo, 27/11/2026, estudo "Sofy coletiva", proposta B+).
// Por quê: cada Sofaly conserta sozinho os defeitos da sua TV, mas aprende só com a própria casa. Para quê: juntar os
// casos de todas as casas (o que deu errado, como, por quê, e o que consertou) num banco nosso, achar padrões e devolver
// às TVs as receitas que funcionam, sem atualizar o app. Como: quem ligou "Ajudar outras casas" manda 1x por dia um
// pacote só com campos FIXOS (listas fechadas, faixas largas, nada de texto livre). Qualquer campo fora da lista recusa
// o pacote inteiro: é a segunda trava (a primeira é a TV, SofyFleet.kt). A casa vira um código que a própria TV troca a
// cada dia, então não dá para seguir uma casa ao longo dos dias. Linhas cruas apagam em [KEEP_DAYS] dias.
// O resumo (só com o REPORTS_TOKEN) mostra um grupo apenas quando ele aparece em [minHomes] casas: começa em 1 (só a TV
// do Paulo testa) e sobe sozinho com a frota, até 5. FLEET_MIN_HOMES (variável) força outro número.
// Rotas: POST /v1/community/cases (pública, opt-in na TV)   GET /v1/community/cases/summary (Bearer REPORTS_TOKEN)

import { sameToken } from './reports.js';

export const VERSION = 1;
export const MAX_BODY = 16 * 1024;
export const MAX_CASES = 20;
export const MAX_TRIES = 6;
export const MAX_BEFORE = 6;
export const KEEP_DAYS = 30;
export const MAX_ROWS = 300_000;
export const DAY_MS = 86_400_000;
export const MAX_MIN_HOMES = 5;
/** Uma casa a mais no limiar para cada tantas TVs ativas: 1 TV = 1; 100+ TVs = 5. */
export const HOMES_PER_STEP = 20;

// Listas fechadas. O nome vai no banco: só acrescentar, nunca renomear.
export const TYPES = new Set(['NO_EFFECT', 'ERROR', 'CRASH', 'SLOW', 'ACCESS_LOST', 'PERMISSION_LOST', 'INCOHERENT', 'SCREEN_BROKEN']);
export const AREAS = new Set(['shell', 'accessibility', 'wireless_debug', 'tunnel', 'home', 'perm', 'app', 'tile', 'screen', 'data',
  'feature', 'system', 'bus', 'other']);
export const CAUSES = new Set(['network', 'app', 'memory', 'storage', 'update', 'setting', 'permission', 'access', 'sofaly', 'unknown']);
export const BEFORE = new Set(['app_updated', 'sofaly_updated', 'system_updated', 'rebooted', 'network_changed', 'setting_changed',
  'memory_low', 'plugin_changed', 'backup_restored', 'other']);
export const TOOLS = new Set(['GRANT_PERMISSIONS', 'RELIGHT_ACCESSIBILITY', 'RELIGHT_WIRELESS_DEBUG', 'RECONNECT_SHELL', 'REPAIR_SHELL',
  'RESTART_TUNNEL', 'REFRESH_DATA', 'FREE_MEMORY', 'RESTART_APP', 'CLEAR_APP_CACHE', 'RESTART_LAUNCHER', 'UNDO_LAST_CHANGE',
  'DISABLE_FEATURE', 'REBOOT', 'ASK_AI', 'REPORT']);
export const BY = new Set(['rule', 'ai', 'community']);
export const RESULTS = new Set(['fixed', 'not_fixed', 'failed', 'pending']);
export const OUTCOMES = new Set(['fixed', 'gave_up', 'open']);
export const RELAPSE = new Set(['none', '24h', '7d']);
export const UPTIME = new Set(['lt1h', '1-24h', '1-7d', '7d+']);
export const MEMORY = new Set(['low', 'ok']);
export const NET = new Set(['wifi', 'ethernet', 'none', 'other']);
export const PERIOD = new Set(['night', 'morning', 'afternoon', 'evening']);
export const PLATFORM = new Set(['googletv', 'androidtv', 'other']);
export const ABI = new Set(['32', '64']);
export const RAM = new Set(['lt1', '1-2', '2-3', '3-4', '4-8', '8+']);
export const DISK = new Set(['lt4', '4-8', '8-16', '16-32', '32+']);

const PKG = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const NAME = /^[a-z0-9_.]{1,60}$/;          // nomes do nosso código (feature:mcp, perm:android.permission.x)
const DEVICE_TEXT = /^[A-Za-z0-9 ._+-]{1,40}$/;   // fabricante e modelo (Build.MANUFACTURER / Build.MODEL)
const CODE = /^[0-9a-f]{16}$/;

const isObj = (o) => o !== null && typeof o === 'object' && !Array.isArray(o);
/** Só as chaves permitidas; qualquer outra recusa o pacote inteiro (nada "a mais" passa calado). */
const onlyKeys = (o, allowed) => isObj(o) && Object.keys(o).every((k) => allowed.includes(k));
const intIn = (v, lo, hi) => Number.isSafeInteger(v) && v >= lo && v <= hi;

function cleanDevice(d) {
  if (!onlyKeys(d, ['maker', 'model', 'platform', 'sdk', 'abi', 'ram', 'disk'])) return null;
  if (!DEVICE_TEXT.test(d.maker ?? '') || !DEVICE_TEXT.test(d.model ?? '')) return null;
  if (!PLATFORM.has(d.platform) || !intIn(d.sdk, 21, 60) || !ABI.has(d.abi) || !RAM.has(d.ram) || !DISK.has(d.disk)) return null;
  return { maker: d.maker, model: d.model, platform: d.platform, sdk: d.sdk, abi: d.abi, ram: d.ram, disk: d.disk };
}

function cleanTry(t) {
  if (!onlyKeys(t, ['tool', 'by', 'result']) || !TOOLS.has(t.tool) || !BY.has(t.by) || !RESULTS.has(t.result)) return null;
  return { tool: t.tool, by: t.by, result: t.result };
}

function cleanCase(c) {
  if (!onlyKeys(c, ['type', 'area', 'target', 'cause', 'before', 'state', 'tries', 'outcome', 'relapse', 'count'])) return null;
  if (!TYPES.has(c.type) || !AREAS.has(c.area) || !CAUSES.has(c.cause) || !OUTCOMES.has(c.outcome) || !RELAPSE.has(c.relapse)) return null;
  // O alvo: pacote do app (área "app") ou um nome do nosso código. Nada mais.
  let target = '';
  if (c.target !== undefined && c.target !== '') {
    if (typeof c.target !== 'string' || c.target.length > 150) return null;
    if (c.area === 'app' ? !PKG.test(c.target) : !NAME.test(c.target)) return null;
    target = c.target;
  }
  if (!Array.isArray(c.before) || c.before.length > MAX_BEFORE || !c.before.every((b) => BEFORE.has(b))) return null;
  const s = c.state;
  if (!onlyKeys(s, ['uptime', 'memory', 'net', 'period']) || !UPTIME.has(s.uptime) || !MEMORY.has(s.memory) || !NET.has(s.net)
    || !PERIOD.has(s.period)) return null;
  if (!Array.isArray(c.tries) || c.tries.length > MAX_TRIES) return null;
  const tries = c.tries.map(cleanTry);
  if (tries.includes(null)) return null;
  if (!intIn(c.count, 1, 9999)) return null;
  return {
    type: c.type, area: c.area, target, cause: c.cause, before: [...new Set(c.before)].sort(),
    state: { uptime: s.uptime, memory: s.memory, net: s.net, period: s.period },
    tries, outcome: c.outcome, relapse: c.relapse, count: c.count,
  };
}

/** O pacote do dia conferido, ou null (400). Puro: fleet.test.js. */
export function cleanPacket(o) {
  if (!onlyKeys(o, ['v', 'code', 'app', 'device', 'cases'])) return null;
  if (o.v !== VERSION || !CODE.test(o.code ?? '') || !intIn(o.app, 1, 99_999_999)) return null;
  const device = cleanDevice(o.device);
  if (!device) return null;
  if (!Array.isArray(o.cases) || o.cases.length > MAX_CASES) return null;
  const cases = o.cases.map(cleanCase);
  if (cases.includes(null)) return null;
  return { code: o.code, app: o.app, device, cases };
}

/** O grupo de um caso: o que define "o mesmo problema". */
export const groupKey = (c) => `${c.type}|${c.area}|${c.target}|${c.cause}`;

/**
 * Quantas casas um grupo precisa para aparecer no resumo. Começa em 1 (só a TV do Paulo) e sobe sozinho com a frota:
 * ceil(ativas / [HOMES_PER_STEP]), no máximo [MAX_MIN_HOMES]. [forced] (FLEET_MIN_HOMES) vence quando é número válido.
 */
export function minHomes(activeHomes, forced) {
  const f = Number.parseInt(forced ?? '', 10);
  if (Number.isSafeInteger(f) && f >= 1 && f <= 100) return f;
  return Math.min(MAX_MIN_HOMES, Math.max(1, Math.ceil((activeHomes || 0) / HOMES_PER_STEP)));
}

/**
 * O resumo: [rows] = {day, code, device, app, c} (c = caso limpo). Devolve os grupos com [k]+ casas, com o placar de cada
 * ferramenta e os aparelhos onde aconteceu (um aparelho só aparece pelo nome com [k]+ casas; senão vira "other").
 * Casas de um grupo = o maior número de códigos diferentes num mesmo dia (o código troca a cada dia, então somar dias
 * contaria a mesma casa várias vezes; o maior dia nunca conta a mais).
 */
export function summarize(rows, k) {
  const daily = (list) => {
    const by = new Map();
    for (const r of list) { if (!by.has(r.day)) by.set(r.day, new Set()); by.get(r.day).add(r.code); }
    return Math.max(0, ...[...by.values()].map((s) => s.size));
  };
  const groups = new Map();
  for (const r of rows) {
    const g = groupKey(r.c);
    if (!groups.has(g)) groups.set(g, []);
    groups.get(g).push(r);
  }
  const out = [];
  for (const [g, list] of groups) {
    const homes = daily(list);
    if (homes < k) continue;
    const [type, area, target, cause] = g.split('|');
    const tools = {};
    for (const r of list) {
      for (const t of r.c.tries) {
        const x = (tools[t.tool] ??= { fixed: 0, not_fixed: 0, failed: 0, by_ai: 0 });
        if (t.result !== 'pending') x[t.result]++;
        if (t.by === 'ai') x.by_ai++;
      }
    }
    const devices = new Map();
    for (const r of list) {
      const d = `${r.device.maker} ${r.device.model}|${r.device.platform}|${r.device.abi}`;
      if (!devices.has(d)) devices.set(d, []);
      devices.get(d).push(r);
    }
    const dev = {};
    for (const [d, l] of devices) { const name = daily(l) >= k ? d : 'other'; dev[name] = (dev[name] ?? 0) + l.length; }
    const before = {};
    for (const r of list) for (const b of r.c.before) before[b] = (before[b] ?? 0) + 1;
    out.push({
      type, area, target, cause, homes, cases: list.length,
      fixed: list.filter((r) => r.c.outcome === 'fixed').length,
      relapsed: list.filter((r) => r.c.relapse !== 'none').length,
      tools, devices: dev, before,
      apps: [...new Set(list.map((r) => r.app))].sort((a, b) => b - a).slice(0, 5),
    });
  }
  return out.sort((a, b) => b.homes - a.homes || b.cases - a.cases);
}

const json = (obj, status = 200) => new Response(JSON.stringify(obj), {
  status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
});
const store = (env) => env.FLEET.get(env.FLEET.idFromName('fleet'));

export async function handleFleetPost(req, env, { ip, take = async () => true }) {
  if (!env.FLEET) return json({ error: 'unavailable' }, 503);
  if (env.COMMUNITY_IP_LIMITER) {
    const { success } = await env.COMMUNITY_IP_LIMITER.limit({ key: ip || 'none' });
    if (!success) return json({ error: 'slow down' }, 429);
  }
  if (Number(req.headers.get('content-length') || 0) > MAX_BODY) return json({ error: 'too large' }, 413);
  const text = await req.text();
  if (text.length > MAX_BODY) return json({ error: 'too large' }, 413);
  let o; try { o = JSON.parse(text); } catch { return json({ error: 'json' }, 400); }
  const p = cleanPacket(o);
  if (!p) return json({ error: 'packet' }, 400);
  if (!(await take('fleet'))) return json({ error: 'slow down' }, 429);
  return json(await store(env).add(p, Date.now()));
}

export async function handleFleetSummary(req, env) {
  const token = env.REPORTS_TOKEN;
  if (!token || !env.FLEET) return json({ error: 'unavailable' }, 503);
  const auth = req.headers.get('authorization') ?? '';
  if (!sameToken(auth.replace(/^Bearer\s+/i, ''), token)) return json({ error: 'unauthorized' }, 401);
  return json(await store(env).summary(Date.now(), env.FLEET_MIN_HOMES));
}
