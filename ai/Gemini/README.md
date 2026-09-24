# Gemini Test Case Generation Guide

โฟลเดอร์นี้สำหรับจัดเก็บข้อมูลและผลลัพธ์การสร้าง Test Case ด้วย **Gemini** ตามข้อกำหนดของโปรเจกต์ SQA

---

## 📁 โครงสร้างโฟลเดอร์

```text
ai/Gemini/
├── prompts/              # เก็บข้อความ Prompt ทั้งหมดที่ส่งให้ Gemini
│   ├── template_prompt.txt
│   └── Lang_1.txt
├── generated-tests/      # เก็บซอร์สโค้ด JUnit Test ที่ Gemini ตอบกลับมา
│   └── Lang_1_Test.java
├── results/              # เก็บผลลัพธ์การวัดผลเฉพาะของ Gemini (JSON/CSV)
└── README.md             # คู่มือการใช้งานนี้
```

---

## 🚀 ขั้นตอนการทดลองกับ Bug แรก (Lang 1: NumberUtils)

### ขั้นตอนที่ 1: เตรียม Prompt
ไฟล์ Prompt พร้อมใช้งานถูกสร้างไว้แล้วที่:
👉 [`ai/Gemini/prompts/Lang_1.txt`](file:///C:/Users/Petchy_STB/Desktop/sqa/FinalProjectSQA/SQA_Project/ai/Gemini/prompts/Lang_1.txt)

*(ไฟล์นี้ประกอบด้วย Prompt ตามข้อกำหนดของกลุ่ม + ซอร์สโค้ดฉบับเต็มของ `NumberUtils.java` จาก Lang 1b)*

### ขั้นตอนที่ 2: นำไปใส่ใน AI
1. เปิดเว็บ [https://ai.kku.ac.th](https://ai.kku.ac.th) หรือ Gemini Web
2. Copy ข้อความทั้งหมดในไฟล์ `Lang_1.txt` ไปวางในช่องแชทแล้วกดส่ง
3. **ข้อแนะนำเพิ่มเติมขณะคุยกับ Gemini:**
   * ตรวจสอบว่าคลาสทดสอบใช้ **JUnit 4** (เช่น `@Test` จาก `org.junit.Test`, Assertions จาก `org.junit.Assert.*`)
   * ตรวจสอบว่ามี package ถูกต้องตามคลาสเป้าหมาย เช่น `package org.apache.commons.lang3.math;`
   * หากโค้ดยาวจนข้อความหยุด ให้พิมพ์ `ต่อ` หรือ `continue` ให้ครบถ้วน

### ขั้นตอนที่ 3: บันทึก JUnit Test ที่ได้
นำโค้ด Java ที่ Gemini สร้างมาบันทึกไว้ในโฟลเดอร์:
👉 `ai/Gemini/generated-tests/Lang_1_Test.java` (หรือระบุ Run เช่น `Lang_1_Run1_Test.java`)

### ขั้นตอนที่ 4: นำไปรันและวัดผล (ร่วมกับคนทำ Measurement Pipeline)
1. นำไฟล์ Test ไปวางใน Project ของ Defects4J (เช่น `src/test/java/org/apache/commons/lang3/math/`)
2. รัน Compile และ Test ผ่าน Defects4J:
   ```bash
   defects4j compile
   defects4j test
   defects4j coverage
   ```
3. บันทึกผลลงใน:
   * `experiments/results.csv`
