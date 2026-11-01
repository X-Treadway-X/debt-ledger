# Debt Ledger (دفتر الديون) — native Android app

Kotlin + Jetpack Compose. Not a WebView. Same features as the website: customers, debts, payments,
history, reports, Arabic/English (RTL), light/dark, WhatsApp messages, contact picker, and
Google Drive sync with the same merge rules and the same backup file format.

## 1. Build the APK (GitHub Actions)

1. Create a **private** GitHub repository (the signing key is inside this project, so keep it private).
2. Upload everything in this folder to it and push to the `main` branch.
3. Open the repository's **Actions** tab. The **Build APK** workflow starts by itself (or press *Run workflow*).
4. When it turns green, open the run and download the **debt-ledger-apk** artifact (a zip containing `app-release.apk`).
5. Copy `app-release.apk` to the phone and open it. Allow "Install unknown apps" for your file manager or browser if asked.

If the run fails, open it, copy the red error lines and send them to Claude.

## 2. Allow Google Drive sign-in for the app (one time)

Use the **same Google Cloud project** you made for the website.

1. Google Cloud console → **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.daftar.deyoun`
4. SHA-1 certificate fingerprint:

   ```
   21:6B:F7:F6:45:1D:3E:11:B4:9A:43:91:14:4E:E2:62:8B:FD:50:47
   ```
5. Create. There is no Client ID to paste into the app: Google matches the app by package name + fingerprint.
6. Make sure the **Google Drive API** is enabled and your Gmail is still listed under **Test users**
   (Google Auth Platform → Audience), and `.../auth/drive.appdata` is under **Data Access**.

Then in the app: **☁ → Google Drive → Connect**, pick your account, allow access.

## 3. Moving your existing data

* Same Google account + same Cloud project: the app should see the website's backups after the first sync.
* If it does not: on the website press **Download backup**, copy the file to the phone, and in the app use
  **Settings → Import backup**.

## 4. Troubleshooting

| Message | Meaning / fix |
|---|---|
| `ApiException: 10` or "DEVELOPER_ERROR" | The Android OAuth client is missing or its package name / SHA-1 is wrong (step 2). Wait a few minutes after creating it. |
| `ApiException: 12500` / sign-in cancelled | Account not in **Test users**, or Drive API not enabled. |
| `ApiException: 7` | No internet. |
| "Access blocked: app has not completed verification" | Add your Gmail under Test users. |
| `HTTP 403` from Drive | Drive API not enabled in the project. |
| App not installing over an older build | Each CI build has a higher version number, so updates install over the old one. If you built locally with a different key, uninstall first. |

## 5. Build on your own computer instead

```bash
# needs JDK 17 and the Android SDK (platform 34); Android Studio installs both
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## 6. Project map

```
app/src/main/kotlin/com/daftar/deyoun/
  Model.kt        data types, balances, money formatting        (pure Kotlin, tested)
  Merge.kt        merge / restore / tombstones                  (pure Kotlin, tested)
  Codec.kt        backup file read/write (same format as web)
  Store.kt        the ledger on disk + edit operations
  Prefs.kt        language, theme, sync options
  GoogleAuth.kt   Google sign-in (Authorization API; silent token refresh)
  DriveApi.kt     Drive appDataFolder REST calls
  SyncManager.kt  sync engine, auto modes, backups list
  Strings.kt      Arabic + English text
  Theme.kt Ui.kt Screens.kt Dialogs.kt MainActivity.kt   the screens
tools/LogicCheck.kt   stand-alone test of Model.kt + Merge.kt (see tools/README.md)
```

## 7. Security note

`daftar.jks` (password `daftar-ledger`) signs the app so its fingerprint stays constant. Anyone who has this file
can sign an app that looks like yours, so keep the repository private. If you ever publish to Google Play,
create a separate release key and remove this one.
