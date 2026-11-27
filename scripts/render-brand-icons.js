#!/usr/bin/env node
// Renders every PNG copy of the Sofaly symbol from the SAME drawing the TV uses (SofalyLogo.drawSymbol, 120 x 112 frame):
// orange sofa-TV with the graphite screen #1E1E1E chosen by Paulo on 27/11/2026 (option B) and the cream glass reflection,
// on the graphite tile #141413 of the favicon (dark is never bluish).
// Why: these PNGs were still the older navy logo (screen #1C2140, tile #121528) and nobody regenerated them, so the
// phone panel, its home-screen icon, the browser tab, the Windows network icon and the website showed a blue logo.
// Shapes kept from the old files: "none" = symbol alone on transparency (PWA icon), "tile" = rounded tile (favicon,
// UPnP), "square" = full-bleed tile (Apple touch icon, which iOS rounds itself; with the logo inside the 80% safe zone
// it is also the Android maskable PWA icon). Change the drawing in SofalyLogo.kt and here together.
// Usage: node scripts/render-brand-icons.js   (Playwright's Chromium, already in the dev container)
const { chromium } = require('playwright');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const TILE = '#141413';
const ORANGE = '#FF6A2B';

/** SofalyLogo.drawSymbol in SVG (same coordinates). `reflexo` = the cream glass highlight, too thin below 64 px. */
function simbolo(reflexo) {
  return `<path d="M60 36 L35 18 M60 36 L89 24" stroke="${ORANGE}" stroke-width="8.5" stroke-linecap="round" fill="none"/>` +
    `<path d="M51 38 A9 9 0 0 1 69 38 Z" fill="${ORANGE}"/>` +
    `<rect x="23.75" y="35.25" width="72.5" height="57.5" rx="15.75" fill="#1E1E1E" stroke="${ORANGE}" stroke-width="13.5"/>` +
    `<rect x="3.5" y="59" width="20.5" height="40.5" rx="9.5" fill="${ORANGE}"/>` +
    `<rect x="96" y="59" width="20.5" height="40.5" rx="9.5" fill="${ORANGE}"/>` +
    `<rect x="12" y="86" width="96" height="13.5" rx="5" fill="${ORANGE}"/>` +
    `<path d="M12.5 92 L24.5 92 L17 108.8 Q16 111 13.6 111 L11.4 111 Q9 111 9.3 108.6 Z M107.5 92 L95.5 92 L103 108.8 ` +
    `Q104 111 106.4 111 L108.6 111 Q111 111 110.7 108.6 Z" fill="${ORANGE}"/>` +
    (reflexo ? `<path d="M37 65 Q37 54 48 53" stroke="#FFF3E6" stroke-opacity="0.85" stroke-width="5.5" stroke-linecap="round" fill="none"/>` : '');
}
const CAIXA = { x: 3, y: 13.5, w: 114, h: 98 };   // arms, antenna caps and feet

/** SVG of size `lado` with the logo filling `escala` of it, on `fundo` ('none', 'tile' or 'square'). */
function svg(lado, escala, fundo) {
  const s = (lado * escala) / Math.max(CAIXA.w, CAIXA.h);
  const dx = (lado - CAIXA.w * s) / 2 - CAIXA.x * s;
  const dy = (lado - CAIXA.h * s) / 2 - CAIXA.y * s;
  const raio = fundo === 'tile' ? lado * 28 / 128 : 0;
  const placa = fundo === 'none' ? '' : `<rect width="${lado}" height="${lado}" rx="${raio}" fill="${TILE}"/>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${lado}" height="${lado}" viewBox="0 0 ${lado} ${lado}">` +
    `${placa}<g transform="translate(${dx} ${dy}) scale(${s})">${simbolo(lado >= 64)}</g></svg>`;
}

const ALVOS = [
  ['app/src/main/assets/painel/icone-192.png', 192, 0.95, 'none'],
  ['app/src/main/assets/painel/icone-512.png', 512, 0.95, 'none'],
  ['app/src/main/assets/painel/icone-apple.png', 180, 0.72, 'square'],
  ['app/src/main/assets/painel/icone-mascaravel.png', 512, 0.62, 'square'],   // inside the 80% safe circle
  ['app/src/main/assets/painel/sofaly-favicon-180.png', 180, 0.80, 'tile'],
  ['app/src/main/assets/painel/sofaly-favicon-32.png', 32, 0.84, 'tile'],
  ['app/src/main/assets/upnp/sofaly-120.png', 120, 0.80, 'tile'],
  ['app/src/main/assets/upnp/sofaly-48.png', 48, 0.82, 'tile'],
  ['website/assets/favicon-180.png', 180, 0.80, 'tile'],
  ['site/assets/favicon-180.png', 180, 0.80, 'tile'],
];

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const [saida, lado, escala, fundo] of ALVOS) {
    await page.setViewportSize({ width: lado, height: lado });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg(lado, escala, fundo)}</body></html>`);
    await page.locator('svg').screenshot({ path: path.join(ROOT, saida), omitBackground: true });
    console.log(saida, lado);
  }
  // Link preview of sofaly.tv (og:image, 1200 x 630 area of a 1200 x 675 picture): the symbol beside "Sofa" in white
  // and "ly" in orange, Nunito 700 like the site header, on the site's graphite (#121212 with a soft neutral light).
  const fonte = require('fs').readFileSync(path.join(ROOT, 'app/src/main/assets/painel/nunito.woff2')).toString('base64');
  const marca = `<svg width="300" height="245" viewBox="${CAIXA.x} ${CAIXA.y} ${CAIXA.w} ${CAIXA.h}">${simbolo(true)}</svg>`;
  await page.setViewportSize({ width: 1200, height: 675 });
  await page.setContent(`<html><head><style>
    @font-face { font-family: N; src: url(data:font/woff2;base64,${fonte}); font-weight: 200 1000; }
    body { margin: 0; width: 1200px; height: 675px; display: flex; align-items: center; justify-content: center; gap: 44px;
      background: radial-gradient(ellipse 70% 80% at 30% 20%, #262626 0%, #161616 55%, #121212 100%); }
    .w { font: 700 230px/1 N; letter-spacing: -.02em; color: #FFFFFF; margin-top: 18px; }
    .w b { color: #FF6A2B; font-weight: 700; }
  </style></head><body>${marca}<span class="w">Sofa<b>ly</b></span></body></html>`);
  await page.evaluate(() => document.fonts.ready);
  for (const saida of ['website/assets/og.png', 'site/assets/og.png']) {
    await page.screenshot({ path: path.join(ROOT, saida) });
    console.log(saida, '1200x675');
  }
  await browser.close();
})();
