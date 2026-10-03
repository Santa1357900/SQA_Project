#!/usr/bin/env python3
"""
Full Automated Pipeline Orchestrator:
Phase 1: Generate tests for all remaining defects in Defects4J (854 total).
Phase 2: Run validate.py until 100% of targets are validated into Results/defects4j/
"""

import os
import re
import subprocess
import sys
import time
from pathlib import Path

# Setup paths
BASE_DIR = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = BASE_DIR / "scripts"
DATASET_DIR = BASE_DIR / "dataset"
TEST_CODE_DIR = BASE_DIR / "ai" / "Gemini" / "generated-tests"
RESULT_DIR = BASE_DIR / "Results" / "defects4j" / "Gemini"

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass


def get_d4j_wsl_bin() -> str:
    env_bin = os.environ.get("DEFECTS4J_BIN")
    if env_bin:
        return env_bin
    local_d4j = BASE_DIR / "defects4j" / "framework" / "bin" / "defects4j"
    if local_d4j.is_file():
        try:
            wsl_res = subprocess.run(["wsl", "wslpath", "-a", local_d4j.as_posix()],
                                     capture_output=True, text=True, encoding="utf-8")
            if wsl_res.returncode == 0 and wsl_res.stdout.strip():
                return wsl_res.stdout.strip()
        except Exception:
            pass
    return "defects4j"


def run_wsl(command: str) -> str:
    res = subprocess.run(["wsl", "bash"], input=command, capture_output=True, text=True, encoding="utf-8")
    if res.returncode != 0:
        raise RuntimeError(f"WSL Error ({res.returncode}): {res.stderr.strip()}")
    return res.stdout.strip()


def get_status():
    d4j_bin = get_d4j_wsl_bin()
    # Try querying D4J via WSL, or fallback to local dataset folder
    pids = []
    project_bugs = {}
    try:
        pids = run_wsl(f"{d4j_bin} pids").split()
        for p in pids:
            bugs = run_wsl(f"{d4j_bin} query -p {p} -q bug.id").split()
            project_bugs[p] = bugs
    except Exception:
        if DATASET_DIR.is_dir():
            for d in DATASET_DIR.iterdir():
                if d.is_dir() and "_" in d.name:
                    p, b = d.name.split("_")
                    if p not in project_bugs:
                        project_bugs[p] = []
                    project_bugs[p].append(b)
            pids = sorted(project_bugs.keys())
            for p in pids:
                project_bugs[p] = sorted(project_bugs[p], key=lambda x: int(x) if x.isdigit() else x)

    test_dirs = set()
    if TEST_CODE_DIR.is_dir():
        test_dirs = set(p.name for p in TEST_CODE_DIR.iterdir() if p.is_dir() and any(p.glob("*.java")))

    # Check results in Results/defects4j/Gemini/<Project>/<Project>-<Bug>/run1/result.json
    result_targets = set()
    if RESULT_DIR.is_dir():
        for rjson in RESULT_DIR.glob("*/*/run1/result.json"):
            proj_folder = rjson.parent.parent.parent.name
            bug_folder = rjson.parent.parent.name
            if "-" in bug_folder:
                bug_id = bug_folder.split("-")[1]
                result_targets.add(f"{proj_folder}_{bug_id}")

    total_d4j = sum(len(bugs) for bugs in project_bugs.values())
    missing_gen = {}
    missing_val = []

    for p, bugs in project_bugs.items():
        missing_p = [b for b in bugs if f"{p}_{b}_buggy" not in test_dirs]
        if missing_p:
            missing_gen[p] = missing_p

        for b in bugs:
            if f"{p}_{b}_buggy" in test_dirs and f"{p}_{b}" not in result_targets:
                missing_val.append(f"{p}_{b}")

    return {
        "pids": pids,
        "total_d4j": total_d4j,
        "generated_count": len(test_dirs),
        "validated_count": len(result_targets),
        "missing_gen": missing_gen,
        "missing_val": missing_val
    }


def main():
    print("=" * 70, flush=True)
    print("   🌟 FULL PIPELINE AUTOMATION: GENERATE + VALIDATE (GOAL)", flush=True)
    print("=" * 70, flush=True)

    status = get_status()
    total_missing_gen = sum(len(bugs) for bugs in status["missing_gen"].values())
    print(f"[*] สถานะเริ่มต้น:", flush=True)
    print(f"    - บั๊กทั้งหมดใน Defects4J: {status['total_d4j']}", flush=True)
    print(f"    - สร้างเทสแล้ว: {status['generated_count']}", flush=True)
    print(f"    - วัดผลแล้ว (COMMON_OUTPUT_FORMAT): {status['validated_count']}", flush=True)
    print(f"    - ต้องสร้างเทสเพิ่ม: {total_missing_gen}", flush=True)
    print(f"    - ต้องวัดผลเพิ่ม: {len(status['missing_val'])}", flush=True)
    print("=" * 70, flush=True)

    # ----------------------------------------------------
    # PHASE 1: GENERATE ALL REMAINING TESTS
    # ----------------------------------------------------
    if total_missing_gen > 0:
        print("\n🚀 [PHASE 1] เริ่มการสร้าง Unit Test สำหรับบั๊กที่เหลือทั้งหมด...", flush=True)
        cmd = [sys.executable, str(SCRIPTS_DIR / "pipeline_gemini.py"), "--all"]
        print(f"[*] กำลังเรียกคำสั่ง: {' '.join(cmd)}", flush=True)

        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
        for line in proc.stdout:
            print(line, end="", flush=True)
        proc.wait()

        if proc.returncode != 0:
            print(f"[!] pipeline_gemini.py จบด้วยรหัสข้อผิดพลาด: {proc.returncode}", flush=True)

        status = get_status()
        total_missing_gen = sum(len(bugs) for bugs in status["missing_gen"].values())
        if total_missing_gen > 0:
            print(f"\n[*] พบบั๊กที่ยังไม่สร้าง {total_missing_gen} บั๊ก กำลังทำการ Retry...", flush=True)
            for proj, bugs in status["missing_gen"].items():
                print(f"[*] Retrying {proj}: {bugs}", flush=True)
                retry_cmd = [sys.executable, str(SCRIPTS_DIR / "pipeline_gemini.py"), "--projects", proj, "--bugs"] + bugs
                retry_proc = subprocess.Popen(retry_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
                for line in retry_proc.stdout:
                    print(line, end="", flush=True)
                retry_proc.wait()
    else:
        print("\n[✓] [PHASE 1] มีไฟล์เทสครบทุกบั๊กแล้ว! (854/854)", flush=True)

    # Re-verify phase 1 completion
    status = get_status()
    print("\n" + "=" * 70, flush=True)
    print(f"🎉 PHASE 1 เสร็จสิ้น! สร้างเทสสำเร็จ: {status['generated_count']}/{status['total_d4j']} บั๊ก", flush=True)
    print("=" * 70, flush=True)

    # ----------------------------------------------------
    # PHASE 2: RUN VALIDATION UNTIL 100% COMPLETE
    # ----------------------------------------------------
    print("\n⚖️ [PHASE 2] เริ่มกระบวนการรัน validate.py บนทุกเป้าหมายที่รอตรวจ...", flush=True)

    val_attempts = 0
    max_val_attempts = 5
    while val_attempts < max_val_attempts:
        status = get_status()
        missing_val_count = len(status["missing_val"])
        print(f"\n[*] รอบที่ {val_attempts + 1}: จำนวนเป้าหมายที่รอรัน validate: {missing_val_count} บั๊ก", flush=True)
        if missing_val_count == 0:
            print("[✓] รัน validate ครบ 100% ทุกเป้าหมายแล้ว!", flush=True)
            break

        val_attempts += 1
        val_cmd = [sys.executable, str(SCRIPTS_DIR / "validate.py"), "--workers", "4"]
        print(f"[*] เริ่มรัน validate.py ด้วย 4 workers...", flush=True)
        val_proc = subprocess.Popen(val_cmd, cwd=str(BASE_DIR), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
        for line in val_proc.stdout:
            print(line, end="", flush=True)
        val_proc.wait()

        time.sleep(3)

    # Combined report collection
    print("\n" + "=" * 70, flush=True)
    print("[*] รวบรวมสรุปผลลัพธ์ทั้งหมด (summary_by_project.csv และ summary_overall.json)...", flush=True)
    collect_res = subprocess.run([sys.executable, str(SCRIPTS_DIR / "validate.py"), "--collect-only"],
                                 cwd=str(BASE_DIR), capture_output=True, text=True, encoding="utf-8")
    print(collect_res.stdout.strip(), flush=True)

    final_status = get_status()
    print("=" * 70, flush=True)
    print("   🏆 สรุปผลการดำเนินการ (FINAL PIPELINE EXECUTION SUMMARY)", flush=True)
    print(f"   - บั๊กทั้งหมด: {final_status['total_d4j']}", flush=True)
    print(f"   - สร้างไฟล์เทสสำเร็จ: {final_status['generated_count']}/{final_status['total_d4j']}", flush=True)
    print(f"   - รัน validate สำเร็จ: {final_status['validated_count']}/{final_status['total_d4j']}", flush=True)
    print("=" * 70, flush=True)


if __name__ == "__main__":
    main()
