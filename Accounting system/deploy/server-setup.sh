#!/usr/bin/env bash
# ============================================================
# smart-ledger 服务器初始化脚本（OpenCloudOS / CentOS / RHEL 系）
#
# 用法（root）：
#   sudo bash server-setup.sh
#
# 做的事：装 JDK17 / MySQL8 / Nginx，建运行用户与目录，
#        注册 systemd 服务，放行防火墙 80/443。
# 不做的事：不建库、不写密钥——见脚本末尾提示。
#
# 前置：jar 已上传到 /opt/smart-ledger/，本脚本与 smart-ledger.service
#       / smart-ledger.env.example 已上传到同一目录。
# ============================================================
set -euo pipefail

DOMAIN="api.gmenggmeng.cn"
APP_DIR="/opt/smart-ledger"
JAR="$APP_DIR/smart-ledger-backend-1.0.0.jar"
ENV_FILE="/etc/smart-ledger/env"
UNIT_SRC="$(dirname "$0")/smart-ledger.service"

echo "==> 1/6 安装依赖"
if command -v dnf >/dev/null 2>&1; then
  PKG=dnf
else
  PKG=yum
fi
$PKG install -y java-17-openjdk-headless nginx mysql-server || true

echo "==> 2/6 启动 MySQL / Nginx"
systemctl enable --now mysqld 2>/dev/null || systemctl enable --now mysql
systemctl enable --now nginx

echo "==> 3/6 创建运行用户与目录"
id -u smartledger >/dev/null 2>&1 || useradd -r -s /sbin/nologin smartledger
mkdir -p "$APP_DIR/uploads" /etc/smart-ledger /etc/nginx/ssl
chown -R smartledger:smartledger "$APP_DIR"
chmod 755 "$APP_DIR"/uploads

echo "==> 4/6 检查 jar 与密钥文件"
[ -f "$JAR" ] || { echo "!! 缺少 $JAR，请先上传打包产物"; exit 1; }
if [ ! -f "$ENV_FILE" ]; then
  echo "!! 缺少 $ENV_FILE，请把 smart-ledger.env.example 复制过去并填写后重跑本脚本"
  exit 1
fi
chmod 600 "$ENV_FILE"

echo "==> 5/6 注册 systemd 服务"
if [ -f "$UNIT_SRC" ]; then
  cp "$UNIT_SRC" /etc/systemd/system/smart-ledger.service
else
  echo "!! 未找到 smart-ledger.service，跳过注册"
fi
systemctl daemon-reload
systemctl enable smart-ledger

echo "==> 6/6 放行防火墙端口"
if systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-service=http >/dev/null
  firewall-cmd --permanent --add-service=https >/dev/null
  firewall-cmd --reload
fi

cat <<EOF

完成。还需要手工做三件事：
  1) 腾讯云控制台 → 安全组，放行 80 / 443（当前从外网探测 80/443 不通）
  2) 建库并导入数据：
       mysql -uroot -p -e "CREATE DATABASE smart_ledger DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
       mysql -uroot -p -e "CREATE USER 'smart_ledger'@'localhost' IDENTIFIED BY '强密码'; GRANT ALL ON smart_ledger.* TO 'smart_ledger'@'localhost'; FLUSH PRIVILEGES;"
       mysql -uroot -p smart_ledger < schema.sql
       mysql -uroot -p smart_ledger < data.sql
  3) 证书放到 /etc/nginx/ssl/$DOMAIN.pem 与 .key，
     然后 cp api.gmenggmeng.cn.conf /etc/nginx/conf.d/ && nginx -t && systemctl reload nginx

启动后端：systemctl start smart-ledger && journalctl -u smart-ledger -f
EOF
