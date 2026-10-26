# XSLT 3.0 (`xslt30-test`): failing cases of the schema-aware run

Schema-aware processor (`-Dxslt30.schemaAware=true`; no streaming or backwards compatibility claimed), with the
native validator of `xsd-core` as schema processor (the default of the harness): 9,293 of 9,310 applicable cases
pass, 17 fail. With Apache Xerces-J 2.12.2 (`-Dxdm.schemaProvider=xerces`) 9,292 pass and 18 fail: the 17 cases of
this file plus `merge-051`, which the native validator passes (see below). Measured offline, on JDK 21 and 25. The default run (basic processor) and its failures are in `xslt30.md`; this run claims schema-awareness in both
directions: the 686 cases that need it run, and the 31 cases written for a processor without it
(`XSLT30SchemaUnawareCasesTest`; 22 of them apply to the default run) are skipped, 663 applicable cases more than
in the default run.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30Test` run with
`-Dxslt30.schemaAware=true` fails on any failing case that is not named here. Remove an entry when its case passes.
Status **Open** means winnable later; **Accepted** means a decision not to.

### `accumulator-073`, `catalog-009`, `strip-space-009`, `validation-1301`
**Status:** Accepted (schema processor).
The schemas use XSD 1.1 (`xs:assert`, `xs:alternative`), which neither Apache Xerces-J 2.12.2 nor the native
validator (XSD 1.0 only until phase V8) supports: the imported schema is rejected (XTSE0220), or for `strip-space-009`
the source schema, so the source is not valid. go-xslt has its own XSD 1.1 validator and passes them.

### `merge-051` (Xerces-J only)
**Status:** Accepted (schema processor).
Xerces-J rejects the XSD 1.1 schema `books.xsd` (XTSE0220). The native validator reads it (a `vc:minVersion`
assertion is left out under XSD 1.0), and since `xsl:merge-source` honours `validation` and `type`, the case passes.
`merge-049`, `merge-050` and `merge-052` pass with the native validator for the same reason; Xerces-J passes them only
because the harness accepts any error where the catalog expects XTSE1650.

### `catalog-005`, `catalog-005b`
**Status:** Accepted (same as go-xslt).
They validate every stylesheet of the suite against the schema for XSLT 3.0 and need a schema in scope that the
stylesheet never imports (a global schema cache, not what XSLT 3.0 section 3.16 provides); `catalog-005b`
additionally reads a stylesheet in an encoding the harness resource reader does not decode (FOUT1190). go-xslt lists
both as residual.

### `validation-0201`
**Status:** Open.
A large GEDCOM to XHTML transformation of a validated source with `xsl:strip-space elements="*"`. The output matches
the reference except for the whitespace before `</p>` at the end of the last note paragraph (the reference keeps a
space there). Not analysed further.

### `base-uri-052`, `collection-006`, `copy-1221`, `document-2401`, `document-2402`, `function-lookup-005`, `function-lookup-006`, `glob-cxt-item-010`, `unparsed-text-2002`, `unparsed-text-2003`
**Status:** As in `xslt30.md`.
The ten failures of the default run, for the same reasons; schema-awareness does not change them.
