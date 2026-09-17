#!/usr/bin/env bash
# 构建离线部署包（在【有网络】的电脑上运行，需要 .NET 10 SDK；Windows Git Bash / Linux / macOS 均可）
# 产物为自包含发布：.NET 运行时已打进包里，目标服务器【无需外网、无需安装 .NET】。
# 用法：
#   ./deploy/make-offline-package.sh               # 默认 linux-x64
#   ./deploy/make-offline-package.sh linux-arm64   # ARM 服务器（如树莓派/国产 ARM 云主机）
# 生成：csharp/nbtserver-<RID>-offline.tar.gz
# 传输到服务器后：
#   tar xzf nbtserver-<RID>-offline.tar.gz && cd nbtserver-<RID>-offline && sudo bash install-offline.sh
set -e

RID="${1:-linux-x64}"
cd "$(dirname "$0")/.."

OUT="nbtserver-$RID-offline"
echo "==> 自包含发布（$RID，含 .NET 运行时，首次需下载运行时包）..."
rm -rf "$OUT" "$OUT.tar.gz"
mkdir -p "$OUT"
dotnet publish NbtServer/NbtServer.csproj -c Release -r "$RID" --self-contained \
    -p:InvariantGlobalization=true -o "$OUT/app"

echo "==> 打入离线安装脚本与配置 ..."
cp NbtServer/appsettings.json deploy/install-offline.sh deploy/nbtserver.sh deploy/nbtserver-offline.service "$OUT/"

echo "==> 压缩为 $OUT.tar.gz ..."
tar -czf "$OUT.tar.gz" "$OUT"

SIZE=$(du -h "$OUT.tar.gz" | cut -f1)
echo "==> 完成：csharp/$OUT.tar.gz（$SIZE）"
echo "    传到服务器（scp / WinSCP / U盘），然后：tar xzf $OUT.tar.gz && cd $OUT && sudo bash install-offline.sh"
