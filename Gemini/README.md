# SQA Project: Gemini Test Generation

โปรเจกต์นี้ทดลองใช้โมเดล Gemini ผ่าน KKU IntelSphere API เพื่อสร้าง JUnit unit tests ให้กับคลาสที่มีบั๊กใน Defects4J แล้วตรวจ test กับเวอร์ชัน buggy/fixed และบันทึกผลในรูปแบบกลาง

## โครงสร้างโฟลเดอร์

```text
Code/       สคริปต์ดึง dataset, สร้าง prompt/test, ตรวจผล และสรุปผล
 dataset/   source Java ของคลาสเป้าหมาย แยกตาม Project_BugID
Prompt/     template_prompt.txt และประวัติ prompt ใน history/
test/       JUnit tests ที่สร้างแล้ว แยกตาม Project_BugID_buggy
Results/    result.json ราย bug และไฟล์สรุปผล
.env.example ตัวอย่างตัวแปรลับสำหรับ API key
.gitignore  กัน key, cache, environment และไฟล์ชั่วคราวไม่ให้เข้า Git
```

หน้าที่ของสคริปต์ใน `Code/`:

- `extract_dataset.py` ดึง source ของคลาสเป้าหมายจาก Defects4J มาไว้ใน `dataset/`
- `pipeline_gemini.py` ประกอบ prompt จาก source และ template แล้วเรียก API เพื่อสร้างไฟล์ Java test
- `generate_prompt.py` สร้าง prompt สำหรับคลาสที่ระบุ
- `validate.py` รัน generated tests กับ buggy และ fixed revisions, วัด coverage และเขียนผลตาม schema กลาง
- `run_all_and_validate.py` ตั้งใจใช้ควบคุมการสร้างและ validation หลายบั๊ก
- `summarize_results.py` แสดงตารางจาก `Results/results.csv`
- `record_result.py` เพิ่มแถวผลแบบ CSV สำหรับงานทดลองที่บันทึกผ่านสคริปต์นี้

## ข้อกำหนด

ต้องมี Python 3, Defects4J, Java และ Perl ที่ Defects4J ใช้งานได้ การตรวจผลต้องทำใน Linux/WSL ที่เรียก `defects4j` ได้จาก `PATH` ติดตั้ง Python dependencies:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install requests python-dotenv
```

ตรวจการติดตั้งก่อน:

```bash
python3 --version
java -version
perl -v
defects4j info -p Chart
```

## ตั้งค่า API key

สร้าง `.env` จากตัวอย่างและใส่ key ที่ได้รับอนุญาตให้ใช้ อย่า commit `.env` หรือส่ง API key ให้ผู้อื่น:

```bash
cp .env.example .env
```

ใน `.env` ให้ตั้ง:

```env
GEMINI_API_KEY=ใส่คีย์ของคุณ
```

แม้ชื่อ key จะเป็น `GEMINI_API_KEY` แต่ `pipeline_gemini.py` ส่งคำขอไปยัง KKU IntelSphere endpoint `https://gen.ai.kku.ac.th/api/v1/chat/completions` ด้วย model เริ่มต้น `gemini-3.5-flash-lite` ตรวจสอบสิทธิ์และ endpoint กับผู้ดูแลระบบก่อนใช้จริง

## ขั้นตอนทดลองและทำซ้ำ

### 1. ตรวจ input

ข้อมูล source ที่เตรียมไว้แล้วอยู่ใน `dataset/<Project>_<BugID>/`. ตรวจว่ามีไฟล์ `.java` ของ target class ก่อนเริ่ม เช่น `dataset/Chart_1/`. ถ้าจะสร้าง dataset ใหม่ ให้ตรวจ Defects4J และ target list ก่อนเรียก `Code/extract_dataset.py`.

### 2. สร้าง test สำหรับ pilot

ตัวอย่าง CLI ของ generator คือ:

```bash
python Code/pipeline_gemini.py \
  --projects Chart --bugs 1 \
  --model gemini-3.5-flash-lite \
  --output-dir test
```

การเรียก API อาจมีค่าใช้จ่าย, rate limits หรือข้อจำกัดจากผู้ให้บริการ ให้ลองทีละ bug ก่อน การรันซ้ำอาจสร้างไฟล์ทับใน output directory เดิม จึงควรสำรองผลเดิมหรือใช้โฟลเดอร์ output แยก

### 3. ตรวจ test บน Defects4J

เมื่อ test อยู่ใน `test/Chart_1_buggy/` ให้รัน validator จาก Linux/WSL ซึ่งมี Defects4J ใน `PATH`:

```bash
python Code/validate.py \
  --test-dir test --targets Chart_1 --workers 1 \
  --experiment gemini-pilot-chart-1 --algorithm Gemini --run 1
```

สำหรับหลาย bug เพิ่ม target IDs หรือใช้ `--projects`/`--limit`. ค่าเริ่มต้นจะข้าม target ที่มี `result.json` อยู่แล้ว; ใช้ `--overwrite` เมื่อต้องการทดสอบซ้ำโดยตั้งใจ ผลใหม่จะเขียนใต้ `Results/<experiment>/<algorithm>/<Project>/<Project>-<bug>/run<run>/`.

### 4. สร้าง summary ใหม่จากผลที่มี

```bash
python Code/validate.py --collect-only \
  --experiment defects4j --algorithm Gemini
```

อ่านผลรายบั๊กจาก `Results/defects4j/Gemini/` และสรุปที่ `Results/defects4j/summary_overall.json` กับ `summary_by_project.csv`. `Results/results.csv` เป็นตาราง CSV แยกอีกชุดหนึ่ง; อย่านำสองชุดมารวมโดยไม่ตรวจ schema และ run ซ้ำก่อน

## ข้อจำกัดของ path ใน source ปัจจุบัน

ก่อนรันใหม่ ให้ตรวจ path คงที่ในสคริปต์ให้ตรงกับโฟลเดอร์จริงของ repo นี้: ปัจจุบัน generator อ้าง template ที่ `ai/Gemini/prompts/template_prompt.txt` แต่ไฟล์จริงอยู่ที่ `Prompt/template_prompt.txt`; `run_all_and_validate.py` อ้างโฟลเดอร์ `scripts/` และ `ai/Gemini/generated-tests/` แต่ของจริงอยู่ใน `Code/` และ `test/`; validator ไม่เลือก `test/` เป็น default โดยอัตโนมัติ ต้องระบุ `--test-dir test`. ตัว orchestrator ยังส่ง option `--all` ให้ generator ทั้งที่ CLI ปัจจุบันไม่มี option นี้

ดังนั้นตัวอย่าง pilot ด้านบนแสดงรูปแบบการเรียก CLI แต่ full pipeline ผ่าน `run_all_and_validate.py` ยังไม่พร้อมทำซ้ำกับโครงสร้างปัจจุบันจนกว่าจะปรับ references เหล่านี้ให้ตรงกัน หรือเลือก input/output path ที่ CLI รองรับ ตรวจ `--help` และทดสอบหนึ่ง bug ให้ครบตั้งแต่ generate ถึง validate ก่อนรันชุดใหญ่

## ผลการทดลอง (run 1, Defects4J 854 บั๊ก)

ตัวเลขมาจาก `Results/defects4j/summary_overall.json` และ `summary_by_project.csv`.

| ตัวชี้วัด                                |                       ค่า |
| ---------------------------------------- | ------------------------: |
| บั๊กที่เลือก                             |                       854 |
| ประเมินผลได้                             |                       455 |
| ตรวจพบบั๊ก                               |                        10 |
| ผลที่ยังไม่ทราบ (inconclusive)           |                       290 |
| ยังไม่มีผลประเมิน (selected - evaluated) |                       399 |
| Fault detection rate                     |                     2.20% |
| Test coverage เฉลี่ย                     | — (ไม่มีข้อมูลใน summary) |
| Line coverage เฉลี่ย                     |                    68.87% |
| Conditions coverage เฉลี่ย                   |                    59.45% |

### ผลแยกตามโปรเจกต์

FDR ใช้ค่าจาก summary (`detected / evaluated`). `Unknown outcome` หมายถึงผล inconclusive ตามสถานะของ Gemini; `ยังไม่มีผลประเมิน` คือ selected ลบ evaluated. ค่า test coverage เป็น `—` เพราะชุดผลนี้ไม่มีข้อมูล method coverage. Line และ Conditions coverage เป็นอัตราส่วนจาก summary.

| Project         | Selected | Evaluated | Detected | Unknown | Fault detection rate | Test coverage | Line coverage | Conditions coverage |
| --------------- | -------: | --------: | -------: | ------: | -------------------: | ------------: | ------------: | --------------: |
| Chart           |       26 |        18 |        2 |       9 |               11.11% |             — |        91.07% |          79.03% |
| Cli             |       39 |        28 |        1 |      18 |                3.57% |             — |        68.77% |          61.12% |
| Closure         |      174 |        56 |        0 |      42 |                0.00% |             — |        27.84% |          18.69% |
| Codec           |       18 |        13 |        0 |       7 |                0.00% |             — |        77.61% |          68.11% |
| Collections     |       28 |        15 |        1 |       8 |                6.67% |             — |        89.83% |          84.63% |
| Compress        |       47 |        38 |        0 |      28 |                0.00% |             — |        61.53% |          55.28% |
| Csv             |       16 |        11 |        0 |       4 |                0.00% |             — |        94.08% |          82.93% |
| Gson            |       18 |         8 |        0 |       5 |                0.00% |             — |        62.70% |          60.94% |
| JacksonCore     |       26 |        13 |        0 |      10 |                0.00% |             — |        53.68% |          42.50% |
| JacksonDatabind |      110 |        41 |        0 |      22 |                0.00% |             — |        54.45% |          43.19% |
| JacksonXml      |        6 |         2 |        0 |       2 |                0.00% |             — |         3.97% |           1.01% |
| Jsoup           |       93 |        59 |        1 |      42 |                1.69% |             — |        78.17% |          66.29% |
| JxPath          |       22 |         5 |        0 |       4 |                0.00% |             — |        36.04% |          26.65% |
| Lang            |       61 |        43 |        0 |      31 |                0.00% |             — |        89.16% |          80.62% |
| Math            |      106 |        76 |        4 |      42 |                5.26% |             — |        83.18% |          74.86% |
| Mockito         |       38 |        23 |        1 |      11 |                4.35% |             — |        73.64% |          61.19% |
| Time            |       26 |         6 |        0 |       5 |                0.00% |             — |        70.26% |          53.49% |

หมายเหตุ: ผลจากโมเดล/API อาจเปลี่ยนตาม model version, prompt, source, API settings และเวลาที่รัน.

## วิธีอ่านไฟล์ผล

```text
Results/defects4j/Gemini/<Project>/<Project>-<BugID>/run1/result.json
Results/defects4j/summary_overall.json
Results/defects4j/summary_by_project.csv
test/<Project>_<BugID>_buggy/<GeneratedTest>.java
Prompt/history/<Project>_<BugID>.txt
```

- `status=complete` หมายถึงมีผลการ validation บันทึกไว้; ดู `status_detail` เพิ่ม เช่น `revealing`, `not_revealing`, `inconclusive`
- `fault_detected=true` หมายถึง test เปิดเผยความต่าง buggy/fixed; `false` หมายถึงประเมินแล้วไม่พบ; `null` หมายถึงยังสรุปไม่ได้
- `validation_failed` หรือ `not_available` หมายถึงไม่มีผลที่ใช้ตัดสิน fault detection ได้ อย่านับเป็น false
- coverage `null` หมายถึงวัดไม่ได้หรือไม่มีข้อมูล ไม่ใช่ coverage 0%

## ก่อน commit หรือเผยแพร่

```bash
git status --short
git diff --check
```

ตรวจว่า `.env` ไม่ได้ถูก stage, ไม่มี API key ในไฟล์หรือ logs และผลลัพธ์ที่จะเผยแพร่ได้รับอนุญาตแล้ว `dataset/`, `test/` และ `Results/` อาจมีข้อมูลและ artifacts จำนวนมาก ตรวจขนาดและสิทธิ์การเผยแพร่ก่อน push.
