# ph-xslt

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> and no release has been published yet. Do not use it in production.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing is left to the JDK, XSD schema-awareness to the optional Apache Xerces-J; everything on top of it is
implemented here.

Status: work in progress. The XPath 3.1 engine runs and passes 99.7% of the applicable W3C QT3 test cases
(22,419 of 22,478). The XSLT 3.0 engine passes 99.9% of the applicable W3C xslt30-test cases as a basic
processor (8,637 of 8,647). Schema-awareness is available as an opt-in; streaming and backwards compatibility are
not done yet.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `xpath-xdm` | XPath data model: node tree built from SAX, names, document order | usable |
| `xpath-core` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `xpath-conformance` | W3C QT3 test harness (not deployed) | usable |
| `xslt-core` | XSLT 3.0: stylesheet compiler, modules, templates and modes, the basic instructions, XSLT functions | in progress |
| `xslt-conformance` | W3C xslt30-test harness (not deployed) | usable |
| `xsd-core` | Native XML Schema validator (port of go-xslt's `internal/xsd`): loader, include/import/redefine, schema-for-schemas checks, component model and read-only view; datatypes, content models and instance validation still to come | in progress |
| `xsd-conformance` | W3C xsdtests (XML Schema) harness with a pluggable validator (not deployed) | usable |

Requires Java 21. CI runs on Java 21 and 25.

## Schema-awareness (optional dependency)

Schema-awareness (`XsltCompileOptions.setSchemaAware (true)`: `xsl:import-schema`, `[xsl:]validation`/`[xsl:]type`,
typed sources, `schema-element()` and user-defined types in XPath) needs Apache Xerces-J (`xerces:xercesImpl` 2.12.2) on
the class path. It is an optional dependency of `xpath-xdm`: add it yourself, excluding `xml-apis` (the JDK provides
those APIs); no resolver or catalogue library is needed. Without it the engine is a basic XSLT 3.0 processor and no
Xerces-J class is loaded.

Xerces-J is used only for schema compilation, the schema component model and validation (XSD 1.0). XML parsing stays
on the JDK parser, and the Xerces-J classes are instantiated directly, never through the JAXP lookup. Note that the
presence of the jar alone changes what `SAXParserFactory.newInstance ()` and `SchemaFactory.newInstance (...)` return
for other code in the same application (its `META-INF/services` entries).

[docs/SCHEMA.md](docs/SCHEMA.md) explains in detail what the JDK validator cannot provide and which features and tests
therefore need Xerces-J, and why Xerces-J is only the fallback next to the planned native validator.

```xml
<dependency>
  <groupId>xerces</groupId>
  <artifactId>xercesImpl</artifactId>
  <version>2.12.2</version>
  <exclusions>
    <exclusion>
      <groupId>xml-apis</groupId>
      <artifactId>xml-apis</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

## Origin and acknowledgements

This library is a port of [go-xslt](https://github.com/tim-riep/go-xslt) by Tim Riep (Apache 2.0), a from-scratch Go
implementation of XSLT 3.0, XPath 3.1 and XSD. Its architecture, its test strategy and large parts of its behaviour are the
basis of this project. See [NOTICE](NOTICE).

# News and noteworthy

v0.1.0 - work in progress
* Initial project setup
* Added the module `xpath-xdm` with `XdmNode`, `XdmQName`, `XdmParser` and `XdmTreeBuilder`
* Added the module `xpath-core` with the XPath 3.1 engine and the F&O 3.1 function library
* Added the module `xpath-conformance` that runs the W3C QT3 test suite; every failing case is documented with its
  reason in `xpath-conformance/docs/accepted-failures/qt3.md`, and an undocumented failure fails the build
* Added the module `xslt-conformance` that runs the W3C xslt30-test suite as a basic XSLT 3.0 processor (no streaming,
  schema-awareness or backwards compatibility), with the same applicable cases as go-xslt's default run
* Added `StaticContext.setStaticTyping` to opt in to the narrow part of the optional Static Typing Feature: axis steps that provably select nothing raise XPST0005
* Added XSLT 3.0 patterns (`XsltPatternParser`, `CompiledXsltPattern`): predicate and selection patterns, rooted patterns, union/intersect/except, default priorities and union alternatives
* Added the XML and XSD version (`StaticContext.setXmlVersion`/`setXsdVersion`) and the default language (`DynamicContext.setDefaultLanguage`) settings
* Added schema-validated input: `XdmParser.setSchemaSet` validates the parsed tree in place (`IXdmSchemaSet.validateDocument`) and annotates it (`XdmSchemaType`, nilled, schema IDs), which gives typed values, `fn:nilled`, schema-based `fn:id`/`fn:element-with-id`/`fn:idref` and `element(N, T)`/`attribute(N, T)` tests; invalid documents raise `XdmValidationException`
* `XdmParser` always uses the JDK parser, also when another JAXP implementation such as Apache Xerces is on the class path
* Added the module `xslt-core` with the XSLT 3.0 compiler and engine (`XsltCompiler`, `XsltExecutable`): include/import, static processing (use-when, static parameters, shadow attributes), template rules and modes, all basic instructions, stylesheet functions, keys, attribute sets, the XSLT functions, step and depth budgets, and an extension API for instruction families (`xslt-core/INSTRAPI.md`)
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
* Added `fn:transform`, backed by the XSLT engine: xpath-core finds it through the `ITransformHandler` service-loader hook, so it is available exactly when xslt-core is on the class path; nested transformations share the caller's budget, resolver and packages
* Added `XsltTransformRequest.setRawDelivery` and `setSharedBudget`; function items of stylesheet functions stay callable after their transformation ended
* Added `fn:stream-available`; `xsl:context-item` in template rules; typed modes raise XTTE3100/XTTE3110; a named initial mode must be declared (XTDE0045)
* Whitespace in DTD element-only content is no longer kept in the tree (XDM 3.1 section 6.7.3); serializing a C0 control character as XML 1.0 raises SERE0006
* Whitespace stripping applies to all trees of the global context item and the initial match selection; shallow copies keep the base URI
* The XSLT 3.0 conformance harness fails on any failing case not documented in `xslt-conformance/docs/accepted-failures/xslt30.md`
* Added about 5,500 specification-based unit test cases for XPath 3.1, F&O 3.1 and XSLT 3.0, ported from go-xslt
* Fixed format-date `[F]`, width and padding handling, the type of dateTimeStamp arithmetic results, regex multi-digit back-references, UCA `caseFirst` below tertiary strength, adaptive serialization type names and the case of JSON control escapes
* Corrected XSLT error codes for forwards-compatible unknown instructions, static attribute values, `key()`, the principal result, parameter documents, `xsl:message` error codes, typed modes and loader errors
* Package visibility follows XSLT 3.0: `xsl:expose` decides entry points, the `xsl:accept` visibility table applies, and private or hidden components are hidden from the packages that use them
* Replaced all `synchronized` blocks with read-write locks; fixed a lock-order deadlock between the `fn:transform` handler lookup and the default function library
* `XdmParser.setLoadExternalEntities(true)` now really reads the external DTD subset and external entities; secure processing had blocked every external access
* Added optional schema-awareness based on Apache Xerces-J 2.12.2 (optional dependency, without xml-apis): `XsltCompileOptions.setSchemaAware`, `xsl:import-schema`, validation of constructed and source nodes, `default-validation`, typed modes, typed values, `schema-element()`/`schema-attribute()` and user-defined simple types in XPath
* Added the schema API `com.helger.xpath.xdm.schema` (`XdmSchemaSupport`, `IXdmSchemaSet`, ...) and `StaticContext.setSchemaSet`
* `fn:json-to-xml` supports `validate=true` when the schema for its namespace is in scope
* `xsl:strip-space` now also applies to typed elements with element-only or mixed content
* The schema SPI (`com.helger.xpath.xdm.schema`) is independent of JAXP: `XdmSchemaType` holds `IXdmTypeDefinition`s, and JAXP is only used inside the providers
* Added `com.helger.xpath.xdm.jaxp.JdkSchemaProvider`: schema-validated input on the JDK's own validator without Apache Xerces-J (no schema component model)
* Added the module `xsd-conformance` that runs the W3C XML Schema test suite (xsdtests) against a pluggable validator; the first one is Apache Xerces-J 2.12.2 (XSD 1.0: 39,145 of 39,401 applicable cases pass; XSD 1.1 is reported as unsupported). Every failing case is documented with its reason in `xsd-conformance/docs/accepted-failures/xsd.md`, and an undocumented failure fails the build
* Added the module `xsd-core`, a native XML Schema validator (port of go-xslt's `internal/xsd`, no Xerces-J or JAXP validation): schema loader with include/import/redefine and conditional inclusion (`vc:*`), schema-for-schemas checks, the component model and its read-only view (`XsdSchema.getView`), local-file-only access with optional confinement (`XsdFileAccess`); constructs not implemented yet are reported as unsupported
* `xsd-conformance`: `-Dxsd.validator=native` runs the native validator; the summary splits schema and instance tests
* `xsd-core` simple types and facets: all built-in types, atomic/list/union derivation, every constraining facet, XSD regular expressions and the lexical checks of each primitive type
* `xsd-core` content models: particles, `xs:all`, wildcards, Unique Particle Attribution, substitution groups and the derivation-by-restriction and -extension checks
* `xsd-core` instance validation: element and attribute validation with defaults, `xsi:type`, `xsi:nil`, `xsi:schemaLocation` hints, and the identity constraints (`xs:unique`, `xs:key`, `xs:keyref`) and ID/IDREF checks
* `xsd-conformance` with `-Dxsd.validator=native`: the native XSD 1.0 validator passes 39,396 of 39,401 applicable cases; the 5 failures are the ones documented by go-xslt
* Added the native schema processor as `IXdmSchemaProvider` (`XsdSchemaProvider` of `xsd-core`): `XdmSchemaSupport` uses it when `xsd-core` is on the class path, Apache Xerces-J otherwise or with `-Dxdm.schemaProvider=xerces`; trees are validated in place; the QT3 and the schema-aware XSLT30 harnesses use it by default
* `xsl:merge-source` honours `validation` and `type`: the documents of `for-each-source` are validated like those of `xsl:source-document`; the schema-aware XSLT30 run with the native validator now fails 17 cases
* The native schema validator reports all errors: `XsdSchema.validate`/`validateText` list every error in document order (`XsdValidationResult.errors`), with line, column, code, element path, and for content-model errors the `kind` (`missing`, `too-many`, `unexpected`, `order`) and `particle`; `XsdValidationOptions` sets the language of the messages and a maximum number of errors; the error that the first-error validation of XSLT and XPath reports is among them
* The native schema validator reads `msg:message` entries in `xs:appinfo` (element, attribute and group declarations) as the wording and `rule` of a cardinality error or a missing required attribute, selected by `xml:lang`; the namespace is `urn:fdc:helger.com:ph-xslt:xsd:messages` (`XsdConstants.NS_MESSAGES`), not the one of go-xslt

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.
