"""Turn one lab run into an incident-copilot fixtures directory.

Usage: python3 -I export.py <run-dir> [--prometheus URL]

Reads <run-dir>/window.txt, changes.tsv and app.log, queries Prometheus for the window,
and writes <run-dir>/payment-service/{metrics,logs,changes}.txt and profile.md in the
formats described in src/main/resources/fixtures/README.md.

Header and footer lines never contain a timestamp: FixtureLoader treats any line with
one as data and would filter it by time like an entry.
"""

import argparse
import json
import re
import statistics
import sys
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path

SERVICE = "payment-service"
STEP = timedelta(minutes=1)
APP_PACKAGE = "dev.xiaohanglyu"
SAMPLES_PER_SIGNATURE = 8
BASELINE_MINUTES = 10

# Each query yields one value per minute. Actuator traffic is excluded so scrapes don't
# dilute latency and request rate.
HTTP = 'uri!~"/actuator.*"'
QUERIES = {
    "p50": f'histogram_quantile(0.5, sum by (le) (rate(http_server_requests_seconds_bucket{{{HTTP}}}[1m])))',
    "p99": f'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{{{HTTP}}}[1m])))',
    "rps": f'sum(rate(http_server_requests_seconds_count{{{HTTP}}}[1m]))',
    "5xx%": f'100 * sum(rate(http_server_requests_seconds_count{{{HTTP},status=~"5.."}}[1m]) or vector(0))'
            f' / sum(rate(http_server_requests_seconds_count{{{HTTP}}}[1m]))',
    "cpu%": "100 * max(process_cpu_usage)",
    "heap%": '100 * sum(jvm_memory_used_bytes{area="heap"}) / max(jvm_memory_max_bytes{area="heap"})',
    "gc ms/s": "1000 * sum(rate(jvm_gc_pause_seconds_sum[1m]))",
    "active": "max(hikaricp_connections_active)",
    "max": "max(hikaricp_connections_max)",
    "pending": "max(hikaricp_connections_pending)",
}

# Spring Boot default console/file pattern:
# 2026-11-04T14:20:00.123+08:00  WARN 1 --- [payment-service] [nio-8080-exec-1] d.x.p.RefundService : msg
ENTRY = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+[+-]\d{2}:\d{2})\s+"
    r"(?P<level>[A-Z]+)\s+\d+\s+---\s+\[[^\]]*\]\s+\[[^\]]*\]\s+(?P<logger>\S+)\s*:\s(?P<msg>.*)$")
READY = re.compile(r"payment-service \S+ ready")


def main():
    args = parse_args()
    run = Path(args.run_dir)
    start, end = read_window(run / "window.txt")
    out = run / SERVICE
    out.mkdir(exist_ok=True)

    rows = metric_rows(args.prometheus, start, end)
    (out / "metrics.txt").write_text(render_metrics(rows))
    (out / "logs.txt").write_text(render_logs(run / "app.log"))
    (out / "changes.txt").write_text(render_changes(run / "changes.tsv"))
    template = Path(__file__).resolve().parent.parent / "scenarios" / f"{SERVICE}.profile.md"
    first_change = second_change_time(run / "changes.tsv")
    (out / "profile.md").write_text(
        template.read_text().replace("{{baselines}}", baselines(rows, first_change)))
    print(f"wrote {out}")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("run_dir")
    parser.add_argument("--prometheus", default="http://localhost:9090")
    return parser.parse_args()


def read_window(path):
    marks = dict(line.split(" ", 1) for line in path.read_text().split("\n") if line)
    if "start" not in marks or "end" not in marks:
        sys.exit(f"{path} needs a start and an end line; was the run finished?")
    return datetime.fromisoformat(marks["start"].strip()), datetime.fromisoformat(marks["end"].strip())


# ---------- metrics ----------

def metric_rows(prometheus, start, end):
    first = start.replace(second=0, microsecond=0) + STEP
    series = {name: query_range(prometheus, q, first, end) for name, q in QUERIES.items()}
    rows = []
    at = first
    while at <= end:
        key = int(at.timestamp())
        rows.append((at, {name: values.get(key) for name, values in series.items()}))
        at += STEP
    return rows


def query_range(prometheus, query, start, end):
    params = urllib.parse.urlencode({
        "query": query, "start": start.timestamp(), "end": end.timestamp(),
        "step": int(STEP.total_seconds())})
    try:
        with urllib.request.urlopen(f"{prometheus}/api/v1/query_range?{params}", timeout=30) as r:
            body = json.load(r)
    except OSError as e:
        sys.exit(f"Prometheus at {prometheus} unreachable ({e}); is the lab still up?")
    if body.get("status") != "success":
        sys.exit(f"Prometheus rejected {query!r}: {body.get('error')}")
    values = {}
    for result in body["data"]["result"]:
        for ts, value in result["values"]:
            number = float(value)
            if number == number:  # drop NaN, e.g. a quantile with no requests
                values[int(float(ts))] = number
    return values


def latency(seconds):
    if seconds is None:
        return "-"
    return f"{seconds * 1000:.0f}ms" if seconds < 1 else f"{seconds:.1f}s"


def number(value, fmt):
    return "-" if value is None else format(value, fmt)


def render_metrics(rows):
    lines = [
        f"Metrics for {SERVICE}, 1-minute buckets",
        "ISO-8601 with offset, Asia/Shanghai. A '-' means no scrape that minute.",
        "",
        f"{'time':<26}{'p50':>8}{'p99':>8}{'rps':>6}{'5xx%':>7}{'cpu%':>6}{'heap%':>7}"
        f"{'gc ms/s':>9}{'pool active/max':>17}{'pending':>9}",
    ]
    for at, v in rows:
        pool = "-" if v["active"] is None else f"{v['active']:.0f}/{number(v['max'], '.0f')}"
        lines.append(
            f"{at.isoformat():<26}{latency(v['p50']):>8}{latency(v['p99']):>8}"
            f"{number(v['rps'], '.0f'):>6}{number(v['5xx%'], '.1f'):>7}"
            f"{number(v['cpu%'], '.0f'):>6}{number(v['heap%'], '.0f'):>7}"
            f"{number(v['gc ms/s'], '.0f'):>9}{pool:>17}{number(v['pending'], '.0f'):>9}")
    return "\n".join(lines) + "\n"


def second_change_time(path):
    """When the first change after the initial deploy happened; baselines stop there."""
    lines = [l for l in path.read_text().split("\n") if l]
    return datetime.fromisoformat(lines[1].split("\t")[0]) if len(lines) > 1 else None


def baselines(rows, until):
    # Skip the first minutes: the JVM is warming up and the seed may still be settling.
    window = [v for at, v in rows if until is None or at < until][2:2 + BASELINE_MINUTES]

    def span(name, render):
        values = [v[name] for v in window if v[name] is not None]
        if not values:
            return "no data"
        return f"{render(min(values))}–{render(max(values))} (median {render(statistics.median(values))})"

    pct = lambda x: f"{x:.0f}%"
    whole = lambda x: f"{x:.0f}"
    return "\n".join([
        f"- p99: {span('p99', latency)}.",
        f"- Requests per second: {span('rps', whole)}.",
        f"- 5xx: {span('5xx%', lambda x: f'{x:.1f}%')}.",
        f"- CPU: {span('cpu%', pct)}.",
        f"- Heap used: {span('heap%', pct)}. G1 collects in cycles, so a saw-tooth is normal.",
        f"- GC pause: {span('gc ms/s', lambda x: f'{x:.0f}ms/s')}.",
        f"- Pool active: {span('active', whole)} of 20. Pending: {span('pending', whole)}.",
    ])


# ---------- logs ----------

def read_entries(path):
    """Yields (first line match, continuation lines) for every log entry."""
    current, rest = None, []
    for line in path.read_text(errors="replace").split("\n"):
        match = ENTRY.match(line)
        if match:
            if current:
                yield current, rest
            current, rest = match, []
        elif current and line.strip():
            rest.append(line)
    if current:
        yield current, rest


def kept(match):
    level = match["level"]
    return level in ("WARN", "ERROR") or (level == "INFO" and READY.search(match["msg"]))


def signature(match):
    # Numbers vary per entry (ids, amounts, timings); without them, repeats group together.
    return match["level"] + " " + match["logger"] + " " + re.sub(r"\d+", "#", match["msg"])[:120]


def trim_trace(lines):
    """Keeps exception lines and this application's frames; a full trace is mostly
    Tomcat and Spring frames that say nothing about this service."""
    out, skipped = [], 0
    for line in lines:
        frame = line.strip().startswith("at ")
        if not frame or APP_PACKAGE in line:
            if skipped:
                out.append(f"\t... {skipped} framework frames")
                skipped = 0
            out.append(line)
        else:
            skipped += 1
    if skipped:
        out.append(f"\t... {skipped} framework frames")
    return out


def sample(items, n):
    """First few and an even spread of the rest, so onset and persistence both show."""
    if len(items) <= n:
        return items
    head = items[:3]
    tail = items[3:]
    step = len(tail) / (n - 3)
    return head + [tail[int(i * step)] for i in range(n - 3)]


def render_logs(path):
    groups = defaultdict(list)
    for match, rest in read_entries(path):
        if kept(match):
            groups[signature(match)].append((match, rest))
    total = sum(len(g) for g in groups.values())
    chosen = sorted((e for g in groups.values() for e in sample(g, SAMPLES_PER_SIGNATURE)),
                    key=lambda e: e[0]["ts"])
    lines = [
        f"Log matches for {SERVICE}: WARN, ERROR and restart lines, {total} entries, "
        f"showing {len(chosen)}",
        "Spring Boot default pattern, ISO-8601 with offset. Repeated messages are sampled;",
        "stack traces keep exception lines and application frames only.",
        "",
    ]
    for match, rest in chosen:
        lines.append(match.group(0))
        lines.extend(trim_trace(rest))
    lines.append("")
    lines.append("Counts by message over the whole window:")
    counts = Counter({sig: len(g) for sig, g in groups.items()})
    for sig, count in counts.most_common():
        lines.append(f"  {count:>7}  {sig}")
    return "\n".join(lines) + "\n"


# ---------- changes ----------

def render_changes(path):
    deploys = [l.split("\t") for l in path.read_text().split("\n") if l]
    lines = [f"Recent changes for {SERVICE}", "ISO-8601 with offset, Asia/Shanghai.", ""]
    for i in range(len(deploys) - 1, -1, -1):
        at, version, pr, title, details = deploys[i]
        current = " — currently running" if i == len(deploys) - 1 else ""
        lines.append(f"{at}  deploy {version}{current}")
        lines.append(f"    PR {pr} \"{title}\"")
        lines.append(f"    {details}")
        lines.append("    deployed by ci-bot")
        if i > 0:
            lines.append(f"    rollback target: {deploys[i - 1][1]}")
        lines.append("")
    lines.append("No infrastructure changes and no database schema migrations in this period.")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    main()
