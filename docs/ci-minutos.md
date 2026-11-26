# Minutos do GitHub Actions (elevação 21.1, 17/11/2026)

O que mudou: o workflow "Build e Release" (`release.yml`) roda a suíte JVM (`:app:testReleaseUnitTest`) em **todo PR**,
antes de compilar o APK. Antes os testes só rodavam no Release manual. Junto, um push novo no mesmo PR cancela a
execução anterior.

## Números medidos (17/11/2026)

| O quê | Valor | De onde |
|---|---|---|
| Passo "Testes JVM" | 1 min 48 s | execução 43947373548 (Release de 16/11), passo 9 |
| PR só compilando | 5 a 12 min, típico 6 min | 40 últimas execuções de PR do `release.yml` |
| Execuções de PR | 286 entre 13/11 e 17/11 (~60 por dia, ritmo de desenvolvimento intenso) | API do Actions |

Estimativa por mês, no ritmo de hoje (~1.800 PRs/push por mês): os testes somam **~3.300 min/mês**; o build de PR
já gasta **~11.000 min/mês**. Num ritmo calmo (10 execuções por dia): ~550 min/mês a mais.
O cancelamento do push anterior devolve parte disso (não medido ainda: medir no fim do primeiro mês).

## Quanto custa

- **Repositório público (como está hoje): R$ 0.** Runner padrão em repositório público é grátis
  (docs.github.com/en/billing/reference/actions-runner-pricing).
- **Repositório privado (decisão de 15/11/2026, código fechado):** o plano Free inclui 2.000 min/mês e o Pro 3.000;
  passou disso, US$ 0,006 por minuto de Linux 2-core (mesma página, lida em 17/11/2026). No ritmo de hoje o build de
  PR sozinho já passa da franquia: ~11.000 + 3.300 = ~14.300 min/mês = ~US$ 74/mês no Free (12.300 × 0,006) ou
  ~US$ 68/mês no Pro (11.300 × 0,006). Só a parte dos testes: ~US$ 20/mês. No ritmo calmo cabe na franquia.
- Para baixar: juntar commits antes de abrir/atualizar PR, `[skip ci]` em commit só de texto, e PR em rascunho
  acumulando trabalho (como já fazemos).

## Rascunho não roda (26/11/2026, repo privado)

**Só vale com o repo privado.** Enquanto for público, o Actions é grátis e o PR em rascunho roda tudo (decisão do Paulo,
25/11/2026 23:29 BRT): o `if` olha `github.event.repository.private` e a trava liga sozinha quando o repo fechar.

O que mudou: "Build e Release" (`release.yml`) e "Telas" (`telas.yml`) **não rodam em PR em rascunho**. Rodam quando o
PR sai do rascunho (`ready_for_review`), a cada push depois disso, no push para a `main` (compila e testa; não publica)
e à mão. Rascunho que precisa do teste pesado: etiqueta **`run-ci`** no PR, ou "Run workflow" na branch. O cancelamento
da execução antiga passou para o nível do job, para que uma execução pulada nunca cancele uma de verdade. Mudança só em
`docs/`, `*.md` ou `website/` não roda o Build (menos README/CHANGELOG/release-notes e as fontes do `readme.py`).
O Release à mão (`workflow_dispatch` na main) não mudou e tem fila própria.

### Medido (API do Actions, 100 últimas execuções de cada)

| Workflow | Janela | Execuções | Minutos | Ritmo |
|---|---|---|---|---|
| Build e Release (PR) | 24/11 22:01Z → 26/11 00:59Z (27 h) | 99 | 797 | ~710 min/dia, ~8 min cada |
| Telas (PR) | 24/11 18:57Z → 26/11 (31 h) | 100 | 841 | ~650 min/dia, ~8,4 min cada |

Juntos: **~1.360 min/dia ≈ 40.000 min/mês** (bate com a estimativa de 35 a 50 mil). Na mesma janela entraram ~13 PRs
mesclados no Build (~11,5 por dia), cada um com 2 a 4 execuções enquanto era rascunho.

### Estimativa depois (não medida: conferir em 7 dias)

- Build: por PR mesclado, ~1 execução ao sair do rascunho + 1 no push da main ≈ 2 × 8 min = 16 min × ~11,5 PRs/dia
  ≈ 185 min/dia ≈ **5.500 min/mês** (mais os pushes feitos depois de sair do rascunho, não medidos).
- Telas: só nos PRs que mexem em tela (cerca de metade): ~6 por dia × 2 × 8,4 min ≈ 100 min/dia ≈ **3.000 min/mês**.
- Total ≈ **8.500 a 10.000 min/mês** (contra ~40.000): ~75% a menos. Acima da franquia do Pro (3.000):
  ~5.500 a 7.000 × US$ 0,006 ≈ **US$ 33 a 42/mês** (antes ~US$ 220).
- O push na `main` custa ~3.500 a 4.500 min/mês dessas contas (~US$ 20 a 27). Vale porque várias threads marcam o PR
  como pronto e mesclam no mesmo minuto: sem ele, o merge entraria sem nenhum teste.
- Etiqueta `run-ci` em rascunho volta a custar ~8 min por push: tirar quando não precisar mais.
