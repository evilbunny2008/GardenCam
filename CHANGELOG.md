## 0.0.2 - unreleased

### Platform and build
- Only arm64-v8a is built now; armeabi-v7a, x86 and x86_64 are no longer supported. Devices with a
  32-bit Android system, including many Android TV boxes, can no longer install the app.
- libVLC is now a custom, stripped-down 3.7.7 build (`app/libs/libvlc.aar`) instead of `libvlc-all`
  from Maven Central. It keeps RTSP, RTP, plain HTTP, MPEG-TS/PS, MP4, H.264/H.265, MJPEG, still
  images and G.711/G.726/AAC audio, and drops everything else, including TLS (`https://` and
  `rtsps://` streams), RTMP and subtitles. Native libraries shrink from 55.3 MB to 11.6 MB and the
  release APK to about 7 MB. The patches and build script are in `libvlc/`.
- `ndkVersion` is set so native libraries are stripped of debug symbols when packaged
  (`libc++_shared.so`: 9.3 MB to 1.4 MB).
- Fullscreen stream view uses `WindowInsetsControllerCompat` instead of the deprecated
  `systemUiVisibility` flags.

## 0.0.1 - unreleased (Garden Cam fork)

Versioning restarted at 0.0.1 (versionCode 1) for the fork.

Forked from [ONVIF Camera](https://github.com/mretallack/cams) 2.5.0 by Mark Retallack
(itself a fork of [Cams](https://github.com/vladpen/cams) by vladpen). Changes since forking:

### Identity
- Renamed the app to **Garden Cam** with application ID `com.odiousapps.gardencam`, so it installs
  alongside Cams and ONVIF Camera. Settings are not shared between them: export from the old app
  and import into Garden Cam.
- Moved the Kotlin packages from `com.vladpen` / `com.vladpen.cams` / `com.vladpen.onvif` to
  `com.odiousapps.gardencam` and `com.odiousapps.gardencam.onvif`.
- APKs are now named `gardencam-<abi>-<buildtype>.apk` and settings exports `gardencam<date>.cfg`.
- About screen and documentation credit the fork history.

### Platform and build
- Target and compile SDK 37 (Android 17), Android Gradle Plugin 9.5.2, Kotlin 2.4.21, Gradle 9.9.1.
- Updated dependencies: libVLC 3.7.7, Gson 2.15.0, JSch 2.28.8, AndroidX Core 1.19.1,
  AppCompat 1.8.0, WorkManager 2.12.0, Activity 1.13.0, Material 1.14.0.
- Release builds are now minified and resource-shrunk by R8, with keep rules for libVLC, JSch and the
  Gson settings models.
- Per-ABI version codes moved to the `androidComponents` API; ABI splits are skipped for App Bundle builds.
- Supports Android 17's local network permission: it is requested when a camera is on the LAN, and
  streams restart once it is granted.

### Security
- ONVIF credentials are now encrypted with AES-256-GCM using a key held in the Android Keystore.
  Credentials saved by older versions are migrated on first load and the old key is deleted.
  Settings exports use a device-independent form, protected by the export password.
- Removed ONVIF debug tests that ran on every stream screen: they posted to httpbin.org, added a fake
  device to the cache and sent extra requests to the camera.
- XML from the camera's encoder settings is parsed with DOCTYPE declarations rejected.

### Video and PTZ
- Lower stream latency: network caching cut from 100 ms to 50 ms (also set per stream), and audio is
  disabled on PTZ-capable streams so the picture is no longer timed against the audio buffer.
- New **Tune camera for low latency** action in the camera editor: sets H.264 Baseline (no
  B-frames) and, through ONVIF Media2, constant bitrate on every encoder, then reports what the camera kept.
- PTZ prefers ContinuousMove and falls back to RelativeMove for cameras that reject it (such as Thingino).
- Motion-event subscriptions are now released when leaving a stream: the unsubscribe request is sent,
  and polling no longer leaks when a camera is reopened.
- PTZ errors are logged instead of ignored.
- Haptic feedback works on Android 7 (pre-Oreo vibration fallback).
- Removed the previous/next file and seek bar from the recording player.

### Housekeeping
- Removed the unit and instrumented tests and their dependencies, and the GitHub Actions CI workflow.
- Added a Software Heritage archive workflow that runs on tags.
- Removed dead code (connection pool, test harnesses, unused helpers and data classes).
- Plural-aware strings for camera counts and the export password length.
- Many lint and inspection clean-ups (deprecated APIs, redundant constructs, named arguments).

## 2.5.0 - 2026-09-26

- Added RTSP Backchannel Audio (Talk-to-Camera) support with push-to-talk microphone button, haptic/visual feedback, and immediate mute toggle
- Added multi-payload SDP backchannel parsing supporting PCMU, PCMA, and AAC codecs
- Added Basic Authentication fallback for RTSP backchannel detection on Thingino camera firmware
- Improved PTZ relative movement speed and responsiveness on Thingino cameras

## 2.4.8 - 2026-01-27

- Fixed PTZ control for Thingino cameras (now uses RelativeMove instead of ContinuousMove)
- Fixed ONVIF stream discovery to inject credentials into RTSP URLs
- Updated README with Thingino camera requirements and troubleshooting

## 2.4.7 - 2026-01-24

- Fixed F-Droid version code calculation for multi-architecture APKs

## 2.4.6 - 2026-01-24

- Restructured configuration page: ONVIF fields now appear first and are always visible
- RTSP URL moved below ONVIF configuration section
- Added ONVIF motion detection support with fast polling fallback
- Updated About page to reflect fork status and maintainer information
- Added comprehensive debug logging for ONVIF troubleshooting
- Multi-architecture support: separate APKs for armeabi-v7a, arm64-v8a, x86, and x86_64

## 2.4.5 - 2026-01-24

Fork with ONVIF PTZ control support

## 2.4.4 - 2025-10-17

Component update

## 2.4.3 - 2025-06-13
- Android 16 support
- Import/export on Android TV (requires file manager)

## 2.4.2 - 2025-05-08

Component update

## 2.4.1 - 2025-01-04

Refactoring, optimization, fixes and improvements

## 2.4 - 2024-11-18

- Android Television support
- Android 15 support
- Show selected source on startup
- Stretch video frames in the groups
- Long click to show app bars in the groups

## 2.3 - 2024-05-14

- Watchdog
- Channel icons
- Component update

## 2.2 - 2024-03-01

- Switching channels on group screens
- Improved loading bar

## 2.1.1 - 2023-12-25

- Playback speed bug fix
- Link to user manual
- Component update

## 2.1 - 2023-09-13

- Motion detection support
- Android 14 "Upside Down Cake" support (API level 34)
- Component update
- Fixes and improvements

## 2.0.3 - 2023-07-03

Network observer fix

## 2.0.2 - 2023-05-20
- Component update
- Adaptive app icon
- Polish localization

## 2.0.1 - 2023-03-28
Component update

## 2.0 - 2022-11-04

- Android 13 "Tiramisu" support (API level 33)
- Kernel update
- Component update
- Improved interface
- Fixes

Attention! The method of file encryption during export of settings has been changed, as well as channels settings, re-saving is required.

## 1.3.5 - 2022-10-04

- 2 channel cameras support
- Fixes and improvements

## 1.3.4 - 2022-09-13

- Unlimited cameras in the groups
- Cameras sorting and deleting in the group settings

## 1.3.3.1 - 2022-08-20

- Fast connections

## 1.3.3 - 2022-08-14

- Groups zooming
- Copying device logs to the clipboard
- Component update
- Optimization and bug fixes

## 1.3.2.1 - 2022-07-14

- Network connection alert visibility fix

## 1.3.2 - 2022-07-11

- Connection lost handler
- Device log reader (section "About")

## 1.3.1 - 2022-06-24

- Export/import: password confirmation and current date in the file name
- Fixes and improvements

## 1.3.0 - 2022-05-30

- Common screen of cameras and groups
- Export/Import: password encryption
- Grid at main screen
- Visualization of swiping
- Refactoring and optimizations
- Fixes

## 1.2.2 - 2022-05-16

- Export/import settings
- Fixes, tweaks and improvements

## 1.2.1 - 2022-05-13

- Cameras and groups: manual sorting
- URL transcoding: critical error fix
- Enhanced security
- Tweaks and improvements

## 1.2.0 - 2022-05-09

- Groups
- Fixes, tweaks and improvements

## 1.1.4 - 2022-04-22

- Enhanced security
- Improved video bars behavior
- Portuguese locale support

## 1.1.3 - 2022-04-15

- Video bars auto hiding
- Clickable header in files mode
- Improved navigation

## 1.1.2 - 2022-04-13

Auto updates for F-Droid

## 1.1.1 - 2022-04-12

- The mute button
- Tweaks and improvements

## 1.1.0 - 2022-04-04

SFTP browser

## 1.0.3 - 2022-03-24

Fastlane directory structure for F-Droid

## 1.0.2 - 2022-03-21

Minor tweaks and improvements

## 1.0.1 - 2022-03-20

Split APKs per ABI and english localization

## 1.0 - 2022-03-16

VideoGestureDetector & VideoActivity.onStart event listener

## [Unreleased] - 2022-03-06

First release
