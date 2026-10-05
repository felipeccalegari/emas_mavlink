#!/usr/bin/env bash

# Records only the JaCaMo/Jason application JVM, including its MAVLink code.
# Same CPU/RSS format and log/jason_N.log naming as benchmark_mavlink.sh.
# Measures for 120 seconds after JaCaMo starts, sampling every 0.5 seconds.
# CPU is ps's lifetime average (% of one core); memory is RSS in MiB.
#
# Optional environment variables:
#   JASON_PID: attach to this JVM; otherwise reuse this project's running JVM
#              or launch it with Gradle. Attached applications remain running.
#   LOG_DIR: defaults to log/ relative to this script.

set -u
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd) || exit 1
cd -- "$SCRIPT_DIR" || exit 1

DURATION_SECONDS=120
SAMPLE_INTERVAL=0.5
LOG_DIR=${LOG_DIR:-log}
PID_JAS=${JASON_PID:-}
APP_PID=""
SAMPLER_PIDS=()
cleanup_done=0

validate_jason_pid() {
    if [[ ! "$PID_JAS" =~ ^[1-9][0-9]*$ ]]; then
        echo "JASON_PID must be one positive integer PID: $PID_JAS" >&2
        return 1
    fi
    if ! ps -p "$PID_JAS" -o pid=,rss= | awk 'NF == 2 {found=1} END {exit !found}'; then
        echo "Jason PID $PID_JAS is not running or its resources cannot be read." >&2
        return 1
    fi
}

find_jacamo_pid() {
    local candidate
    while read -r candidate; do
        if [[ -z "$APP_PID" ]]; then
            # Reuse this project's JVM, including one left by an earlier run.
            [[ "$(readlink "/proc/$candidate/cwd" 2>/dev/null)" == "$SCRIPT_DIR" ]] || continue
        else
            ps -eo pid=,ppid= | awk -v child="$candidate" -v root="$APP_PID" '
                { parent[$1]=$2 }
                END {
                    while (child in parent && child != root && child > 1)
                        child=parent[child]
                    exit (child != root)
                }' || continue
        fi
        echo "$candidate"
        return
    done < <(jps -l | awk '$2 == "jacamo.infra.JaCaMoLauncher" {print $1}')
}

start_sampler() {
    local pid_list="$1" logfile="$2"
    (
        # ps/awk output uses decimal points. Bash printf otherwise expects a
        # decimal comma under pt_BR, rejects the fractional part and emits an
        # error for every sample. Keep the numeric pipeline in one locale.
        export LC_ALL=C
        count=1
        while true; do
            read -r cpu mem < <(
                ps -p "$pid_list" -o %cpu=,rss= 2>/dev/null |
                    awk 'BEGIN {cpu=0; mem=0}
                         NF>=2 {cpu+=$1; mem+=$2}
                         END {printf "%.2f %.2f\n",cpu,mem/1024}'
            )
            printf 'Sample %d - CPU: %6.2f - MEM: %6.2f\n' \
                "$count" "${cpu:-0.00}" "${mem:-0.00}" >> "$logfile"
            ((count++))
            sleep "$SAMPLE_INTERVAL"
        done
    ) &
    SAMPLER_PIDS+=("$!")
}

cleanup() {
    local pid attempt
    [[ "$cleanup_done" -eq 1 ]] && return
    cleanup_done=1
    if [[ "${#SAMPLER_PIDS[@]}" -gt 0 ]]; then
        kill "${SAMPLER_PIDS[@]}" 2>/dev/null || true
        for pid in "${SAMPLER_PIDS[@]}"; do
            wait "$pid" 2>/dev/null || true
        done
    fi
    if [[ -n "$APP_PID" ]]; then
        touch .stop___MAS
        # Give JaCaMo time to stop, then stop only our isolated application group.
        for ((attempt=0; attempt<3; attempt++)); do
            kill -0 -- "-$APP_PID" 2>/dev/null || break
            sleep 1
        done
        if kill -0 -- "-$APP_PID" 2>/dev/null; then
            kill -TERM -- "-$APP_PID" 2>/dev/null || true
            sleep 1
            kill -KILL -- "-$APP_PID" 2>/dev/null || true
        fi
        wait "$APP_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'echo "Benchmark interrupted."; exit 130' INT
trap 'echo "Benchmark terminated."; exit 143' TERM

mkdir -p -- "$LOG_DIR" || exit 1
i=0
while [[ -e "$LOG_DIR/jason_${i}.log" ]]; do
    ((i++))
done
LOG_JAS="$LOG_DIR/jason_${i}.log"

if [[ -n "$PID_JAS" ]]; then
    validate_jason_pid || exit 1
else
    command -v jps >/dev/null || { echo 'jps is required to find the JaCaMo JVM.' >&2; exit 1; }
    PID_JAS=$(find_jacamo_pid)
    if [[ -z "$PID_JAS" ]]; then
        command -v setsid >/dev/null || { echo 'setsid is required to launch JaCaMo.' >&2; exit 1; }
        rm -f .stop___MAS || exit 1
        # Output remains in the terminal, as in the original benchmark.
        setsid ./gradlew --no-daemon -q --console=plain run &
        APP_PID=$!
        echo "Starting JaCaMo..."
        for ((attempt=0; attempt<120; attempt++)); do
            PID_JAS=$(find_jacamo_pid)
            [[ -n "$PID_JAS" ]] && break
            kill -0 "$APP_PID" 2>/dev/null || { echo 'Application exited before JaCaMo started.' >&2; exit 1; }
            sleep 1
        done
        [[ -n "$PID_JAS" ]] || { echo 'JaCaMo did not start within 120 seconds.' >&2; exit 1; }
    fi
fi

[[ -z "$APP_PID" ]] && echo "Sampling existing JaCaMo JVM; it will remain running."
echo "JaCaMo PID: $PID_JAS"
echo "Jason resource log: $LOG_JAS"
echo "CPU: % of one core (ps lifetime average); MEM: RSS in MiB."
start_sampler "$PID_JAS" "$LOG_JAS"

echo "$(date): measuring for $DURATION_SECONDS seconds."
SECONDS=0
while ((SECONDS < DURATION_SECONDS)); do
    ps -p "$PID_JAS" -o pid= | awk 'NF {found=1} END {exit !found}' || {
        echo 'JaCaMo exited before the benchmark finished.' >&2
        exit 1
    }
    sleep 1
done
echo "$(date): benchmark finished."
