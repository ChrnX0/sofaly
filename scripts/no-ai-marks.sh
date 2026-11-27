#!/usr/bin/env bash
# Regra do Paulo: nenhuma marca de IA em commit, merge ou PR (#515; reforçada em 27/11/2026, quando 71 dos 106
# commits do dia entraram na main com "Co-authored-by: Claude" ou "Claude-Session").
# Uso: scripts/no-ai-marks.sh <intervalo git>   (ex.: origin/main..HEAD, ou HEAD --not <antes> origin/main). Sai com 1 e lista os commits com marca.
# Olha autor, committer e o texto do commit; nome de branch (claude/...) não conta.
set -euo pipefail
[ $# -ge 1 ] || { echo "uso: no-ai-marks.sh <intervalo git>"; exit 2; }
intervalo=("$@")   # aceita "a..b" ou "b --not a c"
ruins=0
for c in $(git rev-list "${intervalo[@]}"); do
  quem=$(git log -1 --format='%an <%ae> / %cn <%ce>' "$c")
  texto=$(git log -1 --format='%B' "$c")
  motivo=""
  if printf '%s' "$quem" | grep -qiE 'claude|anthropic'; then motivo="autor/committer: $quem"; fi
  linha=$(printf '%s\n' "$texto" | grep -iE '^[[:space:]]*(co-authored-by|signed-off-by):.*(claude|anthropic)|claude-session:|generated with.*claude|noreply@anthropic\.com' | head -1 || true)
  if [ -n "$linha" ]; then motivo="${motivo:+$motivo; }texto: $linha"; fi
  if [ -n "$motivo" ]; then
    echo "::error::$(git log -1 --format='%h %s' "$c" | cut -c1-80) -> $motivo"
    ruins=$((ruins + 1))
  fi
done
if [ "$ruins" -gt 0 ]; then
  echo "$ruins commit(s) com marca de IA. Faça commit com autor Paulo Eduardo e, no squash, passe título e mensagem explícitos."
  exit 1
fi
echo "Sem marca de IA em $(git rev-list --count "${intervalo[@]}") commit(s)."
