// Versões seguradas (Brainstorm 26/11/2026, item 22). Pública: GET /v1/updates/hold.
//
// Por quê: a vigia de cada TV só vê as quedas DELA. Quando uma versão sai ruim (trava no Chromecast de 2 GB, mas não
// no do desenvolvedor), as outras TVs ainda instalariam. Como: o Atualizador pergunta aqui antes de instalar; uma
// versão da lista não é instalada (a TV espera a próxima). A lista é a variável UPDATES_HOLD (versionCodes separados
// por vírgula), mudada no painel da Cloudflare sem deploy. Falhou a consulta = instala como antes (a vigia local
// continua valendo): o servidor fora do ar nunca trava a atualização de ninguém.

const MAX = 50;

/** "475010, 475020,lixo" -> [475010, 475020]. Só números de 1 a 12 dígitos, sem repetir, no máximo 50. */
export function parseHold(text) {
  const out = [];
  for (const part of String(text ?? '').split(',')) {
    const t = part.trim();
    if (!/^[0-9]{1,12}$/.test(t)) continue;
    const n = Number(t);
    if (n > 0 && !out.includes(n)) out.push(n);
    if (out.length >= MAX) break;
  }
  return out;
}

export function holdBody(env) {
  return { hold: parseHold(env.UPDATES_HOLD) };
}

export function handleHold(env) {
  // 5 min na borda: segurar uma versão chega a todas as TVs logo, sem cada consulta acordar o Worker.
  return new Response(JSON.stringify(holdBody(env)), {
    headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=300' },
  });
}
