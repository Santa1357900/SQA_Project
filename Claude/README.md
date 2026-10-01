# Claude — AI-Assisted Unit Test Generation บน Defects4J

ส่วนนี้ใช้ **Claude (Anthropic)** สร้าง JUnit 4 test suite ให้คลาสที่มีบั๊กใน Defects4J ทั้ง 854 บั๊ก (17 โปรเจกต์)
แล้ววัด Test coverage, Code coverage และ Fault detection ตามรูปแบบผลลัพธ์กลางของกลุ่ม
([COMMON_OUTPUT_FORMAT.md](https://github.com/Santa1357900/SQA_Project/blob/Thananchai/Ant%20Colony%20Optimization(ACO)/docs/COMMON_OUTPUT_FORMAT.md))
เพื่อนำไปเปรียบเทียบกับ Gemini, Differential Evolution และ Ant Colony Optimization

| รายการ | ค่าที่ใช้ |
| --- | --- |
| โมเดล | `claude-sonnet-5` (adaptive thinking, effort `high`) |
| ช่องทางเรียกใช้ | Claude Code CLI (`claude -p`) ด้วยบัญชีที่ล็อกอินในเครื่อง — สลับเป็น Anthropic API ได้ด้วย `backend: "api"` |
| ระดับการทดสอบ | Unit test, JUnit 4, Java 6 syntax (ตามข้อจำกัดของ Defects4J) |
| ชุดข้อมูล | Defects4J 854 active bugs — ซอร์สโค้ดคลาสเป้าหมายอยู่ใน [`dataset/`](../dataset) |
| Prompt | [`Prompt/system_prompt.txt`](Prompt/system_prompt.txt) + [`Prompt/template_prompt.txt`](Prompt/template_prompt.txt) |
| Configuration | [`Configuration/claude_config.json`](Configuration/claude_config.json), [`Configuration/project_notes.json`](Configuration/project_notes.json) |

---

## 1. โครงสร้างโฟลเดอร์

```text
Claude/
├── Code/
│   ├── pipeline_claude.py      # สร้างเทสด้วย Claude API (รันบน Windows ได้ ไม่ต้องมี Defects4J)
│   ├── validate_claude.py      # รันเทสบน Defects4J (WSL/Linux) -> result.json รูปแบบกลาง
│   ├── summarize_claude.py     # รวมผลเป็น summary_by_project.csv / summary_overall.json
│   ├── validate_loop.sh        # วนวัดผลตามหลัง generator จนครบ (ใช้ใน WSL)
│   ├── setup_wsl_defects4j.sh  # ติดตั้ง JDK 11 + Defects4J 3.0.1 ใน WSL (รันเป็น root ครั้งเดียว)
│   ├── progress.py             # dashboard ความคืบหน้า
│   └── requirements.txt
├── Prompt/
│   ├── system_prompt.txt       # บทบาท + กฎ compile/determinism/oracle/output
│   ├── template_prompt.txt     # โครง user prompt (XML tags) มี placeholder {TARGET_SOURCE_CODE} ฯลฯ
│   └── history/<Project>_<bug>.txt   # prompt ฉบับเต็มที่ส่งจริงของแต่ละบั๊ก
├── Configuration/
│   ├── claude_config.json      # โมเดล, effort, max_tokens, จำนวน worker, ชื่อ experiment
│   └── project_notes.json      # ข้อสังเกตเฉพาะโปรเจกต์ที่แทรกใน prompt (ลด compile error)
├── Tests/defects4j-round2/Claude/<Project>/<Project>-<bug>/run<N>/
│   ├── <Class>ClaudeTest.java  # เทสที่ Claude สร้าง (หลัง sanitize)
│   ├── raw_response.txt        # คำตอบดิบจากโมเดล
│   └── generation.json         # เวลา, token usage, stop_reason, โมเดล
└── Results/defects4j-round2/
    ├── Claude/<Project>/<Project>-<bug>/run<N>/
    │   ├── result.json         # ผลตามรูปแบบกลาง (status, coverage, fault_detected, เวลา)
    │   ├── validated_tests/    # เทสหลังตัดตัวที่ fail บน fixed ออก (ชุดที่ใช้วัดผลจริง)
    │   └── logs/               # fixed_test.log, buggy_test.log, coverage.log, failing_tests, all_tests, summary.csv, coverage.xml
    ├── summary_by_project.csv
    ├── summary_overall.json
    └── results_claude.csv      # ตารางแบน method,project,bug_id,run,tests,compile,code_coverage,branch_coverage,fault_detected,time
```

---

## 2. หลักการทำงาน

### 2.1 การออกแบบ Prompt
Prompt แบ่งเป็น 2 ส่วนและใช้แท็ก XML แยกส่วนคำสั่งออกจากโค้ด ซึ่ง Claude ตอบสนองได้ดี

1. **System prompt** — กำหนดบทบาทและกฎ 4 กลุ่ม
   - `<compile_rules>` กฎ Java 6 / JUnit 4 / ห้ามเดา API / ห้าม mock / `throws Throwable` (Defects4J คอมไพล์ด้วย `-source 1.6`)
   - `<determinism_rules>` ห้าม Random, เวลาปัจจุบัน, ไฟล์, เครือข่าย, ลำดับ HashMap
   - `<oracle_rules>` **หัวใจของ fault detection**: ค่าที่คาดหวังต้องมาจาก Javadoc/สัญญาของเมธอด ไม่ใช่จากการไล่โค้ด (ที่มีบั๊ก) ในหัว มิฉะนั้นเทสจะ "ยืนยันบั๊ก" แทนที่จะจับบั๊ก
   - `<output_rules>` ตอบเป็น Java ล้วน ไม่มี markdown
2. **User prompt** (`template_prompt.txt`) — `<target>` ระบุ package/class/ชื่อคลาสเทส, `<project_notes>` ข้อสังเกตเฉพาะโปรเจกต์, `<source_code>` โค้ดเต็มของคลาส, `<instructions>` ให้วิเคราะห์สาขาและค่าขอบก่อน (ขั้นที่ 1) แล้วจึงเขียนเทส 20–45 ตัว (ขั้นที่ 2)

### 2.2 Pipeline สร้างเทส (`pipeline_claude.py`)
1. อ่านซอร์สโค้ดจาก `dataset/<Project>_<bug>/<Class>.java` แล้วดึงชื่อ package/class
2. ประกอบ prompt และบันทึกฉบับเต็มลง `Prompt/history/`
3. เรียก Claude ผ่าน `claude -p --model claude-sonnet-5 --effort high --output-format json --tools "" --system-prompt-file ...` (ส่ง user prompt ทาง stdin) พร้อม retry/backoff เมื่อเจอ rate limit หรือ server error; backend `api` ใช้ Anthropic SDK แบบ streaming + prompt caching แทน
4. Sanitize: ตัด code fence, แปลง JUnit 5 → 4, ตัด diamond `<>`, เติม `throws Throwable`, แก้ `assertEquals(null,…)`/`NaN`, บังคับชื่อคลาสและ package ให้ตรงกับไฟล์
5. บันทึกเทส + `generation.json` (เวลาที่ใช้, token, stop_reason)

### 2.3 การวัดผล (`validate_claude.py`) — ตาม COMMON_OUTPUT_FORMAT
| ขั้น | คำสั่ง Defects4J | ได้อะไร |
| --- | --- | --- |
| 1. Fixed | `checkout -v <bug>f` → `compile` → `test -s suite.tar.bz2` | รายชื่อเทสที่ fail บน fixed (`fixed_failing_tests`) |
| 2. Filter | ตัด `@Test` method ที่ fail บน fixed ออกจากซอร์ส | ชุดเทสที่ผ่านการตรวจ (`tests_validated`) |
| 3. Buggy | `checkout -v <bug>b` → `compile` → `test -s validated.tar.bz2` | `buggy_failing_tests` → `fault_detected = true` เมื่อ > 0 |
| 4. Coverage | `coverage -s validated.tar.bz2` (บน buggy, เฉพาะ modified classes) | `line_ratio`, `branch_ratio` (condition coverage ของ Cobertura), `test_coverage.ratio` = covered_methods / total_methods จาก `coverage.xml` |

สถานะที่เป็นไปได้: `complete`, `no_generated_tests`, `no_valid_tests` (เช่น compile ไม่ผ่าน), `validation_failed`, `timeout`, `error`, `interrupted` โดยรายละเอียดอยู่ใน `status_detail`

---

## 3. วิธีทำซ้ำ (Reproduce)

### 3.1 เตรียมเครื่อง
```powershell
pip install -r Claude/Code/requirements.txt

# ค่าเริ่มต้น (backend = cli): ต้องมี Claude Code CLI และล็อกอินแล้ว
npm install -g @anthropic-ai/claude-code
claude          # เปิดครั้งแรกแล้ว /login จากนั้นออกได้

# ทางเลือก (backend = api): ใส่ ANTHROPIC_API_KEY ใน .env (ไฟล์นี้ถูก git-ignore) แล้วรันด้วย --backend api
copy .env.example .env
```
สำหรับขั้นวัดผลต้องมี Defects4J บน WSL Ubuntu — รันสคริปต์ติดตั้งครั้งเดียว (JDK 11, Perl deps, Defects4J 3.0.1, แก้ classpath ของ Cli):
```powershell
wsl -d Ubuntu -u root bash /mnt/e/SaiStudyKKU/SQA/project/workspeac/Claude/Code/setup_wsl_defects4j.sh <ชื่อ user ใน WSL>
```

### 3.2 สร้างเทส (Windows PowerShell, ที่ root ของ repo)
```powershell
# ลองก่อน 3 บั๊ก
python Claude/Code/pipeline_claude.py --projects Lang --limit 3

# เจาะจงบั๊ก
python Claude/Code/pipeline_claude.py --projects Lang --bugs 1 5 7

# ครบ 854 บั๊ก (8 worker ขนานตาม config; ข้ามบั๊กที่สร้างสำเร็จแล้ว รันซ้ำต่อจากเดิมได้)
python Claude/Code/pipeline_claude.py --all

# รอบที่ 2 เพื่อหาค่าเฉลี่ย (ข้อ 1.7 ของโจทย์)
python Claude/Code/pipeline_claude.py --all --run 2
```
ตัวเลือกเพิ่มเติม: `--workers N`, `--effort low|medium|high|xhigh|max`, `--model <id>`, `--backend cli|api`, `--overwrite`, `--dry-run`

ตัวเลขอ้างอิงจาก Lang-1: 56 เทส, ~140 วินาที, input ~22k token / output ~17k token ต่อบั๊ก
ดังนั้น 854 บั๊กที่ 8 worker ใช้เวลาราว 4–5 ชั่วโมงถ้าไม่ติด rate limit (สคริปต์รันต่อจากจุดที่ค้างได้)

### 3.3 วัดผลบน Defects4J (รันใน WSL)
```powershell
# ลองก่อน (Lang 61 บั๊ก)
wsl -d Ubuntu python3 /mnt/e/SaiStudyKKU/SQA/project/workspeac/Claude/Code/validate_claude.py --projects Lang

# ทั้งหมด 12 worker ขนาน (ตั้งตามสเปกเครื่อง 16 core / 64 GB) ปล่อยรันเบื้องหลังได้
wsl -d Ubuntu bash -c "nohup python3 /mnt/e/SaiStudyKKU/SQA/project/workspeac/Claude/Code/validate_claude.py --all > /tmp/claude_validate.log 2>&1 &"
wsl -d Ubuntu tail -f /tmp/claude_validate.log

# รวมผลใหม่โดยไม่รันเทสซ้ำ
wsl -d Ubuntu python3 /mnt/e/SaiStudyKKU/SQA/project/workspeac/Claude/Code/validate_claude.py --summarize-only
```
ถ้า `defects4j` ไม่อยู่ใน PATH ให้ส่ง `--defects4j-bin /home/<user>/defects4j/framework/bin/defects4j` หรือตั้ง `DEFECTS4J_BIN`
สคริปต์ข้ามบั๊กที่มี `result.json` แล้ว จึงหยุดแล้วรันต่อได้ (`--overwrite` เพื่อรันใหม่)

### 3.4 ดูผล
- `Claude/Results/defects4j-round2/summary_overall.json` — ภาพรวม: fault detection rate, mean line/branch/method coverage, จำนวนสถานะ
- `Claude/Results/defects4j-round2/summary_by_project.csv` — แยกรายโปรเจกต์
- `Claude/Results/defects4j-round2/results_claude.csv` — ตารางแบน ใช้กับ `experiments/scripts/summarize_results.sh` ของกลุ่มได้

---

## 4. ผลการทดลอง (run 1, ครบ 854 บั๊ก — รันเมื่อ 1 ต.ค. 2569)

ตัวเลขทั้งหมดมาจาก [`Results/defects4j-round2/summary_overall.json`](Results/defects4j-round2/summary_overall.json) และ [`summary_by_project.csv`](Results/defects4j-round2/summary_by_project.csv)

| ตัวชี้วัด | ค่า |
| --- | --- |
| บั๊กที่สร้างเทสได้ | 854 / 854 (เฉลี่ย 34.9 เทส/บั๊ก รวม 29,827 เทส) |
| รันวัดผลสมบูรณ์ (`complete`) | **697** (81.6%) |
| คอมไพล์ไม่ผ่าน (`no_valid_tests / test_compile_failed`) | 144 (16.9%) |
| เทสทั้งชุด fail บน fixed (`all_tests_fail_on_fixed`) | 9 |
| วัด coverage ไม่ได้ / หมดเวลา | 3 (Jsoup, Cobertura) + 1 (JxPath-13) |
| **Fault detection** | **191 บั๊ก** = 27.4% ของที่ประเมินได้ (22.4% ของ 854) |
| Line coverage เฉลี่ย (modified classes, buggy) | **72.6%** |
| Condition coverage เฉลี่ย | **63.9%** |
| Method coverage เฉลี่ย | **78.7%** |
| เทสที่ผ่านบน fixed ต่อเทสที่สร้าง (ใน complete runs) | 96.3% |
| เวลาโมเดลเฉลี่ยต่อบั๊ก | 383 วินาที (Closure ช้าสุด ~600 วิ) |
| Token รวม | input 40.9M / output 36.5M (เทียบราคา API ≈ $784) |

### 4.1 รายโปรเจกต์ (fault detected / evaluated, line coverage)
Chart 16/23 (76%), Cli 7/38 (89%), Closure 10/91 (56%), Codec 5/17 (79%), Collections 15/26 (83%), Compress 14/42 (74%),
Csv 4/13 (86%), Gson 6/18 (76%), JacksonCore 6/22 (58%), JacksonDatabind 13/90 (66%), JacksonXml 2/6 (62%), Jsoup 19/83 (76%),
JxPath 6/13 (68%), Lang 22/56 (76%), Math 33/103 (81%), Mockito 10/30 (82%), Time 3/26 (66%)

### 4.2 เทียบกับ Gemini ของกลุ่ม (ชุดบั๊กเดียวกัน, จาก `experiments/results.csv` ใน branch Nattapong)
| | Claude Sonnet 5 (งานนี้) | Gemini 3.5 Flash-Lite |
| --- | --- | --- |
| คอมไพล์ผ่าน | 83.1% | 53.3% |
| จับบั๊กได้ | 191 (22.4% ของ 854) | 10 (1.2%) |
| Line coverage เฉลี่ยของชุดที่คอมไพล์ผ่าน | 72.6% | 68.6% |

### 4.3 ข้อค้นพบ / ประเด็นปัญหา
- **Compile error กระจุกที่ Closure (80/144)** — javac ฟ้อง `cannot find symbol` / `no suitable method found for compile(...)` คือโมเดลเดา API ของ Closure Compiler (เช่น `Compiler.compile(...)`, `Node`) ผิด เพราะเห็นเพียงคลาสเป้าหมายไม่เห็นทั้งโปรเจกต์ รองลงมาคือ JacksonDatabind (20) จากคลาสภายในที่เป็น protected/abstract
- **เทสที่ "ยืนยันบั๊ก"** — 9 บั๊กที่ทุกเทส fail บน fixed และเทสที่ถูกตัดออก 3.7% ส่วนใหญ่คือ assertion ที่คัดลอกพฤติกรรมของโค้ดที่มีบั๊กมา แม้ prompt จะสั่งให้ยึด Javadoc แล้วก็ตาม
- **ข้อจำกัดเครื่องมือ** — Jsoup-4/6/9 Cobertura instrument ไม่ได้ (`Method code too large` ในคลาส `Entities`), JxPath-13 เทสค้างเกิน 15 นาที, Cli 1–34 ต้องแก้ `Cli.build.xml` ของ Defects4J 3.0.1 ให้ชี้ jar ที่มี hamcrest (รวมอยู่ใน `setup_wsl_defects4j.sh` แล้ว)
- **Token/เวลา** — เทสของ Closure ใช้ output 40–115k token ต่อบั๊ก (ส่วนใหญ่เป็น reasoning) จึงต้องยกเพดาน output เป็น 64k และตั้ง timeout 60 นาที; การรันทั้ง 854 บั๊กใช้ generator หลาย instance แบ่งคิวร่วมกัน (claim file) และหยุดรอเองเมื่อชนโควตาการใช้งาน

---

## 5. หมายเหตุ / ข้อจำกัดที่พบ
- ชื่อคลาสเทสใช้ `<Class>ClaudeTest` เพื่อไม่ชนกับเทสเดิมของโปรเจกต์ (เช่น `NumberUtilsTest` มีอยู่แล้วใน Lang)
- Coverage คำนวณจากเทสที่ผ่านบน fixed เท่านั้น ตามข้อกำหนดกลาง จึงอาจต่ำกว่าการนับทั้งชุด
- `branch_ratio` คือ *condition coverage* ของ Cobertura (`branch_measure = "condition"`) ตามที่กลุ่มตกลง
- ไม่มี credential ใดถูก commit: backend `cli` ใช้การล็อกอินของ Claude Code ในเครื่อง ส่วน `.env` (สำหรับ backend `api`) อยู่ใน `.gitignore`
- `generation.json` เก็บ `total_cost_usd` ที่ CLI รายงานไว้เพื่ออ้างอิง (ประมาณ $0.6–1.0 ต่อบั๊กที่ effort `high`) ถ้าต้องการลดเวลา/ค่าใช้จ่ายให้ใช้ `--effort medium`
- `generation_seconds` = เวลาที่โมเดลทำงานจริง (`duration_api_ms`) ส่วน `generation_wall_seconds` รวมเวลา retry/รอโควตา — ใช้ค่าแรกเมื่อเทียบกับ DE/ACO
- รัน generator หลายตัวพร้อมกันได้ (คนละบัญชีผ่าน `--cli-config-dir`) โดยแบ่งคิวอัตโนมัติ; `python Claude/Code/progress.py` แสดงความคืบหน้าสด
