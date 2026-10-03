#!/usr/bin/env python3
"""
ACO test generation + evaluation on Defects4J (run inside Linux/WSL).

Per bug:
  1. checkout + compile the buggy and the fixed revision
  2. AcoSearch on the buggy classes (coverage-guided ant colony, JaCoCo probes as feedback)
  3. AcoOracle on the fixed classes, twice: the stable outcome becomes the expected value
  4. write acogen/AcoGeneratedTest.java (+ the replay helper)
  5. defects4j test on fixed -> drop failing tests -> defects4j test + coverage on buggy
  6. Results/<experiment>/ACO/<Project>/<Project>-<bug>/run<N>/result.json

Usage:
  python3 Code/run_aco.py --projects Lang --bugs 1 --workers 1
  python3 Code/run_aco.py --all --workers 16
  python3 Code/run_aco.py --all --runs 1 2 3        # run N uses seed N
"""

import argparse
import csv
import io
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import threading
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

ACO_DIR = Path(__file__).resolve().parents[1]
CODE = ACO_DIR / "Code"
CONFIG = ACO_DIR / "Configuration" / "aco_config.properties"
EXPERIMENT = "defects4j-round2"
ALGORITHM = "ACO"
VERSION = "2.0.0"
HOME = Path.home()
TOOL = Path(os.environ.get("ACO_TOOL", HOME / "aco-work" / "tool"))
WORK = Path(os.environ.get("ACO_WORK", HOME / "aco-work" / "jobs"))
D4J = os.environ.get("DEFECTS4J_BIN") or str(HOME / "defects4j" / "framework" / "bin" / "defects4j")

PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([\w.]+)\s*;")
FAILING_RE = re.compile(r"(?m)^--- ([\w.$]+)(?:::(\w+))?")
UNSTABLE_RE = re.compile(r"E:java\.lang\.(OutOfMemoryError|StackOverflowError|ThreadDeath)")
TIMEOUT_RC = 124
STOP = threading.Event()
ACTIVE = set()
LOCK = threading.Lock()
PRINT = threading.Lock()


class Interrupted(Exception):
    pass


def log(msg):
    with PRINT:
        print(msg, flush=True)


def environment():
    env = dict(os.environ)
    env["TZ"] = "America/Los_Angeles"
    jvms = sorted(Path("/usr/lib/jvm").glob("java-11-openjdk-*")) if Path("/usr/lib/jvm").is_dir() else []
    if jvms:
        env["JAVA_HOME"] = str(jvms[0])
        env["PATH"] = f"{jvms[0] / 'bin'}:{env.get('PATH', '')}"
    return env


ENV = environment()


def run(args, logfile, cwd=None, timeout=None, capture=False):
    """Run a command, append its output to logfile, return rc (or (rc, stdout) with capture)."""
    with open(logfile, "a", encoding="utf-8") as out:
        out.write("$ " + " ".join(map(str, args))[:2000] + "\n")
        out.flush()
        with LOCK:
            if STOP.is_set():
                raise Interrupted
            proc = subprocess.Popen(args, cwd=cwd, env=ENV, start_new_session=True,
                                    stdout=subprocess.PIPE if capture else out, stderr=out, text=True)
            ACTIVE.add(proc)
        stdout = ""
        try:
            try:
                stdout, _ = proc.communicate(timeout=timeout)
                rc = proc.returncode
            except subprocess.TimeoutExpired:
                kill(proc)
                out.write(f"[timeout after {timeout}s]\n")
                rc = TIMEOUT_RC
        finally:
            with LOCK:
                ACTIVE.discard(proc)
        out.write(f"[exit {rc}]\n")
        if STOP.is_set():
            raise Interrupted
        return (rc, stdout or "") if capture else rc


def kill(proc):
    for sig in (signal.SIGTERM, signal.SIGKILL):
        if proc.poll() is not None:
            break
        try:
            os.killpg(proc.pid, sig)
        except ProcessLookupError:
            break
        try:
            proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            continue
    try:
        proc.communicate(timeout=5)
    except Exception:
        pass


def load_properties(path):
    props = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def export(prop, checkout, logfile):
    rc, out = run([D4J, "export", "-p", prop, "-w", str(checkout)], logfile, capture=True, timeout=300)
    return out.strip() if rc == 0 else ""


def java_literal(text):
    out = []
    for ch in text:
        code = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif 32 <= code < 127:
            out.append(ch)
        else:
            out.append("\\u%04x" % code)
    return '"' + "".join(out) + '"'


def write_suite(suite_dir, rows):
    """rows: [(label, spec, expected)] -> acogen/AcoGeneratedTest.java + helper."""
    pkg = suite_dir / "acogen"
    pkg.mkdir(parents=True, exist_ok=True)
    lines = ["package acogen;", "", "import static org.junit.Assert.assertEquals;", "",
             "import org.junit.FixMethodOrder;", "import org.junit.Test;",
             "import org.junit.runners.MethodSorters;", "",
             "/** Generated by the ACO search; expected values were recorded on the fixed revision. */",
             "@FixMethodOrder(MethodSorters.NAME_ASCENDING)", "public class AcoGeneratedTest {", ""]
    for number, (label, spec, expected) in enumerate(rows, 1):
        method = re.sub(r"\W", "_", label.split("(")[0].split(".")[-1]).strip("_") or "call"
        lines += [f"    // {label}",
                  "    @Test(timeout = 20000)",
                  f"    public void test{number:04d}_{method}() throws Throwable {{",
                  f"        assertEquals({java_literal(expected)},",
                  f"                AcoReplay.run({java_literal(spec)}));",
                  "    }", ""]
    lines.append("}")
    (pkg / "AcoGeneratedTest.java").write_text("\n".join(lines) + "\n", encoding="utf-8")
    helper = (CODE / "AcoReplay.java").read_text(encoding="utf-8")
    (pkg / "AcoReplay_scaffolding.java").write_text("package acogen;\n\n" + helper, encoding="utf-8")


def count_tests(code):
    return len(re.findall(r"@Test\b", code))


def make_archive(suite_dir, destination, sources=None):
    """Pack the suite; `sources` overrides file contents by file name."""
    with tarfile.open(destination, "w:bz2") as archive:
        for path in sorted(suite_dir.rglob("*.java")):
            code = (sources or {}).get(path.name) or path.read_text(encoding="utf-8")
            data = code.encode("utf-8")
            info = tarfile.TarInfo(path.relative_to(suite_dir).as_posix())
            info.size = len(data)
            info.mtime = int(time.time())
            archive.addfile(info, io.BytesIO(data))


def parse_failing(workspace):
    path = workspace / "failing_tests"
    if not path.is_file():
        return []
    return [(m.group(1), m.group(2)) for m in FAILING_RE.finditer(path.read_text(encoding="utf-8", errors="replace"))]


def remove_tests(code, names):
    """Delete the @Test methods (with their comment line) whose name is in `names`."""
    if not names:
        return code
    pattern = re.compile(r"    // [^\n]*\n    @Test\(timeout = \d+\)\n    public void (\w+)\(\) throws Throwable \{\n"
                         r"(?:        [^\n]*\n)+?    \}\n\n")
    return pattern.sub(lambda m: "" if m.group(1) in names else m.group(0), code)


def read_summary(workspace):
    path = workspace / "summary.csv"
    if not path.is_file():
        return None
    with path.open(newline="", encoding="utf-8") as fh:
        row = next(csv.DictReader(fh), None)
    try:
        return {"lines_total": int(row["LinesTotal"]), "lines_covered": int(row["LinesCovered"]),
                "branches_total": int(row["ConditionsTotal"]), "branches_covered": int(row["ConditionsCovered"])}
    except (KeyError, TypeError, ValueError):
        return None


def method_coverage(xml_path):
    if not xml_path.is_file():
        return None
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return None
    total = covered = 0
    for method in root.iter("method"):
        name = method.get("name", "")
        lines = list(method.iter("line"))
        if name == "<clinit>" or "$" in name or not lines:
            continue
        total += 1
        covered += any(int(line.get("hits", "0")) > 0 for line in lines)
    return {"total_methods": total, "covered_methods": covered}


def ratio(a, b):
    return round(a / b, 4) if a is not None and b else None


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    tmp.replace(path)


def copy(src, dst):
    if src.is_file():
        shutil.copy2(src, dst)


def run_job(project, bug, run_no, props, args):
    rel = Path(EXPERIMENT) / ALGORITHM / project / f"{project}-{bug}" / f"run{run_no}"
    result_dir = ACO_DIR / "Results" / rel
    suite_dir = ACO_DIR / "Tests" / rel
    logs = result_dir / "logs"
    for d in (logs, suite_dir):
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True)
    work = WORK / f"{project}-{bug}-r{run_no}"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    seed = int(props.get("random_seed", "1")) + run_no - 1

    result = {
        "schema_version": "1.0", "experiment": EXPERIMENT, "algorithm": ALGORITHM, "algorithm_version": VERSION,
        "project": project, "bug_id": bug, "run": run_no,
        "status": "running", "status_detail": "running", "error": None,
        "tests_generated": 0, "tests_validated": 0,
        "fixed_failing_tests": None, "buggy_failing_tests": None, "fault_detected": None,
        "test_coverage": {"scope": "modified_classes", "revision": "buggy",
                          "covered_methods": None, "total_methods": None, "ratio": None},
        "code_coverage": {"scope": "modified_classes", "revision": "buggy", "tool": "Defects4J/Cobertura",
                          "lines_covered": None, "lines_total": None, "line_ratio": None,
                          "branch_measure": "condition",
                          "branches_covered": None, "branches_total": None, "branch_ratio": None},
        "generation_seconds": None, "total_seconds": None,
        "configuration": {
            "num_ants": int(props.get("number_of_ants", 20)), "iterations": int(props.get("iterations", 30)),
            "alpha": float(props.get("alpha", 1.0)), "beta": float(props.get("beta", 2.0)),
            "evaporation": float(props.get("evaporation_rate", 0.25)),
            "initial_pheromone": float(props.get("initial_pheromone", 1.0)), "seed": seed,
            "time_budget_seconds": int(props.get("time_budget_seconds", 30)),
            "max_tests": int(props.get("max_tests", 150)),
            "search_revision": "buggy", "oracle_revision": "fixed", "search_coverage_tool": "JaCoCo 0.8.12",
        },
        "search": None,
        "artifacts": {"tests": f"Tests/{rel.as_posix()}/", "logs": f"Results/{rel.as_posix()}/logs/"},
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    started = time.perf_counter()

    def done(status, detail=None, error=None):
        result["status"] = status
        result["status_detail"] = detail or status
        result["error"] = error
        result["total_seconds"] = round(time.perf_counter() - started, 3)
        write_json(result_dir / "result.json", result)
        if not args.keep_work:
            shutil.rmtree(work, ignore_errors=True)
        return result

    try:
        buggy, fixed = work / "b", work / "f"
        for version, target in (("b", buggy), ("f", fixed)):
            setup = logs / f"checkout_{version}.log"
            if run([D4J, "checkout", "-p", project, "-v", f"{bug}{version}", "-w", str(target)], setup, timeout=900):
                return done("error", f"checkout_{version}_failed")
            if run([D4J, "compile", "-w", str(target)], setup, timeout=1200):
                return done("error", f"compile_{version}_failed")

        meta = logs / "export.log"
        classes = [c for c in export("classes.modified", buggy, meta).split() if c]
        cp_buggy = export("cp.test", buggy, meta)
        cp_fixed = export("cp.test", fixed, meta)
        bin_dir = buggy / export("dir.bin.classes", buggy, meta)
        if not classes or not cp_buggy or not cp_fixed:
            return done("error", "metadata_missing")

        # ---- search on the buggy revision
        specs_file, stats_file = work / "specs.tsv", work / "stats.json"
        job_props = dict(props)
        job_props.update({"classes": ",".join(classes), "bin_dir": str(bin_dir), "output": str(specs_file),
                          "stats": str(stats_file), "random_seed": str(seed)})
        (work / "aco.properties").write_text("".join(f"{k}={v}\n" for k, v in job_props.items()), encoding="utf-8")
        includes = ":".join(f"{c}:{c}$*" for c in classes)
        budget = int(props.get("time_budget_seconds", 30))
        gen_started = time.perf_counter()
        rc = run(["java", f"-Xmx{args.heap}", "-Xss4m",
                  f"-javaagent:{TOOL / 'jacocoagent.jar'}=output=none,includes={includes}",
                  "-cp", f"{TOOL / 'classes'}:{TOOL / 'jacococore.jar'}:{cp_buggy}",
                  "AcoSearch", str(work / "aco.properties")],
                 logs / "search.log", cwd=buggy, timeout=budget + 120)
        if stats_file.is_file():
            try:
                result["search"] = json.loads(stats_file.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                pass
            copy(stats_file, suite_dir / "search_stats.json")
        found = [line.split("\t") for line in specs_file.read_text(encoding="utf-8").splitlines()] \
            if specs_file.is_file() else []
        found = [f for f in found if len(f) >= 3]
        if not found:
            result["generation_seconds"] = round(time.perf_counter() - gen_started, 3)
            detail = "search_timeout" if rc == TIMEOUT_RC else "search_failed" if rc else \
                "no_callable_methods" if (result["search"] or {}).get("methods") == 0 else "no_tests_found"
            return done("no_generated_tests", detail)

        # ---- expected values from the fixed revision (two replays must agree)
        outcomes = []
        for attempt in (1, 2):
            target = work / f"expected{attempt}.txt"
            run(["java", f"-Xmx{args.heap}", "-Xss4m", "-cp", f"{TOOL / 'classes'}:{cp_fixed}",
                 "AcoOracle", str(specs_file), str(target), "5000"],
                logs / "oracle.log", cwd=fixed, timeout=300 + 6 * len(found))
            outcomes.append(target.read_text(encoding="utf-8").splitlines() if target.is_file() else [])
        rows = []
        for i, (label, spec, *_rest) in enumerate(found):
            first = outcomes[0][i] if i < len(outcomes[0]) else None
            second = outcomes[1][i] if i < len(outcomes[1]) else None
            if first and first == second and first != "TIMEOUT" and not UNSTABLE_RE.match(first):
                rows.append((label, spec, first))
        rows = rows[:int(props.get("max_tests", 150)) * 2]
        result["generation_seconds"] = round(time.perf_counter() - gen_started, 3)
        result["search"] = dict(result["search"] or {}, specs_found=len(found), stable_on_fixed=len(rows))
        shutil.copy2(specs_file, suite_dir / "specs.tsv")
        if not rows:
            return done("no_generated_tests", "no_stable_oracle")
        write_suite(suite_dir, rows)
        result["tests_generated"] = len(rows)
        shutil.rmtree(buggy / ".classes_testgen", ignore_errors=True)

        # ---- validate on fixed
        suite = work / f"{project}-{bug}f-aco.{run_no}.tar.bz2"
        make_archive(suite_dir, suite)
        (fixed / "failing_tests").unlink(missing_ok=True)
        rc = run([D4J, "test", "-s", str(suite)], logs / "fixed_test.log", cwd=fixed, timeout=args.test_timeout)
        if rc == TIMEOUT_RC:
            return done("timeout", "fixed_test_timeout")
        text = (logs / "fixed_test.log").read_text(encoding="utf-8", errors="replace")
        if re.search(r"compile\.gen\.tests\).*FAIL", text):
            return done("no_valid_tests", "test_compile_failed")
        if rc != 0 and not (fixed / "failing_tests").exists():
            return done("validation_failed", "fixed_test_harness_failed")
        failing = parse_failing(fixed)
        copy(fixed / "failing_tests", logs / "fixed_failing_tests")
        result["fixed_failing_tests"] = len(failing)
        if any(method is None for _, method in failing):
            return done("no_valid_tests", "test_class_initialization_failed")
        source = (suite_dir / "acogen" / "AcoGeneratedTest.java").read_text(encoding="utf-8")
        validated_source = remove_tests(source, {m for _, m in failing})
        validated = count_tests(validated_source)
        result["tests_validated"] = validated
        if validated == 0:
            return done("no_valid_tests", "all_tests_fail_on_fixed")
        validated_dir = result_dir / "validated_tests"
        shutil.rmtree(validated_dir, ignore_errors=True)
        validated_dir.mkdir()
        (validated_dir / "AcoGeneratedTest.java").write_text(validated_source, encoding="utf-8")
        vsuite = work / f"{project}-{bug}b-aco.{run_no}.tar.bz2"
        make_archive(suite_dir, vsuite, {"AcoGeneratedTest.java": validated_source})

        # ---- fault detection and coverage on buggy
        (buggy / "failing_tests").unlink(missing_ok=True)
        rc = run([D4J, "test", "-s", str(vsuite)], logs / "buggy_test.log", cwd=buggy, timeout=args.test_timeout)
        if rc == TIMEOUT_RC:
            return done("timeout", "buggy_test_timeout")
        text = (logs / "buggy_test.log").read_text(encoding="utf-8", errors="replace")
        if re.search(r"compile\.gen\.tests\).*FAIL", text):
            return done("validation_failed", "buggy_test_compile_failed")
        if rc != 0 and not (buggy / "failing_tests").exists():
            return done("validation_failed", "buggy_test_harness_failed")
        failing = parse_failing(buggy)
        if any(method is None for _, method in failing):
            return done("validation_failed", "buggy_test_class_initialization_failed")
        copy(buggy / "failing_tests", logs / "failing_tests")
        copy(buggy / "all_tests", logs / "all_tests")
        result["buggy_failing_tests"] = len(failing)
        result["fault_detected"] = len(failing) > 0

        rc = run([D4J, "coverage", "-s", str(vsuite)], logs / "coverage.log", cwd=buggy, timeout=args.test_timeout)
        if rc == TIMEOUT_RC:
            return done("timeout", "coverage_timeout")
        if rc != 0:
            return done("validation_failed", "coverage_failed")
        copy(buggy / "summary.csv", logs / "summary.csv")
        copy(buggy / "coverage.xml", logs / "coverage.xml")
        summary = read_summary(buggy)
        if summary is None:
            return done("validation_failed", "coverage_summary_missing")
        cc = result["code_coverage"]
        cc.update(summary)
        cc["line_ratio"] = ratio(summary["lines_covered"], summary["lines_total"])
        cc["branch_ratio"] = ratio(summary["branches_covered"], summary["branches_total"])
        methods = method_coverage(buggy / "coverage.xml")
        if methods:
            result["test_coverage"].update(methods)
            result["test_coverage"]["ratio"] = ratio(methods["covered_methods"], methods["total_methods"])
        return done("complete")
    except Interrupted:
        return done("interrupted")
    except Exception as exc:
        return done("error", "runner_exception", f"{type(exc).__name__}: {exc}")


def active_bugs(project, logfile):
    rc, out = run([D4J, "bids", "-p", project], logfile, capture=True, timeout=120)
    return [int(x) for x in out.split() if x.isdigit()] if rc == 0 else []


PROJECTS = ["Chart", "Cli", "Closure", "Codec", "Collections", "Compress", "Csv", "Gson", "JacksonCore",
            "JacksonDatabind", "JacksonXml", "Jsoup", "JxPath", "Lang", "Math", "Mockito", "Time"]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--projects", nargs="+")
    parser.add_argument("--bugs", nargs="+", type=int)
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--runs", nargs="+", type=int, default=[1])
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--heap", default="768m")
    parser.add_argument("--test-timeout", type=int, default=900)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--keep-work", action="store_true")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    if not (args.projects or args.all):
        parser.error("choose --projects or --all")
    if not (TOOL / "classes" / "AcoSearch.class").is_file():
        subprocess.run(["bash", str(CODE / "build.sh")], check=True, env=ENV)

    props = load_properties(CONFIG)
    WORK.mkdir(parents=True, exist_ok=True)
    jobs = []
    for project in (PROJECTS if args.all else args.projects):
        bugs = active_bugs(project, WORK / "bids.log")
        if args.bugs:
            bugs = [b for b in bugs if b in set(args.bugs)]
        for bug in bugs:
            for run_no in args.runs:
                target = ACO_DIR / "Results" / EXPERIMENT / ALGORITHM / project / f"{project}-{bug}" / f"run{run_no}" / "result.json"
                if args.overwrite or not target.is_file() or \
                        json.loads(target.read_text(encoding="utf-8")).get("status") in ("running", "interrupted"):
                    jobs.append((project, bug, run_no))
    if args.limit:
        jobs = jobs[:args.limit]
    print(f"Jobs {len(jobs)} | workers {args.workers} | ants {props.get('number_of_ants')} "
          f"iterations {props.get('iterations')} | budget {props.get('time_budget_seconds')}s", flush=True)

    interrupted = False
    pool = ThreadPoolExecutor(max_workers=args.workers)
    try:
        futures = {pool.submit(run_job, p, b, r, props, args): (p, b, r) for p, b, r in jobs}
        for fut in as_completed(futures):
            p, b, r = futures[fut]
            res = fut.result()
            log(f"[{p}-{b} run{r}] {res['status']} ({res['status_detail']}) "
                f"tests={res['tests_validated']}/{res['tests_generated']} fault={res['fault_detected']} "
                f"line={res['code_coverage']['line_ratio']} {res['total_seconds']}s")
    except KeyboardInterrupt:
        interrupted = True
        STOP.set()
        with LOCK:
            for proc in list(ACTIVE):
                kill(proc)
    finally:
        pool.shutdown(wait=True, cancel_futures=interrupted)
    subprocess.run([sys.executable, str(CODE / "summarize_aco.py")], env=ENV)
    print("Done: all jobs finished" if not interrupted else "Interrupted", flush=True)
    return 130 if interrupted else 0


if __name__ == "__main__":
    sys.exit(main())
