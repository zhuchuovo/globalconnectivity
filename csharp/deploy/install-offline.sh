#!/usr/bin/env bash
# 离线安装 NBT 服务器（服务器无需外网、无需安装 .NET）
# 包由 make-offline-package.sh 在有网的电脑上生成，本脚本位于解压后的包根目录。
# 用法：sudo bash install-offline.sh [安装目录]     # 默认 /opt/nbtserver
set -e

DEST="${1:-/opt/nbtserver}"
PKG="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" -ne 0 ]; then
    echo "请用 sudo bash install-offline.sh 运行" >&2
    exit 1
fi

if [ ! -f "$PKG/app/NbtServer" ]; then
    echo "包不完整：找不到 $PKG/app/NbtServer（请确认在解压后的目录里运行）" >&2
    exit 1
fi

echo "==> 复制自包含程序到 $DEST ..."
mkdir -p "$DEST"
cp -r "$PKG/app/." "$DEST/"
chmod +x "$DEST/NbtServer"

echo "==> 安装 systemd 服务（后台运行 + 开机自启 + 崩溃自动重启）..."
sed "s#/opt/nbtserver#$DEST#g" "$PKG/nbtserver-offline.service" > /etc/systemd/system/nbtserver.service
systemctl daemon-reload
systemctl enable --now nbtserver

echo "==> 安装管理面板命令 nbtserver ..."
sed "s#^INSTALL_DIR=.*#INSTALL_DIR=$DEST#" "$PKG/nbtserver.sh" > /usr/local/bin/nbtserver
chmod +x /usr/local/bin/nbtserver

echo "==> 完成！当前状态："
systemctl --no-pager --lines 5 status nbtserver || true
echo
echo "管理面板: nbtserver    命令模式: sudo nbtserver start|stop|restart|enable|disable|status"
echo "配置文件: $DEST/appsettings.json（改端口/密钥后 sudo nbtserver restart 生效）"
