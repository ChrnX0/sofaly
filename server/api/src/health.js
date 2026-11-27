// Saúde do serviço por parte (Brainstorm 26/11/2026, item 25). Pública: GET /v1/health?deep=1.
//
// Por quê: clima, podcasts, transcrição, plugins e caixa postal moram no mesmo Worker; quando um deles para (chave
// vencida, binding apagado no painel da Cloudflare), a TV só via "não funcionou". Aqui cada serviço diz se tem o que
// precisa para responder (chave e binding presentes). Nunca devolve o valor de uma chave, só true/false. O /v1/health
// sem ?deep continua {ok:true}, para o monitor externo.

export function services(env) {
  const has = (v) => typeof v === 'string' ? v.length > 0 : v != null;
  return {
    podcasts: has(env.PODCASTINDEX_API_KEY) && has(env.PODCASTINDEX_API_SECRET),
    weather_open: has(env.OPEN_MODELS_KEY),
    weather_pro: has(env.WEATHERAPI_KEY),
    ai: has(env.AI),
    community: has(env.COMMUNITY) && has(env.COMMUNITY_SALT),
    mailbox: has(env.MAILBOX) && has(env.PAIRING),
    fleet: has(env.FLEET),
  };
}

export function healthBody(env, deep) {
  if (!deep) return { ok: true };
  const s = services(env);
  return { ok: Object.values(s).every(Boolean), services: s };
}
