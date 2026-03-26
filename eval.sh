#!/usr/bin/env bash
# Frozen evaluation harness for KCC14 warehouse optimization.
# DO NOT MODIFY — this is the ground truth evaluator.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KCC14_DIR="$SCRIPT_DIR/input/KCC14"
JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

# --- Compile ---
rm -rf "$KCC14_DIR/build"
mkdir -p "$KCC14_DIR/build"
find "$KCC14_DIR/src" -name "*.java" | xargs "$JAVAC" -d "$KCC14_DIR/build" 2>&1

# --- Run ---
cd "$KCC14_DIR"
OUTPUT=$("$JAVA" -cp "$KCC14_DIR/build" -DuseVisualizer=false com.knapp.codingcontest.Main 2>&1)
echo "$OUTPUT"

# --- Extract parseable metric ---
TOTAL_COST=$(echo "$OUTPUT" | grep "TOTAL COST" | grep -oE '[0-9]+\.[0-9]+')
TICKS=$(echo "$OUTPUT" | grep "costs ticks runtime" | grep -oE '[0-9]+$' | tail -1)
UNFINISHED=$(echo "$OUTPUT" | grep "costs/unfinished orders" | grep -oE '[0-9]+$' | tail -1)

echo "---"
echo "total_cost: ${TOTAL_COST:-ERROR}"
echo "ticks: ${TICKS:-0}"
echo "unfinished_orders: ${UNFINISHED:-0}"
