"""Algorithm-neutral result files for the DE/Defects4J runner.

Legacy result.json files remain the runner's checkpoint and resume state. This
module publishes the shared schema directly in Results/<experiment>, beside
the legacy records, and can convert an already finished DE experiment without
running Defects4J again.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET

SCHEMA_VERSION = "1.0"
ALGORITHM = "DE"
SUMMARY_FIELDS = (
    "algorithm", "project", "selected_unique_bugs", "evaluated_unique_bugs",
    "detected_unique_bugs", "unknown_outcome_bugs", "fault_detection_rate",
    "complete_runs", "coverage_tools", "mixed_coverage_tools",
    "mean_test_coverage_ratio", "mean_line_coverage_ratio",
    "mean_branch_coverage_ratio", "mean_generation_seconds", "mean_total_seconds",
)


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def is_common_record(record):
    return record.get("schema_version") == SCHEMA_VERSION and record.get("algorithm") == ALGORITHM


def iter_legacy_records(results_root: Path):
    """Yield runner checkpoints only, ignoring normalized records beside them."""
    for path in sorted(results_root.glob("*/*/run*/result.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        if not is_common_record(record):
            yield path, record


def count_common_records(results_root: Path):
    return sum(1 for path in results_root.glob("*/*/run*/result.json")
               if is_common_record(json.loads(path.read_text(encoding="utf-8"))))


def normalized_status(detail):
    if detail == "coverage_error":
        return "partial"
    if detail in ("complete", "running", "no_generated_tests", "no_valid_tests", "interrupted"):
        return detail
    if detail == "fixed_validation_failed":
        return "validation_failed"
    if detail == "job_timeout":
        return "timeout"
    return "error"


def ratio(covered, total):
    return (covered / total if isinstance(covered, (int, float))
            and isinstance(total, int) and total > 0 else None)


def method_coverage(xml_path: Path, modified_classes):
    """Count executable Cobertura methods hit by the validated buggy suite."""
    result = {
        "scope": "modified_classes", "revision": "buggy", "tool": "Defects4J/Cobertura",
        "covered_methods": None, "total_methods": None, "ratio": None,
    }
    if not xml_path.is_file():
        result["unavailable_reason"] = "buggy coverage.xml is missing"
        return result
    try:
        root = ET.parse(xml_path).getroot()
        names = set(modified_classes or ())
        classes = [node for node in root.findall(".//class") if node.get("name") in names]
        if not classes or len({node.get("name") for node in classes}) != len(names):
            result["unavailable_reason"] = "coverage.xml omits one or more modified classes"
            return result
        covered = total = 0
        for cls in classes:
            for method in cls.findall("./methods/method"):
                name = method.get("name", "")
                # Static initializers and compiler-generated accessor/lambda methods
                # are outside the agreed source-method denominator.
                if name == "<clinit>" or name.startswith(("access$", "lambda$")):
                    continue
                lines = method.findall("./lines/line")
                if not lines:  # Abstract or non-executable method.
                    continue
                total += 1
                if any(int(line.get("hits", "0")) > 0 for line in lines):
                    covered += 1
        if total == 0:
            result["unavailable_reason"] = "no executable methods in modified classes"
            return result
        result.update(covered_methods=covered, total_methods=total, ratio=ratio(covered, total))
    except (ET.ParseError, OSError, ValueError) as exc:
        result["unavailable_reason"] = "cannot read Cobertura methods: " + str(exc)
    return result


def code_coverage(metrics, measured, tool="Defects4J/Cobertura"):
    metrics = metrics if measured and isinstance(metrics, dict) else {}
    lines_covered = metrics.get("LinesCovered")
    lines_total = metrics.get("LinesTotal")
    jacoco = tool == "JaCoCo"
    branches_covered = metrics.get("BranchesCovered" if jacoco else "ConditionsCovered")
    branches_total = metrics.get("BranchesTotal" if jacoco else "ConditionsTotal")
    return {
        "scope": "modified_classes", "revision": "buggy", "tool": tool,
        "lines_covered": lines_covered, "lines_total": lines_total,
        "line_ratio": ratio(lines_covered, lines_total),
        "branch_measure": "branch" if jacoco else "condition",
        "branches_covered": branches_covered, "branches_total": branches_total,
        "branch_ratio": ratio(branches_covered, branches_total),
    }


def common_result(record, experiment, configuration, legacy_path):
    detail = record.get("status", "error")
    status = normalized_status(detail)
    project, bug_id, run = record["project"], str(record["bug_id"]), int(record["run"])
    attempt = record.get("attempt")
    relative_run = Path(project) / f"{project} {bug_id}" / f"run{run}"
    xml_path = legacy_path.parent / "attempts" / str(attempt) / "buggy-coverage.xml"
    tool = record.get("reported_coverage_tool") or "Defects4J/Cobertura"
    metrics = record.get("buggy_coverage") or {}
    if status == "complete" and tool == "JaCoCo":
        methods = {
            "scope": "modified_classes", "revision": "buggy", "tool": "JaCoCo",
            "covered_methods": metrics.get("MethodsCovered"),
            "total_methods": metrics.get("MethodsTotal"),
            "ratio": ratio(metrics.get("MethodsCovered"), metrics.get("MethodsTotal")),
        }
    else:
        methods = (method_coverage(xml_path, record.get("modified_classes")) if status == "complete" else {
        "scope": "modified_classes", "revision": "buggy", "tool": "Defects4J/Cobertura",
        "covered_methods": None, "total_methods": None, "ratio": None,
        "unavailable_reason": "coverage report unavailable",
        })
    observed = record.get("fault_detected") if status in ("complete", "partial") else None
    return {
        "schema_version": SCHEMA_VERSION,
        "experiment": experiment,
        "algorithm": ALGORITHM,
        "algorithm_version": record.get("version"),
        "project": project,
        "bug_id": int(bug_id),
        "run": run,
        "status": status,
        "status_detail": detail,
        "stage": record.get("stage"),
        "error": record.get("error"),
        "coverage_fallback_used": record.get("coverage_fallback_used", False),
        "cobertura_error": record.get("cobertura_error"),
        "jacoco_error": record.get("jacoco_error"),
        "modified_classes": record.get("modified_classes", []),
        "modified_classes_exported": record.get("modified_classes_exported"),
        "modified_classes_excluded": record.get("modified_classes_excluded", []),
        "tests_generated": record.get("tests_generated", 0),
        "search_incomplete": record.get("search_incomplete", False),
        "search_recovered_from_stage": record.get("search_recovered_from_stage"),
        "tests_validated": record.get("tests_validated", 0),
        "fixed_failing_tests": record.get("fixed_failing_tests"),
        "buggy_failing_tests": record.get("buggy_failing_tests"),
        "fault_detected": observed if isinstance(observed, bool) else None,
        "test_coverage": methods,
        "code_coverage": code_coverage(record.get("buggy_coverage"), status == "complete", tool),
        "generation_seconds": record.get("generation_seconds"),
        "total_seconds": record.get("total_seconds"),
        "configuration": {name: configuration[name] for name in (
            "population", "budget", "mutation", "crossover", "input_range", "max_methods",
            "max_tests_per_method", "method_timeout", "job_timeout", "validation_reserve",
            "test_timeout", "command_timeout", "candidate_timeout", "seed", "heap") if name in configuration},
        "artifacts": {
            "tests": str(Path("Test") / experiment / relative_run / str(attempt)) if attempt else None,
            "logs": str(Path("Results") / experiment / relative_run / "attempts" /
                        str(attempt) / "logs") if attempt else None,
            "legacy_result": str(Path("Results") / experiment / relative_run / "result.json"),
        },
    }


def common_path(output_root: Path, record):
    project, bug_id, run = record["project"], str(record["bug_id"]), int(record["run"])
    return output_root / project / f"{project}-{bug_id}" / f"run{run}" / "result.json"


def read_configuration(source_root: Path):
    path = source_root / "configuration.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def write_common_record(record, source_root: Path, output_root: Path | None = None,
                        legacy_path: Path | None = None, configuration=None):
    output_root = output_root or source_root
    project, bug_id, run = record["project"], str(record["bug_id"]), int(record["run"])
    legacy_path = legacy_path or (source_root / project / f"{project} {bug_id}" /
                                  f"run{run}" / "result.json")
    value = common_result(record, source_root.name,
                          configuration if configuration is not None else read_configuration(source_root),
                          legacy_path)
    if output_root.resolve() != source_root.resolve():
        # Shared results may be exported to another drive. Point back to the
        # original evidence instead of implying it lives beside the export.
        value["artifacts"] = {
            "tests": record.get("test_directory"),
            "logs": record.get("log_directory") or str(
                legacy_path.parent / "attempts" / str(record.get("attempt")) / "logs"),
            "legacy_result": str(legacy_path.resolve()),
        }
    write_json(common_path(output_root, record), value)
    return value


def average(records, field):
    values = [value for record in records if (value := field(record)) is not None]
    return sum(values) / len(values) if values else None


def summarize_group(records, selected, project=None):
    if project is not None:
        records = [record for record in records if record["project"] == project]
        selected = {(pid, bid) for pid, bid in selected if pid == project}
    complete = [record for record in records if record["status"] == "complete"]
    tool_counts = {}
    for record in complete:
        tool = record["code_coverage"]["tool"]
        tool_counts[tool] = tool_counts.get(tool, 0) + 1
    mixed_tools = len(tool_counts) > 1
    evaluated = {(r["project"], str(r["bug_id"])) for r in records
                 if isinstance(r.get("fault_detected"), bool)}
    detected = {(r["project"], str(r["bug_id"])) for r in records
                if r.get("fault_detected") is True}
    return {
        "selected_unique_bugs": len(selected),
        "evaluated_unique_bugs": len(evaluated),
        "detected_unique_bugs": len(detected),
        "unknown_outcome_bugs": len(selected - evaluated),
        "fault_detection_rate": ratio(len(detected), len(evaluated)),
        "complete_runs": len(complete),
        "coverage_tools": ";".join(f"{tool}:{count}" for tool, count in sorted(tool_counts.items())),
        "mixed_coverage_tools": mixed_tools,
        "mean_test_coverage_ratio": None if mixed_tools else average(complete, lambda r: r["test_coverage"]["ratio"]),
        "mean_line_coverage_ratio": None if mixed_tools else average(complete, lambda r: r["code_coverage"]["line_ratio"]),
        "mean_branch_coverage_ratio": None if mixed_tools else average(complete, lambda r: r["code_coverage"]["branch_ratio"]),
        "mean_generation_seconds": average(complete, lambda r: r.get("generation_seconds")),
        "mean_total_seconds": average(complete, lambda r: r.get("total_seconds")),
    }


def write_summaries(source_root: Path, output_root: Path | None = None):
    output_root = output_root or source_root
    records = [record for path in sorted(output_root.glob("*/*/run*/result.json"))
               if is_common_record(record := json.loads(path.read_text(encoding="utf-8")))]
    manifest_path = source_root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.is_file() else []
    manifest_selected = {(entry["project"], str(entry["bug_id"])) for entry in manifest}
    algorithms = sorted({r["algorithm"] for r in records} | {ALGORITHM})
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=SUMMARY_FIELDS)
    writer.writeheader()
    overall_by_algorithm = {}
    for algorithm in algorithms:
        group = [r for r in records if r["algorithm"] == algorithm]
        selected = manifest_selected | {(r["project"], str(r["bug_id"])) for r in group}
        for project in sorted({pid for pid, _ in selected}):
            writer.writerow({"algorithm": algorithm, "project": project,
                             **summarize_group(group, selected, project)})
        overall_by_algorithm[algorithm] = summarize_group(group, selected)
    (output_root / "summary_by_project.csv").write_text(stream.getvalue(), encoding="utf-8")
    write_json(output_root / "summary_overall.json", {
        "schema_version": SCHEMA_VERSION,
        "experiment": source_root.name,
        "algorithms": overall_by_algorithm,
    })
    return overall_by_algorithm[ALGORITHM]


def export_existing(source_root: Path, output_root: Path | None = None):
    output_root = output_root or source_root
    configuration = read_configuration(source_root)
    count = 0
    for legacy_path, record in iter_legacy_records(source_root):
        write_common_record(record, source_root, output_root, legacy_path, configuration)
        count += 1
    return count, write_summaries(source_root, output_root)


def main():
    parser = argparse.ArgumentParser(description="Export legacy DE results to the shared output format")
    parser.add_argument("--results", required=True, type=Path,
                        help="Existing Results/<experiment> directory")
    parser.add_argument("--output", type=Path,
                        help="Output directory; defaults to the existing results directory")
    args = parser.parse_args()
    source_root = args.results.resolve()
    if not source_root.is_dir():
        parser.error(f"Results directory does not exist: {source_root}")
    output_root = args.output.resolve() if args.output else source_root
    output_root.mkdir(parents=True, exist_ok=True)
    count, overall = export_existing(source_root, output_root)
    print(f"Exported {count} DE run results to {output_root}")
    print(json.dumps(overall, indent=2))


if __name__ == "__main__":
    main()
