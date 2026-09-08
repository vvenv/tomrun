#!/usr/bin/env bash
# 构建并发布官网到 run.edao.plus（/api 仍走纪录榜，不被这次 rsync 碰到）。
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
DOMAIN="${LEADERBOARD_DOMAIN:-run.edao.plus}"
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
rsync -avz --delete --exclude=downloads \
  -e "sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=accept-new -p ${PORT}" \
  "$ROOT/website/dist/" "${USER}@${HOST}:${WEB_ROOT}/"

if [ "$SKIP_APK" -eq 0 ]; then
  APK=""
  if [ -f "$ROOT/app/build/outputs/apk/release/app-release.apk" ]; then
    APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
  elif [ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  fi
  if [ -n "$APK" ]; then
    echo "▶ [3/4] Upload APK → ${WEB_ROOT}/downloads/tomrun-1.0.apk"
    ${SCP} "$APK" "${USER}@${HOST}:${WEB_ROOT}/downloads/tomrun-1.0.apk"
  else
    echo "▶ [3/4] No APK found — skip (build assembleRelease first)"
  fi
else
  echo "▶ [3/4] --skip-apk"
fi

echo "▶ [4/4] Refresh nginx site config"
${SCP} "$ROOT/server/nginx/run.edao.plus" "${USER}@${HOST}:/etc/nginx/sites-available/${DOMAIN}"
${SSH} "ln -sf /etc/nginx/sites-available/${DOMAIN} /etc/nginx/sites-enabled/${DOMAIN} && nginx -t && systemctl reload nginx"

echo ""
echo "✓ Website deployed"
echo "  Site: ${PUBLIC_HOST}"
echo "  API:  ${PUBLIC_HOST}/api/v1/leaderboard/run_distance?limit=1"
