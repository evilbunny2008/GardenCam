# Custom libvlc build

Garden Cam uses a stripped-down build of libvlc 3.7.7 (arm64-v8a only) instead of
`org.videolan.android:libvlc-all` from Maven Central. It keeps only what an IP camera viewer needs,
which cuts the native libraries from 55.3 MB to 11.6 MB.

| | Stock libvlc-all 3.7.7 | Garden Cam build |
|---|---|---|
| `libvlc.so` | 45.9 MB | 10.1 MB |
| AAR | 24.4 MB | 7.3 MB |
| VLC plugins | 323 | 81 |

The built library is kept in the repository as `app/libs/libvlc.aar`. This `libvlc/` directory
holds everything needed to reproduce it, and is the corresponding source for the LGPL.

## What is included

- **Access:** RTSP (live555, over TCP or UDP), RTP, UDP, TCP, plain HTTP, local files
- **Demux:** MPEG-TS, MPEG-PS, MP4, raw H.264/H.265 (es, h26x), MJPEG, still images
- **Decode:** MediaCodec (hardware), FFmpeg software decoders for H.264, H.265, MJPEG, PNG, AAC,
  G.711 (A-law/µ-law) and G.726; VLC's native G.711, ADPCM and PCM decoders
- **Output:** Android window/OpenGL ES video, OpenSL ES and AudioTrack audio, soxr resampling
- **Other:** credential keystores (RTSP authentication) and the Android logger

## What is removed

TLS (`https://`, `rtsps://`), RTMP, subtitles and text rendering (libass, FreeType, fontconfig),
optical discs, SMB/NFS/SFTP/UPnP, Lua, stream output and Chromecast, playlists and DASH (libxml2),
Matroska, Ogg, AVI and other container formats, software VP9/AV1, Vorbis/Opus/FLAC/MP3 and most
FFmpeg codecs, and most video and audio filters.

A stream that needs a removed module fails to play. With connection logging enabled, logcat shows
`no suitable ... module`, naming what is missing.

## Rebuilding

The build runs on Linux and needs the Android SDK and an NDK between r27 and r29:

```bash
export ANDROID_SDK=/opt/android-sdk-linux
export ANDROID_NDK=$ANDROID_SDK/ndk/29.0.14206865
libvlc/build-libvlc.sh            # uses ../vlc-android, cloning it if missing
```

The script checks out vlc-android `libvlc-3.7.7` and libvlcjni
`0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6`, fetches VLC with VideoLAN's patches, applies the two
patches below, rebuilds the contribs from scratch and copies the AAR to `app/libs/libvlc.aar`.

## Patches

- **`libvlcjni.patch`** (applies to `vlc-android/libvlcjni`)
  - `buildsystem/compile-libvlc.sh`: trimmed contrib list, VLC configure flags and module blacklist.
    The default GPL licence branch no longer forces `--enable-dvdread`.
  - `libvlc/jni/Android.mk`: links `-liconv` explicitly (it used to arrive through libass and
    libxml2) and drops the unused `-ljpeg`.
  - `libvlc/jni/libvlcjni.c`: fontconfig code is guarded by `HAVE_FONTCONFIG`, so
    `LibVLC.buildFontCache()` does nothing in this build.
- **`vlc.patch`** (applies to `vlc-android/libvlcjni/vlc`)
  - `contrib/src/ffmpeg/rules.mak`: on Android, FFmpeg is built with `--disable-everything` and
    only the decoders and parsers listed above.
  - `contrib/src/live555/keep-first-session-id.patch` (applied by `contrib/src/live555/rules.mak`):
    live555 keeps the session ID from the first SETUP instead of the last. Some cameras ("H264DVR"
    firmware) return a new ID for every SETUP but only stream on the first, so VLC's PLAY got no
    data. Desktop VLC has the same problem; FFmpeg already uses the first ID.

FFmpeg's avformat is still built, without any demuxers, because VLC 3's avcodec module does not
compile without it. The `avformat` and `avio` plugins are blacklisted.

## Updating libvlc

1. Change `VLC_ANDROID_TAG` (and `LIBVLCJNI_HASH`, from `LIBVLCJNI_TESTED_HASH` in that tag's
   `buildsystem/compile.sh`) in `build-libvlc.sh`.
2. Run the script. If a patch no longer applies, update it by hand in the vlc-android checkout and
   regenerate it with `git -C <repo> diff > libvlc/<name>.patch`.
3. Test RTSP over TCP and UDP, a camera with audio, and an HTTP/MJPEG stream.
