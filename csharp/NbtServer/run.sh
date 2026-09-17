#!/usr/bin/env bash
# Linux 启动脚本：
#   - 若存在 publish/NbtServer.dll（先执行过 dotnet publish -o publish），直接运行发布产物
#   - 否则用 dotnet run 以 Release 运行（需要 .NET 10 SDK；只需运行时的话请先 publish）
# 用法：
#   ./run.sh                        # 使用 appsettings.json 中的端口（默认 8080）
#   ./run.sh --urls=http://0.0.0.0:9090   # 临时覆盖端口
set -e
cd "$(dirname "$0")"

if [ -f publish/NbtServer.dll ]; then
    exec dotnet publish/NbtServer.dll "$@"
else
    exec dotnet run -c Release -- "$@"
fi
