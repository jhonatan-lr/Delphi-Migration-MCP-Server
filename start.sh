#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# start.sh — Delphi Migration MCP Server
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/delphi-migration-mcp-1.0.0.jar"
JAVA_MIN_VERSION=17

# ── Verifica Java ─────────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo "❌ Java não encontrado. Instale Java $JAVA_MIN_VERSION+ e tente novamente."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "${JAVA_VERSION:-0}" -lt "$JAVA_MIN_VERSION" ]; then
    echo "❌ Java $JAVA_MIN_VERSION+ necessário. Versão atual: $JAVA_VERSION"
    exit 1
fi

# ── Build se necessário ───────────────────────────────────────────────────────
if [ ! -f "$JAR" ]; then
    echo "📦 JAR não encontrado. Fazendo build com Maven..."
    if ! command -v mvn &>/dev/null; then
        echo "❌ Maven não encontrado. Instale Maven 3.8+ e tente novamente."
        exit 1
    fi
    cd "$SCRIPT_DIR"
    mvn clean package -q -DskipTests
    echo "✅ Build concluído."
fi

# ── Inicia o servidor ─────────────────────────────────────────────────────────
echo "🚀 Iniciando Delphi Migration MCP Server..."
echo "   JAR: $JAR"
echo "   Perfil salvo em: $HOME/.delphi-mcp/project-profile.json"
echo "   Logs em: $SCRIPT_DIR/logs/delphi-mcp.log"
echo ""

exec java \
    -Xmx512m \
    -Xms128m \
    -Dfile.encoding=UTF-8 \
    -jar "$JAR" "$@"
