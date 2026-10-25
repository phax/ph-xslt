# xslt-core: how to add instructions, declarations and functions

Read this fully before adding an XSLT instruction family. It is the Java counterpart of go-xslt's
`internal/xslt/INSTRAPI.md`. The coding conventions of `docs/PORTING.md` apply unchanged.

## Where things live

| Package | Content |
|---------|---------|
| `com.helger.xslt` | Facade: `XsltCompiler`, `XsltExecutable`, `XsltCompileOptions`, `XsltTransformRequest`, `XsltTransformResult`, `XsltException` |
| `com.helger.xslt.tree` | The stylesheet after static processing: `XslModule`, `XslElement`, `XslText`, `XsltNames` (lexical checks) |
| `com.helger.xslt.compile` | `StylesheetLoader` (modules, use-when, static variables, shadow attributes), `ElementValidator` + `XsltElementSpecs` (static validation), `StylesheetCompiler` (declarations), `CompileContext` (the compile API), `XPathExpr`, `Avt`, `CompiledPattern`, the registry `XsltExtensionRegistry` and its SPI interfaces |
| `com.helger.xslt.model` | Compiled declarations: `CompiledStylesheet`, `Template`, `TemplateRule`, `Mode`, `VariableDef`, `UserFunction`, `KeyDefinition`, `AttributeSet`, `OutputDefinition`, `SpaceRules` |
| `com.helger.xslt.instr` | Instructions: `IInstruction`, `CompiledInstruction` (+ `IInstructionBody`), `SequenceConstructor`, `SortKeyDef`, `SimpleContent`, the core instruction families |
| `com.helger.xslt.runtime` | `Engine` (one transformation), `XsltContext` + `XsltState` (run-time context), outputs (`IXsltOutput`, `SequenceOutput`, `ContentOutput`, `BufferedOutput`), `NodeCopier`, `NamespaceFixup`, `XsltTransformer` |
| `com.helger.xslt.fn` | `XsltFunctions`: the XSLT functions of the core |
| `com.helger.xslt.ext.<family>` | One package per instruction family of wave X2 (yours) |

## Registering a family

Each family is one class implementing `IXsltExtension` with a public no-argument constructor. The registry
(`XsltExtensionRegistry.getInstance ()`) instantiates every class listed in
`XsltExtensionRegistry.EXTENSION_CLASS_NAMES` that is on the class path, so the agreed class name is all the core
needs; you do not edit any core file:

| Family | Class |
|--------|-------|
| xsl:number | `com.helger.xslt.ext.number.NumberExtension` |
| xsl:for-each-group, current-group(), current-grouping-key() | `com.helger.xslt.ext.grouping.GroupingExtension` |
| xsl:merge, current-merge-group(), current-merge-key() | `com.helger.xslt.ext.merge.MergeExtension` |
| xsl:iterate, xsl:break, xsl:next-iteration, xsl:on-completion | `com.helger.xslt.ext.iterate.IterateExtension` |
| xsl:try, xsl:catch | `com.helger.xslt.ext.trycatch.TryCatchExtension` |
| xsl:result-document | `com.helger.xslt.ext.resultdocument.ResultDocumentExtension` |
| xsl:accumulator, accumulator-before(), accumulator-after() | `com.helger.xslt.ext.accumulator.AccumulatorExtension` |
| xsl:evaluate | `com.helger.xslt.ext.evaluate.EvaluateExtension` |
| xsl:map, xsl:map-entry, xsl:array (if any) | `com.helger.xslt.ext.map.MapExtension` |
| xsl:source-document | `com.helger.xslt.ext.sourcedocument.SourceDocumentExtension` |
| xsl:assert | `com.helger.xslt.ext.assertion.AssertExtension` |
| xsl:fork | `com.helger.xslt.ext.fork.ForkExtension` |
| xsl:character-map, use-character-maps | `com.helger.xslt.ext.charactermap.CharacterMapExtension` |

```java
public final class NumberExtension implements IXsltExtension
{
  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("number", NumberInstruction::compile);
  }
}
```

The registry methods:

- `registerInstruction (local, IInstructionCompiler)`: an instruction in the XSLT namespace. Throws if the name is
  taken. `replaceInstruction` replaces a core compiler (only for an instruction the core implements in a basic form).
- `registerDeclaration (local, IDeclarationCompiler)`: a top-level declaration (child of xsl:stylesheet).
- `registerFunctions (Consumer <FunctionLibrary>)`: called for the function library of every stylesheet before any
  expression or pattern of it is parsed. Register with `FunctionLibrary.register (NS_FN, name, signature, impl)`
  (signature in F&O notation) or `registerParsed`.
- `registerOutputCustomizer (IOutputCustomizer)`: adjust the `SerializationParams` built from an output definition
  (character maps: read `OutputDefinition.getUseCharacterMaps ()`, call `setCharacterMap`).
- `registerCompileFinisher (ICompileFinisher)`: checks across declarations after all of them are compiled.
- `registerSystemPropertyYes (local)`: make `system-property('xsl:<local>')` return "yes" (e.g.
  `supports-dynamic-evaluation` for xsl:evaluate).

`element-available()` returns true for every registered instruction or declaration. The static shape of all XSLT
3.0 elements (allowed and required attributes, content model) is already in `XsltElementSpecs` and checked by
`ElementValidator` before your compiler runs; the select/content exclusion codes (XTSE3125 for xsl:break, XTSE3140
for xsl:try, ...) and the position rules of xsl:break/xsl:next-iteration (XTSE3120/XTSE3130) are checked there too.
Until a family is registered, its instruction is a static error XTSE0010 unless it has an xsl:fallback child.

## Compiling an instruction

```java
@FunctionalInterface
public interface IInstructionCompiler
{
  @Nullable IInstruction compile (@NonNull CompileContext aCtx, @NonNull XslElement aElement);
}
```

`XslElement` (the stylesheet element after static processing): `getAttr ("select")` (no-namespace attribute or
`null`), `hasAttr`, `getStandardAttr`/`getInheritedStandardAttr` (`version`, `expand-text`, ... honouring the `xsl:`
prefix on literal result elements), `getChildren ()` (elements and text; comments are gone, text merged),
`getElementChildren ()`, `getXslChildren ("sort")`, `isXsl ("when")`, `lookupNamespaceUri`, `getInScopeNamespaces`,
`getStaticBaseUri`, `resolveQName`, `isForwardsCompatible`, `getLine`/`getColumn`.

`CompileContext` is immutable; it carries the variables in scope:

| Method | Use |
|--------|-----|
| `compileExpression (el, text)`, `compileRequiredExpression (el, attr)` (XTSE0010 if missing), `compileOptionalExpression (el, attr)` | XPath on an element → `XPathExpr` |
| `compileAvt (el, text)`, `compileOptionalAvt (el, attr)` | attribute value templates → `Avt` (`isConstant`, `getConstantValue`, `evaluate (ctx)`) |
| `compileTextValueTemplate (el, text)` | text value templates (expand-text) |
| `compilePattern (el, text)` | pattern → `CompiledPattern` (only globals in scope); `getCompiler ().compileCheckedPattern (ctx, el, text)` adds XTSE1060/1070/3470/3500 |
| `compileSequenceType (el, text)` | `as` attributes → `SeqType` |
| `resolveQName (el, lexical, bDefaultNs)`, `resolveQNameAttr (el, attr, bDefaultNs)` | names (XTSE0020/XTSE0280) |
| `compileContent (el)` | the children as a sequence constructor, without xsl:param, xsl:sort, xsl:with-param, xsl:context-item |
| `compileSequenceConstructor (el, children)` | any list of children |
| `compileInstruction (el)` | one child instruction or literal result element |
| `compileVariable (el, VariableDef.EKind)` | xsl:variable / xsl:param / xsl:with-param → `VariableDef` (`evaluate (ctx)`, `convert`, `isRequired`, `isTunnel`) |
| `compileWithParams (el)`, `compileSortKeys (el)` | xsl:with-param / xsl:sort children |
| `withVariable (name)` | a context in which a variable is in scope (xsl:iterate parameters, the err:* variables of xsl:catch, ...) |
| `getStylesheet ()`, `getCompiler ()`, `getFunctionLibrary ()` | the stylesheet being built |
| `CompileContext.isExpandText (node)` | text value templates on? |

Static errors: `throw XsltException.at (aElement, "XTSE0010", "message")`. XPath errors from the compile methods are
located automatically.

An instruction is mostly a lambda over the compiled parts:

```java
public static IInstruction compile (final CompileContext aCtx, final XslElement aEl)
{
  final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
  final SequenceConstructor aBody = aCtx.compileContent (aEl);
  return new CompiledInstruction (aEl, (c, o) -> {
    for (final IXdmItem aItem : aSelect.evaluate (c))
      aBody.execute (c.withFocus (aItem, 1, 1), o);
  });
}
```

Compiled instructions must be immutable (a compiled stylesheet runs on several threads at once); keep all run-time
state in the context, the engine or your run data. Implement `IInstruction` directly (or extend
`AbstractInstruction`) when you need a class, e.g. a marker other instructions look for. An instruction that binds a
variable for its following siblings implements `IBindingInstruction`.

## Declarations

```java
@FunctionalInterface
public interface IDeclarationCompiler
{
  void compile (@NonNull CompileContext aCtx, @NonNull XslElement aElement);
}
```

Called in import precedence order (lowest first, then declaration order); `aElement.getPrecedence ()` is the import
precedence. Store results with `aCtx.getStylesheet ().putExtensionData (key, value)` (use a private constant object
or your class as key) and read them at run time with `getExtensionData`. Declarations that must exist before
expressions are compiled (none of the X2 families need it) are not supported by the SPI.

## Run time

`IInstruction.execute (XsltContext aCtx, IXsltOutput aOut)`. Throw `XPathException` (or `XsltException`) with the
W3C code; the enclosing `SequenceConstructor` attaches the location of the innermost instruction and counts each
instruction against the execution budget.

`XsltContext` (immutable):

| Method | Use |
|--------|-----|
| `getContextItem ()`, `getPosition ()`, `getSize ()` | focus |
| `withFocus (item, pos, size)` | new focus; the current item (fn:current()) follows |
| `withVariable (name, value)` | bind a variable |
| `getState ()`, `withState (state)`, `get (key)`, `with (key, value)` | XSLT state (below) |
| `withoutFocusAndVariables ()` | e.g. for a called component |
| `getEngine ()`, `getDynamicContext ()` | the transformation, the XPath context |
| `XsltContext.of (DynamicContext)` | in an XSLT function: the context of the calling expression (`null` outside a transformation) |

Evaluate expressions with `XPathExpr.evaluate (ctx)` (also `evaluateBoolean`, `evaluateString (ctx, sep)`) and AVTs
with `Avt.evaluate (ctx)`; patterns with `CompiledPattern.matches (item, ctx)`. Never evaluate an `IExpr` with
another static context: use the expression's own (`XPathExpr.createDynamicContext (ctx)`).

`XsltState` holds the current template rule and mode (xsl:apply-imports, xsl:next-match, mode="#current"), tunnel
parameters, the captured regex groups (`withRegexGroups`, read by regex-group()) and **extension slots**: declare
`public static final XsltStateKey <XdmSequence> CURRENT_GROUP = new XsltStateKey <> ("current-group",
XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);` and use `ctx.with (CURRENT_GROUP, group)` / `ctx.get (CURRENT_GROUP)`.
The scope says what happens in calls: `KEPT`, `CLEARED_IN_FUNCTIONS` (kept in templates, cleared in stylesheet
functions, global variables and patterns), `CLEARED_IN_CALLS`. `isSet (key)` distinguishes "absent" from `null`.
`Engine.CURRENT_OUTPUT_URI` is the slot of fn:current-output-uri(); xsl:result-document sets it.

`Engine` (one per transformation, single-threaded):

| Method | Use |
|--------|-----|
| `getStylesheet ()`, `getRequest ()` | |
| `applyTemplates (ctx, items, mode, params, out)`, `findRule (mode, item, ctx)`, `applyBuiltIn (...)` | template rules |
| `callTemplate (ctx, template, params, out)`, `applyNextMatch (...)` | |
| `applyAttributeSets (names, ctx, out)` | |
| `findKeyNodes (ctx, name, values, top)`, `getDocument (ctx, uri)`, `stripWhitespace (doc)` | |
| `addMessage (text)`, `addSecondaryResult (result)`, `getSecondaryResults ()` | messages, xsl:result-document |
| `getRunData (key, supplier)` | per-run state of your family (accumulator values, used output URIs, ...) |
| `enterDepth ()` / `leaveDepth ()`, `step ()` | depth (10,000) and step (20,000,000) budgets, both XPDY0130 |
| `createRootContext ()`, `getPatternContext ()`, `getGlobalContextItem ()` | |

`ParamValue.evaluate (withParamDefs, ctx)` evaluates xsl:with-param in the caller's context. `ControlInstructions
.getCurrentMode (ctx)` gives the current mode (or the default mode).

## Building results

A sequence constructor writes to an `IXsltOutput`:

- `append (item)`: an existing item. A `SequenceOutput` keeps it as it is; a `ContentOutput` copies a node.
- `appendNew (node)`: a node you just built, complete, without parent. Content outputs adopt it without copying,
  sequence outputs finish it (namespace fixup, document order).
- `appendText (s)`, `appendAll (seq)`.
- `getDocumentContentOutput ()`: when you construct a document node, write its content here if non-null (the
  document node would only be replaced by its children), else build a real one.

Outputs you create:

- `new SequenceOutput ()` → `getSequence ()`: the raw items (xsl:function results, `as` typed values).
- `new ContentOutput (XdmNode.createElement (name))`: builds element content by the rules for constructing complex
  content (atomic values → text with spaces between adjacent ones, merging, XTDE0410/0420/0430/0440/0450); then
  `out.appendNew (element)`. For a document node that becomes a tree on its own (a temporary tree), call
  `finishTree ()` afterwards.
- `new BufferedOutput ()`: records items with their "new" flag; inspect (`size`, `get`, `isNew`) and `replay`
  them later (xsl:try rollback, xsl:where-populated, xsl:on-empty).

Helpers: `XdmNode.createElement/createAttribute/createText/createComment/createProcessingInstruction/
createNamespace/createDocument`, `NodeCopier.copyDeep (node, bCopyNamespaces)`, `NodeCopier.copyShallow`,
`SimpleContent.join (seq, separator)` (value of xsl:attribute etc.), `NamespaceFixup.finish (root)`.

## Flow control

Use your own unchecked exception class (not an `XPathException`) for non-local exits such as xsl:break, so that the
location logic and xsl:try do not see it; catch it in your loop. Do not keep it in shared fields.

## XSLT functions

```java
aRegistry.registerFunctions (aLib -> aLib.register (XdmConstants.NS_FN, "current-group", "() as item()*", (aCtx, aArgs) -> {
  final XsltContext aX = XsltContext.of (aCtx);
  if (aX == null || !aX.getState ().isSet (CURRENT_GROUP) || aX.get (CURRENT_GROUP) == null)
    throw new XPathException ("XTDE1061", "current-group() is absent");
  return aX.get (CURRENT_GROUP);
}));
```

The arguments arrive converted to the declared types. Pass `true` as last argument of `register` for focus-dependent
functions. The core already provides: system-property, function-available, element-available, type-available,
available-system-properties, current, key, document, regex-group, current-output-uri, unparsed-entity-uri/-public-id, copy-of, snapshot.

## Tests

JUnit 4 in `src/test/java`, same package as your extension. `com.helger.xslt.XsltTestHelper` (test scope of
xslt-core) runs whole stylesheets:

```java
assertEquals ("3", XsltTestHelper.text ("<xsl:template name='xsl:initial-template'><xsl:value-of select='1+2'/></xsl:template>", null));
assertEquals ("XTDE1450", XsltTestHelper.errorCode (XsltTestHelper.SHEET + "..." + XsltTestHelper.END, "<doc/>"));
```

`text (body, source)` wraps the body in a stylesheet with `method="text"`; `transform (sheet, source, params)`
returns the serialized result, `run (...)` the full `XsltTransformResult`. Then run the XSLT30 harness for your test
sets: `mvn -o -q test -pl xslt-conformance -am -Dtest=XSLT30Test -Dxslt30.only=<set>` (fails in
`xslt-conformance/target/xslt30-fails.txt`; `-Dxslt30.faildump=true` prints the reasons). Compare fail lists by
name.

## Rules

1. Only add files in your `com.helger.xslt.ext.<family>` package (main and test). If you need a change in a core
   file, keep it additive and list it in your report.
2. Do not reimplement core helpers (outputs, copying, sorting, AVTs, variables, parameter binding).
3. Keep compiled structures immutable; per-run state goes into `Engine.getRunData`, per-context state into
   `XsltState` slots.
4. Recursion over input-controlled structure needs a bound (`Engine.enterDepth`) or an iterative algorithm.
