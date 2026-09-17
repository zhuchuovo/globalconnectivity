#!/usr/bin/env bash
# NBT 结构文件服务器 · 命令行管理面板
# 由 deploy/install.sh 一键安装到 /usr/local/bin/nbtserver，之后任意目录输入 nbtserver 即可打开。
# 手动安装：
#   sudo cp deploy/nbtserver.sh /usr/local/bin/nbtserver && sudo chmod +x /usr/local/bin/nbtserver
# 支持面板模式（不带参数）和命令模式：nbtserver start|stop|restart|enable|disable|status

SERVICE=nbtserver
INSTALL_DIR=/opt/nbtserver   # install.sh 安装时会自动替换为实际目录

# systemctl 需要 root，普通用户运行时自动提权
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        exec sudo "$0" "$@"
    else
        echo "需要 root 权限，请用 sudo 运行" >&2
        exit 1
    fi
fi

if [ ! -f /etc/systemd/system/$SERVICE.service ]; then
    echo "尚未安装 $SERVICE 服务。请先在仓库 csharp 目录执行：sudo ./deploy/install.sh"
    exit 1
fi

green() { printf '\033[32m%s\033[0m\n' "$1"; }
red()   { printf '\033[31m%s\033[0m\n' "$1"; }

listen_url() {
    grep -oP '"Urls"\s*:\s*"\K[^"]+' "$INSTALL_DIR/appsettings.json" 2>/dev/null | head -1
}

show_status() {
    if systemctl is-active --quiet "$SERVICE"; then
        green "  服务状态： ● 运行中"
    else
        red   "  服务状态： ○ 已停止"
    fi
    if systemctl is-enabled --quiet "$SERVICE" 2>/dev/null; then
        echo  "  开机自启： 已开启"
    else
        echo  "  开机自启： 已关闭"
    fi
    local u
    u=$(listen_url)
    [ -n "$u" ] && echo "  监听地址： $u"
    echo "  配置文件： $INSTALL_DIR/appsettings.json（修改后需重启生效）"
    echo "  查看日志： journalctl -u $SERVICE -f"
}

do_action() {
    case "$1" in
        start)   systemctl start   "$SERVICE" && green "✔ 已启动服务器"   || red "✘ 启动失败，用 journalctl -u $SERVICE -n 20 排查" ;;
        stop)    systemctl stop    "$SERVICE" && green "✔ 已关闭服务器"   || red "✘ 停止失败" ;;
        restart) systemctl restart "$SERVICE" && green "✔ 已重启服务器"   || red "✘ 重启失败，用 journalctl -u $SERVICE -n 20 排查" ;;
        enable)  systemctl enable  "$SERVICE" && green "✔ 已设置开机自启动" || red "✘ 设置失败" ;;
        disable) systemctl disable "$SERVICE" && green "✔ 已关闭开机自启动" || red "✘ 设置失败" ;;
        status)  ;;
        *) return 2 ;;
    esac
}

pause_menu() {
    printf '\n按回车键返回菜单...'
    read -r
}

# 命令模式：nbtserver start / stop / restart / enable / disable / status
if [ -n "$1" ]; then
    if ! do_action "$1"; then
        echo "用法：nbtserver [start|stop|restart|enable|disable|status]（不带参数打开管理面板）"
        exit 1
    fi
    show_status
    exit 0
fi

# 面板模式
while true; do
    command -v clear >/dev/null 2>&1 && clear
    echo "════════════════════════════════════════════"
    echo "        NBT 结构文件服务器 · 管理面板"
    echo "════════════════════════════════════════════"
    show_status
    echo "────────────────────────────────────────────"
    echo "    1. 启动服务器"
    echo "    2. 关闭服务器"
    echo "    3. 重启服务器"
    echo "    4. 设置开机自启动"
    echo "    5. 关闭开机自启动"
    echo "    0. 退出面板"
    echo "────────────────────────────────────────────"
    read -rp "请输入编号并回车: " choice
    case "$choice" in
        1) do_action start;   pause_menu ;;
        2) do_action stop;    pause_menu ;;
        3) do_action restart; pause_menu ;;
        4) do_action enable;  pause_menu ;;
        5) do_action disable; pause_menu ;;
        0|q|Q) exit 0 ;;
        *) ;;
    esac
done
