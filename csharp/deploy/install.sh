#!/usr/bin/env bash
# 一键部署 NBT 服务器到 Linux：发布 + 安装 systemd 服务（后台运行、开机自启、崩溃自动重启）
# 用法：
#   sudo ./deploy/install.sh              # 安装到 /opt/nbtserver
#   sudo ./deploy/install.sh /srv/nbt     # 安装到自定义目录
set -e

DEST="${1:-/opt/nbtserver}"
DOTNET="$(command -v dotnet || echo /usr/bin/dotnet)"
cd "$(dirname "$0")/.."

if [ "$(id -u)" -ne 0 ]; then
    echo "请用 sudo 运行（需要写入 $DEST 和 systemd）" >&2
    exit 1
fi

echo "==> 发布 Release 产物到 $DEST ..."
dotnet publish NbtServer/NbtServer.csproj -c Release -o "$DEST"

echo "==> 安装 systemd 服务（dotnet: $DOTNET）..."
sed -e "s#/opt/nbtserver#$DEST#g" -e "s#/usr/bin/dotnet#$DOTNET#g" \
    deploy/nbtserver.service > /etc/systemd/system/nbtserver.service
systemctl daemon-reload
systemctl enable --now nbtserver

echo "==> 完成！当前状态："
systemctl --no-pager --lines 5 status nbtserver || true
echo
echo "常用命令："
echo "  查看状态: systemctl status nbtserver"
echo "  跟踪日志: journalctl -u nbtserver -f"
echo "  重启服务: systemctl restart nbtserver   （改 appsettings.json 后执行）"
echo "  停止服务: systemctl stop nbtserver"
echo "  取消自启: systemctl disable nbtserver"
