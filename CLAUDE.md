# ph-xslt - notes for Claude

Pure Java XPath 3.1 / XSLT 3.0 engine, a port of the Go project at `../../git-thirdparty/go-xslt` (read its `CLAUDE.md`
for the architecture). Roadmap: `docs/PLAN.md`.

- Build: `mvn install` (JDK 21; also run on 25). Maven parent is `ph-parent-pom`, ph-commons is the base library.
- W3C suites live outside the repo in `../../git-thirdparty/{qt3tests,xslt30-test,xsdtests}`; never copy them in.
- Copyright header is "Philip Helger" only. Tim Riep appears in `NOTICE` and `README.md` only.
- Never read Saxon sources (MPL); work from the W3C specs and the Go code.
- Traversals over input-controlled structure are iterative or depth-limited, never unbounded recursion.
- Nodes are `XdmNode` (own model, built from JDK SAX). Do not switch to DOM or IMicroDom as the model.
