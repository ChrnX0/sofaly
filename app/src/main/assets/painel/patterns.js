// Os mesmos padrões da TV no painel (25/11/2026, Paulo: "reformular o painel web e do celular nos mesmos padrões").
// Porquê: a TV ganhou visuais prontos, objetivos e pedido em frase (ondas B, D e E da simplificação); o painel tinha
// só as opções soltas, e a regra é painel e TV com as mesmas opções e o mesmo jeito.
// Como: tudo passa pelo /api/config (PainelConfig.kt), gravado pelos MESMOS objetos da TV (HomeLooks, SettingsGoals,
// SettingsIntents); este arquivo só desenha. Entra depois do app.js e do busca.js e embrulha o desenharConfig.
// Referências: Mobbin (Joi e Cal AI: objetivo primeiro; Muse e stoic.: visuais prontos em cartões; Google: a resposta
// acima da lista na busca), as mesmas da TV.
'use strict';

const OBJETIVOS = [
  ['read', 'Ler melhor', 'Aa'], ['clean', 'Tela mais limpa', '◻'], ['energy', 'Poupar energia', '☾'], ['kids', 'Proteger crianças', '☺'],
];
// Visual pronto: nome, foto (a mesma da TV, em cores) e a cor dos 3 cards da miniatura.
const VISUAIS = [
  ['sofaly', 'Sofaly', 'linear-gradient(180deg,#3a5d78 0%,#1d3b3a 55%,#0f1b1a 100%)', 'rgba(28,28,26,.96)'],
  ['night', 'Noite', 'linear-gradient(180deg,#0b1030 0%,#1a1f4a 60%,#0b0b0a 100%)', 'rgba(20, 20, 19,.55)'],
  ['mountain', 'Serra', 'linear-gradient(180deg,#9cb4c8 0%,#5f7d8e 50%,#2f3f3a 100%)', '#fff3e6'],
  ['easy', 'Fácil de ver', 'linear-gradient(180deg,#3a5d78 0%,#1d3b3a 55%,#0f1b1a 100%)', 'rgba(28,28,26,.96)'],
  ['minimal', 'Mínimo', 'linear-gradient(180deg,#141413 0%,#141413 100%)', 'rgba(28,28,26,.96)'],
];
const PEDIDOS = {
  text_bigger: 'Letra maior', text_smaller: 'Letra menor', goal_read: 'Ler melhor', goal_clean: 'Tela mais limpa',
  goal_energy: 'Poupar energia', look_sofaly: 'Visual Sofaly', look_night: 'Visual Noite', look_mountain: 'Visual Serra',
  look_minimal: 'Visual Mínimo',
};

/** 4 botões no alto das Configurações; o ativo mostra ✓ e o mesmo clique desfaz. Crianças leva à página Família. */
function desenharObjetivos(r) {
  const hub = document.querySelector('#abaAjustes .lista-hub');
  if (!hub) return;
  let caixa = $('objetivos');
  if (!caixa) {
    caixa = document.createElement('div');
    caixa.id = 'objetivos'; caixa.className = 'objetivos';
    caixa.setAttribute('aria-label', 'Configurar por objetivo');
    hub.before(caixa);
  }
  caixa.replaceChildren(...OBJETIVOS.map(([id, nome, ic]) => {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'objetivo';
    const ativo = r.objetivo === id;
    b.classList.toggle('ativo', ativo);
    b.setAttribute('aria-pressed', String(ativo));
    const i = document.createElement('span'); i.className = 'ic'; i.setAttribute('aria-hidden', 'true'); i.textContent = ativo ? '✓' : ic;
    const t = document.createElement('span'); t.className = 't'; t.textContent = nome;
    b.append(i, t);
    b.addEventListener('click', () => {
      if (id === 'kids') { mudarAba('familia'); return; }
      gravarConfig({ campo: 'objetivo', valor: id }, ativo ? 'Desfeito.' : 'Pronto. Clique de novo para desfazer.');
    });
    return b;
  }));
}

/** Visuais prontos PRIMEIRO na página Tela inicial (só na barra única: é dela que os visuais mexem). */
function desenharVisuais(r) {
  const pagina = $('abaTelainicial');
  if (!pagina) return;
  let cartao = $('cartaoVisuais');
  if (!cartao) {
    cartao = document.createElement('div');
    cartao.id = 'cartaoVisuais'; cartao.className = 'cartao';
    const h = document.createElement('h2'); h.textContent = 'Visuais prontos';
    const p = document.createElement('p'); p.className = 'aviso'; p.textContent = 'Um clique troca tudo junto. A TV muda na hora.';
    const fila = document.createElement('div'); fila.className = 'visuais'; fila.id = 'filaVisuais';
    cartao.append(h, p, fila);
    pagina.querySelector('.cabeca-pagina').after(cartao);
  }
  cartao.hidden = !r.barraUnica;
  const fila = $('filaVisuais');
  const itens = [];
  if (r.visualVoltar) itens.push(['voltar', 'Como estava', null, null]);
  itens.push(...VISUAIS);
  fila.replaceChildren(...itens.map(([id, nome, foto, card]) => {
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'visual';
    const escolhido = r.visualPronto === id;
    b.classList.toggle('escolhido', escolhido);
    b.setAttribute('aria-pressed', String(escolhido));
    const mini = document.createElement('span'); mini.className = 'mini'; mini.setAttribute('aria-hidden', 'true');
    if (foto) {
      mini.style.background = foto;
      const cards = document.createElement('span'); cards.className = 'cards';
      for (let k = 0; k < 3; k++) { const c = document.createElement('span'); c.style.background = card; cards.appendChild(c); }
      mini.appendChild(cards);
    } else mini.textContent = '↺';
    const t = document.createElement('span'); t.className = 't'; t.textContent = nome;
    b.append(mini, t);
    b.addEventListener('click', () => gravarConfig({ campo: 'visualPronto', valor: id }, id === 'voltar' ? 'Voltou como estava.' : 'Pronto.'));
    return b;
  }));
}

/** O que não vale na barra única some (onda A): o tamanho da cruz só existe no jeito cruz. */
function esconderOQueNaoVale(r) {
  const cruz = document.querySelector('[data-fino="sz"]');
  if (cruz) cruz.closest('.linha-fino').hidden = !!r.barraUnica;
}

// ---- pedido em frase (onda E): o mesmo entendimento da TV (SettingsIntents.kt), com as frases do idioma dela.
const normalizarPedido = (t) => (t || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
  .replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();

function modelosPedido(frases) {
  const m = [];
  for (const par of (frases || '').split(';')) {
    const acao = par.split('=')[0].trim();
    for (const f of (par.split('=')[1] || '').split('|')) {
      const ws = normalizarPedido(f).split(' ').filter(Boolean);
      if (ws.length) m.push([acao.toLowerCase(), ws]);
    }
  }
  return m;
}

/** A ação que a frase pede (nome em minúsculas), ou null. Uma palavra só continua sendo busca. */
function entenderPedido(frase) {
  if (typeof estadoConfig === 'undefined' || !estadoConfig || !estadoConfig.pedidoFrases) return null;
  const palavras = new Set(normalizarPedido(frase).split(' '));
  if (palavras.size < 2) return null;
  let melhor = null, tam = 0;
  for (const [a, ws] of modelosPedido(estadoConfig.pedidoFrases)) {
    if (ws.length > tam && ws.every((w) => palavras.has(w))) { melhor = a; tam = ws.length; }
  }
  return melhor;
}

/** O item "Fazer agora" que a busca põe no topo; o aviso traz o Desfazer. */
function itemDoPedido(frase) {
  const a = entenderPedido(frase);
  if (!a || !PEDIDOS[a]) return null;
  const nome = PEDIDOS[a];
  return { tipo: 'Fazer agora', nome, icone: '✓', fazer: async () => {
    desenharConfig(await chamar('POST', '/api/config', { campo: 'pedido', valor: a }));
    const desfazer = async () => { desenharConfig(await chamar('POST', '/api/config', { campo: 'pedido', valor: 'desfazer' })); aviso('Desfeito.', 'ok'); };
    if (typeof avisoDesfazer === 'function') avisoDesfazer('Pronto.', null, desfazer); else aviso('Pronto.', 'ok');
  } };
}

// Embrulha o desenharConfig do app.js: cada vez que o estado chega, as peças novas se redesenham junto.
(function () {
  const desenharAntes = desenharConfig;
  // eslint-disable-next-line no-global-assign
  desenharConfig = function (r) {
    desenharAntes(r);
    try { desenharObjetivos(r); desenharVisuais(r); esconderOQueNaoVale(r); } catch (e) { console.warn('patterns', e); }
  };
  if (typeof estadoConfig !== 'undefined' && estadoConfig) desenharConfig(estadoConfig);
})();
