#!/system/bin/sh
# P6 prototype: translate the original IR Menu key into Luma's global actions.
# Reads only the IR device; does not change Android key maps or the Home app.
state_dir=${LUMA_MENU_DIR:-/data/local/tmp}
echo $$ >"$state_dir/luma-menu-bridge.pid"
trap 'rm -f "$state_dir/luma-menu-bridge.pid"' EXIT
trap 'rm -f "$state_dir/luma-menu-bridge.pid"; exit 0' HUP INT TERM
find_ir_event() {
    awk '
        /^N: Name="sunxi-ir-uinput"/ { found=1; next }
        found && /^H: Handlers=/ {
            if (match($0, /event[0-9]+/)) {
                print "/dev/input/" substr($0, RSTART, RLENGTH); exit
            }
        }
        /^$/ { found=0 }
    ' /proc/bus/input/devices
}

listen() {
pressed=0
getevent -lt "$1" | while IFS= read -r event; do
    case "$event" in
        *KEY_KP0*DOWN*)
            if [ "$pressed" -eq 0 ]; then
                pressed=1
                started=$(date +%s%3N)
            fi
            continue ;;
        *KEY_KP0*UP*)
            [ "$pressed" -eq 1 ] || continue
            pressed=0
            duration=$(($(date +%s%3N) - started)) ;;
        *) continue ;;
    esac

    # Luma already handles Menu on its own Home screen. The separate global
    # Control Centre task is the sole Luma window that this bridge toggles.
    focus=$(dumpsys window 2>/dev/null | grep -m 1 mCurrentFocus)
    case "$focus" in
        *GlobalControlActivity*) ;;
        *dev.luma.launcher*)
            echo "$(date +%T) home_skipped duration_ms=$duration" >>"$state_dir/luma-menu-bridge.log"
            continue ;;
        *mCurrentFocus=null*|"")
            echo "$(date +%T) no_focus_skipped duration_ms=$duration" >>"$state_dir/luma-menu-bridge.log"
            continue ;;
    esac

    if [ "$duration" -ge 450 ]; then
        action=app_switcher
        case "$focus" in
            *GlobalControlActivity*)
                # Dismiss the separate translucent task before showing the
                # existing App Switcher in Luma's normal task.
                am start -n dev.luma.launcher/.GlobalControlActivity \
                    >>"$state_dir/luma-menu-action.log" 2>&1 ;;
        esac
        am start -n dev.luma.launcher/.LauncherActivity \
            -a dev.luma.launcher.GLOBAL_CONTROL --es target app_switcher \
            >>"$state_dir/luma-menu-action.log" 2>&1
    else
        action=control_centre
        source_package=$(printf '%s' "$focus" | sed -n 's/.*u[0-9][0-9]* \([^ /}]*\)\/.*/\1/p')
        am start -n dev.luma.launcher/.GlobalControlActivity \
            --es source_package "$source_package" \
            >>"$state_dir/luma-menu-action.log" 2>&1
    fi
    echo "$(date +%T) $action duration_ms=$duration" >>"$state_dir/luma-menu-bridge.log"
done
}

while :; do
    ir_event=$(find_ir_event)
    if [ -n "$ir_event" ] && [ -r "$ir_event" ]; then
        echo "$(date +%T) listening $ir_event" >>"$state_dir/luma-menu-bridge.log"
        listen "$ir_event"
    fi
    # Re-discover the event node if Android recreates it after sleep.
    sleep 1
done
