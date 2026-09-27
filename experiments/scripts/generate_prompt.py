import argparse
import os

def create_prompt(project: str, bug_id: str, class_name: str, source_path: str, output_path: str):
    if not os.path.exists(source_path):
        raise FileNotFoundError(f"Source file not found: {source_path}")
    
    with open(source_path, "r", encoding="utf-8", errors="ignore") as f:
        source_code = f.read()

    prompt = f"""คุณเป็นผู้เชี่ยวชาญด้านการทดสอบซอฟต์แวร์ ให้วิเคราะห์ Source Code ภาษา Java จากโปรเจกต์ใน Defects4J ที่กำหนด และสร้างกรณีทดสอบระดับ Unit Test ด้วย JUnit

ข้อมูลโปรเจกต์
• Project: {project}
• Project ID/Version: {bug_id} (Buggy)
• Class ที่ต้องการทดสอบ: {class_name}

ข้อกำหนด
1. สร้าง Test Case สำหรับ Java Class ที่กำหนด (ใช้ JUnit 4: @Test จาก org.junit.Test และ assertions จาก org.junit.Assert.*)
2. ครอบคลุม Normal Case, Boundary Case และ Exception Case หากมี
3. พยายามเพิ่ม Branch Coverage และ Code Coverage ให้มากที่สุด
4. สร้าง Test Case ที่มีโอกาสตรวจพบข้อบกพร่องของโปรแกรม
5. Test ที่สร้างต้องสามารถ Compile และ Execute กับโปรเจกต์ที่กำหนดได้
6. แสดงผลลัพธ์เฉพาะโค้ด JUnit Test เท่านั้น

Java Source Code:
```java
{source_code}
```
"""
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(prompt)
    print(f"[OK] Prompt saved to {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate AI Prompt for Defects4J Java Classes")
    parser.add_argument("--project", default="Lang", help="Defects4J Project Name (e.g. Lang)")
    parser.add_argument("--bug", default="1", help="Bug ID (e.g. 1)")
    parser.add_argument("--class-name", default="org.apache.commons.lang3.math.NumberUtils", help="Target Java Class Name")
    parser.add_argument("--source-file", required=True, help="Path to the Java source file")
    parser.add_argument("--output", default=None, help="Output prompt file path")
    args = parser.parse_args()

    out = args.output or f"ai/Gemini/prompts/{args.project}_{args.bug}.txt"
    create_prompt(args.project, args.bug, args.class_name, args.source_file, out)
