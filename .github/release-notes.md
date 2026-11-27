**Sofaly** · build {BUILD} · commit {COMMIT}

Your TV, simple again: an ad-free home for Google TV and Android TV. The full tour is on [sofaly.tv](https://sofaly.tv)
and in the [README](https://github.com/ChrnX0/sofaly#readme).

### What changed

<!-- auto:changes -->
- Phone app: the QR reader stands upright with a square window and orange corners, instead of the wide barcode reader
- Phone app: a paired TV shows its own name, not “TV” or a code
- Phone as a helper: the camera picture keeps the TV's shape, the phone microphone cuts out less, and “Find the remote” lists only real remotes and remembers when each was last used
- The panel icon on the phone, the browser tab, the TV in Windows' Network and the website use the graphite logo, without the old navy blue
- Panel: the “Help other homes” switch now says that slow apps and the screen-check rules go too
- New tagline on the About screen and at startup: “Cozy time on your TV.”
- Automations also use three columns, the Family PIN notice is an info line, and Internet diagnostics has a compact button to check again
- Phone app: reading the TV's QR code no longer closes it, the path to the QR on the TV is right, and the remote and “Use away from home?” respond again
- Help other homes: the screen now says that the name and version of apps that crashed also go, and “See what goes today” shows all of it
- The settings screens that were still one stretched column now use three columns; the forecast shows its art under the dark veil
- Movies, Radio, theme, rows and focus lists follow the same standard as the other screens
- Weather saved by the home no longer shows up as a change in Undo or in the TV's diary
- Sofaly name everywhere: the phone app downloads as sofaly-phone.apk and the panel certificate as sofaly-panel.crt
- With a film open, the subtitle delay and the player settings changed in Settings no longer erase each other
- Settings: “Home and network” is now “Home and remote access”
- Sofaly checks the screens by reading the app in front and, when it cannot read one, says why
- Texts that ran words together got the missing space, as in Internet diagnostics
<!-- /auto:changes -->

### Download

| File | For |
|---|---|
| `sofaly-launcher.apk` | 32-bit (`armeabi-v7a`), e.g. Chromecast with Google TV. Downloader code **6566820** |
| `sofaly-launcher-arm64.apk` | 64-bit (`arm64-v8a`). In Downloader: `sofaly.tv/download-arm64` |
| `sofaly-phone.apk` | The Sofaly phone app (Android 10+): finds your TVs, opens the panel, remote, volume keys, Share to TV |

Each APK has a `.sha256` file next to it. Already installed? Sofaly updates itself from here.
