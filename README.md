# SQA Project: Automated Test Generation

โครงงานนี้เปรียบเทียบวิธีสร้าง unit test อัตโนมัติสำหรับโปรเจกต์ Java ใน Defects4J โดยใช้ทั้ง search-based algorithms และโมเดล AI จากนั้นตรวจสอบเทสต์กับเวอร์ชัน buggy/fixed และวัด coverage กับ fault detection

## Algorithms และ AI ที่ใช้

| วิธี                          | แนวทางทำงาน                                                                                                                                                      |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Differential Evolution (DE)   | วิวัฒนาการชุด input ด้วย mutation, crossover และ selection ตาม fitness เพื่อค้นหา input ที่นำไปสร้าง JUnit tests แล้วตรวจด้วย Defects4J                          |
| Ant Colony Optimization (ACO) | มดสร้างเส้นทางบน control-flow graph โดยใช้ pheromone และ heuristic; ปรับ pheromone ด้วยการระเหยและเสริมเส้นทางที่ครอบคลุม branch แล้วแปลง input เป็น JUnit tests |
| Claude Sonnet 5               | ส่ง source code และ prompt ให้โมเดลสร้าง JUnit tests จากนั้นปรับรูปแบบโค้ดและตรวจเทสต์บน Defects4J                                                               |
| Gemini 3.5 Flash Lite         | ส่ง source code และ prompt ให้โมเดลสร้าง JUnit tests แล้วตรวจเทสต์บน Defects4J                                                                                   |

DE และ ACO เป็นอัลกอริทึมค้นหา ไม่ได้เรียก generative AI; Claude และ Gemini ใช้โมเดลภาษาเพื่อสร้างโค้ดทดสอบ ส่วนขอบเขต dataset และการตั้งค่าทดลองให้ดู README ของแต่ละวิธี เพราะแต่ละ pipeline อาจใช้ชุดบั๊กและการตั้งค่าต่างกัน

## สมาชิกและส่วนที่รับผิดชอบ

| ชื่อ              | รหัสนักศึกษา | วิธีที่รับผิดชอบ |
| ----------------- | ------------ | ---------------- |
| ธนกร ทองศรี       | 673380040-2  | DE               |
| ธนันชัย พันธราช   | 673380042-8  | ACO              |
| พีรพัฒน์ กรธนกิจ  | 673380053-3  | Claude           |
| ณัฐพงค์ ป้องกันยา | 673380038-9  | Gemini           |

## โฟลเดอร์ของแต่ละวิธี

- `Differential Evolution(DE)/` — DE/rand/1/bin และ runner สำหรับ Defects4J
- `Ant Colony Optimization(ACO)/` — ACO test generator และการทดลองที่เกี่ยวข้อง
- `Claude/` — prompt, configuration, pipeline เรียก Claude และ validator
- `Gemini/` — prompt, pipeline เรียก Gemini, dataset และผลการทดลอง

ดูขั้นตอนติดตั้ง วิธีรัน และรูปแบบผลลัพธ์ฉบับเต็มใน `README.md` ของโฟลเดอร์แต่ละวิธี ก่อนทดลอง Claude หรือ Gemini ให้ตั้ง API key ใน `.env` ตามคู่มือของโฟลเดอร์นั้น และห้าม commit `.env` หรือเปิดเผย API key

### การแปลงรูปแบบข้อมูล Coverage

เนื่องจากที่โปรเจคมีการเขียน conditions coverage ผิดเป็น branches coverage จึงอาจทำให้เกิดความเข้าใจผิดขึ้นจึงต้องมีการปรับไฟล์ `result.json` ของแต่ละวิธีโดยใช้ไฟล์ `migrate_coverage_schema.py` ในการปรับ

ไฟล์ `migrate_coverage_schema.py` ใช้ปรับ `result.json` ของ ACO, Claude, DE และ Gemini ให้เป็น schema version `1.1` โดยระบุชนิดของ coverage ให้ตรงกับเครื่องมือวัด: ผลจาก Cobertura เปลี่ยนชื่อฟิลด์ `branches_*` เป็น `conditions_*` และกำหนด `coverage_measure` เป็น `condition` ส่วนผลจาก JaCoCo กำหนดเป็น `branch` สคริปต์แก้เฉพาะรูปแบบข้อมูลที่บันทึกไว้ **ไม่รันทดสอบหรือคำนวณ coverage ใหม่**

รันจากโฟลเดอร์หลักของโปรเจกต์:

```bash
python migrate_coverage_schema.py
```

คำสั่งนี้แสดงรายการไฟล์ที่จะเปลี่ยนโดยยังไม่แก้ไฟล์ หากต้องการบันทึกการเปลี่ยนแปลง ให้รัน:

```bash
python migrate_coverage_schema.py --write
```

**ควรรัน `--write` เพียงครั้งเดียวกับผลลัพธ์ชุดเดิม** เพราะสคริปต์ปัจจุบันไม่ได้ตรวจว่าไฟล์เคยแปลงเป็น schema `1.1` แล้ว
