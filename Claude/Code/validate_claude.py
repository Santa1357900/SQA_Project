#!/usr/bin/env python3
"""
Validate Claude-generated JUnit 4 suites on Defects4J and emit the group's
COMMON_OUTPUT_FORMAT result.json (see Ant Colony Optimization(ACO)/docs/COMMON_OUTPUT_FORMAT.md).

Must run inside Linux/WSL where `defects4j` is installed (JDK 8).

Per target (Tests/<exp>/Claude/<P>/<P>-<n>/run<r>/*.java):
  1. fixed  : checkout -> compile -> test -s suite.tar.bz2      -> fixed failing tests
  2. filter : drop @Test methods that fail on fixed             -> validated suite
  3. buggy  : checkout -> compile -> test -s validated suite    -> buggy failing tests
  4. buggy  : coverage -s validated suite                       -> summary.csv + coverage.xml
  5. write  Results/<exp>/Claude/<P>/<P>-<n>/run<r>/result.json (+ logs/)

Usage (from Windows PowerShell):
  wsl -d Ubuntu python3 /mnt/e/.../workspeac/Claude/Code/validate_claude.py --projects Lang --workers 4
  wsl -d Ubuntu python3 /mnt/e/.../workspeac/Claude/Code/validate_claude.py --all --workers 6
"""

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
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLAUDE_DIR = ROOT / "Claude"
CONFIG_FILE = CLAUDE_DIR / "Configuration" / "claude_config.json"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from summarize_claude import summarize  # noqa: E402

PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;")
FAILING_HEADER_RE = re.compile(r"(?m)^--- ([\w.$]+)(?:::(\w+))?")   # method is absent for class-level failures
TIMEOUT_RC = 124
STOP = threading.Event()
ACTIVE = set()
ACTIVE_LOCK = threading.Lock()
PRINT_LOCK = threading.Lock()


class Interrupted(Exception):
    pass


def log(msg):
    with PRINT_LOCK:
        print(msg, flush=True)


# Subprocess helpers

def run(args, logfile: Path, cwd=None, timeout=None) -> int:
    with logfile.open("a", encoding="utf-8") as out:
        out.write("$ " + " ".join(map(str, args)) + "\n")
        out.flush()
        with ACTIVE_LOCK:
            if STOP.is_set():
                raise Interrupted
            proc = subprocess.Popen(args, cwd=cwd, stdout=out, stderr=subprocess.STDOUT,
                                    start_new_session=True)
            ACTIVE.add(proc)
        try:
            try:
                rc = proc.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                out.write(f"[timeout after {timeout}s]\n")
                kill(proc)
                rc = TIMEOUT_RC
        finally:
            with ACTIVE_LOCK:
                ACTIVE.discard(proc)
        out.write(f"[exit {rc}]\n")
        if STOP.is_set():
            raise Interrupted
        return rc


def kill(proc):
    if proc.poll() is None:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.wait()


# Java suite helpers

def count_tests(code: str) -> int:
    return len(re.findall(r"@(?:org\.junit\.)?Test\b", code))


def make_archive(java_sources: dict, destination: Path) -> int:
    """java_sources: {file_name: code}. Files are placed under their package path."""
    total = 0
    with tarfile.open(destination, "w:bz2") as archive:
        for file_name, code in sorted(java_sources.items()):
            match = PACKAGE_RE.search(code)
            rel = Path(*(match.group(1).split(".") if match else [])) / file_name
            data = code.encode("utf-8")
            info = tarfile.TarInfo(rel.as_posix())
            info.size = len(data)
            info.mtime = int(time.time())
            import io
            archive.addfile(info, io.BytesIO(data))
            total += count_tests(code)
    return total


def parse_failing(workspace: Path) -> list:
    """Return [(fqcn, method), ...] from Defects4J's failing_tests file."""
    failing = workspace / "failing_tests"
    if not failing.is_file():
        return []
    text = failing.read_text(encoding="utf-8", errors="replace")
    return [(m.group(1), m.group(2)) for m in FAILING_HEADER_RE.finditer(text)]


def remove_test_methods(code: str, method_names: set) -> str:
    """Delete @Test-annotated methods whose name is in method_names (brace-aware)."""
    if not method_names:
        return code
    pattern = re.compile(
        r"(?:^[ \t]*//[^\n]*\n)*"                       # leading line comments
        r"[ \t]*@Test(?:\s*\([^)]*\))?\s*"              # @Test / @Test(expected=..)
        r"(?:@\w+(?:\([^)]*\))?\s*)*"                   # other annotations
        r"(?:public|protected)?\s*void\s+(\w+)\s*\([^)]*\)\s*(?:throws\s+[\w.,\s]+)?\s*\{",
        re.MULTILINE)
    out = []
    pos = 0
    for m in pattern.finditer(code):
        if m.start() < pos:
            continue
        if m.group(1) not in method_names:
            continue
        end = find_block_end(code, m.end() - 1)
        if end is None:
            continue
        out.append(code[pos:m.start()])
        pos = end + 1
    out.append(code[pos:])
    return "".join(out)


def find_block_end(code: str, open_index: int):
    """Index of the '}' matching code[open_index] == '{', skipping strings/chars/comments."""
    depth = 0
    i = open_index
    n = len(code)
    while i < n:
        c = code[i]
        if c == '"' or c == "'":
            quote = c
            i += 1
            while i < n and code[i] != quote:
                if code[i] == "\\":
                    i += 1
                i += 1
        elif code.startswith("//", i):
            i = code.find("\n", i)
            if i == -1:
                return None
        elif code.startswith("/*", i):
            i = code.find("*/", i + 2)
            if i == -1:
                return None
            i += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return None


# Coverage parsing (Cobertura XML from `defects4j coverage`)

def read_summary_csv(workspace: Path):
    path = workspace / "summary.csv"
    if not path.is_file():
        return None
    with path.open(newline="", encoding="utf-8") as fh:
        row = next(csv.DictReader(fh), None)
    if not row:
        return None
    try:
        return {"lines_total": int(row["LinesTotal"]), "lines_covered": int(row["LinesCovered"]),
                "branches_total": int(row["ConditionsTotal"]),
                "branches_covered": int(row["ConditionsCovered"])}
    except (KeyError, ValueError):
        return None


def method_coverage(coverage_xml: Path):
    """Count methods with executable lines (skip <clinit>, synthetic '$', abstract=no lines)."""
    if not coverage_xml.is_file():
        return None
    try:
        tree = ET.parse(coverage_xml)
    except ET.ParseError:
        return None
    total = covered = 0
    for method in tree.getroot().iter("method"):
        name = method.get("name", "")
        if name == "<clinit>" or "$" in name:
            continue
        lines = list(method.iter("line"))
        if not lines:
            continue
        total += 1
        if any(int(line.get("hits", "0")) > 0 for line in lines):
            covered += 1
    return {"total_methods": total, "covered_methods": covered}


def ratio(num, den):
    if num is None or not den:
        return None
    return round(num / den, 4)


# One target

def base_result(config, project, bug_id, run, meta, test_dir, result_dir):
    return {
        "schema_version": "1.0",
        "experiment": config["experiment"],
        "algorithm": config["algorithm"],
        "algorithm_version": config["algorithm_version"],
        "project": project,
        "bug_id": bug_id,
        "run": run,
        "status": "running",
        "status_detail": "running",
        "error": None,
        "tests_generated": meta.get("tests_generated", 0),
        "tests_validated": 0,
        "fixed_failing_tests": None,
        "buggy_failing_tests": None,
        "fault_detected": None,
        "test_coverage": {"scope": "modified_classes", "revision": "buggy",
                          "covered_methods": None, "total_methods": None, "ratio": None},
        "code_coverage": {"scope": "modified_classes", "revision": "buggy",
                          "tool": "Defects4J/Cobertura",
                          "lines_covered": None, "lines_total": None, "line_ratio": None,
                          "branch_measure": "condition",
                          "branches_covered": None, "branches_total": None, "branch_ratio": None},
        "generation_seconds": meta.get("generation_seconds"),
        "validation_seconds": None,
        "total_seconds": None,
        "configuration": {
            "backend": meta.get("backend"),
            "model": meta.get("model", config.get("model")),
            "effort": meta.get("effort", config.get("effort")),
            "thinking": meta.get("thinking", config.get("thinking")),
            "max_tokens": meta.get("max_tokens", config.get("max_tokens")),
            "prompt_system": config["prompt_files"]["system"],
            "prompt_template": config["prompt_files"]["user_template"],
            "usage": meta.get("usage"),
        },
        "artifacts": {
            "tests": test_dir.relative_to(CLAUDE_DIR).as_posix() + "/",
            "logs": (result_dir / "logs").relative_to(CLAUDE_DIR).as_posix() + "/",
            "prompt": meta.get("prompt_history"),
        },
        "created_at": datetime.now(timezone.utc).isoformat(),
    }


def finish(result, status, detail=None, error=None):
    result["status"] = status
    result["status_detail"] = detail or status
    result["error"] = error
    return result


def checkout_compile(d4j, project, bug_id, version, workspace: Path, logfile: Path) -> str:
    if run([d4j, "checkout", "-p", project, "-v", f"{bug_id}{version}", "-w", str(workspace)], logfile):
        return "checkout_failed"
    if run([d4j, "compile", "-w", str(workspace)], logfile):
        return "compile_failed"
    return "ok"


def test_compile_ok(test_log: Path):
    text = test_log.read_text(encoding="utf-8", errors="replace")
    if re.search(r"compile\.gen\.tests\).*FAIL", text):
        return False
    return True


def validate_target(config, d4j, project, bug_id, run_no, test_dir: Path, timeout: int):
    result_dir = (CLAUDE_DIR / "Results" / config["experiment"] / config["algorithm"]
                  / project / f"{project}-{bug_id}" / f"run{run_no}")
    logs = result_dir / "logs"
    if logs.exists():
        shutil.rmtree(logs)
    logs.mkdir(parents=True)

    meta_file = test_dir / "generation.json"
    meta = json.loads(meta_file.read_text(encoding="utf-8")) if meta_file.is_file() else {}
    result = base_result(config, project, bug_id, run_no, meta, test_dir, result_dir)
    started = time.perf_counter()

    def done(status, detail=None, error=None):
        finish(result, status, detail, error)
        result["validation_seconds"] = round(time.perf_counter() - started, 3)
        gen = result.get("generation_seconds") or 0.0
        result["total_seconds"] = round(gen + result["validation_seconds"], 3)
        write_json(result_dir / "result.json", result)
        return result

    java_sources = {p.name: p.read_text(encoding="utf-8-sig", errors="replace")
                    for p in sorted(test_dir.glob("*.java"))}
    if meta.get("status") not in (None, "ok") and meta.get("status") != "truncated":
        return done("no_generated_tests", f"generation_{meta.get('status')}", meta.get("error"))
    if not java_sources or sum(count_tests(c) for c in java_sources.values()) == 0:
        return done("no_generated_tests", "no_test_methods")
    result["tests_generated"] = sum(count_tests(c) for c in java_sources.values())

    try:
        with tempfile.TemporaryDirectory(prefix=f"claude-{project}-{bug_id}-") as tmp:
            tmp = Path(tmp)
            suite = tmp / f"{project}-{bug_id}f-claude.{run_no}.tar.bz2"
            make_archive(java_sources, suite)

            # ---- 1. fixed revision: which tests are valid? ----
            fixed_ws = tmp / "fixed"
            fixed_log = logs / "fixed.log"
            state = checkout_compile(d4j, project, bug_id, "f", fixed_ws, fixed_log)
            if state != "ok":
                return done("error", f"fixed_{state}")
            (fixed_ws / "failing_tests").unlink(missing_ok=True)
            rc = run([d4j, "test", "-s", str(suite)], logs / "fixed_test.log", cwd=fixed_ws, timeout=timeout)
            if rc == TIMEOUT_RC:
                return done("timeout", "fixed_test_timeout")
            if not test_compile_ok(logs / "fixed_test.log"):
                return done("no_valid_tests", "test_compile_failed")
            if rc != 0 and not (fixed_ws / "failing_tests").exists():
                return done("validation_failed", "fixed_test_harness_failed")
            fixed_failing = parse_failing(fixed_ws)
            copy_if_exists(fixed_ws / "failing_tests", logs / "fixed_failing_tests")
            copy_if_exists(fixed_ws / "all_tests", logs / "fixed_all_tests")
            result["fixed_failing_tests"] = len(fixed_failing)
            if any(method is None for _, method in fixed_failing):
                # Whole test class failed to initialise (e.g. runner/classpath problem):
                # no individual test ever ran, so nothing can be validated.
                return done("no_valid_tests", "test_class_initialization_failed")

            # ---- 2. filter out tests that fail on fixed ----
            bad_methods = {m for _, m in fixed_failing}
            validated_sources = {name: remove_test_methods(code, bad_methods)
                                 for name, code in java_sources.items()}
            validated_count = sum(count_tests(c) for c in validated_sources.values())
            result["tests_validated"] = validated_count
            if validated_count == 0:
                return done("no_valid_tests", "all_tests_fail_on_fixed")
            validated_dir = result_dir / "validated_tests"
            if validated_dir.exists():
                shutil.rmtree(validated_dir)
            validated_dir.mkdir()
            for name, code in validated_sources.items():
                (validated_dir / name).write_text(code, encoding="utf-8")
            vsuite = tmp / f"{project}-{bug_id}b-claude.{run_no}.tar.bz2"
            make_archive(validated_sources, vsuite)
            shutil.rmtree(fixed_ws, ignore_errors=True)

            # ---- 3. buggy revision: fault detection ----
            buggy_ws = tmp / "buggy"
            buggy_log = logs / "buggy.log"
            state = checkout_compile(d4j, project, bug_id, "b", buggy_ws, buggy_log)
            if state != "ok":
                return done("error", f"buggy_{state}")
            (buggy_ws / "failing_tests").unlink(missing_ok=True)
            rc = run([d4j, "test", "-s", str(vsuite)], logs / "buggy_test.log", cwd=buggy_ws, timeout=timeout)
            if rc == TIMEOUT_RC:
                return done("timeout", "buggy_test_timeout")
            if not test_compile_ok(logs / "buggy_test.log"):
                # Valid on fixed but not compilable on buggy (API differs between revisions).
                return done("validation_failed", "buggy_test_compile_failed")
            if rc != 0 and not (buggy_ws / "failing_tests").exists():
                return done("validation_failed", "buggy_test_harness_failed")
            buggy_failing = parse_failing(buggy_ws)
            copy_if_exists(buggy_ws / "failing_tests", logs / "failing_tests")
            copy_if_exists(buggy_ws / "all_tests", logs / "all_tests")
            result["buggy_failing_tests"] = len(buggy_failing)
            result["fault_detected"] = len(buggy_failing) > 0

            # ---- 4. coverage on buggy (modified classes only) ----
            rc = run([d4j, "coverage", "-s", str(vsuite)], logs / "coverage.log", cwd=buggy_ws, timeout=timeout)
            if rc == TIMEOUT_RC:
                return done("timeout", "coverage_timeout")
            if rc != 0:
                return done("validation_failed", "coverage_failed")
            copy_if_exists(buggy_ws / "summary.csv", logs / "summary.csv")
            copy_if_exists(buggy_ws / "coverage.xml", logs / "coverage.xml")
            summary = read_summary_csv(buggy_ws)
            if summary is None:
                return done("validation_failed", "coverage_summary_missing")
            cc = result["code_coverage"]
            cc.update(summary)
            cc["line_ratio"] = ratio(summary["lines_covered"], summary["lines_total"])
            cc["branch_ratio"] = ratio(summary["branches_covered"], summary["branches_total"])
            methods = method_coverage(buggy_ws / "coverage.xml")
            if methods:
                tc = result["test_coverage"]
                tc.update(methods)
                tc["ratio"] = ratio(methods["covered_methods"], methods["total_methods"])
            return done("complete")
    except Interrupted:
        return done("interrupted")
    except (OSError, UnicodeError, ValueError, tarfile.TarError) as exc:
        return done("error", "runner_exception", str(exc))


def copy_if_exists(src: Path, dst: Path):
    if src.is_file():
        shutil.copy2(src, dst)


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    staged = path.with_suffix(".json.tmp")
    staged.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    staged.replace(path)


# CLI

def default_defects4j_bin() -> str:
    candidate = Path.home() / "defects4j" / "framework" / "bin" / "defects4j"
    if candidate.is_file():
        # Defects4J 3.x needs Java 11; make sure a JDK 11 is first on PATH if one is installed.
        for jvm in sorted(Path("/usr/lib/jvm").glob("java-11-*")) if Path("/usr/lib/jvm").is_dir() else []:
            os.environ["JAVA_HOME"] = str(jvm)
            os.environ["PATH"] = f"{jvm / 'bin'}:{os.environ.get('PATH', '')}"
            break
        return str(candidate)
    return "defects4j"


def discover(config, runs):
    root = CLAUDE_DIR / "Tests" / config["experiment"] / config["algorithm"]
    found = []
    if not root.is_dir():
        return found
    for run_dir in sorted(root.glob("*/*-*/run*")):
        m = re.fullmatch(r"([A-Za-z]+)-(\d+)", run_dir.parent.name)
        r = re.fullmatch(r"run(\d+)", run_dir.name)
        if not m or not r:
            continue
        if runs and int(r.group(1)) not in runs:
            continue
        # generation.json is written last by the generator; without it the suite is still in flight.
        if not (run_dir / "generation.json").is_file():
            continue
        found.append((m.group(1), int(m.group(2)), int(r.group(1)), run_dir))
    return sorted(found, key=lambda t: (t[0], t[1], t[2]))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--projects", nargs="+", help="Project names, e.g. Lang Math")
    parser.add_argument("--bugs", nargs="+", type=int, help="Bug ids (with a single project)")
    parser.add_argument("--runs", nargs="+", type=int, help="Run numbers to validate (default: all)")
    parser.add_argument("--all", action="store_true", help="Every generated target")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--workers", type=int, help="Parallel Defects4J jobs (default: validation_workers in config)")
    parser.add_argument("--defects4j-bin", default=os.environ.get("DEFECTS4J_BIN") or default_defects4j_bin(),
                        help="defects4j executable (default: $DEFECTS4J_BIN, then ~/defects4j/framework/bin/defects4j, then PATH)")
    parser.add_argument("--test-timeout", type=int, default=900, help="Seconds per test/coverage command")
    parser.add_argument("--overwrite", action="store_true", help="Re-validate targets that already have result.json")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--summarize-only", action="store_true", help="Only rebuild summary files")
    args = parser.parse_args(argv)

    config = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    if not args.workers:
        args.workers = int(config.get("validation_workers", 4))
    if args.summarize_only:
        summarize(config)
        return 0
    if not (args.projects or args.all):
        parser.error("choose --projects or --all")

    targets = discover(config, set(args.runs or []))
    if args.projects:
        wanted = {p.lower() for p in args.projects}
        targets = [t for t in targets if t[0].lower() in wanted]
        if args.bugs:
            targets = [t for t in targets if t[1] in set(args.bugs)]

    def result_path(t):
        return (CLAUDE_DIR / "Results" / config["experiment"] / config["algorithm"]
                / t[0] / f"{t[0]}-{t[1]}" / f"run{t[2]}" / "result.json")

    def needs_run(t):
        result = result_path(t)
        if args.overwrite or not result.is_file():
            return True
        # Re-validate when the suite was regenerated after the last result was written.
        return (t[3] / "generation.json").stat().st_mtime > result.stat().st_mtime

    pending = [t for t in targets if needs_run(t)]
    if args.limit:
        pending = pending[:args.limit]
    print(f"Targets {len(targets)} | pending {len(pending)} | workers {args.workers}", flush=True)
    if args.dry_run:
        for t in pending:
            print(f"{t[0]}-{t[1]} run{t[2]}")
        return 0
    if pending and not (shutil.which(args.defects4j_bin) or Path(args.defects4j_bin).is_file()):
        parser.error(f"defects4j not found: {args.defects4j_bin} (set DEFECTS4J_BIN or PATH)")

    interrupted = False
    pool = ThreadPoolExecutor(max_workers=args.workers)
    try:
        futures = {pool.submit(validate_target, config, args.defects4j_bin, p, b, r, d, args.test_timeout): (p, b, r)
                   for p, b, r, d in pending}
        for fut in as_completed(futures):
            p, b, r = futures[fut]
            try:
                res = fut.result()
                cov = res["code_coverage"]["line_ratio"]
                log(f"[{p}-{b} run{r}] {res['status']} ({res['status_detail']}) "
                    f"valid={res['tests_validated']}/{res['tests_generated']} "
                    f"fault={res['fault_detected']} line={cov}")
            except Exception as exc:
                log(f"[{p}-{b} run{r}] runner error: {exc}")
    except KeyboardInterrupt:
        interrupted = True
        STOP.set()
        with ACTIVE_LOCK:
            procs = list(ACTIVE)
        for proc in procs:
            kill(proc)
        print("Interrupted.", file=sys.stderr, flush=True)
    finally:
        pool.shutdown(wait=True, cancel_futures=interrupted)
    summarize(config)
    return 130 if interrupted else 0


if __name__ == "__main__":
    sys.exit(main())
