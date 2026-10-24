# QT3 (`qt3tests`, XPath 3.1 / F&O 3.1): failing cases

22,307 of 22,478 applicable cases pass, 171 fail. Measured offline, on JDK 21 and 25.

Every failing case is named here in backticks under a heading with its status and reason. `QT3Test` fails on any
failing case that is not named here, so a regression is loud and a newly accepted failure needs a written reason.
Remove an entry when its case passes. Status **Open** means winnable later; **Accepted** means a decision not to.

### fn:transform
`fn-function-lookup-765`, `fn-function-lookup-766a`, `fn-transform-1`, `fn-transform-1a`, `fn-transform-1b`,
`fn-transform-1c`, `fn-transform-1d`, `fn-transform-1e`, `fn-transform-3`, `fn-transform-3a`, `fn-transform-4`,
`fn-transform-5`, `fn-transform-6`, `fn-transform-7`, `fn-transform-7a`, `fn-transform-7b`, `fn-transform-7c`,
`fn-transform-7d`, `fn-transform-7e`, `fn-transform-8`, `fn-transform-9`, `fn-transform-10`, `fn-transform-11`,
`fn-transform-12`, `fn-transform-13`, `fn-transform-13a`, `fn-transform-14`, `fn-transform-15`, `fn-transform-16`,
`fn-transform-17`, `fn-transform-18`, `fn-transform-20`, `fn-transform-21`, `fn-transform-22`, `fn-transform-23`,
`fn-transform-24`, `fn-transform-25`, `fn-transform-26`, `fn-transform-27`, `fn-transform-28`, `fn-transform-29`,
`fn-transform-30`, `fn-transform-31`, `fn-transform-32`, `fn-transform-33`, `fn-transform-34`, `fn-transform-35`,
`fn-transform-36`, `fn-transform-36a`, `fn-transform-37`, `fn-transform-38`, `fn-transform-39`, `fn-transform-40`,
`fn-transform-41`, `fn-transform-42`, `fn-transform-43`, `fn-transform-44`, `fn-transform-45`, `fn-transform-46`,
`fn-transform-47`, `fn-transform-48`, `fn-transform-50`, `fn-transform-51`, `fn-transform-52`, `fn-transform-53`,
`fn-transform-54`, `fn-transform-55`, `fn-transform-56`, `fn-transform-57`, `fn-transform-58`, `fn-transform-60`,
`fn-transform-61`, `fn-transform-62`, `fn-transform-63`, `fn-transform-64`, `fn-transform-65`, `fn-transform-66`,
`fn-transform-67`, `fn-transform-82a`, `fn-transform-82b`, `fn-transform-82c`, `fn-transform-82d`,
`fn-transform-83`, `fn-transform-84`, `fn-transform-88`, `fn-transform-err-8`, `fn-transform-err-9a`

**Status:** Open (needs the XSLT engine).
They call `fn:transform`, which needs an XSLT 3.0 processor (`fn-function-lookup-765/766a` look it up). go-xslt passes
them. `fn:transform` is deliberately not registered as a stub: `fn-transform-901/902/err-14` expect it to be
unavailable and pass today.

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
`K2-DataFunc-6`, `fn-element-with-id-1`, `fn-element-with-id-2`, `fn-element-with-id-3`, `fn-element-with-id-4`,
`fn-element-with-id-5`, `fn-nilled-33`, `fn-nilled-35`, `fn-nilled-37`, `fn-nilled-38`, `fn-nilled-39`,
`fn-nilled-42`, `fn-nilled-43`, `fn-nilled-46`, `fn-nilled-47`, `fn-nilled-50`, `fn-nilled-51`,
`fn-normalize-space-23`, `fn-normalize-space-24`, `fn-string-22`, `fn-string-length-22`, `fn-string-length-23`,
`json-to-xml-016`, `json-to-xml-017`, `json-to-xml-037`, `json-to-xml-038`, `xs-numeric-021`, `xs-numeric-022`

**Status:** Open (needs schema-awareness).
Each uses a source validated against a schema (`validation="strict"`), a schema import, or `json-to-xml` with
`validate=true`: ID-typed elements, `xsi:nil`, element-only and list types, `element(*, xs:numeric)`,
`schema-element(j:...)`. The engine has no schema-awareness; XSD validation is left to the JDK, which exposes no
type annotations. go-xslt is schema-aware and passes them.

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
