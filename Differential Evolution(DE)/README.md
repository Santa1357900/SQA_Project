# Differential Evolution สำหรับ Defects4J

โปรเจกต์นี้ใช้ **Differential Evolution แบบ DE/rand/1/bin** เพื่อค้นหา input และสร้าง test สำหรับโปรเจกต์ Java ใน Defects4J จากนั้นตรวจ test กับเวอร์ชัน buggy/fixed และวัด test coverage, code coverage และ fault detection

## โครงสร้างโฟลเดอร์

```text
Code/             source code และ runner
Configuration/    ค่าเริ่มต้น, profile และไลบรารีที่ต้องใช้
Results/          ผลการทดลอง, result.json, summaries และ metadata
Test/             test suites ที่สร้างได้
COMMON_OUTPUT_FORMAT.md  นิยาม schema และตัวชี้วัดกลาง
common-result.example.json  ตัวอย่าง result.json
run.sh            คำสั่งหลัก
run-full.sh       สคริปต์รันเต็ม
THIRD_PARTY.md    รายการไลบรารีภายนอกและ license
```

`work/` จะถูกสร้างตอนรัน ใช้เก็บ Defects4J checkouts และไฟล์ชั่วคราว ไม่ใช่ผลลัพธ์สำหรับรายงาน และไม่จำเป็นต้องนำขึ้น GitHub

## สิ่งที่ต้องติดตั้งก่อน

รันบน Ubuntu หรือ WSL เพราะ pipeline ใช้ Defects4J, Java, Perl และ Ant; ไม่รองรับการรัน pipeline โดยตรงบน Windows. ติดตั้ง Defects4J ตามคู่มือของมันและตรวจว่าคำสั่งใช้งานได้ จากนั้นกำหนดตำแหน่งถ้าไม่ได้ติดตั้งไว้ที่ `~/defects4j`:

```bash
export DEFECTS4J_HOME="$HOME/defects4j"
export PATH="$DEFECTS4J_HOME/framework/bin:$PATH"
java -version
perl -v
python3 --version
defects4j info -p Chart
```

เปิด terminal ที่โฟลเดอร์โปรเจกต์ก่อนใช้ `run.sh` ตัว runner จะตั้ง timezone และเพิ่ม Defects4J เข้า `PATH` ให้อัตโนมัติ

## ทดลองกับ bug เดียวก่อน

ตัวอย่างรัน Chart bug 1 ด้วย worker เดียว เพื่อเช็ก environment ก่อนเริ่มการทดลองใหญ่:

```bash
bash run.sh run --project Chart --bug 1 --workers 1 \
  --results pilot-chart-1 --work "$HOME/de-work/pilot-chart-1"
```

ดูผล:

```bash
bash run.sh status --results pilot-chart-1
```

ใช้ชื่อ `--results` ใหม่ทุกครั้งที่เปลี่ยน algorithm code หรือค่าค้นหาที่มีผลต่อการทดลอง เพื่อป้องกันการรวมผลจากคนละ configuration เข้าด้วยกัน

## ทำซ้ำการทดลองเต็ม

ค่าหลักที่บันทึกใน `Configuration/defaults.json` ได้แก่ runs 1, workers 4, population 16, budget 512, mutation 0.7, crossover 0.9, input range 1000, seed 2026 และ heap 512m. คำสั่งด้านล่างระบุการตั้งค่าเต็มอย่างชัดเจนเพื่อให้ตรวจสอบและทำซ้ำได้:

```bash
EXP=all-defects4j-v3120-full512-w4-r1
WORK="$HOME/de-work/$EXP"

bash run.sh run --workers 4 --runs 1 \
  --population 16 --budget 512 \
  --mutation 0.7 --crossover 0.9 --input-range 1000 \
  --max-methods 0 --max-tests-per-method 16 \
  --method-timeout 300 --job-timeout 1500 --validation-reserve 180 \
  --test-timeout 60 --command-timeout 600 --candidate-timeout 12 \
  --seed 2026 --heap 512m \
  --results "$EXP" --work "$WORK"
```

เมื่อไม่ใส่ `--project` หรือ `--bug` runner จะค้นหา active bugs ทั้งหมดจาก Defects4J ที่ติดตั้งอยู่ ณ เครื่องนั้น จำนวน bug ที่พบจะแสดงตอนเริ่มงาน โปรดเก็บเวอร์ชัน Defects4J, Java, คำสั่งจริง และไฟล์ `configuration.json`, `environment.json`, `manifest.json` ไปกับผลรัน เพราะสิ่งเหล่านี้มีผลต่อการทำซ้ำ

## ตรวจสถานะและรันต่อ

```bash
bash run.sh status --results "$EXP"
bash run.sh summarize --results "$EXP"
```

ถ้างานถูกขัดจังหวะ ให้ใช้ `resume` กับ work directory เดิม:

```bash
bash run.sh resume --results "$EXP" --work "$WORK" --workers 4
```

`resume` กู้ขั้นตอน validation/coverage ได้เมื่อมี test suite ที่บันทึกไว้เพียงพอ แต่ไม่เริ่ม DE search ต่อจากประชากรที่ค้างไว้ หากต้องการให้รันงานที่จบหรือไม่มี test ซ้ำ ให้ใช้ตัวเลือก retry ที่ runner รองรับและควรแยกชื่อ experiment ใหม่ ตรวจ `search_incomplete` ก่อนนำผลไปเปรียบเทียบ

## ไฟล์ผลลัพธ์

การรันใหม่จะบันทึกผลแยกตาม experiment และ bug:

```text
Results/<experiment>/
  <Project>/<Project>-<bug>/run<run>/result.json
  summary.csv
  summary_by_project.csv
  summary_overall.json
  configuration.json
  environment.json
  manifest.json
  logs/ และไฟล์รายงาน

Test/<experiment>/<Project>/<Project> <bug>/run<run>/<attempt>/
  DEGeneratedTest.java, cases.json และ test suite ที่สร้าง

<work>/<experiment>/<Project>/<bug>/<run>/
  Defects4J checkouts และไฟล์ชั่วคราว
```

ผล `result.json` ใช้ schema กลาง อธิบาย field ใน [COMMON_OUTPUT_FORMAT.md](COMMON_OUTPUT_FORMAT.md). `Results/summary.csv` มีรายละเอียดระดับ bug; `summary_by_project.csv` และ `summary_overall.json` รวมผลในระดับ project และการทดลอง

สถานะที่พบบ่อย:

- `complete`: ตรวจ test กับ buggy/fixed revisions แล้ว และประเมินผลได้ ไม่ได้หมายความว่าตรวจพบบั๊ก
- `no_generated_tests`: สร้าง test suite ไม่ได้ จึงไม่มีผล coverage หรือ fault detection ที่ประเมินได้
- `no_valid_tests`: สร้าง candidate ได้ แต่ไม่มี test ที่ผ่านเงื่อนไข validation
- `job_timeout`, `error`, `coverage_error`: งานหมดเวลาหรือติดปัญหาในขั้นตอนที่ระบุ ควรรายงานตามสถานะจริง ไม่แทนค่าเป็นศูนย์

## วิธีอ่านตัวชี้วัด

- **Test coverage**: สัดส่วนเมธอดใน modified classes ที่ test ซึ่งผ่าน validation เรียกถึง
- **Code coverage ratio**: `line_ratio = lines_covered / lines_total`; `branch_ratio` เป็น coverage เงื่อนไข/branch ตามเครื่องมือและ field `branch_measure`
- **Fault detection**: `fault_detected=true` เมื่อ test เปิดเผยความต่างระหว่าง buggy กับ fixed revision ตามเกณฑ์ของ harness
- **Fault detection rate (FDR)**: จำนวน bug ที่ตรวจพบหารด้วยจำนวน bug ที่ประเมินผลได้ ต้องรายงานจำนวนที่ประเมินไม่ได้ควบคู่กัน

ค่า `null` หมายถึงไม่มีข้อมูลหรือคำนวณไม่ได้ ไม่ใช่ 0. หากรวม coverage จาก Cobertura และ JaCoCo เข้าด้วยกัน ค่าเฉลี่ยรวมจะไม่คำนวณ เพราะเครื่องมือและนิยาม coverage ต่างกัน

## ผลชุดข้อมูลที่แนบมา

โฟลเดอร์ `Results/` ปัจจุบันเป็น snapshot ของ experiment `all-defects4j-v3110-full512-w4` มีผล 854 bugs จาก 17 projects: 701 รายการประเมินเสร็จ, 153 รายการ `no_generated_tests`, ตรวจพบบั๊ก 95 จาก 701 รายการที่ประเมินได้ (FDR 13.55%). ไม่มีผลที่จัดเป็นกลุ่ม `fail`; `no_generated_tests` จัดเป็น `no run` และไม่ใช่ pass

snapshot นี้รวมผลจากหลาย algorithm versions ตามการซ่อมและ rerun แต่ละ bug (`3.1.10`, `3.1.11`, `3.1.17`, `3.1.19`); ตรวจเวอร์ชันราย bug ได้จาก `algorithm_version` ใน `result.json`. ค่า coverage ใช้ Cobertura 698 รายการและ JaCoCo 3 รายการ จึงไม่แสดงค่าเฉลี่ย coverage รวมใน `summary_overall.json`. ชุดผลนี้เป็นข้อมูลที่บันทึกไว้ ไม่ใช่ผลที่เพิ่งสร้างจาก source package 3.1.20

ดูตารางสรุปที่ `Results/summary_overall.json`, `Results/summary_by_project.csv` และ `Results/summary.csv`. test suites ที่แนบอยู่ใต้ `Test/`; paths ใน `artifacts.tests` ของผล `complete` ชี้ไปยังชุดที่มีอยู่จริง ส่วน logs และ legacy raw results ไม่ได้แนบและถูกระบุเป็น `null`
