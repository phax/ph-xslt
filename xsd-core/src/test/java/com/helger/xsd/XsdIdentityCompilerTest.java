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
package com.helger.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * The compile-time half of identity constraints: the selector/field XPath
 * subset of XSD Part 1 section 3.11.6 (go-xslt
 * <code>TestIdentityXPathSubset</code>) and the constraint cross-checks.
 *
 * @author Philip Helger
 */
public final class XsdIdentityCompilerTest
{
  private static final String [] [] CASES = { { "a", ".", "true" },
                                              { "a/b", "@x", "true" },
                                              { ".//a", "c/@x", "true" },
                                              { "a | .//b", "@x | c", "true" },
                                              { "p:a/p:*", "@p:x", "true" },
                                              { "child::a", "attribute::x", "true" },
                                              { " a ", " @x ", "true" },
                                              { "*", "@*", "true" },
                                              { "/a", ".", "false" },
                                              { "//a", ".", "false" },
                                              { "a[1]", ".", "false" },
                                              { "a/..", ".", "false" },
                                              { "@x", ".", "false" },
                                              { "a/@x", ".", "false" },
                                              { "a", "@x/b", "false" },
                                              { "a", "text()", "false" },
                                              { "a", "q:x", "false" },
                                              { "a", "", "false" },
                                              { "descendant::a", ".", "false" },
                                              { "a", "./@x/.", "false" },
                                              { "a|", ".", "false" } };

  private static String _schema (final String sSelector, final String sField)
  {
    return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:p=\"urn:p\">\n" +
           "  <xs:element name=\"r\"><xs:complexType><xs:sequence><xs:any processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
           "  <xs:unique name=\"u\"><xs:selector xpath=\"" +
           sSelector +
           "\"/><xs:field xpath=\"" +
           sField +
           "\"/></xs:unique></xs:element></xs:schema>";
  }

  private static boolean _isValidXPath (final String sExpr, final boolean bField, final XdmNode aNode)
  {
    try
    {
      XsdIdentityCompiler.validateIdentityXPath (sExpr, bField, aNode);
      return true;
    }
    catch (final XsdInvalidException ex)
    {
      return false;
    }
  }

  @Test
  public void testXPathSubsetGrammar () throws XdmParseException
  {
    final XdmNode aDoc = new XdmParser ().parse ("<x xmlns:p=\"urn:p\"/>");
    final XdmNode aNode = XsdNodes.rootElement (aDoc);
    for (final String [] aCase : CASES)
    {
      final boolean bExpected = Boolean.parseBoolean (aCase[2]);
      final boolean bValid = _isValidXPath (aCase[0], false, aNode) && _isValidXPath (aCase[1], true, aNode);
      assertEquals (aCase[0] + " ; " + aCase[1], Boolean.valueOf (bExpected), Boolean.valueOf (bValid));
    }
  }

  @Test
  public void testXPathSubsetInSchema ()
  {
    int nDecided = 0;
    for (final String [] aCase : CASES)
      for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
      {
        final Compiled aCompiled = XsdTestSupport.compile (_schema (aCase[0], aCase[1]), null, eVersion);
        if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
          continue;
        nDecided++;
        final boolean bExpected = Boolean.parseBoolean (aCase[2]);
        assertEquals (aCase[0] + " ; " + aCase[1] + "/" + eVersion.getID () + ": " + aCompiled.message (),
                      Boolean.valueOf (bExpected),
                      Boolean.valueOf (aCompiled.verdict () == EVerdict.VALID));
      }
    // Every invalid form is rejected by the loader itself
    assertTrue (nDecided >= 26);
  }

  @Test
  public void testCompiledConstraints ()
  {
    // No complex type: the loader alone decides this schema
    final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:t=\"urn:t\" targetNamespace=\"urn:t\">\n" +
                           "  <xs:element name=\"e\">\n" +
                           "    <xs:key name=\"k\"><xs:selector xpath=\"t:a\"/><xs:field xpath=\"@id\"/></xs:key>\n" +
                           "    <xs:keyref name=\"r\" refer=\"t:k\"><xs:selector xpath=\".//t:b\"/><xs:field xpath=\"@ref\"/></xs:keyref>\n" +
                           "  </xs:element>\n" +
                           "</xs:schema>";
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    final XsdElementDecl aDecl = aCompiled.schema ().m_aElements.get (new XsdName ("urn:t", "e"));
    assertEquals (2, aDecl.m_aConstraints.size ());
    final XsdIdentityConstraint aKey = aDecl.m_aConstraints.get (0);
    assertEquals ("key", aKey.m_sKind);
    assertEquals (new XsdName ("urn:t", "k"), aKey.m_aName);
    assertEquals ("t:a", aKey.m_sSelectorSrc);
    assertEquals ("[@id]", aKey.m_aFieldRaw.toString ());
    final XsdIdentityConstraint aRef = aDecl.m_aConstraints.get (1);
    assertEquals (new XsdName ("urn:t", "k"), aRef.m_aRefer);
    assertNotSame (aKey.m_aSelector, aRef.m_aSelector);
    // A keyref must refer to a key or unique with as many fields
    final Compiled aBad = XsdTestSupport.compile (sSchema.replace ("refer=\"t:k\"", "refer=\"t:r\""), null, EXsdVersion.V10);
    assertSame (EVerdict.INVALID, aBad.verdict ());
    // The XSD 1.1 @ref form is a 1.0 schema error
    final Compiled aRefForm = XsdTestSupport.compile (sSchema.replace ("<xs:keyref name=\"r\" refer=\"t:k\"><xs:selector xpath=\".//t:b\"/><xs:field xpath=\"@ref\"/></xs:keyref>",
                                                                       "<xs:key ref=\"t:k\"/>"),
                                                      null,
                                                      EXsdVersion.V10);
    assertSame (EVerdict.INVALID, aRefForm.verdict ());
  }
}
