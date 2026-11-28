**Sofaly** · build {BUILD} · commit {COMMIT}

Cozy time on your TV: an ad-free home for Google TV and Android TV. The full tour is on [sofaly.tv](https://sofaly.tv)
and in the [README](https://github.com/ChrnX0/sofaly#readme).

### What changed

<!-- auto:changes -->
- Phone app: it asks for the camera before opening the QR reader; if you say no, it offers Try again or the phone's settings, in the phone's language
- Help other homes: “See what goes today” shows each server address as it reads, without backslashes
- Panel: turning the phone microphone or camera off also clears the “Microphone on” or “Live on the TV” notice
- Air quality: “Hazardous” is shown in alert red
- Test channel: updates come in parts, so the TV downloads less, and the home screen frees graphics memory while another app is open or the screen is off
- AdGuard DNS: in the panel (Router), paste your AdGuard key and the TV gets its own server “Sofaly · TV name”, private DNS and parental controls (bedtime, blocked services, safe YouTube and search); the last 24 hours show in the panel and in Diagnostics
- Automations: each event shows its value under the name, and the settings tabs fade at the edge
- Phone app: a TV that is offline shows a clear notice in the app instead of a browser error page
- Assistant: when an action is blocked, it points to System › Advanced › Confirmations
- Movies: the movie page now has “Remove from Continue watching”, and the assistant can also remove a title from it by name; the removed movie also leaves the home screen highlight
- Settings open straight on the requested tab; the tabs, Home and Games no longer cut text
- Names no longer break a word in the middle; character and details in Cast show in full
- Home screen: the row that scrolls up no longer seems to slide under the summary
- Games and Listen use the same graphite as the other screens
- Night filter: while the TV is off the amber layer goes away and frees memory; it comes back as soon as the screen turns on
<!-- /auto:changes -->

### Download

| File | For |
|---|---|
| `sofaly-launcher.apk` | 32-bit (`armeabi-v7a`), e.g. Chromecast with Google TV. Downloader code **6566820** |
| `sofaly-launcher-arm64.apk` | 64-bit (`arm64-v8a`). In Downloader: `sofaly.tv/download-arm64` |
| `sofaly-phone.apk` | The Sofaly phone app (Android 10+): finds your TVs, opens the panel, remote, volume keys, Share to TV |

Each APK has a `.sha256` file next to it. Already installed? Sofaly updates itself from here.
