#!/usr/bin/env bash
set -euo pipefail

PKG="com.devyk.av.rtmppush"
ACT="com.devyk.av.rtmppush/.LiveActivity"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTDIR="diagnostics"
mkdir -p "$OUTDIR"
LOGFILE="$OUTDIR/camera-startup-$TIMESTAMP.log"
WAIT_SECS=${1:-8}

echo "[diag] clearing logcat" && adb logcat -c || true
echo "[diag] force-stop $PKG" && adb shell am force-stop "$PKG" || true
echo "[diag] start $ACT" && adb shell am start -n "$ACT" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null

# wait a bit for startup and camera open
echo "[diag] wait ${WAIT_SECS}s for preview setup"
sleep "$WAIT_SECS"

echo "[diag] capturing focused logs to $LOGFILE"
adb logcat -d -v time \
  | grep -E "(Live(Activity|SessionCoordinator)|Camera(View|Holder|Utils|Renderer)|GLSurfaceView|GLThread|EglHelper|SurfaceTexture|CameraService|bindTextureImage|BufferQueueProducer)" \
  | tee "$LOGFILE"

echo "[diag] saved to $LOGFILE"
