#!/usr/bin/env python3
"""
Claude test generator for Defects4J (SQA Project - AI-Assisted Testing).

Flow per bug:
  dataset/<Project>_<bug>/<Class>.java
    -> assemble prompt (Prompt/system_prompt.txt + Prompt/template_prompt.txt)
    -> Claude via Claude Code CLI `claude -p` (default) or Anthropic API
       (backend/model/effort from Configuration/claude_config.json)
    -> sanitize Java 6 / JUnit 4 output
    -> Tests/<experiment>/Claude/<Project>/<Project>-<bug>/run<N>/<Class>ClaudeTest.java
       + generation.json (timing, token usage, stop reason)
    -> Prompt/history/<Project>_<bug>.txt (exact prompt that was sent)

Runs on Windows/macOS/Linux without Defects4J. Validation is a separate step
(validate_claude.py, needs Defects4J in WSL/Linux).

Usage:
  python Claude/Code/pipeline_claude.py --projects Lang --limit 3
  python Claude/Code/pipeline_claude.py --projects Lang --bugs 1 5 7 --run 2
  python Claude/Code/pipeline_claude.py --all --workers 4
"""

import argparse
import json
import os
import random
import re
import shutil
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

import anthropic
from dotenv import load_dotenv

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT = Path(__file__).resolve().parents[2]          # repository root
CLAUDE_DIR = ROOT / "Claude"
DATASET_DIR = ROOT / "dataset"
CONFIG_FILE = CLAUDE_DIR / "Configuration" / "claude_config.json"
HISTORY_DIR = CLAUDE_DIR / "Prompt" / "history"

TARGET_RE = re.compile(r"^([A-Za-z][A-Za-z0-9]*)_([1-9][0-9]*)$")
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;")
PRINT_LOCK = threading.Lock()

load_dotenv(ROOT / ".env")


def log(msg: str):
    with PRINT_LOCK:
        print(msg, flush=True)


# ---------------------------------------------------------------------------
# Configuration & prompt assembly
# ---------------------------------------------------------------------------

def load_config() -> dict:
    return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))


def load_prompt_parts(config: dict):
    files = config["prompt_files"]
    system_prompt = (ROOT / files["system"]).read_text(encoding="utf-8")
    template = (ROOT / files["user_template"]).read_text(encoding="utf-8")
    notes = json.loads((ROOT / files["project_notes"]).read_text(encoding="utf-8"))
    return system_prompt, template, notes


def discover_targets():
    """Return {"Lang_1": Path(dataset/Lang_1/NumberUtils.java), ...}."""
    targets = {}
    if not DATASET_DIR.is_dir():
        return targets
    for folder in DATASET_DIR.iterdir():
        if folder.is_dir() and TARGET_RE.fullmatch(folder.name):
            java_files = sorted(folder.glob("*.java"))
            if java_files:
                targets[folder.name] = java_files[0]
    return targets


def load_target(project: str, bug_id: int, source_file: Path, suffix: str) -> dict:
    code = source_file.read_text(encoding="utf-8", errors="replace")
    match = PACKAGE_RE.search(code)
    package_name = match.group(1) if match else ""
    raw_name = source_file.stem
    class_name = raw_name.split("$")[-1] if "$" in raw_name else raw_name
    return {
        "project": project,
        "bug_id": bug_id,
        "package_name": package_name,
        "class_name": class_name,
        "test_class_name": f"{class_name}{suffix}",
        "source_code": code,
    }


def build_user_prompt(template: str, notes: dict, target: dict) -> str:
    project_note = notes.get(target["project"], notes.get("default", ""))
    return (template
            .replace("{PROJECT}", target["project"])
            .replace("{BUG_ID}", str(target["bug_id"]))
            .replace("{PACKAGE_NAME}", target["package_name"])
            .replace("{CLASS_NAME}", target["class_name"])
            .replace("{TEST_CLASS_NAME}", target["test_class_name"])
            .replace("{PROJECT_NOTES}", project_note)
            .replace("{TARGET_SOURCE_CODE}", target["source_code"]))


# ---------------------------------------------------------------------------
# Java post-processing (Java 6 / JUnit 4 safety net)
# ---------------------------------------------------------------------------

def strip_code_fences(text: str) -> str:
    text = text.strip()
    match = re.search(r"```(?:java)?\s*\n(.*?)```", text, re.DOTALL)
    if match:
        text = match.group(1).strip()
    text = re.sub(r"^```(?:java)?\s*", "", text).strip()
    text = re.sub(r"\s*```$", "", text).strip()
    # Drop any prose before the package declaration.
    pkg = re.search(r"(?m)^\s*(package\s+[\w.]+\s*;)", text)
    if pkg and pkg.start() > 0:
        text = text[pkg.start():].lstrip()
    return text


def sanitize_java(code: str, target: dict) -> str:
    # JUnit 5 -> JUnit 4 imports, in case the model slips.
    code = code.replace("org.junit.jupiter.api.Test", "org.junit.Test")
    code = code.replace("org.junit.jupiter.api.BeforeEach", "org.junit.Before")
    code = code.replace("org.junit.jupiter.api.AfterEach", "org.junit.After")
    code = code.replace("org.junit.jupiter.api.Assertions", "org.junit.Assert")
    code = re.sub(r"import\s+org\.junit\.jupiter\.[^;]+;\s*", "", code)

    # Diamond operator -> raw type (javac -source 1.6 rejects <>).
    code = re.sub(r"new\s+([A-Za-z0-9_$.]+)\s*<>\s*\(", r"new \1(", code)

    # Ensure every @Test method declares `throws Throwable`.
    def add_throws(match):
        signature = match.group(1).rstrip()
        if re.search(r"\bthrows\b", signature):
            return signature + " {"
        return signature + " throws Throwable {"

    code = re.sub(
        r"((?:@Test(?:\s*\([^)]*\))?\s+)(?:@\w+(?:\([^)]*\))?\s+)*(?:public|protected)?\s*void\s+[A-Za-z0-9_]+\s*\([^)]*\)(?:\s*throws\s+[\w.,\s]+)?)\s*\{",
        add_throws, code)
    code = re.sub(r"\{\s*throws\s+Throwable\s*\{", "throws Throwable {", code)

    # Ambiguous JUnit 4 assertions.
    code = re.sub(r"assertEquals\s*\(\s*null\s*,\s*([^,\)]+)\)", r"assertNull(\1)", code)
    for const in ("Double.NaN", "Double.POSITIVE_INFINITY", "Double.NEGATIVE_INFINITY"):
        code = re.sub(r"assertEquals\s*\(\s*" + re.escape(const) + r"\s*,",
                      f"assertEquals(Double.valueOf({const}),", code)

    # Force the expected test class name so the file name always matches.
    expected = target["test_class_name"]
    declared = re.search(r"public\s+(?:final\s+)?class\s+([A-Za-z0-9_$]+)", code)
    if declared and declared.group(1) != expected:
        code = re.sub(r"\b" + re.escape(declared.group(1)) + r"\b", expected, code)

    # Force the expected package declaration.
    if target["package_name"]:
        if PACKAGE_RE.search(code):
            code = PACKAGE_RE.sub(f"package {target['package_name']};", code, count=1)
        else:
            code = f"package {target['package_name']};\n\n" + code

    if not code.rstrip().endswith("}"):
        code = code.rstrip() + "\n}\n"
    return code.rstrip() + "\n"


def count_tests(code: str) -> int:
    return len(re.findall(r"@(?:org\.junit\.)?Test\b", code))


# ---------------------------------------------------------------------------
# Claude backends: "cli" (Claude Code, logged-in account) or "api" (Anthropic SDK)
# ---------------------------------------------------------------------------

def call_claude(backend, config: dict, system_prompt: str, user_prompt: str, label: str):
    """Dispatch to the configured backend. Return (text, usage, stop_reason, request_id)."""
    if config.get("backend", "cli") == "cli":
        return call_claude_cli(backend, config, user_prompt, label)
    return call_claude_api(backend, config, system_prompt, user_prompt, label)


def resolve_cli(config: dict) -> str:
    command = config.get("cli_command", "claude")
    path = shutil.which(command)
    if not path:
        raise FileNotFoundError(f"Claude Code CLI '{command}' not found on PATH. Install it and run `claude` once to log in.")
    return path


QUOTA_LOCK = threading.Lock()
QUOTA_RESUME_AT = [0.0]          # epoch seconds; 0 = no active wait
LIMIT_RE = re.compile(r"limit.*?resets?\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)", re.IGNORECASE)


def parse_limit_reset(message: str):
    """Return an epoch time for 'You've hit your ... limit · resets 9:10am (...)' messages, else None."""
    lowered = (message or "").lower()
    if "limit" not in lowered or "hit your" not in lowered:
        return None        # ordinary 429s / other errors stay on the short backoff path
    m = LIMIT_RE.search(message)
    if not m:
        # "You've hit your ... limit" without a parsable time: back off for 30 minutes.
        return time.time() + 1800
    hour, minute, ampm = int(m.group(1)), int(m.group(2) or 0), m.group(3).lower()
    hour = hour % 12 + (12 if ampm == "pm" else 0)
    now = time.localtime()
    reset = time.mktime((now.tm_year, now.tm_mon, now.tm_mday, hour, minute, 0, 0, 0, -1))
    if reset <= time.time():
        reset += 86400
    return reset + 90          # small margin after the stated reset time


def set_quota_window(reset_at: float, label: str, message: str):
    with QUOTA_LOCK:
        if reset_at > QUOTA_RESUME_AT[0]:
            QUOTA_RESUME_AT[0] = reset_at
            log(f"[~] [{label}] usage limit reached - all workers paused until "
                f"{time.strftime('%Y-%m-%d %H:%M', time.localtime(reset_at))} ({message[:80]})")


def wait_for_quota_window():
    while True:
        with QUOTA_LOCK:
            remaining = QUOTA_RESUME_AT[0] - time.time()
        if remaining <= 0:
            return
        time.sleep(min(remaining, 60))


def call_claude_cli(cli_path: str, config: dict, user_prompt: str, label: str):
    """Run `claude -p` (print mode) with the logged-in Claude Code account.

    The system prompt is passed via --system-prompt-file (config.prompt_files.system);
    the user prompt is piped through stdin to avoid Windows command-line limits.
    """
    attempts = int(config.get("max_attempts", 5))
    system_file = ROOT / config["prompt_files"]["system"]
    # stream-json gives every assistant message; the plain "json" format only returns the
    # last one, which drops the head of long answers when the CLI auto-continues past
    # its output-token cap. The cap itself is raised through the env var below.
    cmd = [cli_path, "-p",
           "--model", config["model"],
           "--effort", config.get("effort", "high"),
           "--output-format", "stream-json", "--verbose",
           "--tools", "",
           "--system-prompt-file", str(system_file)]
    # No --max-turns: with tools disabled the only extra "turns" are the CLI's own
    # continuations after its output-token cap, and capping those at 1 makes long
    # answers end in an error_max_turns result instead of a complete file.
    env = dict(os.environ)
    env["CLAUDE_CODE_MAX_OUTPUT_TOKENS"] = str(config.get("max_tokens", 64000))
    if config.get("cli_config_dir"):
        # Separate Claude Code profile (its own login) so the pipeline's quota use does not
        # share the account used interactively in the IDE.
        env["CLAUDE_CONFIG_DIR"] = str(config["cli_config_dir"])
    last_error = None
    attempt = 0
    while attempt < attempts:
        attempt += 1
        wait_for_quota_window()
        try:
            proc = subprocess.run(cmd, input=user_prompt, capture_output=True, text=True,
                                  encoding="utf-8", errors="replace", env=env,
                                  timeout=int(config.get("cli_timeout_seconds", 1200)))
        except subprocess.TimeoutExpired:
            last_error = f"claude -p timed out after {config.get('cli_timeout_seconds', 1200)}s"
            delay = 5
        else:
            data, text_parts = {}, []
            for line in proc.stdout.splitlines():
                line = line.strip()
                if not line.startswith("{"):
                    continue
                try:
                    event = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if event.get("type") == "assistant":
                    for block in (event.get("message") or {}).get("content") or []:
                        if block.get("type") == "text" and block.get("text"):
                            text_parts.append(block["text"])
                elif event.get("type") == "result":
                    data = event
            if proc.returncode == 0 and data and not data.get("is_error"):
                usage = data.get("usage", {}) or {}
                text = "".join(text_parts) if text_parts else data.get("result", "")
                return (text, {
                    "input_tokens": usage.get("input_tokens"),
                    "output_tokens": usage.get("output_tokens"),
                    "cache_creation_input_tokens": usage.get("cache_creation_input_tokens"),
                    "cache_read_input_tokens": usage.get("cache_read_input_tokens"),
                    "total_cost_usd": data.get("total_cost_usd"),
                    "duration_api_ms": data.get("duration_api_ms"),
                    "num_turns": data.get("num_turns"),
                }, data.get("stop_reason"), data.get("session_id"))
            # Only the CLI's own error text is meaningful; raw stream-json lines are not.
            api_message = (data.get("result") or "").strip() if data else ""
            stderr_tail = (proc.stderr or "").strip()[-400:]
            stdout_tail = "\n".join(l for l in proc.stdout.splitlines() if l.strip() and not l.startswith("{"))[-300:]
            last_error = (api_message or stderr_tail or stdout_tail or
                          (f"result subtype={data.get('subtype')} is_error={data.get('is_error')}" if data else "no result event")
                          + f" (exit {proc.returncode}, {len(proc.stdout)} bytes stdout, {len(text_parts)} text blocks)")[:600]
            lowered = api_message.lower()
            if "not logged in" in lowered:
                raise RuntimeError(f"Claude Code is not logged in: run `claude` once and /login. ({last_error})")
            if data.get("api_error_status") in (400, 401, 403, 404):
                raise RuntimeError(f"Claude CLI non-retryable error: {last_error}")
            reset_at = parse_limit_reset(api_message)
            if reset_at is not None:
                # Subscription usage window exhausted: park every worker until it resets
                # instead of burning retries. Does not count as an attempt.
                set_quota_window(reset_at, label, last_error)
                attempt -= 1
                continue
            delay = min(120, 10 * (2 ** (attempt - 1))) + random.uniform(0, 3)
        log(f"[!] [{label}] CLI error (attempt {attempt}/{attempts}): {last_error}; retry in {delay:.0f}s")
        time.sleep(delay)
    raise RuntimeError(f"Claude CLI failed after {attempts} attempts: {last_error}")


def call_claude_api(client: anthropic.Anthropic, config: dict, system_prompt: str,
                    user_prompt: str, label: str):
    """Anthropic SDK backend. Return (text, usage_dict, stop_reason, request_id). Retries transient errors."""
    attempts = int(config.get("max_attempts", 5))
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            with client.messages.stream(
                model=config["model"],
                max_tokens=int(config["max_tokens"]),
                system=[{"type": "text", "text": system_prompt,
                         "cache_control": {"type": "ephemeral"}}],
                thinking={"type": config.get("thinking", "adaptive")},
                output_config={"effort": config.get("effort", "high")},
                messages=[{"role": "user", "content": user_prompt}],
            ) as stream:
                message = stream.get_final_message()
            text = "".join(block.text for block in message.content if block.type == "text")
            usage = {
                "input_tokens": message.usage.input_tokens,
                "output_tokens": message.usage.output_tokens,
                "cache_creation_input_tokens": getattr(message.usage, "cache_creation_input_tokens", None),
                "cache_read_input_tokens": getattr(message.usage, "cache_read_input_tokens", None),
            }
            return text, usage, message.stop_reason, getattr(message, "_request_id", None)
        except anthropic.RateLimitError as exc:
            last_error = exc
            delay = min(60, 5 * (2 ** (attempt - 1))) + random.uniform(0, 2)
        except anthropic.APIStatusError as exc:
            last_error = exc
            if exc.status_code < 500 and exc.status_code != 529:
                raise
            delay = min(60, 3 * (2 ** (attempt - 1))) + random.uniform(0, 2)
        except (anthropic.APIConnectionError, anthropic.APITimeoutError) as exc:
            last_error = exc
            delay = min(60, 3 * (2 ** (attempt - 1))) + random.uniform(0, 2)
        log(f"[!] [{label}] API error (attempt {attempt}/{attempts}): {last_error}; retry in {delay:.0f}s")
        time.sleep(delay)
    raise RuntimeError(f"Claude API failed after {attempts} attempts: {last_error}")


# ---------------------------------------------------------------------------
# Per-bug worker
# ---------------------------------------------------------------------------

def output_dir_for(config: dict, project: str, bug_id: int, run: int) -> Path:
    return (CLAUDE_DIR / "Tests" / config["experiment"] / config["algorithm"]
            / project / f"{project}-{bug_id}" / f"run{run}")


def claim(out_dir: Path, config: dict) -> bool:
    """Atomically claim a bug so several generator instances (different accounts) can share
    one queue. A claim older than the per-call timeout is considered abandoned."""
    out_dir.mkdir(parents=True, exist_ok=True)
    marker = out_dir / ".claim"
    stale_after = int(config.get("cli_timeout_seconds", 1200)) + 600
    for _ in range(2):
        try:
            fd = os.open(marker, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, f"{os.getpid()} {time.time():.0f}\n".encode())
            os.close(fd)
            return True
        except FileExistsError:
            try:
                if time.time() - marker.stat().st_mtime > stale_after:
                    marker.unlink()
                    continue
            except OSError:
                pass
            return False
    return False


def process_bug(backend, config, system_prompt, template, notes,
                name: str, source_file: Path, run: int, overwrite: bool) -> str:
    project, bug_id = name.split("_")[0], int(name.split("_")[1])
    out_dir = output_dir_for(config, project, bug_id, run)
    meta_file = out_dir / "generation.json"
    if meta_file.is_file() and not overwrite:
        existing = json.loads(meta_file.read_text(encoding="utf-8"))
        if existing.get("status") == "ok":
            return "SKIP (exists)"
    if not claim(out_dir, config):
        return "SKIP (claimed by another instance)"

    target = load_target(project, bug_id, source_file, config.get("test_class_suffix", "ClaudeTest"))
    user_prompt = build_user_prompt(template, notes, target)

    HISTORY_DIR.mkdir(parents=True, exist_ok=True)
    (HISTORY_DIR / f"{name}.txt").write_text(
        "===== SYSTEM =====\n" + system_prompt + "\n\n===== USER =====\n" + user_prompt,
        encoding="utf-8")

    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.java"):
        old.unlink()

    meta = {
        "schema_version": "1.0",
        "algorithm": config["algorithm"],
        "algorithm_version": config["algorithm_version"],
        "experiment": config["experiment"],
        "project": project,
        "bug_id": bug_id,
        "run": run,
        "backend": config.get("backend", "cli"),
        "model": config["model"],
        "effort": config.get("effort"),
        "thinking": config.get("thinking"),
        "max_tokens": config.get("max_tokens"),
        "created_at": datetime.now(timezone.utc).isoformat(),
        "source_file": source_file.relative_to(ROOT).as_posix(),
        "prompt_history": (HISTORY_DIR / f"{name}.txt").relative_to(ROOT).as_posix(),
        "test_class": target["test_class_name"],
        "package": target["package_name"],
    }

    started = time.perf_counter()
    try:
        text, usage, stop_reason, request_id = call_claude(
            backend, config, system_prompt, user_prompt, name)
    except Exception as exc:  # generation failed for good
        meta.update({"status": "error", "error": str(exc),
                     "generation_seconds": round(time.perf_counter() - started, 3),
                     "tests_generated": 0})
        meta_file.write_text(json.dumps(meta, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        (out_dir / ".claim").unlink(missing_ok=True)
        return f"ERROR: {exc}"

    wall_seconds = round(time.perf_counter() - started, 3)
    # generation_seconds = time the model actually spent (comparable with the search-based
    # algorithms' generation time); wall time additionally includes retries and quota waits.
    api_ms = usage.get("duration_api_ms")
    seconds = round(api_ms / 1000, 3) if api_ms else wall_seconds
    code = sanitize_java(strip_code_fences(text), target)
    tests = count_tests(code)
    status = "ok"
    error = None
    has_class = re.search(r"\bclass\s+" + re.escape(target["test_class_name"]) + r"\b", code) is not None
    if stop_reason == "max_tokens":
        status, error = "truncated", "stop_reason=max_tokens (output cut off)"
    elif stop_reason == "refusal":
        status, error = "refused", "stop_reason=refusal"
    elif tests == 0:
        status, error = "empty", "no @Test methods in response"
    elif not has_class:
        status, error = "truncated", "response is missing the class declaration (head cut off)"

    (out_dir / f"{target['test_class_name']}.java").write_text(code, encoding="utf-8")
    (out_dir / "raw_response.txt").write_text(text, encoding="utf-8")
    meta.update({
        "status": status,
        "error": error,
        "stop_reason": stop_reason,
        "request_id": request_id,
        "usage": usage,
        "generation_seconds": seconds,
        "generation_wall_seconds": wall_seconds,
        "tests_generated": tests,
        "test_file": f"{target['test_class_name']}.java",
    })
    meta_file.write_text(json.dumps(meta, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (out_dir / ".claim").unlink(missing_ok=True)
    return (f"{status.upper()} tests={tests} in={usage.get('input_tokens')} "
            f"cached={usage.get('cache_read_input_tokens')} out={usage.get('output_tokens')} {seconds:.1f}s")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Generate JUnit 4 tests for Defects4J bugs with Claude")
    parser.add_argument("--projects", nargs="+", help="Project names, e.g. Lang Math Chart")
    parser.add_argument("--bugs", nargs="+", type=int, help="Bug ids (used with a single --projects value)")
    parser.add_argument("--targets", nargs="+", help="Exact targets, e.g. Lang_1 Math_5")
    parser.add_argument("--all", action="store_true", help="Every bug in dataset/")
    parser.add_argument("--limit", type=int, help="Only the first N pending targets")
    parser.add_argument("--run", type=int, default=1, help="Run number (default: 1)")
    parser.add_argument("--workers", type=int, help="Parallel API calls (default from config)")
    parser.add_argument("--model", help="Override model id from config")
    parser.add_argument("--backend", choices=["cli", "api"], help="cli = Claude Code login (default), api = ANTHROPIC_API_KEY")
    parser.add_argument("--cli-config-dir", help="CLAUDE_CONFIG_DIR for this instance ('default' = the machine's normal login)")
    parser.add_argument("--shard", help="K/N: process only every N-th selected target starting at K (run N instances in parallel, e.g. 0/2 and 1/2)")
    parser.add_argument("--effort", choices=["low", "medium", "high", "xhigh", "max"], help="Override effort")
    parser.add_argument("--overwrite", action="store_true", help="Regenerate even if output exists")
    parser.add_argument("--dry-run", action="store_true", help="List pending targets and exit")
    args = parser.parse_args(argv)

    if not (args.projects or args.targets or args.all):
        parser.error("choose --projects, --targets or --all")

    config = load_config()
    if args.model:
        config["model"] = args.model
    if args.effort:
        config["effort"] = args.effort
    if args.backend:
        config["backend"] = args.backend
    if args.cli_config_dir:
        config["cli_config_dir"] = "" if args.cli_config_dir.lower() == "default" else args.cli_config_dir
    shard = None
    if args.shard:
        m = re.fullmatch(r"(\d+)/(\d+)", args.shard)
        if not m or int(m.group(1)) >= int(m.group(2)):
            parser.error("--shard must be K/N with K < N")
        shard = (int(m.group(1)), int(m.group(2)))
    workers = args.workers or int(config.get("generation_workers", 4))

    available = discover_targets()
    if not available:
        parser.error(f"dataset/ is empty or missing: {DATASET_DIR}")
    selected = sorted(available, key=lambda n: (n.split("_")[0], int(n.split("_")[1])))
    if args.projects:
        wanted = {p.lower() for p in args.projects}
        selected = [n for n in selected if n.split("_")[0].lower() in wanted]
        if args.bugs:
            selected = [n for n in selected if int(n.split("_")[1]) in set(args.bugs)]
    if args.targets:
        missing = set(args.targets) - available.keys()
        if missing:
            parser.error(f"unknown targets: {', '.join(sorted(missing))}")
        selected = [n for n in selected if n in set(args.targets)]
    if shard:
        # Shard on the stable sorted list (not on the pending list) so that instances
        # started at different times still get disjoint subsets.
        selected = [n for i, n in enumerate(selected) if i % shard[1] == shard[0]]

    def is_done(name):
        project, bug_id = name.split("_")[0], int(name.split("_")[1])
        meta = output_dir_for(config, project, bug_id, args.run) / "generation.json"
        if not meta.is_file():
            return False
        return json.loads(meta.read_text(encoding="utf-8")).get("status") == "ok"

    pending = [n for n in selected if args.overwrite or not is_done(n)]
    if args.limit:
        pending = pending[:args.limit]

    print(f"Backend {config.get('backend', 'cli')} (config dir: {config.get('cli_config_dir') or 'default'}) | "
          f"shard {args.shard or 'all'} | model {config['model']} | effort {config.get('effort')} | run {args.run} | "
          f"selected {len(selected)} | pending {len(pending)} | workers {workers}", flush=True)
    if args.dry_run:
        for n in pending:
            print(n)
        return 0
    if not pending:
        return 0

    if config.get("backend", "cli") == "cli":
        try:
            backend = resolve_cli(config)
        except FileNotFoundError as exc:
            print(f"[!] {exc}", file=sys.stderr)
            return 1
    else:
        if not os.environ.get("ANTHROPIC_API_KEY"):
            print("[!] ANTHROPIC_API_KEY is not set. Put it in .env at the repository root.", file=sys.stderr)
            return 1
        backend = anthropic.Anthropic(max_retries=2)
    system_prompt, template, notes = load_prompt_parts(config)

    failures = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(process_bug, backend, config, system_prompt, template, notes,
                               n, available[n], args.run, args.overwrite): n for n in pending}
        try:
            for future in as_completed(futures):
                name = futures[future]
                try:
                    outcome = future.result()
                except Exception as exc:
                    outcome = f"ERROR: {exc}"
                if outcome.startswith("ERROR") or outcome.startswith("TRUNCATED") \
                        or outcome.startswith("EMPTY") or outcome.startswith("REFUSED"):
                    failures += 1
                log(f"[{name}] {outcome}")
        except KeyboardInterrupt:
            print("Interrupted; waiting for in-flight requests...", file=sys.stderr, flush=True)
            pool.shutdown(wait=True, cancel_futures=True)
            return 130
    print(f"Done: {len(pending) - failures} ok, {failures} failed", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
