# Defects4J Target Source Codes Dataset

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
