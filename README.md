# ph-xslt

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> and no release has been published yet. Do not use it in production.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing and XSD validation are left to the JDK (Xerces); everything on top of it is implemented here.

Status: work in progress. The XPath 3.1 engine runs and passes 99.3% of the applicable W3C QT3 test cases
(22,328 of 22,478). The XSLT 3.0 core passes 86.8% of the applicable W3C xslt30-test cases (7,504 of
8,647); instruction families such as xsl:number, grouping and accumulators are being added.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `xpath-xdm` | XPath data model: node tree built from SAX, names, document order | usable |
| `xpath-core` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `xpath-conformance` | W3C QT3 test harness (not deployed) | usable |
| `xslt-core` | XSLT 3.0: stylesheet compiler, modules, templates and modes, the basic instructions, XSLT functions | in progress |
| `xslt-conformance` | W3C xslt30-test harness (not deployed) | usable |

Requires Java 21. CI runs on Java 21 and 25.

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
* Added schema-validated input with the JDK validator: `XdmParser.setSchema` annotates the tree (`XdmSchemaType`, nilled, schema IDs), which gives typed values, `fn:nilled`, schema-based `fn:id`/`fn:element-with-id`/`fn:idref` and `element(N, T)`/`attribute(N, T)` tests; invalid documents raise `XdmValidationException`
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
* `XdmParser.setLoadExternalEntities(true)` now really reads the external DTD subset and external entities; secure processing had blocked every external access

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.
