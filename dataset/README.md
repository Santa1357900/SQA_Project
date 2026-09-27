# Defects4J Target Source Codes Dataset

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
