// Revisão automática de um pedido de plugin (proposta C, 24/11/2026). Lê o corpo da issue (formulário
// plugin_submission.yml), aplica as MESMAS regras que a TV aplica à vitrine (PluginCatalog.parse) e confere na Play Store
// que o app existe. Resultado: "ok" (pronto para entrar na lista, com a linha já montada), "doubt" (uma pessoa olha) ou
// "reject" (fora das regras, com o motivo). Sem dependências. Roda com: node plugin-review.mjs < corpo.md
import { readFileSync } from 'node:fs';

const PACOTE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;

/** Corpo do formulário do GitHub: "### Rótulo\n\nvalor" -> { rótulo: valor }. */
export function campos(corpo) {
  const out = {};
  for (const bloco of corpo.split(/^### /m).slice(1)) {
    const [titulo, ...resto] = bloco.split('\n');
    out[titulo.trim()] = resto.join('\n').trim().replace(/^_No response_$/, '');
  }
  return out;
}

/** Regras fixas (sem rede). Devolve { erros: [...], entrada }. */
export function conferir(f) {
  const erros = [];
  const pacote = (f['Play Store package'] || '').trim();
  const tipo = (f['Kind'] || '').trim();
  const nome = (f['Name shown on the TV'] || '').replace(/[\u0000-\u001f]/g, '').trim();
  const desc = (f['One line in English'] || '').replace(/[\u0000-\u001f]/g, '').trim();
  if (!PACOTE.test(pacote) || pacote.length > 150) erros.push('the package is not a valid Android package name');
  if (!['wallpaper', 'icons'].includes(tipo)) erros.push('kind must be wallpaper or icons');
  if (!nome || nome.length > 40) erros.push('the name must have 1 to 40 letters');
  if (/projectivy/i.test(nome)) erros.push("the name can't use another launcher's name");
  if (!desc || desc.length > 120) erros.push('the description must have 1 to 120 letters');
  if (/https?:\/\//i.test(nome + desc)) erros.push('no links in the name or description');
  const marcadas = ((f['Rules'] || '').match(/- \[x\]/gi) || []).length;
  if (marcadas < 2) erros.push('both rules must be checked');
  const id = nome.toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'plugin';
  return { erros, entrada: { id, type: tipo, name: nome, play: pacote, description: { en: desc } } };
}

/** A página da Play existe? true / false / null (não deu para saber: vira "doubt"). */
export async function naPlay(pacote, buscar = fetch) {
  try {
    const r = await buscar(`https://play.google.com/store/apps/details?id=${encodeURIComponent(pacote)}&hl=en`, { redirect: 'follow' });
    if (r.status === 404) return false;
    return r.ok ? true : null;
  } catch { return null; }
}

export async function revisar(corpo, buscar = fetch) {
  const { erros, entrada } = conferir(campos(corpo));
  if (erros.length) return { resultado: 'reject', motivos: erros, entrada };
  const play = await naPlay(entrada.play, buscar);
  if (play === false) return { resultado: 'reject', motivos: ['the package is not on the Play Store'], entrada };
  if (play === null) return { resultado: 'doubt', motivos: ["couldn't reach the Play Store; a person will check"], entrada };
  return { resultado: 'ok', motivos: [], entrada };
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop())) {
  const r = await revisar(readFileSync(0, 'utf8'));
  process.stdout.write(JSON.stringify(r));
}
