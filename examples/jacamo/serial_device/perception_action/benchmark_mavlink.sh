#!/bin/bash

echo "$(date)"

LOG_DIR="log"
rm -f .stop___MAS

mavlink_sampler=""
jas_sampler=""
cleanup_done=0

join_by_comma() { local IFS=,; echo "$*"; }

find_jacamo_pid() {
    jps -l | awk '/JaCaMoLauncher|jacamo|jason/ {print $1; exit}'
}

start_sampler() {
    local pid_list="$1"
    local logfile="$2"

    (
        count=1
        while true; do
            read cpu mem < <(
                ps -p "$pid_list" -o %cpu=,%mem= 2>/dev/null |
                awk '
                    BEGIN{sCPU=0;sMEM=0}
                    NF>=2{sCPU+=$1;sMEM+=$2}
                    END{printf "%.2f %.2f\n",sCPU,sMEM}'
            )

            cpu="${cpu:-0.00}"
            mem="${mem:-0.00}"

            printf "Sample %d - CPU: %6.2f - MEM: %6.2f\n" \
                "$count" "$cpu" "$mem" >> "$logfile"

            ((count++))
            sleep 0.5
        done
    ) >/dev/null 2>&1 &

    echo $!
}

stop_unneeded_ros() {
    mapfile -t ROS_KILL_ARRAY < <(
        ps -eo pid=,args= | awk '
            ($0 ~ /rosbridge_server/ ||
             $0 ~ /rosbridge_websocket/ ||
             $0 ~ /rosapi_node/ ||
             $0 ~ /mavros px4\.launch/ ||
             $0 ~ /\/mavros_node/) &&
            ($0 !~ /awk/) &&
            ($0 !~ /grep/) {
                print $1
            }'
    )

    if [[ "${#ROS_KILL_ARRAY[@]}" -gt 0 ]]; then
        echo "Stopping ROS/MAVROS-related PIDs: $(join_by_comma "${ROS_KILL_ARRAY[@]}")"
        kill "${ROS_KILL_ARRAY[@]}" 2>/dev/null || true
        sleep 2
        kill -9 "${ROS_KILL_ARRAY[@]}" 2>/dev/null || true
    else
        echo "No ROS/MAVROS-related PIDs found to stop."
    fi
}

cleanup() {
    [[ "$cleanup_done" -eq 1 ]] && return
    cleanup_done=1

    echo "Stopping benchmark..."

    [[ -n "${mavlink_sampler:-}" ]] && kill "$mavlink_sampler" 2>/dev/null
    [[ -n "${jas_sampler:-}" ]] && kill "$jas_sampler" 2>/dev/null

    touch .stop___MAS
}

on_signal() {
    local sig="$1"
    echo "Received $sig, cleaning up..."
    exit 130
}

trap cleanup EXIT
trap 'on_signal INT' INT
trap 'on_signal TERM' TERM
trap 'on_signal TSTP' TSTP

# -------------------------------------------------
# Generate incremental log names starting at 0
# -------------------------------------------------
i=0
while [[ -f "$LOG_DIR/mavlink_${i}.log" || -f "$LOG_DIR/jason_${i}.log" ]]; do
    ((i++))
done

LOG_MAVLINK="$LOG_DIR/mavlink_${i}.log"
LOG_JAS="$LOG_DIR/jason_${i}.log"

echo "MAVLink log: $LOG_MAVLINK"
echo "Jason log:   $LOG_JAS"

# -------------------------------------------------
# Stop ROS/MAVROS processes started by the sim
# -------------------------------------------------
stop_unneeded_ros

# -------------------------------------------------
# Start application
# -------------------------------------------------
./gradlew -q --console=plain &
APP_PID=$!
sleep 5

# -------------------------------------------------
# Wait for JaCaMo PID
# -------------------------------------------------
PID_JAS=""
for _ in {1..20}; do
    PID_JAS=$(find_jacamo_pid)
    [[ -n "$PID_JAS" ]] && break
    sleep 1
done

# -------------------------------------------------
# Find MAVLink-side PIDs
#   - px4 process contains the MAVLink module
#   - socat is the transport bridge when PTY/UDP is used
# -------------------------------------------------
mapfile -t MAVLINK_PID_ARRAY < <(
    ps -eo pid=,args= | awk -v jas="$PID_JAS" '
        (($0 ~ /(^|[[:space:]])px4([[:space:]]|$)/) || ($0 ~ /(^|[[:space:]])socat([[:space:]]|$)/)) &&
        ($0 !~ /awk/) &&
        ($0 !~ /grep/) &&
        ($1 != jas) {
            print $1
        }'
)

PIDS_MAVLINK=$(join_by_comma "${MAVLINK_PID_ARRAY[@]}")

echo "MAVLink-side PIDs: ${PIDS_MAVLINK:-none}"
echo "JaCaMo PID:        ${PID_JAS:-none}"

# -------------------------------------------------
# Start samplers
# -------------------------------------------------
if [[ -n "$PIDS_MAVLINK" ]]; then
    mavlink_sampler=$(start_sampler "$PIDS_MAVLINK" "$LOG_MAVLINK")
fi

if [[ -n "$PID_JAS" ]]; then
    jas_sampler=$(start_sampler "$PID_JAS" "$LOG_JAS")
fi

# -------------------------------------------------
# Run benchmark for 80 seconds
# -------------------------------------------------
sleep 80

echo "$(date)"
echo "Benchmark finished."

exit 0
