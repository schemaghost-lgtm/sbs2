# SBS Browser

A minimal native Android (Kotlin) browser that renders the same webpage in
two side-by-side panes, like a Google Cardboard / VR split view. The left
pane is the only one you touch; the right pane mirrors its scroll position
and navigation in real time so both halves stay perfectly in sync.

## How it works

- Two `WebView`s sit in a horizontal split (`activity_main.xml`).
- The **left** WebView is fully interactive. Its `WebViewClient.onPageFinished`
  pushes whatever URL it lands on into the right WebView, so links, redirects,
  and searches on the left are mirrored automatically.
- The **right** WebView has its touch events swallowed
  (`setOnTouchListener { _, _ -> true }`) and its `shouldOverrideUrlLoading`
  short-circuited, so it can never navigate or scroll on its own.
- `webViewLeft.setOnScrollChangeListener { ... }` copies the left pane's
  scroll offset onto the right pane on every scroll tick, with a boolean
  guard (`isSyncingScroll`) to avoid feedback loops.
- Pinch-zoom is disabled on both panes (`setSupportZoom(false)`) so the two
  renders always share the same layout width/scale — otherwise scroll
  offsets wouldn't line up pixel-for-pixel between panes.
- A "VR" button hides the address bar and enables immersive fullscreen
  (hides status/nav bars) for dropping the phone into a cardboard-style
  viewer. Back button (or the VR button again) exits immersive mode.

## Getting an APK with zero local tools (GitHub Actions)

This project includes `.github/workflows/build.yml`, which compiles a debug
APK in the cloud every time you push. You only need a free GitHub account
and a browser — no Android Studio, no SDK, nothing installed locally.

1. Go to github.com, sign up/log in (free), and create a **new repository**
   (any name, e.g. `sbs-browser`). Leave it empty — don't add a README.
2. On the new repo's page, click **"uploading an existing file"** (or the
   "Add file" → "Upload files" button).
3. Unzip `SBSBrowser.zip` on your computer/phone, then drag the *contents*
   of the `SBSBrowser` folder (not the zip itself) into the GitHub upload
   box. Make sure the `.github` folder comes along — some file managers hide
   dot-folders, so double check it's included.
4. Commit the upload.
5. Click the **"Actions"** tab at the top of the repo. A workflow run called
   "Build APK" should start automatically (takes ~2-4 minutes).
6. When it finishes (green checkmark), click into the run, scroll to
   **Artifacts**, and download `SBSBrowser-debug-apk.zip`.
7. Unzip that — inside is `app-debug.apk`. Transfer it to your phone (Google
   Drive, email, USB, whatever) and tap it to install.
8. Android will likely block it as "unknown source" the first time — you'll
   need to allow installs from that source (Settings → Apps → Special access
   → Install unknown apps) since this isn't a Play Store build.

This produces a debug-signed APK, which is totally fine for installing on
your own device — it just isn't signed for Play Store distribution.

## Building it locally (if you ever get Android Studio)

1. Open the `SBSBrowser` folder in Android Studio (Koala or newer recommended).
2. Let Gradle sync — it will pull the AGP 8.5.2 / Kotlin 1.9.24 toolchain.
3. Run on a device or emulator (`minSdk 24`, targets `34`).

## Things you'll likely want to tweak next

- **Barrel/pincushion distortion correction** — real Cardboard viewers bend
  the image through a lens. Right now this is a flat split, not lens-corrected.
  If you want proper VR distortion, that's a much bigger lift (custom
  `SurfaceView`/GL rendering instead of two WebViews) — worth a separate
  conversation if you want to go there.
- **IPD (eye separation) adjustment** — could add a slider that insets each
  pane horizontally to match different face widths.
- **Default homepage / bookmarks** — currently hardcoded to Google; swap
  `defaultUrl` in `MainActivity.kt`.
- **Cleartext/mixed content** — `usesCleartextTraffic="false"` in the
  manifest blocks plain `http://` sites by default; flip it if you need it.
