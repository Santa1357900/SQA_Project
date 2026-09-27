import csv
import os
from collections import defaultdict

import argparse
import sys

DEFAULT_CSV = os.path.join(os.path.dirname(__file__), "..", "results.csv")

def summarize(csv_file=None):
    csv_path = csv_file or DEFAULT_CSV
    if not os.path.exists(csv_path):
        print(f"File not found: {csv_path}")
        return

    data = defaultdict(lambda: {
        "runs": 0, "tests": [], "compile_success": 0,
        "code_cov": [], "branch_cov": [], "fault_detected": 0,
        "time": []
    })

    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = (row["project"], row["bug_id"], row["method"])
            entry = data[key]
            entry["runs"] += 1
            entry["tests"].append(int(row["tests"]))
            if row["compile"].strip().upper() in ["TRUE", "1", "YES"]:
                entry["compile_success"] += 1
            entry["code_cov"].append(float(row["code_coverage"]))
            entry["branch_cov"].append(float(row["branch_coverage"]))
            if row["fault_detected"].strip().upper() in ["TRUE", "1", "YES"]:
                entry["fault_detected"] += 1
            entry["time"].append(float(row["time"]))

    if not data:
        print("No experimental data recorded yet.")
        return

    print("| Project | Bug | Method | Runs | Avg Tests | Compile % | Avg Code Cov % | Avg Branch Cov % | Fault Detected % | Avg Time (s) |")
    print("| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
    for (proj, bug, method), val in sorted(data.items()):
        runs = val["runs"]
        avg_tests = sum(val["tests"]) / runs if runs else 0
        compile_pct = (val["compile_success"] / runs) * 100 if runs else 0
        avg_code_cov = sum(val["code_cov"]) / runs if runs else 0
        avg_branch_cov = sum(val["branch_cov"]) / runs if runs else 0
        fault_pct = (val["fault_detected"] / runs) * 100 if runs else 0
        avg_time = sum(val["time"]) / runs if runs else 0

        print(f"| {proj} | {bug} | {method} | {runs} | {avg_tests:.1f} | {compile_pct:.1f}% | {avg_code_cov:.2f}% | {avg_branch_cov:.2f}% | {fault_pct:.1f}% | {avg_time:.2f}s |")

if __name__ == "__main__":
    target_csv = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_CSV
    if "--v2" in sys.argv:
        target_csv = os.path.join(os.path.dirname(__file__), "..", "results_v2.csv")
    summarize(target_csv)
