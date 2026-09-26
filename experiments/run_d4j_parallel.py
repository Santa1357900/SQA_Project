#!/usr/bin/env python3
"""
Defects4J Massive Parallel Test Runner
Runs tests across all projects and bugs in Defects4J using Python multiprocessing.

Features:
- Auto-detects all projects and active bug IDs
- Runs in parallel using configurable worker pool (e.g. 4, 8, 12 workers)
- Automatic workspace cleanup to preserve disk space
- Per-bug timeout protection (prevents hanging on infinite loops)
- Real-time incremental CSV output
- Tracks: Project, Bug ID, Checkout, Compile, Tests Run, Tests Failed, Execution Time
"""

import os
import sys
import time
import shutil
import subprocess
import csv
from concurrent.futures import ProcessPoolExecutor, as_completed

D4J_BIN = "/home/thananchai/defects4j/framework/bin/defects4j"
DEFAULT_WORK_DIR = "/tmp/d4j_parallel_workspace"
OUTPUT_CSV = os.path.abspath(os.path.join(os.path.dirname(__file__), "results_all_defects4j.csv"))

# Default timeout per bug execution in seconds (e.g., 5 minutes)
BUG_TIMEOUT = 300


def get_all_bugs():
    """Retrieve all projects and their active bug IDs from Defects4J."""
    print("[*] Fetching all project IDs from Defects4J...")
    pids_raw = subprocess.check_output([D4J_BIN, "pids"]).decode().strip().split()
    all_bugs = []
    for pid in pids_raw:
        try:
            bids_raw = subprocess.check_output([D4J_BIN, "bids", "-p", pid]).decode().strip().split()
            for bid in bids_raw:
                all_bugs.append((pid, bid))
        except Exception as e:
            print(f"[!] Warning: Could not fetch bids for {pid}: {e}")
    return all_bugs


def run_single_bug(task):
    """Execute checkout, compile, and test on a single bug."""
    pid, bid, work_base_dir = task
    work_dir = os.path.join(work_base_dir, f"{pid}_{bid}")
    start_time = time.time()

    res = {
        "project": pid,
        "bug_id": bid,
        "checkout": "FAILED",
        "compile": "FAILED",
        "tests_status": "SKIPPED",
        "failing_tests_count": -1,
        "elapsed_seconds": 0.0,
        "error_msg": ""
    }

    env = os.environ.copy()
    env["PATH"] = f"/home/thananchai/defects4j/framework/bin:{env.get('PATH', '')}"

    try:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)
        os.makedirs(work_dir, exist_ok=True)

        # 1. Checkout
        checkout_cmd = [D4J_BIN, "checkout", "-p", pid, "-v", f"{bid}b", "-w", work_dir]
        p_checkout = subprocess.run(checkout_cmd, capture_output=True, text=True, timeout=BUG_TIMEOUT, env=env)
        if p_checkout.returncode != 0:
            res["error_msg"] = "Checkout failed: " + p_checkout.stderr.strip()[:100]
            return res
        res["checkout"] = "SUCCESS"

        # 2. Compile
        compile_cmd = [D4J_BIN, "compile"]
        p_compile = subprocess.run(compile_cmd, cwd=work_dir, capture_output=True, text=True, timeout=BUG_TIMEOUT, env=env)
        if p_compile.returncode != 0:
            res["error_msg"] = "Compile failed"
            return res
        res["compile"] = "SUCCESS"

        # 3. Test
        test_cmd = [D4J_BIN, "test"]
        p_test = subprocess.run(test_cmd, cwd=work_dir, capture_output=True, text=True, timeout=BUG_TIMEOUT, env=env)
        
        # Read failing_tests if created
        failing_tests_file = os.path.join(work_dir, "failing_tests")
        if os.path.exists(failing_tests_file):
            with open(failing_tests_file, "r") as f:
                lines = [l for l in f if l.startswith("--- ")]
                res["failing_tests_count"] = len(lines)
            res["tests_status"] = "COMPLETED"
        else:
            res["tests_status"] = "COMPLETED" if p_test.returncode == 0 else "FAILED"
            res["failing_tests_count"] = 0 if p_test.returncode == 0 else -1

    except subprocess.TimeoutExpired:
        res["error_msg"] = f"Timeout ({BUG_TIMEOUT}s) exceeded"
    except Exception as e:
        res["error_msg"] = str(e)
    finally:
        # Cleanup workspace immediately to preserve disk space
        shutil.rmtree(work_dir, ignore_errors=True)
        res["elapsed_seconds"] = round(time.time() - start_time, 2)

    return res


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Parallel Defects4J Test Runner")
    parser.add_argument("-j", "--workers", type=int, default=8, help="Number of parallel workers (default: 8)")
    parser.add_argument("-p", "--project", type=str, default=None, help="Filter specific project (e.g. Lang)")
    parser.add_argument("-o", "--output", type=str, default=OUTPUT_CSV, help="Output CSV path")
    parser.add_argument("-w", "--workdir", type=str, default=DEFAULT_WORK_DIR, help="Temp directory for work")
    args = parser.parse_args()

    os.makedirs(args.workdir, exist_ok=True)
    all_bugs = get_all_bugs()

    if args.project:
        all_bugs = [b for b in all_bugs if b[0].lower() == args.project.lower()]

    total = len(all_bugs)
    print(f"[*] Total bugs to run: {total} across projects.")
    print(f"[*] Parallel workers: {args.workers}")
    print(f"[*] Output CSV: {args.output}")

    # Prepare CSV file and check for already completed bugs (Auto-Resume)
    file_exists = os.path.exists(args.output)
    completed_bugs = set()
    if file_exists:
        try:
            with open(args.output, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    if row.get("project") and row.get("bug_id"):
                        completed_bugs.add((str(row["project"]).strip(), str(row["bug_id"]).strip()))
            print(f"[*] Auto-Resume: Found {len(completed_bugs)} already completed bugs in CSV.")
        except Exception as e:
            print(f"[!] Warning reading existing CSV: {e}")

    tasks = [(pid, bid, args.workdir) for (pid, bid) in all_bugs if (str(pid).strip(), str(bid).strip()) not in completed_bugs]
    remaining = len(tasks)
    print(f"[*] Tasks to execute: {remaining} remaining out of {total} (Completed: {len(completed_bugs)})")

    if not tasks:
        print("[✓] All bugs have already been executed! Nothing to do.")
        return

    csv_file = open(args.output, "a", newline="", encoding="utf-8")
    fieldnames = ["project", "bug_id", "checkout", "compile", "tests_status", "failing_tests_count", "elapsed_seconds", "error_msg"]
    writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
    if not file_exists:
        writer.writeheader()
        csv_file.flush()

    completed = len(completed_bugs)
    start_all = time.time()

    print("[*] Starting parallel execution...")
    with ProcessPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(run_single_bug, task): task for task in tasks}
        for future in as_completed(futures):
            res = future.result()
            writer.writerow(res)
            csv_file.flush()
            completed += 1
            print(f"[{completed}/{total}] {res['project']}-{res['bug_id']}: Compile={res['compile']}, Tests={res['tests_status']} (Failing={res['failing_tests_count']}) [{res['elapsed_seconds']}s]")

    csv_file.close()
    total_elapsed = round(time.time() - start_all, 2)
    print(f"[✓] Finished running {completed} bugs in {total_elapsed} seconds.")


if __name__ == "__main__":
    main()
