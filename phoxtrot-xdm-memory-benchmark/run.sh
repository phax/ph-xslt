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

# Runs the memory benchmark on JDK 21, on JDK 25 and on JDK 25 with compact object headers.
#
# Usage: run.sh [xdm|full] [phoxtrot-xdm jar]
#
#   xdm   (default) measures only the phoxtrot tree and prints one table with a column per JVM
#         and the instance sizes of the node classes
#   full  runs the complete comparison (phoxtrot, MicroDOM, Saxon) once per JVM
#
# The second argument is a phoxtrot-xdm jar (e.g. target/phoxtrot-xdm-0.1.0-SNAPSHOT.jar of a
# checkout that was only packaged, not installed). It is put first on the class path, before the
# jar of the local Maven repository, so a branch can be measured without "mvn install".
# Must be called from the module directory, as the document paths are relative to it.

set -e

MODE=${1:-xdm}
ENGINE_JAR=$2

mvn -o -q clean package
CP=target/classes:$(cat target/classpath.txt)
if [ -n "$ENGINE_JAR" ]; then
  CP=$ENGINE_JAR:$CP
fi

JDK21=$(/usr/libexec/java_home -v 21)
JDK25=$(/usr/libexec/java_home -v 25)
JVM_ARGS="-Xmx4g -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading -Djol.magicFieldOffset=true"

if [ "$MODE" = "full" ]; then
  "$JDK21/bin/java" $JVM_ARGS -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark
  echo
  "$JDK25/bin/java" $JVM_ARGS -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark
  echo
  "$JDK25/bin/java" $JVM_ARGS -XX:+UseCompactObjectHeaders -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark
  exit 0
fi

OUT=$(mktemp -d -t phoxtrot-membench)
"$JDK21/bin/java" $JVM_ARGS -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark xdm > "$OUT/1.tsv"
"$JDK25/bin/java" $JVM_ARGS -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark xdm > "$OUT/2.tsv"
"$JDK25/bin/java" $JVM_ARGS -XX:+UseCompactObjectHeaders -cp "$CP" com.helger.phoxtrot.xpath.membench.MainMemoryBenchmark xdm > "$OUT/3.tsv"

# Join the three runs: one row per document, one size column per run (MiB with two decimals)
LC_ALL=C awk -F'\t' '
  FNR == 1 { nRun++ }
  $1 == "VM" { aVM[nRun] = "JDK " $2 ($3 == "compact" ? ", compact headers" : "") }
  $1 == "CLASS" { aClass[nRun] = $0 }
  $1 == "DOC" {
    if (nRun == 1) { aOrder[++nDocs] = $2; aCounts[$2] = $3 " | " $4 " | " $5 }
    aSize[$2, nRun] = $6
  }
  END {
    printf "| Document | Elements | Attributes | Text nodes"
    for (r = 1; r <= nRun; r++) printf " | %s", aVM[r]
    printf " |\n|---|--:|--:|--:"
    for (r = 1; r <= nRun; r++) printf "|--:"
    printf "|\n"
    for (d = 1; d <= nDocs; d++) {
      printf "| %s | %s", aOrder[d], aCounts[aOrder[d]]
      for (r = 1; r <= nRun; r++) printf " | %.2f MB", aSize[aOrder[d], r] / 1048576
      printf " |\n"
    }
    printf "\nInstance sizes in bytes\n\n"
    for (r = 1; r <= nRun; r++) {
      n = split(aClass[r], aParts, "\t")
      sLine = ""
      for (i = 2; i <= n; i++) sLine = sLine (i > 2 ? ", " : "") aParts[i]
      printf "* %s: %s\n", aVM[r], sLine
    }
  }
' "$OUT/1.tsv" "$OUT/2.tsv" "$OUT/3.tsv"
rm -r "$OUT"
