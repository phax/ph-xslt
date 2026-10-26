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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * The read-only component model. The first tests are ported from go-xslt's
 * public <code>xsd/xsd_test.go</code>; their schemas use facets and complex
 * types, so they are skipped (assumption) as long as the stubs of phases V3
 * and V4 leave the compile undecided. The last tests use schemas the loader
 * alone decides.
 *
 * @author Philip Helger
 */
public final class XsdSchemaModelTest
{
  private static final String MAIN_XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                                         "    xmlns:t=\"urn:t\" xmlns:c=\"urn:common\" targetNamespace=\"urn:t\" elementFormDefault=\"qualified\">\n" +
                                         "  <xs:import namespace=\"urn:common\" schemaLocation=\"common.xsd\"/>\n" +
                                         "  <xs:simpleType name=\"Money\"><xs:restriction base=\"c:Amount\"><xs:minInclusive value=\"0\"/></xs:restriction></xs:simpleType>\n" +
                                         "  <xs:simpleType name=\"Codes\"><xs:list itemType=\"xs:NMTOKEN\"/></xs:simpleType>\n" +
                                         "  <xs:simpleType name=\"IdOrNum\"><xs:union memberTypes=\"xs:ID xs:integer\"/></xs:simpleType>\n" +
                                         "  <xs:simpleType name=\"Kind\"><xs:restriction base=\"xs:string\"><xs:enumeration value=\"a\"/><xs:enumeration value=\"b\"/></xs:restriction></xs:simpleType>\n" +
                                         "  <xs:complexType name=\"Line\">\n" +
                                         "    <xs:sequence>\n" +
                                         "      <xs:element name=\"Desc\" type=\"xs:string\"/>\n" +
                                         "      <xs:element name=\"Price\" type=\"t:Money\" minOccurs=\"0\"/>\n" +
                                         "      <xs:choice maxOccurs=\"unbounded\">\n" +
                                         "        <xs:element name=\"Note\" type=\"xs:string\"/>\n" +
                                         "        <xs:element ref=\"t:Tag\"/>\n" +
                                         "      </xs:choice>\n" +
                                         "    </xs:sequence>\n" +
                                         "    <xs:attribute name=\"no\" type=\"xs:positiveInteger\" use=\"required\"/>\n" +
                                         "    <xs:attribute name=\"kind\" type=\"t:Kind\" default=\"a\"/>\n" +
                                         "    <xs:attribute name=\"old\" type=\"xs:string\" use=\"prohibited\"/>\n" +
                                         "  </xs:complexType>\n" +
                                         "  <xs:complexType name=\"SpecialLine\"><xs:complexContent><xs:extension base=\"t:Line\">\n" +
                                         "    <xs:sequence><xs:element name=\"Extra\" type=\"xs:date\"/></xs:sequence>\n" +
                                         "  </xs:extension></xs:complexContent></xs:complexType>\n" +
                                         "  <xs:complexType name=\"WithText\"><xs:simpleContent><xs:extension base=\"t:Money\"><xs:attribute name=\"cur\" type=\"xs:string\"/></xs:extension></xs:simpleContent></xs:complexType>\n" +
                                         "  <xs:complexType name=\"Mix\" mixed=\"true\"><xs:sequence><xs:any namespace=\"##other\" processContents=\"lax\" minOccurs=\"0\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType>\n" +
                                         "  <xs:element name=\"Invoice\">\n" +
                                         "    <xs:complexType><xs:sequence>\n" +
                                         "      <xs:element name=\"Line\" type=\"t:Line\" maxOccurs=\"unbounded\"/>\n" +
                                         "      <xs:element name=\"Total\" type=\"t:WithText\"/>\n" +
                                         "      <xs:element name=\"Opt\" type=\"t:Mix\" minOccurs=\"0\" nillable=\"true\"/>\n" +
                                         "    </xs:sequence></xs:complexType>\n" +
                                         "  </xs:element>\n" +
                                         "  <xs:element name=\"Tag\" type=\"xs:string\" abstract=\"true\"/>\n" +
                                         "  <xs:element name=\"RedTag\" type=\"xs:string\" substitutionGroup=\"t:Tag\"/>\n" +
                                         "  <xs:element name=\"DarkRedTag\" type=\"xs:string\" substitutionGroup=\"t:RedTag\"/>\n" +
                                         "</xs:schema>";

  private static final String COMMON_XSD = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:common\">\n" +
                                           "  <xs:simpleType name=\"Amount\"><xs:restriction base=\"xs:decimal\"><xs:fractionDigits value=\"2\"/></xs:restriction></xs:simpleType>\n" +
                                           "</xs:schema>";

  private static final String DOCS_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:t=\"urn:t\" targetNamespace=\"urn:t\" elementFormDefault=\"qualified\">\n" +
                                            " <xs:element name=\"Doc\" type=\"t:DocType\">\n" +
                                            "  <xs:annotation><xs:documentation xml:lang=\"en\" source=\"urn:src\">The <b>root</b>\n" +
                                            "    element.</xs:documentation><xs:documentation xml:lang=\"de\">Das Wurzelelement.</xs:documentation></xs:annotation>\n" +
                                            " </xs:element>\n" +
                                            " <xs:element name=\"Global\"><xs:annotation><xs:documentation>Declared globally.</xs:documentation></xs:annotation></xs:element>\n" +
                                            " <xs:attribute name=\"lang\" type=\"xs:string\"><xs:annotation><xs:documentation>Language of the declaration.</xs:documentation></xs:annotation></xs:attribute>\n" +
                                            " <xs:complexType name=\"DocType\">\n" +
                                            "  <xs:annotation><xs:documentation>The document type.</xs:documentation></xs:annotation>\n" +
                                            "  <xs:sequence>\n" +
                                            "   <xs:element name=\"Code\" type=\"t:Code\"><xs:annotation><xs:documentation>A local element.</xs:documentation></xs:annotation></xs:element>\n" +
                                            "   <xs:element ref=\"t:Global\"><xs:annotation><xs:documentation>Used by reference.</xs:documentation></xs:annotation></xs:element>\n" +
                                            "  </xs:sequence>\n" +
                                            "  <xs:attribute name=\"id\" type=\"xs:string\"><xs:annotation><xs:documentation>The id.</xs:documentation></xs:annotation></xs:attribute>\n" +
                                            "  <xs:attribute ref=\"t:lang\"><xs:annotation><xs:documentation>Reference to lang.</xs:documentation></xs:annotation></xs:attribute>\n" +
                                            " </xs:complexType>\n" +
                                            " <xs:simpleType name=\"Base\"><xs:annotation><xs:documentation>Base code.</xs:documentation></xs:annotation>\n" +
                                            "  <xs:restriction base=\"xs:string\"><xs:minLength value=\"2\"/><xs:maxLength value=\"10\"/><xs:pattern value=\"[A-Z]+\"/><xs:pattern value=\"[A-Z]+X\"/></xs:restriction></xs:simpleType>\n" +
                                            " <xs:simpleType name=\"Code\"><xs:restriction base=\"t:Base\"><xs:maxLength value=\"5\"/><xs:pattern value=\"A.*\"/><xs:whiteSpace value=\"collapse\"/></xs:restriction></xs:simpleType>\n" +
                                            " <xs:simpleType name=\"Amount\"><xs:restriction base=\"xs:decimal\"><xs:totalDigits value=\"8\"/><xs:fractionDigits value=\"2\"/><xs:minInclusive value=\"0\"/><xs:maxExclusive value=\"1000\"/></xs:restriction></xs:simpleType>\n" +
                                            "</xs:schema>";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  private static XsdName _t (final String sLocal)
  {
    return new XsdName ("urn:t", sLocal);
  }

  private static XsdName _xs (final String sLocal)
  {
    return new XsdName (XsdConstants.NS_XS, sLocal);
  }

  private XsdSchemaView _compileMain () throws IOException
  {
    final Path aDir = m_aTempFolder.newFolder ().toPath ();
    Files.writeString (aDir.resolve ("common.xsd"), COMMON_XSD);
    final Compiled aCompiled = XsdTestSupport.compile (MAIN_XSD, aDir, EXsdVersion.V10);
    Assume.assumeTrue ("needs the later phases: " + aCompiled.message (), aCompiled.verdict () != EVerdict.UNSUPPORTED);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    return aCompiled.schema ().getView ();
  }

  private static ICommonsList <String> _locals (final ICommonsList <XsdElementView> aElements)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final XsdElementView aElement : aElements)
      ret.add (aElement.getName ().local ());
    return ret;
  }

  private static String _text (final ICommonsList <XsdDocumentation> aDocs)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final XsdDocumentation d : aDocs)
      ret.add (d.lang () + "|" + d.source () + "|" + d.text ());
    return String.join (" // ", ret);
  }

  @Test
  public void testGlobalsAndTypes () throws IOException
  {
    final XsdSchemaView s = _compileMain ();
    assertEquals ("[DarkRedTag, Invoice, RedTag, Tag]", _locals (s.getAllGlobalElements ()).toString ());
    // BuiltinBase follows restriction chains, across the imported file
    final IXsdTypeDefinitionView aMoney = s.getType (_t ("Money"));
    assertNotNull (aMoney);
    assertTrue (aMoney.isSimple ());
    assertEquals (_xs ("decimal"), aMoney.getBuiltinBase ());
    assertEquals (_t ("Money"), aMoney.getName ());
    // A reference to a built-in type reports the built-in's own name
    final XsdComplexTypeView aLine = (XsdComplexTypeView) s.getType (_t ("Line"));
    final XsdElementView aDesc = (XsdElementView) aLine.getContentParticles ().get (0).term ();
    assertEquals ("Desc", aDesc.getName ().local ());
    assertEquals (_xs ("string"), aDesc.getType ().getName ());
    final XsdSimpleTypeView aCodes = (XsdSimpleTypeView) s.getType (_t ("Codes"));
    assertSame (EXsdVariety.LIST, aCodes.getVariety ());
    assertNotNull (aCodes.getItemType ());
    assertEquals ("NMTOKEN", aCodes.getBuiltinBase ().local ());
    final XsdSimpleTypeView aUnion = (XsdSimpleTypeView) s.getType (_t ("IdOrNum"));
    assertSame (EXsdVariety.UNION, aUnion.getVariety ());
    assertEquals (2, aUnion.getMemberTypes ().size ());
    assertEquals ("integer", aUnion.getMemberTypes ().get (1).getBuiltinBase ().local ());
    assertEquals ("[a, b]", ((XsdSimpleTypeView) s.getType (_t ("Kind"))).getEnumeration ().toString ());
  }

  @Test
  public void testContentModelCardinalityAndAttributes () throws IOException
  {
    final XsdSchemaView s = _compileMain ();
    final XsdComplexTypeView aInvoice = (XsdComplexTypeView) s.getElement (_t ("Invoice")).getType ();
    assertTrue (aInvoice.getName ().isZero ());
    final ICommonsList <XsdParticleView> aParticles = aInvoice.getContentParticles ();
    assertEquals (3, aParticles.size ());
    final XsdParticleView aLineP = aParticles.get (0);
    assertEquals ("Line", ((XsdElementView) aLineP.term ()).getName ().local ());
    assertEquals (1, aLineP.minOccurs ());
    assertEquals (XsdConstants.UNBOUNDED, aLineP.maxOccurs ());
    final XsdParticleView aOpt = aParticles.get (2);
    assertEquals (0, aOpt.minOccurs ());
    assertEquals (1, aOpt.maxOccurs ());
    assertTrue (((XsdElementView) aOpt.term ()).isNillable ());
    final XsdComplexTypeView aLT = (XsdComplexTypeView) ((XsdElementView) aLineP.term ()).getType ();
    final ICommonsList <XsdParticleView> aLP = aLT.getContentParticles ();
    assertEquals (3, aLP.size ());
    assertEquals (0, aLP.get (1).minOccurs ());
    final XsdModelGroupView aChoice = (XsdModelGroupView) aLP.get (2).term ();
    assertSame (EXsdCompositor.CHOICE, aChoice.compositor ());
    assertEquals (XsdConstants.UNBOUNDED, aLP.get (2).maxOccurs ());
    assertEquals (2, aChoice.particles ().size ());
    // A referenced element is the very same view as the global declaration
    assertSame (s.getElement (_t ("Tag")), aChoice.particles ().get (1).term ());
    XsdAttributeUseView aNo = null;
    XsdAttributeUseView aKind = null;
    XsdAttributeUseView aOld = null;
    for (final XsdAttributeUseView a : aLT.getAttributes ())
      switch (a.name ().local ())
      {
        case "no":
          aNo = a;
          break;
        case "kind":
          aKind = a;
          break;
        case "old":
          aOld = a;
          break;
        default:
          break;
      }
    assertNotNull (aNo);
    assertSame (EXsdUse.REQUIRED, aNo.use ());
    assertEquals ("positiveInteger", aNo.type ().getBuiltinBase ().local ());
    assertNotNull (aKind);
    assertSame (EXsdUse.OPTIONAL, aKind.use ());
    assertEquals ("a", aKind.defaultValue ());
    // A prohibited use is reported where the type itself declares it
    assertNotNull (aOld);
    assertSame (EXsdUse.PROHIBITED, aOld.use ());
    final XsdComplexTypeView aSpecial = (XsdComplexTypeView) s.getType (_t ("SpecialLine"));
    assertEquals ("extension", aSpecial.getDerivation ());
    assertEquals (_t ("Line"), aSpecial.getBase ().getName ());
    assertEquals (2, aSpecial.getContentParticles ().size ());
    assertEquals (aLT.getAttributes ().size (), aSpecial.getAttributes ().size ());
  }

  @Test
  public void testSimpleContentMixedWildcardAndSubstitution () throws IOException
  {
    final XsdSchemaView s = _compileMain ();
    final XsdComplexTypeView aWithText = (XsdComplexTypeView) s.getType (_t ("WithText"));
    assertSame (EXsdContentKind.SIMPLE, aWithText.getContentKind ());
    assertNotNull (aWithText.getSimpleContent ());
    assertEquals ("decimal", aWithText.getBuiltinBase ().local ());
    assertEquals (1, aWithText.getAttributes ().size ());
    assertEquals ("cur", aWithText.getAttributes ().get (0).name ().local ());
    final XsdComplexTypeView aMix = (XsdComplexTypeView) s.getType (_t ("Mix"));
    assertTrue (aMix.isMixed ());
    assertSame (EXsdContentKind.MIXED, aMix.getContentKind ());
    final XsdWildcardView aWC = (XsdWildcardView) aMix.getContentParticles ().get (0).term ();
    assertEquals ("other", aWC.mode ());
    assertEquals ("lax", aWC.processContents ());
    assertEquals ("[DarkRedTag, RedTag]", _locals (s.getAllSubstitutionMembers (_t ("Tag"))).toString ());
    assertEquals ("[" + _t ("RedTag") + "]", s.getElement (_t ("DarkRedTag")).getSubstitutionGroups ().toString ());
    assertTrue (s.getElement (_t ("Tag")).isAbstract ());
  }

  @Test
  public void testConcurrentWalks () throws Exception
  {
    final XsdSchemaView s = _compileMain ();
    final ExecutorService aExecutor = Executors.newFixedThreadPool (8);
    try
    {
      final ICommonsList <Future <?>> aFutures = new CommonsArrayList <> ();
      for (int g = 0; g < 8; g++)
        aFutures.add (aExecutor.submit ( () -> {
          for (int n = 0; n < 50; n++)
          {
            final XsdComplexTypeView aInvoice = (XsdComplexTypeView) s.getElement (_t ("Invoice")).getType ();
            for (final XsdParticleView p : aInvoice.getContentParticles ())
              if (p.term () instanceof final XsdElementView aElement)
              {
                aElement.getType ().getBuiltinBase ();
                if (aElement.getType () instanceof final XsdComplexTypeView aCT)
                {
                  aCT.getAttributes ();
                  aCT.getContentParticles ();
                }
              }
            s.getAllSubstitutionMembers (_t ("Tag"));
            s.getAllGlobalTypes ();
          }
        }));
      for (final Future <?> aFuture : aFutures)
        aFuture.get ();
    }
    finally
    {
      aExecutor.shutdownNow ();
    }
  }

  @Test
  public void testDocumentationAndFacets ()
  {
    final XsdSchema aSchema;
    try
    {
      aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (DOCS_SCHEMA, null)),
                                   EXsdVersion.V10,
                                   new XsdCompileOptions ().setKeepDocumentation (true));
    }
    catch (final XsdUnsupportedException ex)
    {
      Assume.assumeTrue ("needs the later phases: " + ex.getMessage (), false);
      return;
    }
    final XsdSchemaView s = aSchema.getView ();
    final XsdElementView aDoc = s.getElement (_t ("Doc"));
    final String sDocs = _text (aDoc.getDocumentation ());
    assertTrue (sDocs, sDocs.startsWith ("en|urn:src|The root"));
    assertTrue (sDocs, sDocs.contains ("// de||Das Wurzelelement."));
    final XsdComplexTypeView aCT = (XsdComplexTypeView) aDoc.getType ();
    assertEquals ("||The document type.", _text (aCT.getDocumentation ()));
    final ICommonsList <XsdParticleView> aParts = aCT.getContentParticles ();
    assertEquals ("||A local element.", _text (((XsdElementView) aParts.get (0).term ()).getDocumentation ()));
    // A reference: its own note on the particle, the declaration's on the element
    assertEquals ("||Used by reference.", _text (aParts.get (1).documentation ()));
    assertEquals ("||Declared globally.", _text (((XsdElementView) aParts.get (1).term ()).getDocumentation ()));
    XsdAttributeUseView aId = null;
    XsdAttributeUseView aLang = null;
    for (final XsdAttributeUseView a : aCT.getAttributes ())
      if ("id".equals (a.name ().local ()))
        aId = a;
      else
        aLang = a;
    assertEquals ("||The id.", _text (aId.documentation ()));
    assertEquals ("||Reference to lang.", _text (aLang.documentation ()));
    assertEquals ("||Language of the declaration.", _text (aLang.declaration ().getDocumentation ()));
    assertEquals ("||Base code.", _text (((XsdSimpleTypeView) s.getType (_t ("Base"))).getDocumentation ()));
    // Facets: nearest step wins, inherited ones included, patterns AND across steps
    final XsdFacetsView f = ((XsdSimpleTypeView) s.getType (_t ("Code"))).getFacets ();
    assertEquals (Integer.valueOf (5), f.maxLength ());
    assertEquals (Integer.valueOf (2), f.minLength ());
    assertNull (f.length ());
    assertEquals ("collapse", f.whiteSpace ());
    assertEquals ("[[A.*], [[A-Z]+, [A-Z]+X]]", f.patterns ().toString ());
    final XsdFacetsView a = ((XsdSimpleTypeView) s.getType (_t ("Amount"))).getFacets ();
    assertEquals (Integer.valueOf (8), a.totalDigits ());
    assertEquals (Integer.valueOf (2), a.fractionDigits ());
    assertEquals ("0", a.minInclusive ());
    assertEquals ("1000", a.maxExclusive ());
    assertNull (a.maxInclusive ());
  }

  @Test
  public void testCompileErrors ()
  {
    assertSame (EVerdict.INVALID, XsdTestSupport.compile ("<not-a-schema/>", null, EXsdVersion.V10).verdict ());
    assertSame (EVerdict.INVALID,
                XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"a\" type=\"xs:nope\"/></xs:schema>",
                                        null,
                                        EXsdVersion.V10)
                              .verdict ());
    // A reference to a type the schema does not define: a usable schema that
    // names the dangling reference
    final Compiled aDangling = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"a\" type=\"nope\"/></xs:schema>",
                                                       null,
                                                       EXsdVersion.V10);
    assertSame (aDangling.message (), EVerdict.VALID, aDangling.verdict ());
    assertEquals ("[element a]", aDangling.schema ().getView ().getAllUnresolved ().toString ());
  }

  /** A model without complex types, decided by the loader alone. */
  @Test
  public void testSimpleOnlyModel ()
  {
    final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:t=\"urn:t\" targetNamespace=\"urn:t\">\n" +
                           "  <xs:simpleType name=\"Codes\"><xs:annotation><xs:documentation xml:lang=\"en\">Codes.</xs:documentation></xs:annotation><xs:list itemType=\"xs:NMTOKEN\"/></xs:simpleType>\n" +
                           "  <xs:simpleType name=\"IdOrNum\"><xs:union memberTypes=\"xs:ID xs:integer\"><xs:simpleType><xs:list itemType=\"xs:date\"/></xs:simpleType></xs:union></xs:simpleType>\n" +
                           "  <xs:element name=\"Tag\" type=\"xs:string\" abstract=\"true\"/>\n" +
                           "  <xs:element name=\"RedTag\" substitutionGroup=\"t:Tag\"/>\n" +
                           "  <xs:element name=\"DarkRedTag\" type=\"xs:token\" substitutionGroup=\"t:RedTag\" nillable=\"1\"/>\n" +
                           "  <xs:element name=\"Any\"/>\n" +
                           "  <xs:element name=\"Missing\" type=\"t:Nope\"/>\n" +
                           "  <xs:attribute name=\"code\" type=\"t:Codes\"/>\n" +
                           "</xs:schema>";
    final XsdSchema aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, null)),
                                                 EXsdVersion.V10,
                                                 new XsdCompileOptions ().setKeepDocumentation (true));
    final XsdSchemaView s = aSchema.getView ();
    assertEquals ("[Any, DarkRedTag, Missing, RedTag, Tag]", _locals (s.getAllGlobalElements ()).toString ());
    assertEquals (2, s.getAllGlobalTypes ().size ());
    // the four implicit xsi attributes plus the declared one
    assertEquals (5, s.getAllGlobalAttributes ().size ());
    final XsdSimpleTypeView aCodes = (XsdSimpleTypeView) s.getType (_t ("Codes"));
    assertSame (EXsdVariety.LIST, aCodes.getVariety ());
    assertEquals (_xs ("NMTOKEN"), aCodes.getItemType ().getName ());
    assertEquals ("NMTOKEN", aCodes.getBuiltinBase ().local ());
    assertEquals ("en||Codes.", _text (aCodes.getDocumentation ()));
    assertEquals ("collapse", aCodes.getFacets ().whiteSpace ());
    final XsdSimpleTypeView aUnion = (XsdSimpleTypeView) s.getType (_t ("IdOrNum"));
    assertSame (EXsdVariety.UNION, aUnion.getVariety ());
    assertEquals (3, aUnion.getMemberTypes ().size ());
    assertEquals (_xs ("anySimpleType"), aUnion.getBuiltinBase ());
    assertTrue (aUnion.getMemberTypes ().get (2).getName ().isZero ());
    // the substitution group head's type is inherited by a member without type
    assertEquals (_xs ("string"), s.getElement (_t ("RedTag")).getType ().getName ());
    assertEquals ("[DarkRedTag, RedTag]", _locals (s.getAllSubstitutionMembers (_t ("Tag"))).toString ());
    assertTrue (s.getElement (_t ("DarkRedTag")).isNillable ());
    assertFalse (s.getElement (_t ("RedTag")).isNillable ());
    // an element without type is xs:anyType
    final IXsdTypeDefinitionView aAny = s.getElement (_t ("Any")).getType ();
    assertEquals (XsdComplexType.ANY_TYPE_NAME, aAny.getName ());
    assertSame (EXsdContentKind.MIXED, ((XsdComplexTypeView) aAny).getContentKind ());
    assertEquals ("[element {urn:t}Missing]", s.getAllUnresolved ().toString ());
    // views are cached
    assertSame (s.getElement (_t ("Tag")), s.getElement (_t ("Tag")));
    assertNull (s.getElement (_t ("NoSuch")));
    assertEquals (_xs ("decimal"), s.getType (_xs ("decimal")).getName ());
    assertEquals (XsdComplexType.ANY_TYPE_NAME, s.getType (_xs ("anyType")).getName ());
  }
}
