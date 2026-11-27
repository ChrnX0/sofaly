#!/usr/bin/env python3
"""Confere os APKs das partes (refatoração G, 27/11/2026), saídos de `./gradlew :app:buildPartApks -Psplits`.

Uso: check-parts.py <pasta das partes> <versionCode>

Para cada arquitetura: a base (base-master + base-<abi>) não leva o túnel nem o tradutor; cada parte leva a sua lib,
comprimida; todos os APKs são do pacote tv.sofaly.launcher com o mesmo versionCode. Imprime o tamanho de cada um.
E toda classe que o manifesto da BASE manda o Android criar (application, activity, service, receiver, provider e os
inicializadores do androidx.startup) tem de estar definida no código da base: o Gradle junta o manifesto das partes ao
da base, e uma classe que só existe numa parte derruba o launcher a cada abertura numa TV sem essa parte.
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
# Componentes da base cuja classe pode ficar numa parte, com o motivo. Só entra o que o Android nunca cria sozinho.
FORA_DA_BASE = {
    # Só guarda meta-data (a lista de registrars que o ML Kit lê pelo PackageManager); não é exportado nem tem
    # intent-filter, então ninguém a inicia. As classes que ela cita são lidas só quando a tradução é usada.
    "com.google.mlkit.common.internal.MlKitComponentDiscoveryService",
}
COMPONENTES = {"application", "activity", "service", "receiver", "provider"}
STARTUP = "androidx.startup.InitializationProvider"


def uleb128(b: bytes, i: int) -> tuple[int, int]:
    valor, desloc = 0, 0
    while True:
        x = b[i]
        i += 1
        valor |= (x & 0x7F) << desloc
        if x < 0x80:
            return valor, i
        desloc += 7


def classes_definidas(apk: pathlib.Path) -> set[str]:
    """Nomes (com ponto) das classes DEFINIDAS nos classes*.dex do APK (class_defs, não só citadas)."""
    nomes = set()
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if not re.fullmatch(r"classes\d*\.dex", n):
                continue
            b = z.read(n)
            u32 = lambda o: int.from_bytes(b[o:o + 4], "little")
            str_off, tipo_off = u32(0x3C), u32(0x44)
            n_classes, classes_off = u32(0x60), u32(0x64)
            for k in range(n_classes):
                tipo = u32(classes_off + 32 * k)
                s_idx = u32(tipo_off + 4 * tipo)
                dado = u32(str_off + 4 * s_idx)
                _, i = uleb128(b, dado)
                fim = b.index(0, i)
                d = b[i:fim].decode("utf-8", "replace")
                if d.startswith("L") and d.endswith(";"):
                    nomes.add(d[1:-1].replace("/", "."))
    return nomes


def criadas_pela_base(aapt: str, apk: pathlib.Path) -> list[str]:
    """Classes que o manifesto manda o Android criar, lidas da árvore do `aapt2 dump xmltree`."""
    out = subprocess.run([aapt, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
                         capture_output=True, text=True).stdout
    pilha = []   # (recuo, tag, atributos)
    nos = []
    for linha in out.splitlines():
        recuo = len(linha) - len(linha.lstrip())
        texto = linha.strip()
        if texto.startswith("E: "):
            while pilha and pilha[-1][0] >= recuo:
                pilha.pop()
            no = (recuo, texto[3:].split(" ")[0], {}, pilha[-1] if pilha else None)
            pilha.append(no)
            nos.append(no)
        elif texto.startswith("A: ") and pilha:
            m = re.match(r'A: (?:http://schemas.android.com/apk/res/android:)?(\w+)(?:\(0x[0-9a-f]+\))?="([^"]*)"', texto)
            if m:
                pilha[-1][2][m.group(1)] = m.group(2)
    classes = []
    for _, tag, at, pai in nos:
        nome = at.get("name", "")
        if tag in COMPONENTES and nome and "splitName" not in at:
            classes.append(nome)
            if tag == "application" and at.get("appComponentFactory"):
                classes.append(at["appComponentFactory"])
        if tag == "meta-data" and at.get("value") == "androidx.startup" and pai and pai[2].get("name") == STARTUP:
            classes.append(nome)
    return classes


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
            # Provider numa parte: o Android já cria o da base; um igual na parte só duplica a autoridade.
            if not nome.startswith("base-"):
                for c in (x for x in criadas_pela_base(aapt, apk) if x not in FORA_DA_BASE):
                    erros.append(f"{abi}/{nome}: a parte declara o componente {c} (fica na base ou sai)")
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
        master = d / "base-master.apk"
        if master.is_file():
            definidas = classes_definidas(master)
            criadas = criadas_pela_base(aapt, master)
            if len(criadas) < 20 or len(definidas) < 1000:
                erros.append(f"{abi}/base-master.apk: leitura suspeita ({len(criadas)} componentes, {len(definidas)} classes)")
            for c in criadas:
                if c not in definidas and c not in FORA_DA_BASE:
                    erros.append(f"{abi}/base-master.apk: o manifesto cria {c}, que não está no código da base")
            # O perfil de desempenho da HOME (Android 14 força speed-profile) depende deste inicializador na base.
            if "androidx.profileinstaller.ProfileInstallerInitializer" not in criadas:
                erros.append(f"{abi}/base-master.apk: sem o ProfileInstallerInitializer do androidx.startup")
            print(f"{abi}: {len(criadas)} classes criadas pelo manifesto da base, todas conferidas no código dela")
        base = sum((d / n).stat().st_size for n in ("base-master.apk", f"base-{sufixo}.apk") if (d / n).is_file())
        print(f"{abi}: base (o que toda TV baixa) = {base / 1048576:.2f} MB")
    for e in erros:
        print(f"::error::{e}")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
