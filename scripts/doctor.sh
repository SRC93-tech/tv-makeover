#!/usr/bin/env bash
# Check this computer has what the skills and scripts need, and say how to get what is missing.
# Usage: scripts/doctor.sh
ok() { printf '  ok       %s\n' "$1"; }
miss() { printf '  MISSING  %s  -> %s\n' "$1" "$2"; FAIL=1; }
FAIL=0
echo "Needed for everything:"
command -v python3 >/dev/null && ok "python3 $(python3 -V 2>&1 | cut -d' ' -f2)" || miss python3 "https://www.python.org or brew install python"
command -v git >/dev/null && ok "git" || miss git "xcode-select --install / apt install git"
command -v nc >/dev/null && ok "nc" || miss nc "apt install netcat-openbsd"
echo "Needed to build the launcher:"
JH="${JAVA_HOME:-}"; [ -z "$JH" ] && [ -x /usr/libexec/java_home ] && JH="$(/usr/libexec/java_home -v 17+ 2>/dev/null)"
JV=$({ [ -n "$JH" ] && "$JH/bin/java" -version 2>&1 || java -version 2>&1; } | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)
[ -n "$JV" ] && [ "$JV" -ge 17 ] && ok "JDK $JV" || miss "JDK 17+" "brew install --cask temurin@17 / apt install openjdk-17-jdk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; [ -d "$SDK" ] || SDK="$HOME/Android/Sdk"
[ -d "$SDK/platforms/android-34" ] && ok "Android SDK platform 34 ($SDK)" || miss "Android SDK platform 34" "Android Studio > SDK Manager, or sdkmanager 'platforms;android-34' 'build-tools;34.0.0'"
echo "Needed for UI sounds:"
command -v ffmpeg >/dev/null && ok "ffmpeg" || miss ffmpeg "brew install ffmpeg / apt install ffmpeg"
[ "$FAIL" = 0 ] && echo "All set." || echo "Install what is missing, then run this again."
