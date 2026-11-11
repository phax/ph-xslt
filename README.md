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

## XPath API

Plain XPath 3.1 without XSLT (e.g. a Schematron binding that evaluates rule by rule) uses `phoxtrot-xpath` directly:

```java
StaticContext aSC = new StaticContext ();
aSC.declareNamespace ("cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
aSC.declareVariable (new XdmQName ("limit"));
SeqType aDouble = SeqType.of (ItemType.atomic (EAtomicType.DOUBLE), EOccurrence.ONE);
aSC.setFunctionLibrary (FunctionLibrary.getDefault ().createChild ()
                                       .register (new XdmQName ("urn:ext", "twice"), List.of (aDouble), aDouble,
                                                  (aCtx, aArgs) -> ...));
CompiledXPath aXPath = XPathCompiler.compile ("count(//cbc:ID) le $limit", aSC);  // immutable, thread-safe

DynamicContext aCtx = aXPath.createDynamicContext ().withFocus (aDocNode, 1, 1)
                            .withVariable (new XdmQName ("limit"), XdmSequence.of (XdmAtomic.integer (10)));
aCtx.setMaxSteps (1_000_000);                      // optional, XPDY0130 when exceeded
Boolean aValid = LargeStackRunner.run (() -> Boolean.valueOf (aXPath.evaluateEffectiveBooleanValue (aCtx)),
                                       "my-xpath", EThreading.THREAD_POOL);
```

* Source compatible within 0.x (changes only with a note in "News and noteworthy"): in `com.helger.phoxtrot.xpath`
  `XPathCompiler`, `CompiledXPath`, `XPathException`, `EThreading`, `LargeStackRunner`,
  `XPathStreamabilityAnalyzer`, `XPathStreamabilityResult`, `XPathStreamabilityReason`, `EStreamabilityVerdict` and
  `EStreamabilityConstruct`; `StaticContext`, `DynamicContext`, `IResourceResolver` and `ResourceUri` (`context`);
  `FunctionLibrary` and `IXPathFunction` (`fn`); `SeqType`, `ItemType` and `EOccurrence` (`ast`); `EPosture`, `ESweep`,
  `PostureSweep` and `EStreamFunctionCategory` (`stream`); the value types of `value`, `XPathOperators` (`eval`) and the node model
  of `phoxtrot-xdm`. Everything else in `ast` (expression nodes), `eval`, `parser`, `ops`, `pattern` and `stream` is
  internal.
* Extension functions: `FunctionLibrary.register` with the signature as text (`"(xs:string?) as xs:integer"`) or as
  `SeqType`s; the arguments are converted by the XPath 3.1 function conversion rules before the call (see
  `IXPathFunction`). A compiled stylesheet's library is frozen; register into a `createChild ()`.
* `CompiledXPath.getFreeVariables ()`: the variables of the static context an expression uses, to bind only these.
* Limits: `DynamicContext.setMaxSteps` (loop iterations and function calls, unlimited by default), the function call
  depth (10,000) and `LargeStackRunner` for a thread with a large stack.
* Errors: `XPathException` with code, description and error object; a static error has its position in the
  expression (`getExpressionOffset`, `getExpressionLine`, `getExpressionColumn`). An error raised in an
  `xsl:function` is an `XsltException` naming the function (`getFunctionFrame`).
* Streamability: `XPathStreamabilityAnalyzer` tells whether an expression (`analyze`) or a match pattern
  (`analyzePattern`) can be evaluated while streaming, with the analysis of XSLT 3.0 section 19: posture and sweep, a
  verdict (motionless, consuming, free-ranging) and for a verdict other than motionless the construct that causes it
  (an axis step, a function, a variable, without position in the text). Options: the context node streamed or
  buffered, variables bound to streamed nodes, the streamability category of extension functions (undeclared ones
  are not streamable).
* Stylesheet functions without Java code: compile a module with the `xsl:function`s (and the keys and global variables
  they use) and compile the expressions against `XsltExecutable.createStaticContext ()`, evaluated with the dynamic
  context of `XsltExecutable.createEvaluation (request).createDynamicContext ()`.

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
  `PhoxtrotTransformerFactory.ATTR_THREADING` chooses (an `EThreading` or its name; default `NEW_THREAD`, a new
  thread with a large stack per call; also `THREAD_POOL` and `CALLING_THREAD`), a transformer can override it with
  `PhoxtrotTransformer.setThreading`.
* The `URIResolver` is called as JAXP specifies: with the `href` as written and the base URI it is resolved against
  (the empty string if there is none, e.g. a stylesheet without system ID).
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
  `ATTR_MAX_STEPS` and `ATTR_MAX_DEPTH` (the nesting depth of templates and functions, default 10,000,
  `XsltTransformRequest.UNLIMITED_DEPTH` for no limit but the stack; `PhoxtrotTransformer.setMaxDepth` per
  transformer).
* Not supported: `SAXTransformerFactory` (`TransformerHandler`, `TemplatesHandler`), StAX sources and results, and
  writing secondary results (`xsl:result-document`); these are available from `PhoxtrotTransformer.getLastResult ()`.
  Initial templates, modes and functions need the phoxtrot API (`PhoxtrotTemplates.getExecutable ()`).

## W3C DOM bridge

`com.helger.phoxtrot.xdm.bridge` converts between W3C DOM and the phoxtrot tree without serialising:
`DomToXdm.convertDocument (aNode, sSystemId)` builds the tree of a DOM (namespace fixup for DOMs built with
`createElementNS`, DOM level 1 names, entity references expanded, ID attributes, unparsed entities, the document URI)
and `DomToXdm.convert` returns the counterpart of a given DOM node in it; `DomToXdm.convertWithMapping` also keeps
the way back from every XDM node to its DOM node (`DomNodeMapping.getDomNode`); `XdmToDom.toDocument`,
`toDocumentFragment` and `appendTo` write a tree, a subtree or a parentless node into a DOM with correct namespace
declarations; `XdmToSax.emit` writes a tree as SAX events. `XdmDomView` shows a tree as a read-only DOM without
copying it (`new XdmDomView (aDoc).getNode (aXdmNode)`, one DOM node per XDM node, `XdmDomView.getXdmNode` back), e.g.
to report a node an XPath expression matched to code that expects `org.w3c.dom` nodes.

## Implementation-dependent values

* `fn:generate-id` returns `d`, the number of the node's tree in the run, `n` and the number of the node in that tree,
  both counted in the order the identifiers are first requested (`d1n1`, `d1n2`, `d2n1`, ...). The values differ from
  Saxon's (which also differ between Saxon's tree models), so output that contains them, such as the
  `active-pattern/@id` of an SVRL created by the ISO Schematron skeleton from abstract patterns, differs between
  processors; compare such output with the generated IDs normalised.

## Origin and acknowledgements

This library is a port of [go-xslt](https://github.com/tim-riep/go-xslt) by Tim Riep (Apache 2.0), a from-scratch Go
implementation of XSLT 3.0, XPath 3.1 and XSD. Its architecture, its test strategy and large parts of its behaviour are the
basis of this project. See [NOTICE](NOTICE).

# News and noteworthy

v0.2.0 - work in progress
* API change: every `IResourceResolver` method now takes a `ResourceUri` (the resolved URI, the reference as written
  and its base URI) instead of the absolute URI as string; implementations use `ResourceUri.absoluteUri ()`
* JAXP: the `URIResolver` is now called as JAXP specifies, with the `href` as written and the base URI (the empty
  string without one), so a resolver can apply its own default base for a stylesheet without system ID
* JAXP: a transformation into a `DOMResult` or `SAXResult` no longer serialises the result it never uses; added
  `XsltTransformRequest.setPrincipalTreeOnly`
* Added `IXdmNode.declaredNamespaces ()`, a read-only view of the own namespace declarations without copying, used by
  the serialiser and the DOM and SAX bridges
* `DomToXdm` allocates less than half as much per conversion (no per-node walk records, attribute and namespace
  objects only where needed, DOM text used without copying); added `XdmTreeBuilder.characters (String)`
* JAXP: added the factory attribute `ATTR_MAX_DEPTH` and `PhoxtrotTransformer.setMaxDepth` for the nesting depth
  limit of templates and functions
* Behaviour change: `XsltTransformRequest.setMaxDepth` can now raise the limit above the default of 10,000; added
  `XsltTransformRequest.UNLIMITED_DEPTH` for no limit but the stack
* Behaviour change: `fn:generate-id` now returns values of the form `d1n5` (tree number and node number) instead of
  `id5`, so they no longer collide with IDs like `id1` written by authors
* Added `PhoxtrotVersion` with the version number and build timestamp of phoxtrot
* Updated to ph-commons 12.6.0 and replaced own helpers with its new ones (code points, XML whitespace and name
  characters, hex output, primitive `int` collections)
* The caches of compiled regular expressions and UCA collations are now readable without locking
* JAXP: added the factory attribute `ATTR_ENTITY_RESOLVER` and `PhoxtrotTransformer.setEntityResolver`, used for the
  external DTDs and entities of stylesheets, modules, sources and documents read at run time
* JAXP: `XMLConstants.ACCESS_EXTERNAL_DTD` is now applied (new `XdmParser.setAccessExternalDtd`), so secure processing
  also stops external DTDs and entities that only the entity resolver may then provide
* JAXP: a `jar:` URI is only read directly if `ACCESS_EXTERNAL_STYLESHEET` allows `jar` and its nested URI
* A `URIResolver` that refuses a URI now gives a located error with its reason: XTSE0165 for `xsl:include` and
  `xsl:import`, XTSE0020 for `xsl:output parameter-document`, XTSE3000 for `xsl:use-package`, FOXT0002 for
  `fn:transform`, FODC0002 for `document()` and `doc()`, FOUT1170 for `unparsed-text()` and `json-doc()`
* A document read directly that is not well-formed is now logged as a warning
* The `schema-location` of `xsl:import-schema` is now read through the resolver of the compile options (in JAXP the
  `URIResolver` and `ACCESS_EXTERNAL_STYLESHEET`); added `XsltCompileOptions.setReadLocalSchemaFiles`
* Schema processors never read referenced schema documents from the network; added
  `IXdmSchemaProvider.compile (List, boolean)` to forbid local reads as well, and the Xerces-J provider now uses secure
  processing
* Behaviour change: `XsdSchema.validate` no longer follows the `xsi:schemaLocation` hints of an instance unless
  `XsdValidationOptions.setFollowSchemaLocationHints (true)` is set
* Added `FunctionLibrary.freeze`: the function libraries of a compiled stylesheet are now frozen, so a static function
  call no longer reads the modification stamp of the library chain on every call
* Behaviour change: registering into the function library of a compiled stylesheet (e.g. the one of
  `XsltExecutable.createStaticContext`) now throws an `IllegalStateException`; register into a `createChild ()` instead
* XPath: a call of a built-in function whose value only depends on the context item and its variables is evaluated
  once while they stay the same, e.g. `normalize-space($item/a)` in a predicate or `normalize-space(a)` in the body
  of a `some`/`every`/`for` (loop-invariant hoisting); new `DynamicContext.getVariableToken`, `getRunMemo` and
  `getLoopFocus`
* XPath: `//name` from the document node and a child step from a node with many children reuse one sequence of the
  name index per run instead of copying it on every step; new `AxisNavigator.getNamedIndexList`
* API change: `EXsltThreading` is now `com.helger.phoxtrot.xpath.EThreading` and `LargeStackRunner` moved to
  `com.helger.phoxtrot.xpath` (module `phoxtrot-xpath`), so plain XPath evaluations can use the large-stack threads
* XPath: added a step limit for plain XPath (`DynamicContext.setMaxSteps`, XPDY0130 when exceeded, unlimited by
  default)
* XPath: added `CompiledXPath.getFreeVariables` (the variables of the static context an expression uses)
* XPath: a static error now has its position in the expression (`XPathException.getExpressionOffset`,
  `getExpressionLine`, `getExpressionColumn`)
* XPath: added `FunctionLibrary.register (XdmQName, List, SeqType, IXPathFunction)` for extension functions with typed
  signatures; the argument conversion is documented in `IXPathFunction`
* XPath: the public API of `phoxtrot-xpath` is listed in the README and the package documentation
* XSLT: an error raised in the body of an `xsl:function` names that function (`XsltException.getFunctionFrame`)
* DOM bridge: added `DomToXdm.convertWithMapping`, which keeps the DOM node of every XDM node (`DomNodeMapping`)
* DOM bridge: added `XdmDomView`, a read-only W3C DOM view over an XDM tree that copies nothing
* XPath: added `XPathStreamabilityAnalyzer`, the public streamability analysis of expressions and patterns; `EPosture`,
  `ESweep`, `PostureSweep` and `EStreamFunctionCategory` of `stream` are now public API

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
