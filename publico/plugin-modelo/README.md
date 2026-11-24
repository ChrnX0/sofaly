# Sofaly plugin template

Two small Android TV apps that put a card on the Sofaly home screen. Copy this folder, rename the package and make it
yours. License: MIT No Attribution (use it any way you like, no credit needed).

| Module | What the card shows | Internet |
|---|---|---|
| `contagem` (the template) | Days left until a date the person picks on the TV ("Christmas: 38") | no |
| `lua` | Today's moon phase and days to the next full moon | no |

## Build

```
./gradlew assembleDebug testDebugUnitTest
adb install contagem/build/outputs/apk/debug/contagem-debug.apk
```

Then on the TV: **Settings › Appearance › Plugins**, turn the plugin on (it asks for the parental PIN) and go back to
the home screen. The card appears at the end of the **Watch** row.

## The contract (all of it)

1. A provider, `exported="true"`, with `<action android:name="tv.sofaly.plugin.CARTAO"/>` in its intent filter
   (see `contagem/src/main/AndroidManifest.xml`).
2. `query()` on `content://<authority>/cartao` returns one row with `titulo` (30 chars), `valor` (12),
   `detalhe` (80), `cor` (`#RRGGBB`) and optionally `acao` (an `intent:` URI that opens your own app only).
3. Be fast and local: the launcher reads the card every time the person returns home, with a 2-second limit, away from
   the screen thread. Fetch from the internet in the background and keep the result (WorkManager, a cache file).

Optional (v2): `world` (`watch`, `listen`, `play` or `apps`: the row the card joins; default `watch`) and `image`
(a `content://` URI from a provider in your own package; shown up to 256 px).

The launcher draws the card in its own style, with large letters. It never sends anything to your app.

## Other kinds of plugin

- **Quick panel button:** a provider with `<action android:name="tv.sofaly.plugin.TILE"/>`. `query()` on
  `content://<authority>/tile` returns one row with `title` (14 chars), `detail` (16), `state` (`on`/`off`) and `icon`
  (a name from the list on the page below). On OK the launcher calls `call(uri, "click", null, null)` and reads the row
  again. Scenes and routines can press it too.
- **Wallpaper:** existing wallpaper-provider plugins for Android TV launchers work unchanged.
- **Icon pack:** any pack with an `appfilter.xml`.

A plugin that fails 5 times in a row within 6 hours, or keeps taking more than 2.5 seconds, is turned off by itself;
the person sees why in Plugins.
Full page: https://chrnx0.github.io/sofaly/plugins.html

---

**Português:** dois apps de exemplo que põem um cartão na tela inicial da Sofaly. `contagem` é o modelo (dias até uma
data escolhida na TV); `lua` mostra a fase da Lua calculada na própria TV. Copie, troque o pacote e publique. Nenhum dos
dois usa internet, conta ou dado da pessoa.
