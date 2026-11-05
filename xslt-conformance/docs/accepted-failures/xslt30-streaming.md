# XSLT 3.0 (`xslt30-test`): failing cases of the streaming run

Processor claiming streaming (`-Dxslt30.streaming=true`; no schema-awareness or backwards compatibility claimed):
11,111 of 11,291 applicable cases pass, 180 fail. Measured offline, on JDK 21 and 25, after stage S1 of the streaming
plan (`docs/PLAN.md`): the claim exists, but there is no streamability analysis and no streamed execution yet, so
every construct runs with the whole document in memory. The default run (basic processor) and its failures are in
`xslt30.md`.

The run claims streaming in both directions: the cases that need it run, and the one case written for a processor
without it (`system-property-013`) is skipped. `streaming-fallback` stays unsupported: it names a processor configured
to fall back to non-streamed execution, and this run configures the opposite (`streamable="yes"` is binding,
`XsltCompileOptions.setEnforceStreamability`); `streaming-fallback-005/006` run the stylesheets for which `merge-094/095`
expect XTSE3430. Stylesheets are compiled with the streaming claim, so `system-property('xsl:supports-streaming')` is
"yes", and a principal source with `streaming="true"` in the catalogue is declared streamed
(`XsltTransformRequest.setSourceStreamed`).

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30IT` run with
`-Dxslt30.streaming=true` fails on any failing case that is not named here. Remove an entry when its case passes.
Status **Open** means winnable later (the stage of the streaming plan that addresses it is named); **Accepted** means
a decision not to.

### Expected XTSE3430 (119 cases)
**Status:** Open (stage S3).
The stylesheet declares a construct streamable that is not guaranteed streamable under the rules of XSLT 3.0 section
19, and the case expects the static error XTSE3430. Without a streamability analysis the construct runs buffered.
`accumulator-009s`, `accumulator-029s`, `accumulator-030s`, `accumulator-059`, `accumulator-060`, `accumulator-076`,
`error-3430a`, `merge-094`, `merge-095`, `mode-1903`, `sf-avg-901`, `sf-avg-902`, `sf-boolean-901`, `sf-count-901`,
`sf-current-901`, `sf-empty-901`, `sf-exists-901`, `sf-filter-001`, `sf-innermost-901`, `sf-max-901`, `sf-max-902`,
`sf-min-901`, `sf-min-902`, `sf-not-901`, `sf-reverse-901`, `sf-sum-901`, `sf-sum-902`, `si-copy-901`, `si-copy-902`,
`si-copy-903`, `si-element-901`, `si-element-902`, `si-element-903`, `si-fork-116`, `si-fork-951`, `si-fork-952`,
`si-fork-953`, `si-fork-954`, `si-fork-955`, `si-fork-956`, `si-fork-957`, `si-for-each-806`, `si-for-each-904`,
`si-for-each-905`, `si-for-each-907`, `si-group-017`, `si-group-030`, `si-group-031`, `si-group-052`, `si-group-063`,
`si-group-901`, `si-iterate-035`, `si-iterate-904`, `si-iterate-905`, `si-iterate-907`, `si-lre-901`, `si-lre-902`,
`si-lre-903`, `si-map-901`, `si-map-902`, `si-map-903`, `si-value-of-101`, `streamable-060`, `streamable-061`,
`streamable-062`, `streamable-071`, `streamable-072`, `streamable-079`, `streamable-106`, `streamable-114`,
`streamable-136`, `streamable-142`, `streamable-143`, `streamable-144`, `stream-204`, `su-absorbing-205`,
`su-absorbing-901`, `su-absorbing-904`, `su-absorbing-905`, `su-absorbing-906`, `su-absorbing-908`, `su-absorbing-912`,
`su-ascent-901`, `su-ascent-902`, `su-ascent-903`, `su-ascent-904`, `su-filter-901`, `su-filter-902`, `su-filter-903`,
`su-filter-904`, `su-inspection-901`, `su-inspection-902`, `su-inspection-903`, `su-inspection-904`,
`su-shallow-descent-903`, `su-shallow-descent-904`, `su-shallow-descent-905`, `su-shallow-descent-906`,
`su-shallow-descent-907`, `sx-MapExpr-901`, `sx-MapExpr-902`, `sx-comma-201`, `sx-gc-eq-901`, `sx-gc-eq-902`,
`sx-gc-ge-901`, `sx-gc-ge-902`, `sx-gc-gt-901`, `sx-gc-gt-902`, `sx-gc-le-901`, `sx-gc-le-902`, `sx-gc-lt-901`,
`sx-gc-lt-902`, `sx-gc-ne-901`, `sx-gc-ne-902`, `sx-instance-of-901`, `sx-square-array-201`, `sx-treat-902`,
`sx-union-201`, `sx-union-202`.

go-xslt accepts some of these after measuring them (`si-fork-951`, `si-fork-953`, `si-fork-954`, `su-ascent-903`,
`sx-square-array-201`); they are revisited with the analysis in S3.

### `error-3155a`, `su-shallow-descent-901`
**Status:** Open (stage S3).
XTSE3155: an `xsl:function` without parameters declares a streamability category other than `unclassified`. The rule
belongs to the streamability of stylesheet functions (XSLT 3.0 section 19) and is checked with it.

### `accumulator-061`
**Status:** Open (stage S4).
A dynamic call to `accumulator-after` through a function item captured over a node of a streamed source must fail with
XTDE3350: XSLT 3.0 section 10.3.6 saves an absent focus in a function item when the context item is a streamed node.
The source is declared streamed (`XsltTransformRequest.isSourceStreamed`), but the XPath evaluator does not yet record
that a call came through a captured function item (go-xslt: `Context.ViaFunctionItem`, `checkCapturedFocus`).

### `si-fork-113`, `si-fork-114`, `si-fork-115`
**Status:** Open (stage S4).
Inside a construct declared streamable, a template called from `xsl:for-each-group` sees the current group and current
grouping key as absent (go-xslt follows XSLT 3.0 sections 14.2.1/14.2.2 with a flag set while the body of a
streamable `xsl:source-document` runs). The engine keeps them in the called template. Needs the run-time notion of
"inside a declared-streamable construct" that S4 introduces.

### `merge-079`, `merge-097s`
**Status:** Open (stage S4).
An `xsl:merge-source` with `streamable="yes"` delivers the merged items as snapshots: `merge-079` expects that the
ancestors of a merged node have no other children (`ancestor::record[1]//temp` is empty), `merge-097s` that a snapshot
is taken of each node. The buffered execution hands out the original nodes.

### Schema-awareness needed but not declared (45 cases)
**Status:** Accepted (catalogue).
The shared stylesheets of these cases use `xsl:source-document validation="lax"`/`"strict"` (`stream-A.xsl`,
`stream-B.xsl`) or `xsl:import-schema` (`si-fork-A.xsl`, `si-map-A.xsl`), but the test sets declare only the
streaming dependency. A processor without schema-awareness rejects the whole stylesheet (XTSE1660, XTSE1650), as the
default run requires for the same constructs. All of them pass when schema-awareness is claimed as well
(`-Dxslt30.schemaAware=true -Dxslt30.streaming=true`, checked for the three test sets).
`non-stream-001`, `non-stream-003`, `non-stream-004`, `non-stream-005`, `non-stream-007`, `non-stream-008`,
`non-stream-009`, `non-stream-010`, `non-stream-011`, `non-stream-012`, `non-stream-013`, `non-stream-107`,
`non-stream-108`, `non-stream-109`, `stream-001`, `stream-003`, `stream-004`, `stream-005`, `stream-007`,
`stream-008`, `stream-009`, `stream-010`, `stream-011`, `stream-012`, `stream-013`, `stream-014`, `stream-107`,
`stream-108`, `stream-109`, `si-fork-001`, `si-fork-002`, `si-fork-003`, `si-fork-004`, `si-fork-005`,
`si-fork-006`, `si-fork-007`, `si-fork-008`, `si-fork-009`, `si-map-001`, `si-map-002`, `si-map-003`, `si-map-004`,
`si-map-005`, `si-map-006`, `si-map-008`.

### `sf-reverse-001`
**Status:** Accepted (the expected result contradicts XPath; same as go-xslt).
The expression is `reverse(snapshot(/chapter)//section)/@id`, and the case expects the ids in reverse order. The final
`/@id` is a path step, and XPath 3.1 section 3.3.1 returns the result of `/` in document order. The case itself
points to W3C bug 24125.

### `base-uri-052`, `collection-006`, `copy-1221`, `document-2401`, `document-2402`, `unparsed-text-2002`, `unparsed-text-2003`
**Status:** As in `xslt30.md`.
The seven failures of the default run, for the same reasons; the streaming claim does not change them.
