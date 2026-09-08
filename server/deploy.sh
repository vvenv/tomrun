#!/usr/bin/env bash
# 部署纪录榜 API，并刷新 run.edao.plus Nginx（静态官网 + /api 反代）。
# 官网产物请用 scripts/release-website.sh 单独同步，本脚本不会清空 /var/www。
set -euo pipefail

HOST="${DEPLOY_HOST:?set DEPLOY_HOST}"
USER="${DEPLOY_SSH_USER:-root}"
PORT="${DEPLOY_SSH_PORT:-22}"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-/opt/tomrun}"
SERVICE="${DEPLOY_SERVICE:-tomrun-leaderboard}"
DOMAIN="${LEADERBOARD_DOMAIN:-run.edao.plus}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"
PUBLIC_URL="${LEADERBOARD_PUBLIC_URL:-https://${DOMAIN}}"

if [[ -z "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  echo "DEPLOY_SSH_PASSWORD is required" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SSH="sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=accept-new -p ${PORT} ${USER}@${HOST}"
SCP="sshpass -p ${DEPLOY_SSH_PASSWORD} scp -o StrictHostKeyChecking=accept-new -P ${PORT}"

echo "→ Upload app to ${USER}@${HOST}:${REMOTE_DIR}"
${SSH} "mkdir -p ${REMOTE_DIR} /var/www/certbot"
${SCP} "${ROOT}/server/leaderboard_server.py" "${USER}@${HOST}:${REMOTE_DIR}/"
${SCP} "${ROOT}/server/tomrun-leaderboard.service" "${USER}@${HOST}:/etc/systemd/system/${SERVICE}.service"

echo "→ Restart systemd service"
${SSH} bash <<EOF
set -e
systemctl daemon-reload
systemctl enable ${SERVICE}
systemctl restart ${SERVICE}
sleep 1
systemctl is-active ${SERVICE}
curl -sf "http://127.0.0.1:8787/api/v1/leaderboard/run_distance?limit=1" >/dev/null
echo "leaderboard local OK"
EOF

echo "→ Install Nginx (${DOMAIN})"
${SCP} "${ROOT}/server/nginx/run.edao.plus.http-only" "${USER}@${HOST}:/etc/nginx/sites-available/${DOMAIN}"
${SSH} bash <<EOF
set -e
ln -sf /etc/nginx/sites-available/${DOMAIN} /etc/nginx/sites-enabled/${DOMAIN}
nginx -t
systemctl reload nginx
EOF

echo "→ Try Let's Encrypt for ${DOMAIN}"
CERT_ARGS=(certbot certonly --webroot -w /var/www/certbot -d "${DOMAIN}" --non-interactive --agree-tos)
if [[ -n "${CERTBOT_EMAIL}" ]]; then
  CERT_ARGS+=(--email "${CERTBOT_EMAIL}")
else
  CERT_ARGS+=(--register-unsafely-without-email)
fi

PUBLIC_URL="http://${DOMAIN}"
if ${SSH} "${CERT_ARGS[*]}"; then
  echo "→ Enable HTTPS nginx config"
  ${SCP} "${ROOT}/server/nginx/run.edao.plus" "${USER}@${HOST}:/etc/nginx/sites-available/${DOMAIN}"
  ${SSH} "nginx -t && systemctl reload nginx"
  PUBLIC_URL="https://${DOMAIN}"
else
  echo "⚠ Certbot failed — 请先在 DNSPod 添加 A 记录: ${DOMAIN} → ${HOST}"
fi

echo "→ Smoke test via Host header"
${SSH} bash <<EOF
curl -sf -H "Host: ${DOMAIN}" "http://127.0.0.1/api/v1/leaderboard/run_distance?limit=1" >/dev/null && echo "nginx proxy OK"
EOF

echo "Done."
echo "  Public API: ${PUBLIC_URL}"
echo "  App config: LEADERBOARD_API_BASE=${PUBLIC_URL}"
