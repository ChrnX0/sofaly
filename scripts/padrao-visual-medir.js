// Mede uma página aberta no navegador contra o padrão visual do Sofaly (auditoria 27/11/2026). Roda DENTRO da página
// (page.evaluate) e devolve [tipo, valor, texto]:
//   contraste  letra contra o fundo de verdade atrás dela (WCAG AA: 4,5:1; letra grande, 3:1)
//   letra      menor que opts.minFonte px
//   azul       fundo azulado (escuro NUNCA azulado: grafite neutro)
//   dica       dica de tecla ("Ctrl K", "Esc", "aperte OK para…", "segure VOLTAR")
// Partes que são desenho (maquete de TV, céu, miniatura de tema) ficam de fora com opts.ignorar (seletor CSS).
(opts) => {
  const ignorar = opts.ignorar || '';
  const vis = (el) => { const s = getComputedStyle(el); const b = el.getBoundingClientRect();
    return s.display !== 'none' && s.visibility !== 'hidden' && +s.opacity > 0.05 && b.width > 0 && b.height > 0; };
  const rgba = (c) => { const m = (c.match(/[\d.]+/g) || []).map(Number); if (m.length < 3) return null;
    const k = /^color\(srgb/.test(c) ? 255 : 1;   // color-mix() sai como color(srgb 0..1)
    return [m[0] * k, m[1] * k, m[2] * k, m.length > 3 ? m[3] : 1]; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const mix = (top, under) => { const a = top[3]; return [0, 1, 2].map((i) => top[i] * a + under[i] * (1 - a)).concat(1); };
  const opacidade = (el) => { let o = 1; for (let e = el; e; e = e.parentElement) o *= +getComputedStyle(e).opacity; return o; };
  // Fundo: sobe pelos pais somando as cores semitransparentes; imagem ou degradê no caminho = não dá para medir (null).
  const fundo = (el) => {
    const camadas = [];
    for (let e = el; e; e = e.parentElement) {
      const s = getComputedStyle(e);
      if (s.backgroundImage && s.backgroundImage !== 'none') return null;
      const c = rgba(s.backgroundColor);
      if (c && c[3] > 0) { camadas.push(c); if (c[3] >= 0.99) break; }
    }
    let base = rgba(getComputedStyle(document.documentElement).backgroundColor);
    if (!base || base[3] < 0.99) base = rgba(getComputedStyle(document.body).backgroundColor);
    if (!base || base[3] < 0.99) base = [255, 255, 255, 1];
    for (let i = camadas.length - 1; i >= 0; i--) base = mix(camadas[i], base);
    return base;
  };
  const fora = (el) => (ignorar && el.closest(ignorar)) || el.closest('script,style,noscript,svg,[aria-hidden=true],[disabled],[aria-disabled=true],.sr,.sr-only');
  const out = [];
  const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const vistos = new Set();
  for (let n; (n = w.nextNode());) {
    const t = n.nodeValue.replace(/\s+/g, ' ').trim(); const el = n.parentElement;
    if (!t || t.length < 2 || !el || vistos.has(el) || fora(el) || !vis(el)) continue;
    vistos.add(el);
    const s = getComputedStyle(el);
    if (s.clip !== 'auto' && s.position === 'absolute') continue;   // só para leitor de tela
    const fs = parseFloat(s.fontSize); const peso = +s.fontWeight || 400;
    if (fs < opts.minFonte - 0.05) out.push(['letra', `${fs.toFixed(1)}px`, t.slice(0, 60)]);
    const bg = fundo(el); const fg = rgba(s.color);
    if (bg && fg) {
      const c = mix([fg[0], fg[1], fg[2], fg[3] * opacidade(el)], bg);
      const L1 = lum(c), L2 = lum(bg); const r = (Math.max(L1, L2) + 0.05) / (Math.min(L1, L2) + 0.05);
      const min = fs >= 24 || (fs >= 18.66 && peso >= 700) ? 3 : 4.5;
      if (r < min - 0.05) out.push(['contraste', `${r.toFixed(2)}:1 (mínimo ${min})`, t.slice(0, 60)]);
    }
    if (opts.dicas && (/\b(Ctrl|Esc|Enter)\b/.test(t) || /\b(aperte|pressione|segure) (o |a )?(VOLTAR|OK|HOME|tecla)\b|\((segure|aperte) [A-Z]+\)|\b(press|hold) (the )?(BACK|OK|HOME)\b/i.test(t)))
      out.push(['dica', '', t.slice(0, 80)]);
  }
  for (const el of document.querySelectorAll('body, body *')) {
    if (fora(el) || !vis(el)) continue;
    const s = getComputedStyle(el);
    for (const cc of [s.backgroundColor, ...(s.backgroundImage.match(/(rgba?|color)\([^)]*\)/g) || [])]) {
      const c = rgba(cc); if (!c || c[3] < 0.05) continue;
      if (c[2] - Math.max(c[0], c[1]) >= 12 && c[0] + c[1] + c[2] < 360)
        out.push(['azul', cc, (el.id ? '#' + el.id : '') + (typeof el.className === 'string' && el.className ? '.' + el.className.split(' ')[0] : el.tagName.toLowerCase())]);
    }
  }
  return out;
}
