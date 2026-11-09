# Shared steps for scenario scripts. Source it; don't run it.
#
# A run writes everything to out/<scenario>-<timestamp>/:
#   app.log      the service's log file, across every restart
#   changes.tsv  one line per deploy, written at the moment of the deploy
#   window.txt   start and end of the run
# and finish() turns that, plus Prometheus, into a fixtures directory.

set -euo pipefail

LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Every timestamp the run records is in the service's zone, so logs, changes and metrics
# line up without conversion.
export TZ=Asia/Shanghai
# Shortens every hold for a smoke test, e.g. HOLD_SCALE=0.1. Real runs leave it at 1.
HOLD_SCALE="${HOLD_SCALE:-1}"

compose() { docker compose -f "$LAB_DIR/docker-compose.yml" "$@"; }
now() { date +"%Y-%m-%dT%H:%M:%S%z" | sed -E 's/([0-9]{2})([0-9]{2})$/\1:\2/'; }
say() { echo "[$(date +%H:%M:%S)] $*"; }

# start_run <scenario>: fresh run directory, infrastructure up, healthy baseline deployed.
start_run() {
    SCENARIO="$1"
    RUN_DIR="$LAB_DIR/out/$SCENARIO-$(date +%Y%m%d-%H%M%S)"
    export RUN_DIR
    mkdir -p "$RUN_DIR"
    say "run directory: $RUN_DIR"
    (cd "$LAB_DIR/demo-service" && mvn -q -DskipTests package)
    compose build payment-service
    compose up -d postgres prometheus
    # A service left over from the previous run still finishes its stuck requests (as
    # 30s timeouts) after traffic stops, and those land in this run's first minute.
    compose rm -sf payment-service
    echo "start $(now)" > "$RUN_DIR/window.txt"
}

# deploy <version> <faults> <pr> <title> <details>
# Recreates the service with that version. Faults are comma-separated, or "" for none.
# The change record is written before the restart, as a CD system logs a rollout start.
deploy() {
    local version="$1" faults="$2" pr="$3" title="$4" details="$5"
    printf '%s\t%s\t%s\t%s\t%s\n' "$(now)" "$version" "$pr" "$title" "$details" \
        >> "$RUN_DIR/changes.tsv"
    say "deploy $version${faults:+ (faults: $faults)}"
    LAB_VERSION="$version" LAB_FAULTS="$faults" compose up -d payment-service
    wait_ready
}

wait_ready() {
    # The first start seeds 3M rows, so allow a few minutes.
    for _ in $(seq 1 180); do
        if curl -fs http://localhost:18081/actuator/health > /dev/null 2>&1; then
            return 0
        fi
        if [ -z "$(compose ps -q --status running payment-service)" ]; then
            echo "payment-service exited during startup; see $RUN_DIR/app.log" >&2
            exit 1
        fi
        sleep 2
    done
    echo "payment-service did not become healthy; see $RUN_DIR/app.log" >&2
    exit 1
}

start_traffic() {
    compose up -d load
    say "traffic started"
}

# hold <minutes> <why>
hold() {
    local seconds
    seconds=$(awk -v m="$1" -v s="$HOLD_SCALE" 'BEGIN { printf "%d", m * 60 * s }')
    say "hold ${1}m × $HOLD_SCALE: $2"
    sleep "$seconds"
}

# finish: stop traffic, close the window and export fixtures.
finish() {
    echo "end $(now)" >> "$RUN_DIR/window.txt"
    compose stop load
    # Let Prometheus take its last scrape of the window.
    sleep 10
    python3 -I "$LAB_DIR/export/export.py" "$RUN_DIR"
    cp "$LAB_DIR/scenarios/$SCENARIO.md" "$RUN_DIR/expected.md"
    say "done: $RUN_DIR/payment-service/ (answer key: $RUN_DIR/expected.md)"
}
