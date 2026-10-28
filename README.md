# MeridaGram

MeridaGram is a personal Telegram client for Android, based on the official
[Telegram for Android](https://github.com/DrKLO/Telegram) source code (13.1.1).

It installs as a separate app (`com.meridagram.messenger`), so it can live
next to the official Telegram on the same phone.

## Changes from the official app

- App name **MeridaGram** and its own package name and system account type
- Official Telegram update checks are disabled
- Built for `arm64-v8a` only

## Getting the APK

Every push to `main` is built by GitHub Actions (**Build APK** workflow).
Open the latest successful run on the **Actions** tab and download the
`MeridaGram-<number>` artifact — it is a zip with the APK inside.

The build can also be started by hand: **Actions → Build APK → Run workflow**.

## Building locally

Requirements: JDK 17, Android SDK (platform 36, build-tools 36.0.0),
NDK 27.2.12479018, CMake 3.22.1.

```sh
git clone --recursive https://github.com/hikingerop/meridagram.git
cd meridagram
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
```

The APK is written to
`TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`.

## API keys

`TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` still
holds the official `APP_ID`/`APP_HASH`. Replace them with your own keys from
<https://my.telegram.org> (API development tools).

## License

Telegram for Android is licensed under GNU GPL v2, and so is MeridaGram —
see [LICENSE](LICENSE).
