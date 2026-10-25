# Port plan

Reference implementation: go-xslt (see NOTICE). Gate for every phase: the W3C suites, run by name-diffed fail lists.
Suites are cloned next to this repo (`../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`) and are never committed.

Decisions:

* Apache 2.0, Java 21 (CI also on 25), `com.helger.xpath`, no `ph-` artifact prefix.
* XML parsing: JDK SAX. XSD validation: JDK, XSD 1.0 only. XSD 1.1 is out of scope.
* Own XDM tree (`XdmNode`). `IMicroDocument` from ph-xml becomes an optional adapter module, not the model.
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
| X2 | XSLT instruction families and XSLT functions (number, grouping, merge, iterate, try/catch, result-document, accumulators, evaluate, keys, ...) | `instr_*.go`, `functions.go` | in progress |
| X3 | Packages, `fn:transform`, backwards compatibility, streaming, schema-awareness (Xerces-J vs. own XSD), JAXP `TransformerFactory` | `packages.go`, `fn_transform.go`, `stream_*.go`, ... | |

XSLT30 status (2026-10-25, after X1): 7,504 of 8,647 applicable cases pass (86.8%) as a basic processor; the
remaining fails are mostly the X2 families and packages (X3). Extension API for the families: `xslt-core/INSTRAPI.md`.

QT3 status (2026-10-24): 22,328 of 22,478 applicable cases pass (99.3%), 150 fail, all documented in
`xpath-conformance/docs/accepted-failures/qt3.md` (`QT3Test` fails on any undocumented failure):

| Group | Fails | Status |
|-------|-------|--------|
| `fn:transform` | 87 | Open: needs the XSLT engine |
| XQuery modules | 34 | Accepted: no XQuery (same in go-xslt) |
| Static typing (type inference) | 20 | Accepted: same 20 cases as go-xslt |
| Schema-validated sources | 7 | Open: needs content types and element declarations, which the JDK validator does not expose |
| External entity in `fn:parse-xml`, network access | 2 | Accepted (same in go-xslt) |

Schema-awareness: the JDK validator gives type annotations, typed values, nilled and schema IDs (`XdmParser.setSchema`).
Apache Xerces-J was evaluated (2026-10-24): it would add content types and element declarations (6 more QT3 cases)
but is dormant since 2022 and takes over JAXP application-wide. Deferred to the XSLT work, where
`xsl:import-schema` needs element declarations anyway; decide then between Xerces-J (optional, without xml-apis and
xml-resolver) and porting go-xslt's own XSD validator.

Known JDK differences found so far:

* JDK 25 secure processing limits the element depth to 100, JDK 21 does not. `XdmParser` sets the limit explicitly (default 10,000).
* JDK 25 secure processing allows only 2,500 entity expansions (JDK 21: 64,000). `XdmParser` pins all JAXP entity
  limits to the JDK 21 defaults; `XdmParserEntityExpansionTest` runs go-xslt's expansion attacks against them.
