# xslt-benchmark

JMH benchmarks and profiling workloads of the ph-xslt engines: parsing, XPath, XSLT, XSD validation, streaming and
compilation. The module is standalone like `xdm-memory-benchmark`: its own POM, not part of the reactor build, never
installed or deployed. It uses the engine jars `0.1.0-SNAPSHOT` from the local Maven repository, or the jars of a given
commit (see [Comparing two commits](#comparing-two-commits)).

All input is generated in code from a fixed seed (`BenchData`): an indented invoice-like document with a given number of
lines and ten elements per line (`line`, `item`, `sku`, `qty`, `price`, `tax`, `note`, `period`, `start`, `end`), so
1,000 lines are about 10,000 elements and 300 KB. Nothing large is committed; the stylesheets and
schemas are small resources in `src/main/resources/com/helger/xpath/bench/`.

Every workload is run once in the preparation and its result is compared with a result computed in Java from the
generated lines (counts, sums, the expected output text or its length), so that a later change cannot silently make
it do less. `MainProfile verify` runs these checks for every case.

## Workloads

| Group (JMH class) | Case | Sizes (lines) | What is measured |
|---|---|---|---|
| `parse` (`ParseBenchmark`) | `parse` | 1,000, 10,000 | `XdmParser` from UTF-8 bytes in memory: SAX, tree builder, numbering while building |
| `xpath` (`XPathBenchmark`) | `child` | 1,000, 10,000 | `count(/invoice/lines/line/item)` |
| | `descendant` | | `count(//price) + count(//line//start)` |
| | `positional` | | 200 numeric predicates `/invoice/lines/line[k]`, `position() mod 10`, `last()` |
| | `predicate` | | two chained value predicates, one with a cast |
| | `exists` | | `exists`/`empty`/`not`/`boolean` on early hits, missing names (name index) and a full scan |
| | `strings` | | `concat`, `upper-case`, `substring`, `normalize-space` per line |
| | `regex` | | `matches` and `replace` per line |
| | `arithmetic` | | decimal, integer and double arithmetic and comparisons per line |
| | `flwor` | | `for`, `let`, `some`/`every`, `distinct-values`, `sort`, date subtraction |
| | `maps` | | `map:merge` with combine, `array:join`, `fold-left` and `array:fold-left` with inline functions |
| | `rules` | | Schematron-like: select the lines, then 10 assertions per line as effective boolean value |
| `xslt` (`XsltBenchmark`) | `identity` | 1,000, 10,000 | `on-no-match="shallow-copy"`, serialised as XML (the output equals the input) |
| | `dispatch` | | `apply-templates` over four modes, value and structure predicates, priorities |
| | `keys` | | three `xsl:key`s, `key()` per category, per line and through a parent step |
| | `grouping` | | `xsl:for-each-group` with `group-by` (sorted), `group-adjacent` and a computed key |
| | `sort` | | `xsl:sort` of all lines by a decimal key descending, then a string key |
| | `iterate` | | `xsl:iterate` with an `array(xs:decimal)` parameter that grows by one member per line |
| | `recursion` | | divide-and-conquer `xsl:function` over all prices, recursive named template per line |
| | `serialize-xml` | | result tree with AVTs and `concat`, `method="xml"` |
| | `serialize-html` | | HTML 5 table, `method="html"` |
| | `serialize-json` | | array of maps, `method="json"` |
| `xslt` (`XsltNumberBenchmark`) | `number` | 500, 1,000, 2,000 | `xsl:number` with `level="single"`, `"multiple"` and `"any"` per line; smaller sizes because the time grows with the square of the lines |
| `xsd` (`XsdBenchmark`) | `1.0`, `1.1` | 1,000, 10,000 | `XsdSchema.validateText` (parse plus assessment) against the invoice schema; 1.1 adds an assertion per line, an assertion facet per price and a type alternative per tax element. The preparation also checks the error count for a document with faults |
| `streaming` (`StreamingBenchmark`) | `streamed`, `buffered` | 10,000, 100,000 | `xsl:source-document` with `xsl:iterate` over all lines from bytes in memory; streamed with the streaming claim and enforced streamability (checked: one streamed run), buffered without |
| `compile` (`CompileBenchmark`) | `xslt`, `xpath` | - | compiling the 12 stylesheets of the XSLT and streaming workloads; compiling the 21 expressions of the XPath workloads and the rule set |

Not measured: generating and parsing the input (except in `parse`, `xsd` and `streaming`), compiling (except in
`compile`). The XPath and XSLT workloads parse their document once per trial, so the name indexes a document builds on
first use are reused by later runs; key indexes are built per transformation.

## Build

JDK 21, offline, without touching the reactor:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -o -q clean package -f xslt-benchmark/pom.xml
```

Always build with `clean`: an incremental compile can drop the JMH-generated `META-INF/BenchmarkList` (JMH then finds
no benchmarks). The build writes the class path to `target/classpath.txt`. To benchmark changes of the engine, install
the engine first (`mvn install -DskipTests` in the root), or use `compare.sh`, which does not install anything.

## Run

From `xslt-benchmark` (JMH prints with the default locale; `-Duser.language=en` gives decimal points):

```bash
CP=target/classes:$(cat target/classpath.txt)
J="$JAVA_HOME/bin/java -Duser.language=en -Duser.country=US -cp $CP"

# All benchmarks (about 9 minutes), with the allocation per operation, results as JSON
$J org.openjdk.jmh.Main -prof gc -rf json -rff target/result.json

# One group, or one case and size
$J org.openjdk.jmh.Main XsltBenchmark
$J org.openjdk.jmh.Main -p m_sCase=keys,grouping -p m_nSize=10000 XsltBenchmark

# Shorter runs for a quick look
$J org.openjdk.jmh.Main -wi 1 -i 3 -r 500ms XPathBenchmark

# List the benchmarks
$J org.openjdk.jmh.Main -l
```

The defaults are 3 warm-up and 5 measurement iterations of 1 second in one fork with `-Xmx3g -Xss8m`. With `-prof gc`
the line `gc.alloc.rate.norm` is the allocation per operation in bytes (for `parse`: per parsed document); no JOL
agent is needed. For the retained heap of a tree see `xdm-memory-benchmark`.

Check all workloads without timing (default 1,000 lines):

```bash
$J com.helger.xpath.bench.MainProfile verify
$J com.helger.xpath.bench.MainProfile verify 10000
```

## Profile

`MainProfile <group> <case> [lines] [seconds]` runs one workload repeatedly (default 10,000 lines for 20 seconds; the
first fifth is a warm-up) and prints the mean time per run. With Java Flight Recorder:

```bash
$JAVA_HOME/bin/java -Xmx3g -Xss8m -cp $CP \
  -XX:StartFlightRecording=filename=target/xslt-keys.jfr,settings=profile \
  -XX:FlightRecorderOptions:stackdepth=256 -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
  com.helger.xpath.bench.MainProfile xslt keys 10000 20

jfr view hot-methods target/xslt-keys.jfr
jfr view allocation-by-class target/xslt-keys.jfr
jfr print --events jdk.ExecutionSample --stack-depth 256 target/xslt-keys.jfr > target/xslt-keys.txt
```

`hot-methods` gives the self time (top frame); for inclusive shares aggregate the stacks of `jfr print`. Stylesheets are
compiled on a thread `ph-xslt-compile` and transformations run on a thread `ph-xslt-transform` (a new one per
transformation), not on `main`: include these threads when filtering samples. In the baseline JFR took almost no
samples of the streamed transformation (71 in 12 seconds); the cause was not analysed.

## Comparing two commits

```bash
./compare.sh <base-commit> <new-commit> [JMH arguments...]
./compare.sh main bench -p m_nSize=10000 'XPathBenchmark|XsltBenchmark'
```

For each commit the script creates a detached `git worktree` in a temporary directory, builds the engine modules there
with `mvn -o -q package -DskipTests` and runs JMH with those jars in place of the `0.1.0-SNAPSHOT` jars of `~/.m2`;
nothing is installed. It prints the scores side by side with the ratio new/base and removes the worktrees at the end
(`BENCH_KEEP=1` keeps them, `BENCH_WORK_DIR=<dir>` sets the directory and reuses worktrees found there). The benchmark
classes are compiled once from the current checkout, so both commits need the engine API they use. Run both commits in
the same session: absolute times depend on the machine and its load, compare ratios.

## Reports

Absolute times depend on the machine and vary between runs; compare ratios measured in the same session.

| Report | Content |
|---|---|
| [2026-11-06-baseline.md](../docs/benchmarks/2026-11-06-baseline.md) | Baseline of `main` (`6b8e1c2`) with the hot spots per workload |
