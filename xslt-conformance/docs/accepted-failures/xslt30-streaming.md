# XSLT 3.0 (`xslt30-test`): failing cases of the streaming run

Processor claiming streaming (`-Dxslt30.streaming=true`; no schema-awareness or backwards compatibility claimed):
11,230 of 11,291 applicable cases pass, 61 fail. Measured offline, on JDK 21 and 25, after stage S4 of the streaming
plan (`docs/PLAN.md`), and unchanged on JDK 21 after the execution part of S5 (streamed `xsl:iterate`,
`xsl:for-each-group`, `xsl:fork`, `xsl:merge-source`, record paths with predicates, wrappers with attribute value
templates): the streamability analysis (`com.helger.xslt.stream.XsltStreamability`) makes `streamable="yes"` binding,
and a construct it proves guaranteed streamable whose sequence constructor has the shape the dispatcher runs
(`com.helger.xslt.stream.StreamPlanner`) reads its document as a stream (`com.helger.xpath.xdm.XdmStreamReader`,
`com.helger.xslt.stream.StreamedRun`); everything else runs with the whole document in memory. The default run (basic processor) and its failures are in `xslt30.md`.

The run claims streaming in both directions: the cases that need it run, and the one case written for a processor
without it (`system-property-013`) is skipped. `streaming-fallback` stays unsupported: it names a processor configured
to fall back to non-streamed execution, and this run configures the opposite (`streamable="yes"` is binding,
`XsltCompileOptions.setEnforceStreamability`); `streaming-fallback-005/006` run the stylesheets for which `merge-094/095`
expect XTSE3430. Stylesheets are compiled with the streaming claim, so `system-property('xsl:supports-streaming')` is
"yes", and a principal source with `streaming="true"` in the catalogue is declared streamed
(`XsltTransformRequest.setSourceStreamed`). When such a source is only the initial match selection of the initial mode
(no context item select, no parameters), the harness passes its file with `XsltTransformRequest.setStreamedSourceUri`,
so that a streamable initial mode reads it as a stream.

Every failing case is named here in backticks under a heading with its status and reason. `XSLT30IT` run with
`-Dxslt30.streaming=true` fails on any failing case that is not named here. Remove an entry when its case passes.
Status **Open** means winnable later (the stage of the streaming plan that addresses it is named); **Accepted** means
a decision not to.

### `streamable-100`, `streamable-101`, `streamable-102`
**Status:** Accepted (the suite asks for more than the Recommendation; same as go-xslt).
A quantified expression (`some $t in transaction satisfies ...`) over streamed nodes in a template rule of a
streamable mode. The stylesheets say themselves that they are "streamable in Saxon but not guaranteed-streamable
according to W3C rules", and the catalogue keywords are `_WRONG:not-streamable`. The engine raises XTSE3430, which
the Recommendation requires for a construct declared streamable that is not guaranteed streamable. These cases passed
before S3 only because no analysis ran.

### `si-fork-951`, `si-fork-954`
**Status:** Accepted (measured trade-off; same as go-xslt).
An `xsl:fork`/`xsl:for-each-group` inside a streamable `xsl:source-document` whose body reads `current-group()` twice
(`si-fork-951`) or `current-group()` and `.` (`si-fork-954`). Two consuming operands make the body roaming and
free-ranging (section 19.8.1), so XTSE3430 is correct. The body of an `xsl:source-document` is reported only for a
verdict the analysis marks as stated unconditionally by the Recommendation; the two-consuming-operands clause is not
marked so for instruction operands, because go-xslt measured that doing so loses more cases than it wins
(`si-copy-028`). The construct is classified as not streamable and runs buffered.

### `si-fork-953`
**Status:** Accepted (same as go-xslt).
`xsl:sort` in a streamed `xsl:for-each-group` that is the child of `xsl:fork`. The case's own description asks "should
this be an error?"; the rule of section 19.8.4.19 for a sorted group inside `xsl:fork` does not settle it, so the
verdict is not reported as definite and the body of the `xsl:source-document` runs buffered.

### `su-ascent-903`
**Status:** Accepted (the expected result contradicts section 19.8.5.7; same as go-xslt).
An ascent function that may return its streaming parameter `$element` itself. Section 19.8.5.7 makes a reference to
the streaming parameter of an ascent function climbing and motionless, and a climbing, motionless result is allowed
for the category, so the function is guaranteed streamable under the Recommendation's rules.

### `sx-square-array-201`
**Status:** Accepted (same as go-xslt).
`[$extraItem, //ITEM]?*/PRICE` in a streamable `xsl:source-document`: a crawling and a grounded member, so the
expression is not streamable. The verdict comes from the general rules, not from a rule the analysis reports as
definite for an `xsl:source-document`, so the body runs buffered instead of raising XTSE3430.

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
