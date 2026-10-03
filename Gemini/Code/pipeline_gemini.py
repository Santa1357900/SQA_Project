#!/usr/bin/env python3
"""
Automated Pipeline for Defects4J Unit Test Generation using KKU IntelSphere API.
API Endpoint: https://gen.ai.kku.ac.th/api/v1/chat/completions

โฟลว์การทำงานหลัก (4 ขั้นตอน):
1. Load Code: ดึงโค้ด Java ที่มีบั๊กจาก Defects4J หรือโฟลเดอร์ dataset
2. Build Prompt: นำโค้ด Java มารวมกับกฎการสร้าง Unit Test (JUnit 4)
3. Call KKU API: ยิง HTTP POST ไปที่ KKU IntelSphere API Gateway
4. Save Test: รับโค้ดผลลัพธ์จาก API มาบันทึกเป็นไฟล์ .java
"""

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path
import requests
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

BASE_DIR = Path(__file__).resolve().parents[1]
DATASET_DIR = BASE_DIR / "dataset"
TEMPLATE_FILE = BASE_DIR / "ai" / "Gemini" / "prompts" / "template_prompt.txt"

# ==============================================================================
# 🎯 ส่วนตั้งค่า API ของมหาวิทยาลัยขอนแก่น (KKU IntelSphere)
# ==============================================================================
KKU_API_URL = "https://gen.ai.kku.ac.th/api/v1/chat/completions"
DEFAULT_MODEL = "gemini-3.5-flash-lite"


# ==============================================================================
# ขั้นตอนที่ 1: ดึงและเตรียม Source Code
# ==============================================================================
def get_prompt_template() -> str:
    """โหลดแม่แบบ Prompt กฎการสร้าง Unit Test"""
    if TEMPLATE_FILE.is_file():
        return TEMPLATE_FILE.read_text(encoding="utf-8")
    raise FileNotFoundError(f"Template prompt not found at: {TEMPLATE_FILE}")


def load_target_source(project: str, bug_id: str):
    """อ่านไฟล์ Source Code Java ของคลาสที่ต้องการทดสอบ"""
    target_name = f"{project}_{bug_id}"
    target_dir = DATASET_DIR / target_name
    
    # 1. ลองอ่านจากโฟลเดอร์ dataset ก่อน
    if target_dir.is_dir():
        java_files = list(target_dir.glob("*.java"))
        if java_files:
            src_file = java_files[0]
            code_content = src_file.read_text(encoding="utf-8")
            
            pkg_match = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+);", code_content, re.MULTILINE)
            pkg_name = pkg_match.group(1) if pkg_match else ""
            raw_class_name = src_file.stem
            clean_name = raw_class_name.replace("$", "_") if raw_class_name.startswith("$") else raw_class_name.split("$")[-1]
            
            return {
                "class_name": clean_name,
                "package_name": pkg_name,
                "test_class_name": f"{clean_name}Test",
                "source_code": code_content
            }

    # 2. ถ้าไม่มีใน dataset ให้ checkout ผ่าน Defects4J ใน WSL
    temp_dir = f"/tmp/d4j_ext_{project}_{bug_id}"
    try:
        subprocess.run(["wsl", "bash", "-c", f"rm -rf {temp_dir} && defects4j checkout -p {project} -v {bug_id}b -w {temp_dir}"], check=True)
        mod_class = subprocess.check_output(["wsl", "bash", "-c", f"defects4j export -p classes.modified -w {temp_dir}"], text=True).strip().split()[0]
        src_dir = subprocess.check_output(["wsl", "bash", "-c", f"defects4j export -p dir.src.classes -w {temp_dir}"], text=True).strip()
        rel_path = mod_class.replace(".", "/") + ".java"
        code_content = subprocess.check_output(["wsl", "bash", "-c", f"cat {temp_dir}/{src_dir}/{rel_path}"], text=True)
        
        parts = mod_class.split(".")
        pkg_name = ".".join(parts[:-1]) if len(parts) > 1 else ""
        class_name = parts[-1]
        
        return {
            "class_name": class_name,
            "package_name": pkg_name,
            "test_class_name": f"{class_name}Test",
            "source_code": code_content
        }
    finally:
        subprocess.run(["wsl", "bash", "-c", f"rm -rf {temp_dir}"])


# ==============================================================================
# ขั้นตอนที่ 2 & 3: ยิงเรียก KKU IntelSphere API (ส่วนสำคัญที่ใช้อธิบาย)
# ==============================================================================
def call_kku_api(prompt_text: str, api_key: str, model_name: str = DEFAULT_MODEL) -> str:
    """
    ฟังก์ชันยิงคำขอ HTTP POST ไปยัง KKU IntelSphere API Gateway
    - URL: https://gen.ai.kku.ac.th/api/v1/chat/completions
    - Headers: Authorization: Bearer <API_KEY>
    - Body: JSON ตามมาตรฐาน OpenAI Chat Completions
    """
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }

    payload = {
        "model": model_name,
        "messages": [
            {
                "role": "user",
                "content": prompt_text
            }
        ],
        "temperature": 0.2
    }

    # ส่ง HTTP POST Request
    print(f"    -> [HTTP POST] ยิงคำขอไปยัง: {KKU_API_URL} (Model: {model_name})")
    response = requests.post(KKU_API_URL, headers=headers, json=payload, timeout=90)
    
    # ตรวจสอบสถานะการตอบกลับ
    if response.status_code != 200:
        raise RuntimeError(f"KKU API Error ({response.status_code}): {response.text}")

    # สกัดข้อความผลลัพธ์ที่เป็นโค้ด Java จาก JSON Response
    result_json = response.json()
    generated_code = result_json["choices"][0]["message"]["content"]
    return generated_code


# ==============================================================================
# ขั้นตอนที่ 4: ทำความสะอาดโค้ดและบันทึกไฟล์
# ==============================================================================
def clean_and_sanitize_java(raw_text: str) -> str:
    """ตัดเครื่องหมาย Markdown ```java และปรับไวยากรณ์ให้ตรงตาม Java 6"""
    # 1. ตัด Markdown Fence
    text = raw_text.strip()
    match = re.search(r"```(?:java)?\s*(.*?)\s*```", text, re.DOTALL)
    if match:
        text = match.group(1).strip()
    else:
        text = re.sub(r"^```(?:java)?\s*", "", text).strip()
        text = re.sub(r"\s*```$", "", text).strip()

    if not text.endswith("}"):
        text += "\n}"

    # 2. ปรับให้เข้ากับ Java 6 (ลบ diamond operator, เพิ่ม throws Throwable)
    text = re.sub(r'new\s+([A-Za-z0-9_$.]+)\s*<>\s*\(', r'new \1(', text)
    text = re.sub(r'((?:@Test(?:\s*\([^)]*\))?\s+)+(?:public|protected)?\s+void\s+[A-Za-z0-9_]+\s*\([^)]*\))\s*\{', 
                  lambda m: m.group(1) + (' {' if 'throws' in m.group(1) else ' throws Throwable {'), 
                  text)
    return text


def run_pipeline_for_bug(project: str, bug_id: str, api_key: str, model_name: str, output_root: Path):
    """โฟลว์การทำงานสำหรับบั๊ก 1 ตัว"""
    print(f"\n=======================================================")
    print(f"[*] เริ่มประมวลผล: {project}-{bug_id}")
    print(f"=======================================================")

    # 1. ดึงโค้ดต้นฉบับ
    print("[Step 1/4] โหลด Source Code ภาษา Java...")
    data = load_target_source(project, bug_id)
    print(f"    -> Target Class: {data['class_name']} ({data['package_name']})")

    # 2. สร้าง Prompt
    print("[Step 2/4] ประกอบ Prompt ตามมาตรฐาน SQA...")
    template = get_prompt_template()
    prompt = template.replace("{PACKAGE_NAME}", data["package_name"]) \
                     .replace("{TEST_CLASS_NAME}", data["test_class_name"]) \
                     .replace("{TARGET_SOURCE_CODE}", data["source_code"])

    # 3. ยิงไปหา KKU API
    print("[Step 3/4] ส่ง Prompt ไปยัง KKU IntelSphere API...")
    t0 = time.time()
    raw_output = call_kku_api(prompt, api_key, model_name)
    elapsed = round(time.time() - t0, 2)
    print(f"    -> ได้รับผลลัพธ์สำเร็จใน {elapsed} วินาที")

    # 4. ทำความสะอาดโค้ดและเซฟไฟล์
    print("[Step 4/4] Clean โค้ดและบันทึกไฟล์ Unit Test...")
    final_code = clean_and_sanitize_java(raw_output)
    
    target_dir = output_root / f"{project}_{bug_id}_buggy"
    target_dir.mkdir(parents=True, exist_ok=True)
    out_file = target_dir / f"{data['test_class_name']}.java"
    out_file.write_text(final_code, encoding="utf-8")

    print(f"[✓] บันทึกไฟล์เทสเรียบร้อย: {out_file.relative_to(BASE_DIR)}")


# ==============================================================================
# จุดเริ่มต้นการทำงาน (Main)
# ==============================================================================
def main():
    parser = argparse.ArgumentParser(description="Generate Defects4J Tests via KKU IntelSphere API")
    parser.add_argument("--projects", nargs="+", default=["Lang"], help="ชื่อโปรเจกต์ เช่น Lang Chart Math")
    parser.add_argument("--bugs", nargs="+", default=["1"], help="หมายเลขบั๊ก เช่น 1 2 3")
    parser.add_argument("--model", default=DEFAULT_MODEL, help=f"โมเดล AI (default: {DEFAULT_MODEL})")
    parser.add_argument("--output-dir", default=str(BASE_DIR / "ai" / "KKU" / "generated-tests"), help="โฟลเดอร์ปลายทาง")
    args = parser.parse_args()

    # ดึง API Key จาก .env (ใช้ GEMINI_API_KEY)
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("[!] ไม่พบ GEMINI_API_KEY ในไฟล์ .env", file=sys.stderr)
        print("    กรุณาใส่ GEMINI_API_KEY=... ในไฟล์ .env ก่อนรัน", file=sys.stderr)
        sys.exit(1)

    output_root = Path(args.output_dir).resolve()

    print("*" * 65)
    print("   KKU INTELSPHERE API TEST GENERATOR PIPELINE")
    print("   Endpoint: " + KKU_API_URL)
    print("   Model:    " + args.model)
    print("   Output:   " + str(output_root))
    print("*" * 65)

    for project in args.projects:
        for bug_id in args.bugs:
            try:
                run_pipeline_for_bug(project, bug_id, api_key, args.model, output_root)
            except Exception as e:
                print(f"[X] เกิดข้อผิดพลาดใน {project}-{bug_id}: {e}", file=sys.stderr)


if __name__ == "__main__":
    main()
