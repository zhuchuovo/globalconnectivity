#!/usr/bin/env bash
# NBT 服务器一键拉取并启动（Linux）
# 一行命令（见 README）：
#   curl -sL https://raw.githubusercontent.com/zhuchuovo/globalconnectivity/main/csharp/deploy/quickstart.sh | sudo bash
# 流程：拉取仓库 -> 检测/安装 .NET 10 SDK -> 发布 + 安装 systemd 服务并立即启动 + 安装 nbtserver 管理面板
set -e

REPO=https://github.com/zhuchuovo/globalconnectivity
SRC=/tmp/globalconnectivity

if [ "$(id -u)" -ne 0 ]; then
    echo "请加 sudo 运行：curl -sL <脚本地址> | sudo bash" >&2
    exit 1
fi

echo "==> 1/3 拉取仓库 ..."
rm -rf "$SRC"
if command -v git >/dev/null 2>&1; then
    git clone --depth 1 "$REPO.git" "$SRC"
else
    curl -sL "$REPO/archive/refs/heads/main.tar.gz" | tar -xz -C /tmp
    mv /tmp/globalconnectivity-main "$SRC"
fi

echo "==> 2/3 检查 .NET 10 SDK ..."
export DOTNET_ROOT="$HOME/.dotnet"
export PATH="$DOTNET_ROOT:$PATH"
if ! command -v dotnet >/dev/null 2>&1 || ! dotnet --list-sdks 2>/dev/null | grep -q '^10\.'; then
    echo "    未检测到 .NET 10 SDK，安装到 $DOTNET_ROOT（不影响系统）..."
    curl -sSL https://dot.net/v1/dotnet-install.sh | bash -s -- --channel 10.0
fi

echo "==> 3/3 安装并启动服务 ..."
bash "$SRC/csharp/deploy/install.sh"

echo
echo "全部完成！输入 nbtserver 打开管理面板（启动/关闭/重启/开关开机自启）。"
