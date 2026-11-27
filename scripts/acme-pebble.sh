#!/usr/bin/env bash
# Teste de ponta a ponta do cliente ACME da TV (item 26, opção A) contra o Pebble, o servidor ACME de teste do
# Let's Encrypt, com o desafio DNS-01 de verdade (pebble-challtestsrv faz o papel das máquinas de DNS). 27/11/2026.
# Precisa de Go. Uso (da raiz): bash scripts/acme-pebble.sh   -> compila o Pebble, sobe os dois, roda AcmeClientTest.
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${CACHE:-$HOME/.cache/tv-launcher-testes}"
PEB="$CACHE/pebble"; VER=v2.6.0
mkdir -p "$PEB"
export GOPATH="$PEB/gopath" GOBIN="$PEB/bin"
[ -x "$PEB/bin/pebble" ] || go install "github.com/letsencrypt/pebble/v2/cmd/pebble@$VER"
[ -x "$PEB/bin/pebble-challtestsrv" ] || go install "github.com/letsencrypt/pebble/v2/cmd/pebble-challtestsrv@$VER"
SRC="$GOPATH/pkg/mod/github.com/letsencrypt/pebble/v2@$VER"
cp "$SRC/test/certs/pebble.minica.pem" "$PEB/"
"$PEB/bin/pebble-challtestsrv" -dns01 127.0.0.1:8053 -management 127.0.0.1:8055 -http01 "" -https01 "" -tlsalpn01 "" -doh "" >"$PEB/chall.log" 2>&1 &
C1=$!
(cd "$SRC" && PEBBLE_VA_NOSLEEP=1 exec "$PEB/bin/pebble" -config test/config/pebble-config.json -dnsserver 127.0.0.1:8053) >"$PEB/pebble.log" 2>&1 &
C2=$!
trap 'kill $C1 $C2 2>/dev/null || true' EXIT
for _ in $(seq 1 50); do curl -sk https://127.0.0.1:14000/dir >/dev/null && break; sleep 0.2; done
ONETV_PEBBLE="$PEB" bash "$RAIZ/scripts/testes-puros.sh" AcmeClientTest
grep -q "Issued certificate" "$PEB/pebble.log" && echo "Pebble emitiu o certificado." || { echo "Pebble NÃO emitiu (o teste pulou?)"; exit 1; }
