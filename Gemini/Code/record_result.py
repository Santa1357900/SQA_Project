import argparse
import csv
import os

RESULTS_CSV = os.path.join(os.path.dirname(__file__), "..", "Results", "results.csv")

def append_result(method: str, project: str, bug_id: str, run: int,
                  tests: int, compile_status: bool, code_cov: float,
                  branch_cov: float, fault_detected: bool, time_sec: float):
    file_exists = os.path.exists(RESULTS_CSV)
    
    with open(RESULTS_CSV, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if not file_exists or os.path.getsize(RESULTS_CSV) == 0:
            writer.writerow([
                "method", "project", "bug_id", "run", "tests",
                "compile", "code_coverage", "branch_coverage",
                "fault_detected", "time"
            ])
        writer.writerow([
            method, project, bug_id, run, tests,
            str(compile_status).upper(), f"{code_cov:.2f}",
            f"{branch_cov:.2f}", str(fault_detected).upper(), f"{time_sec:.2f}"
        ])
    print(f"[OK] Recorded {method} Run {run} for {project}-{bug_id} to {RESULTS_CSV}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Record experiment result to results.csv")
    parser.add_argument("--method", required=True, choices=["DE", "ACO", "Claude", "Gemini"])
    parser.add_argument("--project", default="Lang")
    parser.add_argument("--bug", default="1")
    parser.add_argument("--run", type=int, default=1)
    parser.add_argument("--tests", type=int, default=0, help="Number of generated tests")
    parser.add_argument("--compile", action="store_true", help="Set flag if compilation succeeded")
    parser.add_argument("--code-cov", type=float, default=0.0, help="Code coverage percentage")
    parser.add_argument("--branch-cov", type=float, default=0.0, help="Branch coverage percentage")
    parser.add_argument("--fault-detected", action="store_true", help="Set flag if fault was exposed")
    parser.add_argument("--time", type=float, default=0.0, help="Generation time in seconds")
    args = parser.parse_args()

    append_result(
        args.method, args.project, args.bug, args.run,
        args.tests, args.compile, args.code_cov,
        args.branch_cov, args.fault_detected, args.time
    )
