# XSD (`xsdtests`): failing cases of the native validator

The native validator of `xsd-core` (a port of go-xslt's `internal/xsd`) is selected with `-Dxsd.validator=native`;
its fail list is written to `target/xsd-native-fails.txt`. This file is its accepted-failures list, separate from the
Xerces-J one (`xsd.md`).

While the port is incomplete (phases V2 to V5 of `docs/PLAN.md`), the accepted-failures check is off by default for
the native run (`-Dxsd.checkAccepted=true` switches it on). Constructs that are not implemented yet are reported as
unsupported, outside the pass/fail denominator, never as failures. The check becomes the gate in phase V6, when every
failing case must be named here with a reason, like in `xsd.md`. The target is go-xslt's five documented XSD 1.0
failures.

No case is accepted yet.
