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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Schema and instance rules checked against the clauses of XSD 1.0 and 1.1
 * where go-xslt's reading differs: value constraints and whitespace, xsi:nil
 * whitespace, bound lexicals, the complete attribute wildcard, conditional
 * inclusion, occurrence whitespace, the XPath default namespace and the
 * identity-constraint and regular expression grammars.
 *
 * @author Philip Helger
 */
public final class XsdSpecClauseTest
{
  private static final String XS = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'" +
                                   " xmlns:vc='http://www.w3.org/2007/XMLSchema-versioning'>";
  private static final String XSI = " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'";

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  @NonNull
  private static Compiled _compile (@NonNull final String sSchema, @Nullable final Path aBase, @NonNull final EXsdVersion eVersion)
  {
    return XsdTestSupport.compile (sSchema, aBase, eVersion);
  }

  private static void _assertInstance (@NonNull final String sSchema,
                                       @NonNull final String sInstance,
                                       @NonNull final EVerdict eExpected)
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final Compiled aCompiled = _compile (sSchema, null, eVersion);
      assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
      assertEquals (eVersion + " " + sInstance, eExpected, XsdTestSupport.validate (aCompiled.schema (), sInstance));
    }
  }

  private static void _assertSchema (@NonNull final String sSchema, @NonNull final EVerdict eExpected)
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
      assertEquals (eVersion.toString (), eExpected, _compile (sSchema, null, eVersion).verdict ());
  }

  @Test
  public void testNilWhitespace ()
  {
    // xsi:nil is an xs:boolean, so its value is whitespace-collapsed
    _assertInstance (XS + "<xs:element name='e' type='xs:int' nillable='true'/></xs:schema>",
                     "<e" + XSI + " xsi:nil=' true '/>",
                     EVerdict.VALID);
  }

  @Test
  public void testValueConstraintWhitespace ()
  {
    // Whitespace is character content (cvc-elt.5.2): it is compared with the
    // fixed value and replaces a default
    final String sFixed = XS + "<xs:element name='e' type='xs:string' fixed='abc'/></xs:schema>";
    _assertInstance (sFixed, "<e> </e>", EVerdict.INVALID);
    _assertInstance (sFixed, "<e/>", EVerdict.VALID);
    _assertInstance (sFixed, "<e>abc</e>", EVerdict.VALID);
    final String sDefault = XS + "<xs:element name='e' type='xs:int' default='5'/></xs:schema>";
    _assertInstance (sDefault, "<e> </e>", EVerdict.INVALID);
    _assertInstance (sDefault, "<e/>", EVerdict.VALID);
    _assertInstance (sDefault, "<e><!-- c --></e>", EVerdict.VALID);
  }

  @Test
  public void testBoundWhitespace ()
  {
    // The bound has no timezone once collapsed: against a timezoned value the
    // order depends on the imputed offset, so the pair is incomparable
    _assertInstance (XS +
                     "<xs:element name='e'><xs:simpleType><xs:restriction base='xs:dateTime'>" +
                     "<xs:maxInclusive value='2000-01-01T00:00:00 '/></xs:restriction></xs:simpleType></xs:element></xs:schema>",
                     "<e>2000-01-01T10:00:00+14:00</e>",
                     EVerdict.INVALID);
  }

  @Test
  public void testCompleteWildcardProcessContents ()
  {
    // The local xs:anyAttribute's processContents wins over the group's
    _assertInstance (XS +
                     "<xs:attributeGroup name='g'><xs:anyAttribute processContents='strict'/></xs:attributeGroup>" +
                     "<xs:element name='e'><xs:complexType><xs:attributeGroup ref='g'/>" +
                     "<xs:anyAttribute processContents='skip'/></xs:complexType></xs:element></xs:schema>",
                     "<e xmlns:f='urn:f' f:x='1'/>",
                     EVerdict.VALID);
    // Without a local one, the first group wildcard's
    _assertInstance (XS +
                     "<xs:attributeGroup name='g1'><xs:anyAttribute processContents='skip'/></xs:attributeGroup>" +
                     "<xs:attributeGroup name='g2'><xs:anyAttribute processContents='strict'/></xs:attributeGroup>" +
                     "<xs:element name='e'><xs:complexType><xs:attributeGroup ref='g1'/><xs:attributeGroup ref='g2'/>" +
                     "</xs:complexType></xs:element></xs:schema>",
                     "<e xmlns:f='urn:f' f:x='1'/>",
                     EVerdict.VALID);
  }

  @Test
  public void testIntersectTwoOther () throws IOException
  {
    final Path aDir = m_aTmp.getRoot ().toPath ();
    Files.writeString (aDir.resolve ("b.xsd"),
                       "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:b'>" +
                                               "<xs:attributeGroup name='g'><xs:anyAttribute namespace='##other' processContents='lax'/>" +
                                               "</xs:attributeGroup></xs:schema>");
    final String sSchema = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:a' xmlns:b='urn:b'>" +
                           "<xs:import namespace='urn:b' schemaLocation='b.xsd'/>" +
                           "<xs:element name='e'><xs:complexType><xs:attributeGroup ref='b:g'/>" +
                           "<xs:anyAttribute namespace='##other' processContents='lax'/></xs:complexType></xs:element></xs:schema>";
    // 1.0: not(a) and not(b) have no expressible intersection
    assertEquals (EVerdict.INVALID, _compile (sSchema, aDir, EXsdVersion.V10).verdict ());
    // 1.1: not {a, b, absent}
    final Compiled aCompiled = _compile (sSchema, aDir, EXsdVersion.V11);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    assertEquals (EVerdict.VALID, XsdTestSupport.validate (aCompiled.schema (), "<a:e xmlns:a='urn:a' xmlns:c='urn:c' c:x='1'/>"));
    assertEquals (EVerdict.INVALID, XsdTestSupport.validate (aCompiled.schema (), "<a:e xmlns:a='urn:a' xmlns:b='urn:b' b:x='1'/>"));
    assertEquals (EVerdict.INVALID, XsdTestSupport.validate (aCompiled.schema (), "<a:e xmlns:a='urn:a' a:x='1'/>"));
  }

  @Test
  public void testConditionalInclusionNames ()
  {
    // Only expanded names in the XML Schema namespace name built-in types and
    // facets: my:string is unknown, so the xs:int declaration is ignored
    final String sMy = XS.replace (">", " xmlns:my='urn:my'>");
    _assertInstance (sMy +
                     "<xs:element name='e' type='xs:int' vc:typeAvailable='my:string'/>" +
                     "<xs:element name='e' type='xs:string' vc:typeUnavailable='my:string'/></xs:schema>",
                     "<e>abc</e>",
                     EVerdict.VALID);
    _assertInstance (sMy +
                     "<xs:element name='e' type='xs:int' vc:facetAvailable='my:pattern'/>" +
                     "<xs:element name='e' type='xs:string' vc:facetUnavailable='my:pattern'/></xs:schema>",
                     "<e>abc</e>",
                     EVerdict.VALID);
    // xs:anyType and xs:anySimpleType are built-in types
    _assertInstance (XS +
                     "<xs:element name='e' type='xs:int' vc:typeAvailable='xs:anyType xs:anySimpleType'/>" +
                     "<xs:element name='e' type='xs:string' vc:typeUnavailable='xs:anyType xs:anySimpleType'/></xs:schema>",
                     "<e>abc</e>",
                     EVerdict.INVALID);
  }

  @Test
  public void testVersionIsDecimal ()
  {
    // vc:minVersion is an xs:decimal: INF is no value of it
    final String sSchema = XS + "<xs:element name='e' type='xs:int' vc:minVersion='INF'/></xs:schema>";
    assertEquals (EVerdict.INVALID, _compile (sSchema, null, EXsdVersion.V11).verdict ());
    final String sPlus = XS + "<xs:element name='e' type='xs:int' vc:minVersion='+1.1'/></xs:schema>";
    assertEquals (EVerdict.VALID, _compile (sPlus, null, EXsdVersion.V11).verdict ());
  }

  @Test
  public void testOccursWhitespace ()
  {
    _assertInstance (XS +
                     "<xs:element name='e'><xs:complexType><xs:sequence>" +
                     "<xs:element name='c' maxOccurs=' unbounded '/></xs:sequence></xs:complexType></xs:element></xs:schema>",
                     "<e><c/><c/><c/></e>",
                     EVerdict.VALID);
  }

  @Test
  public void testXPathDefaultNamespaceOfHost ()
  {
    // ##defaultNamespace is the default namespace in scope at the host
    // element (urn:b at the selector), not at xs:schema (urn:a)
    final String sSchema = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:b'" +
                           " elementFormDefault='qualified' xmlns='urn:a' xpathDefaultNamespace='##defaultNamespace'>" +
                           "<xs:element name='e'><xs:complexType><xs:sequence>" +
                           "<xs:element name='i' type='xs:string' maxOccurs='unbounded'/></xs:sequence></xs:complexType>" +
                           "<xs:unique name='u'><xs:selector xmlns='urn:b' xpath='i'/><xs:field xpath='.'/></xs:unique>" +
                           "</xs:element></xs:schema>";
    final Compiled aCompiled = _compile (sSchema, null, EXsdVersion.V11);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    assertEquals (EVerdict.INVALID, XsdTestSupport.validate (aCompiled.schema (), "<e xmlns='urn:b'><i>1</i><i>1</i></e>"));
    assertEquals (EVerdict.VALID, XsdTestSupport.validate (aCompiled.schema (), "<e xmlns='urn:b'><i>1</i><i>2</i></e>"));
  }

  @Test
  public void testIdentityXPathAdjacentNames ()
  {
    _assertSchema (XS +
                   "<xs:element name='e'><xs:unique name='u'><xs:selector xpath='a b'/><xs:field xpath='.'/>" +
                   "</xs:unique></xs:element></xs:schema>",
                   EVerdict.INVALID);
    _assertSchema (XS +
                   "<xs:element name='e'><xs:unique name='u'><xs:selector xpath=' a / b | .// c '/><xs:field xpath='@ x'/>" +
                   "</xs:unique></xs:element></xs:schema>",
                   EVerdict.VALID);
  }

  @Test
  public void testRegexUnescapedBrace ()
  {
    // '}' is not a NormalChar
    _assertSchema (XS +
                   "<xs:element name='e'><xs:simpleType><xs:restriction base='xs:string'>" +
                   "<xs:pattern value='a}'/></xs:restriction></xs:simpleType></xs:element></xs:schema>",
                   EVerdict.INVALID);
    _assertSchema (XS +
                   "<xs:element name='e'><xs:simpleType><xs:restriction base='xs:string'>" +
                   "<xs:pattern value='a\\}{2}'/></xs:restriction></xs:simpleType></xs:element></xs:schema>",
                   EVerdict.VALID);
  }
}
