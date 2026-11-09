# XSLT 3.0 (`xslt30-test`): failing cases of the backwards compatible run

Processor claiming backwards compatibility, the XSLT 1.0 compatibility feature of XSLT 3.0 section 27.4
(`-Dxslt30.backwardsCompatible=true`; no schema-awareness or streaming claimed): 8,751 of 8,762 applicable cases pass,
11 fail. Measured offline, on JDK 21. Stylesheets are compiled with `XsltCompileOptions.setBackwardsCompatibleClaim`, so an
element whose effective version is below 2.0 is processed with XSLT 1.0 behaviour (section 3.9.1) instead of raising
XTDE0160, and `system-property('xsl:supports-backwards-compatibility')` is "yes". The run claims the feature in both
directions: the cases that need it run, and the cases written for a processor without it (`error-0160a`,
`version-031`, `initial-template-080`, `initial-template-081`, `system-property-019a`, `system-property-020`) are
skipped; they run in the default run (`xslt30.md`).

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30IT` run with
`-Dxslt30.backwardsCompatible=true` fails on any failing case that is not named here. Remove an entry when its case
passes. Status **Open** means winnable later; **Accepted** means a decision not to.

## Failures of the default run

The seven failures of the default run (`xslt30.md`) fail here for the same reasons; none of them involves backwards
compatible processing.

### `collection-006`, `document-2401`, `document-2402`
**Status:** Accepted (see `xslt30.md`).
`xsl:strip-space` per package for documents loaded through `fn:collection`, `document()` and `fn:doc`.

### `unparsed-text-2002`, `unparsed-text-2003`
**Status:** Accepted (offline only, see `xslt30.md`).
Both read live pages from www.w3.org.

### `base-uri-052`
**Status:** Accepted (see `xslt30.md`).
Needs XInclude.

### `copy-1221`
**Status:** Accepted (see `xslt30.md`).
`xsl:copy-of` with `copy-namespaces="no"` and the namespace of the original parent.

## Failures of the backwards compatible run

### `docbook-001`, `docbook-002`
**Status:** Accepted (same decision as go-xslt).
The DocBook XSL stylesheets (version 1.0) call the EXSLT extension function `exsl:node-set()` without testing that it
is available. With XSLT 1.0 behaviour the call is bound to the fallback error function (section 3.9.1), so it raises
XTDE1425 when it is evaluated (`docbook-001`), or the fallback branch of the stylesheet produces other output
(`docbook-002`). EXSLT is not a W3C specification and XSLT 3.0 does not require it; it is not implemented.

### `xpath-compat-0302`, `version-021`
**Status:** Accepted (the W3C specification takes precedence over the expected results; go-xslt special-cases it).
`round(doc/none)` and `floor(nonexistent)` in a 1.0 region, where the path selects no node: the suite expects `NaN`
(the XPath 1.0 result). XPath 3.1 section 3.1.5.2 applies the XPath 1.0 compatibility conversions only to an argument
that is "not of the expected type", and the empty sequence is an instance of `xs:numeric?`, the parameter type of
`fn:round` and `fn:floor` (F&O 3.1 sections 4.4.3 and 4.4.4), so the argument stays empty and so does the result, as
F&O specifies; XSLT 3.0 section 3.9 says the result of backwards compatible processing is defined by the XSLT 3.0 and
XPath 3.0 specifications, not by earlier versions. go-xslt returns NaN for an empty argument of the numeric functions
under XSLT 1.0 behaviour; that deviation is not ported.
