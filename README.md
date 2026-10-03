# SQA Project — Automated Unit Test Generation

โครงงานทดสอบซอฟต์แวร์อัตโนมัติ (Software Quality Assurance) เพื่อสร้างและประเมินประสิทธิภาพชุดทดสอบระดับ Unit Test ด้วย **Generative AI (Gemini Engine)** บนชุดข้อมูลมาตรฐาน **Defects4J** (854 บั๊ก / 17 โปรเจกต์)

---

## สารบัญเอกสาร
- 📖 [Flow การทำงานของระบบและคู่มือสคริปต์ (docs/PIPELINE_WORKFLOW.md)](docs/PIPELINE_WORKFLOW.md)
- 📋 [ข้อกำหนดรูปแบบผลลัพธ์มาตรฐาน (docs/COMMON_OUTPUT_FORMAT.md)](docs/COMMON_OUTPUT_FORMAT.md)
- 📁 [ชุดข้อมูลคลาสเป้าหมาย 854 บั๊ก (dataset/README.md)](dataset/README.md)
- 📊 [ผลลัพธ์การทดลองมาตรฐาน (Results/defects4j/)](Results/defects4j/)

---

## โครงสร้างโปรเจกต์ (Clean Project Architecture)

```text
SQA_Project_Clean/
├── scripts/                         # [หมวดสคริปต์] เครื่องมือรันและประเมินผลทั้งหมด
│   ├── pipeline_gemini.py           # สร้าง Unit Test อัตโนมัติด้วย Gemini
│   ├── validate.py                  # ตรวจสอบเทสบน Defects4J และสรุปผลตามมาตรฐานกลาง
│   ├── run_all_and_validate.py      # ออโต้ไปป์ไลน์คุมทั้ง Generate และ Validate
│   ├── summarize_results.py         # แสดงตารางสรุปผลทางหน้าจอ Terminal
│   ├── extract_dataset.py           # สกัดซอร์สโค้ดจาก Defects4J
│   ├── generate_prompt.py           # เครื่องมือสร้าง prompt ตัวอย่าง
│   └── record_result.py             # บันทึกผลลัพธ์ลง CSV
├── Results/                         # [หมวดผลลัพธ์] ผลการทดลองและรายงานสรุปทั้งหมด
│   ├── defects4j/                   # ผลลัพธ์มาตรฐานตาม COMMON_OUTPUT_FORMAT
│   │   ├── Gemini/                  # ผลลัพธ์รายบั๊ก (854 โฟลเดอร์: result.json + logs)
│   │   ├── summary_by_project.csv   # สรุปผลแยกตาม 17 โปรเจกต์
│   │   └── summary_overall.json     # สรุปภาพรวม Gemini (854 บั๊ก, FDR 2.20%)
│   └── results.csv                  # ตารางผลลัพธ์รวม 854 แถว
├── dataset/                         # [หมวดข้อมูลนำเข้า] คลาสที่มีบั๊ก 854 ตัว
├── ai/                              # [หมวด AI] แม่แบบ Prompt, ประวัติ Prompt, และโค้ดเทสที่สร้าง
│   └── Gemini/
│       ├── prompts/                 # template_prompt.txt และ history/ (854 ไฟล์)
│       └── generated-tests/         # ไฟล์ JUnit Test (*Test.java) ครบ 854 บั๊ก
├── docs/                            # [หมวดเอกสาร] สเปกและคู่มือการทำงาน
└── README.md
```

---

## การเริ่มต้นใช้งานอย่างรวดเร็ว (Quick Start)

### 1. ติดตั้ง Dependencies
```bash
pip install google-generativeai python-dotenv
```

### 2. ตั้งค่า API Key
สร้างไฟล์ `.env` ที่โฟลเดอร์หลัก (ดูตัวอย่างจาก `.env.example`):
```env
GEMINI_API_KEY=your_gemini_api_key_here
# คีย์สำรองสำหรับระบบ Auto Key Rotation (ถ้ามี)
#GEMINI_API_KEY=your_backup_gemini_api_key_1
```

### 3. รัน Generator
สคริปต์จะดึงโค้ดจากโฟลเดอร์ `dataset/` ในเครื่องทันที (รันได้บนทุก OS โดยไม่ต้องติดตั้ง Defects4J):
```bash
# ตัวอย่าง: ทดสอบสร้าง Unit Test สำหรับโปรเจกต์ Lang
python scripts/pipeline_gemini.py --projects Lang --limit 5
```

### 4. รวบรวมสรุปผลลัพธ์ (ตามมาตรฐาน COMMON_OUTPUT_FORMAT)
```bash
# รวบรวมและสร้าง summary_by_project.csv และ summary_overall.json
python scripts/validate.py --collect-only

# แสดงตารางสรุปผลบนหน้าจอ Terminal
python scripts/summarize_results.py
```

รายละเอียดคำสั่งทั้งหมดและการวัดผล (Validation) ศึกษาเพิ่มเติมได้ที่ [docs/PIPELINE_WORKFLOW.md](docs/PIPELINE_WORKFLOW.md) และ [docs/COMMON_OUTPUT_FORMAT.md](docs/COMMON_OUTPUT_FORMAT.md)