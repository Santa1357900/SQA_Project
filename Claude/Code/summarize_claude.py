#!/usr/bin/env python3
"""
Aggregate Claude result.json files into the group's summary files
(COMMON_OUTPUT_FORMAT.md):

  Claude/Results/<exp>/summary_by_project.csv   one row per (algorithm, project)
  Claude/Results/<exp>/summary_overall.json     one entry per algorithm, bugs pooled before FDR
  Claude/Results/<exp>/results_claude.csv       flat table in the team's experiments/results.csv layout

Usage:
  python Claude/Code/summarize_claude.py
"""

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLAUDE_DIR = ROOT / "Claude"
CONFIG_FILE = CLAUDE_DIR / "Configuration" / "claude_config.json"

SUMMARY_COLUMNS = [
    "algorithm", "project", "selected_unique_bugs", "evaluated_unique_bugs",
    "detected_unique_bugs", "unknown_outcome_bugs", "fault_detection_rate",
    "complete_runs", "mean_test_coverage_ratio", "mean_line_coverage_ratio",
    "mean_branch_coverage_ratio", "mean_generation_seconds", "mean_total_seconds",
]


def load_results(config):
    root = CLAUDE_DIR / "Results" / config["experiment"] / config["algorithm"]
    results = []
    for path in sorted(root.glob("*/*-*/run*/result.json")):
        try:
            results.append(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"[!] skip {path}: {exc}", file=sys.stderr)
    return results


def mean(values):
    values = [v for v in values if v is not None]
    return round(sum(values) / len(values), 4) if values else None


def aggregate(results):
    """Return one summary dict for the given list of result.json dicts."""
    bugs = defaultdict(list)
    for r in results:
        bugs[(r["project"], r["bug_id"])].append(r)
    selected = len(bugs)
    evaluated = detected = 0
    for runs in bugs.values():
        complete = [r for r in runs if r["status"] == "complete"]
        if complete:
            evaluated += 1
            if any(r.get("fault_detected") is True for r in complete):
                detected += 1
    complete_runs = [r for r in results if r["status"] == "complete"]
    return {
        "selected_unique_bugs": selected,
        "evaluated_unique_bugs": evaluated,
        "detected_unique_bugs": detected,
        "unknown_outcome_bugs": selected - evaluated,
        "fault_detection_rate": round(detected / evaluated, 4) if evaluated else None,
        "complete_runs": len(complete_runs),
        "mean_test_coverage_ratio": mean(r["test_coverage"]["ratio"] for r in complete_runs),
        "mean_line_coverage_ratio": mean(r["code_coverage"]["line_ratio"] for r in complete_runs),
        "mean_branch_coverage_ratio": mean(r["code_coverage"]["branch_ratio"] for r in complete_runs),
        "mean_generation_seconds": mean(r.get("generation_seconds") for r in complete_runs),
        "mean_total_seconds": mean(r.get("total_seconds") for r in complete_runs),
    }


def status_breakdown(results):
    counts = defaultdict(int)
    for r in results:
        counts[r["status"]] += 1
    return dict(sorted(counts.items()))


def write_results_csv(path: Path, results, algorithm):
    """Team-wide flat layout: method,project,bug_id,run,tests,compile,code_coverage,branch_coverage,fault_detected,time"""
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["method", "project", "bug_id", "run", "tests", "compile",
                    "code_coverage", "branch_coverage", "fault_detected", "time"])
        for r in sorted(results, key=lambda x: (x["project"], x["bug_id"], x["run"])):
            cc = r["code_coverage"]
            compiled = r["status"] not in ("no_generated_tests",) and \
                r.get("status_detail") != "test_compile_failed"
            line = cc["line_ratio"]
            branch = cc["branch_ratio"]
            w.writerow([
                algorithm, r["project"], r["bug_id"], r["run"],
                r.get("tests_generated", 0),
                "TRUE" if compiled else "FALSE",
                f"{100 * line:.2f}" if line is not None else "",
                f"{100 * branch:.2f}" if branch is not None else "",
                "TRUE" if r.get("fault_detected") else "FALSE",
                f"{(r.get('generation_seconds') or 0):.2f}",
            ])


def summarize(config=None):
    config = config or json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    algorithm = config["algorithm"]
    results = load_results(config)
    out_dir = CLAUDE_DIR / "Results" / config["experiment"]
    out_dir.mkdir(parents=True, exist_ok=True)

    by_project = defaultdict(list)
    for r in results:
        by_project[r["project"]].append(r)

    with (out_dir / "summary_by_project.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=SUMMARY_COLUMNS)
        w.writeheader()
        for project in sorted(by_project):
            row = {"algorithm": algorithm, "project": project}
            row.update(aggregate(by_project[project]))
            w.writerow(row)

    overall = {"schema_version": "1.0", "experiment": config["experiment"],
               "algorithms": {algorithm: dict(aggregate(results),
                                              status_breakdown=status_breakdown(results),
                                              model=config.get("model"),
                                              effort=config.get("effort"))}}
    (out_dir / "summary_overall.json").write_text(
        json.dumps(overall, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_results_csv(out_dir / "results_claude.csv", results, algorithm)

    agg = overall["algorithms"][algorithm]
    print(f"Summary: {len(results)} runs | bugs {agg['selected_unique_bugs']} "
          f"evaluated {agg['evaluated_unique_bugs']} detected {agg['detected_unique_bugs']} "
          f"| FDR {agg['fault_detection_rate']} | line {agg['mean_line_coverage_ratio']} "
          f"| branch {agg['mean_branch_coverage_ratio']} | method {agg['mean_test_coverage_ratio']}",
          flush=True)
    print(f"  -> {out_dir / 'summary_by_project.csv'}\n  -> {out_dir / 'summary_overall.json'}\n"
          f"  -> {out_dir / 'results_claude.csv'}", flush=True)
    return overall


if __name__ == "__main__":
    summarize()
