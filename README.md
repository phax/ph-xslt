# phoxtrot

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> and no release has been published yet. Do not use it in production.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing is left to the JDK; everything on top of it, including an XML Schema 1.0 validator, is implemented here.
phoxtrot was previously called ph-xslt.

Status: work in progress. The XPath 3.1 engine runs and passes 99.7% of the applicable W3C QT3 test cases
(22,419 of 22,478). The XSLT 3.0 engine passes 99.9% of the applicable W3C xslt30-test cases as a basic
processor (8,640 of 8,647). The native XML Schema validator passes 99.99% of the applicable W3C xsdtests cases for
XSD 1.0 (39,396 of 39,401) and XSD 1.1 (41,596 of 41,600). Schema-awareness and streaming are available as opt-ins
(streaming: the streamability analysis, streamed execution of guaranteed-streamable constructs and output written while
it is built); backwards compatibility is not done yet.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `phoxtrot-xdm` | XPath data model: node tree built from SAX, incremental StAX reader for streamed documents, names, document order | usable |
| `phoxtrot-xpath` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `phoxtrot-xpath-conformance` | W3C QT3 test harness (not deployed) | usable |
| `phoxtrot-xslt` | XSLT 3.0: stylesheet compiler, modules, templates and modes, all instruction families, XSLT functions, packages, schema-awareness, streaming (analysis, streamed execution, output written while it is built) | in progress |
| `phoxtrot-xslt-conformance` | W3C xslt30-test harness (not deployed) | usable |
| `phoxtrot-xsd` | Native XML Schema 1.0 and 1.1 validator (port of go-xslt's `internal/xsd`): loader, include/import/redefine/override, schema-for-schemas checks, simple types and facets, content models with open content, instance validation with identity constraints, assertions and conditional type assignment, read-only component view, validation API that reports all errors; the default schema provider for XPath and XSLT (schemas compiled as XSD 1.1) | usable |
| `phoxtrot-xsd-conformance` | W3C xsdtests (XML Schema) harness with a pluggable validator (not deployed) | usable |
| `phoxtrot-xslt-benchmark` | JMH benchmarks and profiling workloads for parsing, XPath, XSLT, XSD, streaming and compiling (standalone, not in the reactor, not deployed; see its `README.md` and `docs/benchmarks`) | usable |
| `phoxtrot-xdm-memory-benchmark` | Retained heap of the node tree compared with ph-xml MicroDOM and Saxon TinyTree (standalone, not deployed) | usable |

Requires Java 21. CI runs on Java 21 and 25.

## Schema-awareness (optional dependency)

Schema-awareness (`XsltCompileOptions.setSchemaAware (true)`: `xsl:import-schema`, `[xsl:]validation`/`[xsl:]type`,
typed sources, `schema-element()` and user-defined types in XPath) needs a schema processor on the class path. Without
one the engine is a basic XSLT 3.0 processor. Two providers are supported:

* **The native validator** (module `phoxtrot-xsd`, recommended): no further dependencies; imported schemas are compiled as
  XSD 1.1, so assertions, conditional type assignment, open content and `xs:override` work. It is used whenever it is
  on the class path.
* **Apache Xerces-J** (`xerces:xercesImpl` 2.12.2, an optional dependency of `phoxtrot-xdm`): the fallback when
  `phoxtrot-xsd` is absent, or when the system property `xdm.schemaProvider=xerces` is set. XSD 1.0 only. Add it yourself, excluding
  `xml-apis` (the JDK provides those APIs). Note that the presence of the jar alone changes what
  `SAXParserFactory.newInstance ()` and `SchemaFactory.newInstance (...)` return for other code in the same
  application (its `META-INF/services` entries).

In both cases XML parsing stays on the JDK parser. `phoxtrot-xsd` can also be used on its own to validate documents
(`XsdSchema.validate`, which reports all errors), against XSD 1.0 or 1.1.

```xml
<dependency>
  <groupId>com.helger.phoxtrot</groupId>
  <artifactId>phoxtrot-xsd</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

[docs/SCHEMA.md](docs/SCHEMA.md) explains how the provider is selected, what the JDK validator cannot provide, and why
Xerces-J is only the fallback next to the native validator.

## Origin and acknowledgements

This library is a port of [go-xslt](https://github.com/tim-riep/go-xslt) by Tim Riep (Apache 2.0), a from-scratch Go
implementation of XSLT 3.0, XPath 3.1 and XSD. Its architecture, its test strategy and large parts of its behaviour are the
basis of this project. See [NOTICE](NOTICE).

# News and noteworthy

v0.1.0 - work in progress
* Initial project setup
* Added the module `phoxtrot-xdm` with `IXdmNode`, `XdmQName`, `XdmParser` and `XdmTreeBuilder`
* Added the module `phoxtrot-xpath` with the XPath 3.1 engine and the F&O 3.1 function library
* Added the module `phoxtrot-xpath-conformance` that runs the W3C QT3 test suite; every failing case is documented with its
  reason in `phoxtrot-xpath-conformance/docs/accepted-failures/qt3.md`, and an undocumented failure fails the build
* Added the module `phoxtrot-xslt-conformance` that runs the W3C xslt30-test suite as a basic XSLT 3.0 processor (no streaming,
  schema-awareness or backwards compatibility), with the same applicable cases as go-xslt's default run
* Added `StaticContext.setStaticTyping` to opt in to the narrow part of the optional Static Typing Feature: axis steps that provably select nothing raise XPST0005
* Added XSLT 3.0 patterns (`XsltPatternParser`, `CompiledXsltPattern`): predicate and selection patterns, rooted patterns, union/intersect/except, default priorities and union alternatives
* Added the XML and XSD version (`StaticContext.setXmlVersion`/`setXsdVersion`) and the default language (`DynamicContext.setDefaultLanguage`) settings
* Added schema-validated input: `XdmParser.setSchemaSet` validates the parsed tree in place (`IXdmSchemaSet.validateDocument`) and annotates it (`XdmSchemaType`, nilled, schema IDs), which gives typed values, `fn:nilled`, schema-based `fn:id`/`fn:element-with-id`/`fn:idref` and `element(N, T)`/`attribute(N, T)` tests; invalid documents raise `XdmValidationException`
* `XdmParser` always uses the JDK parser, also when another JAXP implementation such as Apache Xerces is on the class path
* Added the module `phoxtrot-xslt` with the XSLT 3.0 compiler and engine (`XsltCompiler`, `XsltExecutable`): include/import, static processing (use-when, static parameters, shadow attributes), template rules and modes, all basic instructions, stylesheet functions, keys, attribute sets, the XSLT functions, step and depth budgets, and an extension API for instruction families (`phoxtrot-xslt/INSTRAPI.md`)
* Added `xsl:number`: `level` single/multiple/any, `count`/`from` patterns, `value`, `select`, `start-at`; format tokens for any Unicode decimal digit family, alphabetic, Roman, Greek and Unicode numbering sequences, and words (English, German, French, Italian) with ordinals and grouping
* Added `xsl:for-each-group` (group-by, group-adjacent, group-starting-with, group-ending-with, composite keys, collations, sorted groups) with `current-group()` and `current-grouping-key()`
* Added `xsl:merge` with `current-merge-group()` and `current-merge-key()`, `xsl:iterate` with `xsl:break`, `xsl:next-iteration` and `xsl:on-completion`, and `xsl:fork` (not streamed)
* Added `xsl:result-document` (all serialization attributes as attribute value templates, `format`, `parameter-document`, `current-output-uri()`, XTDE1480/1490/1500) and `xsl:character-map` with `use-character-maps`
* Serializer: empty XHTML 5 elements get an end tag, `xmlns=""` is written on prefixed elements, top-level disable-output-escaping text is kept, and the generated Content-Type meta uses the character map
* Regular-expression category escapes (`\p{..}`, `\d`, `\w`) and the `i` flag are pinned to Unicode 15.0, so results are identical on JDK 21 and 25
* Regular expressions use the XSD 1.1 character-class grammar unless XSD 1.0 is set explicitly
* `FunctionLibrary` can register a separate implementation for dynamic function calls (`current-group#0` raises XTDE1061, `regex-group#1` returns a zero-length string)
* Added accumulators (`xsl:accumulator`, `accumulator-before()`, `accumulator-after()`) with `use-accumulators` on `xsl:mode`, `xsl:source-document` and `xsl:merge-source`; values survive `copy-of()`, `snapshot()` and `copy-accumulators="yes"`
* Added `xsl:try`/`xsl:catch` (err:* variables, `errors` name tests, `rollback-output`), `xsl:assert`, `xsl:evaluate` (the processor reports `supports-dynamic-evaluation`), `xsl:map`/`xsl:map-entry` and `xsl:source-document` (not streamed)
* Added XSLT 3.0 packages: `xsl:use-package` with package-version ranges (library packages via `XsltCompileOptions.addPackage`), `xsl:accept`, `xsl:override` with `xsl:original`, `xsl:expose`, visibility and abstract components, and package-local keys, decimal formats, accumulators, namespace aliases, outputs and character maps
* Added `fn:transform`, backed by the XSLT engine: phoxtrot-xpath finds it through the `ITransformHandler` service-loader hook, so it is available exactly when phoxtrot-xslt is on the class path; nested transformations share the caller's budget, resolver and packages
* Added `XsltTransformRequest.setRawDelivery` and `setSharedBudget`; function items of stylesheet functions stay callable after their transformation ended
* Added `fn:stream-available`; `xsl:context-item` in template rules; typed modes raise XTTE3100/XTTE3110; a named initial mode must be declared (XTDE0045)
* Whitespace in DTD element-only content is no longer kept in the tree (XDM 3.1 section 6.7.3); serializing a C0 control character as XML 1.0 raises SERE0006
* Whitespace stripping applies to all trees of the global context item and the initial match selection; shallow copies keep the base URI
* The XSLT 3.0 conformance harness fails on any failing case not documented in `phoxtrot-xslt-conformance/docs/accepted-failures/xslt30.md`
* Added about 5,500 specification-based unit test cases for XPath 3.1, F&O 3.1 and XSLT 3.0, ported from go-xslt
* Fixed format-date `[F]`, width and padding handling, the type of dateTimeStamp arithmetic results, regex multi-digit back-references, UCA `caseFirst` below tertiary strength, adaptive serialization type names and the case of JSON control escapes
* Corrected XSLT error codes for forwards-compatible unknown instructions, static attribute values, `key()`, the principal result, parameter documents, `xsl:message` error codes, typed modes and loader errors
* Package visibility follows XSLT 3.0: `xsl:expose` decides entry points, the `xsl:accept` visibility table applies, and private or hidden components are hidden from the packages that use them
* Replaced all `synchronized` blocks with read-write locks; fixed a lock-order deadlock between the `fn:transform` handler lookup and the default function library
* `XdmParser.setLoadExternalEntities(true)` now really reads the external DTD subset and external entities; secure processing had blocked every external access
* Added optional schema-awareness based on Apache Xerces-J 2.12.2 (optional dependency, without xml-apis): `XsltCompileOptions.setSchemaAware`, `xsl:import-schema`, validation of constructed and source nodes, `default-validation`, typed modes, typed values, `schema-element()`/`schema-attribute()` and user-defined simple types in XPath
* Added the schema API `com.helger.phoxtrot.xdm.schema` (`XdmSchemaSupport`, `IXdmSchemaSet`, ...) and `StaticContext.setSchemaSet`
* `fn:json-to-xml` supports `validate=true` when the schema for its namespace is in scope
* `xsl:strip-space` now also applies to typed elements with element-only or mixed content
* The schema SPI (`com.helger.phoxtrot.xdm.schema`) is independent of JAXP: `XdmSchemaType` holds `IXdmTypeDefinition`s, and JAXP is only used inside the providers
* Added `com.helger.phoxtrot.xdm.jaxp.JdkSchemaProvider`: schema-validated input on the JDK's own validator without Apache Xerces-J (no schema component model)
* Added the module `phoxtrot-xsd-conformance` that runs the W3C XML Schema test suite (xsdtests) against a pluggable validator; the first one is Apache Xerces-J 2.12.2 (XSD 1.0: 39,145 of 39,401 applicable cases pass; XSD 1.1 is reported as unsupported). Every failing case is documented with its reason in `phoxtrot-xsd-conformance/docs/accepted-failures/xsd.md`, and an undocumented failure fails the build
* Added the module `phoxtrot-xsd`, a native XML Schema validator (port of go-xslt's `internal/xsd`, no Xerces-J or JAXP validation): schema loader with include/import/redefine and conditional inclusion (`vc:*`), schema-for-schemas checks, the component model and its read-only view (`XsdSchema.getView`), local-file-only access with optional confinement (`XsdFileAccess`); constructs not implemented yet are reported as unsupported
* `phoxtrot-xsd-conformance`: `-Dxsd.validator=native` runs the native validator; the summary splits schema and instance tests
* `phoxtrot-xsd` simple types and facets: all built-in types, atomic/list/union derivation, every constraining facet, XSD regular expressions and the lexical checks of each primitive type
* `phoxtrot-xsd` content models: particles, `xs:all`, wildcards, Unique Particle Attribution, substitution groups and the derivation-by-restriction and -extension checks
* `phoxtrot-xsd` instance validation: element and attribute validation with defaults, `xsi:type`, `xsi:nil`, `xsi:schemaLocation` hints, and the identity constraints (`xs:unique`, `xs:key`, `xs:keyref`) and ID/IDREF checks
* `phoxtrot-xsd-conformance` with `-Dxsd.validator=native`: the native XSD 1.0 validator passes 39,396 of 39,401 applicable cases; the 5 failures are the ones documented by go-xslt
* Added the native schema processor as `IXdmSchemaProvider` (`XsdSchemaProvider` of `phoxtrot-xsd`): `XdmSchemaSupport` uses it when `phoxtrot-xsd` is on the class path, Apache Xerces-J otherwise or with `-Dxdm.schemaProvider=xerces`; trees are validated in place; the QT3 and the schema-aware XSLT30 harnesses use it by default
* `xsl:merge-source` honours `validation` and `type`: the documents of `for-each-source` are validated like those of `xsl:source-document`; the schema-aware XSLT30 run with the native validator now fails 17 cases
* `phoxtrot-xsd` keeps compiled simple types acyclic: an XSD 1.0 union cycle collapses to the members that do not lead back, any other circular derivation is rejected (go-xslt issue #12)
* The native schema validator reports all errors: `XsdSchema.validate`/`validateText` list every error in document order (`XsdValidationResult.errors`), with line, column, code, element path, and for content-model errors the `kind` (`missing`, `too-many`, `unexpected`, `order`) and `particle`; `XsdValidationOptions` sets the language of the messages and a maximum number of errors; the error that the first-error validation of XSLT and XPath reports is among them
* The native schema validator reads `msg:message` entries in `xs:appinfo` (element, attribute and group declarations) as the wording and `rule` of a cardinality error or a missing required attribute, selected by `xml:lang`; the namespace is `urn:fdc:helger.com:phoxtrot:xsd:messages` (`XsdConstants.NS_MESSAGES`), not the one of go-xslt
* Checked against go-xslt v0.3.9: the package-version upper bound `to 3.3.*`, the type error for a boolean arithmetic operand, the syntax error for `attribute(N, T?)` and the function conversion rules for the arguments of every built-in were already in place; new tests pin them (`ArgumentConversionTest`, `BuiltinSignatureCatalogueTest`, `BuiltinOddArgumentsTest`, `FunctionsArgsSpecTest`, `XsltFunctionCatalogueTest`)
* The declared signature of `fn:root#0` is `node()` (was `node()?`), as in F&O 3.1; an argument conversion error names the function and the argument position
* Added a host API for standalone XPath expressions and patterns that use the declarations of a stylesheet (e.g. for a Schematron executor): `XsltExecutable.createStaticContext` (namespaces, stylesheet functions, XSLT functions such as `key()` and `current()`, global variables and parameters, decimal formats of the principal stylesheet element) and `XsltExecutable.createEvaluation`, which returns an `XsltEvaluation` per source document, set up like a transformation (global context item with whitespace stripping, global parameters, keys)
* `CompiledXPath.evaluate` always uses the static context the expression was compiled with, also when the dynamic context was created from another one

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.
