#!/bin/sh
# Deletes libvlc build output so the next build starts from scratch.
# Keeps the downloaded source tarballs (vlc/contrib/tarballs).
#
# Usage: libvlc/clean.sh [vlc-android checkout dir]
set -e

HERE=$(cd "$(dirname "$0")" && pwd -P)
J=${1:-$(dirname "$(dirname "$HERE")")/vlc-android}/libvlcjni

rm -rf "$J/vlc/contrib/aarch64-linux-android" \
       "$J/vlc/contrib/contrib-android-aarch64-linux-android" \
       "$J/vlc/build-android-aarch64-linux-android" \
       "$J/libvlc/jni/obj" \
       "$J/libvlc/jni/libs" \
       "$J/libvlc/build"
