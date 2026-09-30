# คู่มือวิธีการทำ (SQA Project: Defects4J & ACO Experiment Guide)

คู่มือสรุปขั้นตอนการทำงานตั้งแต่เริ่มต้นจนถึงการดูผลลัพธ์ แบบกระชับ เข้าใจง่าย พร้อมคำสั่งที่คัดลอกไปใช้งานได้ทันที

---

## สารบัญขั้นตอน
1. [เตรียม Environment (ติดตั้งครั้งแรก)](#1-เตรียม-environment-ติดตั้งครั้งแรก)
2. [การรัน Defects4J Benchmark แบบขนาน (Parallel Runner)](#2-การรัน-defects4j-benchmark-แบบขนาน-parallel-runner)
3. [การรันการทดลองสร้าง Test ด้วย ACO (Ant Colony Optimization)](#3-การรันการทดลองสร้าง-test-ด้วย-aco-ant-colony-optimization)
4. [ตำแหน่งและการเปิดดูผลลัพธ์ (Results)](#4-ตำแหน่งและการเปิดดูผลลัพธ์-results)
5. [Cheat Sheet รวมคำสั่งด่วน](#5-cheat-sheet-รวมคำสั่งด่วน)

---

## 1. เตรียม Environment (ติดตั้งครั้งแรก)

> **หมายเหตุ:** หากเคยติดตั้ง WSL, Java และ Defects4J แล้ว สามารถข้ามไป [ขั้นตอนที่ 2](#2-การรัน-defects4j-benchmark-แบบขนาน-parallel-runner) ได้ทันที

### 1.1 ติดตั้ง Ubuntu บน WSL (เปิด PowerShell ในโหมด Admin)
```powershell
wsl --install -d Ubuntu
```
*(เมื่อติดตั้งเสร็จแล้ว ให้รีสตาร์ตคอมพิวเตอร์ 1 ครั้ง หากระบบแจ้งเตือน จากนั้นเปิด Ubuntu ขึ้นมาและตั้ง Username / Password)*

### 1.2 ติดตั้ง Dependencies ทั้งหมดใน Ubuntu (เปิด Ubuntu Terminal)
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-8-jdk openjdk-11-jdk git subversion perl curl build-essential cpanminus unzip python3 python3-pip libstring-interpolate-perl libdbi-perl libdbd-csv-perl libjson-perl libjson-parse-perl
```

### 1.3 ติดตั้ง Defects4J และตั้งค่า PATH
```bash
cd ~
git clone https://github.com/rjust/defects4j.git
cd defects4j
./init.sh

# ตั้งค่า PATH และ Java ใน ~/.bashrc
echo 'export PATH="$HOME/defects4j/framework/bin:$PATH"' >> ~/.bashrc
echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc

# ทดสอบว่าคำสั่ง defects4j พร้อมใช้งาน
defects4j info -p Lang
```

---

## 2. การรัน Defects4J Benchmark แบบขนาน (Parallel Runner)

สคริปต์หลัก: `experiments/run_d4j_parallel.py`  
ทำหน้าที่: ดึงโค้ด (Checkout), คอมไพล์ (Compile), รันชุดทดสอบ (Test), และวัด Coverage (Line & Condition/Branch) แบบหลายคอร์พร้อมกัน พร้อมบันทึกผลลัพธ์ตาม Common Format อัตโนมัติ

เปิด **PowerShell** บน Windows แล้วสั่งรันได้ทันที:

### 2.1 ทดสอบรันโปรเจกต์ขนาดเล็กก่อน (แนะนำสำหรับตรวจความพร้อม)
* **โปรเจกต์ Csv (16 บั๊ก, ใช้เวลา ~2-3 นาที):**
  ```powershell
  wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -p Csv -j 4
  ```

* **โปรเจกต์ Lang (61 บั๊ก, ใช้เวลา ~10-15 นาที):**
  ```powershell
  wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -p Lang -j 8
  ```

### 2.2 รันครบทุกโปรเจกต์ (854 บั๊ก ทั้งหมด 17 โปรเจกต์)
* **แบบแสดงผลบนหน้าจอ (Real-time):**
  ```powershell
  wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -j 8
  ```
  *(ปรับ `-j 8` เป็นจำนวน Worker CPU ตามต้องการ เช่น 8 หรือ 12)*

* **แบบปล่อยรันเบื้องหลัง (Background / ข้ามคืน ปิดหน้าจอได้):**
  ```powershell
  wsl -d Ubuntu bash -c "nohup python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -j 8 > /tmp/d4j_run.log 2>&1 &"
  ```

### 2.3 คำสั่งจัดการการรันเบื้องหลัง (Monitor & Stop)
* **ดูความคืบหน้าแบบ Real-time:**
  ```powershell
  wsl -d Ubuntu tail -f /tmp/d4j_run.log
  ```
  *(กด `Ctrl + C` เพื่อออกจากหน้าจอดู log โดยระบบยังรันต่อ)*

* **เช็คว่าโปรแกรมยังรันอยู่หรือไม่:**
  ```powershell
  wsl -d Ubuntu bash -c "ps aux | grep run_d4j_parallel"
  ```

* **สั่งหยุดการรันทั้งหมดทันที:**
  ```powershell
  wsl -d Ubuntu bash -c "pkill -f run_d4j_parallel.py"
  ```
  *(ระบบรองรับ Graceful Shutdown และ Auto-Resume เมื่อสั่งรันใหม่จะข้ามบั๊กที่ทำเสร็จแล้วต่อได้ทันที)*

---

## 3. การรันการทดลองสร้าง Test ด้วย ACO (Ant Colony Optimization)

โฟลเดอร์หลัก: `Ant Colony Optimization(ACO)/`  
สคริปต์ควบคุม: `experiments/scripts/run_aco_experiments.sh`  
ทำหน้าที่: สังเคราะห์ชุดทดสอบ JUnit อัตโนมัติด้วยอัลกอริทึม ACO ทั้งหมด 5 รอบ (Seeds: 1, 2, 3, 4, 5) บนบั๊กเป้าหมาย (Lang-1) แล้ววัดค่า Coverage และ Fault Detection

เปิด **PowerShell** แล้วรันคำสั่งตามลำดับ:

### 3.1 Checkout บั๊กเป้าหมายของ Defects4J (Lang-1 Buggy)
```powershell
wsl -d Ubuntu bash -c "mkdir -p ~/d4j-work && defects4j checkout -p Lang -v 1b -w ~/d4j-work/Lang_1_buggy"
```

### 3.2 สั่งรันการทดลอง ACO 5 รอบ (ครบกระบวนการในคำสั่งเดียว)
```powershell
wsl -d Ubuntu bash /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/scripts/run_aco_experiments.sh "/mnt/c/Users/PXn/Pictures/SQA/SQA_Project/Ant Colony Optimization(ACO)" ~/d4j-work/Lang_1_buggy 1 2 3 4 5
```

**สิ่งที่สคริปต์ทำให้อัตโนมัติ:**
1. คอมไพล์ซอร์สโค้ด Java ของ ACO
2. สร้างชุดทดสอบ `ACOTest.java` ตาม Seed แต่ละรอบ (1 ถึง 5)
3. คัดลอกเข้าสู่โปรเจกต์ Defects4J แล้วสั่ง `defects4j compile`, `test`, `coverage`
4. วัดผลและแปลงเข้าสู่รูปแบบ SQA Common Output Format อัตโนมัติ

---

## 4. ตำแหน่งและการเปิดดูผลลัพธ์ (Results)

ผลลัพธ์ทั้งหมดจะถูกสร้างขึ้นในโฟลเดอร์ `Results/defects4j-round2/` และ `Tests/defects4j-round2/`:

| รายการ | ตำแหน่งไฟล์ | คำอธิบาย |
| :--- | :--- | :--- |
| **ผลระดับ Bug (Defects4J)** | `Results/defects4j-round2/Defects4J/<Project>/<Bug>/run1/result.json` | ผลการรัน, Line Coverage, Condition Coverage, Fault Detection |
| **ผลระดับ Bug (ACO)** | `Results/defects4j-round2/ACO/Lang/Lang-1/run<1-5>/result.json` | ผลของชุดทดสอบที่ ACO สร้างในแต่ละ seed |
| **ชุดเทสต์ที่ ACO สร้าง** | `Tests/defects4j-round2/ACO/Lang/Lang-1/run<1-5>/ACOTest.java` | โค้ด JUnit Test Case ที่สร้างขึ้น |
| **ตารางสรุปรายโปรเจกต์** | `Results/defects4j-round2/summary_Defects4J_by_project.csv` | สรุปค่าเฉลี่ย Coverage และ Fault Detection Rate แต่ละโปรเจกต์ |
| **ภาพรวมเปรียบเทียบทุกวิธี** | `Results/defects4j-round2/summary_overall.json` | สรุปเปรียบเทียบภาพรวมระหว่าง Defects4J Benchmark และ ACO |
| **ตารางผลสดทั้งหมด** | `experiments/results_all_defects4j_with_coverage.csv` | ข้อมูลดิบทุกบั๊กที่รันผ่าน Parallel Runner |

### วิธีดูสรุปผลลัพธ์อย่างง่าย
เปิดดูสรุปภาพรวมใน PowerShell:
```powershell
Get-Content Results\defects4j-round2\summary_overall.json
```
หรือเปิดดูตารางสรุปด้วย Excel / VS Code:
- `Results/defects4j-round2/summary_Defects4J_by_project.csv`
- `Results/defects4j-round2/summary_by_project.csv`

---

## 5. Cheat Sheet รวมคำสั่งด่วน

```powershell
# 1. รัน Defects4J โปรเจกต์ทดสอบ (Csv)
wsl -d Ubuntu python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -p Csv -j 4

# 2. รัน Defects4J ทั้งหมด 854 บั๊ก (รัน Background)
wsl -d Ubuntu bash -c "nohup python3 /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/run_d4j_parallel.py -j 8 > /tmp/d4j_run.log 2>&1 &"

# 3. ดูสถานะความคืบหน้า
wsl -d Ubuntu tail -f /tmp/d4j_run.log

# 4. หยุดการรันทั้งหมด
wsl -d Ubuntu bash -c "pkill -f run_d4j_parallel.py"

# 5. รันการทดลอง ACO (Lang-1 จำนวน 5 seeds)
wsl -d Ubuntu bash /mnt/c/Users/PXn/Pictures/SQA/SQA_Project/experiments/scripts/run_aco_experiments.sh "/mnt/c/Users/PXn/Pictures/SQA/SQA_Project/Ant Colony Optimization(ACO)" ~/d4j-work/Lang_1_buggy 1 2 3 4 5
```
