#!/usr/bin/env python3
"""
Automated Pipeline for Defects4J Unit Test Generation using Gemini Engine.
Author: SQA Group (Gemini Pipeline v2.0)

Workflow:
1. Query bugs from Defects4J (Lang, Math, Chart, etc.)
2. Automatically check out target class source code via Defects4J
3. Formulate professional SQA Gemini prompt (with v2.0 compile-safety rules)
4. Generate standalone JUnit 4 test suites via Google Gemini API with Auto Key Rotation
5. Clean & sanitize Java code (Java 6 compatibility, throws Throwable, boxed NaN/Infinity)
6. Save generated tests into TestCode_v2/ (isolated from v1) and ai/Gemini/generated-tests-v2/
"""

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from dotenv import load_dotenv

# โหลด API Key จากไฟล์ .env
load_dotenv()

# รองรับภาษาไทยบน Windows Terminal
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ที่อยู่หลักของโปรเจกต์
BASE_DIR = Path(__file__).resolve().parents[2]
ENV_FILE = BASE_DIR / ".env"
D4J_WSL_BIN = "/mnt/c/Users/Petchy_STB/Desktop/sqa/FinalProjectSQA/SQA_Project/defects4j/framework/bin/defects4j"

# ที่อยู่ของไฟล์แม่แบบ Prompt
TEMPLATE_FILE = BASE_DIR / "ai" / "Gemini" / "prompts" / "template_prompt.txt"

def get_prompt_template() -> str:
    """โหลดข้อความ Prompt จากไฟล์ ai/Gemini/prompts/template_prompt.txt"""
    if TEMPLATE_FILE.is_file():
        return TEMPLATE_FILE.read_text(encoding="utf-8")
    raise FileNotFoundError(f"Template prompt file not found at: {TEMPLATE_FILE}")

def run_wsl(command: str) -> str:
    """สั่งรันคำสั่ง Linux ผ่าน WSL และคืนค่า stdout โดยใช้ stdin เพื่อป้องกันปัญหา escaping"""
    res = subprocess.run(["wsl", "bash"], input=command, capture_output=True, text=True, encoding="utf-8")
    if res.returncode != 0:
        raise RuntimeError(f"WSL Error ({res.returncode}): {res.stderr.strip()}")
    return res.stdout.strip()

class KeyManager:
    """จัดการโหลดและสลับ API Key ของ Gemini อัตโนมัติเมื่อโควตาเต็ม"""
    def __init__(self, model_name: str = "gemini-3.5-flash-lite"):
        self.model_name = model_name
        self.keys = []
        self.current_idx = 0
        self.load_keys()
        self.model = None
        self.setup_current_model()

    def load_keys(self):
        self.keys = []
        if ENV_FILE.is_file():
            content = ENV_FILE.read_text(encoding="utf-8")
            # 1. ค้นหา Key ปัจจุบันที่ active
            active_match = re.search(r"^\s*GEMINI_API_KEY\s*=\s*([A-Za-z0-9_\-\.]+)", content, re.M)
            if active_match:
                self.keys.append(active_match.group(1).strip())
            
            # 2. ค้นหา Key สำรองที่คอมเมนต์ไว้
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("#"):
                    comment_body = line.lstrip("#").strip()
                    m = re.search(r"(?:GEMINI_API_KEY\s*=\s*)?([A-Za-z0-9_\-\.]{20,})", comment_body)
                    if m:
                        k = m.group(1).strip()
                        if k not in self.keys:
                            self.keys.append(k)

        if not self.keys:
            print("[!] ไม่พบ API Key ใน .env เลย! กรุณาตรวจสอบไฟล์ .env", file=sys.stderr)
            sys.exit(1)

    def setup_current_model(self):
        try:
            import google.generativeai as genai
        except ImportError:
            print("[!] ไม่พบไลบรารี google-generativeai กรุณาติดตั้งด้วย: pip install google-generativeai", file=sys.stderr)
            sys.exit(1)

        current_key = self.keys[self.current_idx]
        genai.configure(api_key=current_key)
        self.model = genai.GenerativeModel(self.model_name)
        print(f"[*] ใช้งาน Gemini API Key ตัวที่ {self.current_idx + 1}/{len(self.keys)} (ลงท้ายด้วย ...{current_key[-8:]})")

    def rotate_key(self) -> bool:
        if self.current_idx + 1 < len(self.keys):
            self.current_idx += 1
            new_active = self.keys[self.current_idx]
            print(f"\n🔄 [KEY ROTATION] โควตา Key เดิมเต็ม! กำลังสลับไปใช้ Key สำรองตัวที่ {self.current_idx + 1}/{len(self.keys)} (ลงท้ายด้วย ...{new_active[-8:]})...")
            
            # อัปเดตไฟล์ .env
            lines = [f"GEMINI_API_KEY={new_active}\n\n"]
            for i, k in enumerate(self.keys):
                if i != self.current_idx:
                    lines.append(f"#{k}\n")
            ENV_FILE.write_text("".join(lines), encoding="utf-8")
            
            self.setup_current_model()
            return True
        else:
            print(f"\n[❌] โควตาของ Gemini API Key หมดครบทุกตัวแล้ว ({len(self.keys)} ตัว)!", file=sys.stderr)
            return False

def clean_java_output(raw_text: str) -> str:
    """ตัดบล็อก Markdown ```java ออกถ้า Gemini เผลอใส่มา"""
    text = raw_text.strip()
    match = re.search(r"```(?:java)?\s*(.*?)\s*```", text, re.DOTALL)
    if match:
        text = match.group(1).strip()
    else:
        text = re.sub(r"^```(?:java)?\s*", "", text).strip()
        text = re.sub(r"\s*```$", "", text).strip()
    # ตรวจสอบว่ามีปีกกาปิดครบถ้วน
    if not text.endswith("}"):
        text += "\n}"
    return text

def sanitize_java_code(code: str) -> str:
    """ทำความสะอาดไวยากรณ์ Java ให้สอดคล้องกับ Java 6 และลดปัญหา compile error ตามข้อกำหนด SQA"""
    # 0. ล้างข้อผิดพลาดปีกกาซ้ำซ้อนถ้ามี
    code = re.sub(r'\{\s*throws\s+Throwable\s*\{', 'throws Throwable {', code)

    # 1. แทนที่ Diamond Operator <> ด้วย raw type สำหรับ Java 6 (-source 1.6)
    code = re.sub(r'new\s+([A-Za-z0-9_$.]+)\s*<>\s*\(', r'new \1(', code)

    # 2. เพิ่ม throws Throwable ให้กับทุก @Test method ที่ยังไม่มี เพื่อแก้ checked exception
    def add_throws(match):
        sig = match.group(1)
        if 'throws' in sig:
            return sig + ' {'
        return sig + ' throws Throwable {'

    code = re.sub(r'((?:@Test(?:\s*\([^)]*\))?\s+)+(?:public|protected)?\s+void\s+[A-Za-z0-9_]+\s*\([^)]*\))\s*\{', add_throws, code)

    # 3. แก้ปัญหา ambiguous assertEquals(null, ...) -> assertNull(...)
    code = re.sub(r'assertEquals\s*\(\s*null\s*,\s*([^,\)]+)\)', r'assertNull(\1)', code)

    # 4. แก้ปัญหา ambiguous assertEquals(Double.NaN / Infinity, ...) -> assertEquals(Double.valueOf(...), ...)
    code = re.sub(r'assertEquals\s*\(\s*Double\.NaN\s*,', 'assertEquals(Double.valueOf(Double.NaN),', code)
    code = re.sub(r'assertEquals\s*\(\s*Double\.POSITIVE_INFINITY\s*,', 'assertEquals(Double.valueOf(Double.POSITIVE_INFINITY),', code)
    code = re.sub(r'assertEquals\s*\(\s*Double\.NEGATIVE_INFINITY\s*,', 'assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY),', code)

    # 5. แก้ไข ClassUtil import ใน JacksonDatabind ถ้าถูกเรียกใช้แต่ไม่มี import
    if "ClassUtil." in code and "import com.fasterxml.jackson.databind.util.ClassUtil;" not in code:
        code = re.sub(r'(package\s+[^;]+;\s*)', r'\1import com.fasterxml.jackson.databind.util.ClassUtil;\n', code, count=1)

    return code

def extract_target_source(project: str, bug_id: str):
    """สั่ง Defects4J ดึงซอร์สโค้ดของคลาสที่มีบั๊กออกมา พร้อมรองรับ edge cases (inner class, newly added files)"""
    temp_dir = f"/tmp/d4j_ext_{project}_{bug_id}"
    try:
        # Checkout bug
        run_wsl(f"rm -rf {temp_dir} && {D4J_WSL_BIN} checkout -p {project} -v {bug_id}b -w {temp_dir}")
        
        # ดึง Modified Class และ Source Directory
        mod_classes = run_wsl(f"{D4J_WSL_BIN} export -p classes.modified -w {temp_dir}").split()
        if not mod_classes:
            raise ValueError(f"No modified classes found for {project}-{bug_id}")
            
        src_dir = run_wsl(f"{D4J_WSL_BIN} export -p dir.src.classes -w {temp_dir}")
        
        target_class = None
        code_content = None
        
        # ค้นหาคลาสต้นทางที่มีอยู่จริงใน buggy revision
        for cls in mod_classes:
            candidates = [cls.replace(".", "/") + ".java"]
            if "$" in cls:
                candidates.append(cls.split("$")[0].replace(".", "/") + ".java")
                
            for cand in candidates:
                cand_path = f"{temp_dir}/{src_dir}/{cand}"
                check = run_wsl(f"if [ -f '{cand_path}' ]; then cat '{cand_path}'; fi")
                if check:
                    target_class = cls
                    code_content = check
                    break
            if target_class:
                break
                
        # หากไม่พบคลาสใน buggy (อาจเป็นไฟล์ใหม่ที่ถูกเพิ่มใน fixed) ให้ดึงจาก fixed revision
        if not target_class:
            fixed_temp = f"/tmp/d4j_ext_{project}_{bug_id}_f"
            try:
                run_wsl(f"rm -rf {fixed_temp} && {D4J_WSL_BIN} checkout -p {project} -v {bug_id}f -w {fixed_temp}")
                fixed_src_dir = run_wsl(f"{D4J_WSL_BIN} export -p dir.src.classes -w {fixed_temp}")
                for cls in mod_classes:
                    candidates = [cls.replace(".", "/") + ".java"]
                    if "$" in cls:
                        candidates.append(cls.split("$")[0].replace(".", "/") + ".java")
                    for cand in candidates:
                        cand_path = f"{fixed_temp}/{fixed_src_dir}/{cand}"
                        check = run_wsl(f"if [ -f '{cand_path}' ]; then cat '{cand_path}'; fi")
                        if check:
                            target_class = cls
                            code_content = check
                            break
                    if target_class:
                        break
            finally:
                run_wsl(f"rm -rf {fixed_temp}")
                
        if not target_class:
            raise FileNotFoundError(f"Could not find source file for any modified class: {mod_classes}")

        # แยก package และ class name
        parts = target_class.split(".")
        pkg_name = ".".join(parts[:-1]) if len(parts) > 1 else ""
        class_name = parts[-1]
        
        clean_name = class_name.replace("$", "_") if class_name.startswith("$") else class_name.split("$")[-1]
        test_class_name = f"{clean_name}Test"
        
        return {
            "full_class": target_class,
            "class_name": class_name,
            "package_name": pkg_name,
            "test_class_name": test_class_name,
            "source_code": code_content
        }
    finally:
        # ลบโฟลเดอร์ชั่วคราว
        run_wsl(f"rm -rf {temp_dir}")

def process_bug(key_mgr: KeyManager, project: str, bug_id: str, output_root: Path, overwrite: bool = False, version_tag: str = "v2"):
    """ประมวลผลบั๊ก 1 ตัว: ดึงโค้ด -> ส่ง Gemini -> เซฟไฟล์ Test"""
    target_dir = output_root / f"{project}_{bug_id}_buggy"
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # ตรวจสอบว่าเคยสร้างไปแล้วหรือไม่
    existing_tests = list(target_dir.glob("*.java"))
    if existing_tests and not overwrite:
        print(f"[-] [{project}_{bug_id}] มีไฟล์เทสอยู่แล้ว ({existing_tests[0].name}) ข้าม...")
        return True

    print(f"\n[*] [{project}_{bug_id}] กำลังดึงซอร์สโค้ดจาก Defects4J...")
    try:
        data = extract_target_source(project, bug_id)
    except Exception as e:
        print(f"[!] [{project}_{bug_id}] ดึงซอร์สโค้ดล้มเหลว: {e}", file=sys.stderr)
        return False

    template = get_prompt_template()
    prompt = template.replace("{PACKAGE_NAME}", data["package_name"]) \
                     .replace("{TEST_CLASS_NAME}", data["test_class_name"]) \
                     .replace("{TARGET_SOURCE_CODE}", data["source_code"])

    print(f"[*] [{project}_{bug_id}] กำลังส่ง Prompt ไปยัง Gemini API ({data['class_name']})...")
    
    # ยิงเรียก Gemini API พร้อมระบบ Retry และ Key Rotation
    for attempt in range(1, 6):
        try:
            t0 = time.time()
            current_prompt = prompt
            if attempt > 1:
                prefix = f"// DIRECTIVE: Write clean, original synthetic unit tests from first principles.\n// Strictly do NOT recite verbatim test cases from open-source repositories.\n// Use unique descriptive test method names (e.g. test_{data['test_class_name']}_behavior_attempt_{attempt}()).\n\n"
                current_prompt = prefix + prompt

            response = key_mgr.model.generate_content(current_prompt)
            if response.candidates and response.candidates[0].finish_reason == 4:
                raise ValueError("finish_reason 4: recitation detected")
                
            elapsed = round(time.time() - t0, 2)
            clean_code = clean_java_output(response.text)
            clean_code = sanitize_java_code(clean_code)
            
            output_file = target_dir / f"{data['test_class_name']}.java"
            output_file.write_text(clean_code, encoding="utf-8")
            
            # บันทึกลง ai/Gemini/generated-tests ตามข้อกำหนด
            gen_dir_name = f"generated-tests-{version_tag}" if version_tag else "generated-tests"
            gemini_gen_dir = BASE_DIR / "ai" / "Gemini" / gen_dir_name / f"{project}_{bug_id}_buggy"
            gemini_gen_dir.mkdir(parents=True, exist_ok=True)
            (gemini_gen_dir / f"{data['test_class_name']}.java").write_text(clean_code, encoding="utf-8")
            
            # บันทึก prompt ที่ส่งจริงไว้เป็นหลักฐานใน history
            history_dir_name = f"history_{version_tag}" if version_tag else "history"
            prompt_log_dir = BASE_DIR / "ai" / "Gemini" / "prompts" / history_dir_name
            prompt_log_dir.mkdir(parents=True, exist_ok=True)
            (prompt_log_dir / f"{project}_{bug_id}.txt").write_text(current_prompt, encoding="utf-8")

            print(f"[+] [{project}_{bug_id}] สำเร็จ! (ใช้เวลา {elapsed}s) บันทึกไฟล์เทสที่: {output_file.relative_to(BASE_DIR)}")
            return True
        except Exception as e:
            err_str = str(e)
            print(f"[!] [{project}_{bug_id}] Gemini Error (รอบที่ {attempt}/5): {e}", file=sys.stderr)
            if "finish_reason" in err_str and "4" in err_str:
                time.sleep(2)
                continue
            
            # ตรวจสอบปัญหาโควตาเต็ม -> ทำการหมุนเวียน Key
            if "GenerateRequestsPerDay" in err_str or "QuotaExceeded" in err_str or ("429" in err_str and "quota" in err_str.lower()):
                print(f"\n[!] ตรวจพบว่าโควตาของ Key ปัจจุบันหมดแล้ว! กำลังทำการสลับ Key...")
                rotated = key_mgr.rotate_key()
                if rotated:
                    time.sleep(2)
                    continue
                else:
                    print(f"\n[❌] โควตารายวันของ Gemini API เต็มหมดทุก Key แล้ว!", file=sys.stderr)
                    sys.exit(1)
            elif "429" in err_str or "ResourceExhausted" in err_str:
                wait_time = 25 * attempt
                print(f"[*] ติด Rate Limit ชั่วคราว พักรอ {wait_time} วินาทีก่อนลองใหม่...", file=sys.stderr)
                time.sleep(wait_time)
            else:
                time.sleep(attempt * 4)

    return False

def main():
    parser = argparse.ArgumentParser(description="Automated Defects4J Test Generator with Gemini Engine v2.0")
    parser.add_argument("--all", action="store_true", help="รันทุกโปรเจกต์และทุกบั๊กใน Defects4J (ทั้งหมด 854 บั๊ก)")
    parser.add_argument("--projects", nargs="+", default=["Lang"], help="รายชื่อโปรเจกต์ เช่น Lang Math Chart หรือ all")
    parser.add_argument("--bugs", nargs="+", help="ระบุหมายเลขบั๊กเฉพาะเจาะจง เช่น 1 3 4")
    parser.add_argument("--limit", type=int, help="จำกัดจำนวนบั๊กที่จะประมวลผล (เช่น --limit 5)")
    parser.add_argument("--model", default="gemini-3.5-flash-lite", help="โมเดล Gemini (default: gemini-3.5-flash-lite)")
    parser.add_argument("--output-dir", default=str(BASE_DIR / "TestCode"), help="โฟลเดอร์สำหรับเก็บไฟล์เทส (default: TestCode)")
    parser.add_argument("--version-tag", default=None, help="Tag เวอร์ชันสำหรับโฟลเดอร์ ai/Gemini (default: None)")
    parser.add_argument("--overwrite", action="store_true", help="สร้างเทสทับไฟล์เดิมถ้ามีอยู่แล้ว")
    args = parser.parse_args()

    output_root = Path(args.output_dir).resolve()

    print("=" * 65)
    print("   🚀 GEMINI AUTOMATED TEST GENERATOR PIPELINE")
    print("   Model:", args.model)
    print("   Output Directory:", output_root)
    print("   Version Tag:", args.version_tag or "Standard")
    print("=" * 65)

    key_mgr = KeyManager(args.model)
    
    # ดึงรายชื่อโปรเจกต์
    if args.all or any(p.lower() == "all" for p in args.projects):
        project_list = run_wsl(f"{D4J_WSL_BIN} pids").split()
        print(f"[*] โหมด All: เลือกทำทั้งหมด {len(project_list)} โปรเจกต์")
    else:
        project_list = args.projects

    total_processed = 0
    total_success = 0

    for project in project_list:
        print(f"\n>>> เริ่มโปรเจกต์: {project}")
        
        # ดึงรายชื่อบั๊กทั้งหมดของโปรเจกต์
        if args.bugs:
            bug_list = args.bugs
        else:
            bug_query = run_wsl(f"{D4J_WSL_BIN} query -p {project} -q bug.id")
            bug_list = bug_query.split()

        if args.limit and len(bug_list) > args.limit:
            bug_list = bug_list[:args.limit]

        print(f"[*] พบทั้งหมด {len(bug_list)} บั๊กที่ต้องทำ: {bug_list[:10]}...")

        for bug_id in bug_list:
            success = process_bug(key_mgr, project, bug_id, output_root, args.overwrite, args.version_tag)
            total_processed += 1
            if success:
                total_success += 1
            # เว้นจังหวะ 1 วินาทีเพื่อไม่ให้ชน Rate Limit
            time.sleep(1)

    print("\n" + "=" * 65)
    print(f"🎉 เสร็จสิ้นภารกิจ! สำเร็จ {total_success}/{total_processed} บั๊ก")
    print(f"📁 ไฟล์เทสทั้งหมดถูกเก็บไว้ที่: {output_root}")
    print("👉 ขั้นตอนถัดไป: สั่งรัน validate.py เพื่อวัดผลคะแนนได้ทันที!")
    print("=" * 65)

if __name__ == "__main__":
    main()
