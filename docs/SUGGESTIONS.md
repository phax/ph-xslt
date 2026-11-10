# Suggestions

Suggestions for phoxtrot from projects that use it, from S9 on from the plan for a ph-schematron engine on phoxtrot.
Proposals only; the numbering continues the earlier suggestions S1 to S6 and S8 (all done, see `PLAN.md`).

## S7 - Simple xsl:function bodies as XPath conditional (key indexes and calls)

Status: measured, not implemented (2026-11-06, `PLAN.md` "Function calls, conversions and key patterns"). A working
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

Status: done (2026-11-09) except item 5 for text values (too slow); input documents can be parsed with `XdmParser.setRecordLocations (false)`; see `PLAN.md` batch 4.

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
aggregate functions (only the type dispatch is saved, the parse stays). Item 3 is open: it needs the function
libraries of a stylesheet frozen after compilation.

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
