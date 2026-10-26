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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;

/**
 * Validation of an existing tree in place must reach the same verdict as
 * validating the text, and both must agree with the specification (go-xslt
 * <code>bridge_spec_test.go</code>). The instance table of the composite
 * prelude runs through both paths as whole documents.
 *
 * @author Philip Helger
 */
public final class XsdBridgeSpecTest
{
  private static final String XSI = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

  private record Inst (String instance, boolean valid)
  {}

  private static final Inst [] CASES = { new Inst ("<eBase><a>x</a></eBase>", true),
                                         new Inst ("<eBase><b>x</b></eBase>", false),
                                         new Inst ("<eBase" + XSI + " xsi:type=\"Ext\"><a>x</a><b>1</b></eBase>", true),
                                         new Inst ("<eBlocked" + XSI + " xsi:type=\"Ext\"><a>x</a><b>1</b></eBlocked>", false),
                                         new Inst ("<eAbsType" + XSI + " xsi:type=\"Conc\"/>", true),
                                         new Inst ("<eAbs><a>x</a></eAbs>", false),
                                         new Inst ("<eNil" + XSI + " xsi:nil=\"true\"/>", true),
                                         new Inst ("<eNil" + XSI + " xsi:nil=\"true\">3</eNil>", false),
                                         new Inst ("<eDef/>", true),
                                         new Inst ("<eFix>y</eFix>", false),
                                         new Inst ("<eMixed>t<a>x</a></eMixed>", true),
                                         new Inst ("<eEmpty>x</eEmpty>", false),
                                         new Inst ("<eSC u=\"1\">5</eSC>", true),
                                         new Inst ("<eAttrs req=\"1\"/>", true),
                                         new Inst ("<eAttrs/>", false),
                                         new Inst ("<eStrict><eDef>7</eDef></eStrict>", true),
                                         new Inst ("<eStrict><zz/></eStrict>", false),
                                         new Inst ("<eLax><zz/></eLax>", true),
                                         new Inst ("<eIDs><item id=\"a\"/><item ref=\"a\"/></eIDs>", true),
                                         new Inst ("<eIDs><item id=\"a\"/><item id=\"a\"/></eIDs>", false),
                                         new Inst ("<eIDs><item ref=\"b\"/></eIDs>", false),
                                         new Inst ("<eHold><m><a>x</a><b>1</b></m></eHold>", true),
                                         new Inst ("<eHold><mb><a>x</a></mb></eHold>", false),
                                         new Inst ("<eChoice><p/><q/></eChoice>", true),
                                         new Inst ("<eAll><q/></eAll>", false),
                                         new Inst ("<eUnion>2020-01-01</eUnion>", true) };

  @NonNull
  private static IXdmSchemaSet _compile ()
  {
    return XsdSchemaProvider.createSchemaSet (XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (XsdInstanceValidationTest.PRELUDE,
                                                                                                                 null)),
                                                                 EXsdVersion.V10,
                                                                 null));
  }

  @NonNull
  private static XdmNode _parseElement (@NonNull final String sXml)
  {
    try
    {
      final XdmNode aRoot = XsdNodes.rootElement (new XdmParser ().parse (sXml));
      assertNotNull (aRoot);
      return aRoot;
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalStateException (ex);
    }
  }

  @Test
  public void testAgreesWithValidateText ()
  {
    final XsdSchema aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (XsdInstanceValidationTest.PRELUDE,
                                                                                                  null)),
                                                 EXsdVersion.V10,
                                                 null);
    final IXdmSchemaSet aSet = XsdSchemaProvider.createSchemaSet (aSchema);
    for (final Inst aInst : CASES)
    {
      assertEquals (aInst.instance (), aInst.valid (), aSchema.validateText (aInst.instance ()).valid ());
      boolean bValid = true;
      try
      {
        aSet.validateDocument (new XdmParser ().parse (aInst.instance ()));
      }
      catch (final XdmParseException ex)
      {
        bValid = ex.getClass () != XdmValidationException.class;
      }
      assertEquals (aInst.instance (), aInst.valid (), bValid);
    }
  }

  /** After a successful validation the tree carries the PSVI properties. */
  @Test
  public void testPSVIProperties () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ();
    final XdmNode aEl = _parseElement ("<eAttrs req=\"1\"/>");
    aSet.validateElement (aEl, null, false);
    assertEquals ("d", aEl.getAttribute ("", "opt").getValue ());
    assertEquals ("f", aEl.getAttribute ("", "fix").getValue ());
    assertTrue (aEl.getAttribute ("", "req").getSchemaType ().hasAtomicBase ("int"));
    assertTrue (aEl.getAttribute ("", "opt").getSchemaType ().hasAtomicBase ("string"));

    final XdmNode aNil = _parseElement ("<eNil" + XSI + " xsi:nil=\"true\"/>");
    aSet.validateElement (aNil, null, false);
    assertTrue (aNil.isNilled ());

    final XdmNode aIDs = _parseElement ("<eIDs><item id=\"a\" refs=\"a a\"/></eIDs>");
    aSet.validateElement (aIDs, null, true);
    final XdmNode aItem = aIDs.getChildAtIndex (0);
    assertEquals (EIDKind.ID, aItem.getAttribute ("", "id").getIDKind ());
    assertEquals (EIDKind.IDREFS, aItem.getAttribute ("", "refs").getIDKind ());
    assertTrue (aItem.getAttribute ("", "refs").getSchemaType ().isList ());
  }

  /**
   * ID and IDREF are document-level: an element-rooted episode does not
   * resolve IDREFs to IDs outside it, a document-rooted one does.
   */
  @Test
  public void testIDScope () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ();
    final String sInstance = "<eIDs><item ref=\"nowhere\"/></eIDs>";
    aSet.validateElement (_parseElement (sInstance), null, false);
    try
    {
      aSet.validateElement (_parseElement (sInstance), null, true);
      fail ("a document episode accepted a dangling IDREF");
    }
    catch (final XdmValidationException ex)
    {
      assertTrue (ex.isDocumentConstraint ());
    }
  }

  /** Whitespace text is dropped from element-only content, and only there. */
  @Test
  public void testStripsElementOnlyWhitespace () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ();
    final XdmNode aEl = _parseElement ("<eBase>\n  <a> x </a>\n</eBase>");
    aSet.validateElement (aEl, null, false);
    assertEquals (1, aEl.getChildCount ());
    assertTrue (aEl.getChildAtIndex (0).isElement ());
    assertEquals (" x ", aEl.getChildAtIndex (0).getChildAtIndex (0).getValue ());
    final XdmNode aMixed = _parseElement ("<eMixed>\n  <a>x</a>\n</eMixed>");
    aSet.validateElement (aMixed, null, false);
    assertEquals (3, aMixed.getChildCount ());
  }

  @Test
  public void testNotAssessedBelowSkip () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ();
    final XdmNode aEl = _parseElement ("<eSkip><eDef>x</eDef></eSkip>");
    aSet.validateElement (aEl, null, false);
    final XdmNode aChild = aEl.getChildAtIndex (0);
    assertNull (aChild.getSchemaType ());
    assertEquals ("anyType", aChild.getTypeAnnotation ().localName ());
  }

  @Test
  public void testDocumentWithoutDocumentElement ()
  {
    try
    {
      _compile ().validateDocument (XdmNode.createDocument ());
      fail ("no document element");
    }
    catch (final IllegalArgumentException | XdmValidationException ex)
    {
      assertTrue (ex instanceof IllegalArgumentException);
    }
  }

  /**
   * An element no declaration governs is laxly assessed, and its attributes
   * with a global declaration are validated: a duplicate xml:id is an error.
   */
  @Test
  public void testLaxAssessedAttributes () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = XsdSchemaProvider.createSchemaSet (XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText ("""
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="http://www.w3.org/XML/1998/namespace">
  <xs:attribute name="id" type="xs:ID"/>
</xs:schema>""", null)), EXsdVersion.V10, null));
    final String sHead = "<doc xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:anyType\">";
    aSet.validateElement (_parseElement (sHead + "<e xml:id=\"a\"/><f xml:id=\"b\"><g xml:id=\"c\"/></f></doc>"), null, true);
    try
    {
      aSet.validateElement (_parseElement (sHead + "<e xml:id=\"a\"/><f><g xml:id=\"a\"/></f></doc>"), null, true);
      fail ("a duplicate xml:id");
    }
    catch (final XdmValidationException ex)
    {
      assertTrue (ex.isDocumentConstraint ());
    }
    final XdmNode aValid = _parseElement (sHead + "<e xml:id=\"a\"/></doc>");
    aSet.validateElement (aValid, null, true);
    assertEquals (EIDKind.ID, aValid.getChildAtIndex (0).getAttribute ("http://www.w3.org/XML/1998/namespace", "id").getIDKind ());
  }
}
