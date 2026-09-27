#!/usr/bin/env python3
"""
Extract Defects4J target source codes and metadata into a local dataset folder.
Allows the test generation pipeline to run completely offline/independently of Defects4J checkout.
"""

import json
import re
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
HISTORY_DIR = BASE_DIR / "ai" / "Gemini" / "prompts" / "history"
D4J_PROJECTS_DIR = BASE_DIR / "defects4j" / "framework" / "projects"
GENERATED_TESTS_DIR = BASE_DIR / "ai" / "Gemini" / "generated-tests"
DATASET_DIR = BASE_DIR / "dataset"


def extract_all():
    print(f"[*] Starting dataset extraction to: {DATASET_DIR}")
    DATASET_DIR.mkdir(parents=True, exist_ok=True)

    history_files = sorted(HISTORY_DIR.glob("*.txt"))
    if not history_files:
        print("[!] No prompt history files found in ai/Gemini/prompts/history", file=sys.stderr)
        return False

    success_count = 0
    total = len(history_files)

    for idx, h_file in enumerate(history_files, 1):
        target_name = h_file.stem  # e.g. Chart_1
        project, bug_id = target_name.split("_")

        # 1. Read source code from prompt history
        content = h_file.read_text(encoding="utf-8", errors="replace")
        if "[TARGET SOURCE CODE]" not in content or "[OUTPUT PROTOCOL]" not in content:
            print(f"[!] Target delimiters missing in {h_file.name}", file=sys.stderr)
            continue

        parts = content.split("[TARGET SOURCE CODE]", 1)
        source_code = parts[1].split("[OUTPUT PROTOCOL]", 1)[0].strip()

        # 2. Get modified class from Defects4J metadata if available
        meta_file = D4J_PROJECTS_DIR / project / "modified_classes" / f"{bug_id}.src"
        if meta_file.is_file():
            mod_classes = meta_file.read_text(encoding="utf-8").strip().splitlines()
            full_class = mod_classes[0].strip()
        else:
            # Fallback parse package & class from source code
            pkg_match = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+);", source_code, re.MULTILINE)
            pkg = pkg_match.group(1) if pkg_match else ""
            cls_match = re.search(r"\b(?:public\s+)?(?:final\s+|abstract\s+)*(?:class|interface|enum|@interface)\s+([A-Za-z0-9_]+)", source_code)
            cls_name = cls_match.group(1) if cls_match else target_name
            full_class = f"{pkg}.{cls_name}" if pkg else cls_name

        # Parse package and class names
        class_parts = full_class.split(".")
        pkg_name = ".".join(class_parts[:-1]) if len(class_parts) > 1 else ""
        raw_class_name = class_parts[-1]

        # Determine clean class name and test class name
        # Check generated test directory first for ground truth
        test_dir = GENERATED_TESTS_DIR / f"{target_name}_buggy"
        test_files = list(test_dir.glob("*Test.java"))
        if test_files:
            test_class_name = test_files[0].stem
            clean_name = test_class_name[:-4] if test_class_name.endswith("Test") else test_class_name
        else:
            clean_name = raw_class_name.replace("$", "_") if raw_class_name.startswith("$") else raw_class_name.split("$")[-1]
            test_class_name = f"{clean_name}Test"

        source_filename = f"{raw_class_name}.java" if not raw_class_name.startswith("$") else f"{clean_name}.java"

        # Create target directory
        out_dir = DATASET_DIR / target_name
        out_dir.mkdir(parents=True, exist_ok=True)

        # Write Java source file
        (out_dir / source_filename).write_text(source_code, encoding="utf-8")

        # Write metadata.json
        meta_data = {
            "project": project,
            "bug_id": int(bug_id),
            "target_class": full_class,
            "package_name": pkg_name,
            "class_name": clean_name,
            "raw_class_name": raw_class_name,
            "test_class_name": test_class_name,
            "source_file": source_filename
        }
        (out_dir / "metadata.json").write_text(json.dumps(meta_data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        success_count += 1
        if idx % 100 == 0 or idx == total:
            print(f"[*] Processed {idx}/{total} targets...")

    # Write README.md in dataset folder
    readme_text = """# Defects4J Target Source Codes Dataset

ชุดข้อมูลซอร์สโค้ดของคลาสที่มีบั๊ก (Target Classes) ทั้งหมด 854 บั๊ก จาก 17 โปรเจกต์ใน Defects4J Benchmark

## โครงสร้างไดเรกทอรี
```
dataset/
├── <Project>_<BugID>/
│   ├── metadata.json       # ข้อมูลคลาส, package name, test class name
│   └── <ClassName>.java    # ซอร์สโค้ด Java ของคลาสที่มีบั๊ก
...
```

## วิธีใช้งาน
สคริปต์ Generator (เช่น `experiments/scripts/pipeline_gemini.py` หรือเครื่องมือ Claude, DE, ACO) สามารถโหลดโค้ดได้โดยตรง:
1. อ่านไฟล์ `metadata.json` ในโฟลเดอร์ของบั๊กที่ต้องการ
2. โหลดโค้ดจากไฟล์ `.java` ที่ระบุใน `source_file`
3. ไม่จำเป็นต้องรัน `defects4j checkout` ผ่าน WSL ทำให้ทำงานได้ทุกระบบปฏิบัติการ (Windows, Mac, Linux)
"""
    (DATASET_DIR / "README.md").write_text(readme_text, encoding="utf-8")

    print(f"\n[+] Extraction complete! Successfully created {success_count}/{total} bug datasets in {DATASET_DIR.relative_to(BASE_DIR)}")
    return True


if __name__ == "__main__":
    extract_all()
