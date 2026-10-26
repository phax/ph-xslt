# Schema processing

ph-xslt has a schema provider interface (`com.helger.xpath.xdm.schema`, entry point `XdmSchemaSupport`) with two
implementations today and a third one in progress:

| Provider | Package | Needs | Gives |
|----------|---------|-------|-------|
| JDK validator | `com.helger.xpath.xdm.jaxp` (`JdkSchemaProvider`) | nothing beyond the JDK | validation and type annotations only |
| Apache Xerces-J 2.12.2 | `com.helger.xpath.xdm.xerces` (`XercesSchemaProvider`) | `xerces:xercesImpl` on the class path (optional dependency of `xpath-xdm`) | validation plus the full schema component model |
| Native validator | `com.helger.xsd` (module `xsd-core`) | nothing beyond ph-xslt | planned: validation plus the full component model, see [PLAN.md](PLAN.md) "Native XSD validator" |

## Why Xerces-J is needed for some features and tests

### What the JDK validator cannot tell

The JDK ships its own copy of Xerces inside the `java.xml` module (`com.sun.org.apache.xerces.internal.*`). That copy
builds the full schema component model internally, but the module does not export those packages, so ph-xslt cannot
reach it without `--add-exports`. What the public JAXP validation API (`javax.xml.validation`) exposes is only the
`org.w3c.dom.TypeInfo` of each validated element and attribute: the type name, its namespace, and a derivation check
against a named type (`isDerivedFrom`).

From that, `JdkTypeDefinition` can derive the type name, simple vs. complex, list types and the built-in atomic base,
which is enough for type annotations, typed values of simple types, `fn:nilled` and schema-based IDs. Everything that
needs the schema *components* is missing (verified in `JdkSchemaSet` and `JdkTypeDefinition`):

| Needed for | Component information | JDK validator |
|------------|-----------------------|---------------|
| FOTY0012 when atomizing element-only content; typed value of complex types with simple content | content type of a complex type | not available (`getContentType` throws) |
| `schema-element()`, `schema-attribute()`, `xsl:import-schema`, `fn:json-to-xml` with `validate=true` | element and attribute declarations by name, substitution groups | lookups return `null` |
| user-defined atomic, list and union types in XPath (casts, constructor functions, `instance of`), `[xsl:]type` | type definitions by name, base type, validating a single value against a type | lookups return `null`, `getBaseType` and `validate` throw |
| `[xsl:]validation` on constructed nodes | validating an element in place against a declaration or type | not available |

`IXdmTypeDefinition.hasComponentModel ()` is `false` for JDK types and `XdmSchemaType.getDefinition ()` returns `null`
for them; callers such as the atomizer then fall back to what the type annotation alone allows.

### Features that need Xerces-J

* **Schema-aware XSLT** (`XsltCompileOptions.setSchemaAware (true)`): `xsl:import-schema`, `[xsl:]validation`,
  `[xsl:]type`, typed modes, `schema-element()`. Claiming it without Xerces-J throws an `IllegalStateException` when
  the stylesheet is compiled (`StylesheetCompiler`). Without the claim the processor is a basic XSLT 3.0 processor, as
  XSLT 3.0 section 26 allows.
* **User-defined types in XPath** and the element/attribute declaration tests listed above.

### Tests that need Xerces-J

Xerces-J is in test scope of `xslt-core`, `xpath-conformance` and `xslt-conformance`, and a compile dependency of the
(not deployed) `xsd-conformance`, so these runs use it:

* **QT3**: 7 cases pass only with Xerces-J (`K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23` need the
  content type for FOTY0012; `json-to-xml-016/017/037/038` need element declarations for `schema-element(j:...)` and
  `validate=true`). `-Dqt3.schemaProcessor=jdk` runs QT3 on the JDK validator: 66 fails instead of 59, otherwise
  identical by name. See `xpath-conformance/docs/accepted-failures/qt3.md`.
* **XSLT30 schema-aware run** (`-Dxslt30.schemaAware=true`): all 9,310 applicable cases run with Xerces-J; 18 fail
  (`xslt-conformance/docs/accepted-failures/xslt30-schema-aware.md`). The default run (8,647 cases) does not need it.
  `XSLT30SchemaUnawareCasesTest` checks the 31 cases that require a processor *without* schema-awareness in both claim
  states.
* **xsdtests** (`xsd-conformance`): Xerces-J is the reference validator until the native one takes over (V6).
* **`NoXercesTest`**: a second surefire execution of `xslt-core` without Xerces-J on the class path checks that the
  basic processor and the JDK validator work without it and that no Xerces-J class is loaded.

### How Xerces-J is kept contained

* `XdmSchemaSupport` checks for the class `org.apache.xerces.xs.XSModel` and only then loads
  `com.helger.xpath.xdm.xerces.XercesSchemaProvider` by name; without the jar no Xerces-J class is touched.
* The provider instantiates `org.apache.xerces.jaxp.validation.XMLSchemaFactory` directly and builds the `XSModel`
  from the same grammar pool, so the types the validator reports are the model's own components. It never goes
  through a JAXP `newInstance` lookup.
* XML parsing stays on the JDK parser (`SAXParserFactory.newDefaultInstance ()`). The parsed tree is replayed as SAX
  events through a Xerces-J `ValidatorHandler` into a copy, and the annotations are transferred back
  (`AbstractJaxpSchemaSet`).
* `xml-apis` is excluded: the `java.xml` module already provides `javax.xml`, `org.w3c.dom` and `org.xml.sax`, and the
  jar would put a second, older copy of these APIs on the class path. No resolver or catalogue library is used.

## Why Xerces-J is only the fallback

The decision (2026-10-26) is that the native validator, ported from go-xslt, becomes the default provider and
Xerces-J stays as an optional alternative and as the differential reference. Xerces-J was the quickest way to get
schema-awareness, but it is not a good permanent base:

* **Maintenance.** 2.12.2 is the latest release on Maven Central (metadata last updated 2022-01-27; the jar was built
  on 2022-01-21); ph-xslt depends on it as is.
* **XSD 1.0 only.** The published artifact does not read XSD 1.1 schemas. 5 of the 18 schema-aware XSLT30 fails
  (`accumulator-073`, `catalog-009`, `merge-051`, `strip-space-009`, `validation-1301`) are XSD 1.1 schemas, and the
  whole XSD 1.1 part of xsdtests (41,553 cases) is unsupported. go-xslt's validator passes 99.99% of both versions.
* **Conformance.** On xsdtests XSD 1.0, Xerces-J fails 256 of 39,401 cases, go-xslt's validator 5 (same
  denominator). Without the 92 offline cases, the rest are mainly Xerces-J deviations: regular expressions (26),
  pre-Fifth-Edition XML name characters (16), `minOccurs`/`maxOccurs` of 2^31 and above (15), `vc:minVersion` (12),
  the `--MM--` gMonth form (26), identity constraints, particle restriction and others. Details:
  `xsd-conformance/docs/accepted-failures/xsd.md`.
* **It changes JAXP for the whole application.** The jar registers itself in `META-INF/services` for
  `SAXParserFactory`, `DocumentBuilderFactory`, `SchemaFactory`, `DatatypeFactory`, `XMLEventFactory`,
  `DOMImplementationSourceList` and `org.xml.sax.driver`. Its mere presence changes what every `newInstance ()` call
  in the application returns, not only in ph-xslt. ph-xslt itself had to switch its catalogue readers and several
  tests to `newDefaultInstance ()` so that the default run stays independent of it.
* **The JAXP API hides part of the model.** Through JAXP a compiled schema is "fully composed": instance
  `xsi:schemaLocation` hints are ignored (6 xsdtests cases), and a second import of an already known namespace is not
  followed (2 cases, or 2 others with `honour-all-schemaLocations`).
* **Resource limits.** Without secure processing, Xerces-J expands large occurrence ranges in content models until
  the heap is exhausted (`particlesZ036_*`); with it, content models are capped at 3,000 nodes, which makes 6 valid
  test cases fail. go-xslt uses a position-set matcher and a linear counting matcher that need neither.
* **API defects.** The DOM `TypeInfo.isDerivedFrom (null, ...)` of a Xerces-J complex type in no namespace returns
  `true` for an unrelated type and even for a type name that does not exist, while the XS API
  (`XSTypeDefinition.derivedFrom`) answers `false` for both (reproduced with 2.12.2 on 2026-10-26).
  `XercesTypeDefinition` therefore checks derivation through the XS API only.
* **Integration cost.** Validating an `XdmNode` tree means replaying it as SAX events into a copy and transferring the
  annotations back, so every validated document is built twice. A validator working on `XdmNode` directly (as go-xslt's
  `ValidateNode`) validates in place.
