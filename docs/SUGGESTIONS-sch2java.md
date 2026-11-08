# Suggestions from ph-sch2java

Suggestions for phoxtrot that came up in ph-sch2java (Schematron interpreter and Schematron → Java generator on top of
phoxtrot), and from S9 on in the plan for a ph-schematron engine on phoxtrot. Proposals only;
the numbering continues the earlier suggestions S1 to S6 and S8 (all done, see `PLAN.md`).

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

**Measured with ph-sch2java** (generated code, best warm run of six, three alternating rounds, load about 1): TSR with
50,000 subtotals 2,506-2,602 ms (`main`) against 2,558-2,720 ms (`sch-s7`); TSR with 10,000 395-421 against 406-416 ms;
end user statistics report with 10,000 subsets 403-423 against 404-413 ms. This confirms the measurement of phoxtrot: no
gain.

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

ph-schematron offers per-rule execution telemetry on Saxon through a `TraceListener` (template enter and leave). phoxtrot
has only the global `fn:trace` listener. Proposal: a per-run listener for template and instruction enter and leave,
with the instruction's module URI, line and match pattern, and no cost when not set.

### S16 - XSLT 1.0 backwards compatible mode

Open in `PLAN.md` X3. The ISO pipeline creates XSLT 2.0, so the plan does not need it. Prebuilt Schematron XSLT files
are often XSLT 1.0 (`iso_svrl_for_xslt1`) and today run with XPath 3.1 semantics, e.g. XPTY0004 where XSLT 1.0 takes
the first node. This mode is the precondition for a phoxtrot variant of ph-schematron's prebuilt-XSLT engine.

### S17 - Java baseline

Decided (2026-11-08): phoxtrot keeps Java 21 as baseline. ph-schematron stays on Java 17 and builds its phoxtrot
module in a Maven profile that is active from JDK 21, so its CI matrix stays 17, 21 and 25. No change in phoxtrot.

### S18 - Size of the XDM tree

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
