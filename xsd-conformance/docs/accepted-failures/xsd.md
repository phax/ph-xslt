# XSD (`xsdtests`): failing cases

Baseline with Apache Xerces-J 2.12.2 (secure processing on, otherwise the factory defaults), XSD 1.0: 39,145 of
39,401 applicable cases pass, 256 fail (253 distinct names; three `missing001.*` names occur in two groups). Nothing
is reported as unsupported in the XSD 1.0 run. Measured offline, on JDK 21 and 25; the denominator equals go-xslt's.

Xerces-J 2.12.2 implements XSD 1.0 only. The XSD 1.1 run (`-Dxsd.version=1.1`) exists, but reports every applicable
case as unsupported, outside the pass/fail denominator, so it has no failing cases.

Every failing case is named here in backticks under a heading with its status and reason. `XsdTest` fails on any
failing case that is not named here (switch that off with `-Dxsd.checkAccepted=false`), so a regression is loud and a
newly accepted failure needs a written reason. Remove an entry when its case passes. Status **Accepted (Xerces-J
baseline)** means a verified deviation of Xerces-J; these entries are expected to change when the native validator
replaces Xerces-J in this harness. The reasons were checked against the first error Xerces-J reports
(`-Dxsd.faildump=true`), the test files and the suite's own annotations.

go-xslt's five documented XSD 1.0 fails: Xerces-J also fails `particlesZ001.i` (see below), and gives the suite's
verdict for particlesZ023, particlesZ024, groupH021v and idB005 (names without backticks here, so that
this file does not accept them).

## Harness and environment

### Schemas from www.w3.org (offline)
`introspect-testSet-schema`, `introspect-testSet-AGroupDef-4`, `introspect-testSet-All-34`,
`introspect-testSet-allGroup-50`, `introspect-testSet-anyAttribute-51`, `introspect-testSet-Assert-35`,
`introspect-testSet-Assert-52`, `introspect-testSet-assertion-53`, `introspect-testSet-AttrDecl-5`,
`introspect-testSet-AttrUse-6`, `introspect-testSet-BoeingXSDTestCases-33`, `introspect-testSet-Complex-36`,
`introspect-testSet-ConditionalInclusion-54`, `introspect-testSet-constraintsOnAttribute-55`,
`introspect-testSet-CTA-37`, `introspect-testSet-CTA-82`, `introspect-testSet-CType-7`,
`introspect-testSet-CyclicDependencies_Redefine_Include_Import_Override-56`, `introspect-testSet-date-57`,
`introspect-testSet-dateTimeStamp-58`, `introspect-testSet-dayTimeDuration-59`,
`introspect-testSet-defaultAttributesApply-60`, `introspect-testSet-DefaultFixed-61`, `introspect-testSet-double-62`,
`introspect-testSet-EDCWildcard-63`, `introspect-testSet-ElemDecl-8`, `introspect-testSet-explicitTimezone-64`,
`introspect-testSet-float-65`, `introspect-testSet-gDay-69`, `introspect-testSet-gMonth-70`,
`introspect-testSet-gMonthDay-68`, `introspect-testSet-gYear-67`, `introspect-testSet-gYearMonth-66`,
`introspect-testSet-ID_IDREF-71`, `introspect-testSet-Id-38`, `introspect-testSet-IdConstrDefs-9`,
`introspect-testSet-IdentityConstraint-72`, `introspect-testSet-introspection-1`, `introspect-testSet-list-73`,
`introspect-testSet-MGroup-10`, `introspect-testSet-MGroupDef-11`, `introspect-testSet-MS-Additional2006-07-15-16`,
`introspect-testSet-MS-Annotations2006-07-15-17`, `introspect-testSet-MS-Attribute2006-07-15-19`,
`introspect-testSet-MS-AttributeGroup2006-07-15-18`, `introspect-testSet-MS-ComplexType2006-07-15-20`,
`introspect-testSet-MS-DataTypes2006-07-15-21`, `introspect-testSet-MS-Element2006-07-15-22`,
`introspect-testSet-MS-Errata102006-07-15-23`, `introspect-testSet-MS-Group2006-07-15-24`,
`introspect-testSet-MS-IdentityConstraint2006-07-15-25`, `introspect-testSet-MS-ModelGroups2006-07-15-26`,
`introspect-testSet-MS-Notations2006-07-15-27`, `introspect-testSet-MS-Particles2006-07-15-28`,
`introspect-testSet-MS-Regex2006-07-15-29`, `introspect-testSet-MS-Schema2006-07-15-30`,
`introspect-testSet-MS-SimpleType2006-07-15-31`, `introspect-testSet-MS-Wildcards2006-07-15-32`,
`introspect-testSet-NIST2004-01-14-2`, `introspect-testSet-Notation-12`, `introspect-testSet-Open-39`,
`introspect-testSet-Override-40`, `introspect-testSet-PopenContent-74`, `introspect-testSet-RegularExpression-75`,
`introspect-testSet-RestrictionOfComplexTypes-76`, `introspect-testSet-rf-whiteSpace-77`,
`introspect-testSet-Schema-14`, `introspect-testSet-Simple-41`, `introspect-testSet-SType-13`,
`introspect-testSet-Subsgroup-42`, `introspect-testSet-substitution-groups-49`,
`introspect-testSet-substitutionGroup-78`, `introspect-testSet-suntest-3`, `introspect-testSet-TargetNS-43`,
`introspect-testSet-TargetNS-79`, `introspect-testSet-time-80`, `introspect-testSet-TypeAlternativeTests-81`,
`introspect-testSet-union-83`, `introspect-testSet-UnitsLength-84`, `introspect-testSet-unsignedIntegers-85`,
`introspect-testSet-VC-44`, `introspect-testSet-VC-86`, `introspect-testSet-Wild-45`, `introspect-testSet-Wildcard-15`,
`introspect-testSet-wildcard-87`, `introspect-testSet-XML11Support-88`, `introspect-testSet-XmlVersions-46`,
`introspect-testSet-XPathDefaultNSonKeyKeyRefUnique-89`, `introspect-testSet-XSImportReference-90`,
`introspect-testSet-yearMonthDuration-91`, `introspect-testSet-Zone-47`, `introspect-testSet-Zone-48`

**Status:** Accepted (offline only).
The introspection set validates the suite's own catalogue files against `common/xsts.xsd`, which imports the XLink
and XML namespace schemas from `http://www.w3.org/XML/2008/06/xlink.xsd` and `http://www.w3.org/2001/xml.xsd`. The
harness never touches the network, so both imports are unreadable (only a warning for Xerces-J) and the schema fails
with `src-resolve` on `xlink:type`; the instance tests then fail because their schema was rejected. go-xslt passes
them with built-in declarations for the XML and XLink namespaces, which Xerces-J does not have (see the next entry).

### No built-in XML namespace attributes
`addC001`, `addC001.v`

**Status:** Accepted (Xerces-J baseline).
The schema imports the XML namespace without a `schemaLocation` and references `xml:base`. Xerces-J has no built-in
declarations for the XML namespace and reports `src-resolve` for `xml:base`. go-xslt supplies the XML namespace
attributes built in.

### Instance hints ignored by a fully composed schema
`schA1.v`, `attgD034.v`, `ctL021.v`, `targetNS00101m1_p`, `ST_targetNS00101m2_p`, `particlesB013.v`

**Status:** Accepted (harness: JAXP API).
Each instance names, with `xsi:schemaLocation`/`xsi:noNamespaceSchemaLocation`, a schema document for a namespace the
group's schema test does not contain. The harness compiles the schema test with Xerces-J's JAXP
`SchemaFactory.newSchema (Source[])`, which yields a fully composed schema whose validators ignore instance hints.
Verified for each case by compiling the hinted documents together with the schema test: all six instances are then
valid. Driving Xerces-J through its XNI API with an open grammar pool might honour the hints; not tried.

### Second import of an already known namespace
`schG3.v`, `schG12.v`

**Status:** Accepted (Xerces-J baseline).
A imports B, B imports C, and C has the namespace of A (group schG3) resp. of B (group schG12), so C's declarations must be
added to a namespace that is already known. By default Xerces-J does not load C (strict wildcard without
declaration). Both pass with Xerces-J's feature `honour-all-schemaLocations`, which makes schZ012_b and schZ012_c
fail instead, so the harness keeps the default. The schema tests of both groups are queried in the suite and not
counted; the instance tests are.

### Hints of a schemaless instance
`adda007.v`, `addA008.v`

**Status:** Accepted (Xerces-J baseline).
These groups have no schema test. The instance names only namespace `ns-b` in `xsi:schemaLocation`; the declaration
of its document element `a:root` (namespace `ns-a`) comes from the schema that `adhocAddB003_b.xsd` imports.
Xerces-J does not find a declaration for the document element (`cvc-elt.1.a`).

### Content-model size limit of secure processing
`elemZ020.v`, `particlesQ005.v`, `particlesZ005.v`, `particlesZ036_b1.i`, `particlesZ036_b2.i`, `particlesZ036_c.v`

**Status:** Accepted (harness: secure processing).
The harness turns on Xerces-J's secure processing, which limits an expanded content model to 3,000 nodes; these
instances need a larger one. Without secure processing, validating `particlesZ036_b1.i`, `particlesZ036_b2.i` and
`particlesZ036_c.v` makes Xerces-J expand the content model until the heap is exhausted (seen with a 1 GB heap, and in
the full run with the default surefire heap, where the whole `Particles_w3c` set was lost), so the verdicts would
depend on the heap size. `elemZ020.v` passes without secure processing.

## Xerces-J deviations

### Occurrence values above the int range
`elemZ004`, `addB099`, `groupF009v`, `groupF009v.v`, `groupH009v`, `groupH009v.v`, `groupJ009v`, `groupJ009v.v`,
`groupL009v`, `groupL009v.v`, `groupN009v`, `groupN009v.v`, `particlesZ033_a`, `particlesZ035_a`, `particlesZ035_a.i`

**Status:** Accepted (Xerces-J baseline).
`minOccurs`/`maxOccurs` are `xs:nonNegativeInteger`, unbounded in size. Xerces-J rejects values from 2,147,483,648 on
(`s4s-att-invalid-value ... is not a valid value for 'nonNegativeInteger'`), e.g. `2147483648` (`elemZ004`),
`12678967543233` (`addB099`) and `999999999999999` (`groupH009v`). The instance tests of these groups fail because
their schema was rejected.

### Conditional inclusion (`vc:minVersion`) not implemented
`vc001.xsd`, `vc001.v1.xml`, `vc001.n1.xml`, `vc002.xsd`, `vc002.v1.xml`, `vc002.n1.xml`, `vc014.xsd`,
`vc014.v1.xml`, `vc014.n1.xml`, `vc024-11.xsd`, `vc024.v1.xml`, `vc024.n1.xml`

**Status:** Accepted (Xerces-J baseline).
The schemas mark XSD 1.1 constructs (`xs:assert`, the assertion facet, the type `xs:error`) with
`vc:minVersion="1.1"`, so that an XSD 1.0 processor leaves them out. Xerces-J 2.12.2 ignores the `vc:` attributes and
rejects the 1.1 constructs (`s4s-elt-invalid-content.1`, `s4s-elt-must-match.1`, `src-resolve.4.2`).

### Missing components reject the whole schema
`missing001.xsd`, `missing001.v1.xml`, `missing001.n1.xml`, `missing003.xsd`, `missing003.v1.xml`,
`missing003.n1.xml`, `missing006.xsd`, `missing006.v1.xml`, `missing006.n1.xml`

**Status:** Accepted (Xerces-J baseline).
Saxonica's `Missing` set (XSD 1.0 only) references an undefined type or element from a declaration that the valid
instance does not use; per the set's annotations that is an error only when the declaration is needed for
validation. Xerces-J rejects the schema (`src-resolve`), so the schema test and both instance tests fail. The group
`missing002` reuses the names `missing001.xsd`, `missing001.v1.xml` and `missing001.n1.xml`.

### XML 1.0 Fifth Edition name characters
`xv001.xsd`, `xv001.v01.xml`, `xv002.v01.xml`, `xv004.xsd`, `xv004.v01.xml`, `xv005.v01.xml`, `xv006.n02.xml`,
`xv006.n03.xml`, `xv007.v01.xml`, `xv008.n01.xml`, `xv008.n02.xml`, `xv009.v02.xml`, `xv100i.i.xml`,
`xv100c.i.xml`, `xv100c.c.xml`, `reZ006i.i`

**Status:** Accepted (Xerces-J baseline).
The suite uses the name characters of XML 1.0 Fifth Edition (the same as XML 1.1) for `xs:NCName`, `xs:NMTOKEN` and
the regex escapes `\i`, `\I`, `\c`, `\C` (header comment of the `XmlVersions` set; group reZ006i refers to W3C bug 13606).
Xerces-J uses the older XML 1.0 name characters: it rejects the name `Dĳkstra` (`xv001.xsd`), and its `\i` and `\c` do
not match U+0370 (`xv005.v01.xml`, `xv007.v01.xml`) or U+0346 (`reZ006i.i`).

### XSD 1.1 wildcard syntax in groups without a version
`s3_10_6v01s`, `s3_10_6v01i`, `s3_10_6v02s`, `s3_10_6v02i`, `s3_10_6v04s`, `s3_10_6v04i`, `s3_10_1ii08s`,
`s3_10_1ii08i`, `s3_10_1ii09s`, `s3_10_1ii09i`

**Status:** Accepted (suite defect for XSD 1.0).
IBM's groups `s3_10_6ii01`, `s3_10_6ii02`, `s3_10_6ii04`, `s3_10_1ii08` and `s3_10_1ii09` use the XSD 1.1 wildcard
attributes `notQName`/`notNamespace` (with `##defined`, `##definedSibling`) and expect the schema to be valid, but,
unlike their sibling groups (`version="1.1"`), carry no `version`, so they also apply to XSD 1.0. Xerces-J rejects
the attributes as XSD 1.0 requires (`s4s-att-not-allowed`). go-xslt accepts them in both versions.

### Regular expressions
`reF56`, `reM77`, `reM77.i`, `reN75`, `reN75.i`, `HighSurrogates`, `HighSurrogates.i`, `reV3.i`, `reV6.i`, `reV15.i`,
`reV17.i`, `reV19.i`, `reV21.i`, `reV26.i`, `reV27.i`, `reV28.i`, `reV32.i`, `reV43.i`, `RegexTest_119`,
`RegexTest_141`, `RegexTest_151`, `RegexTest_155`, `RegexTest_465`, `RegexTest_517`, `RegexTest_930`,
`RegexTest_1414`

**Status:** Accepted (Xerces-J baseline).
Deviations of Xerces-J's regular expressions from what the suite expects:
- The block escapes `\p{IsHighSurrogates}` and `\p{IsLowSurrogates}` are rejected as "Unknown property" (`reM77`,
  `reN75`, `HighSurrogates`; their instance tests fail with the schema).
- The subtraction `[a-z--[b-z]]` is rejected ("'-' is an invalid character range"); the suite expects a valid pattern
  (`reF56`).
- `\W` matches supplementary characters that are letters, marks, digits or symbols (U+1D7A8, U+1D7C9, U+2FA1D,
  U+1D1AD, U+1D172, U+1D7FF, U+1034A, U+10323, U+1D1DD; none is in category P, Z or C), so the `reV*.i` instances
  are accepted.
- Patterns outside the XSD grammar are accepted: octal escapes such as `\077` and `\040` (`RegexTest_141`, `_151`,
  `_155`, `_517`; the suite's annotation: "bogus \[digit]"), `(?#...)` comments (`RegexTest_119`, `_930`, `_1414`)
  and `[-[e-g]+` (`RegexTest_465`).

### gMonth in the old `--MM--` form
`gMonth_enumeration002_203`, `gMonth_maxInclusive002_205`, `gMonth_maxExclusive002_208`, `gMonth_minInclusive002_210`,
`gMonth_minExclusive002_213`, `gMonth_pattern001_1274.v`, `gMonth_enumeration001_1275`, `gMonth_enumeration002_1276`,
`gMonth_enumeration003_1277`, `gMonth_enumeration004_1278`, `gMonth_maxInclusive001_1279`,
`gMonth_maxInclusive002_1280`, `gMonth_maxInclusive003_1281`, `gMonth_maxExclusive001_1282`,
`gMonth_maxExclusive002_1283`, `gMonth_maxExclusive003_1284`, `gMonth_minInclusive001_1285`,
`gMonth_minInclusive002_1286`, `gMonth_minInclusive003_1287`, `gMonth_minInclusive004_1288`,
`gMonth_minInclusive005_1289`, `gMonth_minExclusive001_1290`, `gMonth_minExclusive002_1291`,
`gMonth_minExclusive003_1292`, `gMonth_minExclusive004_1293`, `gMonth_minExclusive005_1294`

**Status:** Accepted (Xerces-J baseline).
The facet and instance values are written `--01--`, `--10--`, ...; XSD 1.0 Second Edition writes a gMonth `--MM`, so
the suite expects them to be invalid. Xerces-J still accepts the `--MM--` form.

### Other datatype checks
`time_maxInclusive007_119`, `positiveInteger_maxExclusive001_1834`, `positiveInteger_maxExclusive002_1835`,
`anyURI_b006_1356`, `QName009_2092.v`

**Status:** Accepted (Xerces-J baseline).
Xerces-J accepts what the suite expects to be invalid:
- `time_maxInclusive007_119`: `maxInclusive` `12:00:00-14:00` restricting a base with `maxInclusive`
  `12:00:00-10:00`. In UTC these are 02:00 of the following day and 22:00, so the derived value is the greater one.
- `positiveInteger_maxExclusive001_1834`, `_1835`: `maxExclusive` `1` on `xs:positiveInteger`, whose `minInclusive`
  is 1.
- `anyURI_b006_1356`: enumeration values such as `\` and `\\a\b`; per the test's annotation the TSTF ruled that they
  are not valid `xs:anyURI` values in XSD 1.0 (valid in 1.1).
- `QName009_2092.v`: the QName value `xmlns:xsi`; per the annotation the WG decided (2010-02-05) that the prefix
  `xmlns` has no binding, so it is no valid QName.

### Identity constraints and IDREFs
`idI021`, `idJ029`, `idL100.v`, `idZ012.i`, `idZ015.i`

**Status:** Accepted (Xerces-J baseline).
- `idI021`, `idJ029`: Xerces-J rejects the path `. //.` (whitespace between `.` and `//`, `c-general-xpath`); the
  suite expects it to be accepted.
- `idL100.v`: the key field `@*` is evaluated on elements that each carry two attributes (`myNS:row` and `xsi:nil`);
  the TSTF reversed the verdict to invalid (annotation). Xerces-J accepts the instance.
- `idZ012.i`: an `xs:IDREFS` attribute whose default `abc` refers to no ID. Xerces-J does not report the dangling
  reference that comes from the default value.
- `idZ015.i`: the key field selects an attribute that is only admitted by a `processContents="skip"` wildcard, so it
  has no simple type; the TSTF concluded that this is invalid in XSD 1.0 (annotation). Xerces-J accepts it.

### Particle restriction
`particlesHa080`, `particlesHa084`, `mgE014`, `mgE014.v`, `particlesZ039`

**Status:** Accepted (Xerces-J baseline).
- `particlesHa080`, `particlesHa084`: a sequence of two elements and a wildcard (in `particlesHa084` wrapped in a
  second sequence) restricting a sequence that holds one `##any` wildcard with occurrence range 2..3 ("Apply Pointless
  rules at top level"). Xerces-J reports `rcase-NSRecurseCheckCardinality.1`; the suite expects a valid restriction.
- `mgE014`: an optional element restricted to `maxOccurs="0"`. Xerces-J reports `cos-particle-restrict.2`; the suite
  expects a valid restriction (the instance test fails with the schema).
- `particlesZ039`: a complex type with simple content restricted to complex content with an empty sequence; the
  suite expects an invalid restriction, Xerces-J accepts it.

### Other content and structure checks
`complex022.n1.xml`, `attP031.i`, `addB156.i`, `addB158.i`, `schZ014_a`, `schZ014_b`, `errF001.i`

**Status:** Accepted (Xerces-J baseline).
- `complex022.n1.xml`: an element whose type is the empty choice `<xs:choice/>`; the annotation says empty content
  does not satisfy an empty choice. Xerces-J accepts the empty element.
- `attP031.i`: a local attribute declared `use="prohibited" fixed="37"` and present with value 37. The suite expects
  the instance to be valid in XSD 1.0 (the schema is invalid in 1.1); Xerces-J treats the prohibited use as absent
  and rejects the attribute (`cvc-complex-type.3.2.2`). go-xslt keeps the fixed use in XSD 1.0.
- `addB156.i`, `addB158.i`: an `xsi:schemaLocation` for a namespace (`foo` resp. `bar`) that appears after an element
  of that namespace was validated; the suite expects that to be an error in XSD 1.0 (valid in 1.1; W3C bugs 9158 and
  15863). Xerces-J accepts it. go-xslt implements the rule (`checkLateHints`).
- `schZ014_a`, `schZ014_b`: `namespace=""` on `xs:import` resp. `targetNamespace=""`, which the annotations say are
  not allowed. Xerces-J accepts both.
- `errF001.i`: the schema puts `length` and `maxLength` in different derivation steps, which erratum E2-35 allows.
  Xerces-J rejects the schema (`length-minLength-maxLength.2.2.a`), so the instance test fails; the schema test itself
  is queried in the suite and not counted.

## Disputed or contradictory tests

### `particlesZ001`, `particlesZ001.i`
**Status:** Accepted (suite contradiction).
The schema test expects the restriction to be valid, while the annotation calls it an "invalid restriction which
becomes valid in XSD 1.1" and the instance test expects invalid for 1.0 and valid for 1.1. Xerces-J rejects the schema
(`rcase-Recurse.2`), which fails the schema test and, with it, the instance test. go-xslt documents `particlesZ001.i`
as a suite contradiction too.

### `stE072`, `stE072.v`
**Status:** Accepted (disputed in the annotation).
An element with `fixed="a"` whose type is a union of `xs:gYearMonth`, `xs:ID` and `xs:long`. Expected valid, but the
test's own annotation says that the schema is possibly invalid in XSD 1.0 because an ID cannot have a fixed value,
which is what Xerces-J reports (`e-props-correct.5`).

### `stZ073b.i`
**Status:** Accepted (disputed in the suite).
`xsi:type="xs:integer"` on an element whose type restricts a union of `xs:string` and `xs:integer` by enumeration.
The annotation says that the test awaits the disposition of W3C bug 5784, and one of the two groups that run it
(stZ073b, stZ073ba) marks it `disputed-spec`. Xerces-J accepts the instance.

### `ste110.i`
**Status:** Accepted (schema test queried).
`stE110.xsd` defines a circular union. Its schema test is queried in the suite and not counted, its instance test
(expected invalid) is. Xerces-J rejects the schema (`st-props-correct.2`), so the instance test fails.

### `schU1`
**Status:** Accepted (disputed in the suite).
Circular redefines; the latest status of the schema test is `disputed-test`. Xerces-J reports two global components
with the same name (`sch-props-correct.2`).
