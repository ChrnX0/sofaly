// Receitas da rede de Sofys (passo 4, Paulo 27/11/2026: "consertos voltam às TVs sem atualizar o app").
//
// Por quê: o banco (fleet.js) sabe, para cada tipo de defeito, qual ferramenta consertou em quantas casas. Para quê: a
// TV passa a tentar primeiro o que deu certo nas outras casas. Como: uma receita é SÓ DADO, nunca código: a lista de
// ferramentas que a TV já tem (as mesmas de SofyRepair.Tool, lista fechada), numa ordem. A TV confere cada campo contra
// as listas dela e descarta o que não reconhece.
//
// Esta é a camada "código" da IA híbrida: conta simples, sem modelo, de graça. Ela só olha grupos que já passaram pelo
// limiar de casas (summarize em fleet.js), então nenhuma receita sai de uma casa só quando a frota cresce. As camadas de
// IA (Workers AI para agrupar, Haiku para os casos difíceis) entram depois, em cima destes mesmos números.
//
// Segurança na TV (SofyRecipes.kt): toda receita nova começa em SOMBRA. A TV só anota se a receita teria acertado
// (a ferramenta que consertou de verdade era a primeira da receita?) e nada muda no conserto. Aplicar de verdade só
// depois de noites de acerto, e isso é um passo à parte.
import { AREAS, CAUSES, DAY_MS, TOOLS, TYPES } from './fleet.js';

export const RECIPES_VERSION = 1;
export const MIN_TRIES = 2;        // tentativas da ferramenta, somadas nas casas, para ela entrar na receita
export const MIN_RATE = 0.6;       // quantas das tentativas consertaram
export const MAX_TOOLS = 3;
export const MAX_RECIPES = 200;

// Pedir à IA e relatar não consertam nada sozinhos: nunca entram numa receita.
const NOT_A_FIX = new Set(['ASK_AI', 'REPORT']);
const rate = (s) => { const n = s.fixed + s.not_fixed + s.failed; return n ? s.fixed / n : 0; };

/** Um grupo do resumo vira receita se alguma ferramenta conserta com frequência. Senão, null. */
export function recipeOf(g, day) {
  if (!TYPES.has(g.type) || !AREAS.has(g.area) || !CAUSES.has(g.cause)) return null;
  const tools = Object.entries(g.tools ?? {})
    .filter(([t, s]) => TOOLS.has(t) && !NOT_A_FIX.has(t) && s.fixed + s.not_fixed + s.failed >= MIN_TRIES && rate(s) >= MIN_RATE)
    .sort(([ta, a], [tb, b]) => rate(b) - rate(a) || b.fixed - a.fixed || ta.localeCompare(tb))
    .slice(0, MAX_TOOLS);
  if (!tools.length) return null;
  const id = `${g.type}|${g.area}|${g.target}|${g.cause}`;
  return {
    id,
    match: { type: g.type, area: g.area, target: g.target, cause: g.cause },
    tools: tools.map(([t]) => t),
    evidence: {
      homes: g.homes, cases: g.cases,
      rates: Object.fromEntries(tools.map(([t, s]) => [t, Math.round(rate(s) * 100) / 100])),
    },
    day,
  };
}

/** Todas as receitas da noite, as com mais casas primeiro. */
export function deriveRecipes(groups, now) {
  const day = Math.floor(now / DAY_MS);
  return groups.map((g) => recipeOf(g, day)).filter(Boolean)
    .sort((a, b) => b.evidence.homes - a.evidence.homes || b.evidence.cases - a.evidence.cases || a.id.localeCompare(b.id))
    .slice(0, MAX_RECIPES);
}

const json = (body, status = 200, extra = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...extra } });

/** GET /v1/community/recipes (pública, igual para todos): a lista da última noite. A borda guarda 1 h. */
export async function handleRecipes(env) {
  if (!env.FLEET) return json({ error: 'unavailable' }, 503);
  const r = await env.FLEET.get(env.FLEET.idFromName('fleet')).recipes();
  return json({ v: RECIPES_VERSION, ...r }, 200, { 'cache-control': 'public, max-age=3600' });
}

/** A noite (cron): o Fleet refaz o resumo e grava as receitas. */
export async function nightly(env, now = Date.now()) {
  if (!env.FLEET) return { ok: false };
  return env.FLEET.get(env.FLEET.idFromName('fleet')).nightly(now, env.FLEET_MIN_HOMES);
}
