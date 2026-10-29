# Porting guide (go-xslt to ph-xslt)

Read this fully before writing code. It applies to every workstream.

## Reference material

- Go reference implementation: `/Users/philip/dev/git-thirdparty/go-xslt` (read its `CLAUDE.md` first).
  The XPath engine is in `internal/xpath`, the node tree in `internal/xmltree`.
  `internal/xpath/FNAPI.md` describes how the Go functions are organised.
- W3C QT3 test suite: `/Users/philip/dev/git-thirdparty/qt3tests` (catalog.xml + test sets).
- Specifications: work from the W3C Recommendations (XPath 3.1, F&O 3.1, XDM 3.1, Serialization 3.1). If you need
  precise wording, fetch the live REC text; never trust a stale local copy.
- **Never read Saxon sources** (licence). Port from the Go code and the specs.

## Architecture (the contract)

Module `xpath-xdm` (`com.helger.xpath.xdm`): `IXdmNode` (node tree, document order; sealed `AbstractXdmNode` with one class per node kind; `XdmSchemaType` for schema-validated trees), `XdmQName` (record, prefix not part
of equality), `XdmParser`/`XdmTreeBuilder` (JDK SAX to tree), `IXdmItem` (marker for all items).

Module `xpath-core` (`com.helger.xpath`):

| Package | Content |
|---------|---------|
| `com.helger.xpath` | `XPathException` (unchecked, carries the W3C error code), `XPathCompiler`, `CompiledXPath` |
| `.value` | `XdmSequence` (immutable, 0-based), `XdmAtomic` (type + payload, see its Javadoc for payload classes), `EAtomicType`, `XdmMap`, `XdmArray`, `IXdmFunction`, `XdmDateTimeValue`, `XdmDurationValue`, `DateTimeOps`, `AtomicLexical` |
| `.ast` | `IExpr` (sealed, nested records), `SeqType`, `ItemType`, `NodeTest`, enums. Names are resolved at parse time. |
| `.parser` | `XPathParser` |
| `.eval` | `XPathEvaluator`, `SequenceTypeMatcher`, `NodeSorter` |
| `.ops` | `AtomicCaster`, `AtomicComparator`, `AtomicArithmetic`, `Atomizer` |
| `.collation` | `ICollation`, `CollationRegistry` |
| `.context` | `StaticContext`, `DynamicContext`, `IResourceResolver` |
| `.fn` | `FunctionLibrary` (registration with F&O signature strings), `IXPathFunction`, one class per function family |

Stubs throw `UnsupportedOperationException ("Not implemented yet")`. A workstream owns a set of files (listed in its
brief) and replaces the stubs there. Everything else is read-only for it.

Differences to the Go design, on purpose:

- No bare Go scalars, no `NodeSet` vs. `Sequence` split: every value is an `XdmSequence` of `IXdmItem`s.
- Names are `XdmQName`s resolved by the parser; the evaluator never sees prefixes.
- Errors are `XPathException` with the spec error code; never return error values.
- Function signatures are declared once as F&O signature strings; the evaluator applies the function conversion
  rules generically, so function implementations receive converted arguments.

## Rules for parallel work

1. Only create or edit files your brief assigns to you. New classes go into your package(s).
2. If you need a change in a file you do not own (a new method on `XdmAtomic`, a new field in `DynamicContext`, ...),
   keep it strictly additive, make it, and list it verbatim in your final report (file, signature, why). The
   integrator applies it to the main tree.
3. Build with `mvn -o -q test -pl xpath-core -am` (or `-pl <your module> -am`). **Never run `mvn install`**: other
   workstreams share `~/.m2`.
4. JDK 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` in the same shell command as Maven.
5. Your code must compile against the stubs of the other workstreams. Tests that need code of another workstream
   (e.g. the parser for an evaluator test) may be written but must then be marked `@Ignore ("needs <workstream>")`, so
   the build stays green.

## Coding conventions (mandatory)

Follow `~/.claude/rules/naming.md` exactly. In short:

- Apache 2.0 header, `Copyright (C) 2026 Philip Helger (www.helger.com)` - copy it from an existing file.
- Hungarian notation for fields, parameters and locals: `s` String, `n` int/long, `b` boolean, `c` char, `d` double,
  `f` float, `e` enum, `a` everything else. `m_` instance fields, `s_` static fields, `ALL_UPPER` constants.
  Record components are the exception: plain camelCase.
- Abbreviations in camel case (`getBaseUri`, `XdmQName` stays), except `ID` which is always upper case.
- All parameters `final`. `@NonNull`/`@Nullable` from `org.jspecify.annotations` on non-primitive parameters/returns.
- Space before parentheses and generics: `foo ()`, `new Foo <> ()`, `if (x)`, `List <String>`.
- Private methods start with `_` and are declared **before** their callers. Order: fields, constructors, private
  methods (leaf helpers first), public methods.
- Always import; never inline fully qualified names. Imports: `java*`/`javax*`, `org*`, `com*`, blank line between.
- No auto-boxing/unboxing: `Integer.valueOf (n)`, `aValue.intValue ()`.
- `instanceof` with pattern binding (`if (aItem instanceof final IXdmNode aNode)`).
- No banner/section comments. Javadoc on public API. British English in comments and messages.
- No `@Override` on pure interface method implementations.
- Utility classes: `final`, private constructor, `@PresentForCodeCoverage private static final X INSTANCE`.
- Collections: ph-commons (`ICommonsList`/`CommonsArrayList`, `ICommonsMap`/`CommonsHashMap`) for fields and returns.
- Tests: JUnit 4, class name `<Class>Test`, same package as the class.

## Robustness

- Never recurse over input-controlled structure without a bound. The Go engine limits: 5,000 nested expressions in
  the parser, 10,000 function call levels (`DynamicContext.enterCall`), 10,000 element levels. Limit errors are
  `XPDY0130`. Prefer iteration.
- Results must not depend on platform or locale: no `String.format` without `Locale.ROOT`, no
  `toLowerCase ()` without `Locale.ROOT`, `StrictMath` where a numeric result must be reproducible.
- Call `DynamicContext.checkInterrupt ()` in loops that may run long.

## Wave 2 (function families)

The engine now runs end to end: parser, evaluator, atomic operations and the QT3 harness are merged. Date/time
(`XdmDateTimeValue`, `XdmDurationValue`, `DateTimeOps`, `FnDateTime`, `FnFormat`) is still being written elsewhere -
do not touch those files.

- Test through XPath: `XPathCompiler.compile (sExpr, new StaticContext ()).evaluate (...)`.
- Run QT3 test sets: `mvn -o -q test -pl xpath-conformance -am -Dqt3.only=<test-set name substring>`; fails are in
  `xpath-conformance/target/qt3-fails.txt` (with `qt3.only` the expression, expected and actual result of every fail
  are printed). Test-set names are in `/Users/philip/dev/git-thirdparty/qt3tests/catalog.xml`.
- Compare fail lists **by name**, never by count.
- You may fix clear bugs in non-owned engine code (evaluator, ops, parser, harness) if your test sets need it. Keep each
  fix minimal and record every such change as a unified diff with a one-line reason in `OUTSIDE-CHANGES.md` at the
  workspace root. The integrator merges those by hand.
