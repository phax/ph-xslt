/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.run;

import org.junit.Test;

/**
 * Port of go-xslt <code>internal/xpath/fn_seq_spec_test.go</code> (commit 04ccada).
 *
 * @author Philip Helger
 */
public final class FnSeqSpecTest
{
  /**
   * The $at document of the F&O 3.1 §14.2.3 fn:deep-equal examples, plus a few
   * siblings for the node-kind cases.
   */
  private static final String SQ_ATTENDEES = "<doc><attendees><name last='Parker' first='Peter'/><name last='Barker' first='Bob'/><name last='Parker' first='Peter'/></attendees>" +
                                              "<e a=\"1\" b=\"2\"><c>t</c></e><e b=\"2\" a=\"1\"><c>t</c></e><e a=\"1\"><c>t</c></e><e a=\"1\" b=\"3\"><c>t</c></e>" +
                                              "<f xmlns=\"urn:x\"/><f/><g>text</g><g><h/></g><k A=\"x\"/><k A=\"X\"/>" +
                                              "<!--c1--><!--c1--><!--c2--><?p one?><?p one?><?q one?><?p two?></doc>";

  /**
   * F&O 3.1 §14.2.3 fn:deep-equal.
   */
  @Test
  public void seqSpecDeepEqual ()
  {
    run (SQ_ATTENDEES,
      // The REC examples.
      ok ("let $at := //attendees return deep-equal($at, $at/*)", "false"),
      ok ("let $at := //attendees return deep-equal($at/name[1], $at/name[2])", "false"),
      ok ("let $at := //attendees return deep-equal($at/name[1], $at/name[3])", "true"),
      ok ("let $at := //attendees return deep-equal($at/name[1], 'Peter Parker')", "false"),
      ok ("deep-equal(map{1:'a', 2:'b'}, map{2:'b', 1:'a'})", "true"),
      ok ("deep-equal([1, 2, 3], [1, 2, 3])", "true"),
      ok ("deep-equal((1, 2, 3), [1, 2, 3])", "false"),
      // Notes: items of different kinds compare false, not an error.
      ok ("deep-equal(1, current-dateTime())", "false"),
      ok ("deep-equal(map{}, [])", "false"),
      ok ("deep-equal([], map{})", "false"),
      ok ("deep-equal(1, map{})", "false"),
      ok ("deep-equal(1, [1])", "false"),
      ok ("deep-equal(//e[1], 1)", "false"),

      // Atomic values: eq semantics, except that NaN is deep-equal to NaN.
      ok ("deep-equal(xs:double('NaN'), xs:float('NaN'))", "true"),
      ok ("deep-equal(1, 1.0)", "true"),
      ok ("deep-equal(xs:untypedAtomic('a'), 'a')", "true"),
      ok ("deep-equal(xs:double('NaN'), 1)", "false"),

      // Maps: same keys, values deep-equal; size matters.
      ok ("deep-equal(map{1:'a'}, map{1:'a', 2:'b'})", "false"),
      ok ("deep-equal(map{1:'a'}, map{2:'a'})", "false"),
      ok ("deep-equal(map{1:('a','b')}, map{1:('a','b')})", "true"),
      ok ("deep-equal(map{1:('a','b')}, map{1:('b','a')})", "false"),
      // Arrays: size and members in order.
      ok ("deep-equal([1, 2], [1, 2, 3])", "false"),
      ok ("deep-equal([1, 2], [2, 1])", "false"),
      ok ("deep-equal([(1,2), 3], [(1,2), 3])", "true"),

      // Element nodes: name, attributes (any order), children.
      ok ("deep-equal(//e[1], //e[2])", "true"),
      ok ("deep-equal(//e[1], //e[3])", "false"),
      ok ("deep-equal(//e[1], //e[4])", "false"),
      ok ("deep-equal(//f[1], //f[2])", "false"),
      ok ("deep-equal(//g[1], //g[2])", "false"),
      ok ("deep-equal(//e[1], //g[1])", "false"),
      ok ("deep-equal(//e[1]/c, //e[2]/c)", "true"),
      // Document nodes.
      ok ("deep-equal(/, /)", "true"),
      ok ("deep-equal(/, /doc)", "false"),
      // Attribute nodes: name and typed value.
      ok ("deep-equal(//e[1]/@a, //e[2]/@a)", "true"),
      ok ("deep-equal(//e[1]/@a, //e[1]/@b)", "false"),
      ok ("deep-equal(//e[1]/@b, //e[4]/@b)", "false"),
      // Text, comment and PI nodes as items of the sequences.
      ok ("deep-equal(//g[1]/text(), //c[1]/text())", "false"),
      ok ("deep-equal(//e[1]/c/text(), //e[2]/c/text())", "true"),
      ok ("deep-equal(/doc/comment()[1], /doc/comment()[2])", "true"),
      ok ("deep-equal(/doc/comment()[1], /doc/comment()[3])", "false"),
      ok ("deep-equal(/doc/processing-instruction()[1], /doc/processing-instruction()[2])", "true"),
      ok ("deep-equal(/doc/processing-instruction()[1], /doc/processing-instruction()[3])", "false"),
      ok ("deep-equal(/doc/processing-instruction()[1], /doc/processing-instruction()[4])", "false"),
      ok ("deep-equal(/doc/comment()[1], //c[1]/text())", "false"),
      // Nodes of different kinds.
      ok ("deep-equal(//e[1]/@a, //e[1]/c)", "false"),

      // The collation governs string comparisons, attribute values included.
      ok ("deep-equal('a', 'A', 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive')", "true"),
      ok ("deep-equal('a', 'A', 'http://www.w3.org/2005/xpath-functions/collation/codepoint')", "false"),
      ok ("deep-equal(//k[1], //k[2])", "false"),
      ok ("deep-equal(//k[1], //k[2], 'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive')", "true"),
      ok ("deep-equal(('a', 1), ('A', 1), 'http://www.w3.org/2013/collation/UCA?strength=secondary')", "true")
    );
    run (SQ_ATTENDEES,
      // FOTY0015: a function item (not a map or array) anywhere in the input.
      err ("deep-equal(abs#1, abs#1)", "FOTY0015"),
      err ("deep-equal(1, abs#1)", "FOTY0015"),
      err ("deep-equal([abs#1], [1])", "FOTY0015"),
      err ("deep-equal(map{1:abs#1}, map{1:1})", "FOTY0015"),
      err ("deep-equal(1, 1, 'http://example.com/no-such-collation')", "FOCH0002")
    );
  }

  /**
   * The REC's §14.2.3 rules compare element children as $i1/(*|text()) (or
   * $i1/* for element-only content): comments and processing instructions
   * among the children do not take part ("The contents of comments and
   * processing instructions are significant only if these nodes appear directly
   * as items in the two sequences being compared").
   */
  @Test
  public void seqSpecDeepEqualIgnoresCommentChildren ()
  {
    run ("<r><a><b/><!--x--><b/></a><a><b/><b/></a><a><b/><?pi?><b/></a></r>",
      ok ("deep-equal(//a[1], //a[2])", "true"),
      ok ("deep-equal(//a[3], //a[2])", "true")
    );
  }

  /**
   * F&O 3.1 §14.1: insert-before, remove, unordered, and the cardinality
   * functions of §14.3.
   */
  @Test
  public void seqSpecPositionalAndCardinality ()
  {
    run ("<r><p>2</p><q>x</q></r>",
      // §14.1.4 examples, $abc := ("a", "b", "c").
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), 0, \"z\"), \",\")", "z,a,b,c"),
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), 1, \"z\"), \",\")", "z,a,b,c"),
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), 2, \"z\"), \",\")", "a,z,b,c"),
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), 3, \"z\"), \",\")", "a,b,z,c"),
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), 4, \"z\"), \",\")", "a,b,c,z"),
      ok ("string-join(insert-before((), 3, (\"x\", \"y\")), \",\")", "x,y"),
      ok ("string-join(insert-before((\"a\", \"b\"), 2, ()), \",\")", "a,b"),
      // A node or untypedAtomic position is cast to xs:integer.
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), //p, \"z\"), \",\")", "a,z,b,c"),
      ok ("string-join(insert-before((\"a\", \"b\", \"c\"), xs:untypedAtomic('3'), \"z\"), \",\")", "a,b,z,c"),
      // Any integer subtype is acceptable.
      ok ("string-join(insert-before((\"a\", \"b\"), xs:byte(2), \"z\"), \",\")", "a,z,b"),

      // §14.1.5 examples.
      ok ("string-join(remove((\"a\", \"b\", \"c\"), 0), \",\")", "a,b,c"),
      ok ("string-join(remove((\"a\", \"b\", \"c\"), 1), \",\")", "b,c"),
      ok ("string-join(remove((\"a\", \"b\", \"c\"), 6), \",\")", "a,b,c"),
      ok ("count(remove((), 3))", "0"),
      ok ("string-join(remove((\"a\", \"b\", \"c\"), //p), \",\")", "a,c"),
      ok ("string-join(remove((\"a\", \"b\", \"c\"), xs:untypedAtomic('3')), \",\")", "a,b"),

      // §14.1.8 fn:unordered: some permutation of the input.
      ok ("count(unordered((1, 2, 3)))", "3"),
      ok ("sum(unordered((1, 2, 3)))", "6"),

      // §14.3.
      ok ("count(zero-or-one(()))", "0"),
      ok ("zero-or-one(4)", "4"),
      ok ("count(one-or-more((1, 2)))", "2"),
      ok ("exactly-one(//p)", "2")
    );
    run ("<r><p>2</p><q>x</q></r>",
      err ("zero-or-one((1, 2))", "FORG0003"),
      err ("one-or-more(())", "FORG0004"),
      err ("exactly-one(())", "FORG0005"),
      err ("exactly-one((1, 2))", "FORG0005"),
      // $position as xs:integer: no decimal/double/string, exactly one.
      err ("insert-before((1), (), 2)", "XPTY0004"),
      err ("insert-before((1), (1, 2), 2)", "XPTY0004"),
      err ("insert-before((1), 1.0, 2)", "XPTY0004"),
      err ("insert-before((1), 1e0, 2)", "XPTY0004"),
      err ("insert-before((1), '1', 2)", "XPTY0004"),
      err ("remove((1), 1.0)", "XPTY0004"),
      err ("remove((1), \"1\")", "XPTY0004"),
      err ("remove((1), ())", "XPTY0004"),
      // A node whose value is not an integer: the cast fails.
      err ("remove((1), //q)", "FORG0001"),
      err ("insert-before((1), //q, 2)", "FORG0001"),
      err ("insert-before((1), xs:untypedAtomic('x'), 2)", "FORG0001")
    );
  }
}
