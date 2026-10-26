# XSD (`xsdtests`): failing cases of the native validator

The native validator of `xsd-core` (a port of go-xslt's `internal/xsd`) is selected with `-Dxsd.validator=native`;
its fail list is written to `target/xsd-native-fails.txt`. This file is its accepted-failures list, separate from the
Xerces-J one (`xsd.md`).

XSD 1.0: 39,396 of 39,401 applicable cases pass, 5 fail, nothing is unsupported; the denominator equals go-xslt's and
so does the fail list. XSD 1.1 is not implemented yet (phase V8): every applicable 1.1 case is reported as
unsupported, outside the pass/fail denominator, never as a failure.

Every failing case is named here in backticks under a heading with its status and reason. `XsdTest` fails on any
failing case that is not named here (switch that off with `-Dxsd.checkAccepted=false`), so a regression is loud and a
newly accepted failure needs a written reason. Remove an entry when its case passes. The five entries are go-xslt's
documented XSD 1.0 failures (`docs/accepted-failures/xsd.md` there), with the same reasons.

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
