# Port plan

Reference implementation: go-xslt (see NOTICE). Gate for every phase: the W3C suites, run by name-diffed fail lists.
Suites are cloned next to this repo (`../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`) and are never committed.

Decisions:

* Apache 2.0, Java 21 (CI also on 25), `com.helger.xpath`, no `ph-` artifact prefix.
* XML parsing: JDK SAX. XSD: schema-awareness and the QT3 harness use Apache Xerces-J 2.12.2 (optional dependency of
  `xpath-xdm`, without xml-apis, XSD 1.0); without it the JDK validator (`JdkSchemaProvider`, validation only). XSD 1.1 is out of scope.
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
| X2 | XSLT instruction families and XSLT functions (number, grouping, merge, iterate, try/catch, result-document, accumulators, evaluate, keys, ...) | `instr_*.go`, `functions.go` | done |
| X3 | Packages, `fn:transform`, backwards compatibility, streaming, schema-awareness (Xerces-J vs. own XSD), JAXP `TransformerFactory` | `packages.go`, `fn_transform.go`, `stream_*.go`, ... | packages, `fn:transform` and schema-awareness (Xerces-J, opt-in) done; rest open |

XSLT30 status (2026-10-25, after packages and `fn:transform`): 8,637 of 8,647 applicable cases pass (99.9%) as a basic
processor, identical on JDK 21 and 25; all 10 fails are documented in `xslt-conformance/docs/accepted-failures/xslt30.md`
(`XSLT30Test` fails on any undocumented failure). Extension API for the families: `xslt-core/INSTRAPI.md`.

QT3 status (2026-10-26): 22,419 of 22,478 applicable cases pass (99.7%), 59 fail, all documented in
`xpath-conformance/docs/accepted-failures/qt3.md` (`QT3Test` fails on any undocumented failure):

| Group | Fails | Status |
|-------|-------|--------|
| `fn:transform` wanted unavailable | 3 | Accepted: `fn-transform-901/902/err-14` (same in go-xslt) |
| XQuery modules | 34 | Accepted: no XQuery (same in go-xslt) |
| Static typing (type inference) | 20 | Accepted: same 20 cases as go-xslt |
| External entity in `fn:parse-xml`, network access | 2 | Accepted (same in go-xslt) |

Schema-awareness (2026-10-26): Apache Xerces-J, optional and loaded only when present (`XdmSchemaSupport`), is the
schema processor. `-Dxslt30.schemaAware=true` runs XSLT30 schema-aware: 9,292 of 9,310 pass
(`xslt-conformance/docs/accepted-failures/xslt30-schema-aware.md`); the default run is unchanged (8,637/8,647).
With `-Dqt3.schemaProcessor=jdk` the QT3 harness uses the JDK validator instead, and the 7 schema-validated source
cases fail again.

## Native XSD validator

Port of go-xslt's `internal/xsd` (about 16,700 lines of Go, 99.99% of `xsdtests` for XSD 1.0 and 1.1), decided
2026-10-26. Why Xerces-J is needed today and why it is only the fallback: [SCHEMA.md](SCHEMA.md).

* New module `xsd-core` (package `com.helger.xsd`, depends on `xpath-core`) plus `xsd-conformance` for the harness.
* XSD 1.0 first; the version switch stays in the code so that XSD 1.1 (assertions and conditional type assignment on
  our XPath engine, `xs:override`, open content) can follow without a rework.
* The native validator becomes the default schema provider. Xerces-J stays as an optional second provider and as the
  differential reference.
* XSLT and XPath only need the first error. The public validation API that collects all errors comes after the switch-over.

| Phase | Content | Go reference | Status |
|-------|---------|--------------|--------|
| V0 | Schema SPI independent of JAXP: `XdmSchemaType` holds an `IXdmTypeDefinition` instead of `TypeInfo`; no `getJaxpSchema`/`ValidatorHandler` access; validation parses with the JDK and then validates the tree in place; Xerces provider adapted, QT3 schema cache off JAXP. Gate: unchanged numbers | - | done: JAXP only in the `jaxp` and `xerces` packages, `JdkSchemaProvider` and the Xerces-J provider share `AbstractJaxpSchemaSet` |
| V1 | `xsd-conformance`: `xsdtests` harness, accepted-failures gate, first run against Xerces-J as baseline | `test/conformance/xsd_test.go` | done: Xerces-J baseline XSD 1.0 39,145 of 39,401 (256 fails documented), same denominator as go-xslt; the native validator plugs in through `IXsdTestValidator` |
| V2 | Loader and component model, include/import/redefine, schema-for-schemas checks, resolver and file-access rules | `loader.go`, `structure.go`, `components.go`, `imports.go`, `model.go`, `docs.go`, `schemavalid.go` | done: `xsd-core` with stubs and `xsd-core/XSDAPI.md` for V3 to V6; native run 0 fails (schema tests 2,144 pass, 12,226 unsupported; instance tests all unsupported). With permissive stubs the schema-test fail list equals go-xslt's (1,307 by name) |
| V3 | Simple types and facets (reusing casting and the XSD regex grammar) | `simpletype.go`, `facets.go`, `lexscan.go` | open |
| V4 | Content models: position-set NFA, linear matcher above 600 children, UPA, restriction derivation | `content.go`, `bigmatch.go`, `upa.go`, `restriction.go` | open |
| V5 | Instance validation: attributes, identity constraints, `xsi:type`/`nil`/`schemaLocation` hints | `instance.go`, `attributes.go`, `identity.go` | open |
| V6 | Bridge as an `IXdmSchemaProvider`, in-place vs. from-text differential test over the whole suite, switch XSLT30 schema-aware and QT3 to it, compare by name with Xerces-J | `bridge.go` | open |
| V7 | Public validation API: all errors in document order, content-model explanations, `msg:message` | `collect.go`, `explain.go`, `messages.go` | open |
| V8 | XSD 1.1 | `xsd11.go`, `override.go`, version branches | later |

V3 to V5 run as parallel worktree agents once V2 is merged. Gate for each: the XSD 1.0 fail list by name, with
go-xslt's 5 documented fails as the target, plus the XSLT30 (default and schema-aware) and QT3 lists.

Known JDK differences found so far:

* JDK 25 secure processing limits the element depth to 100, JDK 21 does not. `XdmParser` sets the limit explicitly (default 10,000).
* JDK 25 secure processing allows only 2,500 entity expansions (JDK 21: 64,000). `XdmParser` pins all JAXP entity
  limits to the JDK 21 defaults; `XdmParserEntityExpansionTest` runs go-xslt's expansion attacks against them.
