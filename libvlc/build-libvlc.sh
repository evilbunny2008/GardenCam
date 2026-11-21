#!/bin/sh
# Builds the stripped-down libvlc used by Garden Cam and copies it to app/libs/libvlc.aar.
#
# Usage: libvlc/build-libvlc.sh [vlc-android checkout dir]
#   (default: ../vlc-android next to this repository)
#
# Requires ANDROID_SDK and ANDROID_NDK (NDK 27-29) in the environment, e.g.
#   export ANDROID_SDK=/opt/android-sdk-linux
#   export ANDROID_NDK=$ANDROID_SDK/ndk/29.0.14206865
set -e

HERE=$(cd "$(dirname "$0")" && pwd -P)
APP_DIR=$(dirname "$HERE")
VLC_ANDROID=${1:-$(dirname "$APP_DIR")/vlc-android}

VLC_ANDROID_TAG=libvlc-3.7.7
LIBVLCJNI_HASH=0b8dc65efb203c86a0476bc337ddd99ecf1c0ef6
ABI=arm64-v8a
TUPLE=aarch64-linux-android

: "${ANDROID_SDK:?set ANDROID_SDK to your Android SDK directory}"
: "${ANDROID_NDK:?set ANDROID_NDK to an NDK 27-29 directory}"

if [ ! -d "$VLC_ANDROID" ]; then
    git clone https://code.videolan.org/videolan/vlc-android.git "$VLC_ANDROID"
fi
cd "$VLC_ANDROID"
git checkout -q "$VLC_ANDROID_TAG"

# Fetch libvlcjni and VLC (with VideoLAN's own patches) without building anything yet
if [ ! -d libvlcjni ]; then
    git clone -q --single-branch --branch libvlcjni-3.x \
        https://code.videolan.org/videolan/libvlcjni.git libvlcjni
    git -C libvlcjni reset -q --hard "$LIBVLCJNI_HASH"
fi
if [ ! -d libvlcjni/vlc ]; then
    (cd libvlcjni && ./buildsystem/get-vlc.sh)
fi

# Apply Garden Cam's changes (skipped if already applied)
apply() {
    if git -C "$1" apply --check "$2" 2>/dev/null; then
        git -C "$1" apply "$2"
    elif git -C "$1" apply --check --reverse "$2" 2>/dev/null; then
        echo "$(basename "$2"): already applied"
    else
        echo "$(basename "$2"): does not apply cleanly to $1" >&2
        exit 1
    fi
}
apply libvlcjni "$HERE/libvlcjni.patch"
apply libvlcjni/vlc "$HERE/vlc.patch"

# Contribs must be rebuilt from scratch whenever their flags change
rm -rf "libvlcjni/vlc/contrib/$TUPLE" \
       "libvlcjni/vlc/contrib/contrib-android-$TUPLE" \
       "libvlcjni/vlc/build-android-$TUPLE"

./buildsystem/compile.sh -l -a "$ABI" -r

mkdir -p "$APP_DIR/app/libs"
cp libvlcjni/libvlc/build/outputs/aar/libvlc-release.aar "$APP_DIR/app/libs/libvlc.aar"
echo "Copied libvlc.aar to $APP_DIR/app/libs/"
