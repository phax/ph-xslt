# XSLT 3.0 (`xslt30-test`): failing cases

Basic processor (no streaming, schema awareness or backwards compatibility claimed): 8,640 of 8,647 applicable cases
pass, 7 fail. Measured offline, on JDK 21 and 25.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30IT` fails on any
failing case that is not named here (switch that off with `-Dxslt30.checkAccepted=false`), so a regression is loud
and a newly accepted failure needs a written reason. Remove an entry when its case passes. Status **Open** means
winnable later; **Accepted** means a decision not to.

### `collection-006`, `document-2401`, `document-2402`
**Status:** Accepted (same decision as go-xslt).
`xsl:strip-space` must apply per package to the documents loaded through `fn:collection`, `document()` and
`fn:doc`. That means carrying a "current package" identity through every one of those calls down to the resolver,
a large change for three tests; go-xslt declines it for the same reason.

### `unparsed-text-2002`, `unparsed-text-2003`
**Status:** Accepted (offline only).
Both read live pages from www.w3.org. The harness never fetches network resources, so that a result never depends
on the network; the resource is unavailable (FOUT1170, resp. `unparsed-text-available` returns false). go-xslt
accepts the same two offline and passes them with its network switch on.

### `base-uri-052`
**Status:** Accepted.
Needs XInclude (the expected base URIs are those of included elements). XInclude is a separate specification and
XSLT 3.0 does not require it. go-xslt accepts the same case.

### `copy-1221`
**Status:** Accepted.
`xsl:copy-of` with `copy-namespaces="no"`: the copied `aa` element still carries the namespace
`a=http://www.a.com/` of its original parent, which the expected result (changed in 2019) leaves to namespace fixup
alone. go-xslt accepts `copy-1220` and `copy-1221` for the same reason: the only fix found there loses more cases
than it gains. `copy-1220` passes here.
