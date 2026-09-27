#!/usr/bin/env python3
"""
Extract Defects4J target source codes into a local dataset folder.
Allows the test generation pipeline to run completely offline/independently of Defects4J checkout.
"""

import re
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
HISTORY_DIR = BASE_DIR / "ai" / "Gemini" / "prompts" / "history"
D4J_PROJECTS_DIR = BASE_DIR / "defects4j" / "framework" / "projects"
GENERATED_TESTS_DIR = BASE_DIR / "ai" / "Gemini" / "generated-tests"
DATASET_DIR = BASE_DIR / "dataset"


def extract_all():
    print(f"[*] Starting clean dataset extraction to: {DATASET_DIR}")
    DATASET_DIR.mkdir(parents=True, exist_ok=True)

    history_files = sorted(HISTORY_DIR.glob("*.txt"))
    if not history_files:
        print("[!] No prompt history files found in ai/Gemini/prompts/history", file=sys.stderr)
        return False

    success_count = 0
    total = len(history_files)
    marker = "\n[TARGET SOURCE CODE]\n"

    for idx, h_file in enumerate(history_files, 1):
        target_name = h_file.stem  # e.g. Chart_1
        project, bug_id = target_name.split("_")

        # 1. Read source code from prompt history using exact newline marker
        content = h_file.read_text(encoding="utf-8", errors="replace")
        if marker not in content or "[OUTPUT PROTOCOL]" not in content:
            print(f"[!] Target delimiters missing in {h_file.name}", file=sys.stderr)
            continue

        parts = content.split(marker, 1)
        source_code = parts[1].split("[OUTPUT PROTOCOL]", 1)[0].strip()

        # 2. Get modified class name
        meta_file = D4J_PROJECTS_DIR / project / "modified_classes" / f"{bug_id}.src"
        if meta_file.is_file():
            mod_classes = meta_file.read_text(encoding="utf-8").strip().splitlines()
            full_class = mod_classes[0].strip()
        else:
            pkg_match = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+);", source_code, re.MULTILINE)
            pkg = pkg_match.group(1) if pkg_match else ""
            cls_match = re.search(r"\b(?:public\s+)?(?:final\s+|abstract\s+)*(?:class|interface|enum|@interface)\s+([A-Za-z0-9_]+)", source_code)
            cls_name = cls_match.group(1) if cls_match else target_name
            full_class = f"{pkg}.{cls_name}" if pkg else cls_name

        raw_class_name = full_class.split(".")[-1]
        clean_name = raw_class_name.replace("$", "_") if raw_class_name.startswith("$") else raw_class_name.split("$")[-1]
        source_filename = f"{raw_class_name}.java" if not raw_class_name.startswith("$") else f"{clean_name}.java"

        # Create target directory
        out_dir = DATASET_DIR / target_name
        out_dir.mkdir(parents=True, exist_ok=True)

        # Remove any metadata.json if present
        meta_json = out_dir / "metadata.json"
        if meta_json.is_file():
            meta_json.unlink()

        # Write clean Java source file
        (out_dir / source_filename).write_text(source_code, encoding="utf-8")

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
│   └── <ClassName>.java    # ซอร์สโค้ด Java ดิบของคลาสที่มีบั๊ก
...
```

## วิธีใช้งาน
สคริปต์ Generator (เช่น `experiments/scripts/pipeline_gemini.py` หรือเครื่องมือ Claude, DE, ACO) สามารถโหลดโค้ดได้โดยตรง:
1. เปิดโฟลเดอร์ของบั๊กที่ต้องการ (เช่น `dataset/Chart_1/`)
2. โหลดโค้ดจากไฟล์ `.java` ภายในโฟลเดอร์โดยตรง
3. ไม่จำเป็นต้องรัน `defects4j checkout` ผ่าน WSL ทำให้ทำงานได้ทุกระบบปฏิบัติการ (Windows, Mac, Linux)
"""
    (DATASET_DIR / "README.md").write_text(readme_text, encoding="utf-8")

    print(f"\n[+] Extraction complete! Successfully created {success_count}/{total} clean bug datasets in {DATASET_DIR.relative_to(BASE_DIR)}")
    return True


if __name__ == "__main__":
    extract_all()
