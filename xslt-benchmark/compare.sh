#!/bin/sh
#
# Copyright (C) 2026 Philip Helger (www.helger.com)
# philip[at]helger[dot]com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Runs the same JMH benchmarks against the engine of two commits and prints the scores side by side.
#
# Usage: compare.sh <base-commit> <new-commit> [JMH arguments...]
# Example: compare.sh main bench -p m_nSize=10000 'XPathBenchmark|XsltBenchmark'
#
# The engine of each commit (xpath-xdm, xpath-core, xslt-core, xsd-core) is built with
# "mvn -o -q package -DskipTests" in a detached git worktree below the work directory, and its jars
# replace the 0.1.0-SNAPSHOT jars of ~/.m2 on the class path. Nothing is installed: the local
# repository stays untouched. The benchmark classes themselves are compiled once, from this
# checkout, so both commits must have the engine API the benchmark uses.
#
# Environment:
#   BENCH_WORK_DIR  work directory for the worktrees and the result files (default: a new temporary
#                   directory); worktrees that exist there are reused
#   BENCH_KEEP=1    keep the worktrees after the run

set -e

if [ $# -lt 2 ]; then
  echo "Usage: $0 <base-commit> <new-commit> [JMH arguments...]" >&2
  exit 2
fi

BASE_REV=$1
NEW_REV=$2
shift 2

MODULE_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$MODULE_DIR/.." && pwd)
WORK_DIR=${BENCH_WORK_DIR:-$(mktemp -d -t xslt-benchmark)}
mkdir -p "$WORK_DIR"
JAVA_HOME=$(/usr/libexec/java_home -v 21)
export JAVA_HOME
ENGINE_MODULES="xpath-xdm xpath-core xslt-core xsd-core"

# Remove the engine worktrees on exit, also after a failure
cleanup ()
{
  if [ "$BENCH_KEEP" != "1" ]; then
    for TREE in "$WORK_DIR"/engine-*; do
      if [ -d "$TREE" ]; then
        git -C "$REPO_DIR" worktree remove --force "$TREE"
      fi
    done
  fi
}
trap cleanup EXIT

# A clean build: an incremental compile can drop the generated META-INF/BenchmarkList
(cd "$MODULE_DIR" && mvn -o -q clean package)
if [ ! -s "$MODULE_DIR/target/classes/META-INF/BenchmarkList" ]; then
  echo "No META-INF/BenchmarkList was generated" >&2
  exit 1
fi

# The class path of the benchmark without the engine jars of the local repository
OTHER_CP=$(tr ':' '\n' < "$MODULE_DIR/target/classpath.txt" | grep -v '/com/helger/xpath/' | paste -s -d ':' -)

run_commit ()
{
  REV=$1
  # The remaining arguments are for JMH
  shift
  SHA=$(git -C "$REPO_DIR" rev-parse --short "$REV^{commit}")
  TREE="$WORK_DIR/engine-$SHA"
  if [ ! -d "$TREE" ]; then
    git -C "$REPO_DIR" worktree add --detach "$TREE" "$SHA" > /dev/null
  fi
  echo "Building the engine of $REV ($SHA) in $TREE" >&2
  (cd "$TREE" && mvn -o -q package -DskipTests -Dmaven.javadoc.skip=true -pl xpath-xdm,xpath-core,xslt-core,xsd-core -am)
  ENGINE_CP=""
  for MODULE in $ENGINE_MODULES; do
    ENGINE_CP="$ENGINE_CP$TREE/$MODULE/target/$MODULE-0.1.0-SNAPSHOT.jar:"
  done
  echo "Running JMH against $REV ($SHA)" >&2
  "$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US \
    -cp "$ENGINE_CP$MODULE_DIR/target/classes:$OTHER_CP" \
    org.openjdk.jmh.Main -rf csv -rff "$WORK_DIR/result-$SHA.csv" "$@" >&2
  RESULT_FILE="$WORK_DIR/result-$SHA.csv"
}

run_commit "$BASE_REV" "$@"
BASE_FILE=$RESULT_FILE
run_commit "$NEW_REV" "$@"
NEW_FILE=$RESULT_FILE

# Join both CSV files on benchmark name and parameters; column 5 is the score, 7 the unit
LC_ALL=C awk -F',' -v BASE="$BASE_REV" -v NEW="$NEW_REV" '
  function rowkey(    k, i) { k = $1; for (i = 8; i <= NF; i++) k = k "," $i; gsub(/"/, "", k); sub(/^com\.helger\.xpath\.bench\./, "", k); return k }
  FNR == 1 { next }
  NR == FNR { aBase[rowkey()] = $5; next }
  {
    k = rowkey()
    unit = $7
    gsub(/"/, "", unit)
    if (!header) { printf "%-60s %14s %14s %8s  %s\n", "Benchmark,parameters", BASE, NEW, "new/base", "unit"; header = 1 }
    b = aBase[k]
    if (b == "" || b + 0 == 0)
      printf "%-60s %14s %14.3f %8s  %s\n", k, "-", $5, "-", unit
    else
      printf "%-60s %14.3f %14.3f %8.2f  %s\n", k, b, $5, $5 / b, unit
  }
' "$BASE_FILE" "$NEW_FILE"

echo "Results: $BASE_FILE, $NEW_FILE" >&2
