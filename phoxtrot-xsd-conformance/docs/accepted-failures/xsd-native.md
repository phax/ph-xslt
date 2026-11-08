# XSD (`xsdtests`): failing cases of the native validator

The native validator of `phoxtrot-xsd` (a port of go-xslt's `internal/xsd`) is selected with `-Dxsd.validator=native`;
`XsdNativeIT` always runs it, for XSD 1.0 and 1.1 unless `-Dxsd.version` selects one. Its fail lists are written to
`target/xsd-native-fails.txt` (1.0) and `target/xsd-native11-fails.txt` (1.1). This file is its accepted-failures
list, separate from the Xerces-J one (`xsd.md`).

* XSD 1.0: 39,396 of 39,401 applicable cases pass, 5 fail, nothing is unsupported.
* XSD 1.1: 41,596 of 41,600 applicable cases pass, 4 fail, nothing is unsupported.

The fail lists equal go-xslt's. The XSD 1.0 denominator does too; the XSD 1.1 one is larger by the 47 cases of the
`full-xpath-in-CTA` test groups, which the harness runs because the native validator evaluates type alternatives
with the full XPath engine (`XsdSuiteRules.getClaimedTokens`).

Every failing case is named here in backticks under a heading with its status and reason. `XsdIT` fails on any
failing case that is not named here (switch that off with `-Dxsd.checkAccepted=false`), so a regression is loud and a
newly accepted failure needs a written reason. Remove an entry when its case passes. The entries are go-xslt's
documented failures (`docs/accepted-failures/xsd.md` there), with the same reasons.

## XSD 1.0

### `particlesZ001.i`
**Status:** Accepted (suite contradiction).
The schema is expected to be invalid, but the valid twin test in the same `schemaTest` needs the opposite reading of
the RecurseAsIfGroup rule. The validator follows the reading the suite needs elsewhere (a wrapper group keeps the
occurrence range only when the base group repeats without bound, see `XsdRestrictionChecker`). Changing it is a net
loss of one case.

### `particlesZ023`, `particlesZ024`, `groupH021v`
**Status:** Accepted (suite contradiction).
A choice restricted by dropping branches. The same mapping with element branches is expected *valid* by the
version-independent tests `particlesT003`, `particlesT005` and `particlesT006`. No XSD 1.0 clause makes these three
invalid and those three valid.

### `idB005`
**Status:** Accepted.
The only defect a processor can detect is a type reference with the wrong letter case (`keyinfo` instead of
`keyInfo`). The broader rule that would reject it (schema-wide uniqueness of `@id`) is refuted by about 20 valid
cross-document tests.

## XSD 1.1

### `all308.xsd`
**Status:** Accepted (questioned in the suite).
Expected invalid; the suite's own description reads "Currently invalid, but see bug 6202 against the spec".

### `particlesZ026`
**Status:** Accepted.
The test-suite task force declared the outcome implementation-determined (W3C bug 4071).

### `particlesZ033_g`
**Status:** Accepted.
Expected invalid, but no XSD 1.1 rule it violates can be found: XSD 1.1 allows the competition between an element and a
wildcard that would make it invalid in 1.0.

### `ste110.i`
**Status:** Accepted (deliberate price).
XSD 1.1 forbids every circular union (`cos-no-circular-unions`; the suite's `simple017`, `simple020` and others).
`ste110.i` expects one such cycle to be accepted, and its valid twin is excluded as queried.
