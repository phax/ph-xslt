# Memory usage of the tree models

How much heap does a parsed document occupy? This page compares three in-memory XML tree models on the same
documents:

| Model | Library | Shape |
|---|---|---|
| **ph-xdm** | phoxtrot `phoxtrot-xdm` 0.1.0-SNAPSHOT, `XdmParser` | One object per node, one class per node kind (`XdmElementNode`, `XdmAttributeNode`, `XdmTextNode`, ...) |
| **MicroDOM** | ph-commons `ph-xml` 12.5.2, `MicroReader.readMicroXML` | One object per node, attributes in a per-element map |
| **Saxon TinyTree** | Saxon-HE 12.10, `Configuration.buildDocumentTree` | No node objects: parallel `int` arrays, names as `NamePool` codes, all text in one shared buffer |

## Results

Retained heap of one parsed document, measured on Temurin 21.0.13 and 25.0.5 without compact object headers (identical
figures), ph-xdm with the default parser settings (node locations recorded). 1 MB = 2^20 bytes.

| Document | Character | File size | Elements | Attributes | Text nodes | ph-xdm | MicroDOM | Saxon TinyTree |
|---|---|--:|--:|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | small business document, namespaces | 0.01 MB | 136 | 98 | 271 | 0.05 MB | 0.07 MB | 0.02 MB |
| XMark auction | mixed data and prose | 3.34 MB | 50,198 | 11,526 | 91,070 | 13.44 MB | 20.20 MB | 5.35 MB |
| iTunes plist | very many tiny elements | 9.98 MB | 393,688 | 1 | 607,488 | 81.82 MB | 114.03 MB | 16.26 MB |
| Bank transactions | empty elements with 2 attributes each | 4.74 MB | 100,002 | 200,001 | 100,003 | 29.27 MB | 55.85 MB | 16.13 MB |
| Unicode characters | empty elements with 5 attributes each | 6.08 MB | 116,767 | 583,830 | 116,767 | 60.62 MB | 109.60 MB | 38.05 MB |
| Old Testament | prose, long text nodes | 3.33 MB | 25,317 | 0 | 49,638 | 8.66 MB | 11.36 MB | 4.35 MB |
| XSLT 3.0 spec (XHTML) | document markup, mixed content | 2.92 MB | 47,867 | 52,503 | 66,267 | 14.34 MB | 24.11 MB | 7.70 MB |
| XSLT test stylesheets | many namespace declarations | 6.53 MB | 71,672 | 84,618 | 127,382 | 25.60 MB | 40.99 MB | 10.45 MB |

Node counts are taken from the ph-xdm tree. All three models keep whitespace-only text nodes and hold the same nodes
(checked against the MicroDOM class counts and the TinyTree node kinds).

### Compact object headers

The same documents on Temurin 25.0.5 with `-XX:+UseCompactObjectHeaders` (JEP 519), which shrinks every object header
from 12 to 8 bytes (16 to 12 for arrays), for all three models (measured 2026-11-09, `run.sh full`; Temurin 25.0.5
without the option gives the same figures as JDK 21):

| Document | ph-xdm | MicroDOM | Saxon TinyTree | ph-xdm saving | MicroDOM saving | TinyTree saving |
|---|--:|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | 0.04 MB | 0.06 MB | 0.02 MB | - | - | - |
| XMark auction | 11.85 MB | 18.22 MB | 5.30 MB | 12 % | 10 % | 1 % |
| iTunes plist | 69.72 MB | 103.32 MB | 16.26 MB | 15 % | 9 % | 0 % |
| Bank transactions | 27.15 MB | 48.73 MB | 15.12 MB | 7 % | 13 % | 6 % |
| Unicode characters | 56.64 MB | 94.04 MB | 34.96 MB | 7 % | 14 % | 8 % |
| Old Testament | 7.81 MB | 10.49 MB | 4.35 MB | 10 % | 8 % | 0 % |
| XSLT 3.0 spec (XHTML) | 12.87 MB | 21.67 MB | 7.45 MB | 10 % | 10 % | 3 % |
| XSLT test stylesheets | 23.04 MB | 36.78 MB | 10.05 MB | 10 % | 10 % | 4 % |

Before batch 4 ph-xdm gained only 3 to 5 %: its node objects were padded to a multiple of 8 bytes, and the 4 bytes a
header loses were taken by the padding. The layouts of batch 4 (tree stamp, rare fields in `Details`) are cut so that
the header saving counts; ph-xdm now gains about as much as MicroDOM, which has more, smaller objects per node.
TinyTree consists of a few large arrays and gains little.

After batch 4 (see below) the node layouts are cut so that the header saving counts too. ph-xdm now, JDK 21 and JDK 25
with compact headers, with and without node locations (`XdmParser.setRecordLocations (false)`):

| Document | JDK 21 | JDK 21, no locations | JDK 25, compact headers | JDK 25, compact headers, no locations |
|---|--:|--:|--:|--:|
| UBL invoice (Peppol) | 0.05 MB | 0.04 MB | 0.04 MB | 0.04 MB |
| XMark auction | 13.44 MB | 12.28 MB | 11.85 MB | 10.69 MB |
| iTunes plist | 81.82 MB | 74.18 MB | 69.72 MB | 62.08 MB |
| Bank transactions | 29.27 MB | 26.21 MB | 27.15 MB | 24.10 MB |
| Unicode characters | 60.62 MB | 54.39 MB | 56.64 MB | 50.40 MB |
| Old Testament | 8.66 MB | 8.09 MB | 7.81 MB | 7.23 MB |
| XSLT 3.0 spec (XHTML) | 14.34 MB | 13.07 MB | 12.87 MB | 11.60 MB |
| XSLT test stylesheets | 25.60 MB | 23.34 MB | 23.04 MB | 20.78 MB |

Instance sizes in bytes (JDK 21 / compact headers): element 56 / 48 (with location 64 / 56), attribute 40 / 40
(48 / 48), text 40 / 32 (48 / 40), document 72 / 64, `Details` 48 / 48.

### Heap per byte of input

| Document | ph-xdm | MicroDOM | Saxon TinyTree | ph-xdm vs. MicroDOM | ph-xdm vs. TinyTree |
|---|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | 4.5 | 6.8 | 1.6 | 0.66x | 2.8x |
| XMark auction | 4.0 | 6.0 | 1.6 | 0.67x | 2.5x |
| iTunes plist | 8.2 | 11.4 | 1.6 | 0.72x | 5.0x |
| Bank transactions | 6.2 | 11.8 | 3.4 | 0.52x | 1.8x |
| Unicode characters | 10.0 | 18.0 | 6.3 | 0.55x | 1.6x |
| Old Testament | 2.6 | 3.4 | 1.3 | 0.76x | 2.0x |
| XSLT 3.0 spec (XHTML) | 4.9 | 8.2 | 2.6 | 0.59x | 1.9x |
| XSLT test stylesheets | 3.9 | 6.3 | 1.6 | 0.62x | 2.4x |

### Summary

- **Saxon TinyTree** is by far the most compact: 1.3 to 6 times the file size, and 1.6 to 5.0 times smaller than
  ph-xdm. The gap is largest for documents made of many small nodes (iTunes plist) and smallest for prose and
  attribute-heavy documents.
- **ph-xdm** needs between 2.6 (prose) and 10 (attribute-heavy) times the file size. It is 24 to 48 % smaller than
  MicroDOM, and 44 to 58 % smaller than originally; without node locations and with compact object headers 53 to 65 %.
- Small nodes are expensive in every object-per-node model: the cost is driven by the node count, not the byte
  count.

## ph-xdm optimisations

| Step | Change | Saving |
|---|---|--:|
| Shared names | `XdmTreeBuilder` keeps one `XdmQName` per distinct name of a document instead of one per element and attribute (the map is keyed by namespace URI and lexical name, because `XdmQName.equals` ignores the prefix) | 4 to 12 % |
| `Details` holder | The rarely set properties (declared namespaces, built-in type annotation, nilled, document URI, base URI, disable-output-escaping) moved into a nested object that is created on the first non-default value. The node shrank from 104 to 72 bytes | 15 to 20 % |
| Node subtypes | The model became the sealed interface `IXdmNode`, implemented below the sealed `AbstractXdmNode` by one class per node kind that only carries the fields of its kind: element 64 bytes, attribute 56, text and comment 48 | 11 to 13 % |
| End order | Documents and elements store the order of the last node of their subtree (`getEndOrder ()`), which the name index of a document needs to answer descendant steps by interval. Element 72 bytes; the index itself is built on first use and not included here | -2 to -3 % |
| Child arrays | The children of documents and elements are a plain array, trimmed when the element is complete, instead of a list; the attribute list is created with the size of the parser event; indentation whitespace is one shared string per document | 8 to 27 % |
| Attribute arrays | The attributes of an element are an array of exactly their count instead of a list (no count field: the element keeps its size) | 0 to 7 % |
| Optional locations | Line and column live in a `Located` subclass of each node kind, created only when the parser records locations (default on); without, every node is 8 bytes smaller | 0 % (7 to 9 % without locations) |
| Tree stamp | The numbering of a tree is a shared `TreeStamp` reference instead of a `long` per node | 0 % (8 bytes per node with compact headers) |
| Rare fields in `Details` | The ID kind and the namespace nodes of elements and attributes moved into the holder; the schema type stays a field | 2 to 7 % |
| Shared attribute values | Attribute values of up to 32 characters are shared per document through a table of 1,024 slots (sharing text as well saved up to 17 % more, but cost about 10 % of the parse time) | 0 to 15 % |

The schema type is a field of element and attribute nodes, so schema-validated trees do not need a `Details` object
per node: the type annotation of a validated node is derived from its schema type.

| Document | Original | Shared names | `Details` holder | Node subtypes | End order | Child arrays | Attribute arrays | Rare fields | Shared attribute values | Total saving |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| UBL invoice (Peppol) | 0.10 MB | 0.10 MB | 0.08 MB | 0.07 MB | 0.07 MB | 0.05 MB | 0.05 MB | 0.05 MB | 0.05 MB | 50 % |
| XMark auction | 27.63 MB | 26.22 MB | 21.56 MB | 18.92 MB | 19.30 MB | 14.48 MB | 14.22 MB | 13.75 MB | 13.44 MB | 51 % |
| iTunes plist | 170.25 MB | 161.24 MB | 130.68 MB | 113.78 MB | 116.78 MB | 84.82 MB | 84.82 MB | 81.82 MB | 81.82 MB | 52 % |
| Bank transactions | 69.58 MB | 62.72 MB | 50.51 MB | 44.40 MB | 45.17 MB | 37.39 MB | 35.10 MB | 32.81 MB | 29.27 MB | 58 % |
| Unicode characters | 139.00 MB | 122.96 MB | 98.02 MB | 85.55 MB | 86.44 MB | 79.29 MB | 76.61 MB | 71.27 MB | 60.62 MB | 56 % |
| Old Testament | 15.38 MB | 14.80 MB | 12.51 MB | 11.18 MB | 11.37 MB | 8.85 MB | 8.85 MB | 8.66 MB | 8.66 MB | 44 % |
| XSLT 3.0 spec (XHTML) | 31.09 MB | 28.78 MB | 23.69 MB | 21.01 MB | 21.38 MB | 17.89 MB | 17.39 MB | 16.63 MB | 14.34 MB | 54 % |
| XSLT test stylesheets | 57.34 MB | 51.60 MB | 43.19 MB | 37.96 MB | 38.51 MB | 30.19 MB | 28.99 MB | 27.94 MB | 25.60 MB | 55 % |

All with locations, on JDK 21; "Optional locations" and "Tree stamp" do not change these figures (see the table of
compact object headers above for what they save).

## Where the memory goes

Class footprint of the "Bank transactions" document on JDK 21 (100,002 elements, 200,001 attributes, 100,003 text
nodes; measured 2026-11-09 with `run.sh footprint`, ph-xdm with node locations):

| ph-xdm | Count | Size | | MicroDOM | Count | Size |
|---|--:|--:|---|---|--:|--:|
| `XdmElementNode` (located, 64 bytes) | 100,002 | 6.10 MB | | `MicroElement` | 100,002 | 3.81 MB |
| `XdmAttributeNode` (located, 48 bytes) | 200,001 | 9.16 MB | | `MicroAttribute` + `MicroQName` | 400,002 | 9.16 MB |
| `XdmTextNode` (located, 48 bytes) | 100,003 | 4.58 MB | | `MicroText` + `MicroDataAware` + `StringBuilder` | 300,009 | 6.87 MB |
| `XdmDocumentNode`, `Details`, `TreeStamp`, shared `XdmQName` | 9 | 0.00 MB | | | | |
| `String` + `byte[]` | 261,978 | 6.38 MB | | `String` + `byte[]` | 500,021 | 13.73 MB |
| child and attribute arrays (`AbstractXdmNode[]`) | 100,004 | 3.05 MB | | `CommonsLinkedHashMap` + entries + tables | 400,003 | 21.36 MB |
| | | | | child lists (`CommonsArrayList` + `Object[]`) | 6 | 0.92 MB |
| **Total** | | **29.27 MB** | | **Total** | | **55.85 MB** |

Observations for ph-xdm:

- The node objects are now two thirds of the total (19.8 MB); without node locations each is 8 bytes smaller
  (26.21 MB in total).
- The strings dropped from 14.5 MB (before batch 3) to 6.4 MB: indentation whitespace is one string per document and
  the attribute values (code values such as account numbers and dates repeat) are shared per document.
- Children and attributes are exact-size arrays; there are no list objects any more (8.55 MB before batch 3).
- MicroDOM pays for a `LinkedHashMap` per element with attributes and a `StringBuilder` per text node; its element
  objects are smaller, but it has about twice as many objects.

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

The benchmark lives in the standalone Maven module `phoxtrot-xdm-memory-benchmark` of phoxtrot (not part of the reactor
build). It expects the W3C test suites in `../../git-thirdparty` and `peppol-commons` as a sibling of phoxtrot.

```bash
mvn install -pl phoxtrot-xdm -am -DskipTests
cd phoxtrot-xdm-memory-benchmark
mvn compile exec:exec
```

`run.sh` runs it on JDK 21, on JDK 25 and on JDK 25 with compact object headers, and can measure a phoxtrot-xdm jar that
was only packaged (e.g. of a branch), without installing it:

```bash
cd phoxtrot-xdm-memory-benchmark
# ph-xdm only: one table with a column per JVM, plus the instance size of each node class
sh run.sh xdm ../phoxtrot-xdm/target/phoxtrot-xdm-0.1.0-SNAPSHOT.jar
# The complete comparison of the three models, once per JVM
sh run.sh full
# The class footprint of the "Bank transactions" document, ph-xdm and MicroDOM (JDK 21)
sh run.sh footprint
```

Without a jar argument the phoxtrot-xdm jar of the local Maven repository is used.
