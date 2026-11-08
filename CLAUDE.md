# ph-xslt - notes for Claude

Pure Java XPath 3.1 / XSLT 3.0 engine, a port of the Go project at `../../git-thirdparty/go-xslt` (read its `CLAUDE.md`
for the architecture). Roadmap: `docs/PLAN.md`.

- Build: `mvn install` (JDK 21; local runs and gates use JDK 21 only, JDK 25 is left to the CI). Maven parent is
  `ph-parent-pom`, ph-commons is the base library.
- The W3C suite runs are integration tests (`*IT`, failsafe) and only run with `-Pconformance`:
  `mvn install -Pconformance` is the full gate; `-Dit.test=<IT class>` picks one. XSLT30 alone takes about 12 minutes.
- Benchmarks: `xslt-benchmark` (JMH, standalone like `xdm-memory-benchmark`: own POM, not in the reactor, never
  installed). Build with `mvn -o -q clean package -f xslt-benchmark/pom.xml` (always `clean`: an incremental compile can
  drop `META-INF/BenchmarkList`); it uses the engine jars of `~/.m2`, `xslt-benchmark/compare.sh <base> <new>` builds
  the engine of two commits in temporary worktrees instead. Inputs are generated (`BenchData`, fixed seed) and every
  workload checks its result once against a value computed in Java; a new workload needs such a check, and its case ID
  in both `EWorkloadGroup.getAllCaseIDs` and the `@Param` list of its JMH class. Reports: `docs/benchmarks/`.
- W3C suites live outside the repo in `../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`; never copy them in.
- Copyright header is "Philip Helger" only. Tim Riep appears in `NOTICE` and `README.md` only.
- Never read Saxon sources (MPL); work from the W3C specs and the Go code.
- The official W3C specifications always take precedence over the Go implementation: where go-xslt deviates from a spec,
  follow the spec (and say so), do not port the deviation.
- Traversals over input-controlled structure are iterative or depth-limited, never unbounded recursion.
- Nodes are `IXdmNode` (own model, built from JDK SAX; one implementation class per node kind below the sealed
  `AbstractXdmNode`). Do not switch to DOM or IMicroDom as the model.
- Document order: `getOrder ()`/`getEndOrder ()` (end = last node of the subtree, attributes included); a document node
  has lazily built name indexes (elements, attributes, and the kept child elements by name of nodes with many children)
  used by `AxisNavigator.collectNamedDescendants`/`collectNamedSubtreeAttributes`/`collectNamedChildren`. Every
  structural change to a numbered tree must go through `appendChild`/`removeChild`/`addAttribute`, which drop numbering
  and indexes. `XdmTreeBuilder` and `XdmStreamReader` number nodes as they create them (`internalAppendNumbered`,
  `internalStamp`), with the numbers `assignDocumentOrder` would give; a change from a wrapping handler while building
  drops that numbering and `XdmTreeBuilder.endDocument` numbers the finished tree.
- Hosts that evaluate their own XPath against a stylesheet's declarations (Schematron) use
  `XsltExecutable.createStaticContext`/`createEvaluation`, never `Engine` directly; the start-up of a run lives in
  `XsltTransformer.initGlobalContextItem` and is shared by both. `CompiledXPath.evaluate` uses its own static context.
- XML name characters (5th edition): only `XmlNames` (`xpath-xdm`); do not add another copy of the tables.
- `XdmMap` is a persistent hash trie and `XdmArray.append` shares a backing array; both stay immutable from the outside.
  Iterate with `entries ()`/`members ()` (views); `getAllEntries ()`/`getAllMembers ()` copy.
- `XdmSequence` keeps a single item in a field without an array (an item array has 0 or at least 2 entries).
  `XdmSequence.Builder` hands an exactly full buffer to the built sequence and copies it on the next change;
  `XdmSequence.ListBuilder` is the `List` to pass to code that fills one (axis navigation) instead of an `ArrayList`
  copied afterwards. `IExpr.LiteralExpr.sequence ()` is the sequence of the literal, built once.
- QT3: `mvn verify -Pconformance -pl xpath-conformance -am` (`-Dqt3.only=<set>` for one test set). Every failing
  case must be named in `xpath-conformance/docs/accepted-failures/qt3.md` with a reason, or `QT3IT` fails. Compare
  fail lists (`xpath-conformance/target/qt3-fails.txt`) by name, never by count. Only dependency skips (spec, normalisation
  form) are skips; a case the harness cannot set up (missing environment, unreadable test file, no assertion, unknown
  assertion kind, a `param` that does not evaluate or match its `as`) fails as `harness:<reason>`. `error` passes on
  any error as FOTS says; `-Dqt3.strictCode=true` makes a wrong code fail, and the mismatches are listed in
  `target/qt3-code-mismatches.txt` either way.
- All harnesses: `not` passes only if its child was evaluated and is false (an error result, an expression that does
  not evaluate or a skipped child fail it); an accepted-failures file accepts only backticked names in a heading line or
  on a line that is nothing but a list of backticked names, never a name in prose.
- Keep `README.md` in sync like a wiki page: status figures, the module table and the setup sections are updated in the
  same change that makes them wrong.
- XSD: the native validator (`xsd-core`) does XSD 1.0 and 1.1; `XsdSchemaProvider` (schema-aware XPath/XSLT) compiles as
  1.1. `XsdNativeIT` gates both versions (`xsd-conformance/target/xsd-native-fails.txt`, `xsd-native11-fails.txt`).
  A run claims the version tokens of `XsdSuiteRules.getClaimedTokens` (the version, and for 1.1 the CTA XPath subset of
  the validator: the native one claims `full-xpath-in-CTA`); a queried schema test whose compilation crashes counts as
  queried, not as fail. Identity-constraint selectors and fields are walked on the tree by `XsdIdentityPath` (parsed at
  compile time); the compiled XPath of the constraint is only evaluated for a form it does not parse.
- Streaming (plan: `docs/PLAN.md`, "Streaming"): `-Dxslt30.streaming=true` claims it in the XSLT30 harness (out dir
  `xslt-conformance/target/streaming`, accepted file `xslt30-streaming.md`); with `-Dxslt30.schemaAware=true` as well
  it writes to `target/schema-aware-streaming` and is no gate (`checkAccepted` off by default). The engine switches are
  `XsltCompileOptions.setStreamingClaim` (default off; `system-property('xsl:supports-streaming')`, the analysis runs
  and records `StreamVerdicts`, streamed execution and the run-time streaming rules), `setEnforceStreamability`
  (XTSE3430/XTSE3155 binding: `com.helger.xslt.stream.XsltStreamability` checks the stylesheet tree at the end of
  compilation, reading the operands from the `StreamabilityIndex` the compiler fills only then) and
  `XsltTransformRequest.setSourceStreamed` (declaration only) / `setStreamedSourceUri` (read the principal source as a
  stream). The default run must keep claiming no streaming. Streamed execution: `com.helger.xpath.xdm.XdmStreamReader`
  (StAX, nodes numbered as created, `drop`/`skipSubtree`), `IResourceResolver.openDocumentStream`, and in
  `com.helger.xslt.stream` `StreamPlanner` (shape), `StreamConsumer` (the streamed branch of for-each,
  apply-templates, copy-of, iterate, for-each-group, fork; created with an `IStreamHandlerFactory`), `StreamRecordPath`
  (child steps, motionless boolean predicates), `StreamedRun` (the driver: several paths in one pass, records retained
  by a handler with their ancestors until `release`, `startPull`/`nextRecord` for a streamed `xsl:merge-source`);
  accumulators via `AccumulatorExtension.startStreamed`. A streamed document has `XdmDocumentNode.isStreamed ()`:
  never answer a whole-document question from it. Tests tell streamed from buffered runs with
  `StreamedRun.getStreamedCount (<instruction>)`. The memory test is `StreamedMemoryTest` (forked JVM, `-Xmx32m`,
  `-Dstreamed.memory.records=<n>`, one stylesheet per streamed construct).
- Output to a stream (streaming plan S5): `XsltExecutable.transform (request, OutputStream|Writer)` writes the principal
  result instead of returning it (same characters as `principalSerialized ()`). Where the unnamed xsl:output allows it
  (`XsltTransformer._createDrain`: not schema-aware, explicit method, no xsl:result-document anywhere, a tree,
  `XdmIncrementalSerializer.isSupported`) the result is written while it is built: `com.helger.xslt.runtime.ResultDrain`
  tracks the elements under construction (each with its own `ContentOutput`) and writes and detaches what can no longer
  change. Element constructors must create their content output with `ContentOutput.createFor` (pass `false` when the
  instruction changes the content after adding it, as inherit-namespaces="no" and validation do); a temporary output
  (`BufferedOutput`, variables) is never drained. `ResultDrain.getDrainedRunCount ()` tells tests which path ran;
  `-Dxslt30.writeTo=true` reruns every XSLT30 case through the writer and fails it with "harness" on any difference.
- Early exit (XPath 3.1 section 2.3.4): where only existence or the effective boolean value matters (`fn:exists`,
  `fn:empty`, `fn:boolean`, `fn:not` resolved to their built-in implementation, `and`/`or`, `if`, `satisfies`,
  predicates, `XPathEvaluator.evaluateEffectiveBooleanValue`), paths ending in an axis step and filter expressions
  stop at the first decisive item; only the last predicate of the last step is short-circuited. Any other shape falls
  back to the complete evaluation. `and`/`or` operands stay left to right. A path whose last child/descendant step
  names an element the document's index does not contain is empty without evaluating its prefix
  (`XPathEvaluator._isEmptyByNameIndex`, also in the complete evaluation), but only for prefixes that cannot raise an
  error (axis steps without predicates, unions of such paths, a node as context item in a tree with a document root).
- Focus-independent predicates: a predicate that `ExprWalker.isFocusIndependent` accepts (no context item, no path
  other than `$x/a/@b`, no function items, only the listed built-in functions, each resolving to the implementation of
  `FunctionLibrary.getDefault ()`) is evaluated once per step or filter application with two or more candidates
  (`XPathEvaluator._applyPredicate`, `_findPassing`): a number selects that position, anything else all or none.
  The list fails closed; add a function to it only if it reads neither the focus nor anything that changes within a run.
- xsl:number (`NumberInstruction`) counts from the document's name index where the count and from patterns name the
  elements they can match (`XsltPattern.getFinalNames`; patterns of single exact `child::` name steps and the default
  count pattern of an element are counted by binary search on the document order without testing): level="any" over
  the whole document, single/multiple for parents with more than 32 children (`getChildElementsByName`). Unnumbered,
  streamed and parentless trees and other patterns walk the tree. `NumberInstruction.getNameIndexCountCount ()` tells
  tests which path ran.
- Node test names hold interned strings (`NodeTest` constructor, `XdmQName.getInterned`), like the names the JDK
  parsers report and the tree builders keep, so a matching name test is decided by identity.
- `XPathOperators` (`xpath-core`, package `eval`) is the one place for the semantics of comparisons, arithmetic,
  cast/castable and the static function call with argument conversion; `XPathEvaluator` calls it. Change these
  operators there, never in a copy (it is the public API for generated code such as ph-sch2java's).
- Static calls resolve through the `FunctionCallSite` of their `IExpr.FunctionCallExpr` (cached per library instance
  and `FunctionLibrary.getModificationStamp`, the sum of the change counters of the parent chain). Every new
  `FunctionLibrary` method that changes what `getFunction` returns must increment `m_nModCount` under the write lock
  and call `_checkWritable` first.
- `FunctionLibrary.getDefault ()` is shared by every static context and compiled stylesheet and is read-only (changes
  throw); stylesheets, static expressions and `xsl:evaluate` register into a `createChild ()` of it, never call
  `createDefault ()` per compilation.
- Patterns: `XsltPattern.canMatch` rejects a node that the last step of no union path can select (it must stay a pure
  pre-test of what the path search rejects first); `getFinalNames` gives the exact element/attribute names when every
  alternative ends in one, and `Engine._buildKeyIndex` then tests only those nodes from the document's name index
  instead of walking the tree (`Engine.getKeyIndexFromNamesCount ()` tells tests which path ran).
- Streamability (XSLT 3.0 section 19): `com.helger.xpath.stream.XPathStreamability` classifies expressions and
  patterns; the XSLT layer supplies its knowledge through `StreamContext`. It fails closed: an unknown construct is
  roaming/free-ranging, never "streamable".
- When merging parallel work, check for the same mechanism implemented twice in different places (a clean git merge
  does not catch it); it happened with the XPath 1.0 compatibility conversions.

