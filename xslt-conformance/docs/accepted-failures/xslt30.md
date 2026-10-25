# XSLT 3.0 (`xslt30-test`): failing cases

Basic processor (no streaming, schema awareness or backwards compatibility claimed): 8,637 of 8,647 applicable cases
pass, 10 fail. Measured offline, on JDK 21 and 25.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30Test` fails on any
failing case that is not named here (switch that off with `-Dxslt30.checkAccepted=false`), so a regression is loud
and a newly accepted failure needs a written reason. Remove an entry when its case passes. Status **Open** means
winnable later; **Accepted** means a decision not to.

### `function-lookup-005`, `function-lookup-006`
**Status:** Open.
`fn:function-lookup` called inside a used package must see exactly the functions visible in that package. Here it
also finds functions the package hides (`divide` and `subtract` report `exists="true"`, expected `false`), and
`f:add` resolves to the overriding function of the using package instead of the package's own one
(`effect="8"`, expected `7`; 006 does the same through `xsl:original`). go-xslt passes both
(`ResolveLookupFunction`).

### `glob-cxt-item-010`
**Status:** Open.
An `xsl:global-context-item` in a library package must be ignored unless it says `use="required"`. The case passes
when the `glob-cxt-item` set runs on its own and fails only in the full run, so the cause is probably state shared
between cases (the stylesheet cache or the package registration), not the rule itself. Not analysed yet.

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
