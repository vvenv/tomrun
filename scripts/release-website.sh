#!/usr/bin/env bash
# 构建并发布官网到 run.wenfu.dev（/api 仍走纪录榜，不被这次 rsync 碰到）。
#
# Usage:
#   bash scripts/release-website.sh
#   bash scripts/release-website.sh --skip-apk
#
# 凭证来自仓库根目录 .env.deploy（见 server/deploy.env.example）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

error() { echo "ERROR: $*" >&2; exit 1; }

SKIP_APK=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-apk) SKIP_APK=1; shift ;;
    --help|-h)
      sed -n '2,10p' "$0"
      exit 0
      ;;
    *) error "未知选项: $1" ;;
  esac
done

if [ -f "$ROOT/.env.deploy" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env.deploy"
  set +a
fi

HOST="${DEPLOY_HOST:?DEPLOY_HOST not set — add it to .env.deploy}"
USER="${DEPLOY_SSH_USER:-root}"
PORT="${DEPLOY_SSH_PORT:-22}"
DOMAIN="${LEADERBOARD_DOMAIN:-run.wenfu.dev}"
WEB_ROOT="${WEBSITE_REMOTE_DIR:-/var/www/${DOMAIN}}"
PUBLIC_HOST="https://${DOMAIN}"

if [[ -z "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  error "DEPLOY_SSH_PASSWORD is required"
fi

SSH="sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=accept-new -p ${PORT} ${USER}@${HOST}"
SCP="sshpass -p ${DEPLOY_SSH_PASSWORD} scp -o StrictHostKeyChecking=accept-new -P ${PORT}"

echo "▶ Releasing website → ${USER}@${HOST}:${WEB_ROOT}"
echo ""

echo "▶ [1/4] Building website..."
(
  cd "$ROOT/website"
  if [ ! -d node_modules ]; then
    pnpm install
  fi
  pnpm build
)

echo "▶ [2/4] Sync dist/ (keep remote downloads/)"
${SSH} "mkdir -p ${WEB_ROOT}/downloads"
rsync -avz --delete --exclude=downloads --exclude=version.json \
  -e "sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=accept-new -p ${PORT}" \
  "$ROOT/website/dist/" "${USER}@${HOST}:${WEB_ROOT}/"

if [ "$SKIP_APK" -eq 0 ]; then
  # 只发正式签名的 release 包：debug 签名一旦流出去，换签名时老用户只能卸载重装、丢进度
  [ -f "$ROOT/keystore.properties" ] || error "缺少 keystore.properties，无法打正式签名包（或加 --skip-apk）"
  echo "▶ [3/4] Building signed release APK..."
  JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    "$ROOT/gradlew" -p "$ROOT" -q assembleRelease
  APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
  [ -f "$APK" ] || error "没有生成 $APK"
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" 2>/dev/null)"
  APKSIGNER="$(ls -d "${SDK_DIR:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
  [ -n "$APKSIGNER" ] || error "找不到 apksigner，无法确认签名"
  if "$APKSIGNER" verify --print-certs "$APK" | grep -q "CN=Android Debug"; then
    error "$APK 是 debug 签名，拒绝上传"
  fi
  echo "  Upload APK → ${WEB_ROOT}/downloads/tomrun-1.0.apk"
  ${SCP} "$APK" "${USER}@${HOST}:${WEB_ROOT}/downloads/tomrun-1.0.apk"
  # APK 传完再更新 version.json，免得游戏提示了新版、下载到的却还是旧包
  VERSION_CODE="$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' "$ROOT/app/build.gradle.kts")"
  VERSION_NAME="$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' "$ROOT/app/build.gradle.kts")"
  VERSION_JSON="$(mktemp)"
  chmod 644 "$VERSION_JSON"  # mktemp 是 600，scp 会带过去，nginx 读不到就 403
  printf '{"versionCode":%s,"versionName":"%s"}\n' "$VERSION_CODE" "$VERSION_NAME" > "$VERSION_JSON"
  echo "  version.json → v${VERSION_NAME} (${VERSION_CODE})"
  ${SCP} "$VERSION_JSON" "${USER}@${HOST}:${WEB_ROOT}/version.json"
  rm -f "$VERSION_JSON"
else
  echo "▶ [3/4] --skip-apk"
fi

echo "▶ [4/4] Refresh nginx site config"
${SCP} "$ROOT/server/nginx/run.wenfu.dev" "${USER}@${HOST}:/etc/nginx/sites-available/${DOMAIN}"
${SSH} "ln -sf /etc/nginx/sites-available/${DOMAIN} /etc/nginx/sites-enabled/${DOMAIN} && nginx -t && systemctl reload nginx"

echo ""
echo "✓ Website deployed"
echo "  Site: ${PUBLIC_HOST}"
echo "  API:  ${PUBLIC_HOST}/api/v1/leaderboard/run_distance?limit=1"
