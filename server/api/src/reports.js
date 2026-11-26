// "Relatar problema" (Brainstorm 26/11/2026, item 6). Antes, a TV e o painel abriam uma issue no repositório PÚBLICO do
// GitHub, com os dados da TV no texto. Agora o relato vem para cá, assinado pela TV (id do relay), e fica guardado em
// particular; nada é publicado. A Sofy lê a fila (GET com o REPORTS_TOKEN) e transforma em issue só o que é técnico.
// Rotas:
//   POST /v1/report     assinada: {kind, version, code, android, model, mode, crashes, freezes, text, errors:[...]}
//   GET  /v1/reports    Authorization: Bearer <REPORTS_TOKEN>; ?after=<n> devolve os seguintes (até 100)
// Privacidade: o servidor guarda só os campos abaixo, já limpos ([scrub]: e-mail, endereço, IP, MAC, números longos e
// coordenadas viram marcadores), por [KEEP_DAYS] dias. O id da TV é guardado como um código curto (hash), não o id.

export const MAX_BODY = 16 * 1024;
export const MAX_TEXT = 2000;
export const MAX_ERRORS = 5;
export const MAX_FRAMES = 15;
export const KEEP_DAYS = 30;
export const MAX_ROWS = 5000;
const KINDS = new Set(['tv', 'errors']);

/** Tira o que identifica uma pessoa ou uma casa de um texto livre. Puro (reports.test.js). */
export function scrub(t) {
  return String(t ?? '')
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '')
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+/g, '<email>')
    .replace(/\b[a-z][a-z0-9+.-]*:\/\/[^\s<>"]+/gi, '<link>')
    .replace(/\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\b/gi, '<mac>')
    .replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, '<ip>')
    .replace(/\b[0-9a-f]{1,4}(?::[0-9a-f]{0,4}){2,7}/gi, (m) => (/::|[a-f]/i.test(m) ? '<ip>' : m))
    .replace(/-?\d{1,3}\.\d{3,}/g, '<coord>')
    .replace(/\+?\d[\d ()-]{7,}\d/g, (m) => (m.replace(/\D/g, '').length >= 9 ? '<num>' : m))
    .trim();
}

const str = (v, max) => scrub(String(v ?? '')).slice(0, max);
const int = (v) => (Number.isSafeInteger(v) && v >= 0 && v < 1e12 ? v : 0);

/** Um quadro da pilha: só "pacote.Classe.metodo(Arquivo.kt:12)" (sem mensagem de erro, que pode ter texto da casa). */
export function cleanFrame(f) {
  const t = String(f ?? '');
  const causa = /^\s*Caused by:\s*([\w$.]+)/.exec(t);   // "Caused by: Tipo: mensagem" -> só o tipo
  if (causa) return causa[1].length <= 200 ? `Caused by: ${causa[1]}` : null;
  const m = /^\s*(?:at\s+)?([\w$.<>-]+)(\((?:[\w$.-]*|Unknown Source|Native Method)(?::\d+)?\))?\s*$/.exec(t);
  return m && m[1].length <= 200 ? m[1] + (m[2] ?? '') : null;
}

/** O relato conferido e limpo, ou null (400). */
export function cleanReport(o) {
  if (!o || typeof o !== 'object' || !KINDS.has(o.kind)) return null;
  const errors = Array.isArray(o.errors) ? o.errors.slice(0, MAX_ERRORS).map((e) => ({
    type: /^[A-Za-z_$][\w$]*$/.test(e?.type ?? '') ? String(e.type).slice(0, 80) : 'error',
    day: /^\d{4}-\d{2}-\d{2}$/.test(e?.day ?? '') ? e.day : '',
    version: int(e?.version),
    stack: (Array.isArray(e?.stack) ? e.stack : []).slice(0, MAX_FRAMES).map(cleanFrame).filter(Boolean),
  })) : [];
  return {
    kind: o.kind,
    version: /^[\w.+-]{1,40}$/.test(o.version ?? '') ? o.version : '',
    code: int(o.code),
    android: str(o.android, 20),
    model: str(o.model, 60),
    mode: str(o.mode, 40),
    crashes: int(o.crashes),
    freezes: int(o.freezes),
    text: str(o.text, MAX_TEXT),
    errors,
  };
}

/** O id da TV vira um código curto: agrupa relatos da mesma TV sem guardar o id dela. */
export async function deviceTag(id, salt) {
  const h = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${salt}|${id}`)));
  return [...h.slice(0, 6)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Comparação de token em tempo constante. */
export function sameToken(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || !a || a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

const json = (obj, status = 200) => new Response(JSON.stringify(obj), {
  status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
});

const store = (env) => env.REPORTS.get(env.REPORTS.idFromName('reports'));

/** POST /v1/report, já com o id de quem assinou e a cota conferida em index.js. */
export async function handleReportPost(body, env, id, take = async () => true) {
  if (!env.REPORTS) return json({ error: 'unavailable' }, 503);
  if (body.byteLength > MAX_BODY) return json({ error: 'too large' }, 413);
  let o; try { o = JSON.parse(new TextDecoder().decode(body)); } catch { return json({ error: 'json' }, 400); }
  const r = cleanReport(o);
  if (!r) return json({ error: 'report' }, 400);
  // A cota do dia só conta relato válido (revisor 26/11: um JSON errado gastava 1 dos 5).
  if (!(await take('report'))) return json({ error: 'rate limited' }, 429);
  const tag = await deviceTag(id, env.REPORTS_SALT || 'sofaly-reports-v1');
  const saved = await store(env).add(tag, r, Date.now());
  return saved.ok ? json({ ok: true, n: saved.n }, 201) : json({ error: saved.error }, 503);
}

/** GET /v1/reports (só a Sofy, com o token). */
export async function handleReportList(req, url, env) {
  const token = env.REPORTS_TOKEN;
  if (!token || !env.REPORTS) return json({ error: 'unavailable' }, 503);
  const auth = req.headers.get('authorization') ?? '';
  if (!sameToken(auth.replace(/^Bearer\s+/i, ''), token)) return json({ error: 'unauthorized' }, 401);
  const after = Math.max(0, Number.parseInt(url.searchParams.get('after') ?? '0', 10) || 0);
  return json({ reports: await store(env).list(after, Date.now()) });
}
