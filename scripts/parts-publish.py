#!/usr/bin/env python3
"""Prepara as partes para o Release (refatoração G, 27/11/2026).

Uso: parts-publish.py <pasta das partes> <versionCode> <pasta publicar>

Lê app/build/outputs/parts/<abi>/ (já conferida por check-parts.py) e escreve em <publicar>:
  part-<parte>.apk            o APK comum da parte ("master": igual byte a byte nas duas arquiteturas; confere)
  part-<parte>-<abi>.apk      o da arquitetura (armeabi_v7a, arm64_v8a)
  parts.json                  o índice que o Atualizador lê (PartsPlan.kt): nome, parte, arquitetura, SHA-256, tamanho
Os nomes começam por "part-": o Atualizador antigo procura nomes exatos (sofaly-launcher*.apk) e nunca pega um destes.
"""
import hashlib
import json
import pathlib
import shutil
import sys

ABIS = {"armeabi-v7a": "armeabi_v7a", "arm64-v8a": "arm64_v8a"}
PARTES = ["base", "translate", "tunnel"]


def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    pasta, codigo, destino = pathlib.Path(sys.argv[1]), int(sys.argv[2]), pathlib.Path(sys.argv[3])
    destino.mkdir(parents=True, exist_ok=True)
    arquivos = []
    for parte in PARTES:
        masters = [pasta / abi / f"{parte}-master.apk" for abi in ABIS]
        if len({sha(m) for m in masters}) != 1:
            print(f"::error::{parte}-master.apk difere entre as arquiteturas: não dá para publicar um só")
            return 1
        saidas = [(masters[0], f"part-{parte}.apk", "")]
        saidas += [(pasta / abi / f"{parte}-{suf}.apk", f"part-{parte}-{suf}.apk", abi) for abi, suf in ABIS.items()]
        for origem, nome, abi in saidas:
            shutil.copyfile(origem, destino / nome)
            arquivos.append({"name": nome, "split": parte, "abi": abi, "sha256": sha(origem), "size": origem.stat().st_size})
    (destino / "parts.json").write_text(json.dumps({"versionCode": codigo, "files": arquivos}, indent=1) + "\n")
    for a in arquivos:
        print(f"{a['name']}: {a['size'] / 1048576:.2f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
