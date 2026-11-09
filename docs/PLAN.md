# Port plan

Reference implementation: go-xslt (see NOTICE). Gate for every phase: the W3C suites, run by name-diffed fail lists.
Suites are cloned next to this repo (`../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`) and are never committed.

Decisions:

* Apache 2.0, Java 21 (CI also on 25), groupId `com.helger.phoxtrot`, no `ph-` artifact prefix.
* XML parsing: JDK SAX. XSD: the native validator (`phoxtrot-xsd`, XSD 1.0 and 1.1; schema-aware XPath and XSLT compile
  as 1.1) is the default schema provider; Apache Xerces-J 2.12.2 (optional dependency of `phoxtrot-xdm`, without
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
| X1 | XSLT: XSLT30 harness (`phoxtrot-xslt-conformance`), match patterns (`phoxtrot-xpath` `pattern`), core compiler and engine with the basic instructions (`phoxtrot-xslt`) | `xslt30_test.go`, `pattern*.go`, `compile.go`, `transform.go`, `modes.go`, `loader.go`, `avt.go` | done |
| X2 | XSLT instruction families and XSLT functions (number, grouping, merge, iterate, try/catch, result-document, accumulators, evaluate, keys, ...) | `instr_*.go`, `functions.go` | done |
| X3 | Packages, `fn:transform`, backwards compatibility, streaming, schema-awareness (Xerces-J vs. own XSD), JAXP `TransformerFactory` | `packages.go`, `fn_transform.go`, `stream_*.go`, ... | packages, `fn:transform`, schema-awareness (native validator, opt-in), streaming (opt-in claim) and the JAXP adapter (`com.helger.phoxtrot.xslt.jaxp`, 2026-11-09) done; backwards compatibility open |

XSLT30 status (2026-11-04, after the open cases `function-lookup-005`/`006` and `glob-cxt-item-010`): 8,640 of 8,647
applicable cases pass (99.9%) as a basic processor, identical on JDK 21 and 25; all 7 fails are documented in
`phoxtrot-xslt-conformance/docs/accepted-failures/xslt30.md` (`XSLT30IT` fails on any undocumented failure). Extension API for the families: `phoxtrot-xslt/INSTRAPI.md`.

QT3 status (2026-10-26): 22,419 of 22,478 applicable cases pass (99.7%), 59 fail, all documented in
`phoxtrot-xpath-conformance/docs/accepted-failures/qt3.md` (`QT3IT` fails on any undocumented failure):

| Group | Fails | Status |
|-------|-------|--------|
| `fn:transform` wanted unavailable | 3 | Accepted: `fn-transform-901/902/err-14` (same in go-xslt) |
| XQuery modules | 34 | Accepted: no XQuery (same in go-xslt) |
| Static typing (type inference) | 20 | Accepted: same 20 cases as go-xslt |
| External entity in `fn:parse-xml`, network access | 2 | Accepted (same in go-xslt) |

Schema-awareness (2026-11-05): the native validator (`phoxtrot-xsd`) is the schema processor, Apache Xerces-J (optional,
loaded only when present, `XdmSchemaSupport`) the fallback. `-Dxslt30.schemaAware=true` runs XSLT30 schema-aware:
9,298 of 9,310 pass, all 12 fails accepted (`phoxtrot-xslt-conformance/docs/accepted-failures/xslt30-schema-aware.md`); the
default run is unchanged (8,640/8,647).
With `-Dqt3.schemaProcessor=jdk` the QT3 harness uses the JDK validator instead, and the 7 schema-validated source
cases fail again.

## Native XSD validator

Port of go-xslt's `internal/xsd` (about 16,700 lines of Go, 99.99% of `xsdtests` for XSD 1.0 and 1.1), decided
2026-10-26. Why Xerces-J is needed today and why it is only the fallback: [SCHEMA.md](SCHEMA.md).

* New module `phoxtrot-xsd` (package `com.helger.phoxtrot.xsd`, depends on `phoxtrot-xpath`) plus `phoxtrot-xsd-conformance` for the harness.
* XSD 1.0 first, then XSD 1.1 (assertions and conditional type assignment on our XPath engine, `xs:override`, open
  content) on the version switch that stayed in the code. Since V8 `XsdSchemaProvider` compiles as 1.1, like go-xslt's
  XSLT host (decided 2026-11-04).
* The native validator is the default schema provider (V6). Xerces-J stays as an optional second provider and as the
  differential reference (`-Dxdm.schemaProvider=xerces`).
* XSLT and XPath only need the first error; the public validation API (`XsdSchema.validate`) collects all errors (V7).

| Phase | Content | Go reference | Status |
|-------|---------|--------------|--------|
| V0 | Schema SPI independent of JAXP: `XdmSchemaType` holds an `IXdmTypeDefinition` instead of `TypeInfo`; no `getJaxpSchema`/`ValidatorHandler` access; validation parses with the JDK and then validates the tree in place; Xerces provider adapted, QT3 schema cache off JAXP. Gate: unchanged numbers | - | done: JAXP only in the `jaxp` and `xerces` packages, `JdkSchemaProvider` and the Xerces-J provider share `AbstractJaxpSchemaSet` |
| V1 | `phoxtrot-xsd-conformance`: `xsdtests` harness, accepted-failures gate, first run against Xerces-J as baseline | `test/conformance/xsd_test.go` | done: Xerces-J baseline XSD 1.0 39,145 of 39,401 (256 fails documented), same denominator as go-xslt; the native validator plugs in through `IXsdTestValidator` |
| V2 | Loader and component model, include/import/redefine, schema-for-schemas checks, resolver and file-access rules | `loader.go`, `structure.go`, `components.go`, `imports.go`, `model.go`, `docs.go`, `schemavalid.go` | done: `phoxtrot-xsd` with stubs and `phoxtrot-xsd/XSDAPI.md` for V3 to V6; native run 0 fails (schema tests 2,144 pass, 12,226 unsupported; instance tests all unsupported). With permissive stubs the schema-test fail list equals go-xslt's (1,307 by name) |
| V3 | Simple types and facets (reusing casting and the XSD regex grammar) | `simpletype.go`, `facets.go`, `lexscan.go` | done (cyclic unions bounded; new `XsdRegexGrammar` for the XSD regex grammar) |
| V4 | Content models: position-set NFA, linear matcher above 600 children, UPA, restriction derivation | `content.go`, `bigmatch.go`, `upa.go`, `restriction.go` | done (recursion bounded at 250 particle levels, `XsdUnsupportedException` beyond) |
| V5 | Instance validation: attributes, identity constraints, `xsi:type`/`nil`/`schemaLocation` hints | `instance.go`, `attributes.go`, `identity.go` | done, first-error mode only (collect-all is V7). Merged V3-V5, native XSD 1.0: 39,396 of 39,401 pass, 0 unsupported; the 5 fails are go-xslt's documented five (`groupH021v`, `idB005`, `particlesZ001.i`, `particlesZ023/024`). Fail lists identical on JDK 21 and 25 |
| V6 | Bridge as an `IXdmSchemaProvider`, in-place vs. from-text differential test over the whole suite, switch XSLT30 schema-aware and QT3 to it, compare by name with Xerces-J | `bridge.go` | done: `XsdBridge` (`IXdmSchemaSet`) and `XsdSchemaProvider` in `phoxtrot-xsd`; in place vs. from text agree on all 25,018 compared XSD 1.0 instances (`XsdBridgeDifferentialIT`); `XdmSchemaSupport` loads the native provider by default (`-Dxdm.schemaProvider=xerces` for Xerces-J); QT3 identical to Xerces-J by name (59), XSLT30 schema-aware 17 fails against 18 for Xerces-J (see `xslt30-schema-aware.md`); the native xsdtests gate is on (go-xslt's five fails) |
| V7 | Public validation API: all errors in document order, content-model explanations, `msg:message` | `collect.go`, `explain.go`, `messages.go` | done: `XsdSchema.validate`/`validateText` collect all errors (`XsdValidationOptions`: language, max errors), `XsdExplain` names missing/too-many/unexpected/order, `msg:message` in namespace `urn:fdc:helger.com:phoxtrot:xsd:messages` (go-xslt's URI is not used); the in-place path stays first-error; native XSD 1.0 unchanged (39,396 of 39,401) |
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
* QT3 and XSLT30 fail lists unchanged by name on JDK 21 and 25; `LazyVariableTest` (phoxtrot-xslt) and
  `DynamicContextLazyVariableTest` (phoxtrot-xpath).
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
* Tests: `XsltEvaluationTest` (phoxtrot-xslt), `CompiledXPathTest` (phoxtrot-xpath). QT3, XSLT30 and xsdtests fail lists
  unchanged by name on JDK 21 and 25.

## Early exit for existence tests

Status: done (2026-11-05; suggestion S1 of ph-sch2java).

ph-sch2java's differential harness against Saxon found dynamic errors that only phoxtrot raised, e.g. in Peppol MLS
`exists(cac:LineResponse[cac:Response[normalize-space(cac:Status/cbc:StatusReasonCode) = 'BV']])`: with a duplicated
`cac:Status` in a later `cac:LineResponse`, Saxon stops at the first matching line, phoxtrot evaluated all lines and
raised XPTY0004. XPath 3.1 section 2.3.4 allows an implementation to skip parts that do not change the result, and
with them their errors.

Result (`XPathEvaluator`):

* Where only the existence or the effective boolean value of a value matters, the evaluator stops at the first
  decisive item: the argument of a static call of `fn:exists`, `fn:empty`, `fn:boolean` and `fn:not` (recognised by
  the implementation the name resolves to: `FnSequence.EXISTS`/`EMPTY`, `FnBoolean.BOOLEAN`/`NOT`), the operands of
  `and`/`or`, the `if` condition, `satisfies`, predicates, and hosts through
  `XPathEvaluator.evaluateEffectiveBooleanValue`/`CompiledXPath.evaluateEffectiveBooleanValue` (`xsl:if`,
  `xsl:when` and `xsl:assert` through `XPathExpr.evaluateBoolean`). XSD assertions keep the complete evaluation.
* Shapes with early exit: a path whose last step is an axis step (also a fused `//name` step), with or without
  predicates, and a filter expression; also in parentheses. All steps before the last are evaluated completely, as
  are all predicates of the last step but the last one (positions depend on them); the last predicate is tried item
  by item with the known context size (`last()` works, a number is compared with the position) and stops at the first
  item that passes. For a path the context nodes are tried one by one (or the name index serves all at once) and
  document order and duplicates do not matter; every context item is still checked to be a node first (XPTY0019/20).
* Effective boolean value: a path value is a node sequence, so existence decides. For a filter expression the first
  passing item decides if it is a node; after an atomic value or a function item a second passing item is looked for,
  which gives FORG0006 as before.
* A predicate goes through the early path only if its shape can never be a number (a path ending in an axis step);
  a filter expression as predicate is evaluated completely. Every other shape falls back to the complete evaluation.
  The recursion follows the expression nesting, not the input.
* `//@name` as the last two steps is left to the complete evaluation, which answers it from the attribute index of
  the S2 work below instead of visiting every node; the `//@name` shortcut in `_evalPathPrefix` only fires inside
  the prefix asked for (merged together with S2, `EarlyExitTest.testSubtreeAttributes`).
* Not done: reordering `and`/`or` operands by cost (Saxon does; left to right stays), and early exit inside a path
  step before the last, a union, or a sequence of several operands.
* Measured (JDK 21, generated document of 100,000 `Line` elements, minimum of 30 runs, before -> after):
  `exists(//Line[ID = '10'])` 7.9-8.3 -> 0.61-0.65 ms, `exists(//Line[normalize-space(Amount) = '5'])` 14.8-16.0 ->
  0.56-0.61 ms, `exists(/Invoice/Line[Amount > 98])` 10.4-11.7 -> 1.3-1.4 ms, `boolean(Invoice/Line[ID = '10'])`
  8.7-9.6 -> 0.72-0.90 ms; without a match `exists(//Line[ID = 'none'])` 8.1-8.9 -> 8.6-8.7 ms (unchanged within
  noise), `count(//Line[ID = '10'])` 8.3-8.6 -> 8.1 ms.
* Tests: `EarlyExitTest` (phoxtrot-xpath), including the MLS case. QT3, XSLT30 and xsdtests fail lists unchanged by name.

## Public operator API

Status: done (2026-11-05; suggestion S3 of ph-sch2java).

ph-sch2java generates Java for the structure of Schematron expressions (navigation, loops, `and`/`or`, quantifiers),
but the helpers with the evaluator's semantics for comparisons, arithmetic, casts and function argument conversion
were private in `XPathEvaluator`. The generated code therefore built a small AST over operand variables per operator
(`XRt.delegate`/`XRt.op`) and evaluated it in a new `DynamicContext` each time.

Result: `com.helger.phoxtrot.xpath.eval.XPathOperators`, next to `XPathEvaluator` (it needs `SequenceTypeMatcher` and the
package-private parts of the evaluator; the `ops` package stays at the level of single atomic values). The code was
moved there from `XPathEvaluator`, which now calls the same methods, so there is one implementation:

* `generalCompare (EComparisonOp, XdmSequence, XdmSequence, DynamicContext)` returns `boolean`: atomisation, default
  collation, the XPath 1.0 comparison rules in compatibility mode, and for `=` the hash index of a long sequence in the
  run cache (the evaluator's general-equality index). The range fast path (`$x = (1 to 100000)`) needs the
  unevaluated range and stays in the evaluator.
* `valueCompare` and `nodeCompare` return the empty sequence or a boolean, `compare` dispatches by the operator.
* `arithmetic (EBinaryOp, ...)` and `unaryArithmetic (boolean, ...)`, both with the XPath 1.0 compatibility rules.
* `cast (XdmSequence, SeqType, DynamicContext)` and `castable` (atomic, list and schema target types).
* `getFunctionDef (DynamicContext, XdmQName, int)` (XPST0017) and `callFunction (FunctionDef, DynamicContext, List)`:
  the function conversion rules per argument (errors name the function and the argument), then the static
  implementation within `enterCall`/`leaveCall`. The evaluator converts each argument right after evaluating it, as
  before, through the same conversion method.
* Not covered: the node step with the name index (S2 of the suggestions, `AxisNavigator`), set operators, ranges,
  `||`, `instance of` and `treat as`; generated code keeps the delegated AST for those.
* Tests: `XPathOperatorsTest` (phoxtrot-xpath). QT3, XSLT30 and xsdtests fail lists unchanged by name.

## Streaming

Status: done (2026-11-04 to 2026-11-05; S1 to S5). Next: performance and memory optimisation (focused on the hot spots
a profile shows, with an own benchmark first).

Reference: go-xslt, "XSLT 3.0 guaranteed streaming" in its `CLAUDE.md`: a real incremental reader builds ordinary nodes
one record at a time and keeps only the ancestor spine; the streaming dispatcher hands each completed record to the
unchanged buffered evaluator; a compile-time posture/sweep classifier (XSLT 3.0 section 19, appendix J) decides whether
a `streamable="yes"` construct is guaranteed streamable and fails closed (XTSE3430) rather than ever mis-executing.
About 8,200 lines of Go: `internal/xpath/streamability.go`, `internal/xslt/streamability.go`,
`internal/xmltree/stream.go`, `internal/xslt/stream_{exec,acc,group,fork,property}.go`, `transform_to.go`.

| Stage | Content | Go reference | Status |
|-------|---------|--------------|--------|
| S1 | Claim: `-Dxslt30.streaming=true` in the harness (streaming supported in both directions, `<source streaming>`, own out dir and accepted-failures file), the compile option that makes `streamable="yes"` binding, `system-property('xsl:supports-streaming')`; baseline with buffered execution | `xslt30_test.go`, `stream_property.go`, `features.go` | done (2026-11-04): streaming run 11,111 of 11,291 applicable cases pass, 180 fail, all in `phoxtrot-xslt-conformance/docs/accepted-failures/xslt30-streaming.md` (119 expect XTSE3430 and 2 XTSE3155 for S3, 6 need streamed execution for S4, 45 need undeclared schema-awareness, 1 contradicts XPath, the 7 of the default run) |
| S2 | XPath streamability: posture and sweep of expressions | `internal/xpath/streamability.go` | done (2026-11-04): `com.helger.phoxtrot.xpath.stream.XPathStreamability` with go-xslt's test cases; not yet called. Deviation from go-xslt: a call to a stylesheet function is not assumed to return childless items (go-xslt classifies `string(f:filter(.))` as streamable although it reads the subtree) |
| S3 | XSLT streamability: instructions, modes, accumulators, `xsl:source-document`, `xsl:merge`; XTSE3430 when enforced | `internal/xslt/streamability.go` | done (2026-11-05): `com.helger.phoxtrot.xslt.stream.XsltStreamability`, run at the end of compilation when `setEnforceStreamability` is on; streaming run 11,224 of 11,291 pass, 67 fail (114 of the 119 expected XTSE3430 and both XTSE3155 fixed; 5 XTSE3430 cases and `streamable-100` to `-102` accepted with reasons, as in go-xslt) |
| S4 | Streaming execution: incremental reader (JDK StAX) building `IXdmNode` records, the dispatcher, accumulators, grouping, `xsl:fork`; memory test with a large document | `internal/xmltree/stream.go`, `stream_exec/acc/group/fork.go` | done (2026-11-05): `XdmStreamReader`, `StreamedRun`, streamed accumulators, memory test; streaming run 11,230 of 11,291 pass, 61 fail (the 6 S4 cases fixed: `accumulator-061`, `si-fork-113` to `-115`, `merge-079`, `merge-097s`); streamed `xsl:iterate`, `xsl:for-each-group` and `xsl:fork` are left for S5 (they run buffered) |
| S5 | Streamed `xsl:iterate`, `xsl:for-each-group`, `xsl:fork`, `xsl:merge-source`, record-path predicates and wrappers; bounded `generate-id` and snapshot origins; bounded-memory output: serialise while transforming | `stream_exec/group/fork.go`, `transform_to.go` | done (2026-11-05): streaming run unchanged at 11,230 of 11,291 (the newly streamed constructs passed buffered before; the gain is memory); `XsltExecutable.transform (request, OutputStream\|Writer)`, drained where the output definition allows it; the XSLT30 run through the writer (`-Dxslt30.writeTo=true`) gives the same output for every case |

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
Recommendation does not guarantee to run, and now get XTSE3430 (go-xslt accepts them the same way). Found on the way
and fixed on 2026-11-05 (default behaviour): `xsl:fork` mixing `xsl:sequence` and `xsl:for-each-group` children, or
with two `xsl:for-each-group`, is rejected with XTSE0010; `xsl:fallback` is allowed among its children (section 17). Left for S4: the run-time XTSE3430 of go-xslt for `fn:key` over a streamed document.

S4 notes: `com.helger.phoxtrot.xdm.XdmStreamReader` reads with the JDK StAX parser (settings of `XdmParser`: internal
subset read, external subset and entities not, same entity and depth limits) and builds ordinary nodes numbered as they
are created (an open element covers what is read later; `drop`/`skipSubtree` do not renumber; a streamed document has
no name index). It merges character data itself (the coalescing mode of the JDK parser reports element-content
whitespace inconsistently) and refuses XML 1.1, external parsed entities and, when external entities are to be read, an
external subset (`XdmStreamUnsupportedException`, before the first event; the caller reads in full). A stream that drops
nothing builds the tree `XdmParser` builds. Dispatch (`com.helger.phoxtrot.xslt.stream`): with the streaming claim the analysis
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

S5 notes: streamed only with the streaming claim and a proven verdict, everything else runs buffered. `xsl:iterate`
over a record path (bounded); `xsl:for-each-group` with `group-adjacent`/`-starting-with`/`-ending-with` (one open
group retained); `group-by` as the direct child of `xsl:fork` (every selected record retained, then the buffered
grouping: correct, not bounded); `xsl:fork` whose prongs each consume one record path (all paths in one pass, each
prong's result buffered, replayed in prong order); streamable `xsl:merge-source` read one record at a time per document
(`StreamedRun.startPull`/`nextRecord`); predicates on any step of a record path when motionless, non-positional and
boolean or node-valued; wrappers with attribute value templates proven motionless. `fn:generate-id` and the
accumulator origin map of copies are weak maps (identity, `IXdmNode` has no `equals`); copies of streamed nodes keep
frozen accumulator values; a streamed accumulator rule may read another accumulator at the same node (XTDE3400 on a
cycle). Deviations from go-xslt, following the Recommendation: `group-adjacent` with a motionless key is streamed
(section 19.8.4.19), fork prongs may read different non-nesting paths (section 16), predicates on any step (section
19.8), `group-by` under `xsl:fork` is streamed. Output: level 1 writes xml/xhtml/html to the target in chunks (text,
json and adaptive are built first); level 2 (`ResultDrain`, `XsltTransformer._createDrain`) writes and detaches every
completed child of the elements under construction when the stylesheet is not schema-aware, the unnamed `xsl:output`
names its method, there is no item separator, no `xsl:result-document` in any module, no indent, no standalone or
doctype-system, `include-content-type='no'` for html/xhtml and no character map or normalisation for text.
Serialisation errors found while draining are raised at the end, so a later dynamic error still wins as on the
ordinary path. Deviations from go-xslt: html and xhtml are drained too; the `xsl:result-document` check walks the
compiled modules instead of scanning the text. Memory test (`-Xmx32m`): every streamed construct about 25 MB peak; a
streamed copy of 2.8 million records (242 MB in, 234 MB out) 24.5 MB, OutOfMemoryError without draining. Not done
(later, if needed): bounded `group-by` under fork, positional predicates in record paths, nested forks and prongs with
wrappers, draining with schema-awareness or `xsl:result-document`, a top-level `xsl:document`, and incremental text,
json and adaptive output.

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

## Empty paths, interned names and numbering while building (sch2java S4 to S6)

Done (2026-11-05). Reported by ph-sch2java on `Large_Invoice_sample2.xml` (Peppol BIS Billing 3.0, 63,404 lines,
61 MB) with the Peppol 2026.5 rules: 54.6 % of CEN-EN16931-UBL in 131 asserts of the form
`not((cac:InvoiceLine|cac:CreditNoteLine)/cbc:UUID)` whose last element does not occur, `XdmQName.equals` and
`String.equals` at 15 % and 13 % from child steps, and 12 % of the parse in the second numbering pass.

* S4: `XPathEvaluator._isEmptyByNameIndex` answers a path as empty without evaluating its prefix when the last step
  (or the fused `//N` step) is a child, descendant or descendant-or-self step with an exact element name that the
  document's element index does not contain. Only for prefixes that cannot raise an error: axis steps without
  predicates, unions or parenthesised groups of such paths, a context node whose root is a document node; anything
  else is evaluated as before (XPDY0002, XPTY0020, XPDY0050 stay). Used by the early exit and the complete path
  evaluation; unnumbered and streamed trees have no index. Measured: the `UBL-CR` asserts from 12 to 67 ms to about
  0.01 ms each. Trade-off: the first such path on a numbered document builds the element index.
* S5: the names of compiled node tests are interned (`NodeTest`, `XdmQName.getInterned`), as the JDK parsers already
  do for parsed names (the tree builder and stream reader intern on a cache miss as a safeguard), so a matching name
  is decided by identity. The JVM string table is the pool: thread-safe and garbage-collected. Measured 3 to 5 % on
  child steps; the remaining string comparisons are of names that differ.
* S6: `XdmTreeBuilder` numbers nodes as it creates them (`internalAppendNumbered`, as `XdmStreamReader`), so a parsed
  document needs no second `assignDocumentOrder` pass; a structural change while building (`JaxpTypeAnnotator` with
  Xerces-J) drops the numbering and the pass runs at the end as before. `XdmTreeBuilderNumberingTest` checks that the
  numbering equals the second pass. Measured: parse 8 to 10 % faster.

Seen in the profile, left for the performance phase: `FunctionLibrary.getFunction` builds the key
`{ns}local#arity` on every static function call (15 to 20 % of the per-line samples), and every axis step allocates an
`ArrayList` that is then copied into an `XdmSequence` (about 13 % of the child-step profile).

## Review pass: Javadoc, findings, stricter harnesses

Done (2026-11-05). Every module got Javadoc on what its methods actually do (comments only, checked token by token
against the code). The pass reported about 90 suspected bugs, spec mismatches and rule violations; each was reproduced
with a unit test before it was fixed, decided against the spec text, and checked against what the W3C suites expect
(where a suite case contradicts the spec, the suite's behaviour was kept and the case is named below). The harnesses
were tightened: `not` passes only on an evaluated false child, `assert-eq` compares typed values,
`assert-result-document` resolves the full URI, `assert-serialization` is exact, QT3 skips that hid failures fail as
`harness:<reason>`, `-Dqt3.strictCode=true`, accepted-failures files count only listed names, and xsdtests runs the
`full-xpath-in-CTA` groups. The final gate on JDK 21 (QT3 59, XSLT30 7, schema-aware 12, streaming 61, writer 7, xsd
256, native 5 and 4) showed only three new failures, `cta0021`/`cta0022`/`cta0024`, fixed in the XPath context of
assertions and type alternatives (XSD 1.1 Part 1, 3.12.4 and 3.13.4.2).

Kept because a suite case contradicts the spec: `PT.5S`/`PT1.S` are rejected (QT3 `CastAs688`–`691`); SERE0008 is not
raised for comments and PIs (Serialization 3.1 section 5.1.3; XSLT30 `output-0707` with the adaptive method); named
accumulators need not be declared streamable (XSLT 3.0 section 18.2.2; about 60 cases); an absent context item for
`unparsed-entity-uri` stays XTDE1370/XTDE1380 (`error-1370a`, `error-1380a`).

Open, for later:

* `schema-element()` subtyping ignores substitution groups (XPath 3.1 section 3.3.1); an `xs:QName` typed value is
  atomised without a namespace context (`Atomizer._addTypedValue`); a character-map result outside the encoding does
  not raise SERE0008 (Serialization 3.1 section 11); SERE0008 for comments and PIs outside the adaptive method.
* `eq` is not transitive across float, double and decimal, so one distinct-values/group-by bucket key cannot match
  every equal pair.
* A parentless processing instruction does not get the instruction's base URI (fixing it touches node adoption in
  `ContentOutput._appendNode`); the section 26.1 rule that a version 1.0 principal stylesheet defaults to the xml
  method is not implemented; a missing serialization parameter document raises XTSE0020 (the spec says it should be
  ignored); the decimal-format check treats the whole digit family as clashing, section 5.4 names only zero-digit.
* `xsl:expose` rules are checked twice (`compile/ExposeChecker` and `PackageChecks.getExposure`); `XsltStreamability`
  reads a missing recorded `as` as `item()*` instead of failing closed; `AccumulatorExtension._lookup` raises the static
  XTSE3430 at run time (detect it statically instead); mode use-accumulators with an invalid token gives XTSE0020, not
  XTSE3300; `Mode` warning flags, `VariableDef.EKind.FUNCTION_PARAM` and the context of `Engine._buildKeyIndex` are
  unused.
* XSD: the 1.0 compiler honours `xpathDefaultNamespace`; `vc:typeAvailable` reports `xs:precisionDecimal`; added
  default element content is not visible to XSD 1.1 assertions; the wildcard intersection with a union operand;
  src-ct.2 and src-redefine.1/.2 codes without a clear clause; the current dateTime of assertion tests is not constant
  over an assessment episode (3.13.4.2).
* Harness: the XSLT30 harness has no code-mismatch list like `qt3-code-mismatches.txt`; XSD tokens `Unicode_*`,
  edition and XML-version tokens are never claimed; a non-queried schema compile crash drops the group's instance tests
  uncounted.

## Function calls, conversions and key patterns

Status: items 1 to 3 and sch2java S8 done (2026-11-06); the queued items below are open. Follows from sch2java S7 (see `docs/SUGGESTIONS-sch2java.md`): compiling simple
`xsl:function` bodies into compiled parts was implemented and measured and gave no gain (key index over 100,000 nodes
with `use='f:schemeAndValue(id)'`: 94 ms before, 97 ms after; the same `use` inline 79 ms), so it was dropped: it
would have put the semantics of xsl:variable, xsl:choose, xsl:if, xsl:value-of and xsl:sequence in two places. A JFR
profile of that workload shows instead:

1. `FunctionLibrary.getFunction` builds and hashes the key `{ns}local#arity` on every static function call (about
   17 %; 15 to 20 % of the per-line Schematron profile of S4 to S6).
2. `SequenceTypeMatcher.convert` for the `as` conversions of results, variables and arguments (16 to 20 %).
3. `PatternMatcher$PathSearch` allocated per key `match` test (about 15 % of the key index build).

Also queued for this phase: one `ArrayList` per axis step copied into an `XdmSequence` (about 13 % of the child-step
profile of S4 to S6), and two go-xslt v0.4.2 algorithm changes that apply here too: `xsl:number` creates a context
per candidate node (`NumberInstruction`, go-xslt `fbf8394`), and `array(T)` checks of `xsl:iterate` parameters
re-check every member (`SequenceTypeMatcher`, go-xslt `0483716`/`a60fa65`).

Done (JDK 21, min of 25 runs after 5 warm-ups, before/after alternated; `1836f9a` against `2be6fee`):

* Static calls resolve once per call site: `IExpr.FunctionCallExpr` carries a `FunctionCallSite` that caches the
  definition per library instance and `FunctionLibrary.getModificationStamp` (libraries change after compilation:
  stylesheet functions, `AccumulatorExtension`), with `XPathOperators.getFunctionDef` for generated code.
  `getFunction` share 12.8 % to 1.0 % (invoice), 11.4 % to 1.9 % (key with function).
* `as` conversion of a single item without a sequence builder (`SequenceTypeMatcher._convertSingleAtomic`, exact type
  before the derivation walk); the conversion share 21 % to 13 % on the key workload.
* `XsltPattern.canMatch` rejects a node the last step of no path can select before any context or path search is
  built; `PathSearch.<init>` 9.6 % to 1.5 %.
* sch2java S8: a key whose match patterns all end in an exact element or attribute name takes its candidates from the
  document's name index (`XsltPattern.getFinalNames`, `Engine._getKeyCandidatesFromNames`), merged in document order;
  anything else walks the tree as before.

| Workload | Before | After |
|----------|--------|-------|
| key over 100,000 nodes, `use` calls `f:schemeAndValue` | 84 to 90 ms | 58 ms |
| the same key, `use` inline | 69 to 71 ms | 46 to 47 ms |
| 100,000 function calls | 71 to 74 ms | 52 ms |
| TSR-like: 3 keys over 150,000 elements | 212 to 243 ms | 97 to 109 ms |
| invoice: 8 per-line expressions over 63,404 lines | 110 ms | 90 ms |

Next in the profiles: the child scan of `AxisNavigator.collectNamedChildren` (35 % inclusive of the invoice
workload, `getChildAtIndex` 14 %; the `ArrayList` copy is only about 6 %, so that item is dropped), the key index
`HashMap` growth (about 10 %, pre-sizing), context allocation per call and focus (about 5 %),
`EAtomicType.isNumeric` through `isDerivedFrom` (a flag would make it constant), pattern predicates of key matches.

Benchmark (2026-11-06): module `phoxtrot-xslt-benchmark` (JMH, standalone; `compare.sh <base> <new>` builds two commits in
temporary worktrees and compares them), baseline and ranked hot spots in `docs/benchmarks/2026-11-06-baseline.md`.
Checked against the queued items: the `array(T)` check is already linear (uniform member type), dropped; the key
index `HashMap` growth is 0.7 %, dropped; the `xsl:number` cost is the quadratic counting (`_countAny` 93 %), not the
context per candidate. New in the profile: `XdmSchemaType.of` per annotated node in XSD 1.1 assertions (68 % of 1.1
validation), `FunctionLibrary.createDefault` per compiled stylesheet (47 % of compiling),
`PatternMatcher._getSelectingAxis` per `canMatch` (26 % of dispatch), the default collation of `min`/`max` resolved
per call (54 % of the positional workload), `days-from-duration` through `BigInteger` (33 % of FLWOR).

Batch 1 (2026-11-08, `docs/benchmarks/2026-11-08-batch-1.md`): XSD 1.1 assertion schema types per type and run,
linear `xsl:number` counting, the default collation resolved once per static context, one shared read-only default
function library, the selecting axis of a pattern's last step computed once, duration parts in `long` arithmetic, no
lambda per name in the tree builders, `EAtomicType.isDerivedFrom` in constant time. `xsl:number` at 2,000 lines
2,218 ms to 3.5 ms (6.3 GB to 8 MB allocated), XPath positional and FLWOR about half, XSD 1.1 validation 0.58 of the
allocation. Gate unchanged.

Batch 2 (2026-11-08, `docs/benchmarks/2026-11-08-batch-2.md`): focus-independent predicates evaluated once per step
(`ExprWalker.isFocusIndependent`), a `long` fast path for integer arithmetic, argument conversion without builders,
XSD identity paths walked on the tree (`XsdIdentityPath`), the depth walk and assertion tree copies. XPath positional
288 ms to 3.4 ms, XPath rules 0.87, XSLT recursion 0.87, XSD allocation 0.89 to 0.91. Gate unchanged. Open next: the
child scan (node model), `XdmSequence` building, literal arguments converted per call, the tree builder, and for
XSD the depth recorded by the tree builder and `getDeclaredNamespaces` without a copy.

Batch 3 (2026-11-08, `docs/benchmarks/2026-11-08-batch-3.md`): `XdmSequence` without an array for one item and
without copies in builders, literal arguments converted once per call site, string functions without copies,
children in a plain array, a cheaper tree builder, the maximum element depth recorded while building, declared
namespaces without a copy. Against batch 2: XPath strings 0.57, child 0.64, rules 0.81, XSLT recursion 0.83, grouping
0.73, serialisation 0.74 to 0.81, XSD 1.1 0.87, parse 0.92 with 20 % less allocation, retained heap of the tree 10 to
27 % smaller. Gate unchanged.

Batch 4 (2026-11-09, suggestions S18 and S12; figures in `phoxtrot-xdm-memory-benchmark/README.md`): attributes as an
exact-size array, source locations in located node subclasses (`XdmParser.setRecordLocations`, default on; every
stylesheet, schema and XSD instance parse sets it explicitly, `fn:parse-xml` parses without), the tree number as a
shared `TreeStamp`, `m_eIDKind` and the namespace nodes in `Details`, short attribute values shared per document
(sharing short text values cost 10 to 14 % parse time and was dropped). Retained heap against batch 3: XMark 14.5 to
13.4 MB, Bank 37.4 to 29.3 MB, Unicode 79.3 to 60.6 MB; without locations and with compact object headers (JDK 25)
another 15 to 25 %; the gap to Saxon's TinyTree 1.6 to 5.0 times. `EXsltThreading` (`NEW_THREAD` default,
`THREAD_POOL`, `CALLING_THREAD`) on `XsltCompileOptions` and `XsltTransformRequest`: a 1 KB transform 48.7 µs on a new
thread, 15.3 µs on the pool, 11.8 µs on the calling thread (8 threads: 237, 42, 22 µs). Gate unchanged.

