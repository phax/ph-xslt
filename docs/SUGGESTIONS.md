# Suggestions

Suggestions for phoxtrot from projects that use it, from S9 on from the plan for a ph-schematron engine on phoxtrot.
Proposals only; the numbering continues the earlier suggestions S1 to S6 and S8 (all done, see `PLAN.md`).

## S7 - Simple xsl:function bodies as XPath conditional (key indexes and calls)

Status: closed (2026-11-11), not implemented. Measured 2026-11-06 (`PLAN.md` "Function calls, conversions and key patterns"). A working
implementation (compiled bodies with lazy variables and exact error locations) gave no measurable gain: 100,000 key
`use` calls 94 ms before and 97 ms after, against 79 ms for the same `use` written inline. The instruction
interpreter is a few percent of a call; the 36.6 % was inclusive of the XPath work below the call. The costs are the
function lookup per call, the `as` conversions and the pattern matcher allocation per key match, which are being
fixed instead.

Status: implemented on branch `sch-s7` (2026-11-05), no measurable gain, not merged (2026-11-06).

**Correction of the finding:** the 36.6 % for `Engine._buildKeyIndex` on a transaction statistics report (TSR) with
50,000 subtotals of each type was an inclusive figure: it counted all the XPath work of the `use` expressions and the
match patterns. Split by self time (JFR, `-XX:+DebugNonSafepoints`, phoxtrot `main` 5fb228e, 2026-11-06), the key
index build is 41 % of the validation, of which the instruction interpreter (`SequenceConstructor`, `xsl:choose`,
`xsl:value-of`, `xsl:text`, `UserFunction`) is 0.4 % of the validation; XDM navigation and values 9.5 %, the XPath
evaluator 9.1 %, XPath functions 6.5 %, the walk over the document and the match patterns about 8 %. Only 8.4 % of the
validation is under a function call at all (inclusive).

**Measured downstream** (best warm run of six, three alternating rounds, load about 1): TSR with 50,000 subtotals
2,506-2,602 ms (`main`) against 2,558-2,720 ms (`sch-s7`); TSR with 10,000 395-421 against 406-416 ms; end user
statistics report with 10,000 subsets 403-423 against 404-413 ms. This confirms the measurement of phoxtrot: no gain.

## Suggestions from the ph-schematron engine plan (2026-11-08)

Source: `../ph-schematron/docs/PLAN-phoxtrot-engine.md`. The plan adds a ph-schematron module that runs the ISO
Schematron XSLT pipeline (three XSLT 2.0 steps, then the generated XSLT on the instance document) on phoxtrot, without
Saxon, behind the existing `ISchematronResource` API (W3C DOM in and out, JAXP `ErrorListener` and `URIResolver`).
None of the points below blocks the plan; each one removes glue code or a documented limitation there.

### S9 - JAXP adapter (`TransformerFactory`, `Templates`, `Transformer`)

Status: done (2026-11-09): `com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory` with `Templates` and `Transformer`; not registered under `META-INF/services` (see README).

Open in `PLAN.md` X3. ph-schematron's XSLT engines are built on JAXP (`ISchematronXSLTBasedProvider` returns a
`Transformer`). With an adapter, the existing ISO, SchXslt and prebuilt-XSLT engines could run on phoxtrot by
configuration, instead of each needing its own module. Needed: `URIResolver`, `ErrorListener`, parameters,
`DOMSource`/`StreamSource`/`SAXSource` input, `DOMResult`/`StreamResult`/`SAXResult` output.

### S10 - Public W3C DOM bridge

Status: done (2026-11-09): `com.helger.phoxtrot.xdm.bridge` (`DomToXdm`, `XdmToDom`, `XdmToSax`).

There is no conversion between W3C DOM and `IXdmNode` in either direction. The plan writes both:
- DOM input via a JDK identity transformer into `XdmTreeBuilder`;
- an `IXdmNode` → `Document` walker for the SVRL result.

A tested public helper in phoxtrot (namespaces, base URI, document URI, PIs, comments) would avoid every user doing
this again, and could build the tree from a DOM without a SAX round trip.

### S11 - Error and message listener

Status: done (2026-11-09): `IXsltMessageListener` on `XsltTransformRequest`, `XsltException.getCollectedMessages ()`.

- Compile and dynamic errors are thrown one at a time, and `xsl:message` texts are only available after a successful
  run.
- The ISO skeleton reports its explanations with `xsl:message` before it fails. These are lost on the error path.
- Proposal:
  - an optional listener on `XsltCompileOptions` and `XsltTransformRequest` that receives messages live, and warnings
    such as recoverable errors;
  - or at least the messages collected so far, attached to the thrown `XsltException`.

### S12 - Transform on the calling thread

Status: done (2026-11-09): `EXsltThreading` (`THREAD_POOL`, `CALLING_THREAD`) on `XsltCompileOptions` and `XsltTransformRequest`; see `PLAN.md` batch 4.

- Every `compile` and `transform` call runs on a new worker thread with a 2 GB stack reservation.
- A Schematron validation is one `transform` per document, often of small documents, many of them in parallel in a
  server.
- Proposal: an option to run on the calling thread (the caller then provides the stack) or on a reusable pool. To
  be measured first with the planned warm benchmarks of small documents.

### S13 - Explicit "no step limit"

Status: done (2026-11-09): `XsltTransformRequest.UNLIMITED_STEPS`.

- `setMaxSteps (0)` means the default of 20,000,000 steps. Large business documents with many rules can exceed it
  (XPDY0130).
- Proposal: a documented value for "unlimited", e.g. `Long.MAX_VALUE` or a constant, so an engine can offer the limit
  as an option without guessing.

### S14 - Entity resolver hook in `XdmParser`

Status: done (2026-11-09): `XdmParser.setEntityResolver`.

ph-schematron resources accept a SAX `EntityResolver`. `XdmParser` has no hook for it, so the plan has to fall back
to its own SAX parser into `XdmTreeBuilder` when one is configured. Proposal: `XdmParser.setEntityResolver
(EntityResolver)`, used only when external entities are enabled, or for the DTD only.

### S15 - Trace hook for template and rule execution

Status: done (2026-11-09): `IXsltTraceListener` on `XsltTransformRequest` (template rules, named templates, functions, optionally instructions; balanced enter/leave), JAXP `PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER`.

ph-schematron offers per-rule execution telemetry on Saxon through a `TraceListener` (template enter and leave). phoxtrot
has only the global `fn:trace` listener. Proposal: a per-run listener for template and instruction enter and leave,
with the instruction's module URI, line and match pattern, and no cost when not set.

### S16 - XSLT 1.0 backwards compatible mode

Status: done (2026-11-09): XSLT 1.0 backwards compatible processing, claimed by default (`XsltCompileOptions.setBackwardsCompatibleClaim`), plus EXSLT - Common (`exsl:node-set`, `exsl:object-type`).

Open in `PLAN.md` X3. The ISO pipeline creates XSLT 2.0, so the plan does not need it. Prebuilt Schematron XSLT files
are often XSLT 1.0 (`iso_svrl_for_xslt1`) and today run with XPath 3.1 semantics, e.g. XPTY0004 where XSLT 1.0 takes
the first node. This mode is the precondition for a phoxtrot variant of ph-schematron's prebuilt-XSLT engine.

### S17 - Java baseline

Decided (2026-11-08): phoxtrot keeps Java 21 as baseline. ph-schematron stays on Java 17 and builds its phoxtrot
module in a Maven profile that is active from JDK 21, so its CI matrix stays 17, 21 and 25. No change in phoxtrot.

### S18 - Size of the XDM tree

Status: done (2026-11-09) except item 5 for text values (too slow; closed 2026-11-11, not implemented); input documents can be parsed with `XdmParser.setRecordLocations (false)`; see `PLAN.md` batch 4.

Decided (2026-11-08): to be tackled.

- The ph-xdm tree needs 2.3 to 7.2 times the heap of the Saxon TinyTree (`phoxtrot-xdm-memory-benchmark`).
- For a validation server, the input tree is the largest per-request allocation.

The proposals below are based on the field layout of `AbstractXdmNode`, `AbstractXdmParentNode`, `XdmElementNode`,
`XdmAttributeNode` and `XdmTextNode`, and on the class footprint of the "Bank transactions" document in the
`phoxtrot-xdm-memory-benchmark` README. There, of 45.17 MB:
- `String` + `byte[]` take 14.50 MB;
- `CommonsArrayList` + `Object[]` take 8.55 MB;
- the attribute nodes take 10.68 MB.

Only changes that keep the current tree design are listed (cheap steps first). Each proposal must be measured with
the memory benchmark and the XSLT and QT3 conformance runs.

1. **Measure with compact object headers first.** The README figures are without compact object headers. On JDK 25
   (`-XX:+UseCompactObjectHeaders`) every object loses 4 bytes. Add a column for it to the benchmark. It costs nothing
   in code and shows how much of the gap is object headers.
2. **Arrays instead of lists for children and attributes.**
   - Every element with children or attributes holds a `CommonsArrayList` plus its `Object[]`. An `ArrayList` grows
     with spare capacity.
   - Proposal: build them as lists in `XdmTreeBuilder`, and at `endElement` store a trimmed `AbstractXdmNode[]`, or
     a shared empty array.
   - This saves the list object and the spare capacity per element. The read API can stay, with a list view
     created on demand.
3. **Source locations optional.**
   - `m_nLine` and `m_nColumn` take 8 bytes in every node.
   - They are needed for stylesheets (error locations) but rarely for instance documents.
   - Proposal: an `XdmParser` option, on for stylesheet parsing and off by default for input documents. Without
     locations the fields move into the `Details` holder, or into a subclass for located trees.
4. **Tree identifier as `int`.** `m_nTree` is a `long` in every node. Proposal: an `int` (4 bytes less per node),
   if the number of trees per JVM run can be bounded or the counter may wrap safely.
5. **Shared strings while building.**
   - Whitespace-only text nodes (indentation) and repeated short values (code values such as `EUR`, `0088`, `true`)
     are separate `String` + `byte[]` objects per node.
   - Proposal: a per-document string cache in `XdmTreeBuilder` for whitespace-only text and for text and attribute
     values up to a small length (e.g. 32 characters), the same way names are already shared.
   - It is a bounded map that is dropped after the build.
6. **Rarely used element fields into the holder.**
   - `XdmElementNode` carries `m_eIDKind`, `m_aSchemaType` and `m_aNamespaceNodes` in every element.
   - Without schema validation and without `xml:id`/DTD IDs these are always the default.
   - Proposal: move them into `Details`, or into a subclass for typed trees. The `Details` holder already does this
     for the other rare properties; the README says the schema type was kept in the node for validated trees, so the
     split should depend on whether the tree is typed.

### S19 - Threading in the JAXP adapter

Status: done (2026-11-09): factory attribute `PhoxtrotTransformerFactory.ATTR_THREADING` (an `EXsltThreading` or its
name) for compilation and the transformers, `PhoxtrotTransformer.setThreading` per transformer.

Source: the update of the ph-schematron plan (2026-11-09).

- The ph-schematron engine runs on the JAXP adapter (`PhoxtrotTransformerFactory`), because its XSLT engines are
  built on `Templates` and `Transformer`.
- `EXsltThreading` can be set on `XsltCompileOptions` and `XsltTransformRequest`, but the JAXP adapter has no
  setting for it. Every JAXP transformation therefore uses `NEW_THREAD`.
- Proposal: a factory attribute (e.g. `ATTR_THREADING` with the enum name) that applies to compilation and
  transformation, and the same on `PhoxtrotTransformer`.
- Its use depends on the warm benchmarks of small documents in ph-schematron; the attribute lets them compare
  `NEW_THREAD`, `THREAD_POOL` and `CALLING_THREAD` without leaving JAXP.

### S20 - Interruption in the JAXP adapter

Status: done for transformations (2026-11-09): `PhoxtrotTransformer.transform` sets the interrupt hook to the
interrupted state of the calling thread, and keeps it set once the call ends interrupted, so a run left on a worker
thread stops at its next check. Compilation has no interrupt hook yet (`XsltCompileOptions`), so it is not covered.

Source: the update of the ph-schematron plan (2026-11-09).

- ph-schematron stops a validation when the calling thread is interrupted (`SchematronInterruptedException`).
- With `NEW_THREAD` an interrupted caller gets FOER0000, but the transformation goes on in the background until it
  ends (`EXsltThreading` Javadoc). The JAXP adapter sets no interrupt hook.
- Proposal: the JAXP adapter sets `XsltTransformRequest.setInterrupt` to the interrupted state of the calling thread
  (captured when `transform` starts), so a cancelled validation also stops using CPU. The same applies to
  compilation.


## Suggestions from the Schematron validation profile (2026-11-09)

Measured: the ISO Schematron pipeline (`iso_dsdl_include`, `iso_abstract_expand`, `iso_svrl_for_xslt2`, then the
generated XSLT on the instance document) through the JAXP adapter against Saxon-HE 12.10, Peppol rules 2025.11
(`PEPPOL-EN16931-UBL.sch`: 13 patterns, 38 rules; `CEN-EN16931-UBL.sch`: 3 patterns, 105 rules), invoice
`base-example.xml` with its first line copied N times. JDK 21, minimum of 10 to 15 warm runs, load average about 2;
JFR profiles on the calling thread (444 to 588 samples). Both engines report the same failed asserts and fired rules.

| Rules | Size | Saxon | phoxtrot |
|---|--:|--:|--:|
| `PEPPOL-EN16931-UBL` | 1 line | 0.50 ms | 0.89 to 0.95 ms |
| `PEPPOL-EN16931-UBL` | 1,000 lines | 80 ms | 131 ms |
| `PEPPOL-EN16931-UBL` | 10,000 lines | 758 ms | 1,339 ms |
| `CEN-EN16931-UBL` | 1 line | 0.60 ms | 0.82 to 0.85 ms |
| `CEN-EN16931-UBL` | 1,000 lines | 81 ms | 91 ms |
| `CEN-EN16931-UBL` | 10,000 lines | 758 ms | 928 ms |

Compiling the generated XSLT is faster on phoxtrot (5 to 14 ms against 16 to 42 ms); the three ISO steps take the
same time on both. `THREAD_POOL` and `CALLING_THREAD` save only about 5 % on 1-line documents.

### S21 - Template rules indexed by name per mode

Status: done (2026-11-09): `Mode.sortRules` builds the index (from 2 rules on), `Engine._findRule` merges the named
candidates with the other rules in rule order; the multiple-match check still scans the following rules of the full
list. Synthetic workload (10 modes, 20,000 elements of 40 names, rules with a predicate, best of 15): 16 rules per mode
39.5 → 20.0 ms, 41 rules per mode 81.9 → 30.1 ms; a mode with 2 or 3 rules is not slower. The Peppol figures above are
still to be measured again in ph-schematron.

- `Engine._findRule` tests the rules of a mode one by one against every item (`Mode.rules ()`). With
  `CompiledXsltPattern.canMatch` before each full match, this is still 41 % (`PEPPOL-EN16931-UBL`, 1 line), 57 %
  (`PEPPOL-EN16931-UBL`, 10,000 lines) and 38 % (`CEN-EN16931-UBL`, 10,000 lines) of the validation; the self time is
  in `PatternMatcher._canAxisSelect`, `CompiledXsltPattern._canLastStepSelect` and `PatternMatcher$LastStep.canSelect`.
- The ISO skeleton applies templates to the whole document once per pattern, each in its own mode, so every node
  is tested against every rule of every pattern.
- Proposal: an index per mode, built after `Mode.sortRules`, from the node kind and the names of the last step of
  each pattern (`XsltPattern.getFinalNames`, already used for keys in S8) to the rules in sorted order. Rules
  without a final name (wildcards, `node()`, `text()`, union branches without a name) go into a list per node kind
  and are merged in rule order with the named candidates. `xsl:next-match`, `xsl:apply-imports` and the
  multiple-match checks then scan the merged candidates instead of all rules.
- Expected: phoxtrot about at Saxon's level on these workloads.

### S22 - `xsl:result-document` check once per stylesheet

Status: done (2026-11-09): computed at the end of the compilation (`CompiledStylesheet.hasResultDocument`).

- `XsltTransformer._hasResultDocument` walks the element trees of all modules on every run to decide whether the
  principal result can be drained while it is built (`_createDrain`). For the 220 KB XSLT generated from
  `PEPPOL-EN16931-UBL.sch` this is 7.7 % of a 1-line validation, plus `XslElement.getElementChildren` below it.
- Proposal: compute it once per compiled stylesheet (it does not depend on the run).

### S23 - No new SAX parser per parse

Status: done (2026-11-09): `XdmParser` keeps one JDK SAX parser per thread and per setting of external entities,
reset after each parse; a parse started during another on the same thread creates its own. A small document parses in
2.7 µs instead of 19.7 µs (best of 10 rounds of 20,000 parses, JDK 21).

- `XdmParser` creates a `SAXParserFactory` and a `SAXParser` on every parse (`SAXParserFactory.newDefaultInstance`,
  `newSAXParser`): about 3 % of a 1-line validation (`SAXParserFactoryImpl.setFeature`, `SAXParserImpl.<init>`).
- Proposal: a configured factory shared per `XdmParser` configuration, and the `XMLReader` reused per thread (with
  `reset` semantics of the JDK parser), or at least the factory created once.

### S24 - Path patterns matched without a search

Status: done (2026-11-09): both parts in `CompiledXsltPattern` and `PatternMatcher.DirectPath`. Part 2 also takes
predicates that are not provably boolean (such as `[$isGreekSender]`) as long as they call neither `position`,
`last` nor `function-lookup`: a predicate that returns a single number at run time hands the path back to the search.

Measured again after S21 to S23 (`1e44250`, same set-up as above, load average 2.2 to 2.6):

| Rules | Size | Saxon | before S21 | after S23 |
|---|--:|--:|--:|--:|
| `PEPPOL-EN16931-UBL` | 1 line | 0.50 ms | 0.89 to 0.95 ms | 0.70 to 0.76 ms |
| `PEPPOL-EN16931-UBL` | 1,000 lines | 78 ms | 131 ms | 116 ms |
| `PEPPOL-EN16931-UBL` | 10,000 lines | 767 ms | 1,339 ms | 1,118 ms |
| `CEN-EN16931-UBL` | 1 line | 0.62 ms | 0.82 to 0.85 ms | 0.48 to 0.54 ms |
| `CEN-EN16931-UBL` | 1,000 lines | 79 ms | 91 ms | 60 ms |
| `CEN-EN16931-UBL` | 10,000 lines | 734 ms | 928 ms | 646 ms |

With `PEPPOL-EN16931-UBL`, `Engine._findRule` is still 44 % (1 line) and 48 % (10,000 lines) of the validation, now
in the full match of candidates whose name already fits: `CompiledXsltPattern.matches` 25 to 26 % inclusive, with
`PatternMatcher` and `PathSearch` allocated per match (`PathSearch.<init>` 3.5 to 3.8 % self), the anchor search
(`PatternMatcher.getAnchors`, `_getPostfixContexts` 5.7 %) and `HashSet`/`AnchorSet` creation. The index lookup itself
(`Mode.getNamedRuleIndexes`) is 1.2 to 3.5 %.

The patterns behind it, from the XSLT the ISO skeleton generates for `PEPPOL-EN16931-UBL.sch` (after abstract rule
expansion; 119 template rules in 13 pattern modes):
- Unions of short child paths with boolean predicates, e.g. eleven rules
  `cbc:EndpointID[@schemeID = '0088'] | cac:PartyIdentification/cbc:ID[@schemeID = '0088'] | cbc:CompanyID[@schemeID = '0088']`
  for the scheme identifiers, the amount rule with 13 names, `cac:TaxCategory[upper-case(cbc:TaxExemptionReasonCode)='VATEX-EU-O']`
  and absolute paths such as `/ubl-invoice:Invoice/cbc:ID[$isGreekSender]`. Every `cbc:ID`, `cbc:CompanyID` and
  amount element is a candidate of several of them, and each full match runs the general search.
- Ten German rules (mode `M41`, 10 of its 12 rules) of the form
  `(/ubl-invoice:Invoice/cac:TaxTotal/cac:TaxSubtotal | /ubl-creditnote:CreditNote/cac:TaxTotal/cac:TaxSubtotal)[$supplierCountryIsDE and $customerCountryIsDE]`.
  The parenthesised union with a predicate is a single path with a postfix step: it has no final names and no last
  step for the pre-test, so S21 puts it in the list for every node and each element of the document goes through
  `getAnchors` (a `PathSearch` per branch) and, when a branch matches, the whole filter expression evaluated from the
  root.

Proposal, two parts:

1. **Distribute a boolean predicate over a parenthesised union** when compiling the matcher. For
   `(P1 | ... | Pn)[q1]...[qk]` where every `Pi` is a path pattern and every `qj` passes
   `ExprWalker.isFocusFreeBoolean` (never read as a position, so its value only depends on the node as context item),
   match it as `P1[q1]...[qk] | ... | Pn[q1]...[qk]`, the predicates appended to the last step of each `Pi`. A node
   is selected by the left side if and only if it is selected by some `Pi` and every `qj` is true for it as context
   item, which is the right side. This only changes `m_aUnionPaths`, `m_aLastSteps` and `m_aFinalNames` of
   `CompiledXsltPattern`; the pattern source, the default priority (0.5 for the parenthesised form) and the split into
   alternatives of XSLT 3.0 section 6.5 stay as they are. The German rules then get final names, go into the S21
   index, and the postfix search is not needed for them. Predicates that fail the test (numeric or positional)
   keep the current path.
2. **A direct matcher for simple path patterns.** At compile time, mark each union path whose steps are all `child`
   or `attribute` axis steps (plus the leading `/` of an absolute path) with name or kind tests and only predicates
   passing `ExprWalker.isFocusFreeBoolean`. Such a path is matched bottom-up without `PatternMatcher`, `PathSearch`,
   `AnchorSet` or visited sets: test the last step on the node, evaluate its predicates with the node as context
   item (`withFocus (aNode, 1, 1)`, as the fast path of `_predicatesMatch` already does, without the numeric check),
   go to the parent for the previous step, and at the start require a document node for an absolute path, or apply
   the child-or-top rule for a relative one-step path as `PathSearch.search` does. Each step has exactly one
   candidate (the parent), so there is no branching and no search state. Everything else (`//`, other axes, `start`
   expressions, postfix steps, positional or possibly numeric predicates) keeps the general search.

- Both parts can be checked with the existing pattern tests and the XSLT 3.0 conformance run; part 1 also with
  patterns whose predicate is numeric at run time (`(a | b)[$n]` with `$n` = 1 must keep the union semantics).
- Expected: most of the 25 % of the full matches and the 5.7 % of the postfix search on `PEPPOL-EN16931-UBL`;
  `CEN-EN16931-UBL` (no parenthesised patterns, mostly single names) gains less.

### S25 - Smaller costs in the Peppol reporting rules

Status: items 1, 2 and 4 done (2026-11-10): string `eq`/`ne`/`=`/`!=` through `_stringsEqual` in
`AtomicComparator._compareAtomics` and `_generalPair10`; a node without schema type to `xs:string` straight from its
string value in `SequenceTypeMatcher._convertSingleAtomic` (after the XPath 1.0 compatibility conversions, so every
rule of the function conversion still applies); `AtomicCaster.castStringToDouble` for the untyped values of the
aggregate functions (only the type dispatch is saved, the parse stays). Item 3 done (2026-11-11):
`FunctionLibrary.freeze` (only after the parent; the shared default library is frozen), `StylesheetCompiler` freezes
the stylesheet, package and xsl:original libraries at the end of the compilation and `EvaluateExtension` its library;
`FunctionCallSite` reuses an entry whose library was frozen before the lookup without reading the stamp.

Measured 2026-11-10: the same pipeline with Peppol Transaction Statistics Reporting 1.0.5 (TSR) and End User
Statistics Reporting 1.1.5 (EUSR), on generated valid reports with N unique entries (TSR: N subtotals each of
`PerSP-DT-PR` and `PerSP-DT-PR-CC`; EUSR: N subsets each of `PerDT-PR`, `PerDT-PR-EUC` and `PerDT-EUC`). Build
`1e44250` plus the S24 working copy, `CALLING_THREAD`, minimum of 4 to 8 runs, load average 6 to 7 (the ratios are more
reliable than the absolute times). Both engines report 0 failed asserts on the generated reports and the same results
on the 20 official test files of TSR 1.0.4 and 1.0.5 and EUSR 1.1.4 and 1.1.5.

| Rules | N | Size | Saxon | phoxtrot |
|---|--:|--:|--:|--:|
| TSR | 1 | 2 KB | 0.09 ms | 0.10 ms |
| TSR | 1,000 | 1.1 MB | 40 ms | 49 ms |
| TSR | 10,000 | 11 MB | 399 ms | 538 ms |
| TSR | 50,000 | 54 MB | 2,015 ms | 2,822 ms |
| EUSR | 1 | 12 KB | 0.38 ms | 0.43 ms |
| EUSR | 1,000 | 1.5 MB | 40 ms | 44 ms |
| EUSR | 10,000 | 15 MB | 371 ms | 493 ms |
| EUSR | 50,000 | 76 MB | 1,915 ms | 2,564 ms |

There is no dominant cost as in S21 or S24. In JFR profiles of N = 10,000 (shares of all samples; the recorded stacks
are cut at 64 frames, so the shares of outer frames are lower bounds) the large blocks are of similar size on both
engines: building the `xsl:key` indexes (phoxtrot `Engine._buildKeyIndex` 19 to 20 %, Saxon `KeyIndex.buildIndex`
21.6 %, TSR), parsing, rule lookup (phoxtrot `Engine._findRule` 6 to 9 %, Saxon `SimpleMode.getRule` 6.9 %) and
writing the SVRL. The gap is spread over the XPath evaluation of the asserts and the key `use` expressions. Four
items stand out:

1. **String equality through the collation order.** `AtomicComparator._compareAtomics` decides `eq`, `ne`, `=` and
   `!=` on two strings via `_compareComparable` → `_compareStrings` → `CollationRegistry.CODEPOINT.compare`, a
   code point loop: 4.0 % (TSR) and 3.2 % (EUSR) self time, from `normalize-space(@type) = 'PerSP-DT-PR'` and
   `normalize-space(@metaSchemeID) = 'SP'` in rule contexts, predicates and key `use` expressions. The node comparisons
   already use `_stringsEqual`, which is `String.equals` for the codepoint collation. Proposal: for `VALUE_EQ`,
   `VALUE_NE`, `GENERAL_EQ` and `GENERAL_NE` on two values of the string class, use `_stringsEqual` instead of the
   order.
2. **Argument conversion of built-in functions.** `XPathOperators.convertArgument` → `SequenceTypeMatcher.convert` is
   6.5 % (TSR) and 6.0 % (EUSR) inclusive, nearly all for built-in calls such as `normalize-space(@type)` with a single
   node as argument: atomisation (`Atomizer.getUntypedValue`), a new `xs:untypedAtomic` (`XdmAtomic.of`) and its cast
   to `xs:string` (`_convertSingleAtomic`). Proposal: a fast path for a single node passed to an optional atomic
   string parameter, from the string value of the node to the converted value without the intermediate atomic value.
   Whether this preserves every rule of the function conversion (typed nodes, XPath 1.0 compatibility mode) must be
   checked first.
3. **Modification stamp per function call.** `FunctionCallSite.getFunction` reads
   `FunctionLibrary.getModificationStamp` on every call, which adds the counters of the whole parent chain: 1.2 %
   (TSR) and 0.6 % (EUSR) self time. Proposal: if a library cannot change after compilation, mark it as frozen and
   return the cached definition without reading the stamp; otherwise keep a stamp that a change propagates, so that
   the read is a single field.
4. **Untyped values in `min` and `max`.** `FnNumeric._getAggregateInput` casts each `xs:untypedAtomic` to `xs:double`
   through the general `AtomicCaster.cast`: 2.7 % in EUSR, from the three `max(eusr:Subset/...)` asserts over 30,000
   values each. Proposal: a direct `xs:untypedAtomic` to `xs:double` conversion for this rule.

Checked and not proposed: `contains($cl_iso3166, ...)` (1.4 %) already ends in `String.indexOf` for the codepoint
collation; the key indexes are built once per key and document, from the name index (`_getKeyCandidatesFromNames`).

- Expected: items 1 to 4 together a few percent, not the whole gap (phoxtrot takes 1.1 to 1.4 times as long as Saxon).

## Suggestions from the ph-commons review (2026-11-10)

A review of ph-base and ph-collection for phoxtrot found three changes phoxtrot can make with the current ph-commons
(12.5.3), without waiting for new API. Larger proposals (code point compare, primitive int collections, XML
whitespace and name helpers, hex helpers, a concurrent bounded cache) are being added to ph-commons 12.6.0 and are
not listed here.

### S26 - Use the existing ph-commons helpers

Status: done (2026-11-10), with ph-commons 12.6.0. Item 1 was
replaced by `ConcurrentBoundedCache` (see S28); items 2 and 3 use `StringHelper.iterateCodePoints`/`getCodePoints`
(33 sites) and `StringHex.appendHexStringLeadingZero`.

1. **Regex cache through `LRUMap`.** `XPathRegex` (`phoxtrot-xpath/.../regex/XPathRegex.java:51-59`) declares an
   anonymous access-ordered `LinkedHashMap` subclass with `removeEldestEntry`. ph-collection's `LRUMap` is the same
   design (`new LRUMap <> (CACHE_SIZE)`), so the anonymous class can go. The write lock per lookup stays as long as
   the map is access-ordered.
2. **Code points without streams.** 10 `codePoints ().forEach (...)` calls in main code can use
   `StringHelper.iterateCodePoints (String, IntConsumer)` (ph-base), which avoids the `IntStream` pipeline. The 24
   `codePoints ().toArray ()` calls need an `int []`; from ph-commons 12.6.0 on, `StringHelper.getCodePoints (String)`
   returns it without a stream.
3. **No `String.format` for `\uXXXX`.** `JsonWriter.java:121,127`, `JsonParser.java:257` and `FnJson.java:790` (and
   `XdmParser.java:508` for `U+XXXX`) format 4 hex digits with `String.format`. `StringHex.getHexStringLeadingZero
   (int, 4)` gives the lower case form; for upper case, upper-case the 4 characters, or use a 16 character table.
   From ph-commons 12.6.0 on, `StringHex.appendHexStringLeadingZero (aSB, nValue, 4, true)` appends the upper case
   form without an intermediate String.

### S28 - Use the new ph-commons 12.6.0 helpers

Status: done (2026-11-10) for the targeted sites below, with ph-commons 12.6.0.

- Code point collation: `CollationRegistry.CODEPOINT.compare` is `CompareHelper.compareByCodePoint` (callers use only
  the sign).
- XML names: the checks of `XmlNames` delegate to `XmlNameChars` (same rules); `XmlNames` keeps the code point ranges
  for the regular expression classes `\i` and `\c`.
- XML whitespace: `XsdLexical` (`isXmlWhitespace`, `replaceWhitespace`, `collapseWhitespace`, `trimWhitespace`),
  `FnString.normalizeSpace`, `XsltNames.trimXmlWhitespace`/`isWhitespaceOnly` and `XdmDateTimeValue.trimXmlWhitespace`
  delegate to `XmlWhitespace`; the private copies in `XdmDateTimeValue`, `FnConstructor`, `FnSequence`, `FnString`,
  `XPathLexer`, `XsltNames` and `XsdFacetValidator` are gone. The whitespace-only checks of `XdmStreamReader`,
  `XdmToDom`, `MarkupWriter`, `FnJson` and `XsdBridge` keep "empty counts as whitespace" around
  `XmlWhitespace.isAllXmlWhitespace` (false for the empty string). `XsdInstanceValidator._isSpace` is Unicode
  whitespace, not XML whitespace, and stays.
- Caches: the regex cache (`XPathRegex`, was LRU with a write lock per lookup) and the UCA collation cache
  (`CollationRegistry`, was a read lock and a complete clear at 256 entries) are `ConcurrentBoundedCache`: lock-free
  reads, approximate eviction.
- `xsl:sort` (`SortKeyDef`) and the sorted groups of `xsl:for-each-group` keep the JDK sort of a list of boxed
  positions. `CollectionSort.getSortedIndices` (no boxing) was tried and reverted: the key comparison is the cost,
  and its insertion and merge sort needs more comparisons than the JDK's TimSort (comparator calls at 10,000 items:
  random 172,316 against 120,434, reversed 194,800 against 9,999, nearly sorted 78,038 against 13,932).
- Primitive int collections instead of `Integer` lists, sets and maps: `RegexTranslator` (group bookkeeping,
  `IntArrayList`), `FormatNumber`/`FormatInteger`/`FormatDateTime` (separator positions, `IntArrayList`, `IntHashSet`,
  `IntIntMap`), `CaseVariants.VARIANTS` and `NumberFormatter` (`FAMILY_INDEX`, `ALPHABET_INDEX`, `IntObjectMap`).

Not proposed:
- the character maps (`SerializationParams`, `CharacterMapExtension`: public API with `Integer` keys),
  compile-time `Integer` lists in the XSD and XSLT compilers, type-only swaps of `IdentityHashMap` and `ArrayDeque`,
  `codePointCount (0, len)` (no stream) and `XdmParser.JDK_NAME_CHARS` (parse error path only).

## Suggestions from the ph-schematron v11 plan (2026-11-10)

Source: `../ph-schematron/docs/PLAN-phoxtrot-engine.md`. ph-schematron 11 runs its XSLT pipelines on Saxon or
phoxtrot. For `document()`, `doc()` and `unparsed-text()` at validation time it gives phoxtrot the same policy as
Saxon today (`SaxonSecureResourceResolver`): local URL schemes are always allowed, remote network schemes are blocked
unless explicitly allowed, and `jar:` URIs are checked against the nested URI. ph-schematron implements this as a
JAXP `URIResolver` that throws for a blocked URI, so it relies on the resolver paths of the JAXP adapter below.

### S27 - Resolver and entity resolver of the JAXP adapter: tests and gaps

Status: done (2026-11-10), tests in `JaxpResolverTest` and `XdmParserEntityResolverTest.testAccessExternalDtd`.
1. Factory attribute `ATTR_ENTITY_RESOLVER` and `PhoxtrotTransformer.setEntityResolver` (initially the factory's);
   every parser of the adapter (stylesheets, modules, sources, `document()`/`doc()`) is created from
   `JaxpParserSettings`.
2. `XdmParser.setAccessExternalDtd` (default `all`), set from the factory's `ACCESS_EXTERNAL_DTD`. The JDK parser
   applies it to the external DTD and external entities, not to a source the entity resolver returns (checked on JDK
   21), so with secure processing only the entity resolver gives access.
3. A `jar:` URI needs `jar` and its nested URI allowed, checked down every nesting level.
4. A refusing `URIResolver`: modules fail with XTSE0165 at the `xsl:include`/`xsl:import` with the resolver's message
   (`StylesheetLoader`, a resolver that throws was unlocated before); `resolveText` raises FOUT1170 with the message
   instead of returning `null`; `doc-available`/`unparsed-text-available` stay `false`.
5. A directly read document that is not well-formed is logged as a warning; the other paths have tests.

The direct parser API is well tested (`XdmParserEntityResolverTest`: external DTD and entity, `null` from the
resolver, `EntityResolver2`, `SAXException` and `IOException` from the resolver, no call without external entities).
The JAXP adapter has three tests for resolution (`PhoxtrotTransformerFactoryTest.testUriResolverForInclude`,
`testUriResolverForDocument`, `testSecureProcessingBlocksDirectAccess`). Missing:

1. **No entity resolver on the JAXP path.** `phoxtrot-xslt` never calls `XdmParser.setEntityResolver`. Documents
   read for `document()`/`doc()` and stylesheet modules (`JaxpResourceResolver`, `FileResourceResolver`) are parsed
   without one; only `ATTR_LOAD_EXTERNAL_ENTITIES` switches external DTDs and entities on or off. Proposal: a factory
   attribute (e.g. `ATTR_ENTITY_RESOLVER`) and `PhoxtrotTransformer.setEntityResolver`, passed to every parser of
   the adapter. Tests: a `document()`-loaded file with an external DTD and an external entity, with
   `ATTR_LOAD_EXTERNAL_ENTITIES` off (nothing read, resolver not called) and on (resolver called with the resolved
   system ID, its result used), the same for an `xsl:include`d module.
2. **`ACCESS_EXTERNAL_DTD` has no effect.** The factory accepts and returns it, but with
   `ATTR_LOAD_EXTERNAL_ENTITIES` on `XdmParser` sets `ACCESS_EXTERNAL_DTD` to `all` on the reader
   (`XdmParser.java:629`), so secure processing (which sets it to the empty string) does not stop external DTDs.
   Either pass the factory value through or document that it is ignored. Test: secure processing plus
   `ATTR_LOAD_EXTERNAL_ENTITIES` on, a `document()` with an external DTD.
3. **Nested schemes pass the scheme check.** `JaxpResourceResolver.isAccessAllowed` (line 96) checks only the outer
   scheme: with `ACCESS_EXTERNAL_STYLESHEET` = `jar`, `jar:http://host/x.jar!/a.xml` is read. Check the nested URI
   of `jar:` too (as `SaxonSecureResourceResolver.isResourceAccessAllowed` does). Tests: `jar:file:` allowed and
   `jar:http:` refused with `jar` allowed; `jar:jar:http:` refused.
4. **A `URIResolver` that refuses.** No test for a resolver that throws `TransformerException`, which is how a
   blocking resolver works. Tests for `xsl:include`/`xsl:import` (compile error with location), `document()`,
   `doc()` (FODC0002 with the resolver's message), `doc-available()` (`false` or error, as specified), and
   `unparsed-text()`/`unparsed-text-available()`. In `resolveText` (line 298) the FODC0002 of the resolver is caught
   as `RuntimeException` and becomes "unavailable", so the reason of the refusal is lost; `resolveDocument` keeps it.
   Proposal: let the `XPathException` of the resolver through in `resolveText` as well.
5. **Smaller paths without tests:**
   - a directly read document that is not well-formed returns `null` (line 200) without a log entry, so
     `document()` cannot tell "missing" from "broken";
   - a URI without a scheme is refused by `isAccessAllowed`;
   - schemes are compared case-insensitively (`FILE:` against `file`);
   - the `URIResolver` of the factory against the one set on the `Transformer` for `document()` at run time.

### S29 - API for a Schematron XPath binding (`ph-schematron-pure-xpath-phoxtrot`)

Status: done (2026-11-11), item 2 (b) moved to S33 (done).
1. The public API of `phoxtrot-xpath` is listed in the README ("XPath API") and in the `package-info` of
   `com.helger.phoxtrot.xpath`, source compatible within 0.x.
2. (a) `DomToXdm.convertWithMapping` returns a `DomNodeMapping` with the DOM node of every XDM node (a merged text node
   maps to the first DOM text node of its run).
3. No new API: a module with the `xsl:function`s is compiled with `XsltCompiler` and the expressions against
   `XsltExecutable.createStaticContext`, evaluated with `createEvaluation` (functions see their global variables and
   keys); documented and tested (`XsltEvaluationTest`).
4. `FunctionLibrary.register (XdmQName, List <SeqType>, SeqType, IXPathFunction)`; the conversion rules are in the
   Javadoc of `IXPathFunction`.
5. `DynamicContext.setMaxSteps` (counted in `checkInterrupt` and `enterCall`, unlimited by default);
   `LargeStackRunner` and `EThreading` (renamed from `EXsltThreading`) moved to `com.helger.phoxtrot.xpath`.
6. `CompiledXPath.getFreeVariables` (`ExprWalker.getFreeVariables`, scoped).
7. Static errors: `XPathException.getExpressionOffset`/`Line`/`Column`; errors in an `xsl:function`:
   `XsltException.getFunctionFrame` (innermost function). Dynamic errors have no position in the expression (the AST
   keeps none).

ph-schematron 11 splits its pure engine (Schematron evaluated rule by rule with XPath, no XSLT) into a core and one
XPath binding per processor. The Saxon binding (`ph-schematron-pure-xpath-saxon`) uses s9api: `XPathCompiler`,
`XPathExecutable`/`XPathSelector` with context item and variables, `ExtensionFunction`, user functions loaded from
XQuery modules (`XQueryAsXPathFunctionConverter`), and Saxon's `DocumentWrapper` and `NodeOverNodeInfo` to move
between W3C DOM and the XPath tree. The phoxtrot binding builds on `XPathCompiler.compile (String, StaticContext)`,
`CompiledXPath` (immutable, thread-safe), `StaticContext`, `DynamicContext` (`withFocus`, `withVariable`,
`withLazyVariable`, `setGlobalVariableResolver`, `setResourceResolver`, `setInterrupt`) and `FunctionLibrary.register`
with `IXPathFunction`. What is missing:

1. **Stable public XPath API.** The classes above sit next to internal ones (`ast`, `eval`, `parser`), and
   `FunctionLibrary`/`IXPathFunction` are in the `fn` package with the built-in functions. Declare (Javadoc, package
   documentation or a facade package) which types are public API in 0.x and keep them source compatible, so a
   binding does not break with every phoxtrot release.
2. **From an XDM node back to the DOM node.** The validation handler API of the pure engine reports the rule context
   node as `org.w3c.dom.Node`, and callers often validate a DOM they own. `DomToXdm.convert (Node, String)` gives the
   XDM counterpart of one DOM node, but there is no way back from an `IXdmNode` (a rule match) to the original DOM
   node, and `XdmToDom` copies whole trees. Proposal, either:
   - (a) `DomToXdm` with an optional mapping (`IXdmNode` → source `Node`, e.g. a `Function` or a stored user data
     reference on the XDM node), or
   - (b) a read-only DOM view over an `IXdmNode` tree (like Saxon's `NodeOverNodeInfo`) for input that was parsed
     with `XdmParser`, so no DOM has to be built at all.
   Both are needed for drop-in behaviour: (a) for DOM input, (b) for stream input.
3. **User functions without Java code.** The Saxon binding loads user functions from XQuery modules. phoxtrot has no
   XQuery. Proposal: let `XsltCompiler` (or a small helper) compile a stylesheet module and export its public
   `xsl:function`s into a `FunctionLibrary` that `XPathCompiler` can use (the functions keep their own static
   context, global variables and keys of that module). The same would serve `xsl:function` elements inside a
   Schematron schema (ISO query binding `xslt2`/`xslt3`). Plain XPath 3.1 function items bound to variables
   (`let $f := function ($x) {...}`) work already and need no API.
4. **Typed Java extension functions.** `FunctionLibrary.register` takes the signature as a string in F&O notation.
   For an extension function API comparable to s9api `ExtensionFunction` (name, argument and result types as objects,
   argument conversion done by phoxtrot), a typed overload (`XdmQName`, `List <SeqType>`, `SeqType`, implementation)
   and documented conversion rules (atomisation, untyped promotion, cardinality errors) are needed.
5. **Limits for plain XPath.** `XsltTransformRequest` has a step limit and the large stack threads
   (`LargeStackRunner` in `phoxtrot-xslt`); plain XPath evaluation has only the function call depth
   (`DynamicContext`, XPDY0130) and runs on the calling thread. Deeply nested `and`/`or` chains or recursive user
   functions in a Schematron rule can overflow the default stack. Proposal: a step limit on `DynamicContext`, and the
   large stack runner moved to `phoxtrot-xpath` (or `phoxtrot-xdm`) so an XPath caller can use it.
6. **Free variables of a compiled expression.** The Saxon binding sets only the variables an expression references
   (`XPathExecutable.iterateExternalVariables`). With phoxtrot every `sch:let` must be declared on the
   `StaticContext` before compiling, and `setGlobalVariableResolver` supplies values on demand. A
   `CompiledXPath.getFreeVariables ()` (the declared variables the expression actually uses) would let the binding
   skip unused `sch:let` values and report unused ones; not required, the resolver works without it.
7. **Error details.** `XPathException` carries the error code. For the SVRL and the error handler the binding also
   needs the position in the expression (column) and, for errors raised in a user function, the function name and
   module location, as `XsltException` gives for XSLT.

## Suggestions from the FACTUR-X EXTENDED profile (2026-11-11)

From ph-sch2java: a FACTUR-X 2.5.2 EXTENDED invoice with 1,788 lines (394 group lines) took 456 s with the
Schematron interpreter on phoxtrot (Saxon HE on the isosch XSLT: 211 s). Most of it was one rule test that
ph-sch2java now evaluates once per document; the remaining line rules are quadratic in the number of lines
(`BR-FXEXT-08`, `-11`, `-12`), with sub-expressions that do not change within a predicate or quantifier but are
evaluated for every candidate or item.

### S30 - Loop-invariant function calls

Status: done (2026-11-11). `CallMemo` (package `eval`): a static call of a built-in function whose value is
determined by the context item and the variables it references (`ExprWalker.isDeterminedByContextItem`: no
`position`/`last`, no function items or lookups, only the focus-free functions and the context functions without
argument, each resolving to the default implementation) and that has an argument with work (not only literals,
variables or `.`) is kept per run with the identity tokens of the bindings of its variables
(`DynamicContext.getVariableToken`, an over-approximation of the free variables by
`ExprWalker.getAllVariableReferences`) and the static context. A call that reads the focus is only looked up in the
body of the innermost `for`/`some`/`every` at the focus of that loop (`DynamicContext.getLoopFocus`), where the
focus stays the same between iterations; in a predicate inside the body it is another item and nothing is looked up.
One value per call expression and run; errors are not kept. The plan of a call site is kept in its
`FunctionCallSite` per library state like the resolution. Examples: `normalize-space($item/.../ram:LineID)` in the
predicate of `//ram:IncludedSupplyChainTradeLineItem[normalize-space(.../ram:ParentLineID) = normalize-space($item/.../ram:LineID)]`,
`normalize-space(ram:AssociatedDocumentLineDocument/ram:ParentLineID)` in
`some $p in //.../ram:LineID satisfies normalize-space($p) = normalize-space(ram:AssociatedDocumentLineDocument/ram:ParentLineID)`.

### S31 - No copy of a whole index list per step

Status: done (2026-11-11). A named child or descendant step from one node whose result is a complete list of the
document's name index (`AxisNavigator.getNamedIndexList`: the kept child elements of a node with many children, or
all elements with the name for `//name` from a node whose subtree holds all of them) becomes a sequence once per run
and list (`XPathEvaluator._getIndexListSequence`, run memo by list identity) instead of being copied on every step.
The generated code of ph-sch2java (`XRt.step`) does not use it yet; it has no dynamic context there.

### S32 - `AbstractXdmNode.getRoot` in `collectNamedChildren`

Status: closed (2026-11-11), not reproduced. A JFR profile of the interpreter showed 18 % self time in `getRoot`
below `AxisNavigator.collectNamedChildren`, but only one element of the invoice has more than 32 children, so it is
called about a thousand times per validation; after S30 and S31 it is 0.1 % of the samples. Taken as an attribution
artefact of the profile.

## Suggestions deferred from S29 (2026-11-11)

### S33 - Read-only DOM view over an XDM tree

Status: done (2026-11-11). `XdmDomView` (`com.helger.phoxtrot.xdm.bridge`): `new XdmDomView (aNode).getNode (aXdmNode)`
gives the DOM node of any node of the tree, `getDocument ()` the document, `XdmDomView.getXdmNode (aDomNode)` the way
back. One DOM node per XDM node and view (kept on first access, so DOM nodes compare by identity); namespace
declarations are `xmlns` attributes before the attributes; no document type node; mutating methods throw
`NO_MODIFICATION_ALLOWED_ERR`, node creation, `cloneNode`, `importNode` and user data `NOT_SUPPORTED_ERR`. Tested by
reading the view back with `DomToXdm` and the JDK serialiser (`XdmDomViewTest`).

S29 item 2 (b): for input that is parsed with `XdmParser` (or streamed), a pure Schematron engine that reports
`org.w3c.dom.Node`s would need a DOM without building one: a read-only implementation of `Node`, `Document`,
`Element`, `Attr`, `Text`, `Comment`, `ProcessingInstruction`, `NodeList` and `NamedNodeMap` over `IXdmNode` (like
Saxon's `NodeOverNodeInfo`), every mutating method throwing `DOMException.NO_MODIFICATION_ALLOWED_ERR`. Not needed for
DOM input, which `DomToXdm.convertWithMapping` covers.

### S34 - Public streamability analysis for XPath expressions and patterns

Status: done (2026-11-11), option 1. `XPathStreamabilityAnalyzer` in `com.helger.phoxtrot.xpath` (`analyze` for a
`CompiledXPath` or text, `analyzePattern` for a pattern text; `setContextStreamed`, `addStreamedVariable`,
`addFunction` with category and parameter types) returns an `XPathStreamabilityResult` (posture, sweep,
`EStreamabilityVerdict`, `XPathStreamabilityReason`). `EPosture`, `ESweep`, `PostureSweep` and
`EStreamFunctionCategory` became public API; only `phoxtrot-xpath` is needed. Deviations: the reason names the
construct (axis step, root, function, variable, positional predicate, expression kind) without offset, as the AST
keeps no positions; it is found by re-analysing sub-expressions with the context of the whole, so it is a hint, and
among several offending operands the one found is not necessarily the first in the text. Not done: running the XSLT
3.0 streamability cases through the facade.

ph-schematron wants to tell upfront whether, and how, a Schematron can be validated while streaming. Per rule the
question is: can the rule context (an XSLT pattern) be matched on the current node alone, and do the `sch:assert`,
`sch:report` and `sch:let` expressions need (a) nothing beyond the context node, its attributes and ancestors
(motionless: one pass, constant memory), (b) only the subtree of the context node (burst: one pass, the matched
subtree buffered), or (c) anything else (`//` from the root, `preceding`/`following`, keys or `id ()` over the
instance, document-wide variables): two passes or the whole tree. This is the XSLT 3.0 posture and sweep analysis
(section 19), which phoxtrot has in `com.helger.phoxtrot.xpath.stream` (`XPathStreamability.getStreamability (IExpr,
StreamContext)`, `getPatternStreamability`, `PostureSweep`, `EPosture`, `ESweep`, `StreamContext`,
`IStreamFunctionResolver`). The README lists `stream` as internal, and the entry points take AST nodes (`IExpr`,
`CompiledXsltPattern`) from internal packages.

Options:

1. **Public wrapper (preferred).** A small, source compatible facade in `com.helger.phoxtrot.xpath` (or
   `com.helger.phoxtrot.xpath.streamability`), for example `XPathStreamabilityAnalyzer`:
   - Input: a `CompiledXPath` (or expression text plus `StaticContext`) for tests and variables; a pattern text plus
     `StaticContext` for rule contexts (no `CompiledXsltPattern` needed by the caller).
   - Context: whether the context item is a streamed node (striding) or a buffered one (grounded), so a caller can
     ask both "streamable as is" and "streamable after buffering the context subtree" (burst).
   - Variables: declare a variable as grounded (an atomic or a value computed in an earlier pass) or as streamed
     nodes, so schema-level `sch:let`s can be classified as precomputable.
   - Functions: a stream category per extension function or `xsl:function` (as `IStreamFunctionResolver` does
     internally), with a documented default for unknown ones.
   - Result: an immutable value with posture and sweep (public enums, the existing ones or copies), a simplified
     verdict (motionless, consuming downward, free-ranging), and the reason: the first offending sub-expression with
     its offset in the expression text and the construct (axis, function, variable).
   - Only `phoxtrot-xpath` needed (no `phoxtrot-xslt`), so an analysis tool does not pull in the XSLT engine.
2. **Make the existing API public.** Declare `stream` public and add a way to get the AST of a `CompiledXPath`
   (`getExpression ()` exists, but `IExpr` is internal) and to compile a pattern publicly. Less work, but exposes the
   AST and the 2,700 line analyser as API, which then cannot change freely within 0.x.

Tests: the XSLT 3.0 streamability test cases that phoxtrot already passes, run through the facade; Schematron-like
cases (attribute test, child test, `//` test, `preceding-sibling` test, `count (//x)` against a grounded variable,
a pattern with a downward predicate `cac:InvoiceLine[cbc:Quantity < 0]`).

## Suggestions from the ph-schematron v11 implementation (2026-11-11)

### S35 - Nesting limit settable through the JAXP adapter

Status: done (2026-11-11), point 1; point 2 not taken (the default stays 10,000 in every threading mode). The cap
at the default in `Engine` is gone, so `XsltTransformRequest.setMaxDepth` can raise the limit;
`XsltTransformRequest.UNLIMITED_DEPTH` (`Integer.MAX_VALUE`) means no limit but the stack (an overflow is still
XPDY0130). JAXP: factory attribute `PhoxtrotTransformerFactory.ATTR_MAX_DEPTH` (initial value of the transformers,
there is no depth limit at compile time) and `PhoxtrotTransformer.setMaxDepth`; test
`PhoxtrotTransformerFactoryTest.testDepthLimit` (50,000 levels on `THREAD_POOL`).

The ISO Schematron include step (`iso_dsdl_include.xsl`) checks expressions with named templates that recurse about
once per character. For the EUGEN UBL T14 and T15 rules of the ph-schematron test files (a code list test of 23,537
characters in `include/EUGEN-T14-codes.sch`) the step fails on phoxtrot with
`XPDY0130: Templates and functions are nested more than 10000 levels deep`; Saxon (no nesting counter, only the Java
stack) converts them. The limit is `XsltTransformRequest.setMaxDepth` (default 10,000), which the JAXP adapter does
not expose: the factory has `ATTR_MAX_STEPS`, but no attribute for the depth, so ph-schematron cannot raise it.

Proposal:

1. A factory attribute `ATTR_MAX_DEPTH` (a `Number` or a decimal string, 0 for the engine default, a value for no
   limit, like `ATTR_MAX_STEPS`), also applied to compilations, and `PhoxtrotTransformer.setMaxDepth`.
2. Consider a higher default (or no depth limit) when the transformation runs on a thread with the large stack
   (`NEW_THREAD`, `THREAD_POOL`), where a `StackOverflowError` is turned into XPDY0130 anyway; the depth counter then
   only cuts off legitimate deep recursion earlier than the stack would.

ph-schematron would set it like the step limit: no limit by default (as Saxon), configurable.

Test: a stylesheet with a named template recursing 50,000 times, run through `PhoxtrotTransformerFactory` with the
attribute set, on `THREAD_POOL`.

### S36 - Reject `xsl:result-document` under secure processing

Status: done (2026-11-11), as proposed below. `XsltCompileOptions.setSecondaryResultsAllowed` (default `true`,
`DEFAULT_SECONDARY_RESULTS_ALLOWED`), checked in `ResultDocumentExtension._compile`; `PhoxtrotTransformerFactory` sets
it to `!FEATURE_SECURE_PROCESSING` for each compilation. A nested `fn:transform` inherits it, at run time from the
caller's compile options and in a static expression (`use-when`, static parameters) from the options of the
compilation, which `StylesheetLoader` now passes as the host of the static dynamic context (so such a call also
inherits the other options, e.g. external entities and the backwards compatible claim). Tests:
`SecondaryResultsAllowedTest` (the cases of the table, an included module, `fn:transform` at run time and in a static
parameter) and `PhoxtrotTransformerFactoryTest.testSecureProcessingRejectsResultDocumentHref`.

With `XMLConstants.FEATURE_SECURE_PROCESSING` Saxon refuses to compile a stylesheet that contains
`xsl:result-document` (the secondary output destinations are disabled), so ph-schematron reports such a Schematron
XSLT as invalid (`SchematronSecureProcessingTest.testResultDocumentIsRejected`). phoxtrot compiles it and keeps the
secondary results in memory (`PhoxtrotTransformer.getLastResult ()`); nothing is written, but the stylesheet is
accepted. For the same behaviour on both processors, the JAXP adapter should reject an `xsl:result-document` with an
`href` at compile time when secure processing is on (a static error such as XTSE0010-style "not allowed", or a
phoxtrot specific code), and accept it as before otherwise.

Test: the `result-document.xslt` of ph-schematron (`xsl:result-document href="secondary.xml"`): `newTemplates` fails
with secure processing, succeeds without.

**Comparison with Saxon-HE 13.1** (black box through its JAXP factory, 2026-11-11). With
`FEATURE_SECURE_PROCESSING` Saxon fails `newTemplates` with XTSE0010 "xsl:result-document is disabled when extension
functions are disabled", located at the element, for every `xsl:result-document` that has an `href` attribute:

| Stylesheet | Saxon, secure processing | Saxon, default |
|---|---|---|
| `href="secondary.xml"` | XTSE0010 at compile time | runs, writes `secondary.xml` next to the result |
| `href=""` | XTSE0010 | runs, the content goes to the principal result |
| `href="{'x' \|\| '.xml'}"` (AVT) | XTSE0010 | runs, writes `x.xml` |
| in a named template that is never called | XTSE0010 | runs |
| under `xsl:if test="false()"` | XTSE0010 | runs |
| with `use-when="false()"` | compiles and runs | compiles and runs |
| without `href` | compiles and runs (principal result) | compiles and runs |
| only `format` | compiles and runs | compiles and runs |

So the check is static and depends only on the presence of the attribute, not on its value or on whether the
instruction is reached; `use-when` removes the element before it.

**Proposal:**

1. `XsltCompileOptions.setSecondaryResultsAllowed (boolean)` (default `true`). With `false`, compiling an
   `xsl:result-document` that has an `href` attribute (any value, an AVT included) is the static error XTSE0010
   located at the element, as in Saxon; one without `href` stays allowed. The check goes into
   `ResultDocumentExtension._compile`, so elements removed by `use-when` are not affected, and it covers included and
   imported modules and packages. A nested `fn:transform` inherits the setting (like the backwards compatible claim),
   so a stylesheet cannot get around it.
2. `PhoxtrotTransformerFactory` sets it to `false` while `FEATURE_SECURE_PROCESSING` is on (no separate attribute), and
   to `true` again when the feature is switched off.
3. Error code: XTSE0010 as Saxon does. The specification has no code for a disabled instruction; XTSE0010 is the code
   for an XSLT element that is not allowed in its place, which fits closest, and a phoxtrot specific code would make
   ph-schematron tell the two processors apart for no gain.

Note: the difference is about behaviour, not risk. phoxtrot's JAXP adapter never writes secondary results (they stay
in `PhoxtrotTransformer.getLastResult ()`), so secure processing loses nothing there that it needs to protect.

Tests: the eight stylesheets of the table through `PhoxtrotTransformerFactory`, with and without secure processing;
an `href` in an included module; `fn:transform` of such a stylesheet from a secure run.

### S37 - `generate-id()` values like Saxon

Status: closed (2026-11-11), not implemented: phoxtrot keeps its own scheme (following Saxon's would need its
sources, which phoxtrot does not use, and differs by tree model anyway). The form changed with S38; documented in
README "Implementation-dependent values".

ph-schematron treats Saxon as the reference: every difference of phoxtrot is recorded here. The ISO Schematron step
`iso_abstract_expand.xsl` builds the IDs of patterns instantiated from abstract patterns as
`concat(generate-id(.), $is-a)`, so these IDs end up in the created XSLT and in the SVRL (`active-pattern/@id`).
Saxon 13.1 gives e.g. `w141aac11spacesAtStartEnd` (for a node of a wrapped W3C DOM), phoxtrot `id1spacesAtStartEnd`
(ph-schematron test files `CellarBook.sch` and `schematron-svrl.sch`; ph-schematron normalises them in its comparison
tests for now). The values are implementation-dependent by the specification, so this is about compatibility, not
conformance.

To decide in phoxtrot: produce the same values as Saxon for the same input (which would mean following Saxon's
scheme, also for its different tree models: TinyTree, DOM wrapper), or keep the own scheme and document the
difference. If the scheme is kept, ph-schematron keeps the normalisation and documents the difference for users who
compare SVRL across processors.

### S38 - `generate-id()` values collide with IDs of the input

Status: done (2026-11-11). Addendum to S37. `fn:generate-id` gives `d<tree>n<node>` (e.g. `d1n5`): the number of
the tree (root) in the run and the number of the node in it, both in the order of the first request
(`FnAccessor._generateId`). F&O allows only ASCII letters and digits, so the form cannot use a separator that IDs
rarely contain; it is still unlike `id1`-style author IDs.

SchXslt 1 and 2 use `generate-id()` for pattern IDs, template names, modes and map keys. phoxtrot's values (`id1`,
`id2`, ...) have the same form as IDs that authors write themselves: for `example-assert.sch` of the ph-schematron
test files (an `assert` with `id="id1"`) the XSLT created on phoxtrot has the generated pattern ID `id1` next to the
assert ID `id1`; Saxon's values (`w84148aab7` on a wrapped DOM, `d84976e9` on its own tree) do not collide there.
The SVRL is still the same in that case, but a stylesheet that mixes generated and author IDs in one key space can
behave differently. If S37 keeps an own scheme, it should at least use a form that is unlikely in documents (e.g. with
a document number and a character that IDs rarely contain, like Saxon's `d<doc>e<node>`).

### S39 - `URIResolver.resolve` with the original `href` and the base URI

Status: done (2026-11-11). API change: every `IResourceResolver` method takes a `ResourceUri` (`absoluteUri`, `href`
as written, `baseUri`), filled at every call site (include/import, `document()`, `fn:doc`, `unparsed-text`, `json-doc`,
collections, `xsl:source-document`, `xsl:merge-source`, `fn:transform`, `parameter-document`, `schema-location`;
a package location and the streamed principal source have no reference and pass the URI as both).
`JaxpResourceResolver` calls `URIResolver.resolve (href, base)` with `""` for no base and reads the absolute URI
itself when it returns `null`. Tests: `PhoxtrotTransformerFactoryTest.testUriResolverForInclude` (with and without
system ID) and `testUriResolverForDocument`.

The JAXP adapter calls `URIResolver.resolve (href, base)` with the URI it resolved itself as both arguments (README:
"with the absolute URI the engine resolved as href and as base"). JAXP specifies `href` as the attribute value as
written (relative or absolute) and `base` as the base URI in effect, and Saxon calls it that way. The difference
breaks resolvers that resolve relative references against their own default base when the stylesheet has no base
URI: ph-schematron compiles the XSLT created from a Schematron from a DOM without system ID and uses a
`DefaultTransformURIResolver` with the Schematron's directory as default base. For
`<xsl:include href="include.xsl"/>` (ph-schematron `Issue077Test`, Schematron with `allow-foreign`) Saxon calls
`resolve ("include.xsl", "")`, the resolver uses its default base and finds the file; phoxtrot calls
`resolve ("include.xsl", "include.xsl")`, the resolver treats `include.xsl` as the base, the include is not found
(XTSE0165) and the Schematron is reported as invalid.

Proposal: call `resolve` with the `href` as written and the base URI of the module (or of the instruction for
`document()`/`doc()`/`unparsed-text()`), `null` or `""` if there is none, as Saxon does; resolve against the base only
when the `URIResolver` returns `null`.

Test: a stylesheet without system ID with `<xsl:include href="inc.xsl"/>` and a `URIResolver` that records its
arguments: it must receive (`inc.xsl`, `""` or `null`); with a system ID `file:/a/main.xsl` it must receive
(`inc.xsl`, `file:/a/main.xsl`).

### S40 - JAXP `DOMResult`: no serialisation, no namespace map copies

Status: items 1 and 2 done (2026-11-11), items 3 and 4 see the measurement below. Item 1:
`XsltTransformRequest.setPrincipalTreeOnly`, set by `PhoxtrotTransformer` for `DOMResult` and `SAXResult`
(`principalSerialized ()` is then empty). Item 2: `IXdmNode.declaredNamespaces ()`, a read-only view without copying,
used by `MarkupWriter`, `XdmToDom`, `XdmToSax`, `XdmSaxEmitter`, `XdmDomNamedNodeMap`, `NodeCopier` and `FnJson`.

Profile of ph-schematron's `BenchmarkProcessorSize.validateDom` (ISO Schematron, 20,000 items, compiled once; JFR,
2026-11-11, phoxtrot `7fab8a1`): 56.9 ms per validation on phoxtrot against 42.3 ms on Saxon-HE 13.1. Split by phase
(share of the CPU samples, converted to ms per validation):

| Phase | Saxon | phoxtrot |
| ----- | ----: | -------: |
| Input: DOM navigation (Saxon wraps the DOM) / `DomToXdm` (phoxtrot) plus the XSLT execution | 32.3 | 42.5 |
| ... of which serialising the result (`XsltTransformer._run` → `XdmSerializer.serialize`) | - | 9.3 |
| ... of which `DomToXdm` | - | 6.1 |
| Result tree to the `DOMResult` (Saxon `DOMWriter`, phoxtrot `XdmToDom`) | 8.0 | 10.4 |
| Reading the SVRL DOM with JAXB (same code on both) | 1.9 | 4.0 |

Without the serialisation and the input conversion, phoxtrot's XSLT execution itself (about 27 ms) is not slower than
Saxon's execution on the wrapped DOM (32.3 ms). The overhead is around it:

1. **Unused serialisation for a `DOMResult`.** `PhoxtrotTransformer._transformToDom` calls
   `XsltExecutable.transform (aRequest)` without raw delivery, so `XsltTransformer._run` builds the result tree *and*
   serialises it into `XsltTransformResult`'s string, which `_transformToDom` never reads. 139 of 846 samples (16 %).
   Proposal: request the tree only (like `isRawDelivery`, but with the tree) for `DOMResult` and `SAXResult`, or build
   the DOM directly from the result events.
2. **`AbstractXdmNode.getDeclaredNamespaces ()` copies a `CommonsLinkedHashMap` on every call**
   (`CommonsLinkedHashMap.getClone`), and it is called per element by `MarkupWriter._writeStartTag` (74 samples) and
   `XdmToDom._createElement` (61 samples), together 135 samples (16 %, overlapping with item 1). Proposal: a read-only
   view or an iteration method (`forEachDeclaredNamespace`) for internal callers; most elements declare none, so an
   empty-check before the copy already avoids most of it.
3. **Input conversion.** `DomToXdm` takes about 6 ms for 20,000 elements (`XdmTreeBuilder.startElement`,
   `ArrayDeque.addFirst`, `XdmTreeBuilder.<init>` per document); `XdmDocumentNode._buildNameIndex` adds 4 % of the
   execution samples. Saxon avoids the conversion by navigating the DOM through a wrapper.
4. **Not explained:** reading the same SVRL DOM with JAXB takes 4.0 ms after phoxtrot and 1.9 ms after Saxon; the DOM
   built by `XdmToDom` has the same elements, attributes, namespace declarations and text nodes as the one of Saxon's
   `DOMWriter` (checked on a small SVRL). Possibly a JIT or measurement effect; to be checked once items 1 and 2 are done.

Allocation per validation is about twice Saxon's (148 MB against 71 MB for 20,000 items). Items 1 and 2 cause
14 % of the sampled allocated bytes (serialisation 11 %, map copies 9 %, overlapping); most of the rest is allocated
by the XSLT execution itself (68 % of the samples including the serialisation).

**Measured after items 1 and 2** (2026-11-11; JMH `BenchmarkProcessorSize.validateDom`, 20,000 items, the
benchmark jar built once against phoxtrot `7fab8a1` and once against the change, same machine, one after the other):

| | `7fab8a1` | with items 1 and 2 | Saxon-HE 13.1 |
| --- | ---: | ---: | ---: |
| ms per validation | 56.3 ± 1.5 | 46.7 ± 1.6 | 42.2 ± 0.4 |
| MB allocated per validation | 148 | 124 | 72 |

Split of the new profile (JFR execution samples, all threads): XSLT execution 62.5 %, result tree to DOM 18.6 %,
`DomToXdm` 13.1 %, JAXB 5.7 % (Saxon: execution 73.3 %, DOM writing 24.2 %, JAXB 2.0 %). The remaining gap of about
4.5 ms is about the input conversion (13.1 % of 46.7 ms = 6.1 ms).

Item 4: the DOMs are the same. For 20,000 items both processors' SVRL DOMs are `DocumentImpl` with 20,003
`ElementNSImpl`, 20,004 attributes, 5 namespace attributes, one comment, no text nodes, and reading them with
`SVRLMarshaller.read` in one JVM takes the same time (about 21 ms each, also for a deep copy of phoxtrot's DOM made by
`importNode`). The JAXB difference in the benchmark (2.7 against 0.9 ms, all in `DOMScanner.visit` self time) is
therefore not caused by the DOM phoxtrot builds but by the state of the run around it (JIT, memory).

Item 3: no DOM wrapper (decided 2026-11-11). `IXdmNode` is sealed (`permits AbstractXdmNode`) and the engine relies on
its own node model (document order numbers, name indexes, numbered tree builders), so a wrapper would be a second node
model, which `CLAUDE.md` rules out. `DomToXdm` was tuned instead: the walk follows the sibling and parent links instead
of pushing two records per node, the SAX attributes object is reused, the namespace map is only created for an
element with declarations, and DOM text goes to the new `XdmTreeBuilder.characters (String)` without a `char []`
copy. For the 20,000 items of the benchmark (about 100,000 DOM nodes) one conversion went from 2.62 ms and 18.3 MB
allocated to 2.29 ms and 8.1 MB (best of 200 runs after 100 warm-up runs, one JVM).

### S41 - XSLT execution hotspots of a Schematron validation

Status: items 1 (see S42), 2, 3 (in part) and 4 done (2026-11-11).

* Item 2: `CompiledXsltPattern._matchesUnionPaths` creates the context with the node as current item only for a path
  that needs it (`PatternMatcher.DirectPath.hasPredicates`, or the general search); `/root/item`, `text()` and
  `node()` are matched with the caller's context. No node-kind index for the other rules (`canMatch` already
  rejects `text()` for an element cheaply).
* Item 3: the elements of one literal result element share one map of namespace declarations, built at compile time
  (`IXdmNode.declareSharedNamespaces`; `AbstractXdmNode.internalDeclareNamespace` copies it before the first change of
  an element). The declarations themselves are unchanged (no namespace reducer). The fixup walk was not changed:
  measured alone on an SVRL-shaped tree of 20,001 elements (best of 300 runs, one JVM), `fixNamespaces` takes
  0.15 ms with shared maps (0.23 ms with one map per element) and `assignDocumentOrder` 0.13 ms, so carrying the
  scope down the walk or merging it with the numbering could save a fraction of 0.3 ms at most.
* Item 4: the premise did not hold. A child lookup only uses the index for a parent with more than 32 children
  (`AxisNavigator.MIN_CHILDREN_FOR_INDEX`), here the root with its 20,000 `item` children; that lookup built the
  index of all 40,001 elements. `XdmDocumentNode.getChildElementsByName` now keeps the child lists on their own
  (per numbering) and fills a list from one pass over the children of the parent, or from the element index if that
  is built already; it never builds the index itself.

Measured with S42 (2026-11-11; JMH `BenchmarkProcessorSize.validateDom`, 20,000 items, the benchmark jar built once
against phoxtrot `a996fee` and once against the change, same machine, one after the other):

| | `a996fee` | with S41 and S42 | Saxon-HE 13.1 |
| --- | ---: | ---: | ---: |
| ms per validation | 45.9 ± 0.7 | 37.5 ± 0.8 | 41.8 ± 1.0 |
| MB allocated per validation | 111 | 79 | 72 |

Profile of ph-schematron's `BenchmarkProcessorSize.validateDom` after S40 (ISO Schematron, 20,000 items, compiled once;
JFR, 2026-11-11, phoxtrot `a996fee`, Saxon-HE 13.1): 46.1 ms per validation on phoxtrot against 43.8 ms on Saxon,
111 MB allocated against 72 MB. Split by phase (share of the CPU samples, converted to ms per validation):

| Phase | Saxon | phoxtrot |
| ----- | ----: | -------: |
| Input: DOM navigation through the wrapper (Saxon) / `DomToXdm` (phoxtrot) | 16.5 | 5.3 |
| XSLT execution | 16.9 | 28.7 |
| Result tree to the `DOMResult` | 8.7 | 7.9 |
| Reading the SVRL DOM with JAXB (same code on both) | 1.7 | 4.2 |

Input plus execution is now on par (33.4 against 34.0 ms). The JAXB difference of S40 item 4 is not a property of the
DOM: in one JVM, reading the two SVRL DOMs alternately (also directly after each transformation) takes the same time.
No action proposed for it.

phoxtrot's execution (404 samples, about 28.7 ms) has these hotspots:

1. **Union of child steps in every `xsl:apply-templates`.** `XPathEvaluator._evalSet` takes 92 samples (23 %, about
   6.5 ms, including the child steps themselves). Details and measurement in S42.
2. **Template rule search.** `Engine._findRule` takes 63 samples (16 %, about 4.5 ms), 37 of them in
   `CompiledXsltPattern.matches` (`_matchesUnionPaths`, `PatternMatcher$DirectPath.match`,
   `DynamicContext.withCurrentItem`, `PatternMatcher._canAxisSelect`). The patterns of the mode are tested one after
   the other for every node. Proposal: check whether `Mode.getNamedRuleIndexes` pre-filters path patterns like
   `/root/item` and the fallbacks (`text()`, `@*|node()`) by node kind and name, and avoid a new dynamic context per
   pattern test.
3. **Namespace declarations of literal result elements.** Each `svrl:fired-rule` declares all five in-scope namespaces
   of the stylesheet (`CoreInstructions` line 389 → `XdmElementNode.declareNamespace`: a new `CommonsLinkedHashMap` per
   element, and the namespace node cache is dropped per declaration): 23 samples (about 1.6 ms), 2.6 % of the sampled
   allocation. Afterwards `NamespaceFixup.finish` walks the finished tree twice: `fixNamespaces` (`_isBound` →
   `lookupNamespaceUri` through the ancestors for every element, 28 samples) and `assignDocumentOrder` (15 samples),
   together 43 samples (11 %, about 3 ms). The resulting DOM has the declarations on the root only. Proposal: do not
   declare on a literal result element a namespace that its parent in the result tree already binds to the same URI
   (like Saxon's namespace reducer), or share one immutable map per instruction; and fix and number in one walk.
4. **Name index of the input document.** The first child lookup by name (`AxisNavigator.getNamedIndexList` →
   `XdmDocumentNode._buildNameIndex`) indexes all elements of the document: 13 samples (about 0.9 ms); measured alone
   0.6 ms and 1.8 MB for the 40,001 elements. In a single validation pass every lookup is on a parent with one or two
   children. Proposal (low priority): use the index only for descendant searches or parents with many children.

The interrupt check (`PhoxtrotTransformer` line 562, `aCaller.isInterrupted ()`) shows up with 7 samples (2 %).

Note on JFR allocation samples: their weights overrate large arrays. The name index appeared with 8.7 % of the sampled
bytes (about 9.7 MB per validation), but one build allocates 1.8 MB.

### S42 - Union of steps on the same axis as one step

Status: done (2026-11-11), items 1 to 3. The parser's `IExpr.SetExpr` derives `stepUnion ()` (`IExpr.StepUnion`, the
node tests of the `attribute::` and of the `child::` operands) for a union whose operands are all relative paths of
one such step without predicates, or unions of them (the 3-argument constructor keeps every call site).
`XPathEvaluator._evalStepUnion` then scans the attributes and the children of the context node once each and keeps
a node that passes any test of its axis: attributes before children is document order, and one scan gives no
duplicates, so nothing is sorted. The errors stay those of the steps (XPDY0002, XPTY0020). Other axes and steps with
predicates keep the general evaluation. The operator name is only built for the XPTY0004 message. Measurement: see
S41.

The ISO Schematron skeleton (`iso_schematron_skeleton_for_saxon.xsl`, variable `context-xpath`, used in the
`xsl:apply-templates` of the fallback template `match="@*|node()"`, i.e. for every node of the input) selects

* `*|comment()|processing-instruction()` by default,
* `@*|*|comment()|processing-instruction()` if a rule context contains `@` or `attribute`,
* `*` if a rule context contains `(`.

Its comment says that `node()[not(self::text())]` is slower in Saxon than the union, so Saxon optimises the union.
phoxtrot evaluates it literally (`XPathEvaluator._evalSet`): a left-deep binary `SetExpr` tree, where every level
evaluates both operands (one scan of the child axis per step), checks them with `_checkNodes`, concatenates them and
sorts and deduplicates them with `NodeSorter.sortAndDeduplicate`. In addition, `_evalSet` computes
`aExpr.op ().name ().toLowerCase (Locale.ROOT)` on every evaluation, although the name is only used in the error
message of `_checkNodes` (8 samples and 3 % of the sampled allocation in the S41 profile).

Measurement (2026-11-11, phoxtrot `a996fee`, Saxon-HE 13.1, JDK 21; a stylesheet with the skeleton's template
structure in mode `M0`: `match="/root/item"` with priority 1000, `match="text()"` priority -1, `match="@*|node()"`
priority -2, each applying templates to the select below; input 20,000 `item` elements with a `value` child and
whitespace text, passed as `DOMSource`, so the input conversion is included; best of 15 runs after warm-up, one JVM):

| `select` | Saxon [ms] | phoxtrot [ms] |
| -------- | ---------: | ------------: |
| `*` | 5.0 | 6.3 |
| `*\|comment()\|processing-instruction()` | 5.9 | 9.1 |
| `node()[not(self::text())]` | 6.1 | 9.1 |
| `node()` | 6.2 | 8.7 |
| `@*\|*\|comment()\|processing-instruction()` | 18.7 | 14.0 |

The union costs phoxtrot 2.8 ms over `*` (Saxon 0.9 ms); the predicate form is no faster on phoxtrot. With attributes
phoxtrot is faster than Saxon.

Proposal:

1. At compile time, rewrite a union (`|`/`union`) whose operands are all single-step relative paths on the same axis
   without predicates into one step with a union node test (e.g. `child::(element() | comment() |
   processing-instruction())` as an internal node test that matches if any of the tests matches). One scan of the
   axis gives the nodes in document order without duplicates, so no sorting is needed.
2. For operands on different axes from the same context where the order is known statically, concatenate without
   sorting: `@*|child::...` is "attributes, then children", which is document order (attributes of an element precede
   its children).
3. Compute the operator name in `_evalSet` only when `_checkNodes` raises the error (or use a constant per `ESetOp`).

Saxon is the reference for the result, which must not change: the same nodes in document order without duplicates.


### S43 - Remaining hotspots after S41 and S42: namespace fixup pass and name index

Measured 2026-11-11 with phoxtrot `c09e6c0` (S36, S41, S42), Saxon-HE 13.1, JDK 21. Effect of S41 and S42 on
ph-schematron's `BenchmarkProcessorSize` (ISO Schematron, compiled once; JMH, `-prof gc`):

| Input | Items | Saxon [ms] | phoxtrot `a996fee` [ms] | phoxtrot `c09e6c0` [ms] | Alloc Saxon / `c09e6c0` [MB] |
| ----- | ----: | ---------: | ----------------------: | ----------------------: | ---------------------------: |
| DOM | 1,000 | 2.00 | 2.18 | 1.83 | 3.8 / 4.0 |
| DOM | 20,000 | 42.52 | 46.46 | 38.48 | 71.5 / 79.1 |
| stream | 20,000 | 47.12 | 52.15 | 44.69 | 85.9 / 94.2 |

The S42 measurement again (same stylesheet and input): `*|comment()|processing-instruction()` 9.1 → 6.3 ms (Saxon
5.9 ms), `@*|*|comment()|processing-instruction()` 14.0 → 8.1 ms (Saxon 19.0 ms).

JFR profile of the DOM validation with 20,000 items (37.6 ms on phoxtrot, 42.4 ms on Saxon; share of the CPU samples
converted to ms per validation): input conversion 5.9 ms (Saxon navigates the DOM through its wrapper: 16.7 ms),
execution 23.6 ms (Saxon 14.4 ms), result tree to DOM 5.2 ms (Saxon 8.8 ms), JAXB 3.0 ms (Saxon 2.4 ms). The execution
(385 samples) has these hotspots:

1. **`NamespaceFixup.finish` is now the largest single item:** 56 samples (15 % of the execution, about 3.4 ms). It
   walks the finished result tree twice: `fixNamespaces` (43 samples; `_fixElement` → `_isBound` →
   `AbstractXdmNode.lookupNamespaceUri`, one `LinkedHashMap.get` per element, 27 samples) and `assignDocumentOrder`
   (13 samples). Both run after the whole tree is built, so the nodes are no longer in the cache. Proposals: (a) mark
   the elements that can need a fixup (`xsl:element`, `xsl:copy`, namespaced attributes added later) and skip the
   others: a literal result element whose shared namespace map binds its own prefix and those of its attributes is
   correct by construction; (b) or fix an element when it is attached to its parent, when the parent's bindings are at
   hand; (c) number the nodes while the tree is built (in document order, as the builder appends), instead of a
   second pass.
2. **Name index built for an empty check:** `XPathEvaluator._isEmptyByNameIndex` (from a path in a function argument,
   `_evalFunctionCallDirect` → `_evalPath`) calls `XdmDocumentNode.getElementsByName`, which builds the index of all
   elements of the input document: 16 samples (about 1 ms; 0.6 ms and 1.8 MB measured alone for 40,001 elements). In
   a single validation pass the name occurs (`value` in `number(value)`), so the shortcut never pays off. Proposal: use
   the shortcut only if the index already exists, or build it only for descendant searches.
3. **Template rule search** (`Engine._findRule`): 76 samples (20 %, about 4.6 ms), spread over `_isRuleMatch`,
   `CompiledPattern.matches`, `PatternMatcher._canAxisSelect`, `DirectPath.match`, `Mode.getNamedRuleIndexes` and the
   step counting of `checkInterrupt`, without a single hotspot (no method above 7 samples). Only an observation; S41
   item 2 did not change it measurably (63 of 404 samples before, 76 of 385 now).

### S44 - Burst context mode for the streamability facade, and XSLT function signatures for analysis hosts

Status: done (2026-11-12), items 1 and 2. `EStreamContextMode` (`STREAMED`, `BURST`, `GROUNDED`) via
`XPathStreamabilityAnalyzer.setContextMode`, verdict `EStreamabilityVerdict.BURST` (new constant; `isStreamable ()`
now means motionless or consuming, `isBurstStreamable ()` is anything but free-ranging), `addExternalNodeVariable`;
the walk is `com.helger.phoxtrot.xpath.stream.XPathBurstAnalysis` (regions per value: context node, below it,
ancestors, attributes of ancestors, external nodes; fails closed). A burst verdict carries the reason of the streamed
analysis, a free-ranging one the construct that leaves the subtree. Deviations from the proposal: the result of the
expression must not contain ancestors (the host may atomise it); `fn:current-group`, `fn:current-merge-group` and
the accumulator functions are free-ranging (fail closed); function items and map or array entries must not hold nodes
of the document. Item 2: `FunctionLibrary.createDefaultWithXsltSignatures ()`, the signatures of `phoxtrot-xslt`,
calls raise XPST0017 (as the XSLT functions outside a transformation do).

ph-schematron wants a report that sorts each Schematron rule into one of three classes (decided 2026-11-12: report only,
in a new module `ph-schematron-streamability` that depends on `phoxtrot-xpath` only):

| Class | Meaning | Example test |
|---|---|---|
| Motionless | everything is known at the start tag of the context element; constant memory | `@currencyID = 'EUR'` |
| Burst | only the subtree of the context element is needed; it is buffered, checked and dropped | `cbc:A = 'x' or cbc:B = 'y'` |
| Non-local | data outside the subtree is needed: the whole tree or a second pass | `//cbc:ID`, `../cbc:X`, `key('k', @id)` |

The line between burst and non-local is the one that matters: most rules of the Peppol and EN 16931 rule sets look
at one line or party and its children, and a rule set that is all burst can be validated element by element on any
document size.

`XPathStreamabilityAnalyzer` (S34, phoxtrot `cc8329b`) cannot draw that line. Measured 2026-11-12 with a probe
(`analyze` with the expression text; `cbc`, `cac`, `u` declared; `key`, `current`, `document` registered as stand-ins):

| Expression | Streamed context (default) | `setContextStreamed (false)` |
|---|---|---|
| `@id = 'x'` | MOTIONLESS | MOTIONLESS |
| `cbc:ID`, `count(cac:Line) > 0` | CONSUMING | MOTIONLESS |
| `cbc:A = 'x' or cbc:B = 'y'`, `exists(cbc:A) and exists(cbc:B)` | FREE_RANGING | MOTIONLESS |
| `sum(cac:L/cbc:A) = cbc:Total` | FREE_RANGING | MOTIONLESS |
| `let $x := @id return $x = 'a'`, `every $l in cac:Line satisfies $l/cbc:Q > 0` | FREE_RANGING | MOTIONLESS |
| `../cbc:ID`, `//cbc:ID`, `/Invoice/cbc:ID`, `preceding-sibling::cac:Line` | FREE_RANGING | MOTIONLESS |
| `key('k', @id)` | FREE_RANGING | MOTIONLESS |
| `u:check(@id)` (undeclared extension function) | FREE_RANGING | FREE_RANGING |

Both answers are correct for what they ask, but neither is the burst question:

- A streamed context applies the XSLT 3.0 rules for one forward pass without buffering, so two downward reads or a
  variable bound to streamed nodes is free-ranging, the same class as `//` and `key ()`.
- A grounded context is a node with the whole tree in memory, so every axis is available and `//cbc:ID` or
  `../cbc:ID` is motionless. This was the "streamable after buffering the context subtree" question of S34; it
  cannot tell the subtree from the rest of the tree.

**Proposal:**

1. **A burst context mode.** For example `XPathStreamabilityAnalyzer.setContextMode (EStreamContextMode)` with
   `STREAMED` (today's default), `BURST` and `GROUNDED` (today's `setContextStreamed (false)`; the boolean setter can
   stay as a shortcut). In `BURST` the context node is the root of a buffered subtree, as after `copy-of (.)`, but
   keeps its place in the document:
   - The context node, its attributes and every node below it are grounded: any number of reads, any downward
     axis, predicates, variables and higher-order use are fine.
   - The ancestors of the context node and their attributes are available as in streaming (name, attributes,
     `ancestor::x/@id` motionless); their other children and anything reached from them are not.
   - Not available (free-ranging): the root (`/`, `//` from the root, `root ()`), `parent::` and `ancestor::` followed
     by a downward or sideways step, the `preceding`/`following` and sibling axes from the context node,
     `fn:key`, `fn:id`, `fn:idref`, `fn:element-with-id` over the instance (also with an explicit third argument
     that is not a node of the subtree), and positional patterns that need the siblings.
   - `fn:doc`, `fn:document`, `fn:collection` and literal values stay as today (other documents are not the streamed
     one).
   - The verdict: `MOTIONLESS` if the expression is motionless with a streamed context (as today), else `BURST`
     (new constant, or a separate `isBurstStreamable ()` on the result, whichever keeps the result simpler) if it is
     streamable in `BURST` mode, else `FREE_RANGING`. The reason then names the construct that leaves the subtree
     (`../`, `//`, `preceding-sibling::`, `key#2`), which is what the report shows the user.
   - Variables: a variable declared with `addStreamedVariable` is treated as a node of the subtree in `BURST` mode; a
     new `addExternalNodeVariable (XdmQName)` declares one bound to nodes outside the subtree (a schema-level
     `sch:let` computed from the whole document), so navigating from it is free-ranging while comparing its atomised
     value stays fine. Undeclared variables stay grounded values as today.
   - Patterns: `analyzePattern` is unchanged (a rule context is matched on the streamed node; a pattern predicate
     that needs the subtree stays free-ranging, as `cac:InvoiceLine[cbc:Quantity < 0]` today).
2. **XSLT function signatures without `phoxtrot-xslt`.** A host that analyses XPath from an XSLT-based language
   (Schematron with `queryBinding="xslt2"`/`"xslt3"`) needs `fn:key`, `fn:current`, `fn:document`,
   `fn:system-property`, `fn:unparsed-entity-uri`, `fn:unparsed-entity-public-id`, `fn:element-available`,
   `fn:function-available`, `fn:type-available`, `fn:regex-group`, `fn:current-group`, `fn:current-grouping-key`,
   `fn:copy-of`, `fn:snapshot`, `fn:accumulator-before`, `fn:accumulator-after` and `fn:available-system-properties`
   in the static context, which `FunctionLibrary.createDefault ()` does not have. Today the host registers stand-ins
   with hand-written signatures (as `XPathStreamabilityTest._createStaticContext` does). Proposal: a public factory in
   `phoxtrot-xpath`, e.g. `FunctionLibrary.createDefaultWithXsltSignatures ()` (or a method on the analyser that
   returns a suitable `StaticContext`), with the signatures of XSLT 3.0 section 5.4.2 and implementations that raise
   a dynamic error if called, for analysis only. The table of section 19.8.9 in `XPathStreamability` already knows
   these names.

Tests: the rows of the table above in `BURST` mode: the first five rows `BURST` (or `MOTIONLESS` for `@id = 'x'`), the
sixth and seventh `FREE_RANGING` with the reasons `parent::`/`//`/root/`preceding-sibling::` and `key#2`, the extension
function `FREE_RANGING`; `ancestor::cac:X/@id` motionless; `let $p := .. return $p/cbc:X` free-ranging; a variable
declared with `addExternalNodeVariable`: `$v = @id` burst, `$v/cbc:X` free-ranging. For item 2: compiling `key('k',
@id)`, `current()/@id` and `document('x.xml')` with the new library, and calling one raises the dynamic error.

### S45 - Burst mode: external node variables in streamed-motionless expressions, burst patterns, the document element

Status: done (2026-11-12), items 1 to 3. Item 1: in `BURST` mode an expression that references an external node
variable goes through the burst walk even if it is motionless when streamed (`$ext/cbc:X = 'a'` free-ranging with
reason `$ext`, `$ext = @id` motionless). Item 2: in `BURST` mode `analyzePattern` checks each alternative without the
predicates of its last step with the pattern rules, and those predicates as non-positional and burst
(`XPathBurstAnalysis.getPredicateBurstFailure`, effective boolean value, so they may return ancestors); predicates of
other steps stay under the streamed rules, and every predicate goes through the burst walk when the pattern references
an external node variable. Item 3 (decided 2026-11-12: the context node is assumed to be no document node, in
`STREAMED` and `BURST`): new opt-in `StreamContext.setDocumentElementAncestor`, set by the facade, never by the XSLT
layer, analyses the first step of `/x` (child axis, element test) as `ancestor-or-self::x`; the burst walk has a root
region whose element child is an ancestor. `/cac:Doc/@a = 'x'`, `/*/@version`, `name (/*)` motionless,
`/cac:Doc/cac:Title`, `//x`, `/comment()` and `string (/*)` free-ranging. For a rule context `/` the host evaluates
such a test after the start tag of the document element.

Found 2026-11-12 while building `ph-schematron-streamability` against phoxtrot `8297590` (S44).

1. **Navigating from an external node variable is motionless if the expression is motionless when streamed.** The
   Javadoc of `addExternalNodeVariable` says "navigating from its nodes is free-ranging", and
   `testBurstVariablesAndFunctions` checks `$ext/cbc:X = cbc:ID` (free-ranging). But burst mode first takes the
   streamed verdict, where the variable is grounded, so an expression that reads nothing of the subtree stays
   motionless:

   | Expression (`BURST`, `$ext` declared with `addExternalNodeVariable`) | Verdict | Expected |
   |---|---|---|
   | `$ext/cbc:X = cbc:ID` | FREE_RANGING | FREE_RANGING |
   | `$ext/cbc:X = 'a'` | MOTIONLESS | FREE_RANGING (`$ext`) |
   | `$ext/cbc:X` | MOTIONLESS | FREE_RANGING (`$ext`) |
   | `$ext = @id` | MOTIONLESS | MOTIONLESS or BURST (comparing is fine) |

   Proposal: in `BURST` mode, check the external node variables (navigation from them) before taking the motionless
   verdict of the streamed analysis, or run the burst walk whenever an external node variable is referenced.

2. **Patterns ignore the burst mode.** `analyzePattern` gives `cac:InvoiceLine[cbc:Quantity < 0]` free-ranging in
   every mode. A burst validator can match the name on the streamed node, buffer the subtree of every candidate and
   evaluate the predicate on it, so a pattern whose predicates need only the subtree of the matched node (and the
   ancestors as in streaming) is burst. Proposal: in `BURST` mode `analyzePattern` gives `BURST` for such a pattern
   (non-positional predicates analysed with the burst rules and the matched node as context), and stays
   `FREE_RANGING` for positional predicates, rooted paths through predicates that leave the subtree
   (`cac:Line[../cbc:X]`), `key ()` and the like. Patterns without such predicates stay motionless as today.

3. **The document element is an ancestor.** In `BURST` mode every path from the root is free-ranging (reason
   `root /`), also `/ex:Document/@classification` and `name (/*)`. The document element is an ancestor-or-self of
   every node but the document node, so its name and attributes are available as for `ancestor::*` (motionless), and
   from a context that is the document node it is a child (burst). Seen in ph-schematron's
   `check-classifications.sch` (xfront): three rules with `ex:Para[...]` contexts that only read
   `/ex:Document/@classification`. Proposal: in `BURST` (and `STREAMED`) mode, treat `/` followed by a child step to
   an element and then only attribute steps, `name ()`/`local-name ()`/`node-name ()` or nothing, like the same steps
   on the ancestor axis (`ancestor-or-self::ex:Document[not (parent::*)]/@classification`). A child step from the
   document element (`/ubl:Invoice/cbc:ID`) stays free-ranging.

Tests: the rows of the table for item 1; for item 2 `cac:InvoiceLine[cbc:Quantity < 0]` and
`cac:A[count (cac:B) > 1]` burst, `cac:Line[1]`, `cac:Line[../cbc:X]` and `cac:Line[key ('k', @id)]` free-ranging,
`cac:Line` and `cac:Line[@id]` motionless, all in `BURST` mode, the `STREAMED` results unchanged; for item 3
`/ex:Document/@classification = 'x'`, `/*/@version` and `name (/*)` motionless with a streamed or burst element
context, `/ex:Document/ex:Title` and `//ex:Para` free-ranging.

### S46 - Streamability: declare a variable with an already analysed binding

Status: done (2026-11-12). `XPathStreamabilityAnalyzer.analyzeBinding (String|CompiledXPath)` returns an immutable
`XPathStreamabilityBinding` (the compiled binding and `getResult ()`, the binding analysed on its own) and
`addBoundVariable (XdmQName, XPathStreamabilityBinding)` declares it. Deviation from the proposal: the binding object
does not hold a precomputed posture, sweep or burst region; `analyze` wraps the expression in a `let` clause with the
stored bindings (in declaration order, all of them, used or not, as the written form), so the binding is not parsed
again but is analysed again for each expression. That makes verdict, posture, sweep and reason equal to the written
`let` form by construction, in all three modes, which a precomputed value would have to reproduce in both analyses and
the reason search. Patterns are not wrapped (a bound variable is grounded there, like an undeclared one).

Found 2026-11-12 in `ph-schematron-streamability` (phoxtrot `17a53fb`).

A host that analyses many expressions sharing the same `let` bindings (the `sch:let`s of a Schematron rule are used by
all its assertions and messages) must put them in front of every expression as `let $a := (...), $b := (...) return`,
so that their values are analysed where they are used. The binding expressions are then parsed and analysed again for
every expression. `XPathStreamabilityAnalyzer` can declare a variable only as streamed (`addStreamedVariable`) or as an
external node variable (`addExternalNodeVariable`), not as a variable whose binding has a known streamability.

Where: `XPathStreamabilityAnalyzer` (`_createContext`, `analyze`, the burst walk), the `LetExpr` branch of
`stream/XPathStreamability` (binding as navigation operand) and of `stream/XPathBurstAnalysis` (`_bind`/`_unbind` with
the region of the binding); `stream/StreamContext` has one posture and sweep for all streaming parameters.

Proposal: `XPathStreamabilityBinding analyzeBinding (String sExpr, StaticContext aSC)` returns an immutable object with
the posture and sweep of the binding (streamed analysis) and its burst region or failure, and
`addBoundVariable (XdmQName aName, XPathStreamabilityBinding aBinding)` declares a variable with it. A binding may
reference variables bound before. `analyze` treats a reference to such a variable exactly as if the expression were
wrapped in the `let` clause, including the reason (`expression let`, or a new reason naming the variable).

Tests: for expressions E and bindings X, Y, `analyze ("let $a := (X), $b := (Y) return E")` and `analyze ("E")` after
`addBoundVariable` for `$a` and `$b` give the same verdict, posture, sweep and reason in all three context modes; with
bindings that are motionless, consuming, burst (descendants), free-ranging (`parent::x`, `//x`) and unused.

### S47 - Regular expressions with category escapes are slow on JDK 24 and later

Found 2026-11-13 in `ph-schematron-benchmarks` (`BenchmarkProcessorCompile`, phoxtrot `422534e`; the same with
`a996fee`).

The SchXslt 1 and SchXslt 2 conversions of a Schematron with abstract patterns take 18 ms on JDK 25 against 7.2 ms on
JDK 21, with the same jars (ISO Schematron: 7.3 ms on both). The stack profile on JDK 25 has about 30 % of the run time
in `java.util.regex.Pattern.lambda$union$0/$1`, which does not show on JDK 21. The trigger is SchXslt's replacement of
abstract pattern parameters, `replace ($src, concat ('(\W*)\$', $name, '(\W*)'), ...)`, applied to every expression once
per parameter: `\W` is translated with `UnicodeCategories.getClassExpression` for `P`, `Z` and `C`.

Cause: `UnicodeCategories` pins the categories to Unicode 15.0 and corrects the built-in `\p{..}` classes by the code
points whose category in the running JDK differs. JDK 21 has Unicode 15.0, so there is no difference and `\p{C}` stays
5 characters long. JDK 25 has Unicode 16.0: 5,813 code points differ (5,812 of them unassigned in 15.0 and assigned in
16.0, the first is U+0897), and the class expressions grow to `C` 759, `L` 356, `N` 147, `Nd` 148 and `P` 123
characters. `java.util.regex.Pattern` turns every range of a bracketed class into one more nested union predicate, so
each character tested by `\W*` walks a chain of hundreds of predicates.

Where: `regex/UnicodeCategories` (`_buildExpression`, `getClassExpression`), `regex/RegexTranslator` (`\w`, `\W`, `\d`,
`\D`, `\p{..}`, `\P{..}`), and the place where the translated `Pattern` is compiled and matched (`fn/FnRegex`).

Proposal: keep the Unicode 15.0 semantics, but match with the fast built-in classes whenever the input cannot tell the
difference. When the translation of a regular expression used a corrected category expression, also compile the
uncorrected translation (built-in `\p{..}` only). Before matching, scan the input once for a code point from the
difference set (a `BitSet` or a sorted array of the differing code points, built in `UnicodeCategories.Holder`); if
there is none, which is the case for any input below U+0897, use the uncorrected pattern, otherwise the corrected one.
Both patterns can be cached with the translated expression. A cheaper partial measure: put the built-in class in front
of the added runs (`[\p{P}...runs]` instead of `[runs...\p{P}]`), so that members of the built-in class do not walk the
runs; this does not help for characters outside the class.

Tests: `matches`, `replace`, `tokenize` and `analyze-string` with `\w`, `\W`, `\d`, `\p{L}`, `\P{C}` give the same
results with both patterns on ASCII input and on input with a code point that is unassigned in Unicode 15.0 and
assigned in the running JDK (e.g. U+0897 on JDK 24 and later); a benchmark of `replace ($s, '(\W*)\$name(\W*)', ...)`
on a 1 KB ASCII string is on JDK 25 within 20 % of JDK 21.

Status: implemented as proposed. `RegexTranslator` records whether a category class needed a correction and then
repeats its last pass with the built-in classes (`Result.builtInJavaPattern`); `XPathRegex` compiles both, and
`getPattern (String)` (used by `matcher`, so by `fn:matches`, `replace`, `tokenize`, `analyze-string` and
`xsl:analyze-string`, and by `XsdPattern`) picks the built-in one when `UnicodeCategories.containsDifference` finds no
changed code point (sorted array, binary search only for code points from the first difference on; about 120 ns per KB
of ASCII on JDK 25). The partial measure (built-in class in front) was not done. Measured with the replacement of the
proposal on 1 KB of ASCII: JDK 21 14.5 µs, JDK 25 12.4 µs (corrected pattern alone on JDK 25: 264 µs). A benchmark that
runs the corrected pattern in the same JVM slows every pattern down (the predicate call sites in `java.util.regex`
become megamorphic), so it must not be measured after it. Tests: `XPathRegexTest.testBuiltInCategoryPattern`,
`UnicodeCategoriesTest.testContainsDifference`, `FnRegexTest.testUnicode15Categories` (also run on JDK 25).
