# Memory usage of the tree models

How much heap does a parsed document occupy? This page compares three in-memory XML tree models on the same
documents:

| Model | Library | Shape |
|---|---|---|
| **ph-xdm** | ph-xslt `xpath-xdm` 0.1.0-SNAPSHOT, `XdmParser` | One `XdmNode` object per node (document, element, attribute, text, ...) |
| **MicroDOM** | ph-commons `ph-xml` 12.5.2, `MicroReader.readMicroXML` | One object per node, attributes in a per-element map |
| **Saxon TinyTree** | Saxon-HE 12.10, `Configuration.buildDocumentTree` | No node objects: parallel `int` arrays, names as `NamePool` codes, all text in one shared buffer |

## Results

Retained heap of one parsed document, measured on Temurin 21.0.13 and 25.0.5 (identical figures; compact object
headers were not enabled). 1 MB = 2^20 bytes. The ph-xdm column is the current state; the two columns after it show
the earlier steps (see [ph-xdm optimisations](#ph-xdm-optimisations)).

| Document | Character | File size | Elements | Attributes | Text nodes | ph-xdm | ph-xdm shared names | ph-xdm original | MicroDOM | Saxon TinyTree |
|---|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | small business document, namespaces | 0.01 MB | 136 | 98 | 271 | 0.08 MB | 0.10 MB | 0.10 MB | 0.07 MB | 0.02 MB |
| XMark auction | mixed data and prose | 3.34 MB | 50,198 | 11,526 | 91,070 | 21.56 MB | 26.22 MB | 27.63 MB | 20.20 MB | 5.35 MB |
| iTunes plist | very many tiny elements | 9.98 MB | 393,688 | 1 | 607,488 | 130.68 MB | 161.24 MB | 170.25 MB | 114.03 MB | 16.26 MB |
| Bank transactions | empty elements with 2 attributes each | 4.74 MB | 100,002 | 200,001 | 100,003 | 50.51 MB | 62.72 MB | 69.58 MB | 55.85 MB | 16.13 MB |
| Unicode characters | empty elements with 5 attributes each | 6.08 MB | 116,767 | 583,830 | 116,767 | 98.02 MB | 122.96 MB | 139.00 MB | 109.60 MB | 38.05 MB |
| Old Testament | prose, long text nodes | 3.33 MB | 25,317 | 0 | 49,638 | 12.51 MB | 14.80 MB | 15.38 MB | 11.36 MB | 4.35 MB |
| XSLT 3.0 spec (XHTML) | document markup, mixed content | 2.92 MB | 47,867 | 52,503 | 66,267 | 23.69 MB | 28.78 MB | 31.09 MB | 24.11 MB | 7.70 MB |
| XSLT test stylesheets | many namespace declarations | 6.53 MB | 71,672 | 84,618 | 127,382 | 43.19 MB | 51.60 MB | 57.34 MB | 40.99 MB | 10.45 MB |

Node counts are taken from the ph-xdm tree. All three models keep whitespace-only text nodes and hold the same nodes
(checked against the MicroDOM class counts and the TinyTree node kinds).

### Heap per byte of input

| Document | ph-xdm | MicroDOM | Saxon TinyTree | ph-xdm vs. MicroDOM | ph-xdm vs. TinyTree |
|---|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | 7.7 | 6.8 | 1.6 | 1.15x | 4.8x |
| XMark auction | 6.4 | 6.0 | 1.6 | 1.05x | 4.0x |
| iTunes plist | 13.1 | 11.4 | 1.6 | 1.15x | 8.0x |
| Bank transactions | 10.7 | 11.8 | 3.4 | 0.9x | 3.1x |
| Unicode characters | 16.1 | 18.0 | 6.3 | 0.9x | 2.6x |
| Old Testament | 3.8 | 3.4 | 1.3 | 1.1x | 2.9x |
| XSLT 3.0 spec (XHTML) | 8.1 | 8.2 | 2.6 | 1.0x | 3.1x |
| XSLT test stylesheets | 6.6 | 6.3 | 1.6 | 1.05x | 4.1x |

### Summary

- **Saxon TinyTree** is by far the most compact: 1.3 to 6 times the file size, and 2.6 to 8 times smaller than
  ph-xdm. The gap is largest for documents made of many small nodes (iTunes plist) and smallest for prose and
  attribute-heavy documents.
- **ph-xdm and MicroDOM** are now on par: ph-xdm needs up to 15 % more for element- and text-heavy documents and
  10 % less for attribute-heavy ones.
- **ph-xdm** needs between 3.8 (prose) and 16 (attribute-heavy) times the file size, 19 to 30 % less than
  originally.
- Small nodes are expensive in every object-per-node model: the cost is driven by the node count, not the byte
  count.

## ph-xdm optimisations

| Step | Change | Saving |
|---|---|--:|
| Shared names | `XdmTreeBuilder` keeps one `XdmQName` per distinct name of a document instead of one per element and attribute (the map is keyed by namespace URI and lexical name, because `XdmQName.equals` ignores the prefix) | 4 to 12 % |
| `Details` holder | The rarely set properties of `XdmNode` (declared namespaces, built-in type annotation, nilled, document URI, base URI, namespace node index, disable-output-escaping, entities) moved into a nested object that is created on the first non-default value. `XdmNode` shrank from 104 to 72 bytes | 15 to 20 % |

The schema type stays in `XdmNode`, so schema-validated trees do not need a `Details` object per node: the type
annotation of a validated node is derived from its schema type.

## Where the memory goes

Class footprint of the "Bank transactions" document (100,002 elements, 200,001 attributes, 100,003 text nodes):

| ph-xdm | Count | Size | | MicroDOM | Count | Size |
|---|--:|--:|---|---|--:|--:|
| `XdmNode` | 400,007 | 27.47 MB | | `MicroElement` | 100,002 | 3.81 MB |
| `XdmNode$Details` | 1 | 0.00 MB | | `MicroAttribute` + `MicroQName` | 400,002 | 9.16 MB |
| `XdmQName` (shared) | 6 | 0.00 MB | | `String` + `byte[]` | 500,021 | 13.73 MB |
| `String` + `byte[]` | 600,034 | 14.50 MB | | `CommonsLinkedHashMap` + entries + tables | 400,003 | 21.36 MB |
| `CommonsArrayList` + `Object[]` | 200,008 | 8.55 MB | | `MicroText` + `MicroDataAware` + `StringBuilder` | 300,009 | 6.87 MB |
| | | | | child lists (`CommonsArrayList` + `Object[]`) | 6 | 0.92 MB |
| **Total** | | **50.51 MB** | | **Total** | | **55.85 MB** |

Observations for ph-xdm:

- An `XdmNode` is 72 bytes. Attributes and text nodes pay the same price as elements and make up 75 % of the nodes
  here, so a node class per kind would be the next large step.
- The child and attribute lists start with room for 10 entries. Here they average 90 bytes for 1 to 2 entries;
  trimming them to size would save about 3 MB.
- MicroDOM pays for a `LinkedHashMap` per element with attributes instead, which costs more than ph-xdm's attribute
  list, but its node objects are smaller (16 to 40 bytes).

Saxon avoids the per-node objects altogether, which is why its figures scale with the file size rather than the
node count. In addition, an element whose only child is a text node is stored as a single "textual element" entry
(27,115 of the 50,198 elements in XMark), so the text node does not need an entry of its own.

## Method

- The figure is the deep size of the object graph reachable from the tree root, measured with
  [JOL](https://github.com/openjdk/jol) 0.17 (`GraphLayout.parseInstance (...).totalSize ()`): document node for
  ph-xdm, `IMicroDocument` for MicroDOM and the `TinyTree` for Saxon.
- The Saxon tree references its `Configuration`, which is shared by all documents. It is subtracted, including the
  `NamePool`. The `NamePool` growth caused by a document was measured separately and stays below 0.05 MB for all
  documents except the XSLT test stylesheets (0.35 MB), so it does not change the picture.
- Strings shared by the JDK SAX parser (interned element names) are counted in the ph-xdm and MicroDOM figures, but
  only once per tree.
- Cross-check: the heap delta after a full GC around each parse agrees with JOL to within 3 % for ph-xdm and
  MicroDOM. For Saxon it is 0.1 to 6.4 MB higher; it includes the fresh `Configuration`, the remainder was not analysed.
- ph-xdm is parsed without a schema; with schema validation the type annotations add to its figure.
- Only retained memory is measured, not the transient garbage while parsing, and not parse time.

## Running it

The benchmark lives in the standalone Maven module `xdm-memory-benchmark` of ph-xslt (not part of the reactor build).
It expects the W3C test suites in `../../git-thirdparty` and `peppol-commons` as a sibling of ph-xslt.

```bash
mvn install -pl xpath-xdm -am -DskipTests
cd xdm-memory-benchmark
mvn compile exec:exec
```
