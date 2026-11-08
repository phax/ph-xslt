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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.CompiledXPath;
import com.helger.phoxtrot.xpath.XPathCompiler;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.ops.Atomizer;

/**
 * Test the constructor functions of user-defined simple types (XPath 3.1
 * section 3.14.4) with a schema set of {@link XsdSchemaProvider}.
 *
 * @author Philip Helger
 */
public final class XsdUserTypeConstructorTest
{
  private static final String SCHEMA = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t">
        <xs:simpleType name="small"><xs:restriction base="xs:integer"><xs:maxInclusive value="9"/></xs:restriction></xs:simpleType>
        <xs:simpleType name="intList"><xs:list itemType="xs:integer"/></xs:simpleType>
      </xs:schema>""";

  private static final IXdmSchemaSet SCHEMA_SET = XsdSchemaProvider.createSchemaSet (XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (SCHEMA,
                                                                                                                                                         null)),
                                                                                                        EXsdVersion.V10,
                                                                                                        null));

  @NonNull
  private static String _eval (@NonNull final String sExpr)
  {
    final StaticContext aStatic = new StaticContext ();
    aStatic.declareNamespace ("t", "urn:t");
    aStatic.setSchemaSet (SCHEMA_SET);
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aStatic);
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aCompiled.evaluate (aCompiled.createDynamicContext ()))
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (Atomizer.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @Test
  public void testCallAndReference ()
  {
    assertEquals ("7 true", _eval ("let $v := t:small('7') return ($v, $v instance of xs:integer)"));
    assertEquals ("7", _eval ("t:small#1('7')"));
    assertEquals ("7", _eval ("'7' => t:small()"));
  }

  @Test
  public void testPartialApplication ()
  {
    // A placeholder argument makes a partial application (XPath 3.1 section
    // 3.1.5.1), not a cast of the placeholder
    assertEquals ("7 true", _eval ("let $v := t:small(?)('7') return ($v, $v instance of xs:integer)"));
    assertEquals ("1 2 3", _eval ("('1', '2', '3') ! t:small(?)(.)"));
    assertEquals ("1 2", _eval ("t:intList(?)('1 2')"));
    assertEquals ("1", _eval ("function-arity(t:small(?))"));
    try
    {
      _eval ("t:small(?)('12')");
      fail ("12 is above maxInclusive");
    }
    catch (final XPathException ex)
    {
      assertEquals ("FORG0001", ex.getCode ());
    }
  }
}
