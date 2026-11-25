// Celular como parceiro da TV (24/11/2026, proposta B, "funções do Apple TV"). Página "Celular e TV".
// Usa as globais do app.js ($, chamar, mostrar) e do ui.js (aviso).
//
// Calibrar a imagem: a TV mostra padrões (PictureMath.kt), a câmera deste celular fotografa e só NÚMEROS vão para a TV
// (caixas e médias de cor). A foto nunca sai do celular. Referências de tela (Mobbin): visor escuro com cantos e uma
// ação grande (Mesh), resultado em folha embaixo do visor (Shopify, scanner de estoque), barra de passos (Posh) e
// texto calmo do que está acontecendo (Tonal, "Searching for your Tonal").
'use strict';

(function () {
  const LADO = 320;   // a foto é reduzida a 320 px de largura: sobra precisão e o celular não esquenta

  // ---------------------------------------------------------------- conselhos (o que a pessoa faz no menu da TV)

  const CONSELHO = {
    KEEP: 'Está bom.',
    TURN_ON_JUST_SCAN: 'A TV está cortando as bordas da imagem. No menu da TV, em Imagem › Formato da tela, escolha "Tamanho original" (na LG, "Just Scan").',
    RAISE_BRIGHTNESS: 'Os pretos mais escuros somem. Suba o Brilho da TV 2 pontos e meça de novo.',
    LOWER_BRIGHTNESS: 'O preto está acinzentado. Desça o Brilho da TV 2 pontos e meça de novo.',
    LOWER_CONTRAST: 'Os brancos mais claros se juntam. Desça o Contraste da TV 2 pontos e meça de novo.',
    WARMER: 'O branco puxa para o azul. Em Imagem › Temperatura da cor, escolha uma opção mais quente.',
    COOLER: 'O branco puxa para o amarelo. Em Imagem › Temperatura da cor, escolha uma opção mais fria.',
    LESS_GREEN: 'O branco puxa para o verde. No Balanço de branco (modo Especialista), desça um pouco o Verde.',
    MORE_GREEN: 'O branco puxa para o roxo. No Balanço de branco (modo Especialista), suba um pouco o Verde.',
  };
  const INCERTO = {
    no_frame: 'Não achei a TV na foto. Enquadre a tela inteira, com um pouco de parede em volta, e meça de novo.',
    camera_saturated: 'A câmera ofuscou. Afaste um pouco o celular ou diminua a luz da sala e meça de novo.',
    wb: 'Este celular não deixou travar a cor da câmera, então a cor não dá para medir com confiança.',
  };
  const PARTES = [
    ['overscan', 'Bordas'],
    ['black', 'Preto'],
    ['white', 'Branco'],
    ['color', 'Cor'],
  ];

  function textoDo(r) {
    if (!r) return 'Ainda não medido.';
    if (r.verdict === 'UNSURE') {
      if (/wb=auto/.test(r.detail || '')) return INCERTO.wb;
      return INCERTO[r.detail] || INCERTO.no_frame;
    }
    return CONSELHO[r.advice] || CONSELHO.KEEP;
  }

  // ---------------------------------------------------------------- estado na página

  async function carregar() {
    const caixa = $('celularImagemResultado');
    if (!caixa) return;
    try {
      const r = await chamar('GET', '/api/companion');
      const nome = r.displayName ? 'TV: ' + r.displayName : '';
      mostrar($('celularTvNome'), nome);
      desenharResultado(r.picture || {});
    } catch (e) { mostrar($('statusCelularImagem'), e.message); }
  }

  function desenharResultado(pic) {
    const lista = $('celularImagemResultado');
    lista.textContent = '';
    for (const [parte, nome] of PARTES) {
      const r = pic[parte];
      const li = document.createElement('li');
      li.className = 'medida ' + (!r ? 'nao' : r.verdict === 'GOOD' ? 'boa' : r.verdict === 'ADJUST' ? 'ajustar' : 'incerta');
      const t = document.createElement('strong'); t.textContent = nome;
      const s = document.createElement('span'); s.textContent = textoDo(r);
      li.append(t, s);
      lista.appendChild(li);
    }
    $('btnCorAoVivo').hidden = !(pic.color && pic.color.verdict === 'ADJUST');
  }

  // ---------------------------------------------------------------- câmera

  let fluxo = null, video = null, travou = { exp: false, wb: false };

  async function abrirCamera() {
    if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia)
      throw new Error('Para usar a câmera, abra o painel pelo endereço https (porta 8444). No http o navegador não libera a câmera.');
    try {
      fluxo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
    } catch (e) { throw new Error('Não consegui abrir a câmera. Confira se o navegador tem permissão para usá-la.'); }
    video = $('visorVideo');
    video.srcObject = fluxo;
    await video.play().catch(() => {});
    travou = { exp: false, wb: false };
  }

  function fecharCamera() {
    if (fluxo) fluxo.getTracks().forEach((t) => t.stop());
    fluxo = null;
    if (video) video.srcObject = null;
  }

  function trilha() { return fluxo && fluxo.getVideoTracks()[0]; }

  /** Trava exposição e cor da câmera no que ela vê agora (o branco da TV). Nem todo celular deixa: fica anotado. */
  async function travar() {
    const t = trilha(); if (!t || !t.getCapabilities) return;
    const cap = t.getCapabilities(), cfg = t.getSettings ? t.getSettings() : {};
    if ((cap.exposureMode || []).includes('manual') && cfg.exposureTime) {
      try { await t.applyConstraints({ advanced: [{ exposureMode: 'manual', exposureTime: cfg.exposureTime }] }); travou.exp = true; } catch (e) { /* segue automática */ }
    }
    if ((cap.whiteBalanceMode || []).includes('manual')) {
      const ct = cap.colorTemperature;
      const alvo = ct ? Math.min(Math.max(6500, ct.min), ct.max) : undefined;   // 6500 K = o branco padrão do vídeo (D65)
      try { await t.applyConstraints({ advanced: [alvo ? { whiteBalanceMode: 'manual', colorTemperature: alvo } : { whiteBalanceMode: 'manual' }] }); travou.wb = true; } catch (e) { /* segue automática */ }
    }
  }

  /** Volta a exposição para automática (para os degraus escuros e claros a câmera precisa se ajustar). */
  async function soltarExposicao() {
    const t = trilha(); if (!t || !travou.exp) return;
    try { await t.applyConstraints({ advanced: [{ exposureMode: 'continuous' }] }); } catch (e) { /* tanto faz */ }
    travou.exp = false;
  }

  const espera = (ms) => new Promise((ok) => setTimeout(ok, ms));

  /** Média de [n] quadros, em RGB, 320 px de largura. Média tira o ruído e a piscada da TV. */
  async function foto(n = 3) {
    const w = LADO, h = Math.round(LADO * (video.videoHeight || 720) / (video.videoWidth || 1280));
    const tela = document.createElement('canvas'); tela.width = w; tela.height = h;
    const ctx = tela.getContext('2d', { willReadFrequently: true });
    const soma = new Float32Array(w * h * 3);
    for (let k = 0; k < n; k++) {
      ctx.drawImage(video, 0, 0, w, h);
      const d = ctx.getImageData(0, 0, w, h).data;
      for (let i = 0, j = 0; i < d.length; i += 4, j += 3) { soma[j] += d[i]; soma[j + 1] += d[i + 1]; soma[j + 2] += d[i + 2]; }
      await espera(70);
    }
    for (let j = 0; j < soma.length; j++) soma[j] /= n;
    return { w, h, rgb: soma };
  }

  const luma = (f, p) => 0.2126 * f.rgb[p * 3] + 0.7152 * f.rgb[p * 3 + 1] + 0.0722 * f.rgb[p * 3 + 2];

  /**
   * A caixa do que acendeu entre [escuro] e [claro]: linhas e colunas onde pelo menos 30% dos pontos mudaram. Contar
   * por linha (e não pegar o ponto mais distante) ignora um reflexo solto na parede.
   */
  function caixa(escuro, claro) {
    const { w, h } = claro;
    let max = 0;
    const dif = new Float32Array(w * h);
    for (let p = 0; p < w * h; p++) { dif[p] = luma(claro, p) - luma(escuro, p); if (dif[p] > max) max = dif[p]; }
    if (max < 25) return null;   // nada acendeu: TV fora do quadro ou desligada
    const lim = max * 0.5, col = new Uint16Array(w), lin = new Uint16Array(h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (dif[y * w + x] > lim) { col[x]++; lin[y]++; }
    const cMax = Math.max(...col), lMax = Math.max(...lin);
    const pega = (a, m) => { let i = 0, f = a.length - 1; while (i < f && a[i] < m * 0.3) i++; while (f > i && a[f] < m * 0.3) f--; return [i, f + 1]; };
    const [l, r] = pega(col, cMax), [t, b] = pega(lin, lMax);
    return { l, t, r, b };
  }

  /** Brilho médio de cada uma das [n] barras verticais dentro da caixa da TV (miolo de cada barra, longe das bordas). */
  function barras(f, cx, n) {
    const out = [];
    const bw = (cx.r - cx.l) / n;
    for (let i = 0; i < n; i++) {
      const x0 = Math.round(cx.l + bw * (i + 0.25)), x1 = Math.round(cx.l + bw * (i + 0.75));
      const y0 = Math.round(cx.t + (cx.b - cx.t) * 0.25), y1 = Math.round(cx.t + (cx.b - cx.t) * 0.75);
      let s = 0, c = 0;
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) { s += luma(f, y * f.w + x); c++; }
      out.push(c ? s / c : 0);
    }
    return out;
  }

  /** Média R, G, B do miolo (metade do meio) da caixa da TV. */
  function corMedia(f, cx) {
    const x0 = Math.round(cx.l + (cx.r - cx.l) * 0.25), x1 = Math.round(cx.l + (cx.r - cx.l) * 0.75);
    const y0 = Math.round(cx.t + (cx.b - cx.t) * 0.25), y1 = Math.round(cx.t + (cx.b - cx.t) * 0.75);
    let r = 0, g = 0, b = 0, c = 0;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) { const p = (y * f.w + x) * 3; r += f.rgb[p]; g += f.rgb[p + 1]; b += f.rgb[p + 2]; c++; }
    return { r: r / c, g: g / c, b: b / c };
  }

  // ---------------------------------------------------------------- a medição

  const PASSOS = ['Achando a TV', 'Bordas', 'Preto', 'Branco', 'Cor'];
  let cancelada = false;

  function passo(i, texto) {
    $('visorPassos').style.setProperty('--feito', String(i / PASSOS.length));
    $('visorPasso').textContent = PASSOS[i];   // a barra (--feito) já mostra quanto falta
    $('visorTexto').textContent = texto;
  }

  async function padrao(nome, ms = 700) {
    if (cancelada) throw new Error('cancelada');
    await chamar('POST', '/api/companion/pattern', { pattern: nome });
    await espera(ms);   // a TV desenha (~1 quadro) e a câmera se acerta
  }

  const julgar = (corpo) => chamar('POST', '/api/companion/picture', corpo);

  async function medir() {
    cancelada = false;
    const visor = $('visorCamera');
    try {
      await abrirCamera();
      $('visorTerminar').hidden = true;
      visor.hidden = false;
      document.body.classList.add('com-visor');
      passo(0, 'Aponte o celular para a TV, com a tela inteira dentro dos cantos. Segure firme.');
      await padrao('BLACK', 1500);
      const preto = await foto();
      await padrao('WHITE', 1500);
      await travar();
      await espera(500);
      const branco = await foto();
      const tv = caixa(preto, branco);
      if (!tv) throw new Error(INCERTO.no_frame);

      passo(1, 'Medindo as bordas.');
      await padrao('BLACK');
      const pretoTravado = await foto();
      await padrao('INSET');
      const dentro = caixa(pretoTravado, await foto());
      await julgar({ part: 'overscan', save: true, visible: { l: tv.l, t: tv.t, r: tv.r, b: tv.b },
        inset: dentro ? { l: dentro.l, t: dentro.t, r: dentro.r, b: dentro.b } : null });

      // Preto e branco do painel, na MESMA exposição travada: a razão diz se o preto está acinzentado.
      const razao = travou.exp ? (() => {
        const c = (f) => barras(f, tv, 1)[0];
        const w = c(branco); return w > 5 ? Math.min(1, c(pretoTravado) / w) : null;
      })() : null;

      passo(2, 'Medindo os pretos.');
      await soltarExposicao();
      await padrao('NEAR_BLACK', 1600);
      await julgar({ part: 'black', save: true, luma: barras(await foto(4), tv, 7), blackOverWhite: razao });

      passo(3, 'Medindo os brancos.');
      await padrao('NEAR_WHITE', 1600);
      await julgar({ part: 'white', save: true, luma: barras(await foto(4), tv, 7) });

      passo(4, 'Medindo a cor do branco.');
      await padrao('GRAY', 1200);
      const cor = corMedia(await foto(4), tv);
      await julgar({ part: 'color', save: true, r: cor.r, g: cor.g, b: cor.b, wbLocked: travou.wb });
      ultimaCaixa = tv;
      aviso('Medição pronta.', 'ok');
    } catch (e) {
      if (e.message !== 'cancelada') mostrar($('statusCelularImagem'), e.message);
    } finally {
      await chamar('POST', '/api/companion/stop').catch(() => {});
      visor.hidden = true;
      document.body.classList.remove('com-visor');
      fecharCamera();
      carregar();
    }
  }

  // ---------------------------------------------------------------- cor ao vivo (a pessoa mexe no menu, o celular confere)

  let ultimaCaixa = null, aoVivo = false;

  async function corAoVivo() {
    cancelada = false; aoVivo = true;
    const visor = $('visorCamera');
    try {
      await abrirCamera();
      $('visorTerminar').hidden = false;
      visor.hidden = false;
      document.body.classList.add('com-visor');
      $('visorPassos').style.setProperty('--feito', '1');
      $('visorPasso').textContent = 'Cor ao vivo';
      await padrao('BLACK', 1500);
      const preto = await foto();
      await padrao('WHITE', 1500);
      await travar();
      await espera(500);
      const tv = caixa(preto, await foto()) || ultimaCaixa;
      if (!tv) throw new Error(INCERTO.no_frame);
      await padrao('GRAY', 1000);
      let r = null, cor = null;
      while (aoVivo && !cancelada) {
        cor = corMedia(await foto(3), tv);
        r = await julgar({ part: 'color', save: false, r: cor.r, g: cor.g, b: cor.b, wbLocked: travou.wb });
        $('visorTexto').textContent = r.verdict === 'GOOD' ? 'Pronto: o branco está neutro. Toque em Terminar.' : textoDo(r);
        await espera(900);
      }
      // Terminar grava a última leitura (a Sofy fica sabendo como a cor ficou); Cancelar não grava nada.
      if (cor && !cancelada) await julgar({ part: 'color', save: true, r: cor.r, g: cor.g, b: cor.b, wbLocked: travou.wb }).catch(() => {});
    } catch (e) {
      if (e.message !== 'cancelada') mostrar($('statusCelularImagem'), e.message);
    } finally {
      aoVivo = false;
      await chamar('POST', '/api/companion/stop').catch(() => {});
      visor.hidden = true;
      document.body.classList.remove('com-visor');
      fecharCamera();
      carregar();
    }
  }

  // ---------------------------------------------------------------- botões

  $('btnCalibrarImagem')?.addEventListener('click', () => { mostrar($('statusCelularImagem'), ''); medir(); });
  $('btnCorAoVivo')?.addEventListener('click', () => { mostrar($('statusCelularImagem'), ''); corAoVivo(); });
  $('visorCancelar')?.addEventListener('click', () => { cancelada = true; aoVivo = false; });
  $('visorTerminar')?.addEventListener('click', () => { aoVivo = false; });

  document.addEventListener('aba', (ev) => { if (ev.detail === 'celular') carregar(); });
  window.carregarCelular = carregar;
})();

// ---------------------------------------------------------------- acertar o som (AudioSyncMath.kt)
// A TV toca 6 bipes e anota a hora em que cada um entrou no som dela; este celular, com o relógio acertado com a TV,
// ouve os bipes pelo microfone. A diferença é o atraso do som desta TV nesta saída; o "Som no fone" passa a usá-lo.
(function () {
  const espera = (ms) => new Promise((ok) => setTimeout(ok, ms));
  let medindo = false;

  // Pings pelo WebSocket (PanelStream, k=clock): a conexão já está aberta, então ida e volta têm o mesmo caminho. Pelo
  // HTTP cada pedido abre TLS de novo e a ida fica mais longa que a volta (o acerto sairia torto). Sem WebSocket, HTTP.
  async function acertarRelogio() {
    try { return await relogioWs(); } catch (e) { /* cai para o HTTP */ }
    const pings = [];
    for (let i = 0; i < 12; i++) {
      const s = performance.now();
      const r = await chamar('GET', '/api/companion/clock');
      const g = performance.now();
      pings.push({ s, t: r.t, g });
      await espera(40);
    }
    return pings;
  }

  function relogioWs() {
    return new Promise((ok, erro) => {
      const ws = new WebSocket(`wss://${location.host}/api/companion/stream?k=clock`);
      const pings = [];
      let s = 0, prazo = 0;
      const fim = (e) => { clearTimeout(prazo); try { ws.close(); } catch (x) {} e ? erro(e) : ok(pings); };
      const mandar = () => { s = performance.now(); ws.send('p'); clearTimeout(prazo); prazo = setTimeout(() => fim(new Error('sem resposta')), 2000); };
      ws.onopen = mandar;
      ws.onerror = () => fim(new Error('ws'));
      ws.onclose = () => { if (pings.length < 12) fim(new Error('fechou')); };
      ws.onmessage = (ev) => {
        const g = performance.now();
        const t = parseFloat(ev.data);
        if (!isFinite(t)) return fim(new Error('resposta'));
        pings.push({ s, t, g });
        if (pings.length >= 12) fim(); else setTimeout(mandar, 40);
      };
    });
  }

  function rotuloSaida(rota) {
    if (!rota) return '';
    if (rota === 'hdmi') return 'TV (HDMI)';
    if (rota === 'speaker') return 'TV';
    if (rota.startsWith('bt')) return 'Bluetooth' + (rota.length > 3 ? ' · ' + rota.slice(3) : '');
    if (rota.startsWith('usb')) return 'USB' + (rota.length > 4 ? ' · ' + rota.slice(4) : '');
    return rota;
  }

  async function mostrarEstado() {
    const el = $('celularSomResultado');
    if (!el) return;
    try {
      const r = await chamar('GET', '/api/companion');
      const m = r.audio && r.audio[r.route];
      el.textContent = m && m.verdict === 'GOOD'
        ? `Saída de agora: ${rotuloSaida(r.route)}. Atraso medido: ${m.delay} ms. O Som no fone já usa este número.`
        : `Saída de agora: ${rotuloSaida(r.route)}. Ainda não medido: o Som no fone usa ${r.delayInUse} ms, um valor padrão.`;
      el.hidden = false;
    } catch (e) { /* a parte da imagem já mostra o erro */ }
  }

  async function medirSom() {
    if (medindo) return;
    medindo = true;
    const status = $('statusCelularSom'), botao = $('btnMedirSom');
    mostrar(status, '');
    botao.disabled = true;
    let fluxo = null, ac = null;
    try {
      if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia)
        throw new Error('Para usar o microfone, abra o painel pelo endereço https (porta 8444). No http o navegador não libera o microfone.');
      try {
        fluxo = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
      } catch (e) { throw new Error('Não consegui abrir o microfone. Confira se o navegador tem permissão para usá-lo.'); }
      ac = new (window.AudioContext || window.webkitAudioContext)({ latencyHint: 'interactive' });
      await ac.audioWorklet.addModule('companion-worklet.js');
      const no = new AudioWorkletNode(ac, 'ouvido-bipes');
      const blocos = [];   // [quadro, energia, ...]
      no.port.onmessage = (ev) => { for (const v of ev.data) blocos.push(v); };
      ac.createMediaStreamSource(fluxo).connect(no);
      await ac.resume();

      mostrar(status, 'Acertando o relógio com a TV.');
      const pings = await acertarRelogio();
      mostrar(status, 'Ouvindo os bipes da TV. Fique em silêncio por 6 segundos, com o celular perto de onde você assiste.');
      const r0 = await chamar('POST', '/api/companion/audio/start');
      await espera(1000 + 250 + r0.beeps * r0.gapMs + 800);

      // Quadro do contexto -> relógio deste celular. getOutputTimestamp liga o tempo do contexto ao performance.now();
      // a captura aconteceu antes disso: tira a demora de saída e a do microfone (o navegador informa; sem informação, 10 ms).
      const ts = ac.getOutputTimestamp ? ac.getOutputTimestamp() : null;
      const latIn = (fluxo.getAudioTracks()[0].getSettings().latency || 0.01) * 1000;
      const latOut = ((ac.outputLatency || 0) + (ac.baseLatency || 0)) * 1000;
      const paraRelogio = (quadro) => {
        const ctxT = quadro / ac.sampleRate;
        // Com getOutputTimestamp a base é a hora em que o som SAI (desconta a demora de saída); sem ele, já é a de processamento.
        if (ts && ts.performanceTime) return ts.performanceTime + (ctxT - ts.contextTime) * 1000 - latOut - latIn;
        return performance.now() - (ac.currentTime - ctxT) * 1000 - latIn;
      };

      // Bipe = energia passa de 8x o ruído da sala (mediana do primeiro meio segundo), depois de 400 ms de calma.
      const energias = [];
      for (let i = 1; i < blocos.length; i += 2) energias.push(blocos[i]);
      const inicio = energias.slice(0, Math.max(10, Math.floor(ac.sampleRate * 0.5 / 128))).slice().sort((a, b) => a - b);
      const ruido = inicio[Math.floor(inicio.length / 2)] || 1e-9;
      const limite = Math.max(ruido * 8, 1e-6);
      const ouvidos = [];
      let calmaAte = -Infinity;
      for (let i = 0; i < blocos.length; i += 2) {
        const t = paraRelogio(blocos[i]);
        if (blocos[i + 1] > limite && t > calmaAte) { ouvidos.push(t); calmaAte = t + 400; }
      }

      const r = await chamar('POST', '/api/companion/audio/result', { pings, heard: ouvidos.slice(0, 40) });
      if (r.verdict === 'GOOD') {
        mostrar(status, `Pronto: o som desta TV atrasa ${r.delay} ms na saída ${rotuloSaida(r.route)}. O Som no fone já usa este número.`);
      } else {
        mostrar(status, r.heard < 3
          ? 'Não ouvi os bipes direito. Suba um pouco o volume da TV, chegue mais perto e tente de novo.'
          : 'A medida variou demais (Wi-Fi lento ou barulho na sala). Tente de novo em silêncio.');
      }
    } catch (e) {
      mostrar(status, e.message);
    } finally {
      if (fluxo) fluxo.getTracks().forEach((t) => t.stop());
      if (ac) ac.close().catch(() => {});
      botao.disabled = false;
      medindo = false;
      mostrarEstado();
    }
  }

  $('btnMedirSom')?.addEventListener('click', medirSom);
  document.addEventListener('aba', (ev) => { if (ev.detail === 'celular') mostrarEstado(); });
})();

// ---------------------------------------------------------------- achar o controle (FindRemote.kt)
(function () {
  function haQuanto(ms) {
    const s = Math.round(ms / 1000);
    // Frase inteira em cada caso: o i18n troca a frase toda (P em i18n-*.js), não pedaços.
    if (s < 60) return 'Usado agora há pouco.';
    const m = Math.round(s / 60);
    if (m < 60) return `Usado há ${m} min.`;
    const h = Math.round(m / 60);
    return h < 24 ? `Usado há ${h} h.` : 'Usado há mais de um dia.';
  }

  function desenhar(r) {
    const lista = $('listaControles');
    lista.textContent = '';
    const controles = r.remotes || [];
    if (!controles.length) {
      const li = document.createElement('li'); li.className = 'medida nao';
      const t = document.createElement('strong'); t.textContent = 'Nenhum';
      const s = document.createElement('span'); s.textContent = 'Nenhum controle ligado à TV agora.';
      li.append(t, s); lista.appendChild(li);
    }
    for (const c of controles) {
      const li = document.createElement('li');
      li.className = 'medida ' + (c.canVibrate ? 'boa' : 'nao');
      const t = document.createElement('strong'); t.textContent = c.gamepad ? 'Controle de jogo' : 'Controle';
      // Nome do aparelho, quando foi usado e se treme em pedaços separados: cada um se traduz sozinho.
      const s = document.createElement('span');
      const nome = document.createElement('span'); nome.textContent = c.name; nome.translate = false;
      const uso = document.createElement('span'); uso.textContent = c.lastUse ? haQuanto(r.now - c.lastUse) : 'Sem uso desde que a TV ligou.';
      s.append(nome, document.createTextNode(' · '), uso);
      if (c.canVibrate) { const tr = document.createElement('span'); tr.textContent = 'Treme.'; s.append(document.createTextNode(' '), tr); }
      li.append(t, s); lista.appendChild(li);
    }
    $('btnTremerControle').hidden = !controles.some((c) => c.canVibrate);
  }

  async function carregar() {
    if (!$('listaControles')) return;
    try { desenhar(await chamar('GET', '/api/companion/remotes')); } catch (e) { mostrar($('statusCelularControle'), e.message); }
  }

  $('btnTremerControle')?.addEventListener('click', async () => {
    try {
      const r = await chamar('POST', '/api/companion/remotes/ring');
      desenhar(r);
      mostrar($('statusCelularControle'), r.vibrated && r.vibrated.length
        ? 'Tremendo por 8 segundos: procure pelo barulho.'
        : 'Nenhum controle conseguiu tremer agora.');
    } catch (e) { mostrar($('statusCelularControle'), e.message); }
  });
  document.addEventListener('aba', (ev) => { if (ev.detail === 'celular') carregar(); });
})();

// ---------------------------------------------------------------- microfone e câmera na TV (PanelStream.kt)
// Uma conexão WebSocket na mesma porta https do painel, aberta depois do login. A TV não grava nada.
(function () {
  const abrirWs = (k) => new Promise((ok, erro) => {
    const ws = new WebSocket(`wss://${location.host}/api/companion/stream?k=${k}`);
    ws.binaryType = 'arraybuffer';
    ws.onopen = () => ok(ws);
    ws.onerror = () => erro(new Error('A TV não aceitou a conexão. Entre de novo no painel e tente outra vez.'));
  });
  const precisaHttps = () => {
    if (!window.isSecureContext || !navigator.mediaDevices) throw new Error('Abra o painel pelo endereço https (porta 8444): no http o navegador não libera microfone nem câmera.');
  };

  // ----- microfone
  let mic = null;   // { ws, ac, fluxo, ganho }
  // [mic] nasce já com o fluxo do microfone e ganha AudioContext e WebSocket aos poucos: se algo falha no meio, pararMic()
  // fecha o que chegou a abrir (o microfone do celular não fica aceso à toa). [ligandoMic] evita dois toques seguidos.
  let ligandoMic = false;
  async function ligarMic() {
    const status = $('statusCelularMic'), botao = $('btnMicTv');
    if (mic) { pararMic(); return; }
    if (ligandoMic) return;
    ligandoMic = true;
    try {
      precisaHttps();
      const fluxo = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
        .catch(() => { throw new Error('Não consegui abrir o microfone. Confira se o navegador tem permissão para usá-lo.'); });
      mic = { fluxo };
      // Taxa do próprio aparelho (o Firefox recusa misturar taxas); o worklet converte para os 16 kHz que a TV espera.
      const ac = new AudioContext({ latencyHint: 'interactive' });
      mic.ac = ac;
      await ac.audioWorklet.addModule('companion-worklet.js');
      const ws = await abrirWs('mic');
      mic.ws = ws;
      ws.onclose = () => { if (mic && mic.ws === ws) { pararMic(); mostrar(status, 'A TV encerrou o microfone.'); } };
      const ganho = ac.createGain(); ganho.gain.value = parseFloat($('micGanho').value) || 1;
      mic.ganho = ganho;
      const no = new AudioWorkletNode(ac, 'mic-pcm');
      no.port.onmessage = (ev) => { if (ws.readyState === 1 && ws.bufferedAmount < 16000) ws.send(ev.data); };
      ac.createMediaStreamSource(fluxo).connect(ganho).connect(no);
      await ac.resume();
      if (!mic || mic.ws !== ws || ws.readyState !== 1) return;   // a TV fechou enquanto ligava: onclose já avisou
      botao.textContent = 'Desligar';
      mostrar(status, 'Microfone ligado: sua voz está saindo na TV.');
    } catch (e) { pararMic(); mostrar(status, e.message); }
    finally { ligandoMic = false; }
  }
  function pararMic() {
    const m = mic;
    mic = null;
    if (m) {
      if (m.ws) try { m.ws.close(); } catch (e) {}
      m.fluxo.getTracks().forEach((t) => t.stop());
      if (m.ac) m.ac.close().catch(() => {});
    }
    $('btnMicTv').textContent = 'Falar na TV';
  }
  $('micGanho')?.addEventListener('input', () => { if (mic) mic.ganho.gain.value = parseFloat($('micGanho').value) || 1; });
  $('btnMicTv')?.addEventListener('click', ligarMic);

  // ----- câmera
  let cam = null;   // { ws, fluxo, relogio }
  let frente = false;
  let ligandoCam = false;
  async function ligarCam() {
    const status = $('statusCelularCam');
    if (cam) { pararCam(); return; }
    if (ligandoCam) return;
    ligandoCam = true;
    try {
      precisaHttps();
      const fluxo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: frente ? 'user' : 'environment', width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false })
        .catch(() => { throw new Error('Não consegui abrir a câmera. Confira se o navegador tem permissão para usá-la.'); });
      cam = { fluxo, relogio: 0 };   // já guarda o fluxo: se o resto falhar, pararCam() apaga a câmera
      const video = $('camPrevia');
      video.srcObject = fluxo; video.hidden = false;
      await video.play().catch(() => {});
      const ws = await abrirWs('cam');
      cam.ws = ws;
      ws.onclose = () => { if (cam && cam.ws === ws) { pararCam(); mostrar(status, 'A TV fechou a câmera.'); } };
      const tela = document.createElement('canvas');
      // ~8 fotos por segundo, 960 px de largura, JPEG 0,6: cabe no Wi-Fi da casa e na CPU da TV. Fila cheia = pula a foto.
      const enviar = () => {
        if (!cam || cam.ws !== ws) return;
        if (ws.readyState === 1 && ws.bufferedAmount < 300000 && video.videoWidth) {
          const w = Math.min(960, video.videoWidth), h = Math.round(w * video.videoHeight / video.videoWidth);
          tela.width = w; tela.height = h;
          tela.getContext('2d').drawImage(video, 0, 0, w, h);
          tela.toBlob((b) => { if (b && cam && cam.ws === ws && ws.readyState === 1) b.arrayBuffer().then((a) => ws.send(a)); }, 'image/jpeg', 0.6);
        }
        cam.relogio = setTimeout(enviar, 125);
      };
      enviar();
      $('btnCamTv').textContent = 'Desligar';
      $('btnCamVirar').hidden = false;
      mostrar(status, 'Ao vivo na TV.');
    } catch (e) { pararCam(); mostrar(status, e.message); }
    finally { ligandoCam = false; }
  }
  function pararCam() {
    const c = cam;
    cam = null;
    if (c) {
      clearTimeout(c.relogio);
      if (c.ws) try { c.ws.close(); } catch (e) {}
      c.fluxo.getTracks().forEach((t) => t.stop());
    }
    const video = $('camPrevia'); video.srcObject = null; video.hidden = true;
    $('btnCamTv').textContent = 'Mostrar na TV';
    $('btnCamVirar').hidden = true;
  }
  $('btnCamTv')?.addEventListener('click', ligarCam);
  $('btnCamVirar')?.addEventListener('click', async () => { frente = !frente; if (cam) { pararCam(); await ligarCam(); } });

  // Saiu da página ou escondeu o painel: desliga tudo (microfone aberto esquecido é a pior surpresa).
  document.addEventListener('aba', (ev) => { if (ev.detail !== 'celular') { pararMic(); pararCam(); } });
  document.addEventListener('visibilitychange', () => { if (document.hidden) { pararMic(); pararCam(); } });
})();
