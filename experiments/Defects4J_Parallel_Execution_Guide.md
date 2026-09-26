# คู่มือการรัน Defects4J แบบ Parallel ทั้งหมด 854 บั๊ก
**Defects4J Massive Parallel Test Execution Guide**  
**สคริปต์ควบคุม:** `experiments/run_d4j_parallel.py`  
**ผลลัพธ์:** `experiments/results_all_defects4j.csv`  

---

## 1. ภาพรวมระบบ (System Overview)

ระบบรันชุดทดสอบแบบขนาน (Parallel Execution) ถูกพัฒนาขึ้นเพื่อทดสอบและเก็บข้อมูลบั๊กทั้งหมดใน **Defects4J Benchmark (รวม 854 บั๊ก จาก 17 โปรเจกต์ขนาดใหญ่)** โดยใช้ขุมพลังของซีพียูหลายคอร์แบบ Multiprocessing

### ตารางสรุปจำนวนบั๊กในแต่ละโปรเจกต์ (Total: 854 บั๊ก)

| โปรเจกต์ | จำนวนบั๊ก | โปรเจกต์ | จำนวนบั๊ก | โปรเจกต์ | จำนวนบั๊ก |
| :--- | :---: | :--- | :---: | :--- | :---: |
| **Closure** | 174 | **Compress** | 47 | **Cli** | 39 |
| **Math** | 106 | **Mockito** | 38 | **Collections** | 28 |
| **JacksonDatabind** | 110 | **Chart** | 26 | **Time** | 26 |
| **Jsoup** | 93 | **JacksonCore** | 26 | **Codec** | 18 |
| **Lang** | 61 | **JxPath** | 22 | **Gson** | 18 |
| **Csv** | 16 | **JacksonXml** | 6 | **รวมทั้งหมด** | **854 บั๊ก** |

---

## 2. ความพร้อมของเครื่อง (Environment Readiness)

* **CPU Cores:** 16 Cores (สามารถเปิดรันพร้อมกันได้ตั้งแต่ 4 ถึง 12 Workers)
* **WSL Disk Space:** พื้นที่ว่าง 951 GB (เหลือเฟือสำหรับการทดสอบ)
* **Java Environment:** OpenJDK 11 (รองรับ Defects4J บนเครื่องนี้ได้ 100%)
* **ความเร็วเฉลี่ย:** ~4.8 วินาทีต่อ 1 บั๊ก (ในกรณีบั๊กขนาดเล็ก-ปานกลาง)

---

## 3. ฟีเจอร์ความปลอดภัยของสคริปต์ `run_d4j_parallel.py`

1. **Auto Disk Cleanup:** ระบบจะทำการลบ Workspace ชั่วคราวของบั๊กนั้น ๆ ทันทีที่รันเสร็จ เพื่อป้องกันไม่ให้ขนาดโปรเจกต์ 854 ตัวกินพื้นที่ดิสก์จนเต็ม
2. **Timeout Protection (300 วินาที):** ป้องกันการค้าง หากมีบางบั๊กที่ทดสอบแล้วเจอ Infinite Loop ระบบจะตัดจบและบันทึกผลว่า `Timeout` ทันที แล้วข้ามไปทำบั๊กถัดไป
3. **Real-time Incremental CSV:** ผลลัพธ์จะถูกเขียนและบันทึก (Flush) ลงไฟล์ `results_all_defects4j.csv` ทันทีทีละแถว แม้จะหยุดการทำงานกลางคัน ข้อมูลที่รันเสร็จไปแล้วจะไม่สูญหาย

---

## 4. วิธีสั่งรัน (Execution Commands)

สามารถเปิด **PowerShell** หรือ **Windows Terminal** แล้วคัดลอกคำสั่งไปวางได้ทันที:

### แบบที่ 1: รันทั้ง 854 บั๊กแบบเต็มสูบ (แนะนำ 8 Cores)
รันแบบแสดงสถานะบนหน้าจอแบบเรียลไทม์:
```powershell
wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -j 8
```
> **หมายเหตุ:** 
> - ปรับ `-j 8` เป็น `-j 12` ได้หากต้องการความเร็วสูงสุด
> - คาดการณ์เวลาที่ใช้: ประมาณ **1.5 – 3 ชั่วโมง** จบครบทั้งหมด

---

### แบบที่ 2: รันเป็น Background Process (ปล่อยเครื่องรันข้ามคืน / ปิดจอได้)
ใช้คำสั่ง `nohup` เพื่อให้ระบบทำงานต่อไปเรื่อย ๆ แม้จะปิด Terminal:
```powershell
wsl -d Ubuntu bash -c "nohup python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -j 8 > /tmp/d4j_run.log 2>&1 &"
```

* **คำสั่งดูความคืบหน้าแบบ Real-time:**
  ```powershell
  wsl -d Ubuntu tail -f /tmp/d4j_run.log
  ```
  *(กด `Ctrl + C` เพื่อออกจากหน้าจอติดตาม โดยที่โปรแกรมยังคงรันต่อไปในพื้นหลัง)*

* **คำสั่งตรวจสอบว่าโปรแกรมยังรันอยู่หรือไม่:**
  ```powershell
  wsl -d Ubuntu bash -c "ps aux | grep run_d4j_parallel"
  ```

* **คำสั่งสั่งหยุดการรันทั้งหมด (Stop / Kill):**
  ```powershell
  wsl -d Ubuntu bash -c "pkill -f run_d4j_parallel.py"
  ```

---

### แบบที่ 3: รันเฉพาะโปรเจกต์ที่ต้องการ (ตัวอย่างสั้น ๆ เพื่อทดสอบ)
* **ทดสอบโปรเจกต์ `Csv` (มี 16 บั๊ก ใช้เวลา ~1-2 นาที):**
  ```powershell
  wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -p Csv -j 4
  ```

* **รันโปรเจกต์ `Lang` ทั้งหมด (มี 61 บั๊ก ใช้เวลา ~10-15 นาที):**
  ```powershell
  wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -p Lang -j 8
  ```

---

## 5. การเปิดดูผลลัพธ์ (Viewing Results)

ไฟล์ผลลัพธ์จะถูกจัดเก็บไว้ที่:
`experiments/results_all_defects4j.csv`

### โครงสร้างข้อมูลในตารางผลลัพธ์
| คอลัมน์ | คำอธิบาย | ตัวอย่างข้อมูล |
| :--- | :--- | :--- |
| `project` | ชื่อโปรเจกต์ใน Defects4J | `Csv`, `Lang`, `Math` |
| `bug_id` | หมายเลขบั๊ก | `1`, `2`, `3` |
| `checkout` | สถานะการดึงโค้ดเวอร์ชันบั๊ก | `SUCCESS` / `FAILED` |
| `compile` | สถานะการคอมไพล์โค้ด | `SUCCESS` / `FAILED` |
| `tests_status` | สถานะการรันชุดทดสอบ | `COMPLETED` / `FAILED` |
| `failing_tests_count` | จำนวนเคสทดสอบที่ตรวจพบบั๊ก | `1`, `2` (ตรวจเจอบั๊กจริง) |
| `elapsed_seconds` | เวลาที่ใช้ในการประมวลผล (วินาที) | `4.80`, `12.50` |
| `error_msg` | ข้อความแจ้งเตือนข้อผิดพลาด (ถ้ามี) | `""` หรือข้อความ Error |

### วิธีเปิดดูข้อมูล:
1. **เปิดด้วย Antigravity IDE / VS Code:** คลิกเปิดไฟล์ `experiments/results_all_defects4j.csv` ได้โดยตรง
2. **เปิดด้วย Microsoft Excel:** ดับเบิลคลิกไฟล์ CSV เพื่อเปิดทำกราฟ วิเคราะห์สถิติ หรือทำตารางสรุปนำเสนออาจารย์
3. **ดูสรุปจำนวนแถวที่รันเสร็จแล้วผ่าน Terminal:**
   ```powershell
   wsl -d Ubuntu wc -l /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/results_all_defects4j.csv
   ```
