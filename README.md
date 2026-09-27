# SQA Project — Automated Unit Test Generation

โครงงานทดสอบซอฟต์แวร์อัตโนมัติ (Software Quality Assurance) เพื่อเปรียบเทียบประสิทธิภาพการสร้าง Unit Test ระหว่าง **Generative AI** (Gemini, Claude) และ **Search-Based / Metaheuristic Algorithms** (Differential Evolution, Ant Colony Optimization) บนชุดข้อมูลมาตรฐาน **Defects4J** (854 บั๊ก / 17 โปรเจกต์)

---

## สารบัญเอกสาร
- 📖 [Flow การทำงานของระบบและคู่มือ Git (docs/PIPELINE_WORKFLOW.md)](docs/PIPELINE_WORKFLOW.md)
- 📁 [ชุดข้อมูลคลาสเป้าหมาย 854 บั๊ก (dataset/README.md)](dataset/README.md)

---

## การเริ่มต้นใช้งานอย่างรวดเร็ว (Quick Start)

### 1. ติดตั้ง Dependencies
```bash
pip install google-generativeai python-dotenv
```

### 2. ตั้งค่า API Key
สร้างไฟล์ `.env` ในโฟลเดอร์หลัก:
```env
GEMINI_API_KEY=your_gemini_api_key_here
```

### 3. รัน Generator
สคริปต์จะดึงโค้ดจากโฟลเดอร์ `dataset/` ในเครื่องทันที (รันได้บนทุก OS โดยไม่ต้องติดตั้ง Defects4J):
```bash
# ตัวอย่าง: ทดสอบสร้าง Unit Test สำหรับโปรเจกต์ Lang
python experiments/scripts/pipeline_gemini.py --projects Lang --limit 5
```

รายละเอียดคำสั่งทั้งหมดและการวัดผล (Validation) ศึกษาเพิ่มเติมได้ที่ [docs/PIPELINE_WORKFLOW.md](docs/PIPELINE_WORKFLOW.md)