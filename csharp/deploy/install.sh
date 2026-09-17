#!/usr/bin/env bash
# 一键部署 NBT 服务器到 Linux：
#   发布 + 安装 systemd 服务（后台运行、开机自启、崩溃自动重启）+ 安装 nbtserver 管理面板命令
# 用法：
#   sudo ./deploy/install.sh              # 安装到 /opt/nbtserver
#   sudo ./deploy/install.sh /srv/nbt     # 安装到自定义目录
# 安装完成后，任意目录输入 nbtserver 即可打开命令行管理面板。
set -e

DEST="${1:-/opt/nbtserver}"
DOTNET="$(command -v dotnet || echo /usr/bin/dotnet)"
cd "$(dirname "$0")/.."

if [ "$(id -u)" -ne 0 ]; then
    echo "请用 sudo 运行（需要写入 $DEST、/usr/local/bin 和 systemd）" >&2
    exit 1
fi

echo "==> 发布 Release 产物到 $DEST ..."
dotnet publish NbtServer/NbtServer.csproj -c Release -o "$DEST"

echo "==> 安装 systemd 服务（dotnet: $DOTNET）..."
sed -e "s#/opt/nbtserver#$DEST#g" -e "s#/usr/bin/dotnet#$DOTNET#g" \
    deploy/nbtserver.service > /etc/systemd/system/nbtserver.service
systemctl daemon-reload
systemctl enable --now nbtserver

echo "==> 安装管理面板命令 nbtserver 到 /usr/local/bin ..."
sed "s#^INSTALL_DIR=.*#INSTALL_DIR=$DEST#" deploy/nbtserver.sh > /usr/local/bin/nbtserver
chmod +x /usr/local/bin/nbtserver

echo "==> 完成！当前状态："
systemctl --no-pager --lines 5 status nbtserver || true
echo
echo "常用命令："
echo "  管理面板:  nbtserver                    （启动/关闭/重启/开关开机自启）"
echo "  命令模式:  sudo nbtserver start|stop|restart|enable|disable|status"
echo "  查看状态:  systemctl status nbtserver"
echo "  跟踪日志:  journalctl -u nbtserver -f"
echo "  改配置后:  sudo nbtserver restart       （配置在 $DEST/appsettings.json）"
