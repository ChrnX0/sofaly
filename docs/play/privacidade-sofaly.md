# Política de privacidade — Sofaly (rascunho, 16/11/2026)

> Base: `site/privacy.html` (One TV Launcher, 14/11/2026), ajustada ao app da Play. Publicar no site (inglês e português)
> ANTES de enviar à Play e colar o link no Console. Partes marcadas ⚠ dependem de decisão.

**Em resumo.** A Sofaly não pede conta, não mostra anúncios e não rastreia o que você assiste. Não há análise de uso nem
telemetria, e não vendemos dados. Temos um servidor pequeno (api.sofaly.tv) que só repassa alguns pedidos (abaixo). O que o app guarda fica na sua TV ou em contas suas que você escolher conectar.

**O nosso servidor (api.sofaly.tv).** Serve para usar fornecedores cuja chave não pode ficar dentro do app. Recebe o
pedido e o IP da TV, sem conta nem nome, e não guarda quem pediu: busca de podcasts (o termo buscado, repassado ao Podcast
Index), texto e resumo de podcasts públicos (o áudio do podcast, para a Cloudflare Workers AI, que não guarda o pedido) e,
no plano Pro, a previsão do tempo (a localização arredondada a ~11 km, repassada à WeatherAPI.com). Também publica a lista
de add-ons recomendados e o add-on de filmes de domínio público.

**Na TV.** Configurações, ordem dos apps, despertadores, lembretes, rotinas, PINs (hash com sal), contas de mídia que você
adiciona e backups locais. Apagar os dados do app ou desinstalar remove tudo.

**Serviços que o app contata** (recebem o pedido da TV, inclusive o IP, sob a política deles):
- Clima: ⚠ MET Norway ou Open-Meteo (fonte em decisão), com a **localização aproximada** (cerca de 1 km), e o
  Aviation Weather Center (NOAA) para a medida do aeroporto mais próximo.
- Cidade: a busca é feita **dentro da TV** (lista GeoNames no app). CEP: ViaCEP.
- Filmes e séries: Trakt (título e ano do que você abre). Legendas: OpenSubtitles, com a sua própria conta.
- Rádios: Radio Browser. Fundos de arte: The Metropolitan Museum of Art.
- Compras: Google Play (a Sofaly só lê qual plano está ativo).
- Login Google e Google Drive: opcionais, com os mesmos escopos e regras de Uso Limitado do Sofaly.
- Serviços que você mesmo configura: Plex, Jellyfin, pastas da rede, Home Assistant, Tailscale.

**Cuidar de longe.** Desligado de fábrica. Só funciona depois que **quem mora na casa aceita na TV**, com o controle
remoto. Os avisos (pedido de ajuda, "estou bem") só saem quando a pessoa aperta o botão. O aviso vai direto ao celular
escolhido, pelo serviço de push do navegador desse celular. Retirar o aceite é a qualquer hora, na TV.

**Poderes avançados (opcionais).** Serviço de acessibilidade (botão HOME e teclas), shell pela depuração sem fio e
Device Owner servem para a TV funcionar como a pessoa pediu; nenhum deles envia dados para fora. Emprestar esses poderes
a outro app exige o PIN digitado na TV.

**Assistentes de IA (MCP) e painel.** O painel funciona na rede da casa, com PIN. O acesso de fora só existe se você
ligar o Tailscale com a sua conta; a conexão vai até a sua TV, não até nós.

**Crianças.** O app não coleta dados de ninguém de propósito, inclusive de crianças.

**Contato.** ⚠ e-mail de suporte do produto (precisa existir antes de publicar; a Play exige um e-mail).
