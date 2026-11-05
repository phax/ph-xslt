# Port plan

Reference implementation: go-xslt (see NOTICE). Gate for every phase: the W3C suites, run by name-diffed fail lists.
Suites are cloned next to this repo (`../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`) and are never committed.

Decisions:

* Apache 2.0, Java 21 (CI also on 25), `com.helger.xpath`, no `ph-` artifact prefix.
* XML parsing: JDK SAX. XSD: the native validator (`xsd-core`, XSD 1.0 and 1.1; schema-aware XPath and XSLT compile
  as 1.1) is the default schema provider; Apache Xerces-J 2.12.2 (optional dependency of `xpath-xdm`, without
  xml-apis, XSD 1.0) is the fallback; without either the JDK validator (`JdkSchemaProvider`, validation only).
* Own XDM tree (`IXdmNode`). `IMicroDocument` from ph-xml becomes an optional adapter module, not the model.
* Hungarian notation and camel case everywhere except records.

| Phase | Content | Go reference | Status |
|-------|---------|--------------|--------|
| 0 | Repo, POMs, CI, licence headers | - | done |
| 1 | XDM: tree, SAX builder, names, document order; then atomic types, XSD date/time/duration, sequences, maps, arrays, function items | `xmltree`, `value.go`, `atomic.go`, `types.go`, `datetime*.go` | done |
| 2 | XPath lexer, parser, AST | `lexer.go`, `parser.go`, `ast.go` | done |
| 3 | Evaluator: axes, comparison, arithmetic, casting, FLWOR, inline functions, cancellation | `eval.go`, `axes.go`, `compare.go`, `cast.go`, `arith.go` | done |
| 4 | F&O 3.1 catalogue in family files | `fn_*.go` | done |
| 5 | Regex (translation to `java.util.regex`, own matcher as fallback), collation (java.text.Collator, ICU4J undecided) | `regex_*.go`, `fn_collation.go` | done |
| 6 | QT3 harness, triage, accepted-failures file | `test/conformance/qt3_test.go` | done |
| X1 | XSLT: XSLT30 harness (`xslt-conformance`), match patterns (`xpath-core` `pattern`), core compiler and engine with the basic instructions (`xslt-core`) | `xslt30_test.go`, `pattern*.go`, `compile.go`, `transform.go`, `modes.go`, `loader.go`, `avt.go` | done |
| X2 | XSLT instruction families and XSLT functions (number, grouping, merge, iterate, try/catch, result-document, accumulators, evaluate, keys, ...) | `instr_*.go`, `functions.go` | done |
| X3 | Packages, `fn:transform`, backwards compatibility, streaming, schema-awareness (Xerces-J vs. own XSD), JAXP `TransformerFactory` | `packages.go`, `fn_transform.go`, `stream_*.go`, ... | packages, `fn:transform` and schema-awareness (Xerces-J, opt-in) done; rest open |

XSLT30 status (2026-11-04, after the open cases `function-lookup-005`/`006` and `glob-cxt-item-010`): 8,640 of 8,647
applicable cases pass (99.9%) as a basic processor, identical on JDK 21 and 25; all 7 fails are documented in
`xslt-conformance/docs/accepted-failures/xslt30.md` (`XSLT30IT` fails on any undocumented failure). Extension API for the families: `xslt-core/INSTRAPI.md`.

QT3 status (2026-10-26): 22,419 of 22,478 applicable cases pass (99.7%), 59 fail, all documented in
`xpath-conformance/docs/accepted-failures/qt3.md` (`QT3IT` fails on any undocumented failure):

| Group | Fails | Status |
|-------|-------|--------|
| `fn:transform` wanted unavailable | 3 | Accepted: `fn-transform-901/902/err-14` (same in go-xslt) |
| XQuery modules | 34 | Accepted: no XQuery (same in go-xslt) |
| Static typing (type inference) | 20 | Accepted: same 20 cases as go-xslt |
| External entity in `fn:parse-xml`, network access | 2 | Accepted (same in go-xslt) |

Schema-awareness (2026-10-26): Apache Xerces-J, optional and loaded only when present (`XdmSchemaSupport`), is the
schema processor. `-Dxslt30.schemaAware=true` runs XSLT30 schema-aware: 9,292 of 9,310 pass
(`xslt-conformance/docs/accepted-failures/xslt30-schema-aware.md`); the default run is unchanged (8,640/8,647).
With `-Dqt3.schemaProcessor=jdk` the QT3 harness uses the JDK validator instead, and the 7 schema-validated source
cases fail again.

## Native XSD validator

Port of go-xslt's `internal/xsd` (about 16,700 lines of Go, 99.99% of `xsdtests` for XSD 1.0 and 1.1), decided
2026-10-26. Why Xerces-J is needed today and why it is only the fallback: [SCHEMA.md](SCHEMA.md).

* New module `xsd-core` (package `com.helger.xsd`, depends on `xpath-core`) plus `xsd-conformance` for the harness.
* XSD 1.0 first, then XSD 1.1 (assertions and conditional type assignment on our XPath engine, `xs:override`, open
  content) on the version switch that stayed in the code. Since V8 `XsdSchemaProvider` compiles as 1.1, like go-xslt's
  XSLT host (decided 2026-11-04).
* The native validator is the default schema provider (V6). Xerces-J stays as an optional second provider and as the
  differential reference (`-Dxdm.schemaProvider=xerces`).
* XSLT and XPath only need the first error; the public validation API (`XsdSchema.validate`) collects all errors (V7).

| Phase | Content | Go reference | Status |
|-------|---------|--------------|--------|
| V0 | Schema SPI independent of JAXP: `XdmSchemaType` holds an `IXdmTypeDefinition` instead of `TypeInfo`; no `getJaxpSchema`/`ValidatorHandler` access; validation parses with the JDK and then validates the tree in place; Xerces provider adapted, QT3 schema cache off JAXP. Gate: unchanged numbers | - | done: JAXP only in the `jaxp` and `xerces` packages, `JdkSchemaProvider` and the Xerces-J provider share `AbstractJaxpSchemaSet` |
| V1 | `xsd-conformance`: `xsdtests` harness, accepted-failures gate, first run against Xerces-J as baseline | `test/conformance/xsd_test.go` | done: Xerces-J baseline XSD 1.0 39,145 of 39,401 (256 fails documented), same denominator as go-xslt; the native validator plugs in through `IXsdTestValidator` |
| V2 | Loader and component model, include/import/redefine, schema-for-schemas checks, resolver and file-access rules | `loader.go`, `structure.go`, `components.go`, `imports.go`, `model.go`, `docs.go`, `schemavalid.go` | done: `xsd-core` with stubs and `xsd-core/XSDAPI.md` for V3 to V6; native run 0 fails (schema tests 2,144 pass, 12,226 unsupported; instance tests all unsupported). With permissive stubs the schema-test fail list equals go-xslt's (1,307 by name) |
| V3 | Simple types and facets (reusing casting and the XSD regex grammar) | `simpletype.go`, `facets.go`, `lexscan.go` | done (cyclic unions bounded; new `XsdRegexGrammar` for the XSD regex grammar) |
| V4 | Content models: position-set NFA, linear matcher above 600 children, UPA, restriction derivation | `content.go`, `bigmatch.go`, `upa.go`, `restriction.go` | done (recursion bounded at 250 particle levels, `XsdUnsupportedException` beyond) |
| V5 | Instance validation: attributes, identity constraints, `xsi:type`/`nil`/`schemaLocation` hints | `instance.go`, `attributes.go`, `identity.go` | done, first-error mode only (collect-all is V7). Merged V3-V5, native XSD 1.0: 39,396 of 39,401 pass, 0 unsupported; the 5 fails are go-xslt's documented five (`groupH021v`, `idB005`, `particlesZ001.i`, `particlesZ023/024`). Fail lists identical on JDK 21 and 25 |
| V6 | Bridge as an `IXdmSchemaProvider`, in-place vs. from-text differential test over the whole suite, switch XSLT30 schema-aware and QT3 to it, compare by name with Xerces-J | `bridge.go` | done: `XsdBridge` (`IXdmSchemaSet`) and `XsdSchemaProvider` in `xsd-core`; in place vs. from text agree on all 25,018 compared XSD 1.0 instances (`XsdBridgeDifferentialIT`); `XdmSchemaSupport` loads the native provider by default (`-Dxdm.schemaProvider=xerces` for Xerces-J); QT3 identical to Xerces-J by name (59), XSLT30 schema-aware 17 fails against 18 for Xerces-J (see `xslt30-schema-aware.md`); the native xsdtests gate is on (go-xslt's five fails) |
| V7 | Public validation API: all errors in document order, content-model explanations, `msg:message` | `collect.go`, `explain.go`, `messages.go` | done: `XsdSchema.validate`/`validateText` collect all errors (`XsdValidationOptions`: language, max errors), `XsdExplain` names missing/too-many/unexpected/order, `msg:message` in namespace `urn:fdc:helger.com:ph-xslt:xsd:messages` (go-xslt's URI is not used); the in-place path stays first-error; native XSD 1.0 unchanged (39,396 of 39,401) |
| V8 | XSD 1.1 | `xsd11.go`, `override.go`, version branches | done: `XsdAssertions` (assertions, type alternatives), `XsdOverride`, open content; native XSD 1.1 41,549 of 41,553, 0 unsupported, the 4 fails are go-xslt's documented four (`all308.xsd`, `particlesZ026`, `particlesZ033_g`, `ste110.i`); `XsdNativeIT` gates both versions; in place vs. from text agree on all 51,245 instances of both versions; `XsdSchemaProvider` compiles as 1.1: XSLT30 schema-aware 15 fails (was 17) |

V3 to V5 run as parallel worktree agents once V2 is merged. Gate for each: the XSD 1.0 fail list by name, with
go-xslt's 5 documented fails as the target, plus the XSLT30 (default and schema-aware) and QT3 lists.

Known JDK differences found so far:

* JDK 25 secure processing limits the element depth to 100, JDK 21 does not. `XdmParser` sets the limit explicitly (default 10,000).
* JDK 25 secure processing allows only 2,500 entity expansions (JDK 21: 64,000). `XdmParser` pins all JAXP entity
  limits to the JDK 21 defaults; `XdmParserEntityExpansionTest` runs go-xslt's expansion attacks against them.

## Lazy variable evaluation

Status: done (2026-11-04; raised by the Schematron executor `com.helger.tools.schexec` in `ph-xpath-inventory`).

Result:

* `DynamicContext.withLazyVariable (XdmQName, Supplier <XdmSequence>)` binds a memoising thunk: evaluated on first
  use, once; a failure is kept and raised again on every use. Local `xsl:variable`s are bound that way
  (`LocalVariable.bind`, also through `XsltContext.withLazyVariable`); the `as` conversion runs then, and an error
  without a location of its own is located at the `xsl:variable` element. `_isUnusedLocalVariable` is gone.
* `xsl:try` (XSLT 3.0 section 8.3) must not catch "errors that occur while evaluating references to variables whose
  declaration and initialization is outside the xsl:try". Each evaluation of an `xsl:try` body enters a `TryScope`;
  a lazy variable remembers its scope and fails with a `LazyVariableException` that carries it, and an `xsl:try`
  rethrows such an error unless the variable was declared inside it.
* The XPath `let` clause stays eager. Measured on JDK 21 (300,000 bindings, minimum of 25 runs): one binding 45 ms
  eager, 48-51 ms lazy; a chain of three 60-62 ms eager, 67-77 ms lazy; only an unused binding is faster lazily
  (37-39 ms against 33-37 ms). The perfab workloads with local variables (`numbers`, `recursion`, `json-roundtrip`)
  and `templates`/`functions` are unchanged within noise with lazy `xsl:variable`s.
* QT3 and XSLT30 fail lists unchanged by name on JDK 21 and 25; `LazyVariableTest` (xslt-core) and
  `DynamicContextLazyVariableTest` (xpath-core).
* schexec binds document lets on its root context and rule lets per rule context node with `withLazyVariable`; its
  own resolver and caches are gone, so the engine's resolver (`_resolveGlobal`) stays in place.
  `SchematronExecCorpusTest`: 4518 comparisons, 0 differences, the 217 dynamic errors of the reference agree;
  `MainSchexecBenchmark` factors unchanged within noise.

The original task:

Today only global variables and parameters are evaluated on first use (`Engine.getGlobalValue`, reached through the
run-wide resolver of `DynamicContext`, with XTDE0640 for cycles). Everything else is eager:

* A local `xsl:variable` is evaluated when it is bound (`LocalVariable.bind`). The only exception is the narrow
  `CoreInstructions._isUnusedLocalVariable` case (a bare variable reference without `as` that nothing after it
  mentions), added for param-0301.
* The XPath `let` clause evaluates each binding before the body (`XPathEvaluator._evalLet`).
* `DynamicContext.withVariable` only accepts a computed `XdmSequence`; `VarBinding` holds a value.

XPath 3.1 section 2.3.4 leaves the evaluation order open, so eager evaluation is conformant. Saxon evaluates
variables lazily, and stylesheets in the wild depend on it: a variable whose value raises a dynamic error is harmless
as long as no path uses it. The Peppol Schematrons are an example. Schematron rule lets become local `xsl:variable`s
in the ISO skeleton; evaluated eagerly they raised XPTY0004 on mutated instances where Saxon raised nothing.
schexec now works around this with its own resolver that evaluates and caches lets on first use. That resolver
replaces the one set by the engine and falls back to `Engine.getGlobalValue`, so it bypasses `_resolveGlobal`
(static values, `xsl:original`) and keeps mutable per-node state outside the context.

* `DynamicContext`: a lazy binding, for example `withLazyVariable (XdmQName, Supplier <XdmSequence>)`, whose
  `VarBinding` holds a memoising thunk. The thunk evaluates in the context where the variable is declared (focus,
  `current()`, local variables), not in the context where it is referenced.
* `LocalVariable.bind` binds such a thunk. The `as` conversion and the error location (`XsltException.locate` with the
  `xsl:variable` element) happen when the value is first used. `_isUnusedLocalVariable` then becomes unnecessary.
* Decide whether the XPath `let` clause becomes lazy as well. The thunk costs an allocation per binding in hot loops,
  so measure it before deciding.
* Public use by hosts: with `withLazyVariable`, schexec binds document lets once on its root context and rule lets
  per context node, and drops its resolver.

Gate: the QT3 and XSLT30 fail lists unchanged by name on JDK 21 and 25, plus unit tests for "an unused variable with
a dynamic error raises nothing" and "a variable used twice is evaluated once". In `ph-xpath-inventory` the schexec
differential gate (`SchematronExecCorpusTest`) stays at 0 differences after the resolver is removed.

## Flat `and`/`or` chains

Status: not implemented, by decision (2026-11-04; proposed as WP-X4 of `../ph-sch2java/docs/PLAN.md`).

The proposal assumed that the parser's nesting limit (XPDY0130, 5,000 levels) is reached by long `and`/`or` chains at
about two levels per operator, i.e. at about 2,500 operators, and that parsing the chain in a loop would lift it.
Measured (2026-11-04, JDK 21):

* The parser already parses a chain in a loop (`_parseBinary`, operator precedence with an operand stack) and counts
  one level per operator, because it builds a left-deep `BinaryExpr` tree: the parse limit is 5,000 operators.
* The binding limit is evaluation: `XPathEvaluator` recurses down the left-deep tree and reports a
  `StackOverflowError` as XPDY0130 ("The evaluation is nested too deeply"). The limit depends on the thread stack:
  569 operators with 512 KB, about 1,300 with 1 MB (the JVM default), 2,499 with 2 MB, between 2,500 and 4,000 on the
  main thread. Inside XSLT or schexec the evaluation starts deeper in the stack, so the headroom is smaller.
* The longest chain in the Peppol Schematron corpus has 916 operators: it works with the default stack and fails on a
  thread with a 512 KB stack.

Not counting the chain in the parser would only move the failure into the evaluation. A fix would need a flat n-ary
`and`/`or` node evaluated in a loop (like `StringConcatExpr` for `||`), with changes in the parser (`_build`), `IExpr`,
`XPathEvaluator`, `ExprWalker`, `XPathStaticAxes` and the AST walkers of `ph-xpath-inventory` (`ExprAnalyzer`,
`FeatureExtractor`).

Decision: not implemented. The corpus works with the default thread stack; a host that evaluates such expressions on
threads with small stacks has to give them a larger stack.

## Host API for standalone expressions

Status: done (2026-11-04, WP-X2 of `../ph-sch2java/docs/PLAN.md`).

A host such as a Schematron executor compiles its own XPath expressions and XSLT patterns, but needs the declarations
of a small stylesheet (`xsl:function`, `xsl:key`, global `xsl:variable`/`xsl:param`). The prototype in
`ph-xpath-inventory` did this by casting the `XsltExecutable` to `CompiledStylesheet`, building a `StaticContext` by
hand from its function library, and creating an `Engine` itself. That skipped what a transformation does on start
(whitespace stripping of the global context item, `xsl:global-context-item` checks, registering the source document,
checking the global parameters) and lost the stylesheet's namespaces and decimal formats.

Result:

* `XsltExecutable.createStaticContext ()`: what an expression written on the principal stylesheet element sees
  (namespaces, base URI, `xpath-default-namespace`, default collation, function library with stylesheet and XSLT
  functions, visible global variables and parameters, decimal formats, schema set). The compiler keeps this as a
  `CompiledStylesheet.HostScope`.
* `XsltExecutable.createEvaluation (XsltTransformRequest)` returns an `XsltEvaluation` per source document. It shares
  the start-up with `transform` (`XsltTransformer.initGlobalContextItem`) and hands out dynamic contexts with the
  global context item as context and current item, carrying the stylesheet's static context (patterns have none of
  their own).
* `CompiledXPath.evaluate` switches to the expression's own static context if the dynamic context was created from
  another one. Before, a standalone expression evaluated in the engine's context did not find its decimal formats
  (FODF1280) or the namespaces for lexical QNames. All existing callers already passed a context of the same static
  context, so they are unaffected.
* The parsed AST (`CompiledXPath.getExpression`, `XPathParser.parse`) and patterns with a given static context
  (`XsltPatternParser.parse`) were already public.
* Tests: `XsltEvaluationTest` (xslt-core), `CompiledXPathTest` (xpath-core). QT3, XSLT30 and xsdtests fail lists
  unchanged by name on JDK 21 and 25.

## Streaming

Status: in progress (started 2026-11-04). After streaming, performance and memory optimisation start (focused on the
hot spots a profile shows, with an own benchmark first).

Reference: go-xslt, "XSLT 3.0 guaranteed streaming" in its `CLAUDE.md`: a real incremental reader builds ordinary nodes
one record at a time and keeps only the ancestor spine; the streaming dispatcher hands each completed record to the
unchanged buffered evaluator; a compile-time posture/sweep classifier (XSLT 3.0 section 19, appendix J) decides whether
a `streamable="yes"` construct is guaranteed streamable and fails closed (XTSE3430) rather than ever mis-executing.
About 8,200 lines of Go: `internal/xpath/streamability.go`, `internal/xslt/streamability.go`,
`internal/xmltree/stream.go`, `internal/xslt/stream_{exec,acc,group,fork,property}.go`, `transform_to.go`.

| Stage | Content | Go reference | Status |
|-------|---------|--------------|--------|
| S1 | Claim: `-Dxslt30.streaming=true` in the harness (streaming supported in both directions, `<source streaming>`, own out dir and accepted-failures file), the compile option that makes `streamable="yes"` binding, `system-property('xsl:supports-streaming')`; baseline with buffered execution | `xslt30_test.go`, `stream_property.go`, `features.go` | done (2026-11-04): streaming run 11,111 of 11,291 applicable cases pass, 180 fail, all in `xslt-conformance/docs/accepted-failures/xslt30-streaming.md` (119 expect XTSE3430 and 2 XTSE3155 for S3, 6 need streamed execution for S4, 45 need undeclared schema-awareness, 1 contradicts XPath, the 7 of the default run) |
| S2 | XPath streamability: posture and sweep of expressions | `internal/xpath/streamability.go` | done (2026-11-04): `com.helger.xpath.stream.XPathStreamability` with go-xslt's test cases; not yet called. Deviation from go-xslt: a call to a stylesheet function is not assumed to return childless items (go-xslt classifies `string(f:filter(.))` as streamable although it reads the subtree) |
| S3 | XSLT streamability: instructions, modes, accumulators, `xsl:source-document`, `xsl:merge`; XTSE3430 when enforced | `internal/xslt/streamability.go` | done (2026-11-05): `com.helger.xslt.stream.XsltStreamability`, run at the end of compilation when `setEnforceStreamability` is on; streaming run 11,224 of 11,291 pass, 67 fail (114 of the 119 expected XTSE3430 and both XTSE3155 fixed; 5 XTSE3430 cases and `streamable-100` to `-102` accepted with reasons, as in go-xslt) |
| S4 | Streaming execution: incremental reader (JDK StAX) building `IXdmNode` records, the dispatcher, accumulators, grouping, `xsl:fork`; memory test with a large document | `internal/xmltree/stream.go`, `stream_exec/acc/group/fork.go` | done (2026-11-05): `XdmStreamReader`, `StreamedRun`, streamed accumulators, memory test; streaming run 11,230 of 11,291 pass, 61 fail (the 6 S4 cases fixed: `accumulator-061`, `si-fork-113` to `-115`, `merge-079`, `merge-097s`); streamed `xsl:iterate`, `xsl:for-each-group` and `xsl:fork` are left for S5 (they run buffered) |
| S5 | Bounded-memory output: serialize while transforming | `transform_to.go` | open |

S1 notes: the first streaming run had 190 fails; fixed on the way, also for the default processor: `lang` without
`collation` on `xsl:sort`/`xsl:merge-key` now selects a UCA collation for that language with `case-order` as its
case-first parameter (`merge-070`, `-076`, `-078` to `-081`, `-083`, `-084`: Swedish order), `fn:snapshot` keeps the
unparsed entities of the copied document node (`sf-unparsed-entity-03/04`), and XTSE0730 (a streamable attribute set
using a non-streamable one). `system-property('xsl:supports-streaming')` stays "no" by default (go-xslt reports "yes"
because it does stream; flip the default of `XsltCompileOptions.DEFAULT_STREAMING_CLAIM` in S4). Deferred to S4,
because they need run-time knowledge of declared-streamable constructs: XTDE3350 for a function item captured over a
streamed node (section 10.3.6, `accumulator-061`), the absent current group in templates called inside a streamable
construct (`si-fork-113` to `-115`), snapshots from a streamable `xsl:merge-source` (`merge-079`, `merge-097s`), and
global variables without a context item when the initial mode is streamable (go-xslt `streamedVarsNoFocus`,
`streamable-116`, which also needs schema-awareness). XTDE3362 is not raised for a host-streamed source: go-xslt
deliberately does not either, as the error is optional for a processor that can evaluate the accumulator.

S3 notes: the compiled instructions are opaque lambdas, so the analysis walks the stylesheet tree (`XslElement`) and
reads the parsed operands from a `StreamabilityIndex` the compiler fills while it compiles (expressions, patterns,
sequence types keyed by element and text; only with enforcement on, so the default compile is unchanged). An operand
missing from the index fails closed; the walk is limited to 1,000 nested instructions. Reporting follows go-xslt: every
failure in a template rule of a streamable mode, a declared-streamable function, a streamable accumulator or a
streamable `xsl:merge-source` is XTSE3430; the body of a streamable `xsl:source-document` only for a verdict marked
definite, a non-grounded result or a call to a declared-streamable function. `Mode.isStreamable` is new. Deviations
from go-xslt, following the Recommendation: a template rule with `mode="#all"` is checked when any mode is
streamable (go-xslt skips it); only the direct `xsl:for-each-group` child of `xsl:fork` may use `group-by` (go-xslt
lets nested ones pass); the AVTs `letter-value` (`xsl:number`), `error-code` (`xsl:assert`), `stable` (`xsl:sort`),
`schema-aware` (`xsl:evaluate`), the `xsl:merge-key` AVTs and a text value template in `xsl:text` are operands too;
a non-motionless collation AVT of a streamed `xsl:for-each-group` is rejected; `empty-sequence()` permits no nodes.
`streamable-100` to `-102` passed before S3 only because nothing was checked; they expect a construct the
Recommendation does not guarantee to run, and now get XTSE3430 (go-xslt accepts them the same way). Found on the way,
not fixed (default behaviour): `xsl:fork` mixing `xsl:sequence` and `xsl:for-each-group` children is not rejected
with XTSE0010. Left for S4: the run-time XTSE3430 of go-xslt for `fn:key` over a streamed document.

S4 notes: `com.helger.xpath.xdm.XdmStreamReader` reads with the JDK StAX parser (settings of `XdmParser`: internal
subset read, external subset and entities not, same entity and depth limits) and builds ordinary nodes numbered as they
are created (an open element covers what is read later; `drop`/`skipSubtree` do not renumber; a streamed document has
no name index). It merges character data itself (the coalescing mode of the JDK parser reports element-content
whitespace inconsistently) and refuses XML 1.1, external parsed entities and, when external entities are to be read, an
external subset (`XdmStreamUnsupportedException`, before the first event; the caller reads in full). A stream that drops
nothing builds the tree `XdmParser` builds. Dispatch (`com.helger.xslt.stream`): with the streaming claim the analysis
also runs without enforcement and records its verdicts (`StreamVerdicts`; a failed declaration disables all of them);
a streamable `xsl:source-document` proven grounded, or the template rule for the document node of a proven streamable
initial mode (`XsltTransformRequest.setStreamedSourceUri`), is streamed when its sequence constructor is a chain of
literal result elements without AVTs around one consumer (`StreamPlanner`: `xsl:for-each`, `xsl:apply-templates`,
`xsl:copy-of`, no sort keys or parameters) whose select is a path of child steps (`StreamRecordPath`), the resolver
streams the document (`IResourceResolver.openDocumentStream`) and every applicable accumulator is proven streamable.
The consumer keeps its buffered code and gets a streamed branch (`StreamConsumer`) that `StreamedRun` drives: each
record is read in full, handed to the unchanged per-item code with its real position, and dropped; everything off the
record path is skipped unbuilt (walked and dropped node by node while accumulators run). Memory test
(`StreamedMemoryTest`): a generated 242 MB document (2.8 million records) streams in a forked JVM with `-Xmx32m`
(peak heap 24.6 MB, about 2 s); the same transformation without streaming fails with OutOfMemoryError there.
Run-time rules: XTSE3430 for `fn:key` over a streamed document and XTDE3362 for an accumulator not computed while
streaming (as go-xslt); current group and grouping key absent in templates invoked from a declared-streamable construct
(sections 14.2.1/14.2.2, `StreamedGroupScope`); a streamable `xsl:merge-source` delivers `fn:snapshot`s; global
variables without context item when the source is declared streamed and the initial mode is streamable (go-xslt
`streamedVarsNoFocus`). All of these only with the streaming claim. Deviations from go-xslt, following the
Recommendation: the dispatcher relies on the guaranteed-streamability analysis instead of go-xslt's textual scans, so
climbing (`../@id`) and `position()` in a streamed `xsl:apply-templates` are streamed (the real position is passed);
every applicable accumulator is computed while reading (go-xslt only the names it finds in the body, falling back for a
computed name), and an error in an accumulator is raised when a value is asked for (spec bug 29813,
`accumulator-056s/057s`); XTDE3350 (not XPDY0002) for `accumulator-before/after` without context item, also in the
default run (section 18.2.4), which with an absent global context item of a streamed source gives `accumulator-061`
without tracking function items. With a streamed source the global context item is only what the request supplies
(section 2.3: it is independent of the initial match selection). `DEFAULT_STREAMING_CLAIM` stays false: only the shapes
above are streamed, and the claim switches on the analysis at every compilation and the run-time rules above. Left for
S5: streamed `xsl:iterate`, `xsl:for-each-group` (`group-starting-with`/`-ending-with`), `xsl:fork`, predicates and
attribute value templates that do not read the document in the record path and wrappers, streamed
`xsl:merge-source`, distinct generate-id values without retaining the nodes (`fn:generate-id` remembers every node it
saw), and serialising while transforming.

Gate per stage: the default lists (QT3, XSLT30, schema-aware XSLT30, xsdtests) unchanged by name on JDK 21 and 25; the
streaming run only improves, and every failure in it is documented.

## Child, descendant and attribute steps on large documents (sch2java S2)

Done (2026-11-05). Reported by ph-sch2java: the CEN-EN16931-UBL Schematron (Peppol 2025.11) on a generated invoice
spent its time in `NodeTestMatcher.matches`, `AxisNavigator._addDescendants` and `AxisNavigator.collect`. Measured with
`sch-bench` `MainProfile CEN-EN16931-UBL` (JFR `settings=profile`) and a temporary per-step counter in
`XPathEvaluator._evalAxisStep` (one validation, 1,000 invoice lines; "visited" is the number of nodes looked at):

| Step shape | Calls | Visited | Source |
|------------|-------|---------|--------|
| `descendant-or-self::node()` from the document, then `attribute::N` on every node | 23 | 1,686,613 (and 1.57 million attribute steps) | `not(//@schemeName)` and the like: `//@N` is not fused like `//N` |
| `child::N` from `Invoice` (1,036 children) | about 900 | about 930,000 | `../../../cac:InvoiceLine[...]`, `cac:AccountingSupplierParty` etc. from the root, on every evaluation |
| `child::N` from small elements (`InvoiceLine` 15 children, `Item`, `ClassifiedTaxCategory`) | about 300,000 | about 3,000,000 | per-line rules; inherent, linear in the lines |
| `descendant::N` with an exact name | 21 | 21,000 (index) | already answered by the name index |

No step from an element context or with predicates fell back to traversal for `descendant::N`: the index already
covered those. The cost was the two shapes above it.

Changes:

* `//@N` (and `X//@N`): `descendant-or-self::node()` without predicates followed by `attribute::N` with an exact name
  and focus-free boolean predicates is answered from a new attribute index of the document
  (`XdmDocumentNode.getAttributesByName`, `AxisNavigator.collectNamedSubtreeAttributes`): the attributes of the subtree
  of a node are those numbered in `(getOrder (), getEndOrder ()]`. Detected in `XPathEvaluator._evalPath`
  (`_isDescendantOrSelfNode`, `_applySubtreeAttributeStep`); positional predicates (`//@a[1]`) keep the walk.
* `child::N` with an exact name goes through `AxisNavigator.collectNamedChildren`: a node with more than 32 children
  in a numbered document gets its child elements by name from `XdmDocumentNode.getChildElementsByName`, which takes
  the indexed elements of the subtree whose parent is the node (or walks the children if that is shorter) and keeps the
  list with the element index, so a repeated step from the root no longer goes through all lines. Other nodes compare
  the children by name without the node test predicate.
* `XdmQName.equals` compares the local name before the namespace URI (short and usually different, against long and
  usually equal), `hashCode` no longer allocates the varargs array of `Objects.hash`.
* All indexes belong to the numbering of the tree and are dropped with it by `appendChild`/`removeChild`/
  `addAttribute`; streamed documents have none.

Measurements (JDK 21, median of the second half of the runs, the machine also running other builds; the baseline is
`83f11d8` measured in the same session):

| Invoice lines | Before | After |
|---------------|--------|-------|
| 1,000 | 134 to 137 ms | 64 to 67 ms |
| 10,000 | 1,889 ms | 992 ms |

New top of `jfr view hot-methods` (10,000 lines, `-XX:+DebugNonSafepoints`): `collectNamedChildren` 12 %,
`String.equals` 7.5 %, `getChildAtIndex` 5.5 %, then sequence building: the child scans of the per-line rules from
small elements, which are linear in the document.

Not done: the per-line child scans walk object graphs (node, name, string) where an integer name code per node would
be one comparison; that changes the node model and is left for the general performance work. The rule tests that
evaluate `../../../cac:InvoiceLine[...]` once per tax subtotal still evaluate their predicates on every line.
