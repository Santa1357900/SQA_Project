# Differential Evolution for Defects4J

This folder contains the DE/rand/1/bin implementation, its runtime configuration, and the consolidated Defects4J results. The package source is **3.1.20-rebuild**. The included result snapshot is the experiment `all-defects4j-v3110-full512-w4`; it combines records produced by several rebuild versions, recorded per bug in each `result.json`. It is therefore a consolidated dataset, not a single-version rerun.

## Repository layout

```text
Code/             Java and Python runner sources
Configuration/    defaults, runtime profile, and required libraries
Results/          normalized result records, summaries, and run metadata
Test/             generated test suites for completed runs
COMMON_OUTPUT_FORMAT.md
common-result.example.json
run.sh
run-full.sh
THIRD_PARTY.md
```

`work/` is deliberately not included. It contains temporary Defects4J checkouts and build files and can be recreated by a new run. The committed `Test/` folder contains the generated suites for completed records; runs with `no_generated_tests` have no test suite to include.

## Result snapshot

The snapshot contains **854 bugs across 17 Defects4J projects**:

- 701 runs completed evaluation; 153 ended as `no_generated_tests`.
- 95 of the 701 evaluated bugs were detected (fault detection rate: **13.55%** among evaluated bugs).
- There are no recorded `fail` group outcomes in this snapshot. The 153 `no_generated_tests` are reported as `no run`, not as passes.
- The coverage records use both Defects4J/Cobertura and JaCoCo. Since tools are mixed, the consolidated summary leaves mean coverage ratios `null`; compare coverage within a single tool instead.

Read `Results/summary_overall.json` for the experiment-wide counts and `Results/summary_by_project.csv` for project-level counts. Each normalized record is at `Results/<Project>/<Project>-<bug>/run1/result.json`. Its `algorithm_version` identifies the version that produced that bug's result. The `artifacts.tests` path points to the included `Test/` directory for completed runs; logs and legacy raw-result files are not included and are marked `null`.

`complete` means the generated suite was validated and the run could be evaluated; it does not mean a fault was detected. `no_generated_tests` means the generator produced no candidate test suite, so fault detection and coverage are unknown for that bug. See [COMMON_OUTPUT_FORMAT.md](COMMON_OUTPUT_FORMAT.md) for field definitions and metric formulas.

## Requirements

Run the pipeline in Ubuntu/WSL, not directly in Windows. Install and configure Defects4J, Java, Perl, and Python 3. By default the runner expects Defects4J at `~/defects4j`; set `DEFECTS4J_HOME` if it is elsewhere:

```bash
export DEFECTS4J_HOME="$HOME/defects4j"
export PATH="$DEFECTS4J_HOME/framework/bin:$PATH"
java -version
python3 --version
defects4j info -p Chart
```

The result snapshot is for review and reporting. To run a fresh experiment, use a new experiment name; do not overwrite or resume the consolidated snapshot. Keep the `--work` path outside this repository and without spaces (the repository folder name itself contains spaces):

```bash
EXP=all-defects4j-v3120-full512-w4-r1
bash run.sh run --workers 4 --runs 1 \
  --population 16 --budget 512 --mutation 0.7 --crossover 0.9 --input-range 1000 \
  --max-methods 0 --max-tests-per-method 16 \
  --method-timeout 300 --job-timeout 1500 --validation-reserve 180 \
  --test-timeout 60 --command-timeout 600 --candidate-timeout 12 \
  --seed 2026 --heap 512m \
  --results "$EXP" --work "$HOME/de-work/$EXP"
```

This command uses the package's 3.1.20 defaults and asks the installed Defects4J to discover all active bugs. For a pilot, add `--project Chart --bug 1` and use a separate experiment name. Check status with `bash run.sh status --results "$EXP"`; continue eligible interrupted work with `bash run.sh resume --results "$EXP" --work "$HOME/de-work/$EXP" --workers 4`. Save the generated `Results/<experiment>/` folder together with its `configuration.json`, `environment.json`, and `manifest.json` for reproducibility.

## GitHub preparation

Before committing, inspect the staged changes and ensure the data is intended to be public. The result and test folders are substantive experiment artifacts, not temporary build output. From this repository in Git Bash/WSL:

```bash
git status --short
git add -A
git diff --cached --stat
git diff --cached --check
```

Review the staged file list, then commit and push using the repository's normal branch workflow. Do not commit Defects4J checkout/work directories, credentials, or unrelated local files.
