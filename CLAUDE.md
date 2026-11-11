# phoxtrot - notes for Claude

Pure Java XPath 3.1 / XSLT 3.0 engine, a port of the Go project at `../../git-thirdparty/go-xslt` (read its `CLAUDE.md`
for the architecture). Roadmap: `docs/PLAN.md`.

- Build: `mvn install` (JDK 21; local runs and gates use JDK 21 only, JDK 25 is left to the CI). Maven parent is
  `ph-parent-pom`, ph-commons is the base library.
- The W3C suite runs are integration tests (`*IT`, failsafe) and only run with `-Pconformance`:
  `mvn install -Pconformance` is the full gate; `-Dit.test=<IT class>` picks one. XSLT30 alone takes about 12 minutes.
- Names: the project is `phoxtrot` (always lowercase), groupId `com.helger.phoxtrot`, every module directory equals its
  artifactId (`phoxtrot-xdm`, `phoxtrot-xpath`, `phoxtrot-xslt`, `phoxtrot-xsd`, `phoxtrot-*-conformance`, the two
  benchmarks); packages `com.helger.phoxtrot.xdm` (`phoxtrot-xdm`), `.xpath` (`phoxtrot-xpath`, the QT3 harness and the
  benchmarks), `.xslt` and `.xsd`.
- Benchmarks: `phoxtrot-xslt-benchmark` (JMH, standalone like `phoxtrot-xdm-memory-benchmark`: own POM, not in the
  reactor, never installed). Build with `mvn -o -q clean package -f phoxtrot-xslt-benchmark/pom.xml` (always `clean`: an
  incremental compile can drop `META-INF/BenchmarkList`); it uses the engine jars of `~/.m2`,
  `phoxtrot-xslt-benchmark/compare.sh <base> <new>` builds the engine of two commits in temporary worktrees instead.
  compare.sh only works for commits from the package rename to `com.helger.phoxtrot` on (the benchmark classes of the
  checkout cannot link against older engines); it rejects older commits before building anything. Inputs are
  generated (`BenchData`, fixed seed) and every workload checks its result once against a value computed in Java; a new
  workload needs such a check, and its case ID in both `EWorkloadGroup.getAllCaseIDs` and the `@Param` list of its JMH
  class. Reports: `docs/benchmarks/` (dated reports before the rename use the old module and package names).
- Version and build time: `PhoxtrotVersion.BUILD_VERSION`/`BUILD_TIMESTAMP` (`phoxtrot-xdm`), read from
  `phoxtrot-version.properties`, which Maven filters (`phoxtrot-xdm/pom.xml` filters only `*.properties` resources).
- W3C suites live outside the repo in `../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`; never copy them in.
- Copyright header is "Philip Helger" only. Tim Riep appears in `NOTICE` and `README.md` only.
- Never read Saxon sources (MPL); work from the W3C specs and the Go code.
- The official W3C specifications always take precedence over the Go implementation: where go-xslt deviates from a spec,
  follow the spec (and say so), do not port the deviation.
- Traversals over input-controlled structure are iterative or depth-limited, never unbounded recursion.
- Nodes are `IXdmNode` (own model, built from JDK SAX; one implementation class per node kind below the sealed
  `AbstractXdmNode`). Do not switch to DOM or IMicroDom as the model.
- Node locations: element, comment and PI have a nested `Located` subclass with line and column fields, created by
  the tree builders when they record locations (`XdmParser.setRecordLocations`, default off) and by
  `IXdmNode.createElement (name, line, column)`; any other node keeps a set location in its `Details` holder.
  Attribute and text nodes have no location of their own: with a parent they report the parent's (`getLine`/
  `getColumn`), only a parentless one reports what `setLocation` or the `createAttribute`/`createText` factories with
  a location put in its holder. An element copy that must keep its original's location uses `createElement` with the
  location, not `setLocation` (one holder per node); attribute and text copies added to a copy take no location.
  One rule: every reader defaults to no locations (`XdmParser`, `XdmTreeBuilder`, `XdmStreamReader.open` without
  `bRecordLocations`, every `IResourceResolver.resolveDocument`/`openDocumentStream`, `fn:parse-xml`, JAXP sources);
  only stylesheet modules, packages, schema documents and XSD instance validation are parsed with them (error
  locations, `xsl:try`, XSD error lines), and those parse sites set it on explicitly. Stylesheet modules read
  through a resolver (include/import, packages by location, `fn:transform` locations) go through
  `IResourceResolver.resolveStylesheetModule`, never `resolveDocument`: `FileResourceResolver`, `JaxpResourceResolver`
  and the XSLT30/QT3 harness resolvers override it, and its default calls `resolveDocument` inside
  `XdmParser.getWithRecordLocations`, which makes locations the default of every `XdmParser`/`XdmTreeBuilder`
  created on the thread meanwhile (`isRecordLocationsByDefault`; an explicit `setRecordLocations` wins), so a user
  resolver that implements only `resolveDocument` still yields located modules unless it caches or switches them
  off. A wrapping resolver forwards `resolveStylesheetModule`. A new parse site states its setting explicitly.
- Document order: `getOrder ()`/`getEndOrder ()` (end = last node of the subtree, attributes included); a document node
  has lazily built name indexes (elements, attributes, and the kept child elements by name of nodes with many children)
  used by `AxisNavigator.collectNamedDescendants`/`collectNamedSubtreeAttributes`/`collectNamedChildren`. Every
  structural change to a numbered tree must go through `appendChild`/`removeChild`/`addAttribute`, which drop numbering
  and indexes. `XdmTreeBuilder` and `XdmStreamReader` number nodes as they create them (`internalAppendNumbered`,
  `internalStamp`), with the numbers `assignDocumentOrder` would give; a change from a wrapping handler while building
  drops that numbering and `XdmTreeBuilder.endDocument` numbers the finished tree. The builder also records the
  maximum element depth with its numbering (`XdmDocumentNode.getMaxElementDepth`, -1 once the numbering is gone), which
  `XsdInstanceValidator` uses instead of walking the tree. Children are a plain array with a count in
  `AbstractXdmParentNode` (trimmed by the builder at `endElement`); indentation text (a line end and spaces or tabs) is
  one shared string per document.
- Use `hasDeclaredNamespaces ()`/`getDeclaredNamespaceUri (prefix)` where only a check or a lookup is needed:
  `getDeclaredNamespaces ()` returns a copy, also for an element without declarations.
- Threads: `compile` and `transform` run on the thread `EThreading` selects (`XsltCompileOptions.setThreading`,
  `XsltTransformRequest.setThreading`; JAXP: factory attribute `ATTR_THREADING`, `PhoxtrotTransformer.setThreading`),
  through `LargeStackRunner` (both in `phoxtrot-xpath`, package `com.helger.phoxtrot.xpath`, so plain XPath callers
  use them too): by default a new thread with a 2 GB stack per call,
  or a shared pool of such threads, or the calling thread (its stack then limits the nesting). A call on one of the
  worker threads (nested `fn:transform`) runs in place; a stack overflow is XPDY0130 in every mode. Plain XPath has
  a run-wide step limit (`DynamicContext.setMaxSteps`, unlimited by default), counted in `checkInterrupt` and
  `enterCall`; XSLT keeps its own (`ExecutionBudget`, `XsltTransformRequest.setMaxSteps`) and leaves this one
  unlimited.
- Public API of `phoxtrot-xpath` (source compatible within 0.x, listed in README "XPath API" and in
  `com.helger.phoxtrot.xpath/package-info.java`): `XPathCompiler`, `CompiledXPath`, `XPathException`, `EThreading`,
  `LargeStackRunner`, `XPathStreamabilityAnalyzer` (with `XPathStreamabilityResult`, `XPathStreamabilityReason`,
  `EStreamabilityVerdict`, `EStreamabilityConstruct`), `StaticContext`, `DynamicContext`, `IResourceResolver`,
  `ResourceUri`, `FunctionLibrary`, `IXPathFunction`, `SeqType`, `ItemType`, `EOccurrence`, `EPosture`, `ESweep`,
  `PostureSweep`, `EStreamFunctionCategory`, the `value` types and `XPathOperators`. A change to one of them goes into the
  release notes; keep both lists in sync when the set changes.
- Error positions: the parser and lexer set `XPathException.setExpressionPosition` (offset, line, column in the
  expression) on every static error they raise through `_error`/`_syntax`/the lexer's error helper; a new static
  error must go through them. `UserFunction._invoke` attaches the innermost `xsl:function` to errors of its body
  (`XsltException.withFunctionFrame`; a plain `XPathException` is wrapped unlocated, so the caller still locates it,
  and the wrapping constructor copies the frame).
- Hosts that evaluate their own XPath against a stylesheet's declarations (Schematron) use
  `XsltExecutable.createStaticContext`/`createEvaluation`, never `Engine` directly; the start-up of a run lives in
  `XsltTransformer.initGlobalContextItem` and is shared by both. `CompiledXPath.evaluate` uses its own static context.
- Messages: `xsl:message` and failed `xsl:assert` go only through `Engine.addMessage`, which records an `XsltMessage`
  and calls the request's `IXsltMessageListener` at once (on the transform thread); mode warnings
  (`warning-on-no-match`/`-multiple-match`) go only to the listener (`Engine.addWarning`). `XsltTransformer.run`/
  `_runTo` attach the run's messages to the thrown error (`XsltException.getCollectedMessages`, a plain
  `XPathException` is wrapped; outer run before a nested `fn:transform`, which gets the caller's listener unless
  `enable-messages` is false). The compiler produces no warnings, so `XsltCompileOptions` has no listener.
- Tracing: `XsltTransformRequest.setTraceListener` (`IXsltTraceListener`, also JAXP `ATTR_TRACE_LISTENER`) is called
  only from `Engine._invokeRule`/`callTemplate` (template rules, named templates), `UserFunction._invoke` (function
  bodies), `XsltTransformer._runTraced` (start/end) and, if `isTraceInstructions ()` at engine creation, from
  `SequenceConstructor`/`ConditionalContent` (instructions). Every leave is in a `finally`. The hot paths test one field
  (`Engine.m_aTraceListener`, `getInstructionTraceListener ()`) that is null by default; keep it that way. Nested
  `fn:transform` passes the listener on.
- JAXP adapter (`phoxtrot-xslt`, package `com.helger.phoxtrot.xslt.jaxp`): `PhoxtrotTransformerFactory`,
  `PhoxtrotTemplates` (wraps an `XsltExecutable`, thread-safe), `PhoxtrotTransformer`. Never register it in
  `META-INF/services/javax.xml.transform.TransformerFactory` (it would replace the JDK transformer of every
  application on the class path). Sources and DOM conversion go through `JaxpSources`, resources (include, import,
  `document()`, `unparsed-text()`) through `JaxpResourceResolver` (URIResolver first, called as JAXP specifies with the
  href as written and the base URI, `""` without one, from the `ResourceUri` every `IResourceResolver` method gets;
  then direct access to the absolute URI as `ACCESS_EXTERNAL_STYLESHEET` allows, a `jar:` URI only if its nested URI is
  allowed too; a refusing URIResolver gives XTSE0165 for modules (`StylesheetLoader`), FODC0002 for documents and
  FOUT1170 for text). Every `XdmParser` of the adapter is created from `JaxpParserSettings` (external entities,
  `ACCESS_EXTERNAL_DTD` through `XdmParser.setAccessExternalDtd`, the entity resolver of the factory or transformer),
  also for stylesheets and sources; a new parse site in the adapter uses it. JAXP output properties become
  `XsltTransformRequest.outputOverrides ()`, applied by `XsltTransformer._withOverrides` through
  `OutputDefinition.getWithOverrides` to the definition the principal result is serialized with (also for the
  drain decision). `-Dxslt30.jaxp=true` reruns every XSLT30 case of a shape JAXP can express through the adapter
  and fails it with "harness" on any difference. `PhoxtrotTransformer.transform` sets the request's interrupt hook to
  the calling thread's interrupted state (sticky once the call ends interrupted), so a cancelled JAXP run on a worker
  thread stops too; compilation has no interrupt hook.
- W3C DOM bridge (`phoxtrot-xdm`, package `com.helger.phoxtrot.xdm.bridge`): `DomToXdm` feeds the DOM into
  `XdmTreeBuilder` as SAX events (numbered while built, shared names; `convertWithMapping` records the DOM node of
  every XDM node in a `DomNodeMapping`, around the builder events that flush buffered text), `XdmToDom` and
  `XdmToSax` write trees with namespace fixup (`NamespaceScope`). All three walk iteratively. `XdmDomView` is a
  read-only DOM over an XDM tree (`AbstractXdmDomNode` and one class per kind, `XdmDom*`): one DOM node per XDM node,
  kept per view in a map so DOM identity holds (`DomToXdm` relies on `==`); namespace declarations are `xmlns`
  attributes (over parentless namespace nodes); changes throw `NO_MODIFICATION_ALLOWED_ERR`; its walks are iterative
  too. The JDK DOM leaves entity reference nodes empty (`setExpandEntityReferences (false)`); tests that need their
  content use Xerces-J's DOM.
- XML name characters (5th edition): only `XmlNames` (`phoxtrot-xdm`), whose checks delegate to ph-commons
  `XmlNameChars` and which keeps the code point ranges for the regex classes; do not add another copy of the tables.
  XML whitespace (space, tab, CR, LF) checks, trimming and the XSD replace/collapse go through ph-commons
  `XmlWhitespace` (`XsdLexical` delegates to it); do not add private copies.
- `XdmMap` is a persistent hash trie and `XdmArray.append` shares a backing array; both stay immutable from the outside.
  Iterate with `entries ()`/`members ()` (views); `getAllEntries ()`/`getAllMembers ()` copy.
- `XdmSequence` keeps a single item in a field without an array (an item array has 0 or at least 2 entries).
  `XdmSequence.Builder` hands an exactly full buffer to the built sequence and copies it on the next change;
  `XdmSequence.ListBuilder` is the `List` to pass to code that fills one (axis navigation) instead of an `ArrayList`
  copied afterwards. `IExpr.LiteralExpr.sequence ()` is the sequence of the literal, built once.
- Static calls: `FunctionCallSite` also keeps the literal arguments converted for the resolved `FunctionDef` (by
  identity) and the XPath 1.0 compatibility mode; a failed conversion is not kept, so its error comes at the call.
- QT3: `mvn verify -Pconformance -pl phoxtrot-xpath-conformance -am` (`-Dqt3.only=<set>` for one test set). Every failing
  case must be named in `phoxtrot-xpath-conformance/docs/accepted-failures/qt3.md` with a reason, or `QT3IT` fails. Compare
  fail lists (`phoxtrot-xpath-conformance/target/qt3-fails.txt`) by name, never by count. Only dependency skips (spec, normalisation
  form) are skips; a case the harness cannot set up (missing environment, unreadable test file, no assertion, unknown
  assertion kind, a `param` that does not evaluate or match its `as`) fails as `harness:<reason>`. `error` passes on
  any error as FOTS says; `-Dqt3.strictCode=true` makes a wrong code fail, and the mismatches are listed in
  `target/qt3-code-mismatches.txt` either way.
- All harnesses: `not` passes only if its child was evaluated and is false (an error result, an expression that does
  not evaluate or a skipped child fail it); an accepted-failures file accepts only backticked names in a heading line or
  on a line that is nothing but a list of backticked names, never a name in prose.
- Keep `README.md` in sync like a wiki page: status figures, the module table and the setup sections are updated in the
  same change that makes them wrong.
- Schema documents are never read from the network. `IXdmSchemaProvider.compile (sources, bReadReferencedFiles)`
  controls the referenced documents (native: `XsdFileAccess.LOCAL_FILES`/`NONE`; Xerces-J and JDK:
  `LocalSchemaResourceResolver`, Xerces-J with secure processing); `XsltCompileOptions.setReadLocalSchemaFiles` feeds it,
  and `SchemaImportCompiler._readLocation` reads a `schema-location` through the options' resolver when there is one.
  `XsdValidationOptions.setFollowSchemaLocationHints` is off by default (an untrusted instance must not choose files);
  the W3C harnesses (`NativeXsdTestValidator`, `XsdBridgeDifferentialIT`) switch it on.
- XSD: the native validator (`phoxtrot-xsd`) does XSD 1.0 and 1.1; `XsdSchemaProvider` (schema-aware XPath/XSLT) compiles as
  1.1. `XsdNativeIT` gates both versions (`phoxtrot-xsd-conformance/target/xsd-native-fails.txt`, `xsd-native11-fails.txt`).
  A run claims the version tokens of `XsdSuiteRules.getClaimedTokens` (the version, and for 1.1 the CTA XPath subset of
  the validator: the native one claims `full-xpath-in-CTA`); a queried schema test whose compilation crashes counts as
  queried, not as fail. Identity-constraint selectors and fields are walked on the tree by `XsdIdentityPath` (parsed at
  compile time); the compiled XPath of the constraint is only evaluated for a form it does not parse.
- Streaming (plan: `docs/PLAN.md`, "Streaming"): `-Dxslt30.streaming=true` claims it in the XSLT30 harness (out dir
  `phoxtrot-xslt-conformance/target/streaming`, accepted file `xslt30-streaming.md`); with `-Dxslt30.schemaAware=true` as well
  it writes to `target/schema-aware-streaming` and is no gate (`checkAccepted` off by default). The engine switches are
  `XsltCompileOptions.setStreamingClaim` (default off; `system-property('xsl:supports-streaming')`, the analysis runs
  and records `StreamVerdicts`, streamed execution and the run-time streaming rules), `setEnforceStreamability`
  (XTSE3430/XTSE3155 binding: `com.helger.phoxtrot.xslt.stream.XsltStreamability` checks the stylesheet tree at the end of
  compilation, reading the operands from the `StreamabilityIndex` the compiler fills only then) and
  `XsltTransformRequest.setSourceStreamed` (declaration only) / `setStreamedSourceUri` (read the principal source as a
  stream). The default run must keep claiming no streaming. Streamed execution: `com.helger.phoxtrot.xdm.XdmStreamReader`
  (StAX, nodes numbered as created, `drop`/`skipSubtree`), `IResourceResolver.openDocumentStream`, and in
  `com.helger.phoxtrot.xslt.stream` `StreamPlanner` (shape), `StreamConsumer` (the streamed branch of for-each,
  apply-templates, copy-of, iterate, for-each-group, fork; created with an `IStreamHandlerFactory`), `StreamRecordPath`
  (child steps, motionless boolean predicates), `StreamedRun` (the driver: several paths in one pass, records retained
  by a handler with their ancestors until `release`, `startPull`/`nextRecord` for a streamed `xsl:merge-source`);
  accumulators via `AccumulatorExtension.startStreamed`. A streamed document has `XdmDocumentNode.isStreamed ()`:
  never answer a whole-document question from it. Tests tell streamed from buffered runs with
  `StreamedRun.getStreamedCount (<instruction>)`. The memory test is `StreamedMemoryTest` (forked JVM, `-Xmx32m`,
  `-Dstreamed.memory.records=<n>`, one stylesheet per streamed construct).
- EXSLT - Common: the core functions `exsl:node-set` and `exsl:object-type` (`com.helger.phoxtrot.xslt.fn.ExsltCommon`,
  registered with the static XSLT functions, so `function-available` sees them); no other EXSLT module and no
  `exsl:document`.
- Backwards compatibility (XSLT 3.0 sections 3.9 and 27.4, the XSLT 1.0 compatibility feature): the engine switch is
  `XsltCompileOptions.setBackwardsCompatibleClaim` (default on, `DEFAULT_BACKWARDS_COMPATIBLE_CLAIM`; the XSLT30 harness sets it explicitly in both directions; JAXP attribute
  `PhoxtrotTransformerFactory.ATTR_BACKWARDS_COMPATIBLE`; a nested `fn:transform` inherits it;
  `system-property('xsl:supports-backwards-compatibility')` follows it via `XsltFunctions.registerClaims`). An element
  whose effective version (`XslElement.getEffectiveVersion`, the version of xsl:output itself does not count) is below
  2.0 (`XslElement.isXslt10Version`: every value below 2.0 counts as 1.0, 2.0 up to 3.0 as 3.0) is processed with
  XSLT 1.0 behaviour only with the claim (`StylesheetCompiler`/`CompileContext.isXslt10Behaviour`). Without it,
  `CoreInstructions.compileInstruction` compiles such an instruction (its static errors stay static) and replaces it by
  one that raises XTDE0160 when evaluated; the outermost element counts like any other. With it,
  `CompileContext.createStaticContext` turns on XPath 1.0 compatibility mode and binds unknown functions outside the
  `fn` namespace to the fallback error function (`StaticContext.setUnknownFunctionErrorCode ("XTDE1425")`), except for
  text value templates; the XSLT rules are in `Avt` (first item), `NodeInstructions._compileValueOf`,
  `NumberInstruction` (NaN), `SortKeyDef`, `Engine` keys (`KeyDefinition.isXslt10`, string comparison), the XTSE0680
  check, `XsltTransformer._detectMethod` (no default xhtml) and `XsltStreamability._instr` (roaming and free-ranging).
  The XPath 1.0 operator semantics stay in `XPathOperators`/`AtomicComparator`/`SequenceTypeMatcher`. XSLT30 harness:
  `-Dxslt30.backwardsCompatible=true` (out dir `phoxtrot-xslt-conformance/target/backwards`, accepted file
  `xslt30-backwards.md`; together with another claim it writes to `<that dir>-backwards` and is no gate). The default
  run must keep claiming no backwards compatibility. QT3 sets XPath 1.0 compatibility mode per case for
  `feature xpath-1.0-compatibility` (`QT3CaseRunner`); there is no switch for it.
- Output to a stream (streaming plan S5): `XsltExecutable.transform (request, OutputStream|Writer)` writes the principal
  result instead of returning it (same characters as `principalSerialized ()`). Where the unnamed xsl:output allows it
  (`XsltTransformer._createDrain`: not schema-aware, explicit method, no xsl:result-document anywhere, a tree,
  `XdmIncrementalSerializer.isSupported`) the result is written while it is built: `com.helger.phoxtrot.xslt.runtime.ResultDrain`
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
- Loop-invariant calls (`CallMemo`): a static call of a built-in function that `ExprWalker.isDeterminedByContextItem`
  accepts (all calls inside resolving to the default implementation) and that has an argument with work is kept per
  run (`DynamicContext.getRunMemo`) with the static context and the identity tokens of the bindings of all variables
  it references (`DynamicContext.getVariableToken`); one that reads the focus only at the focus of the innermost
  `for`/`some`/`every` (`DynamicContext.get/setLoopFocus`, set by `_evalForExpr`/`_evalQuantifiedExpr`). Errors are
  not kept. The plan is kept in the `FunctionCallSite` (`getMemoPlan`) like the resolution. Add a function to the
  lists of `ExprWalker` only if it reads nothing that changes within a run.
- A single-node named child or descendant step whose result is a complete list of the name index
  (`AxisNavigator.getNamedIndexList`) takes the sequence of that list from the run memo
  (`XPathEvaluator._getIndexListSequence`) instead of copying it.
- xsl:number (`NumberInstruction`) counts from the document's name index where the count and from patterns name the
  elements they can match (`XsltPattern.getFinalNames`; patterns of single exact `child::` name steps and the default
  count pattern of an element are counted by binary search on the document order without testing): level="any" over
  the whole document, single/multiple for parents with more than 32 children (`getChildElementsByName`). Unnumbered,
  streamed and parentless trees and other patterns walk the tree. `NumberInstruction.getNameIndexCountCount ()` tells
  tests which path ran.
- Node test names hold interned strings (`NodeTest` constructor, `XdmQName.getInterned`), like the names the JDK
  parsers report and the tree builders keep, so a matching name test is decided by identity.
- `XPathOperators` (`phoxtrot-xpath`, package `eval`) is the one place for the semantics of comparisons, arithmetic,
  cast/castable and the static function call with argument conversion; `XPathEvaluator` calls it. Change these
  operators there, never in a copy (it is the public API for generated code such as ph-sch2java's).
- Static calls resolve through the `FunctionCallSite` of their `IExpr.FunctionCallExpr` (cached per library instance
  and `FunctionLibrary.getModificationStamp`, the sum of the change counters of the parent chain). Every new
  `FunctionLibrary` method that changes what `getFunction` returns must call `_checkWritable` and increment
  `m_nModCount` inside the write lock (so a change cannot race with `freeze`). A call site reuses an entry for a
  library that was frozen before the lookup without reading the stamp (`FunctionLibrary.freeze`, only after the
  parent).
- `FunctionLibrary.getDefault ()` is shared by every static context and compiled stylesheet and is frozen (changes
  throw); stylesheets, static expressions and `xsl:evaluate` register into a `createChild ()` of it, never call
  `createDefault ()` per compilation. `StylesheetCompiler._freezeFunctionLibraries` freezes the stylesheet, package
  and xsl:original libraries at the end of `compile`, and `EvaluateExtension` freezes its library: anything that
  registers functions must do so before (a registration afterwards throws).
- Patterns: `XsltPattern.canMatch` rejects a node that the last step of no union path can select (it must stay a pure
  pre-test of what the path search rejects first); `getFinalNames` gives the exact element/attribute names when every
  alternative ends in one, and `Engine._buildKeyIndex` then tests only those nodes from the document's name index
  instead of walking the tree (`Engine.getKeyIndexFromNamesCount ()` tells tests which path ran).
  `CompiledXsltPattern` opens a parenthesised union whose predicates pass `ExprWalker.isFocusFreeBoolean`
  (`(P1 | P2)[q]` is matched as `P1[q] | P2[q]`, the source, priority and alternatives unchanged), so it has union
  paths and final names. A union path of only `child`/`attribute` steps without `position`/`last`/`function-lookup`
  in its predicates is matched bottom-up by `PatternMatcher.DirectPath` (one candidate per step, no `PathSearch`); a
  predicate that returns a number hands the path back to the search.
- Template rules: `Mode.sortRules` also builds a rule index (modes with at least `Mode.MIN_INDEXED_RULES` rules, 2
  by measurement): the rule positions by the final element/attribute names of their patterns (`getFinalNames`) and
  the positions of all other rules. `Engine._findRule` merges the named candidates of the item with the other rules
  in rule order, so precedence, priority, xsl:next-match, xsl:apply-imports and the multiple-match check (which still
  scans the following rules of the full list) are unchanged. A rule added to a mode after `sortRules` would not be in
  the index; rules are only added at compile time.
- `XdmParser` keeps one JDK SAX parser per thread and per setting of external entities, taken out of the cache while
  in use (a nested parse on the same thread creates its own) and reset with its handlers cleared afterwards, so the
  cache holds no tree. Every per-parse setting must stay in `_parse` (set on each parse), the factory settings in
  `_createSAXParser`.
- Streamability (XSLT 3.0 section 19): `com.helger.phoxtrot.xpath.stream.XPathStreamability` classifies expressions and
  patterns; the XSLT layer supplies its knowledge through `StreamContext`. It fails closed: an unknown construct is
  roaming/free-ranging, never "streamable". `XPathStreamabilityAnalyzer` is its public facade (no AST in its API); its
  reasons come from re-analysing sub-expressions (`ExprWalker.getAllChildren`, path prefixes), not from the analysis
  itself, and have no text position (the AST keeps none). The call-site signature of a function is built in one place,
  `StreamFunctionSignature.createForParameterTypes` (used by `XsltStreamability` and the facade).
- When merging parallel work, check for the same mechanism implemented twice in different places (a clean git merge
  does not catch it); it happened with the XPath 1.0 compatibility conversions.

