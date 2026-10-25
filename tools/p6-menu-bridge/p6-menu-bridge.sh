#!/bin/sh
# Start or stop the optional P6 root input bridge. Run from a Mac with adb.
set -eu
adb_bin=${ADB_BIN:-adb}
p6_serial=${P6_SERIAL:?Set P6_SERIAL to your projector IP:port, for example 192.168.1.50:5555}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

case ${1:-} in
    start)
        "$adb_bin" -s "$p6_serial" root
        "$adb_bin" -s "$p6_serial" wait-for-device
        "$adb_bin" -s "$p6_serial" shell '
            if [ -f /data/local/tmp/luma-menu-bridge.pid ]; then
                old=$(cat /data/local/tmp/luma-menu-bridge.pid)
                if [ -r /proc/"$old"/cmdline ] &&
                    tr "\000" " " </proc/"$old"/cmdline | grep -q luma-menu-bridge.sh; then
                    pkill -P "$old" 2>/dev/null || true
                    kill "$old" 2>/dev/null || true
                fi
            fi'
        "$adb_bin" -s "$p6_serial" push "$script_dir/luma-menu-bridge.sh" /data/local/tmp/luma-menu-bridge.sh
        "$adb_bin" -s "$p6_serial" shell '
            chmod 700 /data/local/tmp/luma-menu-bridge.sh
            /system/bin/sh /data/local/tmp/luma-menu-bridge.sh \
                >/data/local/tmp/luma-menu-stderr.log 2>&1 </dev/null &'
        ;;
    stop)
        "$adb_bin" -s "$p6_serial" shell '
            if [ -f /data/local/tmp/luma-menu-bridge.pid ]; then
                old=$(cat /data/local/tmp/luma-menu-bridge.pid)
                if [ -r /proc/"$old"/cmdline ] &&
                    tr "\000" " " </proc/"$old"/cmdline | grep -q luma-menu-bridge.sh; then
                    pkill -P "$old" 2>/dev/null || true
                    kill "$old" 2>/dev/null || true
                fi
            fi'
        ;;
    status)
        "$adb_bin" -s "$p6_serial" shell '
            if [ -f /data/local/tmp/luma-menu-bridge.pid ]; then
                old=$(cat /data/local/tmp/luma-menu-bridge.pid)
                ps -A -o PID,PPID,NAME | grep " $old " || true
                tail -5 /data/local/tmp/luma-menu-bridge.log
            else
                echo "Menu bridge is not running"
            fi'
        ;;
    *)
        echo "Usage: $0 {start|stop|status}" >&2
        exit 2 ;;
esac
