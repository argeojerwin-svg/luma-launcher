#!/bin/sh
# Optional Mac-side recovery loop. Restarts the P6's root Menu bridge after boot.
set -u
adb_bin=${ADB_BIN:-adb}
p6_serial=${P6_SERIAL:?Set P6_SERIAL to your projector IP:port, for example 192.168.1.50:5555}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

check_once() {
    "$adb_bin" connect "$p6_serial" >/dev/null 2>&1 || return 0
    state=$("$adb_bin" -s "$p6_serial" get-state 2>/dev/null) || state=
    if [ "$state" != device ]; then
        # A stale wireless ADB transport can stay "offline" indefinitely while
        # `adb connect` reports "already connected". Replace that transport.
        "$adb_bin" disconnect "$p6_serial" >/dev/null 2>&1 || true
        "$adb_bin" connect "$p6_serial" >/dev/null 2>&1 || return 0
        state=$("$adb_bin" -s "$p6_serial" get-state 2>/dev/null) || return 0
    fi
    [ "$state" = device ] || return 0

    uid=$("$adb_bin" -s "$p6_serial" shell id -u 2>/dev/null | tr -d '\r') || return 0
    if [ "$uid" != 0 ]; then
        "$adb_bin" -s "$p6_serial" root >/dev/null 2>&1 || return 0
        "$adb_bin" -s "$p6_serial" wait-for-device >/dev/null 2>&1 || return 0
    fi

    state=$("$adb_bin" -s "$p6_serial" shell '
        if [ -f /data/local/tmp/luma-menu-bridge.pid ]; then
            bridge_pid=$(cat /data/local/tmp/luma-menu-bridge.pid)
            if [ -r /proc/"$bridge_pid"/cmdline ] &&
                tr "\000" " " </proc/"$bridge_pid"/cmdline | grep -q luma-menu-bridge.sh; then
                echo running
            fi
        fi' 2>/dev/null) || return 0
    [ "$state" = running ] && return 0

    ADB_BIN="$adb_bin" P6_SERIAL="$p6_serial" \
        sh "$script_dir/p6-menu-bridge.sh" start
}

if [ "${1:-}" = --once ]; then
    check_once
else
    while :; do
        check_once
        sleep 20
    done
fi
