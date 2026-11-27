#!/usr/bin/env python3
"""Confere os APKs das partes (refatoração G, 27/11/2026), saídos de `./gradlew :app:buildPartApks -Psplits`.

Uso: check-parts.py <pasta das partes> <versionCode>

Para cada arquitetura: a base (base-master + base-<abi>) não leva o túnel nem o tradutor; cada parte leva a sua lib,
comprimida; todos os APKs são do pacote tv.sofaly.launcher com o mesmo versionCode. Imprime o tamanho de cada um.
Falha (código 1) com uma linha ::error:: por problema, no formato que o GitHub Actions mostra.
"""
import pathlib
import re
import subprocess
import sys
import zipfile

ABIS = {"armeabi-v7a": "armeabi_v7a", "arm64-v8a": "arm64_v8a"}
# Lib -> parte onde ela tem de estar (e nunca na base).
LIBS = {"libtsnet.so": "tunnel", "libtranslate_jni.so": "translate"}
PACOTE = "tv.sofaly.launcher"


def aapt2() -> str:
    import os
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        props = pathlib.Path("local.properties")
        if props.exists():
            m = re.search(r"^sdk\.dir=(.*)$", props.read_text(), re.M)
            sdk = m.group(1).strip() if m else None
    tools = sorted(pathlib.Path(sdk, "build-tools").glob("*/aapt2")) if sdk else []
    if not tools:
        sys.exit("::error::aapt2 não achado (ANDROID_HOME)")
    return str(tools[-1])


def badging(aapt: str, apk: pathlib.Path) -> tuple[str, str]:
    out = subprocess.run([aapt, "dump", "badging", str(apk)], capture_output=True, text=True).stdout
    pkg = re.search(r"package: name='([^']*)'", out)
    vc = re.search(r"versionCode='(\d+)'", out)
    return (pkg.group(1) if pkg else "", vc.group(1) if vc else "")


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    pasta, codigo = pathlib.Path(sys.argv[1]), sys.argv[2]
    aapt = aapt2()
    erros = []
    for abi, sufixo in ABIS.items():
        d = pasta / abi
        esperados = [f"base-master.apk", f"base-{sufixo}.apk", "translate-master.apk", f"translate-{sufixo}.apk",
                     "tunnel-master.apk", f"tunnel-{sufixo}.apk"]
        for nome in esperados:
            apk = d / nome
            if not apk.is_file():
                erros.append(f"{abi}: falta {nome}")
                continue
            pkg, vc = badging(aapt, apk)
            if pkg != PACOTE:
                erros.append(f"{abi}/{nome}: pacote {pkg!r}, esperado {PACOTE}")
            if vc != codigo:
                erros.append(f"{abi}/{nome}: versionCode {vc!r}, esperado {codigo}")
            with zipfile.ZipFile(apk) as z:
                for info in z.infolist():
                    base = info.filename.rsplit("/", 1)[-1]
                    if not info.filename.startswith("lib/"):
                        continue
                    if not info.filename.startswith(f"lib/{abi}/"):
                        erros.append(f"{abi}/{nome}: lib de outra arquitetura: {info.filename}")
                    dona = LIBS.get(base)
                    if dona and not nome.startswith(dona + "-"):
                        erros.append(f"{abi}/{nome}: {base} tinha de estar só na parte {dona}")
                    if dona and info.compress_type == zipfile.ZIP_STORED:
                        erros.append(f"{abi}/{nome}: {base} sem compressão (useLegacyPackaging da parte?)")
                nomes = {i.filename for i in z.infolist()}
            for lib, dona in LIBS.items():
                if nome == f"{dona}-{sufixo}.apk" and f"lib/{abi}/{lib}" not in nomes:
                    erros.append(f"{abi}/{nome}: falta {lib}")
            print(f"{abi}/{nome}: {apk.stat().st_size / 1048576:.2f} MB")
        base = sum((d / n).stat().st_size for n in ("base-master.apk", f"base-{sufixo}.apk") if (d / n).is_file())
        print(f"{abi}: base (o que toda TV baixa) = {base / 1048576:.2f} MB")
    for e in erros:
        print(f"::error::{e}")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
