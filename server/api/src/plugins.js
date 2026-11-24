// Vitrine de plugins da Sofaly (24/11/2026). Rota pública GET /v1/plugins/recommended; a TV pergunta 1 vez por dia e,
// se a resposta estragar, fica com a lista embutida no app (PluginCatalog.kt). O servidor só SUGERE: pode reordenar,
// tirar um plugin da vitrine (um que quebrou) e acrescentar um que esteja na Play Store. Para uma entrada que o app já
// conhece (mesmo id), a TV usa os dados dela própria: daqui não se muda pacote nem repositório, e nada daqui faz a TV
// instalar APK. Entrada nova: precisa de `play` (pacote na Play), `name` sem nome de concorrente e `description` curta
// (até 120 letras) em inglês e, se houver, nas outras línguas (a TV usa a dela, senão a inglesa).
// Pedidos de desenvolvedores chegam pelo formulário "Submit a plugin" do repo público e passam pela revisão automática
// (publico/.github/scripts/plugin-review.mjs): com o rótulo plugin-ok, a linha pronta está no comentário do robô; é
// só colar aqui. plugin-doubt = uma pessoa olha antes.
// Para mudar: editar aqui, `node --test`, `npm run deploy` (ver server/README.md).
export const PLUGINS = Object.freeze([
  { id: 'aerialviews' },
  { id: 'overflight' },
  { id: 'dailyphotos' },
  { id: 'weather' },
  { id: 'movieart' },
  { id: 'sofadrop' },
  { id: 'immich' },
  { id: 'tvicons' },
  { id: 'minimalist' },
  { id: 'arcticons' },
]);

// Pacotes que a TV deve DESLIGAR (proposta C, 24/11/2026): um plugin que passou a travar, a mostrar propaganda por cima
// ou a fazer o que não disse. Só desliga, nunca liga; a pessoa vê "Desligado pela Sofaly" e não consegue ligar de novo
// enquanto o pacote estiver aqui. Tirar daqui libera (a pessoa liga de novo se quiser).
export const BLOCKED = Object.freeze([]);

/** Corpo da resposta. `version` muda quando a lista muda (a TV só guarda; serve para conferir de fora). */
export function pluginsBody() {
  return { version: 1, plugins: PLUGINS, blocked: BLOCKED };
}
