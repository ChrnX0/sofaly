// Ouvido dos bipes (companion.js, 24/11/2026): roda na thread de áudio do navegador. Para cada bloco de 128 amostras
// do microfone manda a energia do som "agudo" (diferença entre amostras vizinhas, que realça o bipe de 2 kHz e apaga o
// ronco da sala) e o número do primeiro quadro do bloco. Junta 16 blocos por mensagem (~43 ms) para não afogar a página.
class OuvidoBipes extends AudioWorkletProcessor {
  constructor() { super(); this.lote = []; this.ant = 0; }
  process(entradas) {
    const c = entradas[0] && entradas[0][0];
    if (c) {
      let e = 0, a = this.ant;
      for (let i = 0; i < c.length; i++) { const d = c[i] - a; e += d * d; a = c[i]; }
      this.ant = a;
      this.lote.push(currentFrame, e / c.length);
      if (this.lote.length >= 32) { this.port.postMessage(this.lote); this.lote = []; }
    }
    return true;
  }
}
registerProcessor('ouvido-bipes', OuvidoBipes);

// Microfone do celular para a TV (PhoneMicTv.kt): converte a taxa do aparelho ([sampleRate], 44,1 ou 48 kHz) para 16 kHz
// fazendo a média de cada janela (filtro simples contra chiado de dobra), junta 640 amostras (40 ms) e manda em PCM 16 bits.
class MicPcm extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buf = new Int16Array(640); this.n = 0;
    this.passo = sampleRate / 16000; this.pos = 0; this.soma = 0; this.qtd = 0;
  }
  process(entradas) {
    const c = entradas[0] && entradas[0][0];
    if (c) for (let i = 0; i < c.length; i++) {
      this.soma += c[i]; this.qtd++; this.pos += 1;
      if (this.pos < this.passo) continue;
      this.pos -= this.passo;
      const v = Math.max(-1, Math.min(1, this.soma / this.qtd));
      this.soma = 0; this.qtd = 0;
      this.buf[this.n++] = v < 0 ? v * 0x8000 : v * 0x7fff;
      if (this.n === this.buf.length) { this.port.postMessage(this.buf.buffer.slice(0)); this.n = 0; }
    }
    return true;
  }
}
registerProcessor('mic-pcm', MicPcm);
