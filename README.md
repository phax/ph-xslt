# ph-xslt

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> and no release has been published yet. Do not use it in production.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing and XSD validation are left to the JDK (Xerces); everything on top of it is implemented here.

Status: work in progress. The XPath 3.1 engine runs and passes 99.3% of the applicable W3C QT3 test cases
(22,328 of 22,478); XSLT has not been started.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `xpath-xdm` | XPath data model: node tree built from SAX, names, document order | usable |
| `xpath-core` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `xpath-conformance` | W3C QT3 test harness (not deployed) | usable |

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
* Added `StaticContext.setStaticTyping` to opt in to the narrow part of the optional Static Typing Feature: axis steps that provably select nothing raise XPST0005
* Added XSLT 3.0 patterns (`XsltPatternParser`, `CompiledXsltPattern`): predicate and selection patterns, rooted patterns, union/intersect/except, default priorities and union alternatives
* Added the XML and XSD version (`StaticContext.setXmlVersion`/`setXsdVersion`) and the default language (`DynamicContext.setDefaultLanguage`) settings
* Added schema-validated input with the JDK validator: `XdmParser.setSchema` annotates the tree (`XdmSchemaType`, nilled, schema IDs), which gives typed values, `fn:nilled`, schema-based `fn:id`/`fn:element-with-id`/`fn:idref` and `element(N, T)`/`attribute(N, T)` tests; invalid documents raise `XdmValidationException`
* `XdmParser` always uses the JDK parser, also when another JAXP implementation such as Apache Xerces is on the class path

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.
