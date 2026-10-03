#!/usr/bin/env python3
"""Validate existing Gemini generated tests on both Defects4J revisions in parallel
and output results according to COMMON_OUTPUT_FORMAT standard."""

import argparse
import csv
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from collections import defaultdict


ROOT = Path(__file__).resolve().parents[1]

def get_default_test_root() -> Path:
    ai_gen = ROOT / "ai" / "Gemini" / "generated-tests"
    if ai_gen.is_dir() and any(ai_gen.glob("*/*.java")):
        return ai_gen
    test_code = ROOT / "TestCode"
    if test_code.is_dir() and any(test_code.glob("*/*.java")):
        return test_code
    return ai_gen

TEST_ROOT = get_default_test_root()
RESULT_ROOT = ROOT / "Results"
TARGET_RE = re.compile(r"^([A-Za-z][A-Za-z0-9]*)_([1-9][0-9]*)_buggy$")
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;")
FAILURES_RE = re.compile(r"Failing tests:\s*(\d+)")
FIELDS = ("project", "bug_id", "tests", "buggy_result", "buggy_fails",
          "fixed_result", "fixed_fails", "verdict", "lines", "covered_lines",
          "line_cov", "branches", "covered_branches", "branch_cov")
STOP_REQUESTED = threading.Event()
ACTIVE_COMMANDS = set()
ACTIVE_COMMANDS_LOCK = threading.Lock()
TIMEOUT_RC = 124


class TargetInterrupted(Exception):
    """A target stopped before its report was complete."""


def discover_targets():
    if not TEST_ROOT.is_dir():
        return {}
    return {path.name[:-6]: path for path in TEST_ROOT.iterdir()
            if path.is_dir() and TARGET_RE.fullmatch(path.name)
            and any(path.rglob("*.java"))}


def make_archive(source: Path, destination: Path):
    """Use Java package names because generator output is stored in flat directories."""
    seen = set()
    tests = 0
    with tarfile.open(destination, "w:bz2") as archive:
        for java in sorted(source.rglob("*.java")):
            code = java.read_text(encoding="utf-8-sig", errors="replace")
            match = PACKAGE_RE.search(code)
            relative = Path(*(match.group(1).split(".") if match else [])) / java.name
            name = relative.as_posix()
            if name in seen:
                raise ValueError(f"Duplicate test path in archive: {name}")
            seen.add(name)
            archive.add(java, arcname=name)
            tests += len(re.findall(r"@(?:org\.junit\.)?Test\b", code))
    if not seen:
        raise ValueError("No Java test files")
    return tests, len(seen)


def run_command(args, log: Path, cwd=None, timeout=None):
    with log.open("a", encoding="utf-8") as output:
        output.write("$ " + " ".join(map(str, args)) + "\n")
        output.flush()
        try:
            with ACTIVE_COMMANDS_LOCK:
                if STOP_REQUESTED.is_set():
                    raise TargetInterrupted
                command = subprocess.Popen(args, cwd=cwd, stdout=output,
                                           stderr=subprocess.STDOUT,
                                           start_new_session=(os.name == "posix"))
                ACTIVE_COMMANDS.add(command)
            try:
                try:
                    rc = command.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    output.write(f"[timeout after {timeout}s]\n")
                    output.flush()
                    terminate_command(command)
                    rc = TIMEOUT_RC
            finally:
                with ACTIVE_COMMANDS_LOCK:
                    ACTIVE_COMMANDS.discard(command)
            output.write(f"[exit {rc}]\n")
            if STOP_REQUESTED.is_set():
                raise TargetInterrupted
            return rc
        except OSError as exc:
            output.write(f"[start error] {exc}\n")
            return 127


def terminate_command(command):
    """Stop a Defects4J command and the Java processes it launched."""
    if command.poll() is None:
        try:
            if os.name == "posix":
                os.killpg(command.pid, signal.SIGTERM)
            else:
                command.terminate()
        except ProcessLookupError:
            pass
    try:
        command.wait(timeout=3)
    except subprocess.TimeoutExpired:
        if os.name == "posix":
            try:
                os.killpg(command.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        else:
            command.kill()
        command.wait()


def stop_active_commands():
    """Stop only subprocesses launched by this runner, including Java children."""
    with ACTIVE_COMMANDS_LOCK:
        commands = list(ACTIVE_COMMANDS)
    for command in commands:
        terminate_command(command)


def test_result(workspace: Path, log: Path, rc: int):
    output = log.read_text(encoding="utf-8", errors="replace")
    matches = FAILURES_RE.findall(output)
    if matches:
        count = int(matches[-1])
    else:
        failing = workspace / "failing_tests"
        count = sum(line.startswith("--- ") for line in failing.read_text(
            encoding="utf-8", errors="replace").splitlines()) if failing.exists() else None
    if count is None:
        return "NOT_RUN", None
    if rc != 0 and count == 0:
        return "NOT_RUN", None
    return ("FAIL" if count else "PASS"), count


def read_coverage(workspace: Path):
    summary = workspace / "summary.csv"
    if not summary.is_file():
        return {}
    with summary.open(newline="", encoding="utf-8") as handle:
        row = next(csv.DictReader(handle), None)
    if not row:
        return {}
    try:
        lines = int(row["LinesTotal"])
        covered_lines = int(row["LinesCovered"])
        branches = int(row["ConditionsTotal"])
        covered_branches = int(row["ConditionsCovered"])
    except (KeyError, TypeError, ValueError):
        return {}
    return {"lines": lines, "covered_lines": covered_lines,
            "line_cov": round(100 * covered_lines / lines, 2) if lines else None,
            "branches": branches, "covered_branches": covered_branches,
            "branch_cov": round(100 * covered_branches / branches, 2) if branches else None}


def run_revision(d4j: str, project: str, bug_id: int, suffix: str,
                 archive: Path, temporary: Path, logs: Path, coverage: bool,
                 test_timeout: int):
    label = "buggy" if suffix == "b" else "fixed"
    workspace = temporary / label
    log = logs / f"{label}.log"
    checkout = run_command([d4j, "checkout", "-p", project, "-v",
                            f"{bug_id}{suffix}", "-w", str(workspace)], log)
    if checkout:
        return {"result": "NOT_RUN", "fails": None, "compile": "NOT_RUN"}
    compile_rc = run_command([d4j, "compile", "-w", str(workspace)], log)
    if compile_rc:
        return {"result": "NOT_RUN", "fails": None, "compile": "FAIL"}
    failing = workspace / "failing_tests"
    failing.unlink(missing_ok=True)
    test_log = logs / f"{label}_test.log"
    rc = run_command([d4j, "test", "-s", str(archive)], test_log,
                     cwd=workspace, timeout=test_timeout)
    result, fails = (("NOT_RUN", None) if rc == TIMEOUT_RC else
                     test_result(workspace, test_log, rc))
    test_output = test_log.read_text(encoding="utf-8", errors="replace")
    details = {"result": result, "fails": fails, "compile": "PASS",
               "test_compile": ("FAIL" if re.search(r"compile\.gen\.tests\).*FAIL", test_output)
                                else "PASS" if re.search(r"compile\.gen\.tests\).*OK", test_output)
                                else "NOT_RUN")}
    if rc == TIMEOUT_RC:
        details["test_status"] = "TIMEOUT"
    if failing.is_file():
        shutil.copy2(failing, logs / f"{label}_failing_tests.txt")
    if coverage and label == "buggy" and result != "NOT_RUN":
        coverage_log = logs / "buggy_coverage.log"
        coverage_rc = run_command([d4j, "coverage", "-s", str(archive)],
                                  coverage_log, cwd=workspace, timeout=test_timeout)
        details["coverage_status"] = ("TIMEOUT" if coverage_rc == TIMEOUT_RC else
                                      "PASS" if coverage_rc == 0 else "FAIL")
        if coverage_rc == 0:
            details.update(read_coverage(workspace))
    return details


def write_json_atomic(path: Path, value):
    staged = path.with_suffix(".json.tmp")
    staged.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    staged.replace(path)


def write_csv_atomic(path: Path, rows, fieldnames=None):
    staged = path.with_suffix(".csv.tmp")
    with staged.open("w", newline="", encoding="utf-8") as handle:
        names = fieldnames or FIELDS
        writer = csv.DictWriter(handle, fieldnames=names, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    staged.replace(path)


def write_experiments_csv_atomic(path: Path, rows: list):
    staged = path.with_suffix(".csv.tmp")
    with staged.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow([
            "method", "project", "bug_id", "run", "tests",
            "compile", "code_coverage", "branch_coverage",
            "fault_detected", "time"
        ])
        writer.writerows(rows)
    staged.replace(path)


def run_target(name: str, source: Path, d4j: str, coverage: bool,
               test_timeout: int, algorithm: str = "Gemini",
               algorithm_version: str = "Gemini-3.5-Flash-Lite",
               experiment: str = "defects4j", run: int = 1):
    match = TARGET_RE.fullmatch(source.name)
    project, bug_id = match.group(1), int(match.group(2))
    
    # Destination according to COMMON_OUTPUT_FORMAT
    destination = RESULT_ROOT / experiment / algorithm / project / f"{project}-{bug_id}" / f"run{run}"
    destination.mkdir(parents=True, exist_ok=True)
    logs = destination / "logs"
    logs.mkdir(exist_ok=True)
    for old_log in logs.glob("*.log"):
        old_log.unlink()
    for old_failures in logs.glob("*_failing_tests.txt"):
        old_failures.unlink()
        
    report = {"tests": 0, "test_files": 0}
    try:
        with tempfile.TemporaryDirectory(prefix=f"gemini-{name}-") as temporary_name:
            temporary = Path(temporary_name)
            archive = temporary / f"{project}-{bug_id}b-gemini.1.tar.bz2"
            report["tests"], report["test_files"] = make_archive(source, archive)
            report["buggy"] = run_revision(d4j, project, bug_id, "b", archive,
                                           temporary, logs, coverage, test_timeout)
            shutil.rmtree(temporary / "buggy", ignore_errors=True)
            report["fixed"] = run_revision(d4j, project, bug_id, "f", archive,
                                           temporary, logs, False, test_timeout)
    except TargetInterrupted:
        return name, "INTERRUPTED"
    except (OSError, UnicodeError, ValueError, tarfile.TarError) as exc:
        report["error"] = str(exc)
    if STOP_REQUESTED.is_set():
        return name, "INTERRUPTED"

    buggy = report.get("buggy", {})
    fixed = report.get("fixed", {})
    a, b = buggy.get("result", "NOT_RUN"), fixed.get("result", "NOT_RUN")
    test_compile_buggy = buggy.get("test_compile", "")
    test_compile_fixed = fixed.get("test_compile", "")

    verdict = ("REVEALING" if (a, b) == ("FAIL", "PASS") else
               "NOT_REVEALING" if (a, b) == ("PASS", "PASS") else
               "NOT_AVAILABLE" if "NOT_RUN" in (a, b) else "INCONCLUSIVE")

    # Map to COMMON_OUTPUT_FORMAT status & fault_detected
    if test_compile_buggy == "FAIL" or test_compile_fixed == "FAIL" or a in ("NOT_RUN", None, ""):
        status = "validation_failed"
        fault_detected = None
        status_detail = "not_available"
    elif verdict == "REVEALING":
        status = "complete"
        fault_detected = True
        status_detail = "revealing"
    elif verdict == "NOT_REVEALING":
        status = "complete"
        fault_detected = False
        status_detail = "not_revealing"
    elif verdict == "INCONCLUSIVE":
        status = "complete"
        fault_detected = None
        status_detail = "inconclusive"
    else:
        status = "validation_failed"
        fault_detected = None
        status_detail = "not_available"

    tests_generated = report.get("tests", 0)
    fixed_fails = fixed.get("fails")
    fixed_failing = int(fixed_fails) if fixed_fails is not None else (0 if status == "complete" else None)
    tests_validated = max(0, tests_generated - (fixed_failing or 0)) if status == "complete" else 0
    buggy_fails = buggy.get("fails")

    code_coverage = None
    if buggy.get("lines") is not None and buggy.get("lines", 0) > 0:
        lines_total = buggy.get("lines", 0)
        lines_covered = buggy.get("covered_lines", 0)
        branches_total = buggy.get("branches", 0)
        branches_covered = buggy.get("covered_branches", 0)
        code_coverage = {
            "scope": "modified_classes",
            "revision": "buggy",
            "tool": "Defects4J/Cobertura",
            "lines_covered": lines_covered,
            "lines_total": lines_total,
            "line_ratio": round(lines_covered / lines_total, 6) if lines_total else None,
            "branch_measure": "condition",
            "branches_covered": branches_covered,
            "branches_total": branches_total,
            "branch_ratio": round(branches_covered / branches_total, 6) if branches_total else None
        }

    common_result = {
        "schema_version": "1.0",
        "experiment": experiment,
        "algorithm": algorithm,
        "algorithm_version": algorithm_version,
        "project": project,
        "bug_id": bug_id,
        "run": run,
        "status": status,
        "status_detail": status_detail,
        "error": report.get("error"),
        "tests_generated": tests_generated,
        "tests_validated": tests_validated,
        "fixed_failing_tests": fixed_failing,
        "buggy_failing_tests": buggy_fails,
        "fault_detected": fault_detected,
        "test_coverage": None,
        "code_coverage": code_coverage,
        "generation_seconds": None,
        "total_seconds": None,
        "configuration": {
            "generator": algorithm_version
        },
        "artifacts": {
            "tests": f"ai/Gemini/generated-tests/{project}_{bug_id}_buggy/",
            "logs": f"Results/{experiment}/{algorithm}/{project}/{project}-{bug_id}/run{run}/logs/"
        }
    }
    write_json_atomic(destination / "result.json", common_result)
    return name, verdict


def collect_reports(experiment="defects4j", algorithm="Gemini"):
    base_dir = RESULT_ROOT / experiment
    gemini_dir = base_dir / algorithm

    json_files = sorted(gemini_dir.glob("*/*/run*/result.json"))
    if not json_files:
        return 0

    groups = defaultdict(lambda: {
        "selected": set(), "evaluated": set(), "detected": set(), "unknown": set(),
        "complete_runs": 0, "line_ratios": [], "branch_ratios": [],
        "gen_seconds": [], "total_seconds": []
    })
    overall_data = defaultdict(lambda: {
        "selected": set(), "evaluated": set(), "detected": set(), "unknown": set(),
        "complete_runs": 0, "line_ratios": [], "branch_ratios": [],
        "gen_seconds": [], "total_seconds": []
    })

    exp_rows = []

    for jf in json_files:
        with jf.open(encoding="utf-8") as f:
            r = json.load(f)

        alg = r.get("algorithm", algorithm)
        proj = r.get("project", "")
        bug_id = r.get("bug_id", 0)
        key = (alg, proj)
        unique_key = (proj, bug_id)

        groups[key]["selected"].add(bug_id)
        overall_data[alg]["selected"].add(unique_key)

        is_complete = (r.get("status") == "complete")
        if is_complete:
            groups[key]["evaluated"].add(bug_id)
            groups[key]["complete_runs"] += 1
            overall_data[alg]["evaluated"].add(unique_key)
            overall_data[alg]["complete_runs"] += 1

            if r.get("fault_detected") is True:
                groups[key]["detected"].add(bug_id)
                overall_data[alg]["detected"].add(unique_key)
            elif r.get("fault_detected") is None:
                groups[key]["unknown"].add(bug_id)
                overall_data[alg]["unknown"].add(unique_key)

            cc = r.get("code_coverage") or {}
            lr = cc.get("line_ratio")
            br = cc.get("branch_ratio")
            if lr is not None:
                groups[key]["line_ratios"].append(lr)
                overall_data[alg]["line_ratios"].append(lr)
            if br is not None:
                groups[key]["branch_ratios"].append(br)
                overall_data[alg]["branch_ratios"].append(br)

        # Build row for experiments/results.csv
        cc = r.get("code_coverage") or {}
        line_cov = (cc.get("line_ratio", 0.0) or 0.0) * 100
        branch_cov = (cc.get("branch_ratio", 0.0) or 0.0) * 100
        exp_rows.append([
            alg, proj, str(bug_id), r.get("run", 1), r.get("tests_generated", 0),
            "TRUE" if is_complete else "FALSE",
            f"{line_cov:.2f}", f"{branch_cov:.2f}",
            "TRUE" if r.get("fault_detected") is True else "FALSE",
            "0.00"
        ])

    # 1. Write summary_by_project.csv
    csv_cols = [
        "algorithm", "project", "selected_unique_bugs", "evaluated_unique_bugs",
        "detected_unique_bugs", "unknown_outcome_bugs", "fault_detection_rate",
        "complete_runs", "mean_test_coverage_ratio", "mean_line_coverage_ratio",
        "mean_branch_coverage_ratio", "mean_generation_seconds", "mean_total_seconds"
    ]
    rows = []
    for (alg, proj), g in sorted(groups.items()):
        ev = len(g["evaluated"])
        dt = len(g["detected"])
        fdr = round(dt / ev, 6) if ev else 0.0
        line_m = round(sum(g["line_ratios"]) / len(g["line_ratios"]), 6) if g["line_ratios"] else ""
        branch_m = round(sum(g["branch_ratios"]) / len(g["branch_ratios"]), 6) if g["branch_ratios"] else ""
        rows.append({
            "algorithm": alg, "project": proj,
            "selected_unique_bugs": len(g["selected"]),
            "evaluated_unique_bugs": ev,
            "detected_unique_bugs": dt,
            "unknown_outcome_bugs": len(g["unknown"]),
            "fault_detection_rate": fdr,
            "complete_runs": g["complete_runs"],
            "mean_test_coverage_ratio": "",
            "mean_line_coverage_ratio": line_m,
            "mean_branch_coverage_ratio": branch_m,
            "mean_generation_seconds": "",
            "mean_total_seconds": ""
        })
    write_csv_atomic(base_dir / "summary_by_project.csv", rows, fieldnames=csv_cols)

    # 2. Write summary_overall.json
    overall_json = {}
    for alg, od in overall_data.items():
        ev = len(od["evaluated"])
        dt = len(od["detected"])
        fdr = round(dt / ev, 6) if ev else 0.0
        line_m = round(sum(od["line_ratios"]) / len(od["line_ratios"]), 6) if od["line_ratios"] else None
        branch_m = round(sum(od["branch_ratios"]) / len(od["branch_ratios"]), 6) if od["branch_ratios"] else None
        overall_json[alg] = {
            "selected_unique_bugs": len(od["selected"]),
            "evaluated_unique_bugs": ev,
            "detected_unique_bugs": dt,
            "unknown_outcome_bugs": len(od["unknown"]),
            "fault_detection_rate": fdr,
            "complete_runs": od["complete_runs"],
            "mean_test_coverage_ratio": None,
            "mean_line_coverage_ratio": line_m,
            "mean_branch_coverage_ratio": branch_m,
            "mean_generation_seconds": None,
            "mean_total_seconds": None
        }
    write_json_atomic(base_dir / "summary_overall.json", overall_json)

    # 3. Write Results/results.csv
    exp_csv_path = ROOT / "Results" / "results.csv"
    if exp_rows:
        write_experiments_csv_atomic(exp_csv_path, exp_rows)

    return len(json_files)


def main(argv=None):
    global TEST_ROOT, RESULT_ROOT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=4, help="Concurrent Defects4J checkouts (default: 4)")
    parser.add_argument("--projects", nargs="+", help="Project names, for example Chart Cli Lang")
    parser.add_argument("--targets", nargs="+", help="Exact targets, for example Chart_1 Lang_1")
    parser.add_argument("--limit", type=int, help="Run only the first N pending targets")
    parser.add_argument("--defects4j-bin", default=os.environ.get("DEFECTS4J_BIN", "defects4j"))
    parser.add_argument("--no-coverage", action="store_true", help="Skip buggy revision coverage")
    parser.add_argument("--test-timeout", type=int, default=600,
                        help="Seconds allowed for each test/coverage command (default: 600)")
    parser.add_argument("--overwrite", action="store_true", help="Rerun targets with existing result.json")
    parser.add_argument("--dry-run", action="store_true", help="List pending targets without running")
    parser.add_argument("--collect-only", action="store_true", help="Rebuild combined reports without running tests")
    parser.add_argument("--test-dir", help="Directory containing generated tests (default: ai/Gemini/generated-tests)")
    parser.add_argument("--result-dir", help="Root directory to save validation results (default: Results)")
    parser.add_argument("--experiment", default="defects4j", help="Experiment name (default: defects4j)")
    parser.add_argument("--algorithm", default="Gemini", help="Algorithm name (default: Gemini)")
    parser.add_argument("--run", type=int, default=1, help="Run number (default: 1)")
    args = parser.parse_args(argv)

    if args.test_dir:
        TEST_ROOT = Path(args.test_dir).resolve()
    else:
        TEST_ROOT = get_default_test_root()

    if args.result_dir:
        RESULT_ROOT = Path(args.result_dir).resolve()
    else:
        RESULT_ROOT = ROOT / "Results"

    if args.workers < 1 or args.test_timeout < 1 or (args.limit is not None and args.limit < 1):
        parser.error("--workers, --limit, and --test-timeout must be positive")
    if args.collect_only:
        RESULT_ROOT.mkdir(exist_ok=True)
        count = collect_reports(args.experiment, args.algorithm)
        print(f"Combined {count} reports into {RESULT_ROOT / args.experiment}: summary_by_project.csv, summary_overall.json", flush=True)
        return 0
    if not TEST_ROOT.is_dir():
        parser.error(f"Test directory is missing: {TEST_ROOT}")
    available = discover_targets()
    selected = sorted(available, key=lambda n: (n.split("_")[0], int(n.split("_")[1])))
    if args.projects:
        requested = {p.lower() for p in args.projects}
        selected = [n for n in selected if n.split("_")[0].lower() in requested]
        unknown = requested - {n.split("_")[0].lower() for n in available}
        if unknown:
            parser.error(f"Unknown projects: {', '.join(sorted(unknown))}")
    if args.targets:
        requested = set(args.targets)
        unknown = requested - available.keys()
        if unknown:
            parser.error(f"Unknown targets: {', '.join(sorted(unknown))}")
        selected = [n for n in selected if n in requested]

    def has_result(n):
        p, b = n.split("_")
        return (RESULT_ROOT / args.experiment / args.algorithm / p / f"{p}-{b}" / f"run{args.run}" / "result.json").is_file()

    pending = [n for n in selected if args.overwrite or not has_result(n)]
    if args.limit:
        pending = pending[:args.limit]
    print(f"Selected {len(selected)} targets; pending {len(pending)}; workers {args.workers}", flush=True)
    if args.dry_run:
        for name in pending:
            print(name)
        return 0
    if pending and not (Path(args.defects4j_bin).is_file() or shutil.which(args.defects4j_bin)):
        parser.error(f"Defects4J executable not found: {args.defects4j_bin}")
    RESULT_ROOT.mkdir(exist_ok=True)
    STOP_REQUESTED.clear()
    failures = 0
    interrupted = False
    pool = ThreadPoolExecutor(max_workers=args.workers)
    try:
        futures = {pool.submit(run_target, n, available[n], args.defects4j_bin,
                               not args.no_coverage, args.test_timeout,
                               args.algorithm, "Gemini-3.5-Flash-Lite",
                               args.experiment, args.run): n for n in pending}
        for future in as_completed(futures):
            name = futures[future]
            try:
                _, verdict = future.result()
                print(f"[{name}] {verdict}", flush=True)
                failures += verdict == "NOT_AVAILABLE"
            except Exception as exc:
                print(f"[{name}] runner error: {exc}", file=sys.stderr, flush=True)
                failures += 1
    except KeyboardInterrupt:
        interrupted = True
        STOP_REQUESTED.set()
        print("Interrupted; stopping active commands and updating combined report...",
              file=sys.stderr, flush=True)
        stop_active_commands()
    finally:
        pool.shutdown(wait=True, cancel_futures=interrupted)
    count = collect_reports(args.experiment, args.algorithm)
    print(f"Combined {count} reports into {RESULT_ROOT / args.experiment}", flush=True)
    if interrupted:
        return 130
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
