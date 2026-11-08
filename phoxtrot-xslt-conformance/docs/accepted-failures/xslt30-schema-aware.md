# XSLT 3.0 (`xslt30-test`): failing cases of the schema-aware run

Schema-aware processor (`-Dxslt30.schemaAware=true`; no streaming or backwards compatibility claimed), with the
native validator of `phoxtrot-xsd` as schema processor (the default of the harness; it compiles imported schemas as XSD
1.1): 9,298 of 9,310 applicable cases pass, 12 fail. Apache Xerces-J 2.12.2 (`-Dxdm.schemaProvider=xerces`, XSD 1.0
only) additionally fails the XSD 1.1 cases listed below. Measured offline, on JDK 21 and 25. The default run (basic processor) and its failures are in `xslt30.md`; this run claims schema-awareness in both
directions: the 686 cases that need it run, and the 31 cases written for a processor without it
(`XSLT30SchemaUnawareCasesIT`; 22 of them apply to the default run) are skipped, 663 applicable cases more than
in the default run.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30IT` run with
`-Dxslt30.schemaAware=true` fails on any failing case that is not named here. Remove an entry when its case passes.
Status **Open** means winnable later; **Accepted** means a decision not to.

### `accumulator-073`
**Status:** Accepted (the expected result contradicts XSLT 3.0).
The case transforms the principal source with `match="/"`, and its unnamed mode (`<xsl:mode on-no-match="shallow-copy"/>`)
has no `use-accumulators`. XSLT 3.0 section 18.2.2: "For a document containing nodes supplied in the initial match
selection, the accumulators that are applicable are those determined by the xsl:mode declaration of the initial mode",
whose default "is an empty list"; and "For a tree T created by copying a node in a tree S using [...] xsl:copy-of
with copy-accumulators="yes", an accumulator is applicable to T if and only if it is applicable to S". So
`f:total-price` applies neither to the source nor to the copies, and `accumulator-before` raises XTDE3362 ("If an
accumulator is not applicable to the tree containing the context item, calls to the functions accumulator-before and
accumulator-after [...] will fail with a dynamic error"). The expected result assumes the accumulator applies. The
sibling cases (`accumulator-063`, `-070` to `-072`, `-074`) start at `xsl:initial-template` and build their tree in a
variable, where every accumulator applies. go-xslt treats a mode without `use-accumulators` as unrestricted
(`acc2checkModeApplies`); that deviation is not ported. Checked against xslt30-test `fddf1cf` (2026-06-10).

### `strip-space-009`
**Status:** Accepted (not required by XSLT 3.0).
The case expects whitespace text nodes to be kept below an element whose type has an XSD 1.1 assertion. XSLT 3.0
section 4.4 exempts only elements whose type annotation is a simple type or a complex type with simple content from
`xsl:strip-space`; no rule about assertions was found in the Recommendation.

### Xerces-J only: `catalog-009`, `validation-1301`, `accumulator-073`, `strip-space-009`, `merge-051`
**Status:** Accepted (schema processor).
The schemas use XSD 1.1, which Xerces-J 2.12.2 does not read: the imported schema is rejected (XTSE0220), or for
`strip-space-009` the source schema, so the source is not valid. The native validator passes `catalog-009`,
`validation-1301` and `merge-051`. `merge-049`, `merge-050` and `merge-052` (schema `books.xsd` as well) pass with
Xerces-J only because the harness accepts any error where the catalog expects XTSE1650.

### `catalog-005`, `catalog-005b`
**Status:** Accepted (same as go-xslt).
They validate every stylesheet of the suite against the schema for XSLT 3.0 and need a schema in scope that the
stylesheet never imports (a global schema cache, not what XSLT 3.0 section 3.16 provides); `catalog-005b`
additionally reads a stylesheet in an encoding the harness resource reader does not decode (FOUT1190). go-xslt lists
both as residual.

### `validation-0201`
**Status:** Accepted (the expected result contradicts Serialization 3.1; same as go-xslt).
A GEDCOM to XHTML transformation of a validated source, serialised with `method="xhtml" indent="yes"`. After the
harness has dropped the whitespace that indentation may add, the one remaining difference is a line break that the
reference file `schvalid001.out` has before `</p>` after "...youngest ever President of the United States.". That
whitespace is not in the result tree: the source `Note` ends in "States.</Note>", the `text()` template in mode `note`
copies the text between the line breaks with `xsl:value-of`, and the literal `<p class="text">` has no other content,
so neither whitespace stripping nor validation is involved. The reference serialiser added it, which Serialization
3.1 section 6.1.4 does not allow ("Whitespace MUST NOT be added other than before or after an element, or adjacent to
an existing whitespace character"; the position is inside `p`, after a text node ending in a full stop), nor would
the XML rule of section 5.1.4 ("MAY be added adjacent to a text node only if the text node contains only whitespace
characters"). Checked against xslt30-test `fddf1cf` (2026-06-10).

### `base-uri-052`, `collection-006`, `copy-1221`, `document-2401`, `document-2402`, `unparsed-text-2002`, `unparsed-text-2003`
**Status:** As in `xslt30.md`.
The seven failures of the default run, for the same reasons; schema-awareness does not change them.
