# VividOrbit — Play Store Readiness Plan

Phased plan from current state (`versionCode 2`, 1538 LOC, works on one Droidlogic STB)
to a Play Store release with phone-based configuration.

**Release gates** — you do not have to ship all 9 phases at once:

| Gate | Phases | Version | Track |
| :--- | :--- | :--- | :--- |
| **A** — works off dev hardware, passes review | 1–3 | 2.1 | Internal testing |
| **B** — production candidate | 4, 7 | 2.2 | Closed → production 20% |
| **C** — headline features | 5, 6 | 2.3 | Production update |
| **D** — depth | 8 | 2.4+ | Production update |

Phase 9 (release engineering) runs continuously from Gate A onward, not at the end.

> **If the phone-QR feature is the reason you're shipping**, swap Phase 5 and Phase 7.
> Phase 5 has no dependency on Phase 7; it only depends on Phases 3 and 4.

Effort is rough dev-days for one person familiar with the code. Treat as relative sizing.

---

## Phase 0 — Baseline (0.5d)

Nothing here changes behavior. It exists so the next eight phases have a known-good
reference point.

1. `git checkout -b playstore-prep`.
2. `./gradlew assembleDebug` — confirm it builds clean *before* touching anything.
3. Install on the real Droidlogic STB. Record: cold-launch time, channel count,
   time-to-first-frame, `adb shell dumpsys meminfo com.vorynlabs.vividorbit`.
   These are your regression baselines — you will otherwise have no way to prove
   Phase 3 helped.
4. Screen-record the current guide, settings, and zap behavior. You will change
   focus handling in Phase 3 and want a before/after.
5. `adb logcat -s ChannelRepository:* TvViewHelper:*` during a full session, saved to a file.

**Exit criteria:** debug APK installs and tunes on target hardware; baseline numbers written down.

---

## Phase 1 — Build, SDK, and release config (2–3d)

Pure configuration. Highest chance of breaking the build, zero chance of breaking
runtime logic — which is exactly why it goes first, in isolation.

### 1.1 SDK and toolchain bump

Play's target-API floor rises every August. `targetSdk 34` is below it and **will be
rejected at upload**.

- Confirm the current required `targetSdk` in Play Console → *App bundle explorer*
  (do not trust a number from a blog post — it changes annually).
- Bump `compileSdk` and `targetSdk` to that floor.
- This forces a toolchain bump. Current: AGP 8.2.0, Kotlin 1.9.0, Gradle 8.7.
  Use Android Studio's **AGP Upgrade Assistant** rather than hand-editing — it
  resolves the AGP ↔ Gradle ↔ compileSdk compatibility matrix for you.
- Kotlin 1.9.0 → latest 2.x. Watch for K2 compiler warnings on the `object :`
  anonymous callback in `TvViewHelper.kt:36`.

**Migration risks to verify on device, not just at compile time:**

| Risk | Where | Check |
| :--- | :--- | :--- |
| Edge-to-edge enforcement (API 35+) | `styles.xml` `windowFullscreen` | Video still full-bleed; overlays not clipped |
| Orientation restrictions ignored on large screens (API 36) | `AndroidManifest.xml:21` `screenOrientation="landscape"` | Harmless on TV (landscape is native) but confirm no letterboxing |
| `Activity` + `Theme.Leanback` (not AppCompat) | `MainActivity.kt:34` | Theme still applies, no crash on inflate |

### 1.2 Remove `enableJetifier`

`gradle.properties` sets `android.enableJetifier=true`. No legacy support-library
dependencies remain. Remove it — measurable build-time win, zero risk.

### 1.3 Release signing

```
app/
  keystore.properties      # gitignored — never commit
  build.gradle             # reads it, falls back to debug signing if absent
```

- Generate an **upload key** (2048-bit RSA minimum, 25+ year validity).
- Enroll in **Play App Signing** — Google holds the app signing key, you hold the
  upload key. If you lose the upload key it's recoverable; without Play App Signing
  it is not.
- `signingConfigs.release` must degrade gracefully when `keystore.properties` is
  missing, so CI and other machines can still build debug.

### 1.4 Enable minification

```groovy
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

Low risk here: the custom-numbers JSON is hand-built via `JSONObject` with explicit
string keys (`ChannelRepository.kt:59-69`), so there is **no reflective
serialization to keep**. Coroutines ship their own consumer rules.

- R8 full mode is the default now. Build release, install, and exercise every
  screen — minification bugs are runtime-only.
- Keep `-keep class com.droidlogic.** { *; }` until Phase 3 resolves whether the
  AAR is needed at all.

### 1.5 Icons and banner — currently wrong size *and* wrong folder

`banner.png` is 1672×941 and `logo.png` is 1254×1254, both in `res/drawable/`.
Android treats bare `drawable/` as **mdpi**, so on an xhdpi TV panel it upscales
them 2× — soft edges on your launcher tile.

| Asset | Required | Location |
| :--- | :--- | :--- |
| Launcher icon | 48/72/96/144/192 px | `mipmap-mdpi` … `mipmap-xxxhdpi` |
| Adaptive icon | 108dp fg + bg layers | `mipmap-anydpi-v26/ic_launcher.xml` |
| **TV banner** | **exactly 320×180** | `drawable-xhdpi/banner.png` |
| Play store icon | 512×512 PNG | Play Console upload |
| Feature graphic | 1024×500 | Play Console upload |
| TV screenshots | 1280×720, min 3 | Play Console upload |

Google's TV guidance requires the **app name baked into the banner artwork** — the
Leanback launcher renders no separate text label.

### 1.6 Disable backup

```xml
android:allowBackup="false"
```

Custom channel numbers are keyed by local `TvContract` channel `_ID`s, which are
assigned by the box's own scan. Restoring that map onto a different STB produces a
scrambled lineup — worse than no restore at all.

> The Phase 5 phone UI gives you a *correct* migration path (`/api/export` →
> `/api/import`), keyed by channel name rather than local ID. That is the right
> answer for this, not Android auto-backup.

### 1.7 Ship an AAB

`./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
APKs are no longer accepted for new apps.

### 1.8 Crash reporting

Add Firebase Crashlytics (~10 lines: plugin, dependency, `google-services.json`).

Do this **now, not in Phase 9** — every phase after this one involves testing on
hardware you can't attach a debugger to, and Play Console vitals only start
reporting *after* public release.

**Exit criteria:** signed, minified AAB builds; installs on STB; all screens work;
Crashlytics receives a deliberately-thrown test crash.

---

## Phase 2 — Correctness: make it work on hardware that isn't yours (3–4d)

Without this phase the app is broken on every device except your STB. This is the
phase that decides whether Play review passes.

### 2.1 TV input discovery — the single worst blocker

`ChannelRepository.kt:121` hardcodes:

```kotlin
val tunerInputId = "com.droidlogic.dtvkit.inputsource/.DtvkitTvInput/HW19"
```

On any other box the cursor returns empty → "No channels found" → a reviewer sees a
broken app and rejects it.

**Replace with runtime discovery:**

```kotlin
// ChannelRepository — resolve available tuner inputs
private fun tunerInputIds(): List<String> =
    context.getSystemService(TvInputManager::class.java)
        ?.tvInputList
        ?.filter { it.type == TvInputInfo.TYPE_TUNER }
        ?.map { it.id }
        ?: emptyList()
```

**Selection precedence:**
1. `PREF_PREFERRED_INPUT_ID`, if it still exists in the list.
2. The Droidlogic ID, if present — preserves current behavior on your hardware.
3. The only tuner, if exactly one.
4. Otherwise → show an **input picker** overlay (new small UI, reuses sidebar styling).

**Also fix in the same query** — all of these are currently missing and all of them
produce visible wrongness on other boxes:

| Column | Problem now | Fix |
| :--- | :--- | :--- |
| `COLUMN_BROWSABLE` | Not filtered — hidden/unscanned channels appear in the guide | `WHERE browsable = 1` |
| `COLUMN_SERVICE_TYPE` | Not filtered — data services show as blank rows | Keep `SERVICE_TYPE_AUDIO_VIDEO`; radio behind a user toggle |
| `COLUMN_DISPLAY_NAME` | Can be empty → row renders as a blank strip | Fall back to `"Channel ${displayNumber}"` |

Also switch the manual `try/finally` cursor block to Kotlin's `use { }` — `Cursor`
is `Closeable`, so it's the same guarantee in four fewer lines.

### 2.2 The permission gate can permanently brick the app

`MainActivity.kt:778`:

```kotlin
if (grantResults.isNotEmpty() && grantResults.all { it == PERMISSION_GRANTED })
```

`com.android.providers.tv.permission.READ_EPG_DATA` is declared
`signature|privileged` in AOSP. A normal Play-installed app **cannot be granted it.**
When it returns `DENIED`, `.all { }` is false forever, and the user is parked on
static error text with no retry path and no way out.

This currently works for you only because your build is side-loaded onto a box where
it's effectively privileged. It will fail for every Play user.

**Fix:**
- **Require** only `android.permission.READ_TV_LISTINGS`.
- Still *request* `READ_EPG_DATA` — harmless, and grants extra data on privileged installs.
- Load channels if `READ_TV_LISTINGS` alone is granted.
- Error state gets a focusable **Retry** button, plus a **Open app settings** action
  (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) for the permanent-denial case.

> Open question resolved in Phase 6: whether `TvContract.Programs` reads work with
> `READ_TV_LISTINGS` alone on your target hardware. If not, EPG is a
> privileged-install-only feature. Test this early — it changes Phase 6's scope.

### 2.3 `onStop() → finish()` kills the app too eagerly

`MainActivity.kt:764-769` finishes the activity on any full obscure — Home, standby,
and on some OEMs a system overlay or the permission-grant screen itself. If your
box's runtime-permission UI is a full activity, **first launch closes itself.**

Releasing the tuner is correct; killing the activity is not.

```kotlin
override fun onStop() {
    super.onStop()
    tvViewHelper.reset()      // releases the TIF session — tuner is free
}

override fun onStart() {
    super.onStart()
    selectedChannel?.let { tuneToChannel(it) }
}
```

`TvView.reset()` tears down the session, so the tuner is genuinely released without
destroying the activity. Keep `FLAG_KEEP_SCREEN_ON`.

**Test matrix:** Home → relaunch · standby → wake · permission dialog → return ·
screensaver → dismiss · another TV app steals the tuner → return.

### 2.4 `onDestroy` can mask the real crash

`MainActivity.kt:788-799` calls `super.onDestroy()` first, then touches four
`lateinit` fields. If `onCreate` dies at any of the ~25 `findViewById` calls,
`onDestroy` throws `UninitializedPropertyAccessException` — and *that* is the stack
trace you get in Crashlytics, not the actual inflate failure.

Guard each with `::field.isInitialized`, and move `super.onDestroy()` to the end.

### 2.5 Keypad tuning misses sub-channels

`tuneToChannelNumber` (`MainActivity.kt:554`) matches only via
`displayNumber.toIntOrNull()`. DTH sub-channel numbers like `"102-1"` parse to
`null` and are **unreachable from the keypad entirely**.

Match exact string first, fall back to integer comparison.

### 2.6 Memory pressure

STBs are memory-poor and `ChannelLogoLoader`'s `LruCache` holds bitmaps for the
process lifetime with no eviction hook.

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_RUNNING_LOW) ChannelLogoLoader.evictAll()
}
```

**Exit criteria:** app loads channels on a generic Android TV emulator running the
TIF sample input, with only `READ_TV_LISTINGS` granted. Home/standby round-trip
does not close the app.

---

## Phase 3 — Performance and architecture (2–3d)

This phase **removes** more code than it adds. Do it before any UI expansion —
everything after this builds on the data flow it fixes.

### 3.1 Stop rebuilding both adapters on every change

`loadChannelData()` (`MainActivity.kt:288-305`) constructs a fresh `ChannelAdapter`
*and* a fresh `ChannelSettingsAdapter` and reassigns both `RecyclerView.adapter`
properties. This runs on **every** toggle, auto-renumber, reset, and single-number
save.

Each time: full view re-inflate, every logo rebound, focus lost, guide scroll
position reset to top.

Meanwhile `ChannelAdapter.updateChannels()` (`ChannelAdapter.kt:50`) — a correct
off-main-thread `DiffUtil` implementation, already written and already correct — **is
never called from anywhere in the codebase.**

**Fix:**
- Construct both adapters **once** in `onCreate`, with empty lists.
- `loadChannelData()` calls `updateChannels(...)` on each.
- Delete the adapter-construction block from `loadChannelData`.

Single largest perf win available, and it's a net deletion.

### 3.2 Give the settings adapter the same diff path

`ChannelSettingsAdapter.updateChannels()` (`:35-38`) uses `notifyDataSetChanged()`.

The two adapters differ only by layout resource and click target. Extract the
`DiffUtil.Callback` into one shared top-level function used by both:

```kotlin
// ui/ChannelDiff.kt
fun channelDiff(old: List<Channel>, new: List<Channel>) = object : DiffUtil.Callback() { ... }
```

Do **not** merge the adapters behind a `viewType` — that's an abstraction over two
call sites, and it costs more than it saves.

### 3.3 Selection updates steal focus

`setCurrentChannel` (`ChannelAdapter.kt:39-48`) calls `notifyItemChanged(index)` on
every tune. A full rebind of a *focused* row can reset D-pad focus — likely
contributing to whatever focus jank you've seen while zapping with the guide open.

Use a payload:

```kotlin
notifyItemChanged(index, PAYLOAD_SELECTION)
// onBindViewHolder(holder, position, payloads): if payload present,
// only set itemView.isSelected — do not rebind logo or text
```

### 3.4 Logo loader: negative cache and one decode size

Two separate problems in `ChannelLogoLoader.kt`:

**Missing logos re-hit IO forever.** A channel with no logo returns `null`, nothing
is cached, so *every* subsequent bind reopens a `ContentResolver` stream. Scroll 142
channels a few times and that's hundreds of failed IO round-trips.

```kotlin
private val misses = Collections.synchronizedSet(mutableSetOf<Long>())
// check at entry, add on null result
```

**Cache key ignores requested size.** Keyed on `channelId` alone, so a 100px row
decode poisons the 200px banner request — the banner logo renders soft, and on a 4K
panel (40dp ≈ 160px) noticeably so.

Lazy correct fix: **always decode at the largest size any call site needs (200px)**
and let `ImageView` downscale. One constant changes; no composite cache key, no
extra decode paths.

### 3.5 Dead dependencies and resources

| Item | Evidence | Action |
| :--- | :--- | :--- |
| `com.google.android.material:material` | Zero references in `src/main` | Remove |
| `androidx.appcompat` | Zero direct references (arrives transitively via leanback) | Optional — removing the explicit line changes nothing |
| `_unused_resources/` | Outside source set; costs nothing at build time | Delete for tidiness |
| Unused colors/drawables (`text_tertiary`, `live_dot_bg`) | Defined, unreferenced | Wire up or delete — let `./gradlew lint` produce the list |

**`mochitif-release.aar` — verify before removing.** Grep shows zero references to
`com.droidlogic.*` in Kotlin source. But it may be bound at runtime by the vendor
input service, or required by your STB's integration. **Test on target hardware with
it removed.** If channels still tune, drop it and the `-keep com.droidlogic.**`
ProGuard rule with it. If they don't, leave both and document why.

### 3.6 Main-thread prefs read (conditional)

`updateSettingsToggleUi()` → `isCustomNumbersEnabled()` is the first
`getSharedPreferences` touch, which parses the whole prefs file — including the
custom-numbers JSON — on the main thread.

At 142 channels the JSON is ~3 KB. Fine. At 1500 channels it's an ANR risk. Move the
first touch into the existing IO coroutine **only if** the Phase 9 stress test shows
it matters.

**Exit criteria:** toggling custom numbers preserves guide scroll position and focus.
Baseline numbers from Phase 0 improved or unchanged. Release APK smaller.

---

## Phase 4 — Startup / default channel (1d)

Small, self-contained, and a **prerequisite for Phase 5** — the phone UI needs this
pref model to exist before it can expose it.

### 4.1 Preference model

```kotlin
PREF_STARTUP_MODE       // "last" | "default" | "first"   — default "last"
PREF_DEFAULT_CHANNEL_ID // Long
PREF_LAST_CHANNEL_ID    // Long — already exists
```

Defaulting `STARTUP_MODE` to `"last"` means **zero behavior change** for existing
installs.

### 4.2 Resolution with a full fallback chain

```kotlin
private fun resolveStartupChannel(channels: List<Channel>): Channel? = when (startupMode) {
    DEFAULT -> channels.byId(defaultChannelId) ?: channels.firstOrNull()
    FIRST   -> channels.firstOrNull()
    LAST    -> channels.byId(lastChannelId)
                 ?: channels.byId(defaultChannelId)
                 ?: channels.firstOrNull()
}
```

The fallback chain is the whole point. **Channel IDs change after a tuner rescan** —
every path must degrade to "first channel", never to a crash or a blank screen.
Keep the existing `preserveCurrentChannel` branch untouched; it's orthogonal.

### 4.3 UI surfaces

| Surface | Interaction |
| :--- | :--- |
| Settings panel | New row: **Startup channel** → `Last watched` / `Fixed: BBC News` / `First in list` |
| Guide row | Long-press `DPAD_CENTER` → *"Set as default channel"* — most discoverable placement |
| Phone UI (Phase 5) | Dropdown, same three modes |

### 4.4 Check

Pure function, silent when wrong, three branches → worth one test.

```
test_startup_resolution.kt
  - LAST with valid last id      → that channel
  - LAST with stale id           → falls through to default
  - LAST, stale id, no default   → first channel
  - DEFAULT with stale id        → first channel
  - any mode, empty channel list → null, no crash
```

**Exit criteria:** set a default channel, force-stop, relaunch → opens on it. Rescan
the tuner (IDs change) → relaunch still opens on *something*, never a blank screen.

---

## Phase 5 — Phone configuration over QR (5–7d)

**The headline feature.** TV shows a QR code; phone on the same Wi-Fi scans it and
gets a web UI for reordering channels, assigning numbers, and setting config.

No cloud, no account, no internet required — the STB serves the page itself.

### 5.1 Architecture

```
TV app
├─ LocalConfigServer            embedded HTTP, bound to the LAN interface
│   ├─ GET  /?t=<token>         → serves index.html from assets/
│   ├─ GET  /api/state          → {channels[], startupMode, defaultChannelId, customNumbersEnabled}
│   ├─ POST /api/number         → {channelId, number}          → assignChannelNumber (atomic swap)
│   ├─ POST /api/reorder        → {orderedChannelIds[]}        → bulk renumber 1..N
│   ├─ POST /api/config         → {customNumbersEnabled?, startupMode?, defaultChannelId?}
│   ├─ POST /api/tune           → {channelId}                  → phone-as-remote (bonus)
│   ├─ GET  /api/export         → lineup JSON, keyed by channel NAME
│   └─ POST /api/import         → restore lineup from that JSON
├─ QrPanel                      overlay: QR bitmap + typed URL + session status
└─ Session token                32 hex chars, regenerated every time a session opens
```

`/api/export` + `/api/import` are the migration path that Phase 1.6 gave up by
disabling Android auto-backup — and they're *better*, because keying on channel name
survives a tuner rescan where local IDs don't.

### 5.2 HTTP library — decide with a 30-minute spike

`com.sun.net.httpserver` is **not** in the Android SDK, so the stdlib rung doesn't
apply. Hand-rolling HTTP on a raw `ServerSocket` at a network trust boundary is
exactly the kind of corner not to cut.

| Option | Size | Maintenance | Verdict |
| :--- | :--- | :--- | :--- |
| **Ktor CIO** (`io.ktor:ktor-server-cio`) | ~2–3 MB | Actively maintained | **Recommended** — network-facing code should be maintained code; already using coroutines |
| NanoHTTPD (`org.nanohttpd:nanohttpd`) | ~50 KB | Last release 2016 | Fallback if APK size on the STB is a hard constraint |

Spike both against `/api/state`, measure APK delta, pick, move on. Ktor needs
ProGuard keep rules — budget an hour for that with `minifyEnabled true`.

### 5.3 Security — do not skimp here

An HTTP server on the LAN with no auth means **anyone on the Wi-Fi can rewrite your
channel lineup.** Seven controls, in rough order of how much risk each removes:

1. **Opt-in toggle, default OFF.** Server code never runs unless the user enables
   phone config in settings. Cheapest possible risk reduction — most users never
   open the port at all.
2. **Server lifetime = session lifetime, never background.** Starts when a phone
   session opens; hard-stops on explicit *Disconnect*, on `onStop()`, on activity
   destroy, and on a 10-minute idle timeout.
   - UX nuance: users *will* close the QR panel while still editing on the phone. So
     the session outlives the panel, indicated by a small persistent badge on the TV
     with a Disconnect action. Panel closing ≠ session ending.
   - Consequence worth the tradeoff: no foreground service needed, which also avoids
     Play's FGS-type scrutiny entirely.
3. **Bearer token.** 32 hex chars from `SecureRandom`, in the QR URL on first load,
   then held in `sessionStorage` and sent as `X-Token` on every API call.
   **Constant-time compare.** Anything else → `401`.
4. **Bind to the LAN interface address**, not a broad wildcard.
5. **Server-side validation on every write** — this is the trust boundary, and the
   phone is untrusted input:
   - `channelId` must exist in the current in-memory lineup
   - `number` must match `^[0-9]{1,4}$` and land in 1..9999
   - `orderedChannelIds` must be a **permutation** of known IDs, length-capped
   - request bodies over 64 KB rejected
   - assets served from a **fixed whitelist map**, never by path concatenation
     (no `../` traversal)
6. **Rate limit**: reject more than ~20 writes/sec. Stops a runaway phone script from
   hammering `SharedPreferences`.
7. **No HTTPS — deliberately.** Self-signed TLS on a LAN means a browser warning the
   user must click through, which is worse UX *and* trains people to dismiss cert
   warnings. Accepted risk, documented: LAN-only, short-lived, token-gated, and no
   credentials or PII ever transmitted.

### 5.4 Thread safety — a real bug if skipped

Ktor/NanoHTTPD serve on their own threads. `assignChannelNumber`
(`ChannelRepository.kt:71-96`) is a **read-modify-write** over the custom-numbers
map. Concurrent with a UI-thread read, or with a second phone request, it silently
loses assignments.

`SharedPreferences` get/put are individually thread-safe; the *sequence* is not.

```kotlin
// ChannelRepository
// ponytail: single repo-wide lock; fine below ~2k channels, split per-key if it ever matters
@Synchronized fun assignChannelNumber(...)
@Synchronized fun saveCustomNumbersMap(...)
@Synchronized fun getCustomNumbersMap(...)
```

### 5.5 Pushing changes back to the TV

After a phone write, the TV must reload. Simplest thing that works:

```kotlin
// server thread → main thread, debounced ~500ms
mainHandler.removeCallbacks(reloadRunnable)
mainHandler.postDelayed(reloadRunnable, 500)   // → loadChannelData(preserveCurrentChannel = true)
```

No observers, no `LiveData`, no `Flow`. Phase 3's `updateChannels()` diff path makes
this cheap and non-disruptive — which is why Phase 3 comes first.

### 5.6 Web UI

Single `assets/index.html`. Inline CSS and JS, **no framework, no build step, no
CDN** — the STB may have no internet, and a CDN reference would simply fail to load.

- Phone-viewport-first (`max-width`, large touch targets).
- Dark palette matching the TV theme (`#0B0B0C` / `#161618` / `#F2F2F3`).
- Reorder via **up/down buttons**, not HTML5 drag — drag is fiddly on touch and
  needs more JS.
- Sections: channel list w/ inline number edit · reorder · Auto-Renumber ·
  startup-channel selector · custom-numbers toggle · export/import · *Tune this
  channel now*.
- Manual **Refresh** button. Skip WebSocket/SSE/polling for v1 — live sync is
  polish, and a refresh button is honest about what's happening.

### 5.7 QR generation

`com.google.zxing:core` only — *not* `android-core`, *not* `zxing-android-embedded`
(those are for **scanning**; you're only encoding).

```kotlin
QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512)  // → BitMatrix → IntArray → Bitmap
```

~20 lines. **Also render the URL as plain text under the QR** — for phones without a
scanner app, and for your own debugging.

### 5.8 Manifest and Play consequences

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Get the local IP via `NetworkInterface.getNetworkInterfaces()` — needs no additional
permission.

Note: Android's network-security-config cleartext restriction governs the app's own
*outbound* traffic. A `ServerSocket` accepting inbound connections is unaffected, and
the phone's browser is a separate app. Verify at implementation time, but expect no
manifest change beyond `INTERNET`.

> ⚠️ **`INTERNET` triggers a new Play requirement: a privacy policy URL.** Also
> revisit the Data Safety form — you collect nothing, but you now transmit channel
> names and numbers over the local network, and that should be described. Budget
> half a day for policy text and form updates; don't discover this at upload time.

### 5.9 Network reality testing

| Scenario | Expected behavior |
| :--- | :--- |
| Phone + STB on same Wi-Fi | Works |
| STB on Ethernet, phone on Wi-Fi, same subnet | Usually works |
| AP client isolation enabled | **Cannot work** — detect the failure and say so plainly in the TV UI |
| Guest network | Cannot work — same message |
| No network at all | QR panel shows *"Connect to Wi-Fi or Ethernet to use phone setup"*, server never starts |
| Phone leaves network mid-edit | Phone shows a connection error; TV session idles out cleanly |

The isolation cases are the ones that generate support complaints. A specific error
message is worth more here than any amount of retry logic.

### 5.10 Checks

Two pure, silent-when-wrong, security-relevant paths → both get tests.

```
test_config_server_validation.kt
  - number "0" / "10000" / "abc" / "12a" / ""        → all rejected
  - channelId not in lineup                          → rejected
  - reorder list with a duplicate / a missing id     → rejected
  - reorder list that is a valid permutation         → accepted
  - asset path "../../prefs.xml"                     → rejected
  - body over 64KB                                   → rejected

test_token.kt
  - wrong token → 401 · absent token → 401 · correct → 200
  - compare is constant-time (no early return on first mismatched char)
```

**Exit criteria:** phone scans QR, reorders 5 channels, sets a default channel; TV
reflects all of it within a second. Server is provably gone from `netstat` after
Disconnect and after backgrounding the app.

---

## Phase 6 — EPG / now-playing (3–4d)

The biggest perceived-quality gap. You already hold `READ_EPG_DATA` and never query
a single program.

### 6.1 Resolve the permission question first

Before writing any EPG code, confirm on target hardware whether
`TvContract.Programs` is readable with **`READ_TV_LISTINGS` alone** (see 2.2). If it
needs `READ_EPG_DATA`, EPG is privileged-install-only and this whole phase becomes a
graceful-degradation feature rather than a headline one. **One afternoon of testing
decides the scope of the phase** — do it before committing.

### 6.2 Now / next in the banner

```kotlin
TvContract.buildProgramsUriForChannel(channelId, nowMs, nowMs + 3.hours)
```

Banner gains: current program title, time range, a thin progress bar, and *Next: …*.

### 6.3 Program titles in the guide

Only ~8 rows are visible at a time. **Query lazily per visible row on bind**, cached
with a ~60s TTL — not one batch query over all 142 channels.

### 6.4 Degrade silently

Many DTH tuners populate `Programs` sparsely or not at all. Every EPG surface must
look intentional when the data is absent — no empty labels, no spinners that never
resolve, no layout shift when text appears.

**Out of scope:** the full grid EPG (time-axis × channel-axis). That's its own
multi-day feature with its own scrolling model. Not v1.

**Exit criteria:** banner shows now/next on a channel with EPG data, and looks
deliberate on one without.

---

## Phase 7 — UI, theme, accessibility (2–3d)

Gate B work. This is what Play's TV quality review actually looks at.

### 7.1 Overscan-safe area — most visible "not native" issue

The sidebar sits at `x=0` and the guide header at the very top. Leanback guidance is
**48dp horizontal / 27dp vertical**. On real TVs with overscan, your header clips off
the top edge.

Critical detail: **`TvView` must stay full-bleed.** Apply insets to the sidebar,
settings panel, and banner containers — *not* to the `ConstraintLayout` root, or
you'll letterbox the video.

```xml
<!-- values/dimens.xml -->
<dimen name="tv_overscan_h">48dp</dimen>
<dimen name="tv_overscan_v">27dp</dimen>
```

### 7.2 Extract hardcoded strings

`activity_main.xml` hardcodes: `"All Channels"`, `"◀ Settings"`, `"▶ Guide"`,
`"1..N Linear"`, `"Channel Logo"` (×3), `"Channel Name"`, and the conflict warning
text.

Ironically, `@string/btn_auto_renumber`, `@string/settings_btn_text`, and
`@string/btn_close` **already exist in `strings.xml` and are never used.** Wire those
up and extract the rest. `lint` will flag every one, and localization is currently
impossible.

Given the DTH context, `values-hi/` is worth considering — optional.

### 7.3 Accessibility

- Channel rows expose **three unlabeled nodes** to TalkBack instead of one row.
  Set `importantForAccessibility="no"` on the child `TextView`s and compose a
  single `contentDescription` in `bind()`:
  *"Channel 5, BBC News, now playing"*.
- `android:contentDescription="Channel Logo"` is a hardcoded English literal in
  three layouts. The logo is decorative next to a labeled row → `null` is correct.
- Verify D-pad reachability of every focusable with TalkBack on.

Cheap, and not the kind of thing to skip.

### 7.4 Placeholder logo

`@android:drawable/ic_menu_slideshow` appears in four places. It's a system icon that
looks foreign against your palette. Replace with one vector: a letter-avatar from the
channel's initial, or a muted TV glyph.

### 7.5 Focus feedback

Flat background swap only, no scale or elevation. One `stateListAnimator` XML
(scale 1.0 → 1.04 + elevation on focus) applied to row backgrounds is what makes TV
UI feel responsive. Optional but high perceived value per line.

### 7.6 Confirmation for destructive actions

`Auto-Renumber` and `Reset to DTH` (`MainActivity.kt:379-394`) fire immediately and
irreversibly wipe a hand-built lineup. Add a confirm step — generalize the existing
`edit_number_card` into a reusable confirm card rather than pulling in `AlertDialog`
(which is unthemed against Leanback).

### 7.7 Content-blocked state

`onContentBlocked` (`TvViewHelper.kt:78`) collapses into the generic `onInputError` →
"Channel Unavailable" (`MainActivity.kt:258`). A parental-rating block reads as a
malfunction.

Distinct message at minimum: *"Blocked by parental rating"*. PIN entry is the
complete answer but can follow in Phase 8 — this is compliance-adjacent, not
cosmetic.

### 7.8 States

Loading is a bare `ProgressBar`; error and empty states have no retry affordance and
no branding. Give all three a consistent treatment with the Retry action from 2.2.

### 7.9 Guide navigation at scale

142 channels × 70dp with D-pad only is a long hold. Add:
- A scroll position indicator (`android:scrollbars="vertical"`).
- First-letter jump: hold a number key to jump to that letter group.

**Exit criteria:** nothing clips on a TV with overscan enabled. `./gradlew lint`
reports zero hardcoded strings. TalkBack reads one coherent node per channel row.

---

## Phase 8 — Remaining features (4–6d, individually shippable)

Ordered by value per line of code. Each is independent — ship them one at a time.

| # | Feature | Effort | Notes |
| :--- | :--- | :--- | :--- |
| 1 | **Last-channel toggle** | ~1h | `KEYCODE_LAST_CHANNEL` + long-press BACK. Store `previousChannelId` on each tune. One variable, one branch — best ratio in the whole plan. |
| 2 | **Audio / subtitle track picker** | 1d | `TvViewHelper` already tracks audio tracks; `_unused_resources/TrackAdapter.kt` is an abandoned start. Expose `getTracks`/`selectTrack`, add an overlay, persist preferred language via `TvTrackInfo.getLanguage()` and auto-select on tune. Multi-language DTH without this is a real gap. |
| 3 | **Favorites** | 0.5d | `PREF_FAVORITES_JSON` as `Set<Long>`. Reuses the exact prefs-JSON pattern already written for custom numbers. Filter mode in the guide. |
| 4 | **User-defined channel groups** | 1–2d | Better than genre filtering — DTH `SERVICE_TYPE`/genre data is unreliable. Editable from the Phase 5 phone UI, where typing is actually pleasant. |
| 5 | **Guide search** | 0.5d | On-screen keyboards are miserable on TV. Do first-letter jump on the TV (7.9) and real text search **in the phone UI**. Don't build a TV keyboard. |
| 6 | **Parental PIN** | 1d | Completes 7.7. Store the PIN hashed (not plaintext) in prefs. |
| 7 | ~~Aspect-ratio toggle~~ | — | **Likely not portable.** `TvView` exposes no public aspect/zoom API; OEMs do it via `sendAppPrivateCommand`, which is vendor-specific. Either make it Droidlogic-only and document that, or drop it. |

**Explicitly out of scope for v2.x:** timeshift, recording, cloud sync of lineups,
multi-user profiles, picture-in-picture.

---

## Phase 9 — Release engineering (continuous, 2–3d of dedicated work)

Runs from Gate A onward.

### 9.1 Test matrix

| Axis | Cases |
| :--- | :--- |
| Hardware | Real Droidlogic STB · generic Android TV emulator w/ TIF sample input |
| API level | 28 (minSdk) · 34 · current target |
| Panel | 1080p · 4K (logo sharpness, overscan) |
| Channel count | 0 · 1 · 142 · **1500** (stress, via a debug-only fake repository) |
| Permissions | Both granted · listings only · both denied · denied-then-granted |
| Lifecycle | Home · standby · screensaver · tuner stolen by another app · force-stop |
| Phone config | All six network scenarios from 5.9 |
| Memory | `adb shell am send-trim-memory ... RUNNING_CRITICAL` |
| **Remote variants** | ⚠️ **Some remotes have no MENU or GUIDE key.** The guide must be reachable another way — `DPAD_LEFT` already opens settings from the guide; confirm at least one path exists from fullscreen on a minimal remote. |

### 9.2 Tests worth writing (and only these)

Five places where a bug is both silent and damaging:

```
test_channel_number_assignment.kt   swap logic incl. the no-old-number branch (ChannelRepository.kt:71-96)
test_channel_sort.kt                comparator: non-numeric, duplicate, blank numbers
test_startup_resolution.kt          Phase 4.4 fallback chain
test_config_server_validation.kt    Phase 5.10 input validation
test_token.kt                       Phase 5.10 constant-time compare
```

Extract these as pure functions so they run on the JVM without Robolectric. No
frameworks beyond JUnit, no fixtures, no per-function suites.

### 9.3 Play Console checklist

- [ ] AAB signed with upload key, Play App Signing enrolled
- [ ] `targetSdk` ≥ current Play floor
- [ ] Store icon 512×512 · feature graphic 1024×500 · **TV banner 320×180 in-app**
- [ ] ≥3 TV screenshots at 1280×720
- [ ] Short + full description
- [ ] Content rating questionnaire
- [ ] Data Safety form — revisit after Phase 5 adds `INTERNET`
- [ ] **Privacy policy URL — required once `INTERNET` is requested**
- [ ] Target audience declaration
- [ ] **TV form factor declared** + Play's TV quality checklist passed:
      D-pad-only navigable · no touch requirement · banner present · launch time ·
      no crash on back-from-launcher
- [ ] Internal testing track → closed → production 20% → 100%

### 9.4 Content rights

Play rejects apps that surface broadcast content without demonstrable distribution
rights. VividOrbit reads whatever the box's own tuner already scanned and streams
nothing itself, so you are **likely** fine — but have that explanation written down
before review, because it's the kind of question that stalls an app for a week.

---

## Dependency graph

```
Phase 0  baseline
  └─ Phase 1  build/SDK config ──────────────┐
       └─ Phase 2  correctness               │
            └─ Phase 3  performance ─────────┤
                 ├─ Phase 4  default channel │
                 │    └─ Phase 5  phone/QR   │   ← needs 3 (diff reload) + 4 (pref model)
                 ├─ Phase 6  EPG             │   ← verify 2.2 permission answer first
                 ├─ Phase 7  UI/theme        │
                 └─ Phase 8  features        │
                              Phase 9  ◄─────┘   continuous from Gate A
```

**Total: ~25–35 dev-days** for all nine phases. Gate A alone (Phases 1–3, the
must-ship-or-it's-broken work) is **7–10 days**.

---

## Two things to decide before starting

1. **Is the Droidlogic AAR actually needed?** Phase 3.5 can't be resolved by reading
   code — it needs one test on real hardware with the AAR removed. Answer it early;
   it affects the ProGuard config in Phase 1.4.
2. **Does `TvContract.Programs` work with `READ_TV_LISTINGS` alone?** One afternoon
   of testing (Phase 6.1) determines whether EPG is a headline feature or a
   privileged-install bonus. Test it during Phase 2, not Phase 6.
