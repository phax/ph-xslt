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
package com.helger.xslt.spec;

import static com.helger.xslt.spec.XsltSpecTestHelper.err;
import static com.helger.xslt.spec.XsltSpecTestHelper.ok;
import static com.helger.xslt.spec.XsltSpecTestHelper.specRunAll;

import org.junit.Test;

/**
 * The function conversion rules (XPath 3.1 section 3.1.5.2) reach a call from a stylesheet: the F&amp;O built-ins and
 * the XSLT-defined functions of the standard function namespace (XSLT 3.0 sections 18, 20) alike. Port of go-xslt
 * <code>internal/xslt/functions_args_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class FunctionsArgsSpecTest
{
  private static final String SRC = "<doc><i>a</i><i>b</i></doc>";

  private static String _val (final String sSelect)
  {
    return "<xsl:key name=\"k\" match=\"i\" use=\".\"/>\n" +
           "<xsl:template match=\"/\"><xsl:value-of select=\"" +
           sSelect +
           "\"/></xsl:template>";
  }

  @Test
  public void testFunctionArgumentConversion ()
  {
    specRunAll (
      // F&O built-ins
      err ("upper-case of an integer", _val ("upper-case(1)"), SRC, "XPTY0004"),
      err ("empty collation", _val ("contains-token('a b', 'a', ())"), SRC, "XPTY0004"),
      err ("two nodes for xs:string?", _val ("string-length(doc/i)"), SRC, "XPTY0004"),
      ok ("untyped node cast to xs:double", _val ("substring('abc', count(doc/i))"), SRC, "bc"),
      err ("untyped node cast, bad lexical", _val ("abs(doc/i[1])"), SRC, "FORG0001"),
      ok ("node for xs:string", _val ("upper-case(doc/i[1])"), SRC, "A"),
      ok ("empty for xs:string?", _val ("string-length(doc/none)"), SRC, "0"),
      // XSLT-defined functions
      err ("system-property of a number", _val ("system-property(1)"), SRC, "XPTY0004"),
      err ("system-property of nothing", _val ("system-property(())"), SRC, "XPTY0004"),
      ok ("system-property", _val ("system-property('xsl:version')"), SRC, "3.0"),
      err ("function-available of a number", _val ("function-available(1)"), SRC, "XPTY0004"),
      ok ("function-available arity cast from untyped",
          _val ("function-available('upper-case', xs:untypedAtomic('1'))"),
          SRC,
          "true"),
      err ("function-available arity string", _val ("function-available('upper-case', '1')"), SRC, "XPTY0004"),
      err ("element-available of nothing", _val ("element-available(())"), SRC, "XPTY0004"),
      ok ("element-available", _val ("element-available('xsl:value-of')"), SRC, "true"),
      err ("type-available of a number", _val ("type-available(1)"), SRC, "XPTY0004"),
      err ("key name of a number", _val ("key(1, 'a')"), SRC, "XPTY0004"),
      err ("key top node of a string", _val ("key('k', 'a', 'x')"), SRC, "XPTY0004"),
      err ("key value of a map", _val ("key('k', map{})"), SRC, "FOTY0013"),
      ok ("key", _val ("key('k', 'a')"), SRC, "a"),
      ok ("key with a top node", _val ("key('k', 'b', /)"), SRC, "b"),
      ok ("key values from nodes", _val ("string-join(key('k', doc/i), '')"), SRC, "ab"),
      err ("regex-group of a string", _val ("regex-group('1')"), SRC, "XPTY0004"),
      err ("unparsed-entity-uri of a number", _val ("unparsed-entity-uri(1)"), SRC, "XPTY0004"),
      err ("unparsed-entity-uri doc argument", _val ("unparsed-entity-uri('x', 'y')"), SRC, "XPTY0004"),
      err ("accumulator-before of a number", _val ("accumulator-before(1)"), SRC, "XPTY0004"),
      err ("current-merge-group of a number", _val ("current-merge-group(1)"), SRC, "XPTY0004"),
      err ("stream-available of two uris", _val ("stream-available(('a', 'b'))"), SRC, "XPTY0004"),
      err ("document base node of a string", _val ("document('x.xml', 'y')"), SRC, "XPTY0004"),
      err ("generate-id of an atomic", _val ("generate-id(1)"), SRC, "XPTY0004"),
      ok ("generate-id", _val ("string-length(generate-id(doc/i[1])) gt 0"), SRC, "true"),
      ok ("copy-of any item", _val ("copy-of('a')"), SRC, "a"),
      err ("current-group takes no argument", _val ("current-group(1)"), SRC, "XPST0017"),
      // A named reference and a dynamic call are checked too
      err ("system-property#1 dynamic call", _val ("system-property#1(1)"), SRC, "XPTY0004"),
      err ("upper-case#1 dynamic call", _val ("let $f := upper-case#1 return $f(1)"), SRC, "XPTY0004"));
  }
}
