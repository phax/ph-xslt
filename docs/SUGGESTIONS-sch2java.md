# Suggestions from ph-sch2java

Suggestions for ph-xslt that came up in ph-sch2java (Schematron interpreter and Schematron → Java generator on top of
ph-xslt). Proposals only; the numbering continues the earlier suggestions S1 to S6 and S8 (all done, see `PLAN.md`).

## S7 - Simple xsl:function bodies as XPath conditional (key indexes and calls)

Status: measured, not implemented (2026-11-06, `PLAN.md` "Function calls, conversions and key patterns"). A working
implementation (compiled bodies with lazy variables and exact error locations) gave no measurable gain: 100,000 key
`use` calls 94 ms before and 97 ms after, against 79 ms for the same `use` written inline. The instruction
interpreter is a few percent of a call; the 36.6 % was inclusive of the XPath work below the call. The costs are the
function lookup per call, the `as` conversions and the pattern matcher allocation per key match, which are being
fixed instead.

Status: implemented on branch `sch-s7` (2026-11-05), no measurable gain, not merged (2026-11-06).

**Correction of the finding:** the 36.6 % for `Engine._buildKeyIndex` on a transaction statistics report (TSR) with
50,000 subtotals of each type was an inclusive figure: it counted all the XPath work of the `use` expressions and the
match patterns. Split by self time (JFR, `-XX:+DebugNonSafepoints`, ph-xslt `main` 5fb228e, 2026-11-06), the key
index build is 41 % of the validation, of which the instruction interpreter (`SequenceConstructor`, `xsl:choose`,
`xsl:value-of`, `xsl:text`, `UserFunction`) is 0.4 % of the validation; XDM navigation and values 9.5 %, the XPath
evaluator 9.1 %, XPath functions 6.5 %, the walk over the document and the match patterns about 8 %. Only 8.4 % of the
validation is under a function call at all (inclusive).

**Measured with ph-sch2java** (generated code, best warm run of six, three alternating rounds, load about 1): TSR with
50,000 subtotals 2,506-2,602 ms (`main`) against 2,558-2,720 ms (`sch-s7`); TSR with 10,000 395-421 against 406-416 ms;
end user statistics report with 10,000 subsets 403-423 against 404-413 ms. This confirms the measurement of ph-xslt: no
gain.
