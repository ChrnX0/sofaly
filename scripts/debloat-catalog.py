#!/usr/bin/env python3
"""Builds app/src/main/assets/debloat/catalog.json from a checkout of tellyscope-devices (MIT, see NOTICE there).

Why (22/11/2026, Tellyscope study, proposal A): the Sofaly is sold worldwide, but its cleanup only knew the Sala's
Chromecast. The community keeps hand-checked debloat lists for ~500 TV models (cleanyourtv -> tellyscope-devices).
Only the DATA is used (MIT); no Tellyscope code (GPL) comes in.

How: those lists can't recognise a TV (only 1 of 492 has a match rule), so instead of guessing the model this script
turns them into a catalog BY PACKAGE: how many lists turn it off, which brands, what kind of app it is and the note.
The TV then suggests only packages it has installed, always through Debloat.NUNCA and its own runtime guards.

Usage: python3 scripts/debloat-catalog.py <path to tellyscope-devices checkout>
"""
import json
import pathlib
import subprocess
import sys

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/debloat/catalog.json"

# Packages that keep a TV usable. Never in the catalog, whatever a list says (the app checks Debloat.NUNCA too).
NEVER = {
    "android", "com.android.systemui", "com.android.tv.settings", "com.android.vending", "com.google.android.gms",
    "com.google.android.gsf", "com.google.android.katniss", "com.google.android.apps.tv.launcherx",
    "com.google.android.apps.mediashell", "com.google.android.tts", "com.google.android.tungsten.setupwraith",
    "com.google.android.apps.tv.setupwraith", "com.android.providers.tv", "com.google.android.inputmethod.latin",
    "com.google.android.tvkeyboard", "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.google.android.webview",
    "com.android.webview", "com.android.shell", "com.android.phone", "com.android.bluetooth",
    "com.google.android.apps.tv.launcherx", "com.google.android.tv.bugreportsender",
}
# Words that mark a package as part of the system's plumbing (input, Wi-Fi, updates, sound, HDMI...). Also kept out.
NEVER_WORDS = ("launcher", "setup", "settings", "inputmethod", "keyboard", "webview", "wifi", "bluetooth", "update",
               "ota", "hdmi", "cec", "audio", "speaker", "systemui", "provider", "permission", "installer", "framework",
               "remote", "tvinput", "tv.input", "livetv", "mediashell", "cast", "voice", "katniss", "gms", "gsf",
               "dream", "backdrop")  # screensavers: the Sofaly already handles them (Config.APPS_OCULTAR)


def kind(title: str) -> str:
    t = title.lower()
    if any(w in t for w in ("telemetry", "feedback", "ads", "analytics", "tracking", "logging", "diagnostic")):
        return "telemetry"
    if any(w in t for w in ("screensaver", "ambient", "wallpaper")):
        return "screensaver"
    if any(w in t for w in ("preloaded", "bloat", "promo", "store", "partner", "game", "shopping", "bundled")):
        return "preloaded"
    return "other"


def main(src: pathlib.Path) -> None:
    index = json.loads((src / "index.json").read_text())
    protected = json.loads((src / "lists/protected.json").read_text())
    keep = set(NEVER)
    for g in protected.get("groups", []):
        keep |= set(g.get("packages", {}))
    warn = {p for g in protected.get("warn", []) for p in g.get("packages", {})}
    keep |= warn

    pkgs: dict = {}
    for dev in index["devices"]:
        d = json.loads((src / dev["file"]).read_text())
        brand = d["id"].split("-")[0]
        keep |= {k["pkg"] if isinstance(k, dict) else k for k in d.get("keep", [])}
        for g in d.get("groups", []):
            k = kind(g.get("title", ""))
            for p in g.get("packages", []):
                if p.get("optional"):
                    continue  # the lists themselves mark these as "only if you know"
                e = pkgs.setdefault(p["pkg"], {"lists": 0, "brands": set(), "kinds": {}, "note": ""})
                e["lists"] += 1
                e["brands"].add(brand)
                e["kinds"][k] = e["kinds"].get(k, 0) + 1
                if p.get("note") and len(p["note"]) > len(e["note"]):
                    e["note"] = p["note"][:140]

    out = {}
    for pkg, e in sorted(pkgs.items()):
        low = pkg.lower()
        if pkg in keep or any(w in low for w in NEVER_WORDS):
            continue
        out[pkg] = {
            "lists": e["lists"],
            "brands": sorted(e["brands"]),
            "kind": max(e["kinds"], key=e["kinds"].get),
            **({"note": e["note"]} if e["note"] else {}),
        }
    try:
        commit = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"], capture_output=True, text=True,
                                check=True).stdout.strip()
    except Exception:
        commit = "unknown"
    doc = {
        "schema": 1,
        "source": "https://github.com/man15h/tellyscope-devices",
        "upstream": "https://github.com/Gathin23/cleanyourtv",
        "license": "MIT",
        "commit": commit,
        "devices": len(index["devices"]),
        "packages": out,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n")
    print(f"{OUT}: {len(out)} packages from {len(index['devices'])} lists ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(pathlib.Path(sys.argv[1]))
