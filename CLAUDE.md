# ph-xslt - notes for Claude

Pure Java XPath 3.1 / XSLT 3.0 engine, a port of the Go project at `../../git-thirdparty/go-xslt` (read its `CLAUDE.md`
for the architecture). Roadmap: `docs/PLAN.md`.

- Build: `mvn install` (JDK 21; also run on 25). Maven parent is `ph-parent-pom`, ph-commons is the base library.
- The W3C suite runs are integration tests (`*IT`, failsafe) and only run with `-Pconformance`:
  `mvn install -Pconformance` is the full gate; `-Dit.test=<IT class>` picks one. XSLT30 alone takes about 12 minutes.
- W3C suites live outside the repo in `../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`; never copy them in.
- Copyright header is "Philip Helger" only. Tim Riep appears in `NOTICE` and `README.md` only.
- Never read Saxon sources (MPL); work from the W3C specs and the Go code.
- The official W3C specifications always take precedence over the Go implementation: where go-xslt deviates from a spec,
  follow the spec (and say so), do not port the deviation.
- Traversals over input-controlled structure are iterative or depth-limited, never unbounded recursion.
- Nodes are `IXdmNode` (own model, built from JDK SAX; one implementation class per node kind below the sealed
  `AbstractXdmNode`). Do not switch to DOM or IMicroDom as the model.
- Document order: `getOrder ()`/`getEndOrder ()` (end = last node of the subtree, attributes included); a document node
  has a lazily built name index used by `AxisNavigator.collectNamedDescendants`. Every structural change to a
  numbered tree must go through `appendChild`/`removeChild`/`addAttribute`, which drop numbering and index.
- Hosts that evaluate their own XPath against a stylesheet's declarations (Schematron) use
  `XsltExecutable.createStaticContext`/`createEvaluation`, never `Engine` directly; the start-up of a run lives in
  `XsltTransformer.initGlobalContextItem` and is shared by both. `CompiledXPath.evaluate` uses its own static context.
- XML name characters (5th edition): only `XmlNames` (`xpath-xdm`); do not add another copy of the tables.
- `XdmMap` is a persistent hash trie and `XdmArray.append` shares a backing array; both stay immutable from the outside.
  Iterate with `entries ()`/`members ()` (views); `getAllEntries ()`/`getAllMembers ()` copy.
- QT3: `mvn verify -Pconformance -pl xpath-conformance -am` (`-Dqt3.only=<set>` for one test set). Every failing
  case must be named in `xpath-conformance/docs/accepted-failures/qt3.md` with a reason, or `QT3IT` fails. Compare
  fail lists (`xpath-conformance/target/qt3-fails.txt`) by name, never by count, and run JDK 21 and 25.
- Keep `README.md` in sync like a wiki page: status figures, the module table and the setup sections are updated in the
  same change that makes them wrong.
- XSD: the native validator (`xsd-core`) does XSD 1.0 and 1.1; `XsdSchemaProvider` (schema-aware XPath/XSLT) compiles as
  1.1. `XsdNativeIT` gates both versions (`xsd-conformance/target/xsd-native-fails.txt`, `xsd-native11-fails.txt`).
- Streaming (plan: `docs/PLAN.md`, "Streaming"): `-Dxslt30.streaming=true` claims it in the XSLT30 harness (out dir
  `xslt-conformance/target/streaming`, accepted file `xslt30-streaming.md`); with `-Dxslt30.schemaAware=true` as well
  it writes to `target/schema-aware-streaming` and is no gate (`checkAccepted` off by default). The engine switches are
  `XsltCompileOptions.setStreamingClaim` (only `system-property('xsl:supports-streaming')`, default off until streamed
  execution exists), `setEnforceStreamability` (XTSE3430 binding; stored only until S3) and
  `XsltTransformRequest.setSourceStreamed` (recorded only). The default run must keep claiming no streaming.
- Local `xsl:variable`s are lazy (`DynamicContext.withLazyVariable`, evaluated once on first use); `xsl:try` must not
  catch errors of variables declared outside it (`TryScope`, `LazyVariableException`). The XPath `let` stays eager.
- Streamability (XSLT 3.0 section 19): `com.helger.xpath.stream.XPathStreamability` classifies expressions and
  patterns; the XSLT layer supplies its knowledge through `StreamContext`. It fails closed: an unknown construct is
  roaming/free-ranging, never "streamable".
- When merging parallel work, check for the same mechanism implemented twice in different places (a clean git merge
  does not catch it); it happened with the XPath 1.0 compatibility conversions.

