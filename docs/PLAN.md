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
| 6 | QT3 harness, triage, accepted-failures file | `test/conformance/qt3_test.go` | harness done, triage open |
| 7+ | XSLT 3.0: compiler, runtime, serialiser, packages, JAXP and s9api facade, streaming, schema-awareness | `internal/xslt` | |

QT3 status (2026-10-24): 22,227 of 22,478 applicable cases pass (98.9%), 251 fail:

| Group | Fails | Cause |
|-------|-------|-------|
| `fn-transform*` | 85 | needs the XSLT engine (go-xslt passes them) |
| `fn-load-xquery-module` | 33 | no XQuery; permanent, also in go-xslt |
| `numberformat*`, `*format-number*` | 36 | the harness does not yet pass the environment's decimal formats to `StaticContext` |
| `ST-Axes*` and other `staticTyping` cases | ~25 | go-xslt implements the narrow static axis analysis behind a parse flag |
| schema-dependent (`fn-nilled`, typed values, FOTY0012) | ~20 | no schema-awareness |
| `xsd-version`/`xml-version` 1.0 vs 1.1 variants | ~12 | the harness does not apply these dependencies yet |
| the rest | ~40 | individual triage pending |

Known JDK differences found so far:

* JDK 25 secure processing limits the element depth to 100, JDK 21 does not. `XdmParser` sets the limit explicitly (default 10,000).
* JDK 25 secure processing allows only 2,500 entity expansions (JDK 21: 64,000). `XdmParser` pins all JAXP entity
  limits to the JDK 21 defaults; `XdmParserEntityExpansionTest` runs go-xslt's expansion attacks against them.
