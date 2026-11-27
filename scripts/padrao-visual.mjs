// Padrão visual (auditoria 27/11/2026, pedido do Paulo): contraste, tamanho de letra, fundo azulado e dica de tecla em
// TODAS as páginas do painel web e do site, no computador e no celular, nos temas escuro e claro. Reprova o que sair do
// padrão. A medida mora em padrao-visual-medir.js; o que é de propósito vai em padrao-visual-excecoes.txt (com o motivo).
// As telas da TV têm a mesma checagem em Kotlin (AuditoriaTelasTest, -Ptelas -Pauditoria), e o app do celular abre o
// próprio painel (PainelActivity), então fica coberto aqui.
//
// Uso: node scripts/padrao-visual.mjs [--so painel|site] [--chrome /caminho]   (sai 1 se algo saiu do padrão)
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
let pw;
try { pw = require('playwright'); } catch { pw = require('playwright-core'); }
const AQUI = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(AQUI, '..');
const args = process.argv.slice(2);
const arg = (n) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : null; };
const MEDIR = fs.readFileSync(path.join(AQUI, 'padrao-visual-medir.js'), 'utf8');
const EXCECOES = fs.readFileSync(path.join(AQUI, 'padrao-visual-excecoes.txt'), 'utf8').split('\n')
  .map((l) => l.trim()).filter((l) => l && !l.startsWith('#')).map((l) => {
    const [tipo, onde, texto] = l.split('|').map((x) => x.trim());
    return { tipo, onde: new RegExp(onde), texto: new RegExp(texto) };
  });

const TIPOS = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png',
  '.svg': 'image/svg+xml', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.woff2': 'font/woff2', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.txt': 'text/plain' };
// A TV de mentira do painel (a mesma ideia de painel-navegador.mjs): /api/* responde com o mínimo.
const RESPOSTAS = {
  '/api/estado': { logado: true, aparelho: 'TV de teste', naTela: 'Tela inicial' },
  '/api/apps': { apps: [{ pacote: 'com.exemplo.video', nome: 'Vídeo', oculto: false, sistema: false }] },
  '/api/tvs': { tvs: [] },
  '/api/extras/diario-tv': { itens: [], webhook: '', origens: [], ultimo: '', passos: [] },
};
function servir(pasta, api) {
  return http.createServer((req, res) => {
    const url = new URL(req.url, 'http://x');
    if (api && url.pathname.startsWith('/api/')) {
      if (url.pathname === '/api/eventos') { res.writeHead(204); res.end(); return; }
      res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(RESPOSTAS[url.pathname] || {})); return;
    }
    let p = decodeURIComponent(url.pathname); if (p.endsWith('/')) p += 'index.html';
    let arq = path.join(pasta, path.normalize(p).replace(/^(\.\.[/\\])+/, ''));
    if (!fs.existsSync(arq) && fs.existsSync(arq + '.html')) arq += '.html';
    if (!arq.startsWith(pasta) || !fs.existsSync(arq) || fs.statSync(arq).isDirectory()) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'Content-Type': TIPOS[path.extname(arq)] || 'application/octet-stream' });
    fs.createReadStream(arq).pipe(res);
  });
}
async function abrir(srv) { await new Promise((r) => srv.listen(0, '127.0.0.1', r)); return `http://127.0.0.1:${srv.address().port}/`; }

const VISOES = [
  { nome: 'computador', viewport: { width: 1366, height: 860 } },
  { nome: 'celular', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
];
const TEMAS = ['dark', 'light'];
// Desenho, não interface: maquetes de TV e celular do site, miniaturas de tema, fotos da TV ao vivo no painel.
const IGNORAR_SITE = '.screen, .tv, .stage, .phone, .mini, .shot, figure, .fig, .wn-win, [class^="scr-"], [class*=" scr-"]';
const IGNORAR_PAINEL = '#telaAoVivo, .ao-vivo-tela, video, canvas';

const chrome = arg('--chrome');
const navegador = await pw.chromium.launch(chrome ? { executablePath: chrome } : (process.env.CI ? { channel: 'chrome' } : {}));
const achados = [];
let medidas = 0;
async function medir(page, onde, opts) {
  const r = await page.evaluate(`(${MEDIR})(${JSON.stringify(opts)})`);
  medidas++;
  for (const [tipo, valor, texto] of r) {
    if (EXCECOES.some((e) => e.tipo === tipo && e.onde.test(onde) && e.texto.test(texto))) continue;
    achados.push({ tipo, onde, valor, texto });
  }
}

if (arg('--so') !== 'site') {
  const srv = servir(path.join(RAIZ, 'app/src/main/assets/painel'), true); const base = await abrir(srv);
  for (const v of VISOES) for (const tema of TEMAS) {
    const ctx = await navegador.newContext({ ...v, colorScheme: tema, reducedMotion: 'reduce', locale: 'pt-BR', serviceWorkers: 'block' });
    const page = await ctx.newPage();
    await page.goto(base, { waitUntil: 'load' });
    await page.waitForFunction(() => !document.getElementById('telaPainel').hidden, null, { timeout: 15000 });
    for (const p of await page.evaluate(() => Object.keys(PAGINAS))) {
      await page.evaluate((n) => mudarAba(n, false), p);
      await page.waitForTimeout(150);
      await medir(page, `painel/${p}/${v.nome}/${tema}`, { minFonte: 14, dicas: true, ignorar: IGNORAR_PAINEL });
    }
    await ctx.close();
  }
  srv.close();
}
if (arg('--so') !== 'painel') {
  const pasta = path.join(RAIZ, 'site'); const srv = servir(pasta, false); const base = await abrir(srv);
  // As páginas em inglês (a raiz) e em português: as outras línguas usam o mesmo HTML e CSS.
  const paginas = fs.readdirSync(pasta).filter((f) => f.endsWith('.html') && f !== 'volta.html')
    .concat(fs.readdirSync(path.join(pasta, 'pt')).filter((f) => f.endsWith('.html')).map((f) => 'pt/' + f));
  for (const v of VISOES) for (const tema of TEMAS) {
    const ctx = await navegador.newContext({ ...v, colorScheme: tema, reducedMotion: 'reduce' });
    const page = await ctx.newPage();
    for (const p of paginas) {
      await page.goto(base + p, { waitUntil: 'load' });
      await page.waitForTimeout(150);
      // No site, "Onde: segure VOLTAR" explica onde mora cada função: dica de tecla só reprova no painel.
      await medir(page, `site/${p}/${v.nome}/${tema}`, { minFonte: 14, dicas: false, ignorar: IGNORAR_SITE });
    }
    await ctx.close();
  }
  srv.close();
}
await navegador.close();

// Um achado por (tipo, texto, valor): o mesmo problema em 4 visões vira uma linha com onde apareceu.
const grupos = new Map();
for (const a of achados) {
  const k = `${a.tipo}|${a.valor}|${a.texto}`;
  if (!grupos.has(k)) grupos.set(k, { ...a, onde: [] });
  grupos.get(k).onde.push(a.onde);
}
const linhas = ['## Padrão visual (contraste, letra, azul, dica de tecla)', '', `${medidas} páginas medidas (computador e celular, escuro e claro).`, ''];
if (grupos.size) {
  linhas.push(`### ${grupos.size} fora do padrão`, '');
  for (const g of [...grupos.values()].slice(0, 150))
    linhas.push(`- **${g.tipo}** ${g.valor} "${g.texto}" em ${g.onde.slice(0, 3).join(', ')}${g.onde.length > 3 ? ` e mais ${g.onde.length - 3}` : ''}`);
} else linhas.push('Tudo no padrão.');
const relatorio = linhas.join('\n');
console.log(relatorio);
if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, relatorio + '\n');
process.exit(grupos.size ? 1 : 0);
