#!/bin/bash
# Usage: ./run_measurement.sh <method> <project> <bug_id> <run> <test_class> <work_dir>

METHOD=$1
PROJECT=$2
BUG_ID=$3
RUN=$4
TEST_CLASS=$5
WORK_DIR=$6
RESULTS_CSV="experiments/results.csv"

cd "$WORK_DIR" || exit 1

START_TIME=$(awk '{print $1}' /proc/uptime)

# 1. Compile
defects4j compile > /tmp/compile_log.txt 2>&1
COMPILE_STATUS=$?

if [ $COMPILE_STATUS -ne 0 ]; then
    echo "$METHOD,$PROJECT,$BUG_ID,$RUN,0,FALSE,0,0,FALSE,0" >> "$RESULTS_CSV"
    echo "Compile failed. See /tmp/compile_log.txt"
    exit 1
fi

# 2. หาไฟล์ .java แล้วดึงชื่อ method ที่มี @Test
TEST_SRC_DIR=$(defects4j export -p dir.src.tests)
CLASS_PATH_PART=$(echo "$TEST_CLASS" | tr '.' '/')
TEST_FILE=$(find "$TEST_SRC_DIR" -name "$(basename "$CLASS_PATH_PART").java" | head -1)

if [ -z "$TEST_FILE" ]; then
    echo "Test file not found for $TEST_CLASS"
    exit 1
fi

METHOD_NAMES=$(grep -A1 "@Test" "$TEST_FILE" | grep "public void" | sed -E 's/.*void[[:space:]]+([a-zA-Z0-9_]+)\(.*/\1/')

if [ -z "$METHOD_NAMES" ]; then
    echo "No @Test methods found in $TEST_FILE"
    exit 1
fi

# ประกอบเป็น Class::method1,method2,method3 (ระบุ class แค่ครั้งเดียว)
METHOD_LIST=$(echo "$METHOD_NAMES" | tr '\n' ',' | sed 's/,$//')
TEST_LIST="${TEST_CLASS}::${METHOD_LIST}"

echo "Running tests: $TEST_LIST"

# 3. Run Test
defects4j test -t "$TEST_LIST" > /tmp/test_log.txt 2>&1
TEST_PASSED=$(grep -c "Failing tests: 0" /tmp/test_log.txt)

# 4. Coverage
defects4j coverage -t "$TEST_LIST" > /tmp/coverage_log.txt 2>&1
LINE_COV=$(grep "Line coverage" /tmp/coverage_log.txt | grep -o '[0-9]*\.[0-9]*%' | head -1)
COND_COV=$(grep "Condition coverage" /tmp/coverage_log.txt | grep -o '[0-9]*\.[0-9]*%' | head -1)

# 5. Fault Detection
FAULT_DETECTED=$([ "$TEST_PASSED" -eq 1 ] && echo "FALSE" || echo "TRUE")

END_TIME=$(awk '{print $1}' /proc/uptime)
ELAPSED_MS=$(awk -v s="$START_TIME" -v e="$END_TIME" 'BEGIN{printf "%.0f", (e-s)*1000}')

# 6. Save to CSV
NUM_TESTS=$(echo "$METHOD_NAMES" | wc -l)
echo "$METHOD,$PROJECT,$BUG_ID,$RUN,$NUM_TESTS,TRUE,$LINE_COV,$COND_COV,$FAULT_DETECTED,$ELAPSED_MS" >> "$RESULTS_CSV"

echo "Done. Result appended to $RESULTS_CSV"
