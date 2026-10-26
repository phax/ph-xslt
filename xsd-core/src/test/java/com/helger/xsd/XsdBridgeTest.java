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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.xdm.EIDKind;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xpath.xdm.XdmSchemaType;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.EXdmContentType;
import com.helger.xpath.xdm.schema.EXdmSimpleVariety;
import com.helger.xpath.xdm.schema.IXdmElementDeclaration;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.IXdmTypeDefinition;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmTypedToken;

/**
 * The bridge (go-xslt <code>bridge_test.go</code>): component lookup,
 * derivation queries and validation of an existing tree in place. A
 * validation leaves the tree alone if it fails.
 *
 * @author Philip Helger
 */
public final class XsdBridgeTest
{
  private static final String NS = "urn:t";

  private static final String SCHEMA = """
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:t="urn:t" targetNamespace="urn:t" elementFormDefault="qualified">
  <xs:simpleType name="Zip">
    <xs:restriction base="xs:string"><xs:pattern value="[0-9]{5}"/></xs:restriction>
  </xs:simpleType>
  <xs:simpleType name="Zip5"><xs:restriction base="t:Zip"/></xs:simpleType>
  <xs:complexType name="Addr">
    <xs:sequence>
      <xs:element name="zip" type="t:Zip"/>
      <xs:element name="n" type="xs:int" minOccurs="0"/>
    </xs:sequence>
    <xs:attribute name="kind" type="t:Zip"/>
  </xs:complexType>
  <xs:complexType name="FullAddr">
    <xs:complexContent>
      <xs:extension base="t:Addr">
        <xs:sequence><xs:element name="c" type="xs:string"/></xs:sequence>
      </xs:extension>
    </xs:complexContent>
  </xs:complexType>
  <xs:element name="head" type="t:Addr"/>
  <xs:element name="sub" type="t:FullAddr" substitutionGroup="t:head"/>
  <xs:element name="anon"><xs:complexType><xs:sequence/></xs:complexType></xs:element>
  <xs:attribute name="gkind" type="t:Zip"/>
</xs:schema>""";

  @Rule
  public final TemporaryFolder m_aTempDir = new TemporaryFolder ();

  @NonNull
  private static IXdmSchemaSet _compile (@NonNull final String sSchema)
  {
    return XsdSchemaProvider.createSchemaSet (XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema,
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

  @NonNull
  private static XdmQName _qn (@NonNull final String sNamespace, @NonNull final String sLocal)
  {
    return new XdmQName (sNamespace, sLocal);
  }

  @NonNull
  private static IXdmTypeDefinition _type (@NonNull final IXdmSchemaSet aSet, @NonNull final String sNamespace, @NonNull final String sLocal)
  {
    final IXdmTypeDefinition ret = aSet.getTypeDefinition (_qn (sNamespace, sLocal));
    assertNotNull (sLocal, ret);
    return ret;
  }

  @Test
  public void testLookup () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);

    final IXdmTypeDefinition aZip = _type (aSet, NS, "Zip");
    assertFalse (aZip.isComplex ());
    assertEquals (_qn (NS, "Zip"), aZip.getName ());
    assertTrue (_type (aSet, NS, "Addr").isComplex ());
    final IXdmTypeDefinition aString = _type (aSet, XdmConstants.NS_XS, "string");
    assertFalse (aString.isComplex ());
    assertEquals (_qn (XdmConstants.NS_XS, "string"), aString.getName ());
    assertNull (aSet.getTypeDefinition (_qn (NS, "Nope")));
    // XDM-only names are not schema types, nor are the names of XSD 1.1
    assertNull (aSet.getTypeDefinition (_qn (XdmConstants.NS_XS, "untypedAtomic")));
    assertNull (aSet.getTypeDefinition (_qn (XdmConstants.NS_XS, "untyped")));
    assertNull (aSet.getTypeDefinition (_qn (XdmConstants.NS_XS, "numeric")));

    final IXdmElementDeclaration aHead = aSet.getElementDeclaration (_qn (NS, "head"));
    assertNotNull (aHead);
    assertEquals (_qn (NS, "Addr"), aHead.getTypeDefinition ().getName ());
    assertTrue (aHead.getTypeDefinition ().isComplex ());
    // Only globals count
    assertNull (aSet.getElementDeclaration (_qn (NS, "zip")));
    assertNull (aSet.getAttributeDeclaration (_qn (NS, "kind")));
    assertEquals (_qn (NS, "Zip"), aSet.getAttributeDeclaration (_qn (NS, "gkind")).getTypeDefinition ().getName ());
  }

  /**
   * A declaration with an anonymous type gets a synthetic identity, which is
   * what lets schema-element(N) work for the inline complexType most real
   * schemas use.
   */
  @Test
  public void testAnonymousType () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final IXdmTypeDefinition aAnon = aSet.getElementDeclaration (_qn (NS, "anon")).getTypeDefinition ();
    assertTrue (aAnon.isAnonymous ());
    assertNull (aAnon.getName ());
    assertTrue (aAnon.isComplex ());
    final XdmQName aName = aAnon.getAnnotationName ();
    assertTrue (aName.localName (), aName.localName ().startsWith ("#"));
    // Stable, and it resolves back to the same component
    final IXdmTypeDefinition aAgain = aSet.getElementDeclaration (_qn (NS, "anon")).getTypeDefinition ();
    assertEquals (aName, aAgain.getAnnotationName ());
    assertEquals (aAnon, aAgain);
    assertTrue (aAnon.isDerivedFrom (aAgain));
    assertTrue (aAnon.isDerivedFrom (aName));
    assertSame (aAnon.getContentType (), EXdmContentType.ELEMENT_ONLY);
    // An anonymous type is not the anonymous type of another declaration
    final IXdmSchemaSet aOther = _compile (SCHEMA);
    assertFalse (aAnon.isDerivedFrom (aOther.getElementDeclaration (_qn (NS, "anon")).getTypeDefinition ()));
    assertFalse (_type (aSet, NS, "Zip").isDerivedFrom (aAnon));
  }

  private static void _checkDerives (@NonNull final IXdmTypeDefinition aGot,
                                     @NonNull final IXdmTypeDefinition aWant,
                                     final boolean bExpected,
                                     @NonNull final String sWhy)
  {
    assertEquals (sWhy, bExpected, aGot.isDerivedFrom (aWant));
    assertEquals (sWhy + " (by name)", bExpected, aGot.isDerivedFrom (aWant.getName ()));
  }

  @Test
  public void testDerivation () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final IXdmTypeDefinition aZip = _type (aSet, NS, "Zip");
    final IXdmTypeDefinition aZip5 = _type (aSet, NS, "Zip5");
    final IXdmTypeDefinition aAddr = _type (aSet, NS, "Addr");
    final IXdmTypeDefinition aFull = _type (aSet, NS, "FullAddr");
    final IXdmTypeDefinition aInt = _type (aSet, XdmConstants.NS_XS, "int");
    final IXdmTypeDefinition aInteger = _type (aSet, XdmConstants.NS_XS, "integer");
    final IXdmTypeDefinition aString = _type (aSet, XdmConstants.NS_XS, "string");
    final IXdmTypeDefinition aAnyType = _type (aSet, XdmConstants.NS_XS, "anyType");
    final IXdmTypeDefinition aAnySimple = _type (aSet, XdmConstants.NS_XS, "anySimpleType");

    _checkDerives (aZip, aZip, true, "identity");
    _checkDerives (aZip5, aZip, true, "restriction of a named simple type");
    _checkDerives (aZip, aZip5, false, "derivation is not symmetric");
    _checkDerives (aFull, aAddr, true, "complex extension");
    _checkDerives (aAddr, aFull, false, "complex extension, reversed");
    _checkDerives (aZip, aAddr, false, "unrelated types");
    _checkDerives (aInt, aInteger, true, "the built-in atomic hierarchy");
    _checkDerives (aInteger, aInt, false, "the built-in hierarchy, reversed");
    _checkDerives (aZip, aString, true, "a user type restricting a built-in");
    _checkDerives (aZip, aAnyType, true, "everything derives from xs:anyType");
    _checkDerives (aAddr, aAnyType, true, "a complex type derives from xs:anyType");
    _checkDerives (aZip, aAnySimple, true, "a simple type derives from xs:anySimpleType");
    _checkDerives (aAddr, aAnySimple, false, "element-only content does not derive from xs:anySimpleType");
    _checkDerives (aInt, aZip, false, "a built-in does not derive from a user type");
    assertFalse (aZip.isDerivedFrom (_qn (NS, "Nope")));
    assertFalse (aZip.isDerivedFrom (_qn ("", "Nope")));

    assertEquals (aString, aZip.getBaseType ());
    assertEquals (aZip, aZip5.getBaseType ());
    assertEquals (aAddr, aFull.getBaseType ());
    assertNull (aAnyType.getBaseType ());
    assertEquals (_qn (XdmConstants.NS_XS, "string"), aZip5.getBuiltinAtomicType ());
    assertEquals (EXdmSimpleVariety.ATOMIC, aZip.getVariety ());
    assertNull (aAddr.getVariety ());
    assertNull (aAnySimple.getVariety ());
  }

  @Test
  public void testSubstitutionGroup () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final IXdmElementDeclaration aHead = aSet.getElementDeclaration (_qn (NS, "head"));
    final IXdmElementDeclaration aSub = aSet.getElementDeclaration (_qn (NS, "sub"));
    assertEquals (aHead.getName (), aHead.getSubstitutionMember (_qn (NS, "head")).getName ());
    assertEquals (_qn (NS, "sub"), aHead.getSubstitutionMember (_qn (NS, "sub")).getName ());
    assertNull (aSub.getSubstitutionMember (_qn (NS, "head")));
    assertNull (aHead.getSubstitutionMember (_qn (NS, "anon")));
  }

  @Test
  public void testValidateAnnotates () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final XdmNode aEl = _parseElement ("<t:head xmlns:t=\"urn:t\" kind=\"99999\"><t:zip>12345</t:zip><t:n>7</t:n></t:head>");
    aSet.validateElement (aEl, null, false);
    // The root is complex: its type is exact, and it has no typed value
    XdmSchemaType aType = aEl.getSchemaType ();
    assertNotNull (aType);
    assertEquals (_qn (NS, "Addr"), aType.getName ());
    assertFalse (aType.isSimple ());
    final XdmNode aZip = aEl.getChildAtIndex (0);
    aType = aZip.getSchemaType ();
    assertEquals (_qn (NS, "Zip"), aType.getName ());
    assertTrue (aType.isSimple ());
    assertTrue (aType.hasAtomicBase ("string"));
    // A built-in type is named too: schema-element(N) and element(*, xs:int)
    // ask whether the type of the node is T or derived from it
    final XdmNode aN = aEl.getChildAtIndex (1);
    aType = aN.getSchemaType ();
    assertEquals (_qn (XdmConstants.NS_XS, "int"), aType.getName ());
    assertTrue (aType.hasAtomicBase ("int"));
    assertEquals (1, aEl.getAttributeCount ());
    aType = aEl.getAttributeAtIndex (0).getSchemaType ();
    assertEquals (_qn (NS, "Zip"), aType.getName ());
    assertTrue (aType.isDerivedFrom (XdmConstants.NS_XS, "string"));
  }

  @Test
  public void testInvalidTreeIsLeftAlone () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final XdmNode aEl = _parseElement ("<t:head xmlns:t=\"urn:t\"><t:zip>nope</t:zip></t:head>");
    try
    {
      aSet.validateElement (aEl, null, false);
      fail ("a zip that fails its pattern facet is invalid");
    }
    catch (final XdmValidationException ex)
    {
      assertFalse (ex.isDocumentConstraint ());
    }
    assertNull (aEl.getSchemaType ());
    assertNull (aEl.getTypeAnnotation ());
    assertNull (aEl.getChildAtIndex (0).getSchemaType ());
  }

  @Test
  public void testUndeclaredElement () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    try
    {
      aSet.validateElement (_parseElement ("<t:other xmlns:t=\"urn:t\"><x/></t:other>"), null, false);
      fail ("an undeclared element");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }

  @Test
  public void testValidateAgainstType () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    final IXdmTypeDefinition aAddr = _type (aSet, NS, "Addr");
    final XdmNode aEl = _parseElement ("<anything xmlns:t=\"urn:t\"><t:zip>12345</t:zip></anything>");
    aSet.validateElement (aEl, aAddr, false);
    assertEquals (_qn (NS, "Addr"), aEl.getSchemaType ().getName ());
    try
    {
      aSet.validateElement (_parseElement ("<anything xmlns:t=\"urn:t\"><t:zip>nope</t:zip></anything>"), aAddr, false);
      fail ("content the named type rejects");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
    // An anonymous type can be validated against as well
    final IXdmTypeDefinition aAnon = aSet.getElementDeclaration (_qn (NS, "anon")).getTypeDefinition ();
    aSet.validateElement (_parseElement ("<x/>"), aAnon, false);
    try
    {
      aSet.validateElement (_parseElement ("<x><y/></x>"), aAnon, false);
      fail ("an empty sequence has no children");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }

  /**
   * XSLT builds element content one instruction at a time, so a constructed
   * element routinely carries several adjacent text nodes and empty ones, which
   * the parser never produces: the walk runs on a normalised clone.
   */
  @Test
  public void testConstructedText () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile (SCHEMA);
    for (int nPass = 0; nPass < 2; nPass++)
    {
      final XdmNode aHead = XdmNode.createElement (new XdmQName (NS, "head", "t"));
      aHead.declareNamespace ("t", NS);
      final XdmNode aZip = XdmNode.createElement (new XdmQName (NS, "zip", "t"));
      final String [] aParts = nPass == 0 ? new String [] { "", "12", "", "3", "45", "" } : new String [] { "12", "34" };
      for (final String sPart : aParts)
        aZip.appendChild (XdmNode.createText (sPart));
      aHead.appendChild (aZip);
      final int nTextNodes = aZip.getChildCount ();
      try
      {
        aSet.validateElement (aHead, null, false);
        assertEquals (0, nPass);
        assertTrue (aZip.getSchemaType ().hasAtomicBase ("string"));
      }
      catch (final XdmValidationException ex)
      {
        assertEquals (1, nPass);
      }
      // Only annotations are written
      assertEquals (nTextNodes, aZip.getChildCount ());
    }
  }

  /**
   * A subtree of a larger tree is not the document: an IDREF inside it that
   * resolves somewhere else is not dangling, while a whole document gets the
   * full check.
   */
  @Test
  public void testIDScope () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ("""
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="wrap"><xs:complexType><xs:sequence>
    <xs:element ref="a"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="a"><xs:complexType>
    <xs:attribute name="r" type="xs:IDREF"/></xs:complexType></xs:element>
</xs:schema>""");
    final XdmNode aSub = _parseElement ("<wrap><a r=\"elsewhere\"/></wrap>").getChildAtIndex (0);
    aSet.validateElement (aSub, null, false);
    try
    {
      aSet.validateElement (_parseElement ("<wrap><a r=\"elsewhere\"/></wrap>"), null, true);
      fail ("a document-scoped episode with a dangling IDREF");
    }
    catch (final XdmValidationException ex)
    {
      assertTrue (ex.isDocumentConstraint ());
    }
    aSet.validateElement (_parseElement ("<wrap><a r=\"elsewhere\"/></wrap>"), null, false);
  }

  /** The component set of an XSLT host is fixed by xsl:import-schema. */
  @Test
  public void testSchemaLocationHintsAreNotFollowed () throws IOException, XdmSchemaException
  {
    final Path aDir = m_aTempDir.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("hint.xsd"), """
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="hinted" type="xs:string"/></xs:schema>""");
    final String sBase = """
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="other" type="xs:string"/></xs:schema>""";
    final XsdSchema aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sBase, aDir)),
                                                 EXsdVersion.V10,
                                                 null);
    final String sInstance = "<hinted xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"hint.xsd\">x</hinted>";
    // The string entry point does pick the hint up
    assertTrue (aSchema.validateText (sInstance).valid ());
    try
    {
      XsdSchemaProvider.createSchemaSet (aSchema).validateElement (_parseElement (sInstance), null, false);
      fail ("the in-place validation must not load instance-discovered schema documents");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }

  @Test
  public void testProviderCompile () throws XdmSchemaException, IOException
  {
    final Path aFile = m_aTempDir.newFile ("t.xsd").toPath ();
    Files.writeString (aFile, SCHEMA);
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> (new XdmSchemaSource (aFile.toUri ().toString (),
                                                                                                    null));
    final XsdSchemaProvider aProvider = new XsdSchemaProvider ();
    assertNotNull (aProvider.compile (aSources).getElementDeclaration (_qn (NS, "head")));
    assertNotNull (aProvider.compile (new CommonsArrayList <> (new XdmSchemaSource (null, SCHEMA)))
                            .getElementDeclaration (_qn (NS, "head")));
    assertNotNull (aProvider.compile (new CommonsArrayList <> ()).getTypeDefinition (_qn (XdmConstants.NS_XS, "int")));
    try
    {
      aProvider.compile (new CommonsArrayList <> (new XdmSchemaSource ("http://example.org/t.xsd", null)));
      fail ("the network is never used");
    }
    catch (final XdmSchemaException ex)
    {
      // expected
    }
    try
    {
      aProvider.compile (new CommonsArrayList <> (new XdmSchemaSource (null, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element/></xs:schema>")));
      fail ("an invalid schema");
    }
    catch (final XdmSchemaException ex)
    {
      // expected
    }
  }

  /** The typed value of a simple value against a named type (the casting seam). */
  @Test
  public void testValidateSimpleValue () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ("""
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:c" xmlns="urn:c">
  <xs:simpleType name="Small"><xs:restriction base="xs:int"><xs:maxInclusive value="9"/></xs:restriction></xs:simpleType>
  <xs:simpleType name="U"><xs:union memberTypes="xs:int xs:date"/></xs:simpleType>
  <xs:simpleType name="L"><xs:list itemType="xs:int"/></xs:simpleType>
  <xs:simpleType name="Q"><xs:restriction base="xs:QName"/></xs:simpleType>
  <xs:complexType name="C"/>
</xs:schema>""");
    final IXdmTypeDefinition aSmall = _type (aSet, "urn:c", "Small");
    final ICommonsList <XdmTypedToken> aTokens = aSmall.validate (" 7 ", null);
    assertEquals (1, aTokens.size ());
    assertEquals ("7", aTokens.get (0).lexical ());
    assertEquals (_qn (XdmConstants.NS_XS, "int"), aTokens.get (0).builtinType ());
    assertEquals (aSmall, aTokens.get (0).type ());
    try
    {
      aSmall.validate ("10", null);
      fail ("Small accepted 10");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }

    final IXdmTypeDefinition aUnion = _type (aSet, "urn:c", "U");
    assertEquals (EXdmSimpleVariety.UNION, aUnion.getVariety ());
    assertEquals (2, aUnion.getMemberTypes ().size ());
    assertEquals (_qn (XdmConstants.NS_XS, "date"), aUnion.validate ("2020-01-01", null).get (0).builtinType ());
    final XdmTypedToken aMember = aUnion.validate ("5", null).get (0);
    assertEquals (_qn (XdmConstants.NS_XS, "int"), aMember.builtinType ());
    assertEquals (_qn (XdmConstants.NS_XS, "int"), aMember.type ().getName ());
    try
    {
      aUnion.validate ("x", null);
      fail ("U accepted x");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }

    final IXdmTypeDefinition aList = _type (aSet, "urn:c", "L");
    assertEquals (EXdmSimpleVariety.LIST, aList.getVariety ());
    assertEquals (_qn (XdmConstants.NS_XS, "int"), aList.getItemType ().getName ());
    assertEquals (3, aList.validate (" 1 2  3 ", null).size ());
    try
    {
      aList.validate ("1 x", null);
      fail ("a bad item");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }

    final IXdmTypeDefinition aQName = _type (aSet, "urn:c", "Q");
    assertTrue (aQName.isNamespaceSensitive ());
    assertFalse (aSmall.isNamespaceSensitive ());
    assertTrue (aQName.validate ("p:x", p -> "p".equals (p) ? "urn:p" : null).size () == 1);
    try
    {
      aQName.validate ("q:x", p -> null);
      fail ("an unbound prefix");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }

    try
    {
      _type (aSet, "urn:c", "C").validate ("x", null);
      fail ("a complex type without simple content");
    }
    catch (final XdmValidationException ex)
    {
      // expected
    }
  }

  @Test
  public void testIDKinds () throws XdmValidationException
  {
    final IXdmSchemaSet aSet = _compile ("""
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="e"><xs:complexType><xs:sequence>
    <xs:element name="i" type="xs:ID"/><xs:element name="r" type="xs:IDREF"/>
  </xs:sequence><xs:attribute name="id" type="xs:ID"/><xs:attribute name="refs" type="xs:IDREFS"/></xs:complexType></xs:element>
</xs:schema>""");
    final XdmNode aEl = _parseElement ("<e id=\"a\" refs=\" a  a \"><i>b</i><r>a</r></e>");
    aSet.validateElement (aEl, null, true);
    assertEquals (EIDKind.ID, aEl.getAttribute ("", "id").getIDKind ());
    assertEquals (EIDKind.IDREFS, aEl.getAttribute ("", "refs").getIDKind ());
    assertEquals ("a a", aEl.getAttribute ("", "refs").getValue ());
    assertEquals (EIDKind.ID, aEl.getChildAtIndex (0).getIDKind ());
    assertEquals (EIDKind.IDREF, aEl.getChildAtIndex (1).getIDKind ());
  }
}
