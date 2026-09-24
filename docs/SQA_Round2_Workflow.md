# SQA Project Round 2

## AI-Assisted Testing vs. Automatic Test Case Generation Algorithms

## เครื่องมือที่ใช้

### Automatic Test Case Generation Algorithms

1. Differential Evolution (DE)
2. Ant Colony Optimization (ACO)

### Generative AI / AI-Assisting Tools

1. Claude
2. Gemini

### Dataset

- Defects4J
- Java Projects
- JUnit

---

# 1. ภาพรวมการทำงาน

Workflow หลักของโครงงาน

```text
Setup Defects4J
      ↓
เลือก Project + Bug ID
      ↓
Checkout Buggy Version
      ↓
Compile + Run Test
      ↓
หา Target Class
      ↓
สร้างระบบวัดผลกลาง
      ↓
ทดลองกับ Bug ตัวอย่าง 1 ตัว
      ↓
┌────────┬────────┬────────┬────────┐
│   DE   │  ACO   │ Claude │ Gemini │
└────────┴────────┴────────┴────────┘
      ↓
Generate JUnit Tests
      ↓
Compile + Execute
      ↓
วัด Coverage / Fault Detection / Performance
      ↓
รวมผล
      ↓
เปรียบเทียบ
      ↓
Report + Presentation + Demo
```

---

# 2. Phase 0 — กำหนด Experiment Protocol

ก่อนแบ่งงาน ให้สมาชิกทั้ง 4 คนกำหนดรูปแบบการทดลองร่วมกันก่อน

## สิ่งที่ต้องกำหนด

- Defects4J Project ที่จะใช้
- Bug ID ที่จะใช้
- Target Class
- จำนวนรอบการทดลอง
- Configuration ของ Algorithm
- Metrics ที่จะวัด
- รูปแบบการบันทึกผล

ตัวอย่าง

```text
Algorithms:
- Differential Evolution
- Ant Colony Optimization

AI:
- Claude
- Gemini

Dataset:
- Defects4J

Number of Runs:
- 5 runs / Bug / Method

Metrics:
- Code Coverage
- Branch Coverage
- Fault Detection
- Generation Time
- Number of Generated Tests
- Compile Success / Failure
```

> หมายเหตุ: จำนวนรอบ เช่น 5 runs เป็นค่าที่กลุ่มกำหนดเองได้ โดยโจทย์อนุญาตให้ทดลองหลายครั้งและหาค่าเฉลี่ย

---

# 3. Phase 1 — สร้าง GitHub Repository

สร้าง Repository กลางสำหรับสมาชิกทั้ง 4 คน

ตัวอย่างชื่อ

```text
SQA-Automated-Test-Generation
```

## Repository Structure

```text
SQA-Automated-Test-Generation/
│
├── algorithms/
│   │
│   ├── DE/
│   │   ├── src/
│   │   ├── config/
│   │   ├── generated-tests/
│   │   └── results/
│   │
│   └── ACO/
│       ├── src/
│       ├── config/
│       ├── cfg/
│       ├── generated-tests/
│       └── results/
│
├── ai/
│   │
│   ├── Claude/
│   │   ├── prompts/
│   │   ├── generated-tests/
│   │   └── results/
│   │
│   └── Gemini/
│       ├── prompts/
│       ├── generated-tests/
│       └── results/
│
├── experiments/
│   ├── scripts/
│   ├── raw/
│   └── results.csv
│
├── docs/
│   ├── diagrams/
│   └── experiment-protocol.md
│
├── report/
│
├── presentation/
│
└── README.md
```

## Clone Repository

```bash
git clone <repository-url>
cd SQA-Automated-Test-Generation
```

แต่ละคนสามารถสร้าง Branch ของตัวเอง

```bash
git checkout -b de
```

```bash
git checkout -b aco
```

```bash
git checkout -b claude
```

```bash
git checkout -b gemini
```

---

# 4. Phase 2 — Setup Defects4J

สมาชิกทุกคนควร Setup Defects4J ให้สามารถใช้งานได้ก่อน

## 4.1 Windows

แนะนำให้ใช้

```text
Windows
   ↓
WSL
   ↓
Ubuntu
   ↓
Defects4J
```

ติดตั้ง WSL ผ่าน PowerShell

```powershell
wsl --install
```

จากนั้นเปิด Ubuntu

## 4.2 ตรวจสอบ Environment

```bash
java -version
git --version
perl --version
```

## 4.3 Clone Defects4J

```bash
cd ~
git clone https://github.com/rjust/defects4j.git
cd defects4j
```

ติดตั้ง dependencies ตาม environment ที่ใช้ และ initialize Defects4J

```bash
cpanm --installdeps .
./init.sh
```

เพิ่ม Defects4J เข้า PATH

```bash
export PATH=$PATH:$HOME/defects4j/framework/bin
```

ทดสอบ

```bash
defects4j info -p Lang
```

ถ้าคำสั่งนี้ทำงานได้ แสดงว่า Defects4J พร้อมใช้งาน

---

# 5. Phase 3 — ทดลอง Defects4J Bug แรก

ช่วงแรกยังไม่ต้องทดลองหลาย Bug

ให้เลือกเพียง 1 Bug เพื่อสร้าง Prototype ของระบบทั้งหมดก่อน

ตัวอย่าง

```text
Project = Lang
Bug ID = 1
```

สร้าง Working Directory

```bash
mkdir -p ~/d4j-work
cd ~/d4j-work
```

Checkout Buggy Version

```bash
defects4j checkout -p Lang -v 1b -w Lang_1_buggy
```

เข้า Project

```bash
cd Lang_1_buggy
```

Compile

```bash
defects4j compile
```

Run Test

```bash
defects4j test
```

เป้าหมายของ Phase นี้คือ

```text
Checkout
   ↓
Compile
   ↓
Test
   ↓
Success
```

หากขั้นตอนนี้ยังไม่สำเร็จ ยังไม่ควรเริ่มพัฒนา DE หรือ ACO

---

# 6. Phase 4 — หา Target Class

หลังจาก Checkout Bug แล้ว ต้องหาว่า Bug เกี่ยวข้องกับ Class ใด

ใช้

```bash
defects4j export -p classes.modified
```

ตัวอย่างผลลัพธ์

```text
org.example.SomeClass
```

จากนั้นศึกษา Source Code ของ Class ดังกล่าว

สิ่งที่ต้องดู

- มี Method อะไรบ้าง
- Method รับ Parameter อะไร
- Parameter เป็น Data Type อะไร
- มี if / else หรือไม่
- มี loop หรือไม่
- มี Exception หรือไม่
- มี Boundary Condition อะไร
- Branch ใดที่ต้องพยายามเข้าให้ถึง

ข้อมูลส่วนนี้จะถูกใช้กับทั้ง

```text
DE
ACO
Claude
Gemini
```

---

# 7. Phase 5 — ทดลอง JUnit ด้วยมือก่อน

ก่อนสร้าง Automatic Test Generator ให้ทดลองสร้าง JUnit Test ง่าย ๆ ด้วยมือก่อน

ตัวอย่าง

```java
public class ExampleTest {

    @Test
    public void testSomething() {
        // Arrange

        // Act

        // Assert
    }
}
```

จากนั้นนำ Test เข้า Project แล้วทดลอง

```bash
defects4j compile
defects4j test
```

เป้าหมายคือพิสูจน์ว่า

```text
JUnit Test
    ↓
Defects4J
    ↓
Compile
    ↓
Execute
    ↓
Success
```

ถ้ายังไม่สามารถเพิ่ม Test และ Run ได้ ไม่ควรเริ่มสร้าง Test Generator

---

# 8. Phase 6 — สร้าง Measurement Pipeline

ต้องสร้างวิธีวัดผลกลางที่ทั้ง 4 วิธีใช้เหมือนกัน

```text
Generated JUnit
      ↓
Compile
      ↓
Execute
      ↓
Coverage
      ↓
Fault Detection
      ↓
Save Result
```

## Metrics

อย่างน้อยเก็บ

- Method
- Project
- Bug ID
- Run
- Number of Generated Tests
- Compile Success / Failure
- Test Passed
- Test Failed
- Code Coverage
- Branch Coverage
- Fault Detection
- Generation Time

## ตัวอย่าง results.csv

```csv
method,project,bug_id,run,tests,compile,code_coverage,branch_coverage,fault_detected,time
DE,Lang,1,1,0,FALSE,0,0,FALSE,0
ACO,Lang,1,1,0,FALSE,0,0,FALSE,0
Claude,Lang,1,1,0,FALSE,0,0,FALSE,0
Gemini,Lang,1,1,0,FALSE,0,0,FALSE,0
```

ทุกวิธีต้องใช้รูปแบบผลเดียวกัน เพื่อให้สามารถเปรียบเทียบกันได้

---

# 9. Phase 7 — Prototype ระบบทั้งหมด

ก่อนทดลองจริง ให้ใช้ Bug เพียง 1 ตัวทดสอบระบบทั้งหมดก่อน

```text
                       ┌── DE ──────→ JUnit
                       │
Target Java Class ─────┼── ACO ─────→ JUnit
                       │
                       ├── Claude ───→ JUnit
                       │
                       └── Gemini ───→ JUnit
                                         ↓
                                    Defects4J
                                         ↓
                                  Compile + Test
                                         ↓
                                      Metrics
```

เป้าหมายของ Prototype ไม่ใช่ Coverage สูงที่สุด

เป้าหมายคือทำให้ Pipeline ตั้งแต่ต้นจนจบทำงานได้ก่อน

---

# 10. Phase 8 — แบ่งงาน 4 คน

เมื่อ Prototype และระบบกลางพร้อมแล้ว จึงแยกงาน

| สมาชิก  | งานหลัก                 | งานเพิ่มเติม                |
| ------- | ----------------------- | --------------------------- |
| คนที่ 1 | Differential Evolution  | เก็บ Configuration และผล DE |
| คนที่ 2 | Ant Colony Optimization | CFG และผล ACO               |
| คนที่ 3 | Claude                  | Measurement Pipeline        |
| คนที่ 4 | Gemini                  | Results / Data Collection   |

---

# 11. คนที่ 1 — Differential Evolution (DE)

จากรายงานรอบ 1 กำหนด Workflow ของ DE เป็น

```text
Initialize Population
        ↓
Fitness Evaluation
        ↓
Mutation
        ↓
Crossover
        ↓
Selection
        ↓
Termination
        ↓
Best Test Inputs
        ↓
Generate JUnit
```

## 11.1 Test Input Representation

สมาชิกแต่ละตัวใน Population แทน Test Input

ตัวอย่าง Method

```java
calculate(int x, int y)
```

Candidate อาจเป็น

```text
[10, 5]
[-1, 20]
[0, 0]
[100, -10]
```

## 11.2 Fitness

ใช้แนวทางที่กำหนดไว้ในรอบ 1

```text
Approach Level
+
Branch Distance
```

เป้าหมายคือค้นหา Input ที่สามารถเข้าใกล้หรือครอบคลุม Target Branch

## 11.3 Mutation

ใช้

```text
vi = xr1 + F × (xr2 - xr3)
```

## 11.4 Crossover

ผสม Donor Vector กับ Target Vector ตามค่า

```text
CR = Crossover Rate
```

## 11.5 Selection

เปรียบเทียบ Fitness

```text
Trial Vector
vs
Target Vector
```

เก็บตัวที่มี Fitness ดีกว่าไว้ใน Population รุ่นถัดไป

## 11.6 Output

เมื่อ Algorithm จบ

```text
Best Test Inputs
      ↓
Convert
      ↓
JUnit Tests
```

## สิ่งที่คน DE ต้องส่ง

```text
algorithms/DE/
├── src/
├── config/
├── generated-tests/
└── results/
```

Configuration ที่ควรเก็บ เช่น

```text
Population Size
Mutation Factor (F)
Crossover Rate (CR)
Generation / Budget
Random Seed
```

---

# 12. คนที่ 2 — Ant Colony Optimization (ACO)

Workflow ตามรายงานรอบ 1

```text
Java Method
     ↓
Control Flow Graph
     ↓
Initialize Pheromone
     ↓
Path Construction
     ↓
Input Generation
     ↓
Execute Test
     ↓
Coverage
     ↓
Pheromone Update
     ↓
Repeat
     ↓
Generate JUnit
```

## 12.1 Graph Construction

สร้าง Control Flow Graph (CFG)

```text
Node = Statement / Basic Block
Edge = Control Flow
```

ตัวอย่าง

```text
        Start
          |
        x > 10
       /      \
    True      False
      |         |
   Block A   Block B
       \       /
          End
```

## 12.2 Initialize Pheromone

กำหนดค่า Pheromone เริ่มต้นให้แต่ละ Edge

## 12.3 Path Construction

Artificial Ant เลือกเส้นทางโดยพิจารณาจาก

```text
Pheromone
+
Heuristic
```

สามารถให้ความสำคัญกับ Branch ที่ยังไม่ถูกครอบคลุม

## 12.4 Input Generation

หา Test Input ที่ทำให้โปรแกรมทำงานตาม Path ที่เลือก

ขั้นตอนนี้เป็นส่วนสำคัญที่ต้องออกแบบและ Implement ให้ชัดเจน

## 12.5 Pheromone Update

ลด Pheromone

```text
Evaporation
```

และเพิ่ม Pheromone ให้เส้นทางที่ให้ Coverage ที่ดี

## สิ่งที่คน ACO ต้องส่ง

```text
algorithms/ACO/
├── src/
├── config/
├── cfg/
├── generated-tests/
└── results/
```

Configuration เช่น

```text
Number of Ants
Iterations
Alpha
Beta
Evaporation Rate
Random Seed
```

---

# 13. คนที่ 3 — Claude + Measurement Pipeline

ใช้ Prompt ที่ออกแบบไว้ในรอบ 1

ข้อมูลที่ส่งให้ Claude

```text
Project
Bug ID / Version
Target Class
Java Source Code
```

เป้าหมายของ Prompt

```text
Normal Cases
Boundary Cases
Exception Cases
Branch Coverage
Code Coverage
Fault Detection
```

## สิ่งที่ต้องเก็บ

```text
ai/Claude/
├── prompts/
├── generated-tests/
└── results/
```

ตัวอย่าง

```text
ai/Claude/prompts/Lang_1.txt

ai/Claude/generated-tests/Lang_1_Test.java

ai/Claude/results/Lang_1_Run1.json
```

## งานเพิ่มเติม

รับผิดชอบ Measurement Pipeline กลาง เช่น

```text
JUnit
 ↓
Compile
 ↓
Test
 ↓
Coverage
 ↓
Result
```

เพื่อให้ทั้ง DE, ACO, Claude และ Gemini ใช้วิธีวัดผลเหมือนกัน

---

# 14. คนที่ 4 — Gemini + Results

ใช้ Prompt รูปแบบเดียวกับ Claude ตามที่ออกแบบไว้ในรอบ 1

```text
Project
Bug ID / Version
Target Class
Java Source Code
```

เก็บ

```text
ai/Gemini/
├── prompts/
├── generated-tests/
└── results/
```

## งานเพิ่มเติม

รับผิดชอบรวมผลการทดลอง

```text
experiments/
├── raw/
│   ├── DE/
│   ├── ACO/
│   ├── Claude/
│   └── Gemini/
│
└── results.csv
```

---

# 15. Phase 9 — การทดลองจริง

หลัง Prototype สำเร็จแล้ว จึงขยายไปยัง Bugs ที่กำหนด

สำหรับ Bug แต่ละตัว ทำขั้นตอน

```text
1. Checkout Bug

2. หา Target Class

3. DE
   → Generate JUnit

4. ACO
   → Generate JUnit

5. Claude
   → Generate JUnit

6. Gemini
   → Generate JUnit

7. Compile Tests

8. Execute Tests

9. Measure Coverage

10. Evaluate Fault Detection

11. Record Generation Time

12. Save Results
```

ถ้ากำหนด 5 Runs

```text
Lang-1/
├── Run-1/
├── Run-2/
├── Run-3/
├── Run-4/
└── Run-5/
```

จากนั้นสามารถหาค่าเฉลี่ยของผลลัพธ์ได้

---

# 16. Phase 10 — Fault Detection

ต้องแยก

```text
Coverage
```

ออกจาก

```text
Fault Detection
```

เพราะ Test ที่ Coverage สูง ไม่ได้หมายความว่าจะตรวจพบ Bug เสมอไป

Defects4J มี Buggy และ Fixed Version เช่น

```text
1b = Buggy Version
1f = Fixed Version
```

กลุ่มต้องกำหนด Protocol สำหรับประเมิน Fault Detection ให้ชัดเจน และใช้วิธีเดียวกันกับ Test Suite จากทั้ง 4 วิธี

เป้าหมายคือวัดว่า Test Suite ที่สร้างขึ้นสามารถเปิดเผย Fault ที่ Defects4J ระบุไว้ได้หรือไม่

---

# 17. Phase 11 — รวมผล

เมื่อทดลองครบแล้ว รวมผลเป็นตาราง

| Method | Code Coverage | Branch Coverage | Fault Detection | Generation Time | Tests |
| ------ | ------------: | --------------: | --------------: | --------------: | ----: |
| DE     |           ... |             ... |             ... |             ... |   ... |
| ACO    |           ... |             ... |             ... |             ... |   ... |
| Claude |           ... |             ... |             ... |             ... |   ... |
| Gemini |           ... |             ... |             ... |             ... |   ... |

จากนั้นวิเคราะห์เป็น 3 ส่วน

## Algorithm vs Algorithm

```text
DE
vs
ACO
```

เปรียบเทียบ

- Coverage
- Fault Detection
- Generation Time
- Test Suite Size
- ข้อจำกัดของ Algorithm

## AI vs AI

```text
Claude
vs
Gemini
```

เปรียบเทียบ

- Compile Success
- Coverage
- Fault Detection
- Generation Time
- จำนวน Test
- ปัญหาที่พบ

## Algorithm vs AI

```text
DE + ACO
vs
Claude + Gemini
```

ดูว่าแต่ละแนวทางมีพฤติกรรมแตกต่างกันอย่างไรจากผลการทดลอง

---

# 18. Phase 12 — Final Report

รายงานฉบับสมบูรณ์ควรมี

```text
1. Introduction

2. Background
   2.1 Defects4J
   2.2 Differential Evolution
   2.3 Ant Colony Optimization
   2.4 Claude
   2.5 Gemini

3. Methodology
   3.1 Experimental Setup
   3.2 Dataset / Projects / Bugs
   3.3 DE Implementation
   3.4 ACO Implementation
   3.5 AI Prompt
   3.6 Evaluation Metrics

4. Experiment
   4.1 DE
   4.2 ACO
   4.3 Claude
   4.4 Gemini

5. Results

6. Comparison

7. Discussion

8. Problems / Limitations

9. Conclusion

10. References
```

---

# 19. GitHub README

README ของ Repository ควรอธิบาย

```text
Project Overview
│
├── Environment
├── Installation
├── Defects4J Setup
├── Dataset / Selected Bugs
├── Experiment Protocol
├── Differential Evolution
├── Ant Colony Optimization
├── Claude
├── Gemini
├── Evaluation Metrics
├── Results
└── Reproduction Guide
```

เป้าหมายคือให้คนอื่นสามารถอ่าน Repository แล้วทำการทดลองซ้ำได้

---

# 20. Presentation และ Demo

Presentation ควรอธิบาย

```text
Problem
 ↓
Objective
 ↓
DE / ACO / Claude / Gemini
 ↓
Experiment Design
 ↓
Results
 ↓
Comparison
 ↓
Problems
 ↓
Conclusion
```

## Demo

ไม่จำเป็นต้อง Demo ทุก Bug

เลือก Bug ตัวอย่างที่เห็นภาพชัด

```text
Defects4J Bug
      ↓
Target Class
      ↓
Generate Test
      ↓
JUnit
      ↓
Run Test
      ↓
Coverage / Fault Detection
      ↓
Result
```

---

# 21. การแบ่งงานสรุป

## คนที่ 1 — DE

- Implement Differential Evolution
- Test Input Representation
- Fitness Function
- Branch Distance
- Approach Level
- Mutation
- Crossover
- Selection
- Generate JUnit
- Configuration
- DE Experiment
- DE Results

## คนที่ 2 — ACO

- CFG
- Pheromone
- Heuristic
- Path Construction
- Input Generation
- Pheromone Update
- Generate JUnit
- Configuration
- ACO Experiment
- ACO Results

## คนที่ 3 — Claude + Measurement

- Claude Prompt
- Claude Experiment
- Generated Tests
- Compile / Execute
- Measurement Pipeline
- Coverage Collection
- Claude Results

## คนที่ 4 — Gemini + Results

- Gemini Prompt
- Gemini Experiment
- Generated Tests
- Gemini Results
- รวมข้อมูลทุก Method
- results.csv
- ตาราง
- กราฟ

---

# 22. สิ่งที่ทั้ง 4 คนต้องช่วยกัน

ถึงจะแบ่งงานแล้ว แต่สิ่งต่อไปนี้ควรทำร่วมกัน

- เลือก Defects4J Projects / Bugs
- กำหนด Experiment Protocol
- กำหนด Metrics
- ตรวจสอบ Measurement Pipeline
- วิเคราะห์ผล
- Discussion
- Conclusion
- ตรวจ Final Report
- เตรียม Presentation
- เตรียม Demo

---

# 23. Checklist ก่อนแยกงาน

ทุกคนต้องทำขั้นตอนนี้ให้สำเร็จก่อน

- [ ] สร้าง GitHub Repository
- [ ] Clone Repository
- [ ] Setup WSL
- [ ] Setup Defects4J
- [ ] `defects4j info` ทำงาน
- [ ] เลือก Project ตัวอย่าง
- [ ] เลือก Bug ID ตัวอย่าง
- [ ] Checkout Buggy Version
- [ ] `defects4j compile` สำเร็จ
- [ ] `defects4j test` สำเร็จ
- [ ] หา Target Class ได้
- [ ] อ่าน Target Source Code
- [ ] ทดลองเพิ่ม JUnit Test
- [ ] JUnit Compile ได้
- [ ] JUnit Execute ได้
- [ ] กำหนด Metrics
- [ ] กำหนด Result Format
- [ ] สร้าง Measurement Pipeline เบื้องต้น
- [ ] Prototype กับ Bug แรกสำเร็จ

เมื่อทั้งหมดผ่านแล้วจึงเริ่ม

```text
คน 1 → DE

คน 2 → ACO

คน 3 → Claude + Measurement

คน 4 → Gemini + Results
```

---

# 24. Workflow สุดท้าย

```text
                 ┌─────────────────┐
                 │    Defects4J    │
                 └────────┬────────┘
                          │
                          ▼
                 Project + Bug ID
                          │
                          ▼
                     Target Class
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
         DE              ACO             AI
                                          │
                                   ┌──────┴──────┐
                                   ▼             ▼
                                Claude        Gemini

          │               │        │             │
          └───────────────┴────────┴─────────────┘
                          │
                          ▼
                   Generated JUnit
                          │
                          ▼
                    Compile + Run
                          │
                          ▼
              ┌─────────────────────┐
              │ Evaluation Metrics  │
              ├─────────────────────┤
              │ Code Coverage       │
              │ Branch Coverage     │
              │ Fault Detection     │
              │ Generation Time     │
              │ Number of Tests     │
              │ Compile Success     │
              └──────────┬──────────┘
                         │
                         ▼
                    Compare Results
                         │
                         ▼
                  Analyze / Discuss
                         │
                         ▼
             Report + GitHub + Demo
```

---

# สิ่งที่ควรทำเป็นอันดับแรก

อย่าเพิ่งแยกกันไปทำ DE, ACO, Claude และ Gemini ทันที

ให้ทั้ง 4 คนทำร่วมกันจนถึงจุดนี้ก่อน

```text
1. Setup Defects4J
        ↓
2. Checkout Bug ตัวอย่าง 1 ตัว
        ↓
3. Compile
        ↓
4. Run Test
        ↓
5. หา Target Class
        ↓
6. ทดลองเพิ่ม JUnit ด้วยมือ
        ↓
7. Run JUnit สำเร็จ
        ↓
8. กำหนด Metrics
        ↓
9. กำหนด Result Format
        ↓
10. Prototype Pipeline สำเร็จ
        ↓
11. แยก 4 คน
```

หลังจาก Pipeline แรกทำงานครบแล้ว จึงค่อยขยายไปยัง Bugs และ Projects ที่กำหนดจริง
