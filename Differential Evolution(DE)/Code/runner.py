"""Defects4J orchestration for DE Rebuild 3.1.20. Python standard library only."""
from __future__ import annotations

import argparse
import base64
import contextlib
import csv
import hashlib
import io
import json
import os
import platform
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import threading
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed

import common_output

ROOT = Path(__file__).resolve().parents[1]
VERSION = "3.1.20-rebuild"
PREVIOUS_OUTPUT_COMPATIBLE_VERSION = "3.1.10-rebuild"
PREVIOUS_RUNNER_SHA256 = "1909776e51dc0bb03f55cb3ae770c5825bec49637a30116e4c5e16c4c14d78a9"
PREVIOUS_STATUS_COMPATIBLE_VERSION = "3.1.14-rebuild"
PREVIOUS_STATUS_RUNNER_SHA256 = "5fc504a757546a848701af0aa995a9dce953946be1e5ea55f55ca0425afd2d43"
PREVIOUS_VALIDATION_COMPATIBLE_VERSION = "3.1.15-rebuild"
PREVIOUS_VALIDATION_RUNNER_SHA256 = "a25fadda15dc1f3df8cfe16b4520a7e9e0ab46bc630bf07e781a59c00858a4da"
PREVIOUS_COMMON_OUTPUT_SHA256 = "dfe6afdb6ef058f1d0ef65e6a3bc66d19da42719ee6545545b58edc99e523223"
PREVIOUS_JACOCO_COMPATIBLE_VERSION = "3.1.16-rebuild"
PREVIOUS_JACOCO_RUNNER_SHA256 = "b5f884372f8cdd6c08a40b498c4f00a77223eeb30bd58a4c46e4011c26d26ab6"
PREVIOUS_JACOCO_COMMON_OUTPUT_SHA256 = "b08b0a7336512e047c2afc3dafde0a950bade5f16cb408f733539312a50858a7"
PREVIOUS_JAVA6_COMPATIBLE_VERSION = "3.1.18-rebuild"
PREVIOUS_JAVA6_COMPATIBLE_RUNNER_SHA256 = "b2fd132589818bbf2c47fd3b81376dfc6a3bf8a6731035eeb979156273121f4a"
PREVIOUS_JAVA6_COMPATIBLE_COMMON_OUTPUT_SHA256 = "b08b0a7336512e047c2afc3dafde0a950bade5f16cb408f733539312a50858a7"
PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_VERSION = "3.1.19-rebuild"
PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_RUNNER_SHA256 = "39a7460a09f9c3745f389f36c23aebeb21e2b01206bcb16ab973eceb8afa6cb7"
PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_COMMON_OUTPUT_SHA256 = "b08b0a7336512e047c2afc3dafde0a950bade5f16cb408f733539312a50858a7"
STOP = threading.Event()


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


class JobExpired(Exception):
    pass


class CommandTimeout(Exception):
    pass


class Cancelled(Exception):
    pass


class MetadataError(RuntimeError):
    """Defects4J exported unusable metadata; this is not a no-test outcome."""


class Commands:
    def __init__(self, log: Path, deadline=None):
        self.log, self.deadline, self.counter = log, deadline, 0
        log.mkdir(parents=True, exist_ok=True)

    def remaining(self):
        if STOP.is_set():
            raise Cancelled("Interrupted by user")
        remaining = self.deadline - time.monotonic() if self.deadline else float("inf")
        if remaining <= 0:
            raise JobExpired("Whole-job time limit reached")
        return remaining

    def run(self, argv, cwd=None, timeout=600, include_stderr=False, env=None):
        allowed = min(timeout, self.remaining())
        self.counter += 1
        log = self.log / f"{self.counter:04d}.log"
        command = [str(x) for x in argv]
        proc = subprocess.Popen(command, cwd=cwd, env=env, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, encoding="utf-8",
                                errors="replace", start_new_session=os.name == "posix")
        end = time.monotonic() + allowed
        try:
            while True:
                self.remaining()
                left = end - time.monotonic()
                if left <= 0:
                    raise CommandTimeout("Command time limit reached: " + command[0])
                try:
                    output, diagnostics = proc.communicate(timeout=min(0.5, left))
                    break
                except subprocess.TimeoutExpired:
                    continue
        except (Exception, KeyboardInterrupt):
            if os.name == "posix":
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            else:
                proc.kill()
            output, diagnostics = proc.communicate()
            log.write_text(shlex.join(command) + "\n[stdout]\n" + output
                           + "\n[stderr]\n" + diagnostics, encoding="utf-8")
            raise
        log.write_text(shlex.join(command) + "\n[stdout]\n" + output
                       + "\n[stderr]\n" + diagnostics, encoding="utf-8")
        if proc.returncode:
            raise RuntimeError(f"Command failed ({proc.returncode}); log={log}\n{(output + diagnostics)[-1500:]}")
        return (output + diagnostics if include_stderr else output).strip()


@contextlib.contextmanager
def lock(path):
    """Linux locks are automatically released on cancellation or process exit."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+") as handle:
        if os.name == "posix":
            import fcntl
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise RuntimeError(f"Another run owns {path}") from None
        yield


def json_output(output):
    return json.loads(next(line[8:] for line in reversed(output.splitlines())
                           if line.startswith("DE_JSON:")))


def read_candidates(path):
    result = []
    if path.exists():
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                item = json.loads(line)
            except json.JSONDecodeError:  # A timeout can interrupt the last write.
                continue
            if item["reached_target"] and item["fitness"] >= 0:
                result.append(item)
    return result


def choose(candidates, limit):
    """Greedy coverage archive with input diversity when coverage is tied."""
    candidates = sorted(candidates, key=lambda c: c["fitness"], reverse=True)
    chosen, seen, seen_sources = [], set(), set()
    while candidates and len(chosen) < limit:
        best = max(candidates, key=lambda c: (len(set(c["coverage"]["features"]) - seen), c["fitness"]))
        features = set(best["coverage"]["features"])
        if chosen and not features - seen:
            best = next((candidate for candidate in candidates
                         if candidate.get("generated_source") is not None
                         and candidate["generated_source"] not in seen_sources), None)
            if best is None:
                break
            features = set(best["coverage"]["features"])
        chosen.append(best)
        seen.update(features)
        if best.get("generated_source") is not None:
            seen_sources.add(best["generated_source"])
        candidates.remove(best)
    return chosen


def suite_source(cases, test_timeout, pid=None):
    cli_cases = [c for c in cases if c.get("spec", {}).get("class") in (
        "com.google.javascript.jscomp.CommandLineRunner",
        "com.google.javascript.jscomp.AbstractCompilerRunner")]
    if pid == "Closure" and cli_cases:
        compile_cases = [c for c in cases if c not in cli_cases]
        if compile_cases:
            source = suite_source(compile_cases, test_timeout, pid)
            if "compileClosure(" not in source:
                raise MetadataError("Mixed Closure suite has unsupported cases")
            source = source.rstrip()
            source = source[:-1]
        else:
            source = "public class DEGeneratedTest {\n"
        tests = []
        for case in cli_cases:
            options = case.get("cli_options") or []
            args = ", ".join(json.dumps(option) for option in options)
            tests.append(f'''    @org.junit.Test(timeout={test_timeout * 1000}L)
    public void {case['name']}() throws Exception {{
        org.junit.Assert.assertEquals({json.dumps(case['expected'])},
            runClosureCli({json.dumps(case['spec']['class'])},
                {json.dumps(case['generated_source'])}, new String[]{{{args}}}));
    }}''')
        return source + '''
    private static String runClosureCli(String target, String source, String[] options) throws Exception {
        java.io.File file = java.io.File.createTempFile("de-closure-cli-", ".js");
        java.io.PrintStream previous = System.out;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            java.io.FileOutputStream stream = new java.io.FileOutputStream(file);
            try { stream.write(source.getBytes("UTF-8")); }
            finally { stream.close(); }
            String[] argv = new String[options.length + 2];
            argv[0] = "--js";
            argv[1] = file.getAbsolutePath();
            System.arraycopy(options, 0, argv, 2, options.length);
            Object receiver = null;
            java.lang.reflect.Method method;
            if (target.equals("com.google.javascript.jscomp.CommandLineRunner")) {
                method = Class.forName(target).getDeclaredMethod("main", String[].class);
            } else {
                Class type = Class.forName("com.google.javascript.jscomp.CompilerRunner");
                java.lang.reflect.Constructor[] constructors = type.getDeclaredConstructors();
                int preferred = Integer.MAX_VALUE;
                for (int i = 0; i < constructors.length; i++) {
                    Class[] kinds = constructors[i].getParameterTypes();
                    if (kinds.length == 0 || kinds[0] != String[].class ||
                        kinds.length >= preferred) continue;
                    Object[] values = new Object[kinds.length];
                    values[0] = argv;
                    for (int j = 1; j < kinds.length; j++) {
                        if (kinds[j] == java.io.PrintStream.class)
                            values[j] = new java.io.PrintStream(new java.io.ByteArrayOutputStream());
                        else if (kinds[j] == Boolean.TYPE) values[j] = Boolean.FALSE;
                        else if (kinds[j] == Integer.TYPE) values[j] = Integer.valueOf(0);
                    }
                    try {
                        constructors[i].setAccessible(true);
                        receiver = constructors[i].newInstance(values);
                        preferred = kinds.length;
                    } catch (Exception ignored) { }
                }
                if (receiver == null) throw new IllegalArgumentException("Receiver construction failed");
                method = Class.forName(target).getDeclaredMethod("run");
            }
            method.setAccessible(true);
            System.setOut(new java.io.PrintStream(bytes, true, "UTF-8"));
            try {
                if (receiver == null) method.invoke(null, new Object[]{argv});
                else method.invoke(receiver);
            } catch (java.lang.reflect.InvocationTargetException e) {
                return "THROW:" + e.getCause().getClass().getName();
            }
            return "CLI_OUTPUT:" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } finally {
            System.out.flush();
            System.setOut(previous);
            file.delete();
        }
    }
''' + "\n".join(tests) + "\n}\n"
    if pid == "Closure" and cases and all(
            c.get("spec", {}).get("class") == "com.google.javascript.jscomp.Compiler"
            and c.get("spec", {}).get("method", "").startswith("compile(")
            and c.get("generated_source") is not None for c in cases):
        tests = []
        for case in cases:
            tests.append(f'''    @org.junit.Test(timeout={test_timeout * 1000}L)
    public void {case['name']}() throws Exception {{
        org.junit.Assert.assertEquals({json.dumps(case['expected'])},
            compileClosure({json.dumps(case['generated_source'])}));
    }}''')
        return '''public class DEGeneratedTest {
    private static String compileClosure(String source) throws Exception {
        com.google.javascript.jscomp.Compiler compiler =
            new com.google.javascript.jscomp.Compiler();
        com.google.javascript.jscomp.CompilerOptions options =
            new com.google.javascript.jscomp.CompilerOptions();
        com.google.javascript.jscomp.CompilationLevel.ADVANCED_OPTIMIZATIONS
            .setOptionsForCompilationLevel(options);
        Class current = options.getClass();
        while (current != null) {
            java.lang.reflect.Field[] fields = current.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (field.getType() == Boolean.TYPE &&
                    (name.equals("removeglobals") || name.contains("removeunusedvar") ||
                     name.contains("removeunusedlocal"))) {
                    field.setAccessible(true);
                    field.setBoolean(options, !name.equals("removeglobals"));
                }
            }
            current = current.getSuperclass();
        }
        java.lang.reflect.Method[] methods = options.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            java.lang.reflect.Method method = methods[i];
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            Class[] parameters = method.getParameterTypes();
            if (!name.startsWith("set") || parameters.length != 1 ||
                parameters[0] != Boolean.TYPE) continue;
            if (name.contains("removeglobals")) method.invoke(options, Boolean.FALSE);
            else if (name.contains("removeunusedvar") || name.contains("removeunusedlocal"))
                method.invoke(options, Boolean.TRUE);
        }
        com.google.javascript.jscomp.SourceFile input =
            com.google.javascript.jscomp.SourceFile.fromCode("de-input.js", source);
        compiler.compile(java.util.Collections.<com.google.javascript.jscomp.SourceFile>emptyList(),
            java.util.Collections.singletonList(input), options);
        String output = compiler.toSource();
        return "CLOSURE_SOURCE:java.lang.String:" +
            java.util.Base64.getEncoder().encodeToString(output.getBytes("UTF-8"));
    }
''' + "\n".join(tests) + "\n}\n"
    # Old Commons CLI checkouts run generated tests with a JUnit classpath
    # lacking Hamcrest. JUnit 4 fails before a test method starts there.
    # JUnit 3 TestCase runs on the same classpath and keeps named methods.
    legacy_junit = pid == "Cli"
    tests = []
    for case in cases:
        m = case["spec"]
        annotation = "" if legacy_junit else f"    @org.junit.Test(timeout={test_timeout * 1000}L)\n"
        assertion = "assertEquals" if legacy_junit else "org.junit.Assert.assertEquals"
        tests.append(f'''{annotation}    public void {case['name']}() {{
        {assertion}({json.dumps(case['expected'])}, DEReplay.run(
            {json.dumps(m['class'])}, {json.dumps(m['receivers'])}, {json.dumps(m['method'])},
            new int[]{{{','.join(map(str, case['genes']))}}}));
    }}''')
    base = " extends junit.framework.TestCase" if legacy_junit else ""
    return "public class DEGeneratedTest" + base + " {\n" + "\n".join(tests) + "\n}\n"


def save_suite(directory, pid, bid, run, cases, test_timeout, resources=None):
    directory.mkdir(parents=True, exist_ok=True)
    # Defects4J runs every .java file in a generated suite as a JUnit test.
    # Put the replay helper in the same source file as the actual tests so it
    # is compiled but never discovered as a separate test class.
    closure_direct = (pid == "Closure" and cases and all(
        c.get("generated_source") is not None and (
            c.get("spec", {}).get("class") == "com.google.javascript.jscomp.Compiler"
            and c.get("spec", {}).get("method", "").startswith("compile(")
            or c.get("spec", {}).get("class") in (
                "com.google.javascript.jscomp.CommandLineRunner",
                "com.google.javascript.jscomp.AbstractCompilerRunner")) for c in cases))
    if closure_direct:
        source = suite_source(cases, test_timeout, pid)
    else:
        helper = (ROOT / "Code" / "DEReplay.java").read_text(encoding="utf-8")
        declaration = "public final class DEReplay"
        if helper.count(declaration) != 1:
            raise MetadataError("Unexpected DEReplay class declaration")
        source = helper.replace(declaration, "final class DEReplay", 1) + "\n" + suite_source(cases, test_timeout, pid)
    (directory / "DEGeneratedTest.java").write_text(source, encoding="utf-8")
    (directory / "DEReplay.java").unlink(missing_ok=True)
    write_json(directory / "cases.json", cases)
    archives = {}
    for suffix in ("b", "f"):
        path = directory / f"{pid}-{bid}{suffix}-de.{run}.tar.bz2"
        with tarfile.open(path, "w:bz2") as archive:
            archive.add(directory / "DEGeneratedTest.java", arcname="DEGeneratedTest.java")
            if resources and resources.get(suffix):
                archive.add(resources[suffix], arcname="externs.zip")
        archives[suffix] = path.resolve()
    return archives


def parse_failures(output, path):
    match = re.search(r"Failing tests:\s*(\d+)", output)
    if not match:
        raise RuntimeError("Defects4J did not report a failing-test count")
    count = int(match.group(1))
    text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
    names = sorted(set(re.findall(r"^---\s+(.+?)\s*$", text, flags=re.M)))
    if len(names) != count:
        raise RuntimeError(f"Failure report mismatch: count={count}, names={len(names)}")
    return names


def result_status(status):
    """Classify validation, keeping unavailable coverage in the detailed status."""
    if status in ("complete", "coverage_error"):
        return "pass"
    if status in ("no_generated_tests", "no_valid_tests", "running"):
        return "no run"
    return "fail"


def output_only_upgrade(previous, current):
    """Allow prior experiments when the DE search implementation is unchanged."""
    prior_runners = {
        PREVIOUS_STATUS_COMPATIBLE_VERSION: PREVIOUS_STATUS_RUNNER_SHA256,
        PREVIOUS_VALIDATION_COMPATIBLE_VERSION: PREVIOUS_VALIDATION_RUNNER_SHA256,
        PREVIOUS_JACOCO_COMPATIBLE_VERSION: PREVIOUS_JACOCO_RUNNER_SHA256,
        PREVIOUS_JAVA6_COMPATIBLE_VERSION: PREVIOUS_JAVA6_COMPATIBLE_RUNNER_SHA256,
        PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_VERSION: PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_RUNNER_SHA256,
    }
    old_version = previous.get("version")
    if old_version in prior_runners and current.get("version") == VERSION:
        ignored = {"version", "source_sha256"}
        if {k: v for k, v in previous.items() if k not in ignored} != {
                k: v for k, v in current.items() if k not in ignored}:
            return False
        old_sources = previous.get("source_sha256", {})
        new_sources = current.get("source_sha256", {})
        permitted = {"runner.py", "common_output.py", "CoverageReport.java"}
        if old_version == PREVIOUS_JAVA6_COMPATIBLE_VERSION:
            permitted.add("DEReplay.java")
        common_hash = {
            PREVIOUS_JACOCO_COMPATIBLE_VERSION: PREVIOUS_JACOCO_COMMON_OUTPUT_SHA256,
            PREVIOUS_JAVA6_COMPATIBLE_VERSION: PREVIOUS_JAVA6_COMPATIBLE_COMMON_OUTPUT_SHA256,
            PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_VERSION: PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_COMMON_OUTPUT_SHA256,
        }.get(old_version, PREVIOUS_COMMON_OUTPUT_SHA256)
        expected_sources = (set(old_sources) if old_version in (
            PREVIOUS_JACOCO_COMPATIBLE_VERSION, PREVIOUS_JAVA6_COMPATIBLE_VERSION,
            PREVIOUS_DIRECT_SCHEMA_COMPATIBLE_VERSION)
            else set(old_sources) | {"CoverageReport.java"})
        return (old_sources.get("runner.py") == prior_runners[old_version]
                and old_sources.get("common_output.py") == common_hash
                and set(new_sources) == expected_sources
                and all(old_sources[name] == new_sources[name] for name in old_sources
                        if name not in permitted))
    if previous.get("version") != PREVIOUS_OUTPUT_COMPATIBLE_VERSION or current.get("version") != VERSION:
        return False
    ignored = {"version", "source_sha256"}
    if {k: v for k, v in previous.items() if k not in ignored} != {
            k: v for k, v in current.items() if k not in ignored}:
        return False
    previous_sources = previous.get("source_sha256", {})
    current_sources = current.get("source_sha256", {})
    if previous_sources.get("runner.py") != PREVIOUS_RUNNER_SHA256:
        return False
    if set(current_sources) != set(previous_sources) | {"common_output.py"}:
        return False
    return all(current_sources.get(name) == digest for name, digest in previous_sources.items()
               if name != "runner.py")


def summarize(results):
    records = [record for _, record in common_output.iter_legacy_records(results)]
    fields = ["project", "bug_id", "run", "status_group", "status", "tests_generated", "tests_validated",
              "compile_success", "fixed_failing_tests", "buggy_failing_tests", "fault_detected",
              "line_coverage_ratio", "branch_coverage_ratio", "line_coverage_percent",
              "branch_coverage_percent", "generation_seconds", "total_seconds"]
    stream = io.StringIO()
    writer = csv.DictWriter(stream, fieldnames=fields)
    writer.writeheader()
    for record in sorted(records, key=lambda r: (r["project"], int(r["bug_id"]), r["run"])):
        row = {key: record.get(key, "") for key in fields}
        row["status_group"] = result_status(record["status"])
        writer.writerow(row)
    (results / "summary.csv").write_text(stream.getvalue(), encoding="utf-8")
    by_project = {}
    manifest_path = results / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else []
    for pid in sorted({r["project"] for r in records} | {m["project"] for m in manifest}):
        group = [r for r in records if r["project"] == pid]
        complete = [r for r in group if r["status"] == "complete"]
        tool_counts = {}
        for r in complete:
            tool = r.get("reported_coverage_tool") or "Defects4J/Cobertura"
            tool_counts[tool] = tool_counts.get(tool, 0) + 1
        mixed_tools = len(tool_counts) > 1
        selected = {str(m["bug_id"]) for m in manifest if m["project"] == pid} | {str(r["bug_id"]) for r in group}
        evaluated = {str(r["bug_id"]) for r in group if r.get("fault_detected") is not None}
        detected = {str(r["bug_id"]) for r in group if r.get("fault_detected") is True}
        status_groups = {label: sum(result_status(r["status"]) == label for r in group)
                         for label in ("pass", "no run", "fail")}
        per_bug_status = {}
        for bid in selected:
            bug_runs = [r for r in group if str(r["bug_id"]) == bid]
            if not bug_runs:
                label = "no run"
            elif any(result_status(r["status"]) == "pass" for r in bug_runs):
                label = "pass"
            elif all(result_status(r["status"]) == "no run" for r in bug_runs):
                label = "no run"
            else:
                label = "fail"
            per_bug_status[label] = per_bug_status.get(label, 0) + 1
        averages = {}
        for key in ("line_coverage_percent", "branch_coverage_percent", "generation_seconds", "total_seconds"):
            values = ([] if mixed_tools and key in ("line_coverage_percent", "branch_coverage_percent")
                      else [r[key] for r in complete if r.get(key) is not None])
            averages["mean_" + key + "_complete_runs"] = sum(values) / len(values) if values else None
        by_project[pid] = {"selected_bugs": len(selected), "evaluated_bugs": len(evaluated),
            "detected_unique_bugs": len(detected), "recorded_runs": len(group), "complete_runs": len(complete),
            "detected_runs": sum(r.get("fault_detected") is True for r in group),
            "unknown_outcome_bugs": len(selected - evaluated),
            "fault_detection_evaluation_complete": selected == evaluated,
            "fault_detection_rate_all_selected_bugs": len(detected) / len(selected) if selected and evaluated else None,
            "fault_detection_rate_evaluated_bugs": len(detected) / len(evaluated) if evaluated else None,
            "coverage_tool_counts": tool_counts,
            "mixed_coverage_tools": mixed_tools,
            **averages,
            "status_groups_by_run": status_groups,
            "status_groups_by_bug": {label: per_bug_status.get(label, 0)
                                     for label in ("pass", "no run", "fail")},
            "statuses": {s: sum(r["status"] == s for r in group) for s in sorted({r["status"] for r in group})}}
    write_json(results / "summary.json", by_project)
    common_output.write_summaries(results)
    return by_project


class Job:
    def __init__(self, pid, bid, run, args):
        self.pid, self.bid, self.index, self.args = pid, bid, run, args
        parts = (pid, f"{pid} {bid}", f"run{run}")
        self.result_dir = args.results.joinpath(*parts)
        self.attempt = str(time.time_ns())
        self.attempt_dir = self.result_dir / "attempts" / self.attempt
        self.test_dir = ROOT / "Test" / args.experiment / Path(*parts) / self.attempt
        self.work = args.work / pid / str(bid) / str(run)
        self.work.mkdir(parents=True, exist_ok=True)
        self.start = time.monotonic()
        self.command = Commands(self.attempt_dir / "logs",
            self.start + args.job_timeout if args.job_timeout else None)
        self.record = {"project": pid, "bug_id": bid, "run": run, "version": VERSION,
            "status": "running", "algorithm": "DE/rand/1/bin", "methods": [],
            "coverage_scope": "classes.modified", "search_coverage_tool": "JaCoCo 0.8.12",
            "reported_coverage_tool": "Defects4J/Cobertura", "fault_detected": None,
            "suite_validated": False, "tests_generated": 0, "tests_validated": 0}
        self.record.update(attempt=self.attempt, test_directory=str(self.test_dir),
                           log_directory=str(self.command.log))
        self.record.update(compile_success=None, project_compile_success=None, generation_seconds=None)
        self.generation_start = None
        self.generation_done = False
        self.cases = []
        self.elapsed_offset = 0.0

    def checkpoint(self):
        elapsed = time.monotonic() - self.start
        self.record["total_seconds"] = round(self.elapsed_offset + elapsed, 3)
        if self.elapsed_offset:
            self.record["validation_resume_seconds"] = round(elapsed, 3)
        if self.generation_start is not None and not self.generation_done:
            self.record["generation_seconds"] = round(time.monotonic() - self.generation_start, 3)
        write_json(self.result_dir / "result.json", self.record)
        common_output.write_common_record(self.record, self.args.results)

    def d4j(self, action, *options, timeout=900, env=None):
        return self.command.run([self.args.d4j, action, *options], timeout=timeout, env=env)

    def export(self, checkout, prop):
        # Use the documented output file so diagnostics can never become class names or paths.
        exports = self.attempt_dir / "exports"
        exports.mkdir(parents=True, exist_ok=True)
        key = hashlib.sha256((str(checkout) + "\0" + prop).encode()).hexdigest()[:16]
        output_file = exports / (key + ".txt")
        output_file.unlink(missing_ok=True)
        self.d4j("export", "-w", checkout, "-p", prop, "-o", output_file)
        if not output_file.is_file():
            raise MetadataError(f"Defects4J export did not create its output file for {prop}: {output_file}")
        value = output_file.read_text(encoding="utf-8", errors="strict").strip()
        if not value:
            raise MetadataError(f"Defects4J exported an empty value for {prop}")
        if prop == "classes.modified":
            classes = [line.strip() for line in value.splitlines() if line.strip()]
            if any(not re.fullmatch(r"(?:[A-Za-z_$][\w$]*\.)*[A-Za-z_$][\w$]*", name) for name in classes):
                raise MetadataError(f"Invalid class name in exported {prop}: {output_file}")
            return "\n".join(classes)
        if "\n" in value or "\r" in value:
            raise MetadataError(f"Expected a single-line value for {prop}: {output_file}")
        return value

    def select_compiled_modified_classes(self, buggy, fixed):
        """Search only changed classes with bytecode in both revisions.

        Defects4J may export changed resource paths as class names, or a
        changed class may exist on only one side of a bug. Preserve every
        omission in the result so the measured coverage scope is explicit.
        """
        exported = self.record["modified_classes"]
        selected, omitted = [], []
        for name in exported:
            missing = []
            for label, (_, _, bins) in (("buggy", buggy), ("fixed", fixed)):
                if not (bins / (name.replace(".", "/") + ".class")).is_file():
                    missing.append(label)
            if missing:
                omitted.append({"class": name, "reason": "compiled_class_missing",
                                "revisions": missing})
            else:
                selected.append(name)
        self.record["modified_classes_exported"] = exported
        self.record["modified_classes_excluded"] = omitted
        self.record["modified_classes"] = selected
        if not selected:
            raise MetadataError("No modified class has compiled bytecode in both revisions")

    def java(self, checkout, cp, main, *arguments, timeout=120, instrument=False):
        jars = ROOT / "Configuration" / "lib"
        command = ["java", f"-Xmx{self.args.heap}", "-Djava.awt.headless=true",
                   "-Duser.timezone=UTC", "-Duser.language=en", "-Duser.country=US",
                   "-Dfile.encoding=UTF-8"]
        if instrument:
            includes = ":".join(name + "*" for name in self.record["modified_classes"])
            command.append(f"-javaagent:{jars / 'jacoco-agent.jar'}=output=none,includes={includes}")
            command.extend((f"-Dde.agent.path={jars / 'jacoco-agent.jar'}",
                            f"-Dde.heap={self.args.heap}",
                            f"-Dde.candidate.timeout.seconds={getattr(self.args, 'candidate_timeout', 12)}"))
        classpath = os.pathsep.join([str(ROOT / "Code" / "build"), str(jars / "*"), cp])
        return self.command.run([*command, "-cp", classpath, main, *arguments], cwd=checkout, timeout=timeout)

    def closure_externs_zip(self, checkout, suffix):
        """Build Closure's default externs resource from the checked-out sources."""
        if self.pid != "Closure":
            return None
        source = checkout / "externs"
        if not source.is_dir():
            return None
        files = sorted(source.rglob("*.js"))
        if not files:
            return None
        destination = self.work / "resources" / suffix / "externs.zip"
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in files:
                archive.write(path, path.relative_to(source).as_posix())
        return destination

    def checkout(self, suffix):
        directory = self.work / (self.pid + "-" + self.bid + suffix)
        self.record["stage"] = "checkout_" + suffix
        self.checkpoint()
        self.d4j("checkout", "-p", self.pid, "-v", self.bid + suffix, "-w", directory)
        self.d4j("compile", "-w", directory)
        cp = self.export(directory, "cp.test")
        cp = os.pathsep.join(str((directory / p).resolve()) if not Path(p).is_absolute() else p
                            for p in cp.split(os.pathsep))
        externs = self.closure_externs_zip(directory, suffix)
        if externs:
            cp += os.pathsep + str(externs.parent)
        bins = (directory / self.export(directory, "dir.bin.classes")).resolve()
        if not bins.is_dir():
            raise MetadataError(f"Exported dir.bin.classes does not exist: {bins}")
        return directory, cp, bins

    def test(self, checkout, suite, label):
        failing = checkout / "failing_tests"
        failing.unlink(missing_ok=True)
        output = self.d4j("test", "-w", checkout, "-s", suite, timeout=self.args.command_timeout)
        names = parse_failures(output, failing)
        self.record["compile_success"] = True
        if failing.exists():
            shutil.copy2(failing, self.attempt_dir / (label + "-failing_tests.txt"))
        return names

    def save_suite(self):
        """Preserve friendly output paths, stage Defects4J input under a shell-safe path."""
        resources = {suffix: self.closure_externs_zip(
            self.work / (self.pid + "-" + self.bid + suffix), suffix)
            for suffix in ("b", "f")}
        archives = save_suite(self.test_dir, self.pid, self.bid, self.index,
                              self.cases, self.args.test_timeout, resources)
        staging = self.work / "suite-inputs" / self.attempt
        staging.mkdir(parents=True, exist_ok=True)
        for suffix, source in archives.items():
            target = staging / source.name
            if os.name == "posix" and any(char.isspace() for char in str(target)):
                raise MetadataError(f"Defects4J suite staging path contains whitespace: {target}")
            shutil.copy2(source, target)
            archives[suffix] = target.resolve()
        return archives

    def coverage(self, checkout, suite, label):
        for name in ("summary.csv", "coverage.xml"):
            (checkout / name).unlink(missing_ok=True)
        self.d4j("coverage", "-w", checkout, "-s", suite, timeout=self.args.command_timeout)
        with (checkout / "summary.csv").open(encoding="utf-8", newline="") as handle:
            values = {k: int(v) for k, v in next(csv.DictReader(handle)).items()}
        for name in ("summary.csv", "coverage.xml"):
            if (checkout / name).is_file():
                shutil.copy2(checkout / name, self.attempt_dir / f"{label}-{name}")
        return values

    def jacoco_execution_path(self, label):
        # JAVA_TOOL_OPTIONS splits on whitespace before Java starts. Result directories
        # contain names such as "Jsoup 4", so keep the agent's output under work/.
        return self.work / "jacoco" / self.attempt / f"{label}.exec"

    def jacoco_coverage(self, checkout, suite, label):
        """Measure the validated suite when Defects4J/Cobertura cannot report."""
        execution = self.jacoco_execution_path(label)
        agent = ROOT / "Configuration" / "lib" / "jacoco-agent.jar"
        if any(char.isspace() for char in str(agent)) or any(
                char.isspace() for char in str(execution)):
            raise MetadataError("JaCoCo agent and execution paths must not contain whitespace")
        execution.parent.mkdir(parents=True, exist_ok=True)
        execution.unlink(missing_ok=True)
        includes = ":".join(name + "*" for name in self.record["modified_classes"])
        option = (f"-javaagent:{agent}=destfile={execution},append=true,"
                  f"output=file,includes={includes}")
        env = os.environ.copy()
        env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " " + option).strip()
        self.d4j("compile", "-w", checkout, timeout=self.args.command_timeout)
        bins = (checkout / self.export(checkout, "dir.bin.classes")).resolve()
        if not bins.is_dir():
            raise MetadataError(f"Compiled classes directory missing: {bins}")
        self.d4j("test", "-w", checkout, "-s", suite,
                 timeout=self.args.command_timeout, env=env)
        if not execution.is_file() or execution.stat().st_size == 0:
            raise RuntimeError(f"JaCoCo produced no execution data: {execution}")
        jars = ROOT / "Configuration" / "lib"
        classpath = os.pathsep.join((str(ROOT / "Code" / "build"), str(jars / "*")))
        result = self.command.run(["java", "-cp", classpath, "CoverageReport",
            execution, bins, ",".join(self.record["modified_classes"])],
            timeout=self.args.command_timeout)
        metrics = json_output(result)
        if metrics.get("LinesTotal", 0) <= 0:
            raise RuntimeError("JaCoCo produced no measurable lines")
        return metrics

    def search(self, spec, buggy, fixed, number):
        directory, cp, bins = buggy
        fixed_dir, fixed_cp, _ = fixed
        method = dict(spec, status="searching")
        self.record["methods"].append(method)
        self.record["stage"] = spec["method"]
        self.checkpoint()
        print(f"{self.pid}-{self.bid}: {spec['method']}", flush=True)
        folder = self.attempt_dir / "search" / f"method-{number:04d}"
        folder.mkdir(parents=True, exist_ok=True)
        candidates_path = folder / "candidates.jsonl"
        seed_text = f"{self.args.seed}/{self.pid}/{self.bid}/{self.index}/{spec['class']}/{spec['method']}"
        seed = int(hashlib.sha256(seed_text.encode()).hexdigest()[:15], 16)
        method["seed"] = seed
        remaining = self.command.remaining()
        reserve = self.args.validation_reserve if self.args.job_timeout else 0
        allowed = min(self.args.method_timeout or float("inf"), max(0, remaining - reserve))
        if allowed <= 0:
            method["status"] = "search_budget_exhausted"
            return False
        started = time.monotonic()
        try:
            self.java(directory, cp, "SearchMain", "search", spec["class"], spec["receivers"],
                spec["method"], bins, ",".join(self.record["modified_classes"]), candidates_path,
                self.args.population, self.args.budget, self.args.mutation, self.args.crossover,
                self.args.input_range, seed, timeout=allowed, instrument=True)
            method["status"] = "searched"
        except CommandTimeout:
            method["status"] = "method_timeout"
        except RuntimeError as exc:
            method.update(status="search_error", error=str(exc))
        method["search_seconds"] = round(time.monotonic() - started, 3)
        candidates = read_candidates(candidates_path)
        stats_path = Path(str(candidates_path) + ".stats.json")
        if stats_path.exists():
            method["search"] = json.loads(stats_path.read_text(encoding="utf-8"))
        else:
            # An interrupted method has no completed DE statistics.
            method["search"] = {"de_applied": None, "stats_incomplete": True}
        method["valid_candidate_count"] = len(candidates)
        selected = choose(candidates, self.args.max_tests_per_method)
        method["candidates_selected"] = len(selected)
        method["oracle_rejections"] = []
        count_before = len(self.cases)
        for candidate in selected:
            try:
                closure_compile = (self.pid == "Closure"
                    and spec["class"] == "com.google.javascript.jscomp.Compiler"
                    and spec["method"].startswith("compile("))
                # Closure compilation takes longer than ordinary reflective
                # calls. DEReplay captures its deterministic output once; give
                # that single fixed-side replay room to finish.
                replay_timeout = 180 if closure_compile else self.args.test_timeout * 3 + 10
                replay_args = (spec["class"], spec["receivers"], spec["method"],
                               ",".join(map(str, candidate["genes"])))
                if closure_compile and candidate.get("generated_source") is not None:
                    replay_args += (base64.b64encode(candidate["generated_source"].encode("utf-8")).decode("ascii"),)
                output = self.java(fixed_dir, fixed_cp, "DEReplay", *replay_args, timeout=replay_timeout)
                tokens = [base64.b64decode(line[9:]).decode("utf-8") for line in output.splitlines()
                          if line.startswith("DE_TOKEN:")]
                stable_observation = (len(tokens) == 1 if closure_compile else
                    len(tokens) == 3 and len(set(tokens)) == 1)
                if not stable_observation or tokens[0] == "HARNESS_ERROR":
                    method["oracle_rejections"].append("unstable_or_unreplayable_fixed_observation")
                    continue
                case = dict(candidate, spec=spec, expected=tokens[0],
                            name=f"testDE{len(self.cases):05d}",
                            oracle_kind="type_only" if tokens[0].startswith("TYPE:") else
                                "exception_type" if tokens[0].startswith("THROW:") else
                                "completion_only" if tokens[0] == "VOID" else "value_or_state")
                self.cases.append(case)
            except (CommandTimeout, RuntimeError) as exc:
                method["oracle_rejections"].append(str(exc))
        method["tests_generated"] = len(self.cases) - count_before
        print(f"{self.pid}-{self.bid}: {method['status']}; {len(candidates)} valid candidates, "
              f"{method['tests_generated']} selected tests; {method['search_seconds']}s search", flush=True)
        self.record["tests_generated"] = len(self.cases)
        if self.cases:
            self.save_suite()
        self.checkpoint()
        return True

    def validate(self, buggy, fixed):
        self.record["stage"] = "validate_fixed"
        self.checkpoint()
        rejected = []
        # Remove named failing tests, preserve every rejection, then verify the remaining suite twice.
        for attempt in range(1, 5):
            if not self.cases:
                self.record.update(status="no_valid_tests", discarded_tests=rejected)
                return
            suites = self.save_suite()
            first = self.test(fixed[0], suites["f"], f"fixed-{attempt}-a")
            second = self.test(fixed[0], suites["f"], f"fixed-{attempt}-b")
            failed = set(first) | set(second)
            if not failed:
                break
            removable = {name.split("::")[-1] for name in failed if name.startswith("DEGeneratedTest::")}
            removed = [c for c in self.cases if c["name"] in removable]
            rejected.extend(dict(c, rejection="failed_on_fixed") for c in removed)
            write_json(self.test_dir / "discarded.json", rejected)
            if not removed or attempt == 4:
                self.record.update(status="fixed_validation_failed", fixed_failing_test_names=sorted(failed),
                                   fixed_failing_tests=len(failed), discarded_tests_count=len(rejected))
                return
            self.cases = [c for c in self.cases if c["name"] not in removable]
        self.record.update(suite_validated=True, tests_validated=len(self.cases),
                           fixed_failing_tests=0, fixed_failing_test_names=[],
                           discarded_tests_count=len(rejected))
        self.record["oracle_kinds"] = {kind: sum(c.get("oracle_kind", "unspecified") == kind for c in self.cases)
                                      for kind in sorted({c.get("oracle_kind", "unspecified") for c in self.cases})}
        self.record["stage"] = "validate_buggy"
        self.checkpoint()
        first = self.test(buggy[0], suites["b"], "buggy-a")
        second = self.test(buggy[0], suites["b"], "buggy-b")
        confirmed = sorted(set(first) & set(second))
        # Only failures from generated test methods count as detections, never build/initialization errors.
        case_names = {"DEGeneratedTest::" + c["name"] for c in self.cases}
        confirmed = [name for name in confirmed if name in case_names]
        self.record.update(buggy_failing_tests=len(confirmed), buggy_failing_test_names=confirmed,
                           buggy_failure_runs=[first, second], fault_detected=bool(confirmed))
        self.record["stage"] = "final_coverage"
        for key in ("coverage_error", "cobertura_error", "jacoco_error",
                    "reported_coverage_tool", "coverage_fallback_used",
                    "buggy_coverage", "fixed_coverage"):
            self.record.pop(key, None)
        self.checkpoint()
        try:
            metrics = self.coverage(buggy[0], suites["b"], "buggy")
            fixed_metrics = self.coverage(fixed[0], suites["f"], "fixed")
            tool = "Defects4J/Cobertura"
        except (RuntimeError, CommandTimeout, OSError, ValueError, StopIteration) as cobertura_exc:
            self.record["cobertura_error"] = str(cobertura_exc)
            self.record["stage"] = "final_coverage_jacoco"
            self.checkpoint()
            try:
                metrics = self.jacoco_coverage(buggy[0], suites["b"], "buggy")
                fixed_metrics = self.jacoco_coverage(fixed[0], suites["f"], "fixed")
                tool = "JaCoCo"
            except (RuntimeError, CommandTimeout, OSError, ValueError, StopIteration) as jacoco_exc:
                exc = RuntimeError(f"Cobertura: {cobertura_exc}; JaCoCo: {jacoco_exc}")
                self.record["jacoco_error"] = str(jacoco_exc)
                self.record.update(status="coverage_error", coverage_error=str(exc),
                                   error=str(exc), line_coverage_percent=None,
                                   branch_coverage_percent=None, line_coverage_ratio=None,
                                   branch_coverage_ratio=None)
                return
        self.record["buggy_coverage"] = metrics
        self.record["fixed_coverage"] = fixed_metrics
        self.record["reported_coverage_tool"] = tool
        self.record["coverage_fallback_used"] = tool == "JaCoCo"
        self.record["line_coverage_percent"] = (100 * metrics["LinesCovered"] / metrics["LinesTotal"]
                                                if metrics["LinesTotal"] else None)
        branch_total = metrics.get("BranchesTotal", metrics.get("ConditionsTotal"))
        branch_covered = metrics.get("BranchesCovered", metrics.get("ConditionsCovered"))
        self.record["branch_coverage_percent"] = (100 * branch_covered / branch_total
                                                  if branch_total else None)
        self.record["line_coverage_ratio"] = (self.record["line_coverage_percent"] / 100
                                              if self.record["line_coverage_percent"] is not None else None)
        self.record["branch_coverage_ratio"] = (self.record["branch_coverage_percent"] / 100
                                                if self.record["branch_coverage_percent"] is not None else None)
        self.record.update(status="complete", stage="finished")

    def run(self):
        try:
            with lock(self.work / ".job.lock"):
                buggy, fixed = self.checkout("b"), self.checkout("f")
                self.record["project_compile_success"] = True
                self.generation_start = time.monotonic()
                self.record["stage"] = "read_metadata"
                self.record["modified_classes"] = self.export(buggy[0], "classes.modified").splitlines()
                self.select_compiled_modified_classes(buggy, fixed)
                self.record["stage"] = "discover_methods"
                specs = []
                for cls in self.record["modified_classes"]:
                    try:
                        methods = json_output(self.java(buggy[0], buggy[1], "SearchMain", "discover", cls, buggy[2]))
                        fixed_methods = json_output(self.java(fixed[0], fixed[1], "SearchMain", "discover", cls, fixed[2]))
                        # Direct methods can be public yet unusable because the
                        # receiver or required state is not constructible. Add
                        # public entry points that reach changed methods too.
                        callers = json_output(self.java(buggy[0], buggy[1], "SearchMain",
                            "discover-callers", cls, buggy[2]))
                        fixed_callers = json_output(self.java(fixed[0], fixed[1], "SearchMain",
                            "discover-callers", cls, fixed[2]))
                        existing = {(m["class"], m["method"]) for m in methods}
                        methods += [m for m in callers if (m["class"], m["method"]) not in existing]
                        existing = {(m["class"], m["method"]) for m in fixed_methods}
                        fixed_methods += [m for m in fixed_callers
                                          if (m["class"], m["method"]) not in existing]
                        fixed_by_name = {(m["class"], m["method"]): m for m in fixed_methods
                                         if not m["skip_reason"]}
                        for method in methods:
                            if method["skip_reason"]:
                                self.record["methods"].append(dict(method, status="skipped"))
                                continue
                            counterpart = fixed_by_name.get((method["class"], method["method"]))
                            if not counterpart or method["receivers"] != counterpart["receivers"]:
                                self.record["methods"].append(dict(method, status="revision_mismatch"))
                            elif not self.args.method or method["method"].startswith(self.args.method + "("):
                                specs.append(method)
                    except (RuntimeError, CommandTimeout) as exc:
                        self.record["methods"].append({"class": cls, "status": "discovery_error", "error": str(exc)})
                if self.args.max_methods:
                    omitted, specs = specs[self.args.max_methods:], specs[:self.args.max_methods]
                    self.record["methods"].extend(dict(s, status="method_limit") for s in omitted)
                for index, spec in enumerate(specs):
                    if not self.search(spec, buggy, fixed, index):
                        self.record["methods"].extend(dict(s, status="search_budget_exhausted") for s in specs[index + 1:])
                        break
                self.record["generation_seconds"] = round(time.monotonic() - self.generation_start, 3)
                self.generation_done = True
                if self.cases:
                    self.validate(buggy, fixed)
                else:
                    broken = any(m["status"] in ("discovery_error", "search_error") for m in self.record["methods"])
                    self.record["status"] = "generation_error" if broken else "no_generated_tests"
        except MetadataError as exc:
            self.record.update(status="metadata_error", error=str(exc))
        except JobExpired as exc:
            self.record.update(status="job_timeout", error=str(exc))
        except Cancelled as exc:
            self.record.update(status="interrupted", error=str(exc))
        except Exception as exc:
            self.record.update(status="error", error=str(exc))
        finally:
            if self.cases:
                self.save_suite()
            self.checkpoint()
        return self.record

    def resume_validation(self, previous):
        """Continue an already-generated suite after a validation infrastructure failure."""
        try:
            with lock(self.work / ".job.lock"):
                prior_directory = Path(previous["test_directory"]).resolve()
                expected_root = (ROOT / "Test" / self.args.experiment).resolve()
                if not prior_directory.is_relative_to(expected_root):
                    raise MetadataError(f"Previous test directory is outside this experiment: {prior_directory}")
                cases_file = prior_directory / "cases.json"
                if not cases_file.is_file():
                    raise MetadataError(f"Previous generated cases are missing: {cases_file}")
                self.cases = json.loads(cases_file.read_text(encoding="utf-8"))
                if not self.cases:
                    raise MetadataError(f"Previous suite contains no cases: {cases_file}")
                buggy = self.work / (self.pid + "-" + self.bid + "b")
                fixed = self.work / (self.pid + "-" + self.bid + "f")
                if not buggy.is_dir() or not fixed.is_dir():
                    raise MetadataError("Previous buggy/fixed checkouts are missing; run the bug again")
                current = dict(self.record)
                self.record = dict(previous)
                self.record.update(status="running", stage="resume_validation",
                    version=VERSION, source_search_version=previous.get("version"),
                    attempt=current["attempt"], test_directory=current["test_directory"],
                    log_directory=current["log_directory"],
                    recovery_from_attempt=previous.get("attempt"), error=None,
                    fault_detected=None, suite_validated=False, tests_validated=0)
                if previous.get("stage") not in (
                        "validate_fixed", "validate_buggy", "final_coverage",
                        "final_coverage_jacoco", "resume_validation"):
                    self.record["search_incomplete"] = True
                    self.record["search_recovered_from_stage"] = previous.get("stage")
                self.elapsed_offset = float(previous.get("total_seconds") or 0)
                self.generation_done = True
                self.record["tests_generated"] = len(self.cases)
                self.checkpoint()
                self.validate((buggy,), (fixed,))
        except JobExpired as exc:
            self.record.update(status="job_timeout", error=str(exc))
        except Cancelled as exc:
            self.record.update(status="interrupted", error=str(exc))
        except MetadataError as exc:
            self.record.update(status="metadata_error", error=str(exc))
        except Exception as exc:
            self.record.update(status="error", error=str(exc))
        finally:
            if self.cases:
                self.save_suite()
            self.checkpoint()
        return self.record


def arguments():
    defaults = json.loads((ROOT / "Configuration" / "defaults.json").read_text(encoding="utf-8"))
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("action", choices=["run", "plan", "status", "summarize", "resume"], nargs="?", default="run")
    p.add_argument("--project")
    p.add_argument("--bug")
    p.add_argument("--bug-start", type=int)
    p.add_argument("--bug-end", type=int)
    p.add_argument("--limit", type=int)
    p.add_argument("--method")
    p.add_argument("--results", default="experiment")
    p.add_argument("--work", type=Path, default=ROOT / "work")
    p.add_argument("--retry", action="store_true", help="Retry completed/no-test jobs too")
    p.add_argument("--d4j", default=os.environ.get("DEFECTS4J_BIN", "defects4j"))
    for name, value in defaults.items():
        p.add_argument("--" + name.replace("_", "-"), type=type(value), default=value)
    args = p.parse_args()
    if args.bug and not args.project:
        p.error("--bug requires --project")
    if args.bug_start and args.bug_end and args.bug_start > args.bug_end:
        p.error("--bug-start must be <= --bug-end")
    if args.population < 4 or args.budget <= args.population:
        p.error("DE requires population >= 4 and budget > population")
    if not 0 < args.mutation <= 2 or not 0 <= args.crossover <= 1:
        p.error("Require 0 < mutation <= 2 and 0 <= crossover <= 1")
    for name in ("workers", "runs", "max_tests_per_method", "input_range", "test_timeout", "command_timeout", "candidate_timeout"):
        if getattr(args, name) < 1:
            p.error(name + " must be positive")
    for name in ("job_timeout", "method_timeout", "validation_reserve", "max_methods"):
        if getattr(args, name) < 0:
            p.error(name + " must be nonnegative")
    if args.action != "resume" and args.job_timeout and args.validation_reserve >= args.job_timeout:
        p.error("--job-timeout must exceed --validation-reserve (default 90 seconds)")
    for name in ("limit", "bug_start", "bug_end"):
        if getattr(args, name) is not None and getattr(args, name) < 1:
            p.error(name + " must be positive")
    if not re.fullmatch(r"[1-9][0-9]*[mMgG]", args.heap):
        p.error("--heap must be a Java heap size such as 512m or 1g")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.results):
        p.error("--results must be an experiment name (letters, digits, underscore, hyphen)")
    args.experiment = args.results
    args.results = ROOT / "Results" / args.experiment
    args.work = args.work.resolve()
    return args


def resumable_validation(previous):
    if previous.get("status") not in ("error", "job_timeout", "interrupted",
                                      "fixed_validation_failed", "coverage_error"):
        return False
    if previous.get("stage") in ("validate_fixed", "validate_buggy", "final_coverage",
                                 "final_coverage_jacoco", "resume_validation"):
        return True
    directory = previous.get("test_directory")
    return (previous.get("status") in ("job_timeout", "interrupted")
        and int(previous.get("tests_generated") or 0) > 0
        and bool(directory) and (Path(directory) / "cases.json").is_file())


def resume_main(args):
    if os.name != "posix":
        raise SystemExit("Resume must run in Ubuntu/WSL with the existing Defects4J checkouts.")
    config_path = args.results / "configuration.json"
    if not config_path.is_file():
        raise SystemExit(f"Existing experiment configuration not found: {config_path}")
    settings = json.loads(config_path.read_text(encoding="utf-8"))
    for name in ("test_timeout", "command_timeout", "heap", "job_timeout"):
        flag = "--" + name.replace("_", "-")
        if flag not in sys.argv and not any(arg.startswith(flag + "=") for arg in sys.argv):
            setattr(args, name, settings.get(name, getattr(args, name)))
    invocation = args.results / "last_invocation.json"
    if "--work" not in sys.argv and not any(arg.startswith("--work=") for arg in sys.argv) and invocation.is_file():
        args.work = Path(json.loads(invocation.read_text(encoding="utf-8"))["work"]).resolve()
    if any(char.isspace() for char in str(args.work)):
        raise SystemExit(f"Defects4J requires a work path without whitespace: {args.work}")
    jobs = []
    for path, previous in common_output.iter_legacy_records(args.results):
        pid, bid, run = previous["project"], str(previous["bug_id"]), int(previous["run"])
        if args.project and args.project != pid or args.bug and args.bug != bid:
            continue
        if args.bug_start is not None and int(bid) < args.bug_start:
            continue
        if args.bug_end is not None and int(bid) > args.bug_end:
            continue
        if resumable_validation(previous):
            jobs.append((pid, bid, run, previous))
    jobs.sort(key=lambda item: (item[0], int(item[1]), item[2]))
    if args.limit is not None:
        jobs = jobs[:args.limit]
    print(f"Validation-only recoveries: {len(jobs)}; no DE search will be repeated", flush=True)
    if not jobs:
        return
    from setup import build
    build()
    with lock(args.results / ".run.lock"):
        for pid in sorted({item[0] for item in jobs}):
            snapshot = ROOT / "Code" / args.experiment / pid / ("validation-" + VERSION)
            snapshot.mkdir(parents=True, exist_ok=True)
            for source in (ROOT / "Code" / "runner.py", ROOT / "Code" / "DEReplay.java",
                           ROOT / "Code" / "CoverageReport.java",
                           ROOT / "Code" / "common_output.py"):
                shutil.copy2(source, snapshot / source.name)
        pool = ThreadPoolExecutor(max_workers=args.workers)
        futures = [pool.submit(lambda p=p, b=b, r=r, old=old:
                    Job(p, b, r, args).resume_validation(old)) for p, b, r, old in jobs]
        try:
            for future in as_completed(futures):
                record = future.result()
                print(f"{record['project']}-{record['bug_id']} run {record['run']}: "
                      f"{result_status(record['status'])} ({record['status']}) "
                      f"({record['tests_validated']} validated tests)", flush=True)
                summarize(args.results)
        except KeyboardInterrupt:
            STOP.set()
            for future in futures:
                future.cancel()
            print("Stopping validation processes; saving partial results...", flush=True)
        finally:
            pool.shutdown(wait=True, cancel_futures=True)
            summarize(args.results)


def main():
    args = arguments()
    args.results.mkdir(parents=True, exist_ok=True)
    if args.action in ("status", "summarize"):
        print(json.dumps(summarize(args.results), indent=2))
        return
    if args.action == "resume":
        resume_main(args)
        return
    if os.name == "posix" and any(char.isspace() for char in str(args.work)):
        raise SystemExit(f"Defects4J requires a work path without whitespace: {args.work}")
    command = Commands(args.results / "discovery-logs")
    projects = [args.project] if args.project else command.run([args.d4j, "pids"]).splitlines()
    pairs = []
    for project in projects:
        bids = command.run([args.d4j, "bids", "-p", project]).splitlines()
        pairs.extend((project, bid.strip()) for bid in bids if bid.strip().isdigit()
                     and (not args.bug or bid.strip() == args.bug)
                     and (args.bug_start is None or int(bid) >= args.bug_start)
                     and (args.bug_end is None or int(bid) <= args.bug_end))
    pairs.sort(key=lambda x: (x[0], int(x[1])))
    if args.limit is not None:
        pairs = pairs[:args.limit]
    if not pairs:
        raise SystemExit("No matching active bugs")
    settings = {k: getattr(args, k) for k in json.loads((ROOT / "Configuration" / "defaults.json").read_text())}
    settings.pop("workers")
    settings.pop("runs")
    settings.update(version=VERSION, method=args.method,
                    source_sha256={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                   for p in sorted((ROOT / "Code").glob("*")) if p.suffix in (".java", ".py")})
    config_path = args.results / "configuration.json"
    previous_settings = json.loads(config_path.read_text()) if config_path.exists() else None
    continuing_previous = bool(previous_settings and output_only_upgrade(previous_settings, settings))
    if previous_settings and previous_settings != settings and not continuing_previous:
        raise SystemExit("Code or search settings changed. Use a new --results name.")
    jobs = []
    for pid, bid in pairs:
        for run in range(1, args.runs + 1):
            record = args.results / pid / f"{pid} {bid}" / f"run{run}" / "result.json"
            previous = json.loads(record.read_text()) if record.exists() else {}
            if args.retry or previous.get("status") not in ("complete", "no_generated_tests", "no_valid_tests"):
                jobs.append((pid, bid, run))
    print(f"Matching bugs: {len(pairs)}; pending runs: {len(jobs)}", flush=True)
    if args.action == "plan":
        for item in jobs:
            print(*item)
        return
    if os.name != "posix":
        raise SystemExit("Run the Defects4J pipeline in Ubuntu/WSL. Local unit tests can run on Windows.")
    with lock(args.results / ".run.lock"):
        # Keep the original experiment fingerprint when appending to a 3.1.10
        # run. Each new result still records the runner version that produced it.
        active_settings = previous_settings if continuing_previous else settings
        write_json(config_path, active_settings)
        if continuing_previous:
            write_json(args.results / "output_adapter_continuation.json", {
                "from_version": previous_settings["version"],
                "to_version": VERSION,
                "reason": "reporting-only upgrade; search parameters and Java sources unchanged",
                "new_source_sha256": settings["source_sha256"],
            })
            legacy_count = sum(1 for _ in common_output.iter_legacy_records(args.results))
            common_count = common_output.count_common_records(args.results)
            if common_count < legacy_count:
                converted, _ = common_output.export_existing(args.results)
                print(f"Shared output backfilled from {converted} previous runs", flush=True)
        executable = shutil.which(args.d4j)
        environment = {"python": sys.version, "platform": platform.platform(),
                       "java": command.run(["java", "-version"], include_stderr=True),
                       "java_home": os.environ.get("JAVA_HOME"), "defects4j_executable": executable}
        if executable:
            d4j_root = Path(executable).resolve().parents[2]
            if (d4j_root / ".git").exists() and shutil.which("git"):
                environment["defects4j_commit"] = command.run(["git", "-C", d4j_root, "rev-parse", "HEAD"])
        write_json(args.results / ("environment_continuation.json" if continuing_previous
                                   else "environment.json"), environment)
        manifest_path = args.results / "manifest.json"
        old_manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else []
        all_selected = set(pairs) | {(m["project"], str(m["bug_id"])) for m in old_manifest}
        write_json(manifest_path, [{"project": p, "bug_id": b}
                                  for p, b in sorted(all_selected, key=lambda x: (x[0], int(x[1])))])
        write_json(args.results / "last_invocation.json", {
            "project": args.project, "bug": args.bug, "bug_start": args.bug_start,
            "bug_end": args.bug_end, "workers": args.workers, "runs": args.runs,
            "selected_pairs": pairs, "work": str(args.work), "time": time.time()})
        from setup import build
        build()
        for pid in projects:
            write_json(ROOT / "Configuration" / args.experiment / pid / "settings.json", active_settings)
            destination = ROOT / "Code" / args.experiment / pid
            destination.mkdir(parents=True, exist_ok=True)
            if continuing_previous:
                previous_runner = destination / "runner.py"
                expected_hash = {
                    PREVIOUS_STATUS_COMPATIBLE_VERSION: PREVIOUS_STATUS_RUNNER_SHA256,
                    PREVIOUS_VALIDATION_COMPATIBLE_VERSION: PREVIOUS_VALIDATION_RUNNER_SHA256,
                    PREVIOUS_JACOCO_COMPATIBLE_VERSION: PREVIOUS_JACOCO_RUNNER_SHA256,
                }.get(previous_settings["version"], PREVIOUS_RUNNER_SHA256)
                if previous_runner.is_file() and hashlib.sha256(previous_runner.read_bytes()).hexdigest() == expected_hash:
                    archive = destination / previous_settings["version"]
                    archive.mkdir(exist_ok=True)
                    shutil.copy2(previous_runner, archive / "runner.py")
            for source in (ROOT / "Code").iterdir():
                if source.suffix in (".java", ".py"):
                    shutil.copy2(source, destination / source.name)
        pool = ThreadPoolExecutor(max_workers=args.workers)
        futures = [pool.submit(lambda p=p, b=b, r=r: Job(p, b, r, args).run()) for p, b, r in jobs]
        try:
            for future in as_completed(futures):
                record = future.result()
                print(f"{record['project']}-{record['bug_id']} run {record['run']}: "
                      f"{result_status(record['status'])} ({record['status']}) "
                      f"({record['tests_validated']} validated tests)", flush=True)
                summarize(args.results)
        except KeyboardInterrupt:
            STOP.set()
            for future in futures:
                future.cancel()
            print("Stopping child processes; saving partial results...", flush=True)
        finally:
            pool.shutdown(wait=True, cancel_futures=True)
            summarize(args.results)


if __name__ == "__main__":
    main()
