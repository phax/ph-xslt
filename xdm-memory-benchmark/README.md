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
headers were not enabled). 1 MB = 2^20 bytes. ph-xdm shares one `XdmQName` per distinct name of a document; the
"ph-xdm before" column is the figure without that sharing.

| Document | Character | File size | Elements | Attributes | Text nodes | ph-xdm | ph-xdm before | MicroDOM | Saxon TinyTree |
|---|---|--:|--:|--:|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | small business document, namespaces | 0.01 MB | 136 | 98 | 271 | 0.10 MB | 0.10 MB | 0.07 MB | 0.02 MB |
| XMark auction | mixed data and prose | 3.34 MB | 50,198 | 11,526 | 91,070 | 26.22 MB | 27.63 MB | 20.20 MB | 5.35 MB |
| iTunes plist | very many tiny elements | 9.98 MB | 393,688 | 1 | 607,488 | 161.24 MB | 170.25 MB | 114.03 MB | 16.26 MB |
| Bank transactions | empty elements with 2 attributes each | 4.74 MB | 100,002 | 200,001 | 100,003 | 62.72 MB | 69.58 MB | 55.85 MB | 16.13 MB |
| Unicode characters | empty elements with 5 attributes each | 6.08 MB | 116,767 | 583,830 | 116,767 | 122.96 MB | 139.00 MB | 109.60 MB | 38.05 MB |
| Old Testament | prose, long text nodes | 3.33 MB | 25,317 | 0 | 49,638 | 14.80 MB | 15.38 MB | 11.36 MB | 4.35 MB |
| XSLT 3.0 spec (XHTML) | document markup, mixed content | 2.92 MB | 47,867 | 52,503 | 66,267 | 28.78 MB | 31.09 MB | 24.11 MB | 7.70 MB |
| XSLT test stylesheets | many namespace declarations | 6.53 MB | 71,672 | 84,618 | 127,382 | 51.60 MB | 57.34 MB | 40.99 MB | 10.45 MB |

Node counts are taken from the ph-xdm tree. All three models keep whitespace-only text nodes and hold the same nodes
(checked against the MicroDOM class counts and the TinyTree node kinds).

### Heap per byte of input

| Document | ph-xdm | MicroDOM | Saxon TinyTree | ph-xdm vs. MicroDOM | ph-xdm vs. TinyTree |
|---|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | 9.1 | 6.8 | 1.6 | 1.35x | 5.7x |
| XMark auction | 7.8 | 6.0 | 1.6 | 1.3x | 4.9x |
| iTunes plist | 16.1 | 11.4 | 1.6 | 1.4x | 9.9x |
| Bank transactions | 13.2 | 11.8 | 3.4 | 1.1x | 3.9x |
| Unicode characters | 20.2 | 18.0 | 6.3 | 1.1x | 3.2x |
| Old Testament | 4.4 | 3.4 | 1.3 | 1.3x | 3.4x |
| XSLT 3.0 spec (XHTML) | 9.8 | 8.2 | 2.6 | 1.2x | 3.7x |
| XSLT test stylesheets | 7.9 | 6.3 | 1.6 | 1.25x | 4.9x |

### Summary

- **Saxon TinyTree** is by far the most compact: 1.3 to 6 times the file size, and 3 to 10 times smaller than
  ph-xdm. The gap is largest for documents made of many small nodes (iTunes plist) and smallest for prose, where the
  characters themselves dominate.
- **MicroDOM** needs 10 to 30 % less heap than ph-xdm.
- **ph-xdm** needs between 4.4 (prose) and 20 (attribute-heavy) times the file size. Sharing the names saved 4 to
  12 %, most for attribute-heavy documents.
- Small nodes are expensive in every object-per-node model: the cost is driven by the node count, not the byte
  count.

## Where the memory goes

Class footprint of the "Bank transactions" document (100,002 elements, 200,001 attributes, 100,003 text nodes):

| ph-xdm | Count | Size | | MicroDOM | Count | Size |
|---|--:|--:|---|---|--:|--:|
| `XdmNode` | 400,007 | 39.67 MB | | `MicroElement` | 100,002 | 3.81 MB |
| `XdmQName` (shared) | 6 | 0.00 MB | | `MicroAttribute` + `MicroQName` | 400,002 | 9.16 MB |
| `String` + `byte[]` | 600,034 | 14.50 MB | | `String` + `byte[]` | 500,021 | 13.73 MB |
| `CommonsArrayList` + `Object[]` | 200,008 | 8.55 MB | | `CommonsLinkedHashMap` + entries + tables | 400,003 | 21.36 MB |
| | | | | `MicroText` + `MicroDataAware` + `StringBuilder` | 300,009 | 6.87 MB |
| | | | | child lists (`CommonsArrayList` + `Object[]`) | 6 | 0.92 MB |
| **Total** | | **62.72 MB** | | **Total** | | **55.85 MB** |

Before the names were shared, the ph-xdm tree held 300,003 `XdmQName` objects here (6.87 MB), one per element and
attribute, for 6 distinct names.

Observations for ph-xdm:

- An `XdmNode` is 104 bytes, because every node carries all fields of every kind (line, column, document order,
  type annotation, schema type, base URI, namespace data, ...). Attributes and text nodes pay the same price as
  elements and make up 75 % of the nodes here. This is now the dominant cost.
- MicroDOM pays for a `LinkedHashMap` per element with attributes instead, which costs more than ph-xdm's attribute
  list, but its node objects are much smaller (16 to 40 bytes).

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
