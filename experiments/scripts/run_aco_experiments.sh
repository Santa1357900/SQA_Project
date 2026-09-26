#!/bin/bash
# Usage: ./run_aco_experiments.sh <ACO_PROJECT_DIR> <D4J_WORK_DIR> <seed1> <seed2> ...
# Example:
#   ./run_aco_experiments.sh "/mnt/c/Users/PXn/Pictures/SQA/SQA_Project/Ant Colony Optimization(ACO)" ~/d4j-work/Lang_1_buggy 1 2 3 4 5

ACO_DIR=$1
D4J_WORK_DIR=$2
shift 2
SEEDS=("$@")

CONFIG_FILE="$ACO_DIR/Configuration/aco_config.properties"
TEST_DEST="$D4J_WORK_DIR/src/test/java/org/apache/commons/lang3/math/ACOTest.java"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Config file not found: $CONFIG_FILE"
    exit 1
fi

RUN_INDEX=1
for SEED in "${SEEDS[@]}"; do
    echo ""
    echo "=========================================="
    echo "=== Seed $SEED  (run #$RUN_INDEX) ==="
    echo "=========================================="

    # 1. Update seed in config
    sed -i "s/^random_seed=.*/random_seed=$SEED/" "$CONFIG_FILE"

    # 2. Recompile + regenerate ACOTest.java with this seed
    (cd "$ACO_DIR" && javac -d Code Code/*.java && java -cp Code Main)
    if [ $? -ne 0 ]; then
        echo "ACO generation failed for seed $SEED - skipping"
        RUN_INDEX=$((RUN_INDEX + 1))
        continue
    fi

    # 3. Copy generated test into the Defects4J project
    cp "$ACO_DIR/Test/ACOTest.java" "$TEST_DEST"

    # 4. Run the shared measurement pipeline
        ./experiments/scripts/run_measurement.sh ACO Lang 1 "$RUN_INDEX" org.apache.commons.lang3.math.ACOTest "$D4J_WORK_DIR"

    RUN_INDEX=$((RUN_INDEX + 1))
done

echo ""
echo "All $((RUN_INDEX - 1)) runs complete. See experiments/results.csv"
