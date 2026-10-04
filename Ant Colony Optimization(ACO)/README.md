# Ant Colony Optimization (ACO) สำหรับสร้าง Unit Test บน Defects4J

ธนันชัย พันธราช 673380042-8

ใช้ ACO ค้นหา test case ระดับ unit ให้กับคลาสที่ถูกแก้ไขของแต่ละบั๊กใน Defects4J (854 บั๊ก 17 โปรเจกต์)
แล้ววัด test coverage, code coverage และ fault detection ตามรูปแบบผลลัพธ์กลางของกลุ่ม
ตัวสร้างเทสเป็นแบบทั่วไป ไม่มีข้อมูลเฉพาะของบั๊กใดบั๊กหนึ่ง ใช้ได้กับทุกคลาสผ่าน Java reflection

## แนวคิด

เทสหนึ่งตัวคือการเรียกเมธอดหนึ่งครั้ง มดแต่ละตัวสร้างเทสโดยเดินบนกราฟการตัดสินใจ

1. เลือกเมธอดหรือ constructor ของคลาสเป้าหมาย
2. ถ้าเป็น instance method เลือกวิธีสร้าง object ที่จะเรียก (constructor, static factory, subclass ใน package เดียวกัน)
3. เลือกค่าของพารามิเตอร์ทีละตัว ถ้าพารามิเตอร์เป็น object ก็เดินต่อลงไปอีกชั้น (ลึกไม่เกิน `max_object_depth`)

ค่าที่เลือกได้ของแต่ละชนิดมาจากชุดค่าขอบ (0, -1, MAX, MIN, NaN, "", null, อาเรย์ว่าง ฯลฯ)
ค่าคงที่ที่อ่านจาก constant pool ของ class file และ object สำเร็จรูปของ JDK (List, Map, Reader, Locale ฯลฯ)

ทุกเส้นมีฟีโรโมน `tau` มดเลือกเส้น `i` ด้วยความน่าจะเป็น

    P(i) = tau_i^alpha * eta_i^beta / sum_j (tau_j^alpha * eta_j^beta)

`eta` (heuristic)

- เส้นของเมธอด: `1 / (1 + stale/8)` บวก 1 ถ้ายังไม่เคยถูกลอง (`stale` = จำนวนครั้งติดกันที่ไม่ได้ coverage ใหม่) และเป็น 0 เมื่อ timeout ครบ 3 ครั้ง
- เส้นของค่า: 1.5 สำหรับค่าขอบ, 1.0 สำหรับค่าอื่น

หลังมดครบทุกตัวในรอบ

    tau <- max(tau_min, (1 - rho) * tau)                      ระเหย
    tau <- min(tau_max, tau + 1 + 2 * gain / best_gain)        มดที่ไปถึงโค้ดใหม่
    tau <- min(tau_max, tau + 0.3)                             มดที่ได้ผลลัพธ์แบบใหม่ของเมธอดนั้น

`gain` คือจำนวน probe ของ JaCoCo ในคลาสเป้าหมายที่เทสตัวนั้นไปถึงเป็นครั้งแรก
เทสที่ได้ `gain > 0` หรือให้ผลลัพธ์แบบใหม่ (ไม่เกิน `extra_tests_per_method` ต่อเมธอด) จะถูกเก็บเข้า archive

## ขั้นตอนต่อหนึ่งบั๊ก

| ขั้น | ทำอะไร |
| --- | --- |
| 1 | `defects4j checkout` + `compile` ทั้ง buggy และ fixed |
| 2 | `AcoSearch` ค้นหาบน **buggy** โดยใช้ JaCoCo agent เป็น feedback ได้รายการ spec ของเทส |
| 3 | `AcoOracle` รัน spec บน **fixed** สองครั้ง ผลที่ตรงกันทั้งสองครั้งเป็นค่าที่คาดหวัง (regression oracle) |
| 4 | เขียน `acogen/AcoGeneratedTest.java` (JUnit 4) แต่ละเทสคือ `assertEquals(expected, AcoReplay.run(spec))` |
| 5 | `defects4j test` บน fixed แล้วตัดเทสที่ไม่ผ่านออก |
| 6 | `defects4j test` บน buggy: มีเทส fail = ตรวจพบบั๊ก แล้ว `defects4j coverage` วัด line/condition coverage ของ modified classes |
| 7 | เขียน `result.json` |

ขั้น 2–3 เป็นวิธีเดียวกับที่ DE ของกลุ่มใช้ (ค้นหาบน buggy, เก็บค่าที่ถูกต้องจาก fixed) จึงเทียบผลกันได้

## โครงสร้าง

```text
Code/
  AcoSearch.java        ตัวค้นหา ACO
  AcoReplay.java        รัน spec ผ่าน reflection และแปลงผลเป็นข้อความ (ใช้ทั้งตอนค้นหาและในเทส)
  AcoOracle.java        รัน spec บน fixed เพื่อเก็บค่าที่คาดหวัง
  run_aco.py            pipeline ต่อบั๊ก + วัดผล
  summarize_aco.py      รวมผล
  build.sh, setup_lib.sh
Configuration/
  aco_config.properties
  lib/                  JaCoCo 0.8.12 (agent, core)
Tests/defects4j-round2/ACO/<Project>/<Project>-<bug>/run<N>/
  acogen/AcoGeneratedTest.java, acogen/AcoReplay_scaffolding.java, specs.tsv, search_stats.json
Results/defects4j-round2/
  ACO/<Project>/<Project>-<bug>/run<N>/result.json, validated_tests/, logs/
  summary_by_project.csv, summary_overall.json, results.csv
```

## ค่าที่ใช้ (`Configuration/aco_config.properties`)

| ค่า | ที่ใช้ | ความหมาย |
| --- | --- | --- |
| `number_of_ants` | 20 | มดต่อรอบ |
| `iterations` | 150 | จำนวนรอบขั้นต่ำ (เพิ่มตามจำนวนเมธอด: `methods * evaluations_per_method / ants`) |
| `alpha`, `beta` | 1.0, 2.0 | น้ำหนักของฟีโรโมนและ heuristic |
| `evaporation_rate` | 0.25 | rho |
| `initial_pheromone` | 1.0 | tau เริ่มต้น (tau_min 0.05, tau_max 10) |
| `random_seed` | 1 | run ที่ N ใช้ seed + N - 1 |
| `time_budget_seconds` | 30 | เวลาค้นหาสูงสุดต่อบั๊ก |
| `call_timeout_ms` | 1500 | เวลาสูงสุดต่อการเรียกหนึ่งครั้ง |
| `max_tests` | 150 | ขนาด archive |

## วิธีรัน

ต้องรันใน Linux/WSL ที่มี Defects4J 3.x และ JDK 11

```bash
bash Code/setup_lib.sh          # โหลด JaCoCo ครั้งแรก
bash Code/build.sh              # คอมไพล์ลง ~/aco-work/tool

python3 Code/run_aco.py --projects Lang --bugs 1 --workers 1     # ลองบั๊กเดียว
python3 Code/run_aco.py --all --workers 24                        # ครบ 854 บั๊ก
python3 Code/run_aco.py --all --runs 1 2 3 --workers 24           # หลาย seed
python3 Code/summarize_aco.py
```

รันซ้ำจะข้ามบั๊กที่มี `result.json` แล้ว (`--overwrite` เพื่อรันใหม่)
ถ้า `defects4j` ไม่ได้อยู่ที่ `~/defects4j` ให้ตั้ง `DEFECTS4J_BIN`

## ผลการทดลอง

run 1 (seed 1) ครบ 854 บั๊ก รันเมื่อ 4 ต.ค. 2569 ด้วย 24 workers ตัวเลขมาจาก `Results/defects4j-round2/summary_overall.json` และ `summary_by_project.csv`

| ตัวชี้วัด | ค่า |
| --- | --- |
| บั๊กที่ประเมินได้ (`complete`) | 844 / 854 |
| ตรวจพบบั๊ก | 165 (FDR 19.6%) |
| Test coverage (method) เฉลี่ย | 68.6% |
| Line coverage เฉลี่ย | 46.8% |
| Condition coverage เฉลี่ย | 35.1% |
| เทสที่สร้าง | 62,088 (เฉลี่ย 73.6 ต่อบั๊ก) ผ่านบน fixed 99.97% |
| เวลาค้นหา + เก็บ oracle เฉลี่ย | 20.2 วินาทีต่อบั๊ก |
| สร้างเทสไม่ได้ | 7 (คลาสเป้าหมายไม่มีเมธอดที่เรียกได้จากภายนอก 6, ค้นหาล้มเหลว 1) |
| วัด coverage ไม่ได้ | 3 (Jsoup-4/6/9: Cobertura instrument คลาส `Entities` ไม่ได้) |

| Project | Bugs | Evaluated | Detected | FDR | Test cov. | Line cov. | Condition cov. |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Chart | 26 | 26 | 11 | 42.3% | 93.4% | 52.7% | 33.5% |
| Cli | 39 | 39 | 17 | 43.6% | 88.4% | 65.4% | 51.1% |
| Closure | 174 | 174 | 11 | 6.3% | 38.8% | 21.7% | 12.3% |
| Codec | 18 | 18 | 9 | 50.0% | 98.1% | 86.2% | 80.2% |
| Collections | 28 | 28 | 5 | 17.9% | 78.1% | 66.4% | 65.9% |
| Compress | 47 | 47 | 9 | 19.1% | 75.5% | 50.7% | 39.8% |
| Csv | 16 | 16 | 2 | 12.5% | 62.4% | 48.2% | 34.1% |
| Gson | 18 | 17 | 2 | 11.8% | 62.2% | 32.7% | 21.0% |
| JacksonCore | 26 | 25 | 5 | 20.0% | 50.9% | 31.6% | 22.9% |
| JacksonDatabind | 110 | 109 | 9 | 8.3% | 66.5% | 36.1% | 22.9% |
| JacksonXml | 6 | 6 | 1 | 16.7% | 29.6% | 10.3% | 4.7% |
| Jsoup | 93 | 90 | 21 | 23.3% | 77.3% | 55.2% | 41.3% |
| JxPath | 22 | 22 | 4 | 18.2% | 76.5% | 39.5% | 22.3% |
| Lang | 61 | 61 | 18 | 29.5% | 91.0% | 74.0% | 65.5% |
| Math | 106 | 103 | 27 | 26.2% | 79.0% | 58.8% | 46.8% |
| Mockito | 38 | 38 | 7 | 18.4% | 75.4% | 53.8% | 33.9% |
| Time | 26 | 25 | 7 | 28.0% | 71.6% | 57.8% | 44.9% |

ตัวอย่างบั๊กที่ตรวจพบ: Math-5 `Complex.reciprocal()` ของศูนย์ (fixed ได้ `Infinity`, buggy ได้ `NaN`),
Time-5 `Period.normalizedStandard` (fixed โยน `ArithmeticException`, buggy คืนค่าผิด)

Closure มี FDR ต่ำสุดเพราะเมธอดส่วนใหญ่ต้องการ AST (`Node`) ที่สร้างจากค่าพื้นฐานได้ยาก coverage จึงต่ำตามไปด้วย

## ข้อจำกัด

- เทสหนึ่งตัวเรียกเมธอดเดียว จึงเข้าไม่ถึงพฤติกรรมที่ต้องเรียกหลายเมธอดต่อกันบน object เดียว
- object ที่สร้างยาก (ต้องใช้ input ซับซ้อน เช่น AST ของ Closure หรือ JSON ที่มีโครงสร้าง) ทำให้ coverage ต่ำในโปรเจกต์นั้น
- oracle มาจากพฤติกรรมของ fixed จึงตรวจได้เฉพาะบั๊กที่ทำให้ค่าคืนกลับ สถานะของ object หรือ exception ต่างออกไป
- ค่าที่ขึ้นกับเวลา, hash code หรือสภาพแวดล้อม ถูกตัดออกตอนเทียบผลสองครั้งบน fixed และตอน validate
