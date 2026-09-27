#!/usr/bin/env python3
"""Validate existing Gemini generated tests on both Defects4J revisions in parallel."""

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


ROOT = Path(__file__).resolve().parents[2]
TEST_ROOT = ROOT / "TestCode"
RESULT_ROOT = ROOT / "Result"
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


def write_csv_atomic(path: Path, rows):
    staged = path.with_suffix(".csv.tmp")
    with staged.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    staged.replace(path)


def report_row(report):
    buggy = report.get("buggy", {})
    fixed = report.get("fixed", {})
    row = {"project": report["project"], "bug_id": report["bug_id"],
           "tests": report.get("tests"),
           "buggy_result": buggy.get("result", "NOT_RUN"),
           "buggy_fails": buggy.get("fails"),
           "fixed_result": fixed.get("result", "NOT_RUN"),
           "fixed_fails": fixed.get("fails"),
           "verdict": report["verdict"]}
    row.update({key: buggy.get(key) for key in FIELDS[8:]})
    return row


def run_target(name: str, source: Path, d4j: str, coverage: bool,
               test_timeout: int):
    match = TARGET_RE.fullmatch(source.name)
    project, bug_id = match.group(1), int(match.group(2))
    destination = RESULT_ROOT / name
    destination.mkdir(parents=True, exist_ok=True)
    logs = destination / "logs"
    logs.mkdir(exist_ok=True)
    for old_log in logs.glob("*.log"):
        old_log.unlink()
    for old_failures in logs.glob("*_failing_tests.txt"):
        old_failures.unlink()
    report = {"schema_version": "1.0", "generator": "Gemini-3.5-Flash-Lite",
              "created_at": datetime.now(timezone.utc).isoformat(),
              "project": project, "bug_id": bug_id,
              "source": str(source), "coverage_scope": "modified classes of buggy revision"}
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
    report["verdict"] = ("REVEALING" if (a, b) == ("FAIL", "PASS") else
                         "NOT_REVEALING" if (a, b) == ("PASS", "PASS") else
                         "NOT_AVAILABLE" if "NOT_RUN" in (a, b) else "INCONCLUSIVE")
    write_csv_atomic(destination / "result.csv", [report_row(report)])
    write_json_atomic(destination / "result.json", report)
    return name, report["verdict"]


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


def collect_reports(version_tag="v1"):
    rows = []
    exp_rows = []
    
    if version_tag != "v1":
        gemini_res_dir = ROOT / "ai" / "Gemini" / f"results-{version_tag}"
        raw_gemini_dir = ROOT / "experiments" / "raw" / f"Gemini_{version_tag}"
        exp_csv_path = ROOT / "experiments" / f"results_{version_tag}.csv"
    else:
        gemini_res_dir = ROOT / "ai" / "Gemini" / "results"
        raw_gemini_dir = ROOT / "experiments" / "raw" / "Gemini"
        exp_csv_path = ROOT / "experiments" / "results.csv"

    gemini_res_dir.mkdir(parents=True, exist_ok=True)
    raw_gemini_dir.mkdir(parents=True, exist_ok=True)

    for path in sorted(RESULT_ROOT.glob("*/result.json")):
        with path.open(encoding="utf-8") as handle:
            data = json.load(handle)
            rows.append(report_row(data))

            target_name = path.parent.name
            try:
                shutil.copy2(path, gemini_res_dir / f"{target_name}.json")
                shutil.copy2(path, raw_gemini_dir / f"{target_name}.json")
            except Exception:
                pass

            project = data.get("project", target_name.split("_")[0])
            bug_id = data.get("bug_id", target_name.split("_")[1])
            tests = data.get("tests", 0)
            verdict = data.get("verdict", "NOT_AVAILABLE")
            buggy = data.get("buggy", {})

            compile_success = verdict != "NOT_AVAILABLE"
            fault_detected = verdict == "REVEALING"

            try:
                line_cov = float(buggy.get("line_cov", 0.0))
            except (ValueError, TypeError):
                line_cov = 0.0
            try:
                branch_cov = float(buggy.get("branch_cov", 0.0))
            except (ValueError, TypeError):
                branch_cov = 0.0

            exp_rows.append([
                "Gemini", project, str(bug_id), 1, tests,
                "TRUE" if compile_success else "FALSE",
                f"{line_cov:.2f}", f"{branch_cov:.2f}",
                "TRUE" if fault_detected else "FALSE",
                "0.00"
            ])

    write_csv_atomic(RESULT_ROOT / "report.csv", rows)
    if exp_rows:
        write_experiments_csv_atomic(exp_csv_path, exp_rows)
    return len(rows)


def main(argv=None):
    global TEST_ROOT, RESULT_ROOT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=4, help="Concurrent Defects4J checkouts (default: 4)")
    parser.add_argument("--projects", nargs="+", help="Project names, for example Chart Cli Lang")
    parser.add_argument("--targets", nargs="+", help="Exact targets, for example Chart_1 Lang_1")
    parser.add_argument("--limit", type=int, help="Run only the first N pending targets")
    parser.add_argument("--defects4j-bin", default=os.environ.get("DEFECTS4J_BIN", str(ROOT / "defects4j" / "framework" / "bin" / "defects4j")))
    parser.add_argument("--no-coverage", action="store_true", help="Skip buggy revision coverage")
    parser.add_argument("--test-timeout", type=int, default=600,
                        help="Seconds allowed for each test/coverage command (default: 600)")
    parser.add_argument("--overwrite", action="store_true", help="Rerun targets with existing result.json")
    parser.add_argument("--dry-run", action="store_true", help="List pending targets without running")
    parser.add_argument("--collect-only", action="store_true", help="Rebuild combined report without running tests")
    parser.add_argument("--test-dir", help="Directory containing generated tests")
    parser.add_argument("--result-dir", help="Directory to save validation results")
    parser.add_argument("--v2", action="store_true", help="Use v2 directories (TestCode_v2, Result_v2, results_v2.csv)")
    parser.add_argument("--version-tag", default=None, help="Version tag for results (default: v1, or v2 if --v2)")
    args = parser.parse_args(argv)

    if args.v2 or args.version_tag == "v2":
        v_tag = "v2"
        TEST_ROOT = Path(args.test_dir) if args.test_dir else ROOT / "TestCode_v2"
        RESULT_ROOT = Path(args.result_dir) if args.result_dir else ROOT / "Result_v2"
    else:
        v_tag = args.version_tag or "v1"
        TEST_ROOT = Path(args.test_dir) if args.test_dir else ROOT / "TestCode"
        RESULT_ROOT = Path(args.result_dir) if args.result_dir else ROOT / "Result"

    if args.workers < 1 or args.test_timeout < 1 or (args.limit is not None and args.limit < 1):
        parser.error("--workers, --limit, and --test-timeout must be positive")
    if args.collect_only:
        RESULT_ROOT.mkdir(exist_ok=True)
        count = collect_reports(v_tag)
        print(f"Combined {count} reports: {RESULT_ROOT / 'report.csv'}", flush=True)
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
    pending = [n for n in selected if args.overwrite or not (RESULT_ROOT / n / "result.json").is_file()]
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
                               not args.no_coverage, args.test_timeout): n for n in pending}
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
    count = collect_reports(v_tag)
    print(f"Combined {count} reports: {RESULT_ROOT / 'report.csv'}", flush=True)
    if interrupted:
        return 130
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
