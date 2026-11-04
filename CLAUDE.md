# ph-xslt - notes for Claude

Pure Java XPath 3.1 / XSLT 3.0 engine, a port of the Go project at `../../git-thirdparty/go-xslt` (read its `CLAUDE.md`
for the architecture). Roadmap: `docs/PLAN.md`.

- Build: `mvn install` (JDK 21; also run on 25). Maven parent is `ph-parent-pom`, ph-commons is the base library.
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
- XML name characters (5th edition): only `XmlNames` (`xpath-xdm`); do not add another copy of the tables.
- `XdmMap` is a persistent hash trie and `XdmArray.append` shares a backing array; both stay immutable from the outside.
  Iterate with `entries ()`/`members ()` (views); `getAllEntries ()`/`getAllMembers ()` copy.
- QT3: `mvn test -pl xpath-conformance -am` (`-Dqt3.only=<set>` for one test set). Every failing case must be named in
  `xpath-conformance/docs/accepted-failures/qt3.md` with a reason, or `QT3Test` fails. Compare fail lists
  (`xpath-conformance/target/qt3-fails.txt`) by name, never by count, and run JDK 21 and 25.
- When merging parallel work, check for the same mechanism implemented twice in different places (a clean git merge
  does not catch it); it happened with the XPath 1.0 compatibility conversions.

