# phoxtrot

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.phoxtrot/phoxtrot-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.phoxtrot/phoxtrot-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.phoxtrot/phoxtrot-xdm/javadoc.svg)](https://javadoc.io/doc/com.helger.phoxtrot/phoxtrot-xdm)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

> **Experimental.** This project is at an early stage. APIs, module layout and behaviour may change without notice,
> also between releases. Do not use it in production yet.

A pure Java implementation of XPath 3.1 and XSLT 3.0, intended as an Apache 2.0 licensed alternative to Saxon.
XML parsing is left to the JDK; everything on top of it, including an XML Schema 1.0 validator, is implemented here.

Status: initially released.
* The XPath 3.1 engine runs and passes 99.7% of the applicable W3C QT3 test cases (22,419 of 22,478).
* The XSLT 3.0 engine passes 99.9% of the applicable W3C xslt30-test cases as a basic processor (8,640 of 8,647).
* The native XML Schema validator passes 99.99% of the applicable W3C xsdtests cases for XSD 1.0 (39,396 of 39,401) and XSD 1.1 (41,596 of 41,600).

Schema-awareness and streaming are available as opt-ins (streaming: the streamability analysis, streamed execution of guaranteed-streamable constructs and output written while it is built).
XSLT 1.0 stylesheets run with backwards compatible behaviour (claimed by default; 8,752 of 8,762 cases with the claim), with the EXSLT - Common functions `exsl:node-set` and `exsl:object-type`.
A JAXP `TransformerFactory` and a W3C DOM bridge are included.
See [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## Modules

| Module | Content | Status |
|--------|---------|--------|
| `phoxtrot-xdm` | XPath data model: node tree built from SAX, incremental StAX reader for streamed documents, names, document order, W3C DOM and SAX bridge | usable |
| `phoxtrot-xpath` | XPath 3.1: values, parser, evaluator, F&O 3.1 function library, regex, collations, serialisation | usable |
| `phoxtrot-xpath-conformance` | W3C QT3 test harness (not deployed) | usable |
| `phoxtrot-xslt` | XSLT 3.0: stylesheet compiler, modules, templates and modes, all instruction families, XSLT functions, packages, schema-awareness, streaming (analysis, streamed execution, output written while it is built), JAXP `TransformerFactory` | usable |
| `phoxtrot-xslt-conformance` | W3C xslt30-test harness (not deployed) | usable |
| `phoxtrot-xsd` | Native XML Schema 1.0 and 1.1 validator (port of go-xslt's `internal/xsd`): loader, include/import/redefine/override, schema-for-schemas checks, simple types and facets, content models with open content, instance validation with identity constraints, assertions and conditional type assignment, read-only component view, validation API that reports all errors; the default schema provider for XPath and XSLT (schemas compiled as XSD 1.1) | usable |
| `phoxtrot-xsd-conformance` | W3C xsdtests (XML Schema) harness with a pluggable validator (not deployed) | usable |
| `phoxtrot-xslt-benchmark` | JMH benchmarks and profiling workloads for parsing, XPath, XSLT, XSD, streaming and compiling (standalone, not in the reactor, not deployed; see its `README.md` and `docs/benchmarks`) | usable |
| `phoxtrot-xdm-memory-benchmark` | Retained heap of the node tree compared with ph-xml MicroDOM and Saxon TinyTree (standalone, not deployed) | usable |

Requires Java 21. CI runs on Java 21 and 25.

## Maven usage

The deployed modules are `phoxtrot-xdm`, `phoxtrot-xpath`, `phoxtrot-xslt` and `phoxtrot-xsd`. For XSLT 3.0 add
`phoxtrot-xslt` (it brings `phoxtrot-xpath` and `phoxtrot-xdm`); for XPath 3.1 alone `phoxtrot-xpath`:

```xml
<dependency>
  <groupId>com.helger.phoxtrot</groupId>
  <artifactId>phoxtrot-xslt</artifactId>
  <version>0.1.0</version>
</dependency>
```

Add `phoxtrot-xsd` as well for schema-awareness (see below).

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

In both cases XML parsing stays on the JDK parser. A schema processor reads the documents an imported schema references
(`xs:include`, `xs:import`, `xs:redefine`, `xs:override`) only from local files, never from the network, and only while
`XsltCompileOptions.setReadLocalSchemaFiles` allows it (default `true`; the JAXP adapter derives it from
`ACCESS_EXTERNAL_STYLESHEET`). A `schema-location` itself is read through the resolver of the compile options if one
is set. `phoxtrot-xsd` can also be used on its own to validate documents (`XsdSchema.validate`, which reports all
errors), against XSD 1.0 or 1.1; the `xsi:schemaLocation` hints of an instance are only followed with
`XsdValidationOptions.setFollowSchemaLocationHints (true)`, because the instance then decides which local files are
read.

```xml
<dependency>
  <groupId>com.helger.phoxtrot</groupId>
  <artifactId>phoxtrot-xsd</artifactId>
  <version>0.1.0</version>
</dependency>
```

[docs/SCHEMA.md](docs/SCHEMA.md) explains how the provider is selected, what the JDK validator cannot provide, and why
Xerces-J is only the fallback next to the native validator.

## JAXP (`javax.xml.transform`)

`com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory` is a JAXP `TransformerFactory` for XSLT 3.0 (`Templates`
are thread-safe, a `Transformer` is for one thread at a time). It is deliberately not registered in
`META-INF/services`, which would replace the JDK transformer for every application on the class path; create it
directly or by name:

```java
TransformerFactory aTF = new PhoxtrotTransformerFactory ();
// or: TransformerFactory.newInstance ("com.helger.phoxtrot.xslt.jaxp.PhoxtrotTransformerFactory", aClassLoader)
aTF.setURIResolver (aURIResolver);      // xsl:include/import, document(), doc(), unparsed-text()
Templates aTemplates = aTF.newTemplates (new StreamSource (aXslFile));
Transformer aTransformer = aTemplates.newTransformer ();
aTransformer.setErrorListener (aErrorListener);  // xsl:message arrives as warning()
aTransformer.setParameter ("phase", "#ALL");
aTransformer.transform (new DOMSource (aDoc), new DOMResult ());
```

* Sources: `StreamSource`, `DOMSource`, `SAXSource` (with or without `XMLReader`) and `XdmSource` (a phoxtrot tree);
  results: `StreamResult` (writer, stream or `file:` system ID; written while it is built where the output
  definition allows it), `DOMResult` and `SAXResult`. `newTransformer ()` is the identity transformer.
* Parameters: `String` becomes `xs:untypedAtomic` (so `as="xs:integer"` accepts "42"), numbers, `Boolean`, `URI`,
  `QName`, DOM nodes, phoxtrot items and sequences, arrays and collections (as sequences). Names `{uri}local`.
* Output properties: the `OutputKeys` and the other XSLT 3.0 serialization attributes except `use-character-maps`
  override the unnamed `xsl:output` for a `StreamResult`; namespaced names are accepted without effect.
* Errors: compile errors are reported to `ErrorListener.fatalError` and thrown as `TransformerConfigurationException`,
  run errors as `TransformerException`; the message starts with the error code, the locator has module, line and
  column, the cause is the `XsltException`. `xsl:message` and failed `xsl:assert` go to `ErrorListener.warning` while
  the transformation runs, or to an `IXsltMessageListener` (`PhoxtrotTransformer.setMessageListener`, factory
  attribute `PhoxtrotTransformerFactory.ATTR_MESSAGE_LISTENER`).
* Tracing: an `IXsltTraceListener` (`PhoxtrotTransformer.setTraceListener`, factory attribute
  `PhoxtrotTransformerFactory.ATTR_TRACE_LISTENER`) follows the template rules, named templates, stylesheet functions
  and optionally instructions of each transformation; the same listener as `XsltTransformRequest.setTraceListener`.
* Threads: compilations and transformations run on the thread the factory attribute
  `PhoxtrotTransformerFactory.ATTR_THREADING` chooses (an `EXsltThreading` or its name; default `NEW_THREAD`, a new
  thread with a large stack per call; also `THREAD_POOL` and `CALLING_THREAD`), a transformer can override it with
  `PhoxtrotTransformer.setThreading`.
* What the `URIResolver` does not resolve is read directly if `XMLConstants.ACCESS_EXTERNAL_STYLESHEET` allows the
  scheme (default `all`; `FEATURE_SECURE_PROCESSING` set to `true` allows nothing; a `jar:` URI needs its nested URI
  allowed too). A `URIResolver` that throws refuses the URI: `xsl:include`/`xsl:import` fail with XTSE0165,
  `document()`/`doc()` with FODC0002, `unparsed-text()`/`json-doc()` with FOUT1170, each with its message, and the
  `*-available` functions return `false`.
* External DTDs and entities are only read with the attribute `ATTR_LOAD_EXTERNAL_ENTITIES`, for stylesheets, modules,
  sources and documents alike: an `EntityResolver` (factory attribute `ATTR_ENTITY_RESOLVER`,
  `PhoxtrotTransformer.setEntityResolver`) is asked first, and the parser opens them itself only for the schemes of
  `XMLConstants.ACCESS_EXTERNAL_DTD` (default `all`, empty with `FEATURE_SECURE_PROCESSING`). Further attributes:
  `ATTR_SCHEMA_AWARE` (the `schema-location` of `xsl:import-schema` goes through the `URIResolver` and
  `ACCESS_EXTERNAL_STYLESHEET` like a module; the documents it references are read only if `file` is allowed),
  `ATTR_MAX_STEPS`.
* Not supported: `SAXTransformerFactory` (`TransformerHandler`, `TemplatesHandler`), StAX sources and results, and
  writing secondary results (`xsl:result-document`); these are available from `PhoxtrotTransformer.getLastResult ()`.
  Initial templates, modes and functions need the phoxtrot API (`PhoxtrotTemplates.getExecutable ()`).

## W3C DOM bridge

`com.helger.phoxtrot.xdm.bridge` converts between W3C DOM and the phoxtrot tree without serialising:
`DomToXdm.convertDocument (aNode, sSystemId)` builds the tree of a DOM (namespace fixup for DOMs built with
`createElementNS`, DOM level 1 names, entity references expanded, ID attributes, unparsed entities, the document URI)
and `DomToXdm.convert` returns the counterpart of a given DOM node in it; `XdmToDom.toDocument`,
`toDocumentFragment` and `appendTo` write a tree, a subtree or a parentless node into a DOM with correct namespace
declarations; `XdmToSax.emit` writes a tree as SAX events.

## Origin and acknowledgements

This library is a port of [go-xslt](https://github.com/tim-riep/go-xslt) by Tim Riep (Apache 2.0), a from-scratch Go
implementation of XSLT 3.0, XPath 3.1 and XSD. Its architecture, its test strategy and large parts of its behaviour are the
basis of this project. See [NOTICE](NOTICE).

# News and noteworthy

v0.1.0 - 2026-11-10
* Initial release
* XSD 1.0 and XSD 1.1 support
* XPath 3.1 support
* XSLT 3.0 support incl. streaming

# Contributing

Copyright (C) 2026 Philip Helger. Licensed under the Apache License, Version 2.0.

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.
