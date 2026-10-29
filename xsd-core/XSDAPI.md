# xsd-core: structure, ownership and contracts

`xsd-core` is the native W3C XML Schema validator, a port of go-xslt's `internal/xsd` (see `docs/PLAN.md`, section
"Native XSD validator"). This document is the brief for the agents of phases V3 to V6: it says which file belongs to
which phase, what each stub must do, and which parts of the component model a phase may rely on. Read
`docs/PORTING.md` (rules for parallel work, coding conventions) and go-xslt's `CLAUDE.md` (the `internal/xsd`
paragraph) first.

## Module

* Module `xsd-core`, one package `com.helger.xsd` (go-xslt keeps everything in one Go package; the port keeps the
  package-private access that implies).
* Depends on `xpath-xdm` (the `IXdmNode` tree, `XdmParser`) and `xpath-core` (`EAtomicType`, `XdmAtomic`,
  `AtomicCaster`, `AtomicComparator`, `XsdLexical`, `XPathCompiler`, `EXsdVersion`). Never on Xerces-J, never on
  JAXP validation.
* The version switch is `com.helger.xpath.context.EXsdVersion` (`V10`, `V11`), threaded as `XsdCompiler.m_eVersion`
  / `XsdSchema.m_eVersion` exactly where go-xslt branches on `Version`.

## Public API (V2, stable)

| Class | Purpose |
|-------|---------|
| `XsdSchema` | Compiled schema. `compile (List<XsdSchemaSource>, EXsdVersion, XsdCompileOptions)`, `compileFiles (List<Path>, ...)`, `validate (Path)` / `validate (Path, XsdValidationOptions)`, `validateText (String)` / `validateText (String, XsdValidationOptions)`, `getView ()`. Immutable after compile, shared between threads. |
| `XsdSchemaSource` | A schema document: `ofFile (Path)` (base directory = its folder) or `ofText (String, Path baseDir)`. |
| `XsdCompileOptions` | `setKeepDocumentation (boolean)` (go-xslt `CompileWithDocs`), `setFileAccess (XsdFileAccess)`. |
| `XsdFileAccess` | File reads of the validator: `LOCAL_FILES` (default, any local file) or `confinedTo (Path...)`. Never the network: a location with another URI scheme than `file:` is unavailable like a missing file. `resolveLocation (baseDir, location)`, `readFile (Path)`. |
| `XsdValidationOptions` | `setLanguage (String)` (preferred `xml:lang` of the `msg:message` wording), `setMaxErrors (int)` (0 = unlimited); go-xslt `ValidateRequest.Language` / `MaxErrors`. Neither changes the verdict. |
| `XsdValidationResult` | `valid ()`, `errors ()` (go-xslt `Result`). `errors ()` lists ALL errors found, in document order (V7). |
| `XsdError` | One error: line, column, code, message, rule, path, kind, particle (go-xslt `Error`). |
| `XsdInvalidException` | A genuine invalid verdict, carries an `XsdError`. |
| `XsdUnsupportedException` | No verdict (go-xslt `ErrUnsupported`). |
| `XsdSchemaView` and `*View`, `IXsdTypeDefinitionView`, `IXsdTermView`, `XsdFacetsView`, enums `EXsdVariety`, `EXsdContentKind`, `EXsdCompositor`, `EXsdUse` | The read-only component model (go-xslt `model.go`). |

## Error discipline (all phases)

* An invalid schema or instance: `throw XsdInvalidException.invalid (code, message)` (go-xslt `invalidf`). Code is
  the constraint name where known (`src-resolve`, `cvc-complex-type.2.4`, ...), else empty.
* Missing functionality: `throw new XsdUnsupportedException (...)` (go-xslt `return ErrUnsupported`). A stub throws
  `UnsupportedOperationException ("Not implemented yet")`.
* Only `XsdSchema`'s public entry points (`_noVerdictOnMissingFunctionality`) turn an escaping
  `UnsupportedOperationException` into `XsdUnsupportedException`, naming the class and method it came from. Never
  catch `UnsupportedOperationException` anywhere else, and never turn it into a verdict. Where go-xslt tests a
  function result with `err != nil` and continues, catch `XsdInvalidException` only.
* Error identity: the first error decides (XSLT and XPath need only that). The in-place validation of the bridge
  (`XsdBridge`, `XsdInstanceValidator.validateRoot` without a collector) stops at the first error and its error
  identity is unchanged. `XsdSchema.validate` / `validateText` collect all errors (phase V7, below).

## Compile pipeline (V2, read-only for V3 to V6)

`XsdCompiler.compile` follows go-xslt `compile` step by step:

1. Parse the entry document (`XdmParser`, JDK SAX, no external entities), `XsdDocumentCollector.collect` the
   include/import/redefine graph (conditional inclusion `vc:*` first), then every further given document.
2. `XsdSchemaForSchemas.validateAttrValues` and `validateSchemaAttrs` on every collected document.
3. Pass 1: register the named top-level components.
4. Simple types in dependency order (`XsdSimpleTypeParser.parseSimpleTypeInto`), which calls
   `XsdPattern.validateSyntax` / `compile` and `XsdFacetValidator.validateFacetValues` (**V3 stubs**) and
   `XsdSimpleTypeValidator.validate` for enumerations of list restrictions (**V3 stub**).
5. Global attributes, groups (+ cycle check), attribute groups in dependency order, group/attributeGroup
   redefinitions (`XsdRestrictionChecker.particleRestrictsOK`, **V4 stub**), complex types in base-first order,
   global elements, type redefinitions. Every default/fixed value goes through `XsdCompiler.checkValueConstraint` →
   `XsdSimpleTypeValidator.validate` (**V3 stub**).
6. Substitution groups, wildcard `##definedSibling`, type cycles, ID attribute uses, identity-constraint names,
   `XsdComponentChecks.checkAllRestrictions` (calls `particleRestrictsOK` and `XsdRestrictionChecker.nsSubset`,
   **V4 stubs**), keyrefs, substitution-group derivation, final, attribute derivation, then
   `XsdUpa.checkUPA (schema, effectiveParticle (ct))` for every complex type, named and anonymous (**V4 stub**).

Bounds: component nesting and dependency walks are limited by `XsdCompiler.MAX_NESTING` (1,000;
`enterNesting`/`leaveNesting`, beyond it `XsdUnsupportedException`); base chains by 64 steps like go-xslt; every
walk over a schema document or a content model is iterative. New code must keep that: no unbounded recursion over
input-controlled structure (go-xslt relies on its growable stack, Java does not have one).

## File ownership

| Phase | Owns (only these files may be edited) | Go reference |
|-------|----------------------------------------|--------------|
| V2 (done) | everything not listed below; read-only for later phases | `loader.go`, `structure.go`, `components.go`, `imports.go`, `model.go`, `docs.go`, `schemavalid.go`, and the helpers listed under "Already ported" |
| V3 | `XsdSimpleTypeValidator`, `XsdFacetValidator`, `XsdLexScan`, `XsdPattern`, new classes of its own | `simpletype.go`, `facets.go`, `lexscan.go` |
| V4 | `XsdContentMatcher`, `XsdBigMatch`, `XsdUpa`, `XsdRestrictionChecker`, `XsdMatchResult`, new classes of its own | `content.go`, `bigmatch.go`, `upa.go`, `restriction.go` |
| V5 | `XsdInstanceValidator`, `XsdAttributeValidator`, `XsdIdentityValidator`, `XsdValidationRun`, new classes of its own | `instance.go`, `attributes.go`, `identity.go` (run-time half) |
| V6 | `XsdBridge`, new classes of its own | `bridge.go` |
| V7 (done) | `XsdErrorCollector`, `XsdExplain`, `XsdValidationOptions`, `XsdMessages` (selection); the collector hooks in `XsdInstanceValidator`, `XsdAttributeValidator`, `XsdValidationRun`, `XsdInvalidException`, `XsdSchema` | `collect.go`, `explain.go`, `messages.go` (selection) |
| V8 | the XSD 1.1 stubs (`XsdCompiler.unsupported11` call sites), `XsdAssertion`, `XsdTypeAlternative`, `XsdOpenContent` | `xsd11.go`, `override.go`, 1.1 branches |

A change to a file a phase does not own must be strictly additive (a new method, a new field) and be listed in the
phase's report (PORTING.md rule 2). The stubs' signatures may be changed by their owner as long as every V2 call site
keeps compiling; adjust the call site only if unavoidable and report it.

## Already ported (do not port again)

These go-xslt functions live in V2 classes; use them, do not duplicate them:

| go-xslt | Java |
|---------|------|
| `SimpleType.resolvePrim`, `effectiveWhiteSpace` | `XsdSimpleType.resolvePrim ()`, `effectiveWhiteSpace ()` |
| `primitiveWhiteSpace`, `wsRank`, `strongerWS`, `applyWhiteSpace`, `collapseWhiteSpace` | `XsdWhiteSpace` |
| `validQNameLexical`, `permissiveNCName`, `isNCName`, `validAnyURI10`, `xmlFields`, `lexEqualCollapsed`, `nonNegInt`, `atoiOr` | `XsdLexHelper` |
| `reLanguage`, `reNCName` | `XsdLexical.isLanguage`, `XsdLexical.isNCName` (xpath-core) |
| `isIntegerLike` | `EAtomicType.isIntegerFamily ()` |
| `xpath.AtomDerivesFrom` (XSD semantics: xs:error derives from nothing) | `XsdDerivation.atomDerivesFrom` |
| `xpath.CastTo` + `xpath.CompareAtomic` for facet values | `XsdValueCompare.castOrNull`, `compare`, `compareLexical` (implicit timezone UTC; durations folded like go-xslt) |
| `effectiveParticle`, `allGroupOf`, `effectiveAttrs`, `mergeAttrs`, `effectiveOpenContent`, `matchesElementName`, `substitutionAllowed`, `contentDecls` | `XsdSchema` (iterative) |
| `wildcardMatches`, `wildcardAdmits`, `admitsNamespace`, `wcProcessRank` | `XsdWildcard.matches`, `admits`, `admitsNamespace`, `processRank` |
| `parseBlockSet`, `derivationMethodsTo`, `typeIdentityEqual`, `derivesFrom`, `derivesFromMode`, `simpleDerivesFrom`, `simpleDerivesFromMode`, `isNilType` (= `null`) | `XsdDerivation` |
| `typeRestrictsFrom`, `typeRestrictsFromStrict`, `particleEmptiable`, `isEmptyParticle`, `isAllParticle`, `checkElementConsistency`, `xsdPrimitiveOf`, `typeSignature` | `XsdComponentChecks` |
| `simpleContentType`, `intersectWildcards` | `XsdComponentParser` |
| `checkFacetConsistency`, `checkNotationUsable`, `checkSimpleFinal`, `linkBase`, `parseFacets` | `XsdSimpleTypeParser` |
| `parseIdentityConstraints`, `checkKeyrefs`, `validateIdentityXPath`, `resolveXPathDefaultNS` | `XsdIdentityCompiler` |
| `assertMessages` | `XsdMessages.messagesOf` |
| `checkAllUPA` (the loop) | `XsdCompiler.compile` |
| all of `schemavalid.go` | `XsdSchemaForSchemas` |
| `xmltree` accessors (`AttrLocal`, `LookupPrefix`, `firstXSChild`, `isXS`, `resolveQName`, `declName`, `RootElement`) | `XsdNodes` |

## Component model (V2)

The components are package-private classes with package-private, Hungarian-named fields, written by the compiler and
treated as immutable afterwards (go-xslt's structs). Identity matters: components do not override `equals`, so sets
and maps of components are identity-based, like go-xslt's pointer maps. Maps of the schema are insertion-ordered
(`CommonsLinkedHashMap`), so iteration is deterministic (go-xslt iterates Go maps in random order; only the message of
the first error can differ).

| Class | go-xslt | Notes |
|-------|---------|-------|
| `XsdName` (record `space`, `local`) | `xname` | `NONE` is the zero name of anonymous components; `toString` is Clark notation. |
| `IXsdType` = `XsdSimpleType` \| `XsdComplexType` | `Type` | sealed |
| `XsdSimpleType` | `SimpleType` | `m_aName`, `m_eVariety`, `m_ePrim` (`null` = unset), `m_aBase` (`null` when the base is a built-in), `m_bBaseBuiltin`, `m_aFacets`, `m_aItem`, `m_aMembers`, `m_bDeclared`, `m_aFinal`, `m_aAssertions`. Sentinels `ABSENT` (missing type, XSD 1.0: refuses every value at validation time) and `ERROR` (xs:error). `builtin (EAtomicType)`, `anySimpleType ()`, `isURType ()`. |
| `XsdFacetSet` | `facetSet` | `m_aPatterns` is `ICommonsList<ICommonsList<XsdPattern>>`: one group per restriction step (only this step's), AND across steps, OR within a group; `m_aPatternSrc` only with documentation kept. `m_aEnumQName` holds the Clark-expanded enumeration values for QName/NOTATION types. String facet values are raw (not normalised), `m_sWhiteSpace`/`m_sExplicitTZ` empty when unset. |
| `XsdComplexType` | `ComplexType` | this derivation step only; use `XsdSchema.effectiveParticle/effectiveAttrs/effectiveOpenContent`. `m_aBaseType == null` means xs:anyType. `m_sDerivation` is `extension`, `restriction` or empty. `anyType ()` creates xs:anyType. |
| `XsdParticle` | `particle` | `m_nMin`, `m_nMax` (`XsdConstants.UNBOUNDED` = -1; huge values are clamped to `Integer.MAX_VALUE` - use `long` arithmetic when multiplying occurrences), `m_aTerm`, `m_aDocs`, `m_aDet` (atomic memo for V4's determinism check). |
| `IXsdTerm` = `XsdElementDecl` \| `XsdModelGroup` \| `XsdWildcard` | `term` | sealed |
| `XsdModelGroup` | `modelGroup` | `m_eCompositor`, `m_aParticles`, `m_aMsgs`. A group reference shares the definition's `XsdModelGroup` object. |
| `XsdWildcard` | `wildcard` | `m_sNsMode` (`any`/`other`/`list`), `m_aNamespaces`, `m_sTargetNS`, `m_sProcess` (raw value; `strict`/`lax`/`skip`), XSD 1.1 negations, `m_aParts` for unions. |
| `XsdElementDecl` | `ElementDecl` | `m_aType` is never `null` after compile (xs:anyType by default; `m_bTypeDefaulted`). A global declaration is the one in `XsdSchema.m_aElements`; an `xs:element ref` particle points to that same object. |
| `XsdAttributeDecl`, `XsdAttributeUse`, `XsdAttributeGroup` | `AttributeDecl`, `attrUse`, `attrGroupDef` | a use inherits the declaration's value constraint when it has none of its own (done at compile time). |
| `XsdIdentityConstraint` | `identityConstraint` | selector and fields are `CompiledXPath`s, compiled with the in-scope namespaces of their schema elements and the xpathDefaultNamespace as default element namespace (names are resolved at compile time in xpath-core, unlike go-xslt). Raw sources and namespaces are kept as well. |
| `XsdSchema` | `Schema` | `m_aElements`, `m_aTypes`, `m_aAttributes` (with the four implicit `xsi:` attributes), `m_aSubstMembers` (head to direct members; `substMembersOf`), `m_aSourceDocs` (for hint merges), `m_aBaseDir`, `m_aFileAccess`, `m_eVersion`. No per-validation state. |

## Contracts of the stubs

### V3 (simple types and facets)

* `XsdPattern.validateSyntax (pattern, version)`: `XsdInvalidException` if the facet value is not an XSD regular
  expression of that version (go-xslt `validateXSDRegex`; the xpath-core regex translator implements the grammar).
* `XsdPattern.compile (pattern, version)`: anchored compiled pattern; a pattern the engine cannot run throws
  `XsdUnsupportedException`, never a verdict. `matches (value)` on the whitespace-normalised value.
* `XsdFacetValidator.validateFacetValues (type, version)`: go-xslt `validateFacetValues`, called at the end of every
  simple type restriction (also for `xs:simpleType` without facets) after `checkFacetConsistency`.
* `XsdSimpleTypeValidator.validate / validateIn / check`: value against type, `XsdInvalidException` if invalid;
  `ABSENT` refuses every value. Called by the loader for every default/fixed value and for enumerations of list
  restrictions. `fixedEqual`, `firstMemberRaw`, `checkPrimitiveLexical` and `XsdFacetValidator.checkIn /
  checkPatternEnum / checkListLength` are for V5/V6.
* Casting and ordering of facet values: reuse `XsdValueCompare` (or extend it additively).
* A compiled simple type never reaches itself (go-xslt 42f82b3, issue 12). `XsdSimpleTypeParser` keeps the types
  being parsed (`m_aOpen`, with their derivation kind): a restriction base or list item type that is still open, or
  a union member that is open through a restriction/list, is `circular simpleType derivation` in both versions, and
  an open union member is one in XSD 1.1 (cos-no-circular-unions). `_closeUnion`, run when a union's member list is
  final (`_parseUnion`, and a restriction copying its base union's members), detects a cycle back to the union and,
  in XSD 1.0 (`<xs:union memberTypes="u xs:string"/>` inside `u` itself, stE110), collapses it to the members that
  do not lead back, in member order (the least fixed point of the value space); in 1.1 it is rejected. Both walks
  are iterative. Walks over members, base and item types therefore need no visited set; the bounds and visited sets
  in `XsdSimpleTypeValidator` and `_checkNotationUsable` stay as defence in depth.

### V4 (content models)

* `XsdUpa.checkUPA (schema, effectiveParticle)`: `XsdInvalidException` (`cos-nonambig`) on a UPA violation; `null`
  particle is fine. `upaNullable` for the others.
* `XsdRestrictionChecker.particleRestrictsOK (derived, base)`: `true` if valid (erring towards valid where
  undecidable, like go-xslt). `nsSubset (a, b)`: wildcard namespace subset. The constructor flag `bInMapAndSum` is
  go-xslt's `inMapAndSum`.
* `XsdContentMatcher.contentMatches / contentMatchesOpen / greedyOpenAttribution` and `XsdBigMatch.countMatch /
  particleDeterministic` for V5. `XsdMatchResult (matched, decided)` is go-xslt's `(matched, ok)`.

### V5 (instance validation)

* `XsdInstanceValidator.validate (schema, Path)` and `validateText (schema, String)`: parse with `XdmParser`
  (not well-formed = invalid), assess from the document element. Per-call state belongs in `XsdValidationRun`
  (go-xslt's per-call `Schema` copy: ids, typeCache, childModels, unparsed, unassessed, nodeTypes, ...), never in
  `XsdSchema`.
* `xsi:schemaLocation` hints: read through `schema.m_aFileAccess`, resolved with `XsdFileAccess.resolveLocation`
  against the schema's base directory (go-xslt `hintDocs` uses `Schema.baseDir`; for the empty schema set it is
  `null` in Java, so resolve against the instance's own directory then). Re-compile with `schema.m_aSourceDocs`
  plus the hinted documents (`XsdSchemaSource.ofBytes`).
* `XsdAttributeValidator.validateAttributes`, `XsdIdentityValidator.checkConstraint`: signatures are V5's to shape.

### V6 (bridge, done)

* `XsdSchemaProvider` (public) is the `IXdmSchemaProvider` of `xpath-xdm`: `compile (List<XdmSchemaSource>)` compiles
  the documents as XSD 1.0 with local file access and returns an `XsdBridge`; `createSchemaSet (XsdSchema)` wraps a
  schema compiled otherwise. `XdmSchemaSupport` loads it by name.
* `XsdBridge` is the `IXdmSchemaSet` (go-xslt `bridge.go`). Lookup by name goes over the schema's types and the
  built-ins with the rules of `xsi:type` (XDM-only names, `xs:numeric` and, under 1.0, the 1.1 names are no schema
  types); `XsdTypeDefinition` (`IXdmTypeDefinition`) carries the name that a built-in reference lacks in the compiled
  model (go-xslt `builtinRefName`, the ur-type sentinel is xs:anySimpleType) and answers derivation with
  `XsdDerivation.derivesFromMode` without union substitution (the consumer adds the pure-union rule). An anonymous type
  gets a name `#tN` from a per-schema-set registry (`anonReg`), stable and resolvable by `isDerivedFrom (XdmQName)`.
  `XsdElementDeclaration` and `XsdAttributeDeclaration` are the global declarations; `getSubstitutionMember` honours
  abstract and block (go-xslt `SubstitutesFor`). `XsdTypeDefinition.validate` is the casting seam: the typed tokens of a
  value (a union's accepting member, a list's items) with the nearest built-in type.
* `validateElement` and `validateDocument` validate in place (go-xslt `ValidateNode`): the subtree is cloned into a
  detached tree with merged text nodes (iteratively), validated with `XsdInstanceValidator.validateRoot` (big-stack
  thread for deep trees) and a run that records `enableNodeTypes/Nilled/Defaults`; only after success the types,
  `[nilled]`, schema ID kinds, supplied default attributes (with namespace fixup) are written to the caller's nodes
  and whitespace text in element-only content is removed. Not assessed elements are annotated xs:anyType. A failure
  leaves the tree untouched. `bDocumentLevel` selects `Document` (IDREF resolution, ID uniqueness) against
  `elementScope`; `xsi:schemaLocation` hints are never followed; an xs:ENTITY value need not name an unparsed entity.
  Default element content is not written to the tree (go-xslt does not either).
* Additions to V5's `XsdInstanceValidator`: `validateRoot` with the assessment of the root given, `validateAsType`, and
  lax assessment of an undeclared element now validates its attributes against global attribute declarations and takes
  an explicit `xsi:type` (XSD 1.0 section 3.4.4; go-xslt leaves both unassessed; no xsdtests case changes).

### V7 (error API, done)

* `XsdSchema.validate` / `validateText` collect every error they can find (go-xslt `Schema.ValidateOpts`). The collector
  is `XsdErrorCollector` in the `XsdValidationRun`; the in-place path (bridge) has none and stops at the first error. The
  verdict is identical either way (`XsdBridgeDifferentialTest` compares both over the whole XSD 1.0 suite and also
  checks that the first error of the in-place run is among the collected ones, by code).
* Recording points are `XsdInstanceValidator.soft (run, XsdInvalidException, element)`, which records the error (stamped
  with the location of the innermost element, like `stampLoc`) when the run collects and rethrows it otherwise:
  the attribute check of an element, each child of a content model, each missing required attribute, and the top level.
  Anything else (an identity-constraint error, a simple-type error) ends the check of the element it occurs in and is
  recorded by the nearest enclosing `soft` call, as in go-xslt. `XsdUnsupportedException` is never recorded: before the
  first error it is the result, after it the list just ends early.
* `XsdErrorCollector.Stopped` (go-xslt `errStopped`) unwinds the walk once `MaxErrors` is reached; `ModelMismatch`
  (`errModelMismatch`) is thrown by the content-model check after it recorded its errors, so that `XsdInstanceValidator`
  can still assess the children whose declaration is known by name (`assessDeclaredChildren`) and, when all of them are,
  the assertions (`afterModelError`). Neither is an `XsdInvalidException`, so no verdict handler sees them. The skip-
  wildcard excuse withdraws what a child recorded (`XsdValidationRun.collected` / `uncollect`).
* Errors are sorted by line and column (stable) when the result is built; the wording is chosen by language then
  (`XsdMessages.pickMessage`: `xml:lang` exact, then primary subtag, else an entry without `xml:lang`, else the first).
* Content-model explanations (`XsdExplain`, go-xslt `explain.go`): when the matcher refused the children and the run
  collects, a position automaton replays them and names each defect (`kind` = `missing`, `too-many`, `unexpected`,
  `order`, `particle` = Clark name of the element declaration concerned, code `cvc-complex-type.2.4`). `xs:all`, open
  content, occurrence bounds above 120 to unroll and more than 5,000 children are not explained: the generic message
  stays (`kind` empty). It never decides validity. Walks are iterative; group nesting is followed to 100 levels.
* `msg:message` (go-xslt `messages.go`) in an `xs:appinfo` of an `xs:element`, `xs:attribute`, model group (and, from
  V8 on, `xs:assert` / `xs:assertion`) gives the diagnostic its own wording and `rule`:

      <xs:element name="a" xmlns:msg="urn:fdc:helger.com:ph-xslt:xsd:messages">
        <xs:annotation><xs:appinfo>
          <msg:message on="missing" rule="R1" xml:lang="en">a is required</msg:message>
        </xs:appinfo></xs:annotation>
      </xs:element>

  The namespace is `XsdConstants.NS_MESSAGES` = `urn:fdc:helger.com:ph-xslt:xsd:messages`; go-xslt's
  `https://github.com/tim-riep/go-xslt/xsd/messages` is NOT recognised (a schema written for go-xslt needs the
  namespace changed). `on` narrows a message to `missing` or `too-many` (absent: both). For a cardinality error the
  element declaration's own messages win, else those of the innermost group that holds all alternatives; for a missing
  required attribute the `xs:attribute`'s (the use's, else the referenced declaration's) with `on="missing"` or no `on`.
  The convention affects `message` and `rule` only, never validity.
* `XsdValidationRun.MAX_DEPTH` (instance nesting followed on the calling thread before the big-stack thread takes over)
  is lowered from 250 to 50: a level costs six frames, 2.3 KB interpreted and over 5 KB while the JIT only has profiled
  code, so even 100 levels overflowed a 512 KB stack in a full test run (the collector hooks add one frame per level).
  Deeper documents cost a thread start, nothing else.
* Not part of V7: the assertion half of `messages.go` (`assertionFailed`, `failAssert`) belongs to V8 with `xs:assert`;
  `afterModelError` already calls the assertion check, which is the V8 stub.

## XSD 1.1 (V8)

1.1 branches inside ported functions are kept; the trivial ones are ported, the features throw
`XsdCompiler.unsupported11 (what)`: `xs:assert` / `xs:assertion` (`XsdComponentParser.parseAssert`),
`xs:alternative` (`parseAlternatives`), `xs:openContent` / `xs:defaultOpenContent` (`_applyOpenContent`),
`xs:override` (`XsdDocumentCollector`). The harness reports every 1.1 case as unsupported until then.

## Differences to go-xslt (deliberate)

* Schema documents are parsed by the JDK (any encoding, so UTF-16 documents are read where go-xslt skips them).
* Each given document resolves its references against its own directory (go-xslt: the first document's).
* An absolute `schemaLocation` path is absolute (go-xslt joins it to the base directory).
* Occurrence values above `Integer.MAX_VALUE` are clamped to it (go-xslt: 64-bit).
* Identity-constraint XPaths are compiled at schema compile time; a compile failure is unsupported.
* Facet-value ordering of date/time values without timezone uses the implicit timezone UTC.
* The `msg:message` namespace is `urn:fdc:helger.com:ph-xslt:xsd:messages` (go-xslt: its GitHub URL).
* Collecting is the default of `validate` / `validateText` (go-xslt's `Validate` does the same); there is no
  context-cancellation variant (`ValidateOptsCtx`).

## Testing

* Unit tests: `mvn -o -q test -pl xsd-core -am`. The spec tests ported from go-xslt (`XsdSchemaRepresentationTest`,
  `XsdSchemaCompositionTest`, `XsdSchemaModelTest`) tolerate "unsupported" and count it; a decided verdict must be
  the expected one. As the stubs disappear, more cases are decided; model tests skip (assumption) while their schema
  does not compile yet.
* Suite: `mvn -o -q test -pl xsd-conformance -am -Dtest=XsdTest -Dsurefire.failIfNoSpecifiedTests=false
  -Dxsd.validator=native` (fail list `xsd-conformance/target/xsd-native-fails.txt`; `-Dxsd.unsup=true` prints each
  unsupported case with the stub it hit; `-Dxsd.faildump=true` the fails). The summary splits schema and instance
  tests. Accepted failures: `xsd-conformance/docs/accepted-failures/xsd-native.md`, checked by default
  (`XsdNativeTest` always runs the native validator and gates on it). `XsdBridgeDifferentialTest` compares validation
  in place with validation of the text.
* Differential check used for V2: replace the stubs locally (never committed) by permissive bodies (accept every
  pattern, facet, value, UPA and restriction) and do the same in a scratch copy of go-xslt; then the schema-test fail
  lists of both must be identical by name. For V2 they were (1,307 schema fails each, 0 difference). The same
  technique works for V3/V4 with only the other phases' stubs permissive.
