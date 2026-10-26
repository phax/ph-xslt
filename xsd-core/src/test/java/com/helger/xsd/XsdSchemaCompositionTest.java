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
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Case;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.DocCheck;
import com.helger.xsd.XsdTestSupport.EVerdict;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Composition of schemas from several documents: xs:include (chameleon
 * include), xs:import, xs:redefine, XSD 1.1 xs:override, conditional
 * inclusion (vc:*), and xsi:schemaLocation hints. Ported from go-xslt
 * <code>imports_override_test.go</code> and <code>xsd_test.go</code>.
 *
 * @author Philip Helger
 */
public final class XsdSchemaCompositionTest
{
  private static final String XS_TNS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:t\" xmlns:t=\"urn:t\">";
  private static final String XS_NO_NS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";
  private static final String XSI = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
  private static final int VER_BOTH = XsdTestSupport.VER_BOTH;
  private static final int VER_10 = XsdTestSupport.VER_10;
  private static final int VER_11 = XsdTestSupport.VER_11;
  private static final int SKIP_BUG = XsdTestSupport.SKIP_BUG;

  private static final Case [] CASES = {
    new Case ("chameleon-include", VER_BOTH, XS_TNS + "<xs:include schemaLocation=\"common.xsd\"/><xs:element name=\"root\" type=\"t:Code\"/></xs:schema>", true,
                 new DocCheck ("<t:item xmlns:t=\"urn:t\">ab</t:item>", true),
                 new DocCheck ("<t:item xmlns:t=\"urn:t\">abc</t:item>", false),
                 new DocCheck ("<item>ab</item>", false),
                 new DocCheck ("<t:pair xmlns:t=\"urn:t\"><t:item>ab</t:item><t:item>cd</t:item></t:pair>", true),
                 new DocCheck ("<t:code2 xmlns:t=\"urn:t\">ab</t:code2>", true),
                 new DocCheck ("<t:root xmlns:t=\"urn:t\">xy</t:root>", true)),
    new Case ("plain-include", VER_BOTH, XS_NO_NS + "<xs:include schemaLocation=\"common.xsd\"/></xs:schema>", true,
                 new DocCheck ("<item>ab</item>", true),
                 new DocCheck ("<pair><item>ab</item><item>a</item></pair>", false)),
    new Case ("include-wrong-namespace", VER_BOTH, XS_TNS + "<xs:include schemaLocation=\"wrongns.xsd\"/></xs:schema>", false),
    new Case ("include-without-location", VER_BOTH, XS_TNS + "<xs:include/></xs:schema>", false),
    new Case ("import", VER_BOTH, XS_TNS + "<xs:import namespace=\"urn:o\" schemaLocation=\"other.xsd\"/>\n" +
               "		  <xs:element name=\"root\"><xs:complexType><xs:sequence><xs:element ref=\"o:thing\" xmlns:o=\"urn:o\"/></xs:sequence><xs:attribute ref=\"o:flag\" xmlns:o=\"urn:o\"/></xs:complexType></xs:element></xs:schema>", true,
                 new DocCheck ("<t:root xmlns:t=\"urn:t\" xmlns:o=\"urn:o\" o:flag=\"true\"><o:thing><o:n>1</o:n></o:thing></t:root>", true),
                 new DocCheck ("<t:root xmlns:t=\"urn:t\" xmlns:o=\"urn:o\"><o:thing><o:n>x</o:n></o:thing></t:root>", false),
                 new DocCheck ("<t:root xmlns:t=\"urn:t\" xmlns:o=\"urn:o\" o:flag=\"maybe\"><o:thing><o:n>1</o:n></o:thing></t:root>", false),
                 new DocCheck ("<o:thing xmlns:o=\"urn:o\"><o:n>2</o:n></o:thing>", true)),
    new Case ("import-type-derivation", VER_BOTH, XS_TNS + "<xs:import namespace=\"urn:o\" schemaLocation=\"other.xsd\"/>\n" +
               "		  <xs:complexType name=\"T2\" xmlns:o=\"urn:o\"><xs:complexContent><xs:extension base=\"o:T\"><xs:sequence><xs:element name=\"m\" type=\"xs:int\" form=\"qualified\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
               "		  <xs:element name=\"r\" type=\"t:T2\"/></xs:schema>", true,
                 new DocCheck ("<t:r xmlns:t=\"urn:t\" xmlns:o=\"urn:o\"><o:n>1</o:n><t:m>2</t:m></t:r>", true),
                 new DocCheck ("<t:r xmlns:t=\"urn:t\" xmlns:o=\"urn:o\"><o:n>1</o:n></t:r>", false)),
    new Case ("import-namespace-mismatch", VER_BOTH, XS_TNS + "<xs:import namespace=\"urn:x\" schemaLocation=\"other.xsd\"/></xs:schema>", false),
    new Case ("import-own-namespace", VER_BOTH, XS_TNS + "<xs:import namespace=\"urn:t\"/></xs:schema>", false),
    new Case ("import-no-namespace-from-no-namespace", VER_BOTH, XS_NO_NS + "<xs:import/></xs:schema>", false),
    new Case ("reference-without-import", VER_BOTH, XS_TNS + "<xs:element name=\"r\" type=\"o:T\" xmlns:o=\"urn:o\"/></xs:schema>", false),
    new Case ("redefine", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:complexType name=\"P\"><xs:complexContent><xs:extension base=\"P\"><xs:sequence><xs:element name=\"b\" type=\"xs:int\"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>\n" +
               "		    <xs:simpleType name=\"S\"><xs:restriction base=\"S\"><xs:maxLength value=\"3\"/></xs:restriction></xs:simpleType>\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:group ref=\"G\"/><xs:element name=\"y\" type=\"xs:string\"/></xs:sequence></xs:group>\n" +
               "		    <xs:attributeGroup name=\"AG\"><xs:attributeGroup ref=\"AG\"/><xs:attribute name=\"v\" type=\"xs:int\"/></xs:attributeGroup>\n" +
               "		  </xs:redefine></xs:schema>", true,
                 new DocCheck ("<p><a>x</a><b>1</b></p>", true),
                 new DocCheck ("<p><a>x</a></p>", false),
                 new DocCheck ("<s>abc</s>", true),
                 new DocCheck ("<s>abcd</s>", false),
                 new DocCheck ("<g u=\"1\" v=\"2\"><x/><y/></g>", true),
                 new DocCheck ("<g><x/></g>", false),
                 new DocCheck ("<g v=\"z\"><x/><y/></g>", false)),
    new Case ("redefine-attribute-group-restricting", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:attributeGroup name=\"AG\"><xs:attribute name=\"u\" type=\"xs:string\"/></xs:attributeGroup>\n" +
               "		  </xs:redefine></xs:schema>", true,
                 new DocCheck ("<g u=\"1\"><x/></g>", true),
                 new DocCheck ("<g w=\"1\"><x/></g>", false)),
    new Case ("redefine-group-restricting", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:element name=\"x\" type=\"xs:string\" minOccurs=\"1\"/></xs:sequence></xs:group>\n" +
               "		  </xs:redefine></xs:schema>", true,
                 new DocCheck ("<g><x/></g>", true)),
    new Case ("redefine-type-not-self-derived", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:simpleType name=\"S\"><xs:restriction base=\"xs:string\"/></xs:simpleType></xs:redefine></xs:schema>", false),
    new Case ("redefine-complex-not-self-derived", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:complexType name=\"P\"><xs:sequence><xs:element name=\"z\"/></xs:sequence></xs:complexType></xs:redefine></xs:schema>", false),
    new Case ("redefine-group-self-ref-occurs", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:group ref=\"G\" maxOccurs=\"2\"/></xs:sequence></xs:group></xs:redefine></xs:schema>", false),
    new Case ("redefine-group-self-ref-twice", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:group ref=\"G\"/><xs:group ref=\"G\"/></xs:sequence></xs:group></xs:redefine></xs:schema>", false),
    new Case ("redefine-group-not-restriction", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:element name=\"q\"/></xs:sequence></xs:group></xs:redefine></xs:schema>", false),
    new Case ("redefine-attribute-group-not-restriction", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:attributeGroup name=\"AG\"><xs:attribute name=\"zz\"/></xs:attributeGroup></xs:redefine></xs:schema>", false),
    new Case ("redefine-missing-component", VER_BOTH, XS_NO_NS + "<xs:redefine schemaLocation=\"redef-base.xsd\">\n" +
               "		    <xs:simpleType name=\"Nope\"><xs:restriction base=\"Nope\"/></xs:simpleType></xs:redefine></xs:schema>", false),
    new Case ("redefine-namespace-mismatch", VER_BOTH, XS_TNS + "<xs:redefine schemaLocation=\"wrongns.xsd\"/></xs:schema>", false),
    new Case ("override-1.0", VER_10 + SKIP_BUG, XS_NO_NS + "<xs:override schemaLocation=\"override-base.xsd\"/></xs:schema>", false),
    new Case ("override", VER_11, XS_NO_NS + "<xs:override schemaLocation=\"override-base.xsd\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:string\"/>\n" +
               "		    <xs:element name=\"f\" type=\"xs:int\"/>\n" +
               "		    <xs:simpleType name=\"S\"><xs:restriction base=\"xs:string\"><xs:maxLength value=\"4\"/></xs:restriction></xs:simpleType>\n" +
               "		    <xs:complexType name=\"P\"><xs:sequence><xs:element name=\"b\"/></xs:sequence></xs:complexType>\n" +
               "		    <xs:group name=\"G\"><xs:sequence><xs:element name=\"y\"/></xs:sequence></xs:group>\n" +
               "		    <xs:attributeGroup name=\"AG\"><xs:attribute name=\"v\" type=\"xs:int\"/></xs:attributeGroup>\n" +
               "		    <xs:attribute name=\"ga\" type=\"xs:boolean\"/>\n" +
               "		    <xs:element name=\"absent\" type=\"xs:int\"/>\n" +
               "		  </xs:override></xs:schema>", true,
                 new DocCheck ("<e>abc</e>", true),
                 new DocCheck ("<f>12</f>", true),
                 new DocCheck ("<f>x</f>", false),
                 new DocCheck ("<s>abcd</s>", true),
                 new DocCheck ("<s>abcde</s>", false),
                 new DocCheck ("<p><b/></p>", true),
                 new DocCheck ("<p><a/></p>", false),
                 new DocCheck ("<g><y/></g>", true),
                 new DocCheck ("<g><x/></g>", false),
                 new DocCheck ("<ag v=\"1\"/>", true),
                 new DocCheck ("<ag u=\"1\"/>", false),
                 new DocCheck ("<absent>1</absent>", false)),
    new Case ("override-empty", VER_11, XS_NO_NS + "<xs:override schemaLocation=\"override-base.xsd\"/></xs:schema>", true,
                 new DocCheck ("<e>1</e>", true),
                 new DocCheck ("<e>abc</e>", false),
                 new DocCheck ("<f>abc</f>", true)),
    new Case ("override-duplicate", VER_11, XS_NO_NS + "<xs:override schemaLocation=\"override-base.xsd\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:string\"/><xs:element name=\"e\" type=\"xs:date\"/></xs:override></xs:schema>", false),
    new Case ("override-plus-include-same-doc", VER_11, XS_NO_NS + "<xs:override schemaLocation=\"override-base.xsd\"><xs:element name=\"e\" type=\"xs:string\"/></xs:override>\n" +
               "		  <xs:element name=\"top\"><xs:complexType><xs:sequence><xs:element ref=\"e\"/></xs:sequence></xs:complexType></xs:element></xs:schema>", true,
                 new DocCheck ("<top><e>abc</e></top>", true)),
    new Case ("override-namespace-mismatch", VER_11, XS_TNS + "<xs:override schemaLocation=\"wrongns.xsd\"/></xs:schema>", false),
    new Case ("vc-version-select-1.0", VER_10, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:int\" vc:minVersion=\"1.1\"/><xs:element name=\"e\" type=\"xs:string\" vc:maxVersion=\"1.1\"/></xs:schema>", true,
                 new DocCheck ("<e>abc</e>", true)),
    new Case ("vc-version-select-1.1", VER_11, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:int\" vc:minVersion=\"1.1\"/><xs:element name=\"e\" type=\"xs:string\" vc:maxVersion=\"1.1\"/></xs:schema>", true,
                 new DocCheck ("<e>abc</e>", false),
                 new DocCheck ("<e>12</e>", true)),
    new Case ("vc-type-available", VER_11, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:dateTimeStamp\" vc:typeAvailable=\"xs:dateTimeStamp\"/>\n" +
               "		    <xs:element name=\"e\" type=\"xs:dateTime\" vc:typeUnavailable=\"xs:dateTimeStamp\"/>\n" +
               "		    <xs:element name=\"g\" type=\"xs:int\" vc:typeAvailable=\"xs:noSuchType\"/>\n" +
               "		    <xs:element name=\"g\" type=\"xs:string\" vc:typeUnavailable=\"xs:noSuchType xs:int\"/></xs:schema>", true,
                 new DocCheck ("<e>2020-01-01T00:00:00</e>", false),
                 new DocCheck ("<e>2020-01-01T00:00:00Z</e>", true),
                 new DocCheck ("<g>abc</g>", true)),
    new Case ("vc-type-available-1.0", VER_10, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" type=\"xs:dateTimeStamp\" vc:typeAvailable=\"xs:dateTimeStamp\"/>\n" +
               "		    <xs:element name=\"e\" type=\"xs:dateTime\" vc:typeUnavailable=\"xs:dateTimeStamp\"/></xs:schema>", true,
                 new DocCheck ("<e>2020-01-01T00:00:00</e>", true)),
    new Case ("vc-facet-available", VER_BOTH, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\"><xs:simpleType><xs:restriction base=\"xs:string\">\n" +
               "		      <xs:maxLength value=\"2\" vc:facetAvailable=\"xs:maxLength\"/>\n" +
               "		      <xs:minLength value=\"5\" vc:facetUnavailable=\"xs:maxLength\"/>\n" +
               "		    </xs:restriction></xs:simpleType></xs:element></xs:schema>", true,
                 new DocCheck ("<e>ab</e>", true),
                 new DocCheck ("<e>abc</e>", false)),
    new Case ("vc-bad-version", VER_11, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" vc:minVersion=\"one\"/></xs:schema>", false),
    new Case ("vc-undeclared-prefix", VER_11, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\">\n" +
               "		    <xs:element name=\"e\" vc:typeAvailable=\"q:int\"/></xs:schema>", false)
 };

  /** The xsi:schemaLocation hint cases (go-xslt TestSchemaLocationHints). */
  private static final String HINT_BASE = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                                          "  <xs:element name=\"w\"><xs:complexType><xs:sequence><xs:any namespace=\"##other\" processContents=\"strict\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType></xs:element>\n" +
                                          "  <xs:element name=\"lw\"><xs:complexType><xs:sequence><xs:any processContents=\"lax\" maxOccurs=\"unbounded\"/></xs:sequence></xs:complexType></xs:element>\n" +
                                          "</xs:schema>";

  private static final Case [] HINT_CASES = {
    new Case ("hint-used", VER_BOTH, HINT_BASE, true,
              new DocCheck ("<w " + XSI + " xsi:schemaLocation=\"urn:h hint.xsd\"><h:hinted xmlns:h=\"urn:h\">1</h:hinted></w>", true)),
    new Case ("hint-value-checked", VER_BOTH, HINT_BASE, true,
              new DocCheck ("<w " + XSI + " xsi:schemaLocation=\"urn:h hint.xsd\"><h:hinted xmlns:h=\"urn:h\">x</h:hinted></w>", false)),
    new Case ("no-hint-strict-fails", VER_BOTH, HINT_BASE, true,
              new DocCheck ("<w><h:hinted xmlns:h=\"urn:h\">1</h:hinted></w>", false)),
    new Case ("missing-hint-ignored", VER_BOTH, HINT_BASE, true,
              new DocCheck ("<lw " + XSI + " xsi:noNamespaceSchemaLocation=\"nope.xsd\"><z/></lw>", true)),
    new Case ("late-hint-1.0", VER_10, HINT_BASE, true,
              new DocCheck ("<lw><h:hinted xmlns:h=\"urn:h\">1</h:hinted><x " + XSI + " xsi:schemaLocation=\"urn:h hint.xsd\"/></lw>", false)),
    new Case ("late-hint-1.1", VER_11, HINT_BASE, true,
              new DocCheck ("<lw><h:hinted xmlns:h=\"urn:h\">1</h:hinted><x " + XSI + " xsi:schemaLocation=\"urn:h hint.xsd\"/></lw>", true)),
    new Case ("early-hint-1.0", VER_10, HINT_BASE, true,
              new DocCheck ("<lw><x " + XSI + " xsi:schemaLocation=\"urn:h hint.xsd\"/><h:hinted xmlns:h=\"urn:h\">1</h:hinted></lw>", true)) };

  @Test
  public void testSchemaComposition ()
  {
    final Tally aTally = XsdTestSupport.runCases (CASES, XsdTestSupport.resourceDir ("imports"), false);
    aTally.log ("schema composition");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
    assertTrue (aTally.m_nDecided > 15);
  }

  @Test
  public void testSchemaLocationHints ()
  {
    final Tally aTally = XsdTestSupport.runCases (HINT_CASES, XsdTestSupport.resourceDir ("imports"), false);
    aTally.log ("schema location hints");
    assertTrue (String.join ("\n", aTally.m_aWrong), aTally.m_aWrong.isEmpty ());
  }

  /**
   * An xs:import whose location cannot be resolved is legal: its namespace's
   * components are simply unavailable (go-xslt
   * TestImportMissingLocationCompiles).
   */
  @Test
  public void testImportMissingLocationCompiles ()
  {
    final String sSchema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                           "  <xs:import namespace=\"urn:x\" schemaLocation=\"does-not-exist.xsd\"/>\n" +
                           "  <xs:element name=\"v\" type=\"xs:string\"/>\n" +
                           "</xs:schema>";
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    final EVerdict eInstance = XsdTestSupport.validate (aCompiled.schema (), "<v>hi</v>");
    assertTrue (eInstance == EVerdict.VALID || eInstance == EVerdict.UNSUPPORTED);
  }

  /** An absolute base directory resolves the same documents (go-xslt TestCompileAbsoluteBaseDir). */
  @Test
  public void testCompileAbsoluteBaseDir ()
  {
    final Path aAbs = XsdTestSupport.resourceDir ("imports").toAbsolutePath ();
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:include schemaLocation=\"common.xsd\"/></xs:schema>",
                                                       aAbs,
                                                       EXsdVersion.V10);
    assertTrue (aCompiled.message (), aCompiled.verdict () != EVerdict.INVALID);
    if (aCompiled.schema () != null)
    {
      final EVerdict eInstance = XsdTestSupport.validate (aCompiled.schema (), "<item>ab</item>");
      assertTrue (eInstance == EVerdict.VALID || eInstance == EVerdict.UNSUPPORTED);
    }
  }

  /** Without a base directory no relative location is followed: the include contributes nothing. */
  @Test
  public void testNoBaseDirFollowsNothing ()
  {
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:include schemaLocation=\"common.xsd\"/><xs:element name=\"e\" type=\"Code\"/></xs:schema>",
                                                       null,
                                                       EXsdVersion.V10);
    // Code is not defined: in XSD 1.0 a missing type is tolerated until used
    assertEquals (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    assertEquals ("[element e]", aCompiled.schema ().getView ().getAllUnresolved ().toString ());
  }
}
