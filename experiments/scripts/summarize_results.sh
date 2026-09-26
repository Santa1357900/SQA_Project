#!/bin/bash
# Usage: ./summarize_results.sh <method_name>
# Example: ./summarize_results.sh ACO

METHOD=$1
CSV="experiments/results.csv"

awk -F',' -v method="$METHOD" '
NR==1 { next }
$1==method {
    n++
    gsub("%","",$7); gsub("%","",$8)
    line_sum += $7
    branch_sum += $8
    if ($9=="TRUE") fault_count++
    time_sum += $10
}
END {
    if (n==0) { print "No rows found for method " method; exit }
    printf "Method: %s\n", method
    printf "Runs: %d\n", n
    printf "Avg Line Coverage:     %.2f%%\n", line_sum/n
    printf "Avg Branch Coverage:   %.2f%%\n", branch_sum/n
    printf "Fault Detection Rate:  %d/%d (%.1f%%)\n", fault_count, n, (fault_count/n)*100
    printf "Avg Time:              %.0f ms\n", time_sum/n
}
' "$CSV"
