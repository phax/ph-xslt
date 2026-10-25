# XSLT 3.0 (`xslt30-test`): failing cases

Basic processor (no streaming, schema awareness or backwards compatibility claimed): 8,489 of 8,647 applicable cases
pass, 158 fail. Measured offline, on JDK 21 and 25.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30Test` fails on any
failing case that is not named here (switch that off with `-Dxslt30.checkAccepted=false`), so a regression is loud
and a newly accepted failure needs a written reason. Remove an entry when its case passes. Status **Open** means
winnable later; **Accepted** means a decision not to.

### Packages
`accept-001`, `accept-002`, `accept-005`, `accept-007a`, `accept-008`, `accept-009`, `accept-010`, `accept-021`,
`accept-022`, `accept-040`, `accept-041a`, `accept-042`, `accept-043a`, `accept-044`, `accept-045a`, `accept-046`,
`accept-047a`, `expose-002`, `expose-004`, `expose-008`, `expose-009`, `override-as-001`, `override-as-002`,
`override-as-005`, `override-f-001`, `override-f-002`, `override-f-008`, `override-f-009`, `override-f-010`,
`override-f-011`, `override-f-012`, `override-f-013`, `override-f-014`, `override-f-015`, `override-f-016`,
`override-f-017`, `override-f-018`, `override-f-024`, `override-f-026`, `override-f-027`, `override-f-028`,
`override-f-029`, `override-f-032`, `override-f-033`, `override-f-034`, `override-f-035`, `override-m-001`,
`override-m-005`, `override-m-006`, `override-m-007`, `override-m-008`, `override-m-009`, `override-m-010`,
`override-m-012`, `override-m-014`, `override-m-015`, `override-m-016`, `override-m-019`, `override-m-020`,
`override-m-021`, `override-misc-004`, `override-misc-005`, `override-misc-006`, `override-t-001`, `override-t-002`,
`override-t-003a`, `override-t-003b`, `override-t-007`, `override-t-011`, `override-t-014`, `override-t-015`,
`override-v-001`, `override-v-002`, `override-v-003`, `override-v-004`, `override-v-008`, `override-v-009`,
`override-v-010`, `override-v-013`, `override-v-014`, `override-v-015`, `package-001j`, `package-016`, `package-016a`,
`package-016b`, `package-016c`, `package-017`, `package-018`, `package-019`, `package-020`, `package-021`,
`package-022`, `package-100`, `package-101`, `package-910`, `package-version-101`, `package-version-102`,
`package-version-103`, `package-version-104`, `use-package-001`, `use-package-002`, `use-package-004`,
`use-package-005`, `use-package-101`, `use-package-102`, `use-package-103`, `use-package-107`, `use-package-108`,
`use-package-108b`, `use-package-150`, `use-package-151`, `use-package-160`, `use-package-161`, `use-package-170`,
`use-package-171`, `use-package-172`, `use-package-173`, `use-package-174`, `use-package-175`, `use-package-176`,
`use-package-201`, `use-package-202`, `use-package-203a`, `use-package-203c`, `use-package-204a`, `use-package-204c`,
`use-package-205`, `use-package-206a`, `use-package-206c`, `use-package-207`, `use-package-208`, `use-package-209`,
`use-package-210a`, `use-package-210c`, `use-package-211`, `use-package-212`, `function-lookup-004`,
`function-lookup-005`, `function-lookup-006`, `glob-cxt-item-010`, `next-match-036`, `next-match-037`,
`next-match-040`, `xml-to-json-A2-019`

**Status:** Open (packages: in progress).
They use `xsl:use-package`, `xsl:override`, `xsl:accept` or `xsl:expose` across packages, which the engine
rejects with XTSE3000 today (or, for a few, check package visibility rules that depend on it). go-xslt passes all of
them except `override-as-005` (accepted there, see below).

### `override-as-005`
**Status:** Accepted in go-xslt (decision), listed above under packages until they work here.
Two packages each declare a *private* attribute set with the same name. go-xslt resolves attribute-set references
without their package and declines to thread the package through every `use-attribute-sets` site for one test.

### fn:transform
`transform-002`, `transform-003`, `transform-004`, `transform-005`, `transform-006`, `transform-008`, `transform-009`

**Status:** Open (fn:transform: in progress).
They call `fn:transform`, which is not registered yet (XPST0017). go-xslt passes them.

### `collection-006`, `document-2401`, `document-2402`
**Status:** Open (packages: in progress).
They fail today on `xsl:use-package` (XTSE3000). Beyond that, `xsl:strip-space` must apply per package to the
documents loaded through `fn:collection`, `document()` and `fn:doc`. go-xslt accepts these three (decision): it
would mean carrying a "current package" identity through every one of those calls down to the resolver, a large
change for three tests.

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
