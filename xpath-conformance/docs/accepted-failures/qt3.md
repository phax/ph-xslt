# QT3 (`qt3tests`, XPath 3.1 / F&O 3.1): failing cases

22,412 of 22,478 applicable cases pass, 66 fail. Measured offline, on JDK 21 and 25.

Every failing case is named here in backticks under a heading with its status and reason. `QT3Test` fails on any
failing case that is not named here, so a regression is loud and a newly accepted failure needs a written reason.
Remove an entry when its case passes. Status **Open** means winnable later; **Accepted** means a decision not to.

### fn:transform unavailable
`fn-transform-901`, `fn-transform-902`, `fn-transform-err-14`

**Status:** Accepted (decision).
They depend on the feature `fn-transform-XSLT` (901, 902) or `fn-transform-XSLT30` (err-14) being *not* satisfied and
expect `fn:transform` to report that no (XSLT 3.0) processor is available. With xslt-core on the class path
`fn:transform` is a real XSLT 3.0 processor, which necessarily contradicts them. go-xslt accepts the same cases.

### XQuery modules
`fn-function-lookup-760`, `fn-load-xquery-module-014`, `fn-load-xquery-module-015`, `fn-load-xquery-module-018`,
`fn-load-xquery-module-019`, `fn-load-xquery-module-020`, `fn-load-xquery-module-021`, `fn-load-xquery-module-022`,
`fn-load-xquery-module-025`, `fn-load-xquery-module-026`, `fn-load-xquery-module-027`, `fn-load-xquery-module-028`,
`fn-load-xquery-module-029`, `fn-load-xquery-module-030`, `fn-load-xquery-module-033`, `fn-load-xquery-module-034`,
`fn-load-xquery-module-035`, `fn-load-xquery-module-036`, `fn-load-xquery-module-037`, `fn-load-xquery-module-040`,
`fn-load-xquery-module-041`, `fn-load-xquery-module-042`, `fn-load-xquery-module-045`, `fn-load-xquery-module-046`,
`fn-load-xquery-module-050`, `fn-load-xquery-module-051`, `fn-load-xquery-module-061`, `fn-load-xquery-module-064`,
`fn-load-xquery-module-065`, `fn-load-xquery-module-068`, `fn-load-xquery-module-069`, `fn-load-xquery-module-070`,
`fn-load-xquery-module-073`, `fn-load-xquery-module-074`

**Status:** Accepted (decision).
They load XQuery library modules. This is an XPath processor without an XQuery grammar; `fn:load-xquery-module`
raises FOQM0006 as F&O 3.1 specifies for that case. go-xslt accepts the same cases.

### XPath static typing
`fn-filter-007`, `fn-filter-016`, `fn-filter-019`, `fn-filter-022`, `fn-for-each-pair-032`,
`fn-function-arity-010`, `fn-function-arity-012`, `fn-function-lookup-713`, `fn-function-name-010`,
`fn-has-children-012`, `fn-has-children-013`, `fn-innermost-008`, `fn-innermost-010`, `fn-outermost-008`,
`fn-outermost-010`, `fn-unparsed-text-009`, `fn-unparsed-text-available-007`, `fn-unparsed-text-available-009`,
`fn-unparsed-text-available-011`, `fn-unparsed-text-lines-009`

**Status:** Accepted (decision).
They depend on the optional Static Typing Feature and expect XPTY0004 from static type inference over `if/then/else`
branch unions or inline function bodies. A processor that checks types at run time correctly raises nothing. Only the
narrow static axis-step analysis (`ST-Axes*`, XPST0005) is implemented, behind `StaticContext.setStaticTyping`.
go-xslt accepts the same 20 cases.

### Schema-validated sources
`K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23`, `json-to-xml-016`, `json-to-xml-017`,
`json-to-xml-037`, `json-to-xml-038`

**Status:** Open (needs more schema information than the JDK validator exposes).
Sources with `validation="strict"` are validated with the JDK validator, which provides type annotations, nilled,
schema IDs and typed values (`XdmParser.setSchema`). What it does not provide: whether a complex type has element-only
or mixed content, so atomizing an element with element-only content yields its string value instead of FOTY0012
(`K2-DataFunc-6`, `fn-normalize-space-24`, `fn-string-length-23`); and element declarations, so there are no in-scope
schema types for `schema-element(j:...)` and no validation for `json-to-xml` with `validate=true` (`json-to-xml-016`,
`json-to-xml-017`, `json-to-xml-037`, `json-to-xml-038`). go-xslt has its own XSD processor and passes them.

### External entity in fn:parse-xml
`parse-xml-010`

**Status:** Accepted (decision).
`fn:parse-xml` does not load external entities (`XdmParser` keeps them off by default). Loading them is
implementation-defined and a security boundary. go-xslt accepts the same case.

### Network access
`fn-unparsed-text-054a`

**Status:** Accepted.
It reads `https://timeanddate.com`. The harness runs offline, and that server now answers every client with 403, so
the case fails even with network access. go-xslt accepts the same case.
