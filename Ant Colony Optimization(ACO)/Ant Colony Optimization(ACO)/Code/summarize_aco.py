#!/usr/bin/env python3
"""Aggregate result.json files into summary_by_project.csv, summary_overall.json and results.csv."""

import csv
import json
from collections import defaultdict
from pathlib import Path

ACO_DIR = Path(__file__).resolve().parents[1]
EXPERIMENT = "defects4j-round2"
OUT = ACO_DIR / "Results" / EXPERIMENT
COLUMNS = ["algorithm", "project", "selected_unique_bugs", "evaluated_unique_bugs", "detected_unique_bugs",
           "unknown_outcome_bugs", "fault_detection_rate", "complete_runs", "mean_test_coverage_ratio",
           "mean_line_coverage_ratio", "mean_branch_coverage_ratio", "mean_generation_seconds", "mean_total_seconds"]


def mean(values):
    values = [v for v in values if v is not None]
    return round(sum(values) / len(values), 4) if values else None


def aggregate(results):
    bugs = defaultdict(list)
    for r in results:
        bugs[(r["project"], r["bug_id"])].append(r)
    evaluated = detected = 0
    for runs in bugs.values():
        complete = [r for r in runs if r["status"] == "complete"]
        evaluated += bool(complete)
        detected += any(r.get("fault_detected") is True for r in complete)
    done = [r for r in results if r["status"] == "complete"]
    return {
        "selected_unique_bugs": len(bugs), "evaluated_unique_bugs": evaluated, "detected_unique_bugs": detected,
        "unknown_outcome_bugs": len(bugs) - evaluated,
        "fault_detection_rate": round(detected / evaluated, 4) if evaluated else None,
        "complete_runs": len(done),
        "mean_test_coverage_ratio": mean(r["test_coverage"]["ratio"] for r in done),
        "mean_line_coverage_ratio": mean(r["code_coverage"]["line_ratio"] for r in done),
        "mean_branch_coverage_ratio": mean(r["code_coverage"]["branch_ratio"] for r in done),
        "mean_generation_seconds": mean(r.get("generation_seconds") for r in done),
        "mean_total_seconds": mean(r.get("total_seconds") for r in done),
    }


def main():
    results = []
    for path in sorted((OUT / "ACO").glob("*/*-*/run*/result.json")):
        try:
            results.append(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError):
            pass
    by_project = defaultdict(list)
    statuses = defaultdict(int)
    for r in results:
        by_project[r["project"]].append(r)
        statuses[r["status"]] += 1
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / "summary_by_project.csv").open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=COLUMNS)
        writer.writeheader()
        for project in sorted(by_project):
            writer.writerow(dict({"algorithm": "ACO", "project": project}, **aggregate(by_project[project])))
    overall = {"schema_version": "1.0", "experiment": EXPERIMENT,
               "algorithms": {"ACO": dict(aggregate(results), status_breakdown=dict(sorted(statuses.items())))}}
    (OUT / "summary_overall.json").write_text(json.dumps(overall, indent=2) + "\n", encoding="utf-8")
    with (OUT / "results.csv").open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["method", "project", "bug_id", "run", "tests", "compile", "code_coverage",
                         "branch_coverage", "fault_detected", "time"])
        for r in sorted(results, key=lambda x: (x["project"], x["bug_id"], x["run"])):
            cc = r["code_coverage"]
            writer.writerow(["ACO", r["project"], r["bug_id"], r["run"], r["tests_generated"],
                             "TRUE" if r["tests_generated"] and r["status_detail"] != "test_compile_failed" else "FALSE",
                             "" if cc["line_ratio"] is None else f"{100 * cc['line_ratio']:.2f}",
                             "" if cc["branch_ratio"] is None else f"{100 * cc['branch_ratio']:.2f}",
                             "TRUE" if r.get("fault_detected") else "FALSE",
                             f"{(r.get('generation_seconds') or 0):.2f}"])
    a = overall["algorithms"]["ACO"]
    print(f"Summary: runs {len(results)} | bugs {a['selected_unique_bugs']} evaluated {a['evaluated_unique_bugs']} "
          f"detected {a['detected_unique_bugs']} | FDR {a['fault_detection_rate']} | line {a['mean_line_coverage_ratio']} "
          f"| branch {a['mean_branch_coverage_ratio']} | method {a['mean_test_coverage_ratio']} | {dict(statuses)}")


if __name__ == "__main__":
    main()
