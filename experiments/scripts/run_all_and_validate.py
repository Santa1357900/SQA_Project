#!/usr/bin/env python3
"""
Full Automated Pipeline Orchestrator:
Phase 1: Generate tests for all remaining defects in Defects4J (854 total).
Phase 2: Run validate.py until 100% of targets are validated.
"""

import os
import re
import subprocess
import sys
import time
from pathlib import Path

# Setup paths
BASE_DIR = Path(__file__).resolve().parents[2]
SCRIPTS_DIR = BASE_DIR / "experiments" / "scripts"
TEST_CODE_DIR = BASE_DIR / "TestCode"
RESULT_DIR = BASE_DIR / "Result"
D4J_WSL_BIN = "/mnt/c/Users/Petchy_STB/Desktop/sqa/FinalProjectSQA/SQA_Project/defects4j/framework/bin/defects4j"

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

def run_wsl(command: str) -> str:
    res = subprocess.run(["wsl", "bash"], input=command, capture_output=True, text=True, encoding="utf-8")
    if res.returncode != 0:
        raise RuntimeError(f"WSL Error ({res.returncode}): {res.stderr.strip()}")
    return res.stdout.strip()

def get_status():
    pids = run_wsl(f"{D4J_WSL_BIN} pids").split()
    test_dirs = set(p.name for p in TEST_CODE_DIR.iterdir() if p.is_dir() and any(p.glob("*.java")))
    result_dirs = set(p.name for p in RESULT_DIR.iterdir() if (p / "result.json").is_file())
    
    total_d4j = 0
    missing_gen = {}
    missing_val = []
    
    for p in pids:
        bugs = run_wsl(f"{D4J_WSL_BIN} query -p {p} -q bug.id").split()
        total_d4j += len(bugs)
        missing_p = [b for b in bugs if f"{p}_{b}_buggy" not in test_dirs]
        if missing_p:
            missing_gen[p] = missing_p
            
        for b in bugs:
            if f"{p}_{b}_buggy" in test_dirs and f"{p}_{b}" not in result_dirs:
                missing_val.append(f"{p}_{b}")
                
    return {
        "pids": pids,
        "total_d4j": total_d4j,
        "generated_count": len(test_dirs),
        "validated_count": len(result_dirs),
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
    print(f"    - วัดผลแล้ว: {status['validated_count']}", flush=True)
    print(f"    - ต้องสร้างเทสเพิ่ม: {total_missing_gen}", flush=True)
    print(f"    - ต้องวัดผลเพิ่ม: {len(status['missing_val'])}", flush=True)
    print("=" * 70, flush=True)

    # ----------------------------------------------------
    # PHASE 1: GENERATE ALL REMAINING TESTS
    # ----------------------------------------------------
    if total_missing_gen > 0:
        print("\n🚀 [PHASE 1] เริ่มการสร้าง Unit Test สำหรับบั๊กที่เหลือทั้งหมด...", flush=True)
        # สั่งรัน pipeline_gemini.py --all
        cmd = [sys.executable, str(SCRIPTS_DIR / "pipeline_gemini.py"), "--all"]
        print(f"[*] กำลังเรียกคำสั่ง: {' '.join(cmd)}", flush=True)
        
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
        for line in proc.stdout:
            print(line, end="", flush=True)
        proc.wait()
        
        if proc.returncode != 0:
            print(f"[!] pipeline_gemini.py จบด้วยรหัสข้อผิดพลาด: {proc.returncode}", flush=True)
        
        # ตรวจสอบว่ามีบั๊กใดที่ยังหลุดอยู่อีกหรือไม่
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
        print("\n[✓] [PHASE 1] มีไฟล์เทสครบทุกบั๊กแล้ว! ข้ามไปยังขั้นตอนถัดไป", flush=True)

    # Re-verify phase 1 completion
    status = get_status()
    total_missing_gen = sum(len(bugs) for bugs in status["missing_gen"].values())
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
        # รัน validate.py ผ่าน WSL
        val_cmd = ["wsl", "python3", "experiments/scripts/validate.py", "--workers", "4"]
        print(f"[*] เริ่มรัน validate.py ด้วย 4 workers...", flush=True)
        val_proc = subprocess.Popen(val_cmd, cwd=str(BASE_DIR), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
        for line in val_proc.stdout:
            print(line, end="", flush=True)
        val_proc.wait()
        
        # หน่วงเวลาสั้นๆ ก่อนเช็คสถานะรอบถัดไป
        time.sleep(3)

    # Combined report collection
    print("\n" + "=" * 70, flush=True)
    print("[*] รวมผลลัพธ์ทั้งหมดเป็น report.csv...", flush=True)
    collect_res = subprocess.run(["wsl", "python3", "experiments/scripts/validate.py", "--collect-only"], cwd=str(BASE_DIR), capture_output=True, text=True, encoding="utf-8")
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
