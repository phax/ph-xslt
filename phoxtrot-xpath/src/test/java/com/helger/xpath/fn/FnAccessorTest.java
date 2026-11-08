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
package com.helger.xpath.fn;

import static com.helger.xpath.fn.FnNodeTestHelper.assertError;
import static com.helger.xpath.fn.FnNodeTestHelper.eval;
import static com.helger.xpath.fn.FnNodeTestHelper.parse;
import static com.helger.xpath.fn.FnNodeTestHelper.str;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Test class for class {@link FnAccessor}.
 *
 * @author Philip Helger
 */
public final class FnAccessorTest
{
  private static final String XML = "<?pi data?><r xmlns:p='urn:p' a='1'><p:e p:b='2'>x<!--c--></p:e><e/>t<e>y</e></r>";

  @Test
  public void testStringAndData ()
  {
    final IXdmNode aDoc = parse (XML);
    assertEquals ("xty", str ("string()", aDoc));
    assertEquals ("1", str ("string(/r/@a)", aDoc));
    assertEquals ("", str ("string(())"));
    assertEquals ("1.5", str ("string(1.5)"));
    assertError ("XPDY0002", "string()");
    assertError ("FOTY0014", "string(string#1)");
    assertError ("XPTY0004", "string((1, 2))");

    final XdmSequence aData = eval ("data(/r/@a)", aDoc);
    assertEquals (EAtomicType.UNTYPED_ATOMIC, ((XdmAtomic) aData.get (0)).getType ());
    assertEquals ("1 2", str ("data((1, 2))"));
    assertEquals ("xty", str ("data()", aDoc));
    assertError ("XPDY0002", "data()");
    assertError ("FOTY0013", "data(string#1)");
  }

  @Test
  public void testNames ()
  {
    final IXdmNode aDoc = parse (XML);
    assertEquals ("p:e", str ("name(/r/*[1])", aDoc));
    assertEquals ("e", str ("local-name(/r/*[1])", aDoc));
    assertEquals ("urn:p", str ("namespace-uri(/r/*[1])", aDoc));
    assertEquals ("p:b", str ("name(/r/*[1]/@*)", aDoc));
    assertEquals ("pi", str ("name(/processing-instruction())", aDoc));
    assertEquals ("", str ("name(/r/text())", aDoc));
    assertEquals ("", str ("name(())"));
    assertEquals ("r", str ("/r/name()", aDoc));
    assertEquals ("p", str ("name(/r/namespace::p)", aDoc));
    assertEquals ("", str ("namespace-uri(/r/namespace::p)", aDoc));
    assertError ("XPTY0004", "name()", XdmAtomic.integer (1));
    assertError ("XPDY0002", "local-name()");

    final XdmSequence aNodeName = eval ("node-name(/r/*[1])", aDoc);
    assertEquals (new XdmQName ("urn:p", "e"), ((XdmAtomic) aNodeName.get (0)).getValue ());
    assertTrue (eval ("node-name(/)", aDoc).isEmpty ());
    assertTrue (eval ("node-name(/r/text())", aDoc).isEmpty ());
    assertTrue (eval ("node-name(())").isEmpty ());
  }

  @Test
  public void testNilledRootHasChildren ()
  {
    final IXdmNode aDoc = parse (XML);
    assertEquals ("false", str ("nilled(/r)", aDoc));
    assertTrue (eval ("nilled(/r/@a)", aDoc).isEmpty ());
    assertEquals ("true", str ("root(/r/e[1]) is /", aDoc));
    assertEquals ("true", str ("has-children(/r)", aDoc));
    assertEquals ("false", str ("has-children(/r/e[1])", aDoc));
    assertEquals ("false", str ("has-children(())"));
    assertEquals ("true", str ("has-children()", aDoc));
  }

  @Test
  public void testPath ()
  {
    final IXdmNode aDoc = parse (XML);
    assertEquals ("/", str ("path(/)", aDoc));
    assertEquals ("/Q{}r[1]/Q{urn:p}e[1]", str ("path(/r/*[1])", aDoc));
    assertEquals ("/Q{}r[1]/Q{}e[2]", str ("path(/r/e[2])", aDoc));
    assertEquals ("/Q{}r[1]/@a", str ("path(/r/@a)", aDoc));
    assertEquals ("/Q{}r[1]/Q{urn:p}e[1]/@Q{urn:p}b", str ("path(/r/*[1]/@*)", aDoc));
    assertEquals ("/Q{}r[1]/text()[1]", str ("path(/r/text())", aDoc));
    assertEquals ("/Q{}r[1]/Q{urn:p}e[1]/comment()[1]", str ("path(//comment())", aDoc));
    assertEquals ("/processing-instruction(pi)[1]", str ("path(/processing-instruction())", aDoc));
    assertEquals ("/Q{}r[1]/namespace::p", str ("path(/r/namespace::p)", aDoc));
    assertTrue (eval ("path(())").isEmpty ());

    // A tree that is not rooted in a document node
    final IXdmNode aElement = IXdmNode.createElement (new XdmQName ("a"));
    final IXdmNode aChild = IXdmNode.createElement (new XdmQName ("b"));
    aElement.appendChild (aChild);
    assertEquals ("Q{http://www.w3.org/2005/xpath-functions}root()", FnAccessor.getPath (aElement));
    assertEquals ("Q{http://www.w3.org/2005/xpath-functions}root()/Q{}b[1]", FnAccessor.getPath (aChild));
  }

  @Test
  public void testGenerateId ()
  {
    final IXdmNode aDoc = parse (XML);
    assertEquals ("", str ("generate-id(())"));
    assertEquals ("true", str ("generate-id(/r) = generate-id(/r)", aDoc));
    assertEquals ("false", str ("generate-id(/r) = generate-id(/r/e[1])", aDoc));
    final String sID = str ("generate-id(/r/@a)", aDoc);
    assertTrue (sID, sID.matches ("[A-Za-z][A-Za-z0-9]*"));
    assertNotEquals (str ("generate-id(/r)", aDoc), "");
  }

  @Test
  public void testBaseAndDocumentUri ()
  {
    final IXdmNode aDoc = parse ("<r xml:base='http://example.org/dir/'><a xml:base='sub/'><b/></a><c xml:base='http://other.org/x'/></r>");
    assertTrue (eval ("base-uri(/)", aDoc).isEmpty ());
    assertEquals ("http://example.org/dir/", str ("base-uri(/r)", aDoc));
    assertEquals ("http://example.org/dir/sub/", str ("base-uri(//b)", aDoc));
    assertEquals ("http://other.org/x", str ("base-uri(//c)", aDoc));
    assertEquals ("http://example.org/dir/", str ("base-uri(/r/@xml:base)", aDoc));
    assertTrue (eval ("base-uri(/r/namespace::xml)", aDoc).isEmpty ());
    assertTrue (eval ("document-uri(/)", aDoc).isEmpty ());

    aDoc.setDocumentUri ("http://example.org/doc.xml");
    final IXdmNode aDoc2 = parse ("<r><a xml:base='x/'/></r>");
    aDoc2.setDocumentUri ("http://example.org/base/doc.xml");
    assertEquals ("http://example.org/doc.xml", str ("document-uri(/)", aDoc));
    assertTrue (eval ("document-uri(/r)", aDoc).isEmpty ());
    assertEquals ("http://example.org/base/doc.xml", str ("base-uri()", aDoc2));
    assertEquals ("http://example.org/base/x/", str ("base-uri(//a)", aDoc2));

    // A parentless attribute has no base URI
    assertEquals (null, FnAccessor.getBaseUri (IXdmNode.createAttribute (new XdmQName ("a"), "v")));
    assertError ("XPTY0004", "base-uri()", XdmAtomic.integer (1));
  }
}
