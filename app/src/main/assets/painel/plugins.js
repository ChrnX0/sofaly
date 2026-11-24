// Cartão "Plugins" da página Tela inicial (24/11/2026; rotas em PanelPlugins.kt). O mesmo que a TV mostra em
// Configurações › Tela inicial › Plugins: fundos, pacotes de ícones e cartões de outros apps, e a vitrine curada.
// Todo texto vem da TV já traduzido (r.textos): este arquivo não tem frase própria, então não entra no i18n.
// Nomes de plugins são de terceiros: só por textContent. Toda mudança pede o PIN (confirmar + PrecisaPin).
'use strict';

document.addEventListener('aba', (ev) => { if (ev.detail === 'telainicial') carregarPlugins(); });

let ultimoPlugins = null;

async function carregarPlugins() {
  const status = $('statusPlugins');
  if (!status) return;
  mostrar(status, '');
  try { ultimoPlugins = await chamar('GET', '/api/plugins'); } catch (e) { mostrar(status, textoErro(e)); return; }
  desenharPlugins();
}

function elPlugin(tag, props) { return Object.assign(document.createElement(tag), props || {}); }

/** Uma linha: nome em negrito, detalhe pequeno e (opcional) um botão. */
function linhaPlugin(nome, detalhe, rotulo, acao) {
  const p = elPlugin('p');
  p.append(elPlugin('strong', { textContent: nome }));
  if (detalhe) p.append(elPlugin('span', { className: 'aviso-curto', textContent: ` · ${detalhe}` }));
  if (rotulo) {
    const b = elPlugin('button', { type: 'button', className: 'botao-secundario', textContent: rotulo });
    if (acao) b.addEventListener('click', acao); else b.disabled = true;
    p.append(' ', b);
  }
  return p;
}

/** Pede a mudança com o PIN (modal de confirmação) e redesenha com o estado que a TV devolve. */
async function mudarPlugin(titulo, texto, rotulo, rota, corpo) {
  const status = $('statusPlugins');
  mostrar(status, '');
  try {
    const r = await confirmar(titulo, texto, rotulo, () => chamar('POST', `/api/plugins/${rota}`, corpo), false);
    if (r) { ultimoPlugins = r; desenharPlugins(); }
  } catch (e) { mostrar(status, textoErro(e)); }
}

function desenharPlugins() {
  const r = ultimoPlugins;
  if (!r) return;
  const t = r.textos || {};
  $('detPlugins').textContent = t.det || '';
  const caixa = $('listaPlugins');
  caixa.replaceChildren();
  const secao = (nome) => caixa.append(elPlugin('h3', { textContent: nome }));
  const vazio = (texto) => caixa.append(elPlugin('p', { className: 'aviso-curto', textContent: texto }));

  secao(t.fundos);
  if (!r.fundos.length) vazio(t.nenhumFundo);
  for (const f of r.fundos) {
    caixa.append(f.emUso
      ? linhaPlugin(f.nome, t.emUso, t.parar, () => mudarPlugin(f.nome, t.parar, t.parar, 'fundo', { id: '' }))
      : linhaPlugin(f.nome, f.saude ? t.saude : '', t.usarFundo, () => mudarPlugin(f.nome, t.det, t.usarFundo, 'fundo', { id: f.id })));
  }

  secao(t.icones);
  if (!r.icones.length) vazio(t.nenhumIcone);
  for (const i of r.icones) {
    caixa.append(i.emUso
      ? linhaPlugin(i.nome, t.emUso, t.parar, () => mudarPlugin(i.nome, t.parar, t.parar, 'icones', { pacote: '' }))
      : linhaPlugin(i.nome, t.iconesDet, t.usarIcones, () => mudarPlugin(i.nome, t.iconesDet, t.usarIcones, 'icones', { pacote: i.pacote })));
  }

  secao(t.cartoes);
  if (!r.cartoes.length) vazio(t.nenhumCartao);
  for (const c of r.cartoes) {
    const novo = !c.ligado;
    const rotulo = novo ? t.ligado : t.desligado;
    caixa.append(linhaPlugin(c.nome, c.saude ? t.saude : (c.ligado ? t.ligado : t.desligado), rotulo,
      () => mudarPlugin(c.nome, t.det, rotulo, 'cartao', { pacote: c.pacote, ligado: novo })));
  }

  secao(t.botoes);
  if (!(r.botoes || []).length) vazio(t.nenhumBotao);
  for (const b of r.botoes || []) {
    const novo = !b.ligado;
    const rotulo = novo ? t.ligado : t.desligado;
    caixa.append(linhaPlugin(b.nome, b.saude ? t.saude : (b.ligado ? t.ligado : t.desligado), rotulo,
      () => mudarPlugin(b.nome, t.det, rotulo, 'botao', { pacote: b.pacote, ligado: novo })));
  }

  secao(t.descobrir);
  for (const v of r.vitrine) {
    const det = `${v.tipo} · ${v.descricao}`;
    if (v.instalado) { caixa.append(linhaPlugin(v.nome, det, t.instalado, null)); continue; }
    const linha = v.acao === 'INSTALAR'
      ? linhaPlugin(v.nome, det, t.instalar, () => mudarPlugin(v.nome, v.descricao, t.instalar, 'instalar', { id: v.id }))
      : linhaPlugin(v.nome, det, '', null);
    if (v.pagina) {
      const a = elPlugin('a', { href: v.pagina, target: '_blank', rel: 'noopener noreferrer', textContent: v.acao === 'PLAY' ? t.abrirPlay : v.pagina.replace(/^https:\/\//, '') });
      linha.append(' ', a);
    }
    caixa.append(linha);
  }
  caixa.append(elPlugin('p', { className: 'aviso-curto', textContent: t.seguranca }));
}
