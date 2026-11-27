// Cota por aparelho e por casa (Brainstorm 26/11/2026, item 23).
// Por quê: as rotas abertas (clima, podcasts, fala/resumo, fichas) só tinham limite por minuto por IP e um teto global.
// Uma TV com defeito (ou um script) podia gastar sozinha o teto de TODAS as TVs: o teto global da WeatherAPI é 20/min e o
// limite por IP era 30/min. Agora há um contador por DIA:
//   - por aparelho (id do relay, quando o pedido vem assinado com X-Sofaly-*), para uma TV não gastar a cota das outras;
//   - por rede (IPv4 inteiro ou IPv6 /64), mais alto, porque criar um id novo é de graça e várias casas podem sair
//     pelo mesmo IP (CGNAT das operadoras). TV antiga, sem assinatura, conta só aqui, com o mesmo teto.
// Só gasta o que custa: no clima e nos podcasts, só o pedido que passou do cache e vai ao fornecedor.
// Contadores num Durable Object por chave (quotaStore.js, SQLite), um dia UTC por linha; somem depois de 2 dias.
import { verifySignature } from './rules.js';
import { homeNet } from './community.js';

// Teto por dia, por aparelho. A rede (IP) ganha [NET_FACTOR] vezes isso.
export const DAILY = {
  weather: 200,        // pedidos à WeatherAPI (fora do cache); uma TV pede ~1 a cada 30 min
  weather_open: 200,   // pedidos ao Open-Meteo (fora do cache)
  podcasts: 300,       // buscas ao Podcast Index (fora do cache)
  transcribe: 400,     // pedaços de ~30 s de fala (Workers AI)
  summary: 40,         // resumos de episódio (Workers AI)
  community: 4,        // fichas comunitárias (a TV manda 1x por dia)
  report: 5,           // relatos de problema (src/reports.js)
  fleet: 4,            // pacote do dia da rede de Sofys (src/fleet.js; a TV manda 1x por dia)
};
export const NET_FACTOR = 10;
// Pedido assinado: a rede ganha bem mais (revisor 26/11: atrás de CGNAT, muitas casas dividem um IP; o id já limita
// cada TV, então a rede só segura quem fabrica ids).
export const SIGNED_NET_FACTOR = 50;
export const BUCKETS = new Set(Object.keys(DAILY));

/** As chaves que pagam este pedido: o aparelho (se assinou) e a rede. */
export function keysFor(id, ip) {
  const keys = [`net:${homeNet(ip)}`];
  if (id) keys.unshift(`id:${id}`);
  return keys;
}

export function limitFor(bucket, key, signed = false) {
  const base = DAILY[bucket] ?? 0;
  return key.startsWith('net:') ? base * (signed ? SIGNED_NET_FACTOR : NET_FACTOR) : base;
}

/** O nome do contador: a rede vira um hash com sal (o endereço não fica nem no nome do contador). */
export async function keyName(key, salt = 'sofaly-quota-v1') {
  if (!key.startsWith('net:')) return key;
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${salt}|${key}`));
  return 'net:' + [...new Uint8Array(d)].slice(0, 16).map((b) => b.toString(16).padStart(2, '0')).join('');
}

export const utcDayMs = (now) => Math.floor(now / 86_400_000);

/**
 * Quem pede, pela assinatura (opcional nas rotas abertas). Assinatura presente mas errada = trata como sem assinatura
 * (não recusa: a TV com relógio errado continua tendo clima), mas nunca ganha a cota de um id.
 */
export async function identify(req, body, nowSec = Math.floor(Date.now() / 1000)) {
  if (!req.headers.get('x-sofaly-signature')) return null;
  try {
    const path = new URL(req.url).pathname;
    const who = await verifySignature({ method: req.method, path, headers: req.headers, body: body ?? new Uint8Array(0), nowSec });
    return who.error ? null : who.id;
  } catch { return null; }
}

/**
 * Cria a função `take(bucket)` de um pedido: true = pode, false = passou do teto de hoje (a rota responde 429).
 * Sem o binding QUOTA (testes, preview), sempre pode.
 */
export function quotaFor(env, id, ip, now = () => Date.now()) {
  return async (bucket) => {
    if (!env.QUOTA || !BUCKETS.has(bucket)) return true;
    const day = utcDayMs(now());
    for (const key of keysFor(id, ip)) {
      try {
        const stub = env.QUOTA.get(env.QUOTA.idFromName(await keyName(key, env.QUOTA_SALT)));
        if (!(await stub.take(bucket, limitFor(bucket, key, !!id), day))) return false;
      } catch { /* contador fora do ar: não derruba o serviço (os limites por minuto continuam) */ }
    }
    return true;
  };
}
