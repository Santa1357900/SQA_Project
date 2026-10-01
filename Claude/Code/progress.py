#!/usr/bin/env python3
"""Live progress dashboard for the Claude experiment (generation + validation).

Usage:  python Claude/Code/progress.py          # refresh every 30 s, Ctrl+C to stop
        python Claude/Code/progress.py --once
"""
import glob
import json
import os
import sys
import time
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLAUDE = ROOT / "Claude"
EXP = "defects4j-round2"

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def snapshot():
    gen = Counter()
    for f in glob.glob(str(CLAUDE / "Tests" / EXP / "Claude" / "*" / "*" / "run*" / "generation.json")):
        try:
            gen[json.load(open(f, encoding="utf-8")).get("status", "?")] += 1
        except Exception:
            gen["unreadable"] += 1
    inflight = sum(1 for f in glob.glob(str(CLAUDE / "Tests" / EXP / "Claude" / "*" / "*" / "run*" / ".claim")))
    res = Counter()
    detected = 0
    for f in glob.glob(str(CLAUDE / "Results" / EXP / "Claude" / "*" / "*" / "run*" / "result.json")):
        try:
            d = json.load(open(f, encoding="utf-8"))
        except Exception:
            continue
        res[d.get("status", "?")] += 1
        detected += d.get("fault_detected") is True
    total = len([p for p in (ROOT / "dataset").iterdir() if p.is_dir()]) if (ROOT / "dataset").is_dir() else 854
    return gen, inflight, res, detected, total


def tail(path: Path, n=3):
    if not path.is_file():
        return []
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    keep = [l for l in lines if l.startswith("[")]
    return keep[-n:]


def render():
    gen, inflight, res, detected, total = snapshot()
    ok = gen.get("ok", 0)
    print(time.strftime("%Y-%m-%d %H:%M:%S"))
    print(f"GENERATION  ok {ok}/{total}  ({100 * ok / total:.1f}%)   in-flight {inflight}   "
          f"other: {dict((k, v) for k, v in gen.items() if k != 'ok')}")
    done_val = sum(res.values())
    complete = res.get("complete", 0)
    print(f"VALIDATION  measured {done_val}  complete {complete}  fault detected {detected}"
          f"  ({100 * detected / complete:.1f}% of complete)" if complete else f"VALIDATION  measured {done_val}")
    print(f"            statuses: {dict(res)}")
    for name in sorted(glob.glob(str(CLAUDE / "Results" / "generation_run1_*.log"))):
        p = Path(name)
        print(f"--- {p.name}")
        for l in tail(p):
            print("   " + l[:110])
    vlog = CLAUDE / "Results" / "validation_run1.log"
    print(f"--- {vlog.name}")
    for l in tail(vlog):
        print("   " + l[:110])


if __name__ == "__main__":
    once = "--once" in sys.argv
    while True:
        os.system("cls" if sys.platform == "win32" else "clear") if not once else None
        render()
        if once:
            break
        time.sleep(30)
