## Garden Cam

> **Note**: Garden Cam is a fork of [ONVIF Camera](https://github.com/mretallack/cams) by Mark Retallack,
> which is itself a fork of the original [Cams](https://github.com/vladpen/cams) app by vladpen.
> It was forked to remotely view plants in a garden through a PTZ camera, so it focuses on
> keeping the picture in step with pan/tilt/zoom movements.

**Package ID**: `com.odiousapps.gardencam`  
**Forked from**: ONVIF Camera 2.5.0 (`uk.org.retallack.onvifcamera`)  
**Original app**: Cams (`com.vladpen.cams`)

Garden Cam can be installed alongside both Cams and ONVIF Camera. They don't share settings:
use **Export settings** in the old app's main menu, then import the file into Garden Cam.

![Cams](https://raw.githubusercontent.com/vladpen/cams/main/fastlane/metadata/android/ru-RU/images/phoneScreenshots/5_cover.jpg)

## Changes Since Forking

Compared with ONVIF Camera 2.5.0, Garden Cam:

- **Is renamed** to Garden Cam, with application ID `com.odiousapps.gardencam` and Kotlin packages
  `com.odiousapps.gardencam` / `com.odiousapps.gardencam.onvif`.
- **Reduces latency** for PTZ cameras: smaller network cache, no audio on PTZ streams, and a
  one-tap encoder tuning action (see [Low Latency](#low-latency)).
- **Encrypts ONVIF credentials properly**, with AES-256-GCM and a key held in the Android Keystore.
- **Targets Android 17**, including its local network permission, with updated build tools and libraries.
- **Ships smaller release builds**, minified and shrunk with R8.
- **Drops debug code** that contacted httpbin.org and the camera every time a stream opened.
- **Fixes motion-event clean-up**, so subscriptions are released and polling doesn't leak.
- **Prefers ContinuousMove** for PTZ, falling back to RelativeMove (for example on Thingino cameras).
- **No longer includes** the test suites, the GitHub Actions CI workflow, or the recording player's
  previous/next/seek bar.

See [CHANGELOG.md](CHANGELOG.md) for the full list.

Simple Android mobile application for playing RTSP streams from IP cameras.

Features:

- View RTSP streams from any IP cameras, including H.265+.
- Simultaneous viewing of multiple streams.
- 20x image zoom.
- Support for dual-channel cameras.
- View video recordings or images via SFTP protocol.
- Motion detection alerts configuration.
- High connection speed.
- Extremely simple navigation and control.
- Maximum security and data privacy.
- TCP/UDP protocol switching.
  This option is important when viewing cameras over the internet, where UDP may not be supported or work poorly.
- **ONVIF PTZ Control**: Touch-based pan, tilt, and zoom control for ONVIF-compatible cameras.
- **ONVIF Device Discovery**: Automatic detection of ONVIF cameras on the network.
- **Touch Gesture Control**: Intuitive touch-based PTZ control with visual feedback.

<img src="https://raw.githubusercontent.com/vladpen/cams/main/fastlane/metadata/android/ru-RU/images/phoneScreenshots/1_main_ru.jpg"
alt="Main screen"
width="200">&nbsp;
<img src="https://raw.githubusercontent.com/vladpen/cams/main/fastlane/metadata/android/ru-RU/images/phoneScreenshots/2_edit_ru.jpg"
alt="Edit screen"
width="200">&nbsp;
<img src="https://raw.githubusercontent.com/vladpen/cams/main/fastlane/metadata/android/ru-RU/images/phoneScreenshots/3_files_ru.jpg"
alt="Files screen"
width="200">&nbsp;
<img src="https://raw.githubusercontent.com/vladpen/cams/main/fastlane/metadata/android/ru-RU/images/phoneScreenshots/4_video_ru.jpg"
alt="Video screen"
width="200">

The application is written for joint use with the [python-rtsp-server](https://github.com/vladpen/python-rtsp-server) server,
but works perfectly standalone thanks to the ability to connect to any IP cameras, as well as video recorders that support SFTP.

Plays RTSP and plain HTTP video streams from IP cameras (H.264, H.265 and MJPEG).
The screenshot above shows an image from a real video camera and three test videos in "Group" mode.

### Video Playback Technology

The app uses **VLC for Android (libvlc)** as its video playback engine, providing:
- RTSP (over TCP or UDP), RTP and plain HTTP streams
- Hardware-accelerated H.264 and H.265/HEVC decoding, with software fallback; MJPEG and still images
- G.711, G.726 and AAC camera audio
- Low-latency streaming optimized for IP cameras

Garden Cam ships a stripped-down build of libvlc 3.7.7 (`app/libs/libvlc.aar`, arm64-v8a only)
rather than `libvlc-all` from Maven Central, which cuts the native libraries from 55.3 MB to
11.6 MB. It does not support TLS (`https://`, `rtsps://`), RTMP, subtitles or most container
formats and codecs. See [libvlc/README.md](libvlc/README.md) for what is included and how to
rebuild it.  
License: LGPL 2.1+ / GPL 2.0+ - See [VideoLAN Legal](https://www.videolan.org/legal.html)

*IMPORTANT. The application is focused on security and data privacy, so it does not collect or process any user information.
Data is not sent to any servers, including Google's technical infrastructure and manufacturers' "cloud" storage.*

## Installation

Build the APK yourself:

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/gardencam-arm64-v8a-release-unsigned.apk`.
Only arm64-v8a is built; 32-bit and x86 devices are not supported. The build has no signing configuration, so sign the APK
before installing it:

```bash
apksigner sign --ks release.keystore --out gardencam-arm64-v8a-release.apk \
    app/build/outputs/apk/release/gardencam-arm64-v8a-release-unsigned.apk
```

For a quick test on a device, `./gradlew installDebug` installs a debug build instead.

## Configuration

To connect to a video camera, you need to enter its URL in the "Address" field as specified by the manufacturer. Usually it looks like this:
```
[rtsp://][<user>:<password>@]<IP>[:<port>][/<path>]
```
Parameters in square brackets are optional (depends on camera settings).

For dual-channel cameras, you can additionally specify the address of the second channel.
For example, for Hikvision cameras and their derivatives, the path will look like this:
```
ISAPI/Streaming/Channels/<channel number>
```
Then the first channel (high resolution) will have number 101, and the second (low resolution) — 102.

Low resolution channels can be used to speed up image loading,
to save traffic and to reduce device processor load.
This is especially convenient for viewing groups of cameras at low connection speeds.
During playback, channels can be switched using the K1/K2 button in the lower right corner of the screen.
On camera group screens, K2 is used by default.

Also, to reduce load, playback of cameras that go beyond the screen boundaries when zooming is paused.

SFTP server or video recorder address looks like this:
```
[sftp://]<user>:<password>@<IP>[:<port>][/<path>]
```
WARNING! It is strongly not recommended to use administrator access credentials.
For SFTP server, it's better to create chroot, for example, as described [here](https://wiki.archlinux.org/title/SFTP_chroot).

**Tip:** you can use emoji as icons in camera names.
For example, the screenshots above use icons from the standard mobile phone set.

## ONVIF Setup

ONVIF support (from the ONVIF Camera fork) covers for automatic camera discovery, stream configuration, and PTZ control.

### Adding ONVIF Cameras

#### Quick Setup (Recommended)
1. Tap "Add Camera" in the main screen
2. Enter a camera name
3. Tap "Add ONVIF Configuration" to show ONVIF fields
4. Enter your ONVIF URL in the format:
   ```
   onvif://username:password@camera-ip:port/onvif/device_service
   ```
   Example: `onvif://admin:password123@192.168.1.100:80/onvif/device_service`
5. Wait 1 second - the app will automatically discover and populate RTSP stream URLs
6. Tap "Save"

#### Manual RTSP Entry
If automatic discovery fails, you can still manually enter RTSP URLs:
- The ONVIF URL is optional
- Enter RTSP URLs directly in the "Address" fields
- ONVIF PTZ control will still work if ONVIF URL is provided

#### Common ONVIF Endpoints
Different cameras use different ONVIF service paths:
- Most cameras: `/onvif/device_service`
- Some cameras: `/onvif/media_service`
- Thingino cameras: `/onvif/device_service`

The app automatically tries multiple endpoints to find the correct one.

### Touch-Based PTZ Control

For ONVIF cameras with PTZ capabilities:
- Touch the center of the video screen to activate PTZ control
- Drag your finger to pan and tilt the camera
- **Variable Speed**: Movement speed scales with distance from center
  - Near center: Slow, precise control (10% speed)
  - Medium distance: Moderate speed (up to 50%)
  - Far from center: Fast movement (up to 100% speed)
- The PTZ dot follows your finger movement with visual feedback
- Release to stop movement - the dot smoothly returns to center
- Per-camera settings for PTZ inversion and rate limiting
- Works in fullscreen landscape mode for optimal control

### ONVIF Configuration Options

In the camera edit screen, you can configure:
- **Invert Horizontal PTZ**: Reverse left/right movement direction
- **Invert Vertical PTZ**: Reverse up/down movement direction  
- **PTZ Rate Limit**: Minimum milliseconds between PTZ commands (default: 200ms)
- **Tune camera for low latency**: Reconfigures the camera's encoders over ONVIF (see [Low Latency](#low-latency))

These settings are saved per-camera and persist across app restarts.

### Automatic Stream Discovery

When you enter an ONVIF URL, the app automatically:
1. Connects to the camera's ONVIF service
2. Discovers available media profiles
3. Selects the highest quality profile for primary stream
4. Selects a secondary profile for dual-channel support (if available)
5. Retrieves RTSP URLs for both streams
6. Auto-populates the RTSP URL fields

This eliminates the need to manually find and enter RTSP URLs for ONVIF cameras.

### Supported ONVIF Features

- **Automatic Stream Discovery**: GetProfiles and GetStreamUri for RTSP URL detection
- **Device Discovery**: WS-Discovery protocol for automatic camera detection on network
- **Touch PTZ Control**: Intuitive gesture-based pan, tilt, and zoom with variable speed
- **Per-Camera Settings**: Individual PTZ inversion and rate limiting configuration
- **Profile S Compliance**: Compatible with ONVIF Profile S cameras
- **Secure Authentication**: WS-UsernameToken and HTTP Digest authentication

### Troubleshooting ONVIF

If automatic discovery fails:
- Verify the ONVIF URL format is correct
- Check that ONVIF is enabled on your camera
- Ensure the username/password are correct
- Try different ONVIF endpoints (`/onvif/device_service` vs `/onvif/media_service`)
- Check network connectivity between phone and camera
- Manually enter RTSP URLs as fallback

#### Thingino Camera Notes

Thingino cameras have specific ONVIF requirements:

**PTZ Control:**
- Thingino firmware (as of recent updates) only supports **RelativeMove** for PTZ control
- ContinuousMove is no longer supported - the camera accepts commands but doesn't execute them
- The app automatically uses RelativeMove for all Thingino cameras
- PTZ commands must use the **hostname** (not IP address) for proper operation
- PTZ endpoint is `/onvif/ptz_service` (discovered via GetCapabilities)

**Stream Discovery:**
- RTSP URLs returned by GetStreamUri do not include credentials
- The app automatically injects ONVIF credentials into discovered RTSP URLs
- Use the hostname in your ONVIF URL (e.g., `onvif://user:pass@camera.local:80/onvif/device_service`)
- The camera may return IP addresses in GetCapabilities responses, but PTZ requires using the original hostname

**Common Issues:**
- If PTZ doesn't work, ensure you're using a hostname (not IP) in the ONVIF URL
- If streams don't authenticate, the app will automatically add credentials from ONVIF URL
- After firmware updates, PTZ behavior may change - RelativeMove is currently the only supported method

## Low Latency

PTZ is much easier to use when the picture follows the camera's movement closely. Garden Cam:

- Uses a 50 ms network cache for live streams.
- Disables audio on PTZ-capable streams (those with an ONVIF URL and credentials). Without audio,
  video isn't timed against Android's audio output buffer, which can add 100–300 ms. Those streams
  don't show a mute button; talk-back still works.
- Offers **Tune camera for low latency** in the camera editor. It uses ONVIF to set every
  video encoder to the H.264 Baseline profile (which has no B-frames) and, where the camera
  supports ONVIF Media2, constant bitrate. It then reads the settings back and reports what the
  camera actually kept. This changes the camera's own settings, so it also affects anything else
  that records from it.

For the lowest latency, also turn off any vendor "smart" codec (H.264+/H.265+, Smart Codec, ZeroGOP)
in the camera's web interface, because ONVIF can't control it. Use UDP if your network is reliable.

## Motion Detection Alerts

Optionally, the application can notify about camera motion detector triggers.
Alerts are triggered when a new image from the camera appears in the specified SFTP server folder.
For this function to work, you need to configure cameras and image storage server.
These settings are described in detail in the parallel project [Cams-PWA](https://github.com/vladpen/cams-pwa).

Detailed discussion of the application: [habr.com/ru/post/654915](https://habr.com/ru/post/654915/)
and server: [habr.com/ru/post/597363](https://habr.com/ru/post/597363/).

## Version Codes

Garden Cam restarted its versioning at `versionName` 0.0.1, `versionCode` 1.
Increase `versionCode` by 1 for each release; the build turns it into one code per output:

| Output                 | versionCode            | Example for versionCode 2 |
|------------------------|------------------------|---------------------------|
| App Bundle (no splits) | versionCode × 10       | 20                        |
| arm64-v8a APK          | versionCode × 10 + 2   | 22                        |

Version 0.0.1 (versionCode 1) also had armeabi-v7a (+1), x86 (+3) and x86_64 (+4) APKs. Those
digits stay reserved, so codes never clash if an ABI is added back.

Release notes go in `fastlane/metadata/android/en-US/changelogs/<code>.txt`, one file for each of
these codes (500 characters at most).

*Copyright © 2026 evilbunny2008, © 2026 Mark Retallack, © 2022-2025 vladpen, under the MIT license.
Use it with absolutely no warranty.*


## TODO / Configuration
- TODO: Make PTZ translation step / speed configurable in camera settings.
