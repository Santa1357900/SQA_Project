import argparse
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RESULT_DIRS = [
    "Ant Colony Optimization(ACO)/Results",
    "Claude/Results",
    "Differential Evolution(DE)/Results",
    "Gemini/Results",
]


def migrate(path: Path, write: bool) -> bool:
    try:
        result = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"SKIP {path}: {exc}")
        return False

    coverage = result.get("code_coverage")
    if not isinstance(coverage, dict):
        return False

    tool = coverage.get("tool", "")
    measure = coverage.get("branch_measure")

    # Cobertura reports conditions; JaCoCo reports branches.
    if measure == "condition" or "Cobertura" in tool:
        coverage["conditions_covered"] = coverage.pop("branches_covered", None)
        coverage["conditions_total"] = coverage.pop("branches_total", None)
        coverage["condition_ratio"] = coverage.pop("branch_ratio", None)
        coverage["coverage_measure"] = "condition"
    elif measure == "branch" or "JaCoCo" in tool:
        coverage["coverage_measure"] = "branch"
    else:
        print(f"SKIP {path}: cannot identify coverage measure")
        return False

    coverage.pop("branch_measure", None)
    result["schema_version"] = "1.1"

    if write:
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(result, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)

    print(f"{'UPDATED' if write else 'WOULD UPDATE'} {path}")
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--write",
        action="store_true",
        help="Apply the migration. Without this flag, only preview changes.",
    )
    args = parser.parse_args()

    count = 0
    for relative_dir in RESULT_DIRS:
        directory = ROOT / relative_dir
        if not directory.is_dir():
            print(f"MISSING {directory}")
            continue

        for result_file in directory.rglob("result.json"):
            count += migrate(result_file, args.write)

    print(f"{'Migrated' if args.write else 'Ready to migrate'}: {count} result files")


if __name__ == "__main__":
    main()