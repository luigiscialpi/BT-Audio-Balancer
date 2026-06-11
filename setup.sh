#!/bin/bash
# ============================================================
# setup.sh — Bootstrap BtAudioBalancer su macOS
# Esegui una volta sola dalla root del progetto:
#   chmod +x setup.sh && ./setup.sh
# ============================================================

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== BtAudioBalancer — Setup ==="

# ── 1. Verifica Java ──────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo "❌ Java non trovato. Installalo con:"
    echo "   brew install --cask temurin@17"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
echo "✅ Java $JAVA_VER trovato"

# ── 2. Trova SDK Android ──────────────────────────────────────────────────────
# Prova percorsi comuni su macOS
SDK_CANDIDATES=(
    "$HOME/Library/Android/sdk"
    "$ANDROID_HOME"
    "$ANDROID_SDK_ROOT"
)
SDK_DIR=""
for candidate in "${SDK_CANDIDATES[@]}"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then
        SDK_DIR="$candidate"
        break
    fi
done

if [ -z "$SDK_DIR" ]; then
    echo "❌ Android SDK non trovato."
    echo "   Percorso atteso: ~/Library/Android/sdk"
    echo "   Inserisci il percorso manualmente:"
    read -r SDK_DIR
fi
echo "✅ Android SDK: $SDK_DIR"

# Aggiorna local.properties con il percorso reale
echo "sdk.dir=$SDK_DIR" > local.properties
echo "✅ local.properties aggiornato"

# ── 3. Scarica gradle-wrapper.jar ────────────────────────────────────────────
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "⬇️  Scarico gradle-wrapper.jar..."
    # Scarica da GitHub (sorgente ufficiale Gradle)
    curl -fsSL \
        "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar" \
        -o "$WRAPPER_JAR"
    echo "✅ gradle-wrapper.jar scaricato"
else
    echo "✅ gradle-wrapper.jar già presente"
fi

# ── 4. Rendi gradlew eseguibile ───────────────────────────────────────────────
chmod +x gradlew
echo "✅ gradlew eseguibile"

# ── 5. Verifica build tools disponibili ───────────────────────────────────────
BUILD_TOOLS_DIR="$SDK_DIR/build-tools"
if [ -d "$BUILD_TOOLS_DIR" ]; then
    LATEST_BT=$(ls "$BUILD_TOOLS_DIR" | sort -V | tail -1)
    echo "✅ Build Tools: $LATEST_BT"
else
    echo "⚠️  Build Tools non trovati. Installali da Android Studio:"
    echo "   SDK Manager → SDK Tools → Android SDK Build-Tools"
fi

echo ""
echo "=== Setup completato! ==="
echo ""
echo "Comandi disponibili:"
echo "  ./gradlew assembleDebug          → compila APK debug"
echo "  ./gradlew assembleRelease        → compila APK release"
echo "  ./gradlew installDebug           → installa su device connesso via ADB"
echo ""
echo "L'APK debug si trova in:"
echo "  app/build/outputs/apk/debug/app-debug.apk"
