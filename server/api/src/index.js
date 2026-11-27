// API da Sofaly (Cloudflare Worker, api.sofaly.tv). Roteia cada pedido para a caixa postal (Durable Object) certa.
// Rotas (todas assinadas pelo aparelho com Ed25519, menos as marcadas "pública"):
//   POST   /v1/envelope/<id>       manda um envelope cifrado para a caixa do aparelho <id>
//   GET    /v1/inbox               WebSocket: recebe envelopes na hora (o aparelho confirma com {"ack":[ids]})
//   GET    /v1/inbox/pending       o mesmo, sem WebSocket
//   POST   /v1/inbox/ack           {"ids":[...]} apaga o que já foi recebido
//   POST   /v1/pairing             (TV) pede um código de pareamento
//   POST   /v1/pairing/<code>      (celular) usa o código; corpo = envelope de apresentação para a TV
//   PUT    /v1/dns/txt             (TV) {"value": "..."} desafio DNS-01 do certificado dela
//   DELETE /v1/dns/txt             (TV) apaga os desafios
//   GET    /v1/dns/txt/<id>        pública: as máquinas de DNS leem o desafio daqui
//   GET    /v1/health              pública; ?deep=1 diz qual serviço está sem chave/binding (src/health.js)
//   GET    /v1/addons/recommended  pública: lista curada de add-ons (src/addons.js)
//   GET    /v1/plugins/recommended pública: vitrine de plugins (src/plugins.js)
//   GET    /v1/addons/public-domain/...  pública: nosso add-on de filmes de domínio público (src/publicdomain.js)
//   GET    /v1/podcasts/search     pública: busca no Podcast Index (src/podcasts.js), ?q=&lang=&max=
//   GET    /v1/podcasts/trending   pública: em alta no idioma, ?lang=&max=
//   POST   /v1/transcribe      pública: texto da fala de um pedaço de podcast (Workers AI, src/transcribe.js)
//   POST   /v1/summary         pública: resumo do episódio + trechos de propaganda (Workers AI)
//   GET    /v1/weather             pública: previsão do plano Pro (WeatherAPI), ?lat=&lon= (src/weather.js)
//   GET    /v1/weather/open        pública: previsão do Básico, consenso de 4 modelos abertos, ?lat=&lon= (src/weatherOpen.js)
//   POST   /v1/community/lessons   pública: lições da Sofy de uma casa (opt-in na TV), só pacote+versão+tipo (src/community.js)
//   GET    /v1/community/lessons   pública: as lições vistas em 5+ casas nos últimos 14 dias
//   POST   /v1/community/cases     pública: o pacote do dia da rede de Sofys (opt-in na TV), só campos fixos (src/fleet.js)
//   GET    /v1/community/cases/summary  só com o REPORTS_TOKEN: os grupos de casos de [minHomes]+ casas, para a IA central
//   GET    /v1/community/recipes   pública: as receitas da última noite (src/recipes.js), só dado; a TV começa em sombra
//   GET    /v1/updates/hold        pública: versões que as TVs não devem instalar (src/updates.js, variável UPDATES_HOLD)
//   POST   /v1/report              assinada: relato de problema, guardado em particular (src/reports.js)
//   GET    /v1/reports             só com o REPORTS_TOKEN: a fila de relatos, para a Sofy virar issue sem dado pessoal
// As rotas públicas aceitam a assinatura sem exigir: com ela, a cota do dia é por aparelho (src/quota.js).
import { verifySignature, ID_RE, CODE_RE, newCode, parseJson } from './rules.js';
import { recommendedBody } from './addons.js';
import { pluginsBody } from './plugins.js';
import { handlePodcasts } from './podcasts.js';
import { handleWeather } from './weather.js';
import { handleWeatherOpen } from './weatherOpen.js';
import { handleTranscribe, MAX_B64 } from './transcribe.js';
import { handleCommunity } from './community.js';
import { handleFleetPost, handleFleetSummary } from './fleet.js';
import { handleRecipes, nightly } from './recipes.js';
import { handleHold } from './updates.js';
import { healthBody } from './health.js';
import { identify, quotaFor } from './quota.js';
import { handleReportPost, handleReportList } from './reports.js';
import { BASE_PATH as PD_BASE, handlePublicDomain } from './publicdomain.js';
export { Mailbox } from './mailbox.js';
export { Pairing } from './pairing.js';
export { Community } from './communityStore.js';
export { Fleet } from './fleetStore.js';
export { Quota } from './quotaStore.js';
export { Reports } from './reportsStore.js';

const json = (obj, status = 200) => new Response(JSON.stringify(obj), {
  status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
});
const fail = (r) => json({ error: r.error }, r.status ?? 400);

export default {
  // A noite da rede de Sofys (cron em wrangler.toml): refaz o resumo e as receitas. Com DISABLED=1, nada.
  async scheduled(event, env, ctx) {
    if (env.DISABLED === '1') return;
    ctx.waitUntil(nightly(env, event.scheduledTime ?? Date.now()));
  },

  async fetch(req, env) {
    const url = new URL(req.url);
    const path = url.pathname;
    // Chave de desligar tudo (variável DISABLED=1 no painel da Cloudflare): o app segue funcionando em casa.
    if (env.DISABLED === '1') return json({ error: 'api disabled' }, 503);
    if (path === '/v1/health') return json(healthBody(env, url.searchParams.get('deep') === '1'));
    if (path === '/v1/updates/hold' && req.method === 'GET') return handleHold(env);
    if (path === '/v1/addons/recommended' && req.method === 'GET') {
      // Igual para todos e muda raramente: a borda da Cloudflare pode guardar 1 h.
      return new Response(JSON.stringify(recommendedBody()), {
        headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=3600' },
      });
    }

    if (path === '/v1/plugins/recommended' && req.method === 'GET') {
      return new Response(JSON.stringify(pluginsBody()), {
        headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=3600' },
      });
    }

    if (path.startsWith(`${PD_BASE}/`) && req.method === 'GET') return handlePublicDomain(path);

    const ip = req.headers.get('cf-connecting-ip') ?? '';
    // Cota do dia (item 23): por aparelho quando o pedido vem assinado, e sempre pela rede.
    // Preguiçosa: a assinatura só é conferida quando o pedido vai gastar (fora do cache).
    const quota = async (body) => {
      let id;
      return async (bucket) => quotaFor(env, id === undefined ? (id = await identify(req, body)) : id, ip)(bucket);
    };
    // O corpo só é copiado para conferir a assinatura quando há assinatura e o tamanho declarado cabe (revisor 26/11:
    // copiar antes de olhar o tamanho gastava memória com qualquer corpo grande). Sem isso, conta como sem assinatura.
    const signedBody = async (limit) => {
      const n = Number(req.headers.get('content-length') ?? 0);
      if (!req.headers.get('x-sofaly-signature') || !(n > 0) || n > limit) return undefined;
      return new Uint8Array(await req.clone().arrayBuffer());
    };

    const podcasts = path.match(/^\/v1\/podcasts\/(search|trending)$/);
    if (podcasts && req.method === 'GET') return handlePodcasts(podcasts[1], url, env, { ip, take: await quota() });

    const ia = path.match(/^\/v1\/(transcribe|summary)$/);
    if (ia && req.method === 'POST') {
      const body = await signedBody(MAX_B64 + 4096);
      return handleTranscribe(ia[1], req, url, env, { ip, take: await quota(body) });
    }

    if (path === '/v1/community/lessons') {
      const body = req.method === 'POST' ? await signedBody(256 * 1024) : undefined;
      return handleCommunity(req, env, { ip, take: await quota(body) });
    }

    if (path === '/v1/community/cases' && req.method === 'POST') {
      const body = await signedBody(64 * 1024);
      return handleFleetPost(req, env, { ip, take: await quota(body) });
    }
    if (path === '/v1/community/cases/summary' && req.method === 'GET') return handleFleetSummary(req, env);
    // Receitas da rede de Sofys (pública, igual para todos; recipes.js). Só dado: a TV confere e começa em sombra.
    if (path === '/v1/community/recipes' && req.method === 'GET') return handleRecipes(env);

    if (path === '/v1/weather/open' && req.method === 'GET') return handleWeatherOpen(url, env, { ip, take: await quota() });
    if (path === '/v1/weather' && req.method === 'GET') return handleWeather(url, env, { ip, take: await quota() });

    if (path === '/v1/reports' && req.method === 'GET') return handleReportList(req, url, env);

    const publicTxt = path.match(/^\/v1\/dns\/txt\/([a-z2-7]{20})$/);
    if (publicTxt && req.method === 'GET') {
      return json({ values: await mailbox(env, publicTxt[1]).readTxt() });
    }

    // Daqui para baixo, só quem assinou. O corpo é lido inteiro porque entra na assinatura (máx. 64 KiB + folga).
    const body = new Uint8Array(await req.arrayBuffer());
    if (body.byteLength > 70 * 1024) return json({ error: 'too large' }, 413);
    const who = await verifySignature({
      method: req.method, path, headers: req.headers, body, nowSec: Math.floor(Date.now() / 1000),
    });
    if (who.error) return json({ error: who.error }, 401);

    if (path === '/v1/report' && req.method === 'POST') {
      return handleReportPost(body, env, who.id, quotaFor(env, who.id, ip));
    }

    const envelope = path.match(/^\/v1\/envelope\/([a-z2-7]{20})$/);
    if (envelope && req.method === 'POST') {
      const r = await mailbox(env, envelope[1]).deliver(who.id, body.buffer);
      return r.error ? fail(r) : json(r, 201);
    }

    if (path === '/v1/inbox' && req.headers.get('upgrade') === 'websocket') {
      // Encaminha o pedido inteiro: o Durable Object é quem segura o WebSocket.
      return mailbox(env, who.id).fetch(new Request('https://mailbox/connect', req));
    }
    if (path === '/v1/inbox/pending' && req.method === 'GET') {
      return json({ envelopes: await mailbox(env, who.id).pending() });
    }
    if (path === '/v1/inbox/ack' && req.method === 'POST') {
      const { ids } = parseJson(body);
      if (!Array.isArray(ids)) return json({ error: 'ids' }, 400);
      await mailbox(env, who.id).ack(ids);
      return json({ ok: true });
    }

    if (path === '/v1/pairing' && req.method === 'POST') {
      for (let attempt = 0; attempt < 3; attempt++) {
        const code = newCode();
        const r = await pairing(env, code).open(who.id);
        if (!r.error) return json({ code }, 201);
      }
      return json({ error: 'try again' }, 503);
    }
    const use = path.match(/^\/v1\/pairing\/([A-Z2-9]{8})$/);
    if (use && req.method === 'POST' && CODE_RE.test(use[1])) {
      const r = await pairing(env, use[1]).use();
      if (r.error) return fail(r);
      const delivery = await mailbox(env, r.tv).deliver(who.id, body.buffer);
      return delivery.error ? fail(delivery) : json({ tv: r.tv });
    }

    if (path === '/v1/dns/txt' && req.method === 'PUT') {
      const r = await mailbox(env, who.id).setTxt(String(parseJson(body).value ?? ''));
      return r.error ? fail(r) : json({ ok: true });
    }
    if (path === '/v1/dns/txt' && req.method === 'DELETE') {
      await mailbox(env, who.id).deleteTxt();
      return json({ ok: true });
    }

    return json({ error: 'unknown route' }, 404);
  },
};

// O id do Durable Object vem do id do aparelho: a mesma caixa sempre, em qualquer lugar do mundo.
function mailbox(env, id) {
  if (!ID_RE.test(id)) throw new Error('invalid id');
  return env.MAILBOX.get(env.MAILBOX.idFromName(id));
}
const pairing = (env, code) => env.PAIRING.get(env.PAIRING.idFromName(code));
