// Fichas comunitárias da Sofy (ideia 41 do estudo "extrapolar a Sofy", 24/11/2026).
// Por quê: o app X na versão Y trava em muitas casas; a Sofy de uma casa nova pode saber antes de sofrer.
// Para quê: a TV baixa uma lista pequena e confere SOZINHA o que vale para os apps dela (nenhuma lista de apps sai da TV).
// Como: quem ligou "Ajudar outras casas" manda, no máximo uma vez por dia, só pacote + versão + tipo ("crashy"/"slower").
// Aqui cada casa vira um código curto (sha-256 do IP com sal, nunca o IP), e a lição só aparece na lista pública depois
// de [MIN_HOMES] casas diferentes em [WINDOW_DAYS] dias. Tudo apaga sozinho depois da janela.
// Regras do revisor noturno (plano D, passo 4, 25/11/2026): a regra que VALE numa TV (passou a sombra lá) também pode ir,
// só o padrão e a faixa. Privacidade: o padrão vem da IA lendo a tela da casa, então palavra de 4+ letras só passa se for
// técnica ([RULE_WORDS]); nome, cidade, evento nunca saem. E vale o mesmo limiar de [MIN_HOMES] casas: um texto de uma
// casa só nunca chega à lista pública. Quem recebe põe a regra na SOMBRA dela (3 noites) antes de valer.
// Rotas (públicas): POST /v1/community/lessons {"lessons":[...],"rules":[...]}   GET /v1/community/lessons

export const MAX_LESSONS = 30;
export const MIN_HOMES = 5;
export const WINDOW_DAYS = 14;
export const MAX_PUBLISHED = 500;
export const MAX_ROWS = 200_000;   // ~10 MB no SQLite do plano grátis
export const KINDS = new Set(['crashy', 'slower']);
const PKG = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
export const DAY_MS = 86_400_000;
export const MAX_RULES = 10;
export const RULE_PATTERN_MAX = 80;
export const RULE_KINDS = new Set(['FORBIDDEN', 'RANGE']);
export const RULE_SCREENS = new Set(['*', 'previsao', 'agenda', 'despertador', 'config', 'jogos', 'podcasts', 'filmes', 'series',
  'diagnostico', 'armazenamento', 'ajuda', 'sobre']);
export const RULE_WORDS = new Set(['null', 'undefined', 'infinity', 'error', 'true', 'false', 'none', 'nan', 'http', 'https']);
const NESTED = /\((?:[^()\\]|\\.)*[+*}](?:[^()\\]|\\.)*\)[+*{]/;

// O padrão pode sair da casa? Curto, sem quantificador aninhado, compila, e sem palavra que possa ser da casa.
export function shareablePattern(p) {
  if (typeof p !== 'string' || !p || p.length > RULE_PATTERN_MAX || NESTED.test(p)) return false;
  try { new RegExp(p); } catch { return false; }
  const words = p.replace(/\\./g, ' ').replace(/\[[^\]]*\]/g, ' ').match(/\p{L}{4,}/gu) || [];
  return words.every((w) => RULE_WORDS.has(w.toLowerCase()));
}

// "rule|KIND|screen|min|max|pattern"
export function ruleKey(r) {
  if (!r || typeof r !== 'object' || !RULE_KINDS.has(r.kind) || !shareablePattern(r.pattern)) return null;
  const screen = typeof r.screen === 'string' && RULE_SCREENS.has(r.screen) ? r.screen : '*';
  let min = 0, max = 0;
  if (r.kind === 'RANGE') {
    if (!Number.isFinite(r.min) || !Number.isFinite(r.max) || r.min > r.max || Math.abs(r.min) > 1e9 || Math.abs(r.max) > 1e9) return null;
    let groups = 0; try { groups = new RegExp(`${r.pattern}|`).exec('').length - 1; } catch { return null; }
    if (groups < 1) return null;
    min = r.min; max = r.max;
  }
  return `rule|${r.kind}|${screen}|${min}|${max}|${r.pattern}`;
}

export function parseRuleKey(k) {
  const m = /^rule\|(FORBIDDEN|RANGE)\|([^|]+)\|([^|]+)\|([^|]+)\|(.+)$/s.exec(k);
  return m ? { kind: m[1], screen: m[2], min: Number(m[3]), max: Number(m[4]), pattern: m[5] } : null;
}

// O corpo que a TV manda, conferido. null = recusa (400).
export function cleanReport(o) {
  if (!o || typeof o !== 'object' || !Array.isArray(o.lessons) || o.lessons.length > MAX_LESSONS) return null;
  const out = new Set();
  for (const l of o.lessons) {
    if (!l || typeof l.pkg !== 'string' || l.pkg.length > 150 || !PKG.test(l.pkg)) return null;
    if (!Number.isSafeInteger(l.ver) || l.ver <= 0) return null;
    if (!KINDS.has(l.kind)) return null;
    out.add(`${l.pkg}@${l.ver}:${l.kind}`);
  }
  if (o.rules !== undefined) {
    if (!Array.isArray(o.rules) || o.rules.length > MAX_RULES) return null;
    // Regra que não pode sair (palavra da casa, padrão perigoso) é só pulada: a TV confere antes, isto é a segunda trava.
    for (const r of o.rules) { const k = ruleKey(r); if (k) out.add(k); }
  }
  return [...out];
}

// A casa vira um código: sha-256(sal + IP), 16 hex. Sem o sal, ninguém volta ao IP por tabela.
export async function homeKey(ip, salt) {
  const data = new TextEncoder().encode(`${salt}|${ip}`);
  const h = new Uint8Array(await crypto.subtle.digest('SHA-256', data));
  return [...h.slice(0, 8)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// A "casa" de um IP (revisor, 25/11/2026): no IPv6 cada casa recebe uma /64 inteira, então um aparelho só podia
// "ser" 5 casas trocando o fim do endereço. A casa vira o prefixo /64; o IPv4 fica inteiro.
export function homeNet(ip) {
  if (!ip || !ip.includes(':')) return ip || 'none';
  const [head, tail = ''] = ip.toLowerCase().split('::');
  const a = head ? head.split(':') : [];
  const b = tail ? tail.split(':') : [];
  const full = [...a, ...Array(Math.max(0, 8 - a.length - b.length)).fill('0'), ...b];
  return full.slice(0, 4).map((x) => (x || '0').replace(/^0+(?=.)/, '')).join(':') + '::/64';
}

// "pkg@ver:kind" -> {pkg, ver, kind}
export function parseKey(k) {
  const m = /^(.+)@(\d+):(crashy|slower)$/.exec(k);
  return m ? { pkg: m[1], ver: Number(m[2]), kind: m[3] } : null;
}

const json = (obj, status = 200, cache = 'no-store') => new Response(JSON.stringify(obj), {
  status, headers: { 'content-type': 'application/json', 'cache-control': cache },
});

export async function handleCommunity(req, env, { ip, take = async () => true }) {
  const stub = env.COMMUNITY.get(env.COMMUNITY.idFromName('lessons'));
  if (req.method === 'GET') {
    // Igual para todos: a borda guarda 1 h (uma leitura do Durable Object por hora por borda).
    const cache = typeof caches !== 'undefined' ? caches.default : null;
    const key = new Request('https://api.sofaly.tv/v1/community/lessons');
    const hit = cache && await cache.match(key);
    if (hit) return hit;
    const all = await stub.list();
    const res = json({ lessons: all.filter((r) => r.pkg), rules: all.filter((r) => r.pattern), min_homes: MIN_HOMES, window_days: WINDOW_DAYS }, 200, 'public, max-age=3600');
    if (cache) await cache.put(key, res.clone());
    return res;
  }
  if (req.method !== 'POST') return json({ error: 'method' }, 405);
  if (env.COMMUNITY_IP_LIMITER) {
    const { success } = await env.COMMUNITY_IP_LIMITER.limit({ key: ip || 'none' });
    if (!success) return json({ error: 'slow down' }, 429);
  }
  if (!(await take('community'))) return json({ error: 'slow down' }, 429);   // por dia (src/quota.js)
  // Corpo grande recusado antes de ler (o tamanho declarado), e conferido de novo depois de ler.
  if (Number(req.headers.get('content-length') || 0) > 8 * 1024) return json({ error: 'too large' }, 413);
  const text = await req.text();
  if (text.length > 8 * 1024) return json({ error: 'too large' }, 413);
  let o; try { o = JSON.parse(text); } catch { return json({ error: 'json' }, 400); }
  const lessons = cleanReport(o);
  if (!lessons) return json({ error: 'lessons' }, 400);
  if (!lessons.length) return json({ ok: true });
  const home = await homeKey(homeNet(ip), env.COMMUNITY_SALT || 'sofaly-community-v1');
  return json(await stub.report(home, lessons));
}
