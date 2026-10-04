#!/usr/bin/env python3
"""Live progress of the ACO run.  python Code/progress.py [--once]"""

import json
import os
import sys
import time
from collections import defaultdict
from pathlib import Path

ACO_DIR = Path(__file__).resolve().parents[1]
RESULTS = ACO_DIR / "Results" / "defects4j-round2" / "ACO"
LOG = ACO_DIR / "Results" / "run1.log"
TOTAL = {"Chart": 26, "Cli": 39, "Closure": 174, "Codec": 18, "Collections": 28, "Compress": 47, "Csv": 16,
         "Gson": 18, "JacksonCore": 26, "JacksonDatabind": 110, "JacksonXml": 6, "Jsoup": 93, "JxPath": 22,
         "Lang": 61, "Math": 106, "Mockito": 38, "Time": 26}


def snapshot():
    rows = defaultdict(lambda: {"done": 0, "complete": 0, "fault": 0, "line": [], "other": 0})
    for path in RESULTS.glob("*/*-*/run1/result.json"):
        try:
            r = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if r["status"] == "running":
            continue
        row = rows[r["project"]]
        row["done"] += 1
        if r["status"] == "complete":
            row["complete"] += 1
            row["fault"] += r.get("fault_detected") is True
            if r["code_coverage"]["line_ratio"] is not None:
                row["line"].append(r["code_coverage"]["line_ratio"])
        else:
            row["other"] += 1
    return rows


def bar(done, total, width=24):
    filled = int(width * done / total) if total else 0
    return "#" * filled + "." * (width - filled)


def render(started):
    rows = snapshot()
    done = sum(r["done"] for r in rows.values())
    complete = sum(r["complete"] for r in rows.values())
    fault = sum(r["fault"] for r in rows.values())
    lines = [x for r in rows.values() for x in r["line"]]
    total = sum(TOTAL.values())
    print(f"ACO on Defects4J   {time.strftime('%H:%M:%S')}")
    print(f"[{bar(done, total, 50)}] {done}/{total}  ({100 * done / total:.1f}%)")
    print(f"complete {complete}   fault detected {fault}"
          + (f" ({100 * fault / complete:.1f}%)" if complete else "")
          + (f"   mean line coverage {100 * sum(lines) / len(lines):.1f}%" if lines else ""))
    print()
    print(f"{'project':<16} {'progress':<26} {'done':>9} {'detected':>9} {'line cov':>9} {'failed':>7}")
    for project, count in TOTAL.items():
        r = rows.get(project, {"done": 0, "complete": 0, "fault": 0, "line": [], "other": 0})
        cov = f"{100 * sum(r['line']) / len(r['line']):.1f}%" if r["line"] else "-"
        print(f"{project:<16} {bar(r['done'], count):<26} {r['done']:>4}/{count:<4} {r['fault']:>9} {cov:>9} {r['other']:>7}")
    if LOG.is_file():
        tail = [l for l in LOG.read_text(encoding="utf-8", errors="replace").splitlines() if l.startswith("[")][-6:]
        print("\nlatest:")
        for l in tail:
            print("  " + l[:110])
        if any(l.startswith("Done:") for l in LOG.read_text(encoding="utf-8", errors="replace").splitlines()[-3:]):
            print("\nRUN FINISHED")


if __name__ == "__main__":
    once = "--once" in sys.argv
    begin = time.time()
    while True:
        if not once:
            os.system("cls" if os.name == "nt" else "clear")
        render(begin)
        if once:
            break
        time.sleep(10)
