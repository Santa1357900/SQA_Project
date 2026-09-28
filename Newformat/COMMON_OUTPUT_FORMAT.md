# รูปแบบผลลัพธ์กลางสำหรับทุกอัลกอริทึม

เวอร์ชันข้อกำหนด: `1.0`

ใช้รูปแบบนี้กับ DE, อัลกอริทึมสร้างเทสต์ตัวอื่น และเครื่องมือ AI ทุกตัว ก่อนนำผลมาเปรียบเทียบ ให้ทุกทีมใช้ชุด bug, จำนวนรอบ, Defects4J รุ่นเดียวกัน และตัวประเมิน coverage เดียวกัน ตัวสร้างเทสต์แต่ละตัวเก็บ log และข้อมูลเฉพาะของตนเพิ่มได้ แต่ต้องส่งค่ากลางตามเอกสารนี้

## ไฟล์ที่ต้องส่ง

```text
Results/<experiment>/<algorithm>/<project>/<project>-<bug>/run<run>/result.json
Results/<experiment>/summary_by_project.csv
Results/<experiment>/summary_overall.json
Tests/<experiment>/<algorithm>/<project>/<project>-<bug>/run<run>/...
```

`result.json` หนึ่งไฟล์แทนหนึ่ง bug หนึ่งรอบรัน และต้องเขียนแม้รันไม่สำเร็จ หากยังไม่มีไฟล์ให้ถือว่าเป็นงานที่ไม่มีผล ไม่ใช่ผลลัพธ์ศูนย์

## สถานะกลาง

| `status` | ความหมาย |
| --- | --- |
| `running` | กำลังทำงาน ใช้ชั่วคราว |
| `complete` | สร้าง ตรวจ และวัดผลเสร็จครบ อาจตรวจพบบั๊กหรือไม่ก็ได้ |
| `no_generated_tests` | ไม่มีเทสต์ที่สร้างได้ |
| `no_valid_tests` | สร้างเทสต์ได้ แต่ไม่มีเทสต์ที่ผ่านบนเวอร์ชัน fixed |
| `validation_failed` | ขั้นตรวจสอบเทสต์ทำงานไม่สำเร็จหรือให้ผลที่ยังยืนยันไม่ได้ |
| `timeout` | เกินเวลาที่กำหนด |
| `error` | เกิดข้อผิดพลาดอื่น เช่น metadata, build หรือเครื่องมือประเมิน |
| `interrupted` | งานถูกยกเลิกหรือหยุดกลางทาง |
| `unsupported` | ตัวสร้างเทสต์ไม่รองรับเป้าหมายนี้ และบันทึกเหตุผลไว้ |

บันทึกสถานะละเอียดเดิมของแต่ละเครื่องมือใน `status_detail` เช่น `job_timeout` หรือ `fixed_validation_failed` เพื่อวิเคราะห์สาเหตุ โดยไม่เพิ่มความหมายใหม่ให้ `status` กลาง

## ตัวชี้วัด

### 1. Test coverage

ใช้ **target method coverage** ของชุดเทสต์ที่ผ่านการตรวจบน fixed แล้ว: `covered_methods / total_methods` โดยนับจากการรันเทสต์จริงบนเวอร์ชัน buggy ภายในคลาสที่ Defects4J ระบุว่าแก้ไข นับเมธอดที่มีโค้ดให้รันและ constructor ที่ประกาศในคลาสนั้น ไม่นับ abstract, synthetic และ static initializer ใช้เครื่องมือวัดเดียวกันทุกวิธี

จำนวนเทสต์ไม่ใช่ coverage: บันทึก `tests_generated` และ `tests_validated` แยกไว้

### 2. Code coverage ratio

ค่าหลักสำหรับเปรียบเทียบคือ `line_ratio = lines_covered / lines_total` บนเวอร์ชัน buggy และขอบเขตคลาสที่แก้ไขเหมือนกับ Test coverage บันทึก `branch_ratio = branches_covered / branches_total` เป็นค่าเสริม พร้อมระบุชนิดของ branch/condition ที่เครื่องมือรายงาน หากใช้ `ConditionsCovered` ของ Defects4J/Cobertura ให้ตั้ง `branch_measure = "condition"` และเรียกว่า condition coverage ในรายงาน

### 3. Fault detection rate

ระดับ bug ให้ `fault_detected = true` เมื่อมีอย่างน้อยหนึ่งเทสต์ที่ผ่านบน fixed แต่ล้มเหลวบน buggy โดยต้องเป็นความล้มเหลวของเทสต์ ไม่ใช่การตั้งค่า/build/harness ล้มเหลว `false` หมายถึงประเมินครบแล้วแต่ไม่พบความต่าง `null` หมายถึงยังประเมินไม่ได้

คำนวณในไฟล์สรุป: `fault_detection_rate = detected_unique_bugs / evaluated_unique_bugs` โดย `evaluated_unique_bugs` คือ bug ที่มีอย่างน้อยหนึ่งรอบ `complete` และ `detected_unique_bugs` คือ bug เหล่านั้นที่มีอย่างน้อยหนึ่งรอบตรวจพบ รายงาน `selected_unique_bugs` และ `unknown_outcome_bugs` ควบคู่กันเสมอ หากหลายวิธีมีจำนวนรอบไม่เท่ากัน ห้ามเปรียบเทียบค่า detected-any-run โดยตรง

## กฎข้อมูล

- `ratio` ทุกตัวอยู่ในช่วง `0.0–1.0` และเก็บทั้งตัวตั้งกับตัวหารเพื่อคำนวณซ้ำได้
- ใช้ `null` เมื่อไม่ได้วัดหรือไม่มีตัวหาร อย่าแทนด้วย `0`; ศูนย์ใช้เมื่อวัดแล้วและครอบคลุมศูนย์จริง
- คำนวณ coverage จากเทสต์ที่ผ่าน fixed เท่านั้น และใช้ตัวประเมินเดียวกันทุกวิธี
- `tests_validated <= tests_generated`; เมื่อ `status = "complete"` ต้องมี `tests_validated > 0`, `fixed_failing_tests = 0`, และ `fault_detected` เป็น boolean
- บันทึกเวอร์ชัน/configuration ของตัวสร้างเทสต์ รวมถึงเวลาที่ใช้ เพื่อตรวจสอบความเทียบกันได้
- ค่าเฉลี่ย coverage ในไฟล์สรุปคำนวณจากรอบที่ `complete` เท่านั้น พร้อมบอกจำนวนรอบที่ใช้คำนวณ

ดูตัวอย่างโครงสร้างข้อมูลใน [common-result.example.json](common-result.example.json) ตัวเลขในตัวอย่างเป็นข้อมูลสมมติ

## คอลัมน์สรุปที่ต้องมี

`summary_by_project.csv` มีหนึ่งแถวต่อ `algorithm, project` และอย่างน้อยคอลัมน์เหล่านี้:

```text
algorithm,project,selected_unique_bugs,evaluated_unique_bugs,detected_unique_bugs,unknown_outcome_bugs,fault_detection_rate,complete_runs,mean_test_coverage_ratio,mean_line_coverage_ratio,mean_branch_coverage_ratio,mean_generation_seconds,mean_total_seconds
```

`summary_overall.json` ใช้ชื่อฟิลด์เดียวกันต่ออัลกอริทึม โดยรวมจำนวน bug ก่อนคำนวณ Fault detection rate ใหม่ ไม่เฉลี่ยเปอร์เซ็นต์จากแต่ละโปรเจกต์โดยตรง
