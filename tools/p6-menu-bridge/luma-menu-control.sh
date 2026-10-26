#!/system/bin/sh
# Shared device-side lifecycle used by the Windows helper. No firmware changes.
set -eu
bridge=/data/local/tmp/luma-menu-bridge.sh
pidfile=/data/local/tmp/luma-menu-bridge.pid

running() {
    [ -f "$pidfile" ] || return 1
    bridge_pid=$(cat "$pidfile")
    case "$bridge_pid" in ''|*[!0-9]*) return 1 ;; esac
    [ -r "/proc/$bridge_pid/cmdline" ] || return 1
    tr '\000' ' ' <"/proc/$bridge_pid/cmdline" | grep -q '/data/local/tmp/luma-menu-bridge.sh'
}

case "${1:-}" in
    start)
        if running; then echo running; exit 0; fi
        [ "$(id -u)" = 0 ] || { echo 'Root-capable ADB is required' >&2; exit 1; }
        [ -f "$bridge" ] || { echo 'Bridge script is missing' >&2; exit 1; }
        grep -q 'Name="sunxi-ir-uinput"' /proc/bus/input/devices || {
            echo 'The supported P6 IR input device was not found' >&2; exit 1;
        }
        chmod 700 "$bridge"
        nohup /system/bin/sh "$bridge" >/data/local/tmp/luma-menu-stderr.log 2>&1 </dev/null &
        sleep 1
        running || { echo 'Bridge failed to start' >&2; exit 1; }
        echo running
        ;;
    stop)
        if running; then
            pkill -P "$bridge_pid" 2>/dev/null || true
            kill "$bridge_pid" 2>/dev/null || true
        fi
        rm -f "$pidfile"
        echo stopped
        ;;
    check)
        if running; then echo running; else echo stopped; fi
        ;;
    status)
        if running; then
            echo "running pid=$bridge_pid"
            tail -5 /data/local/tmp/luma-menu-bridge.log 2>/dev/null || true
        else echo stopped; fi
        ;;
    *) echo 'Usage: luma-menu-control.sh {start|stop|check|status}' >&2; exit 2 ;;
esac
