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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Instance validity per the validation rules of Structures section 3.3.4
 * (cvc-elt), 3.4.4 (cvc-complex-type), 3.10.4 (cvc-wildcard), 3.15.4 / 3.3.4
 * for ID/IDREF (cvc-id) and 3.9.4 particles with substitution groups (go-xslt
 * <code>instance_spec_test.go</code>). While the simple type and content model
 * phases are stubs a case may be undecidable; a decided verdict must be the
 * expected one.
 *
 * @author Philip Helger
 */
public final class XsdInstanceValidationTest
{
  /** Declares the global components the instance tests use. Any global element may serve as the validation root. */
  private static final String PRELUDE = """
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:complexType name="Base"><xs:sequence><xs:element name="a" type="xs:string"/></xs:sequence></xs:complexType>
  <xs:complexType name="Ext"><xs:complexContent><xs:extension base="Base"><xs:sequence><xs:element name="b" type="xs:int"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>
  <xs:complexType name="Res"><xs:complexContent><xs:restriction base="Base"><xs:sequence><xs:element name="a" type="xs:string" fixed="r"/></xs:sequence></xs:restriction></xs:complexContent></xs:complexType>
  <xs:complexType name="NoExt" block="extension"><xs:sequence><xs:element name="a" type="xs:string"/></xs:sequence></xs:complexType>
  <xs:complexType name="NoExtD"><xs:complexContent><xs:extension base="NoExt"><xs:sequence><xs:element name="b" type="xs:int"/></xs:sequence></xs:extension></xs:complexContent></xs:complexType>
  <xs:complexType name="Abs" abstract="true"><xs:sequence/></xs:complexType>
  <xs:complexType name="Conc"><xs:complexContent><xs:extension base="Abs"/></xs:complexContent></xs:complexType>
  <xs:simpleType name="IntOrDate"><xs:union memberTypes="xs:int xs:date"/></xs:simpleType>

  <xs:element name="eBase" type="Base"/>
  <xs:element name="eBlocked" type="Base" block="extension"/>
  <xs:element name="eNoExt" type="NoExt"/>
  <xs:element name="eAbs" type="Base" abstract="true"/>
  <xs:element name="eAbsType" type="Abs"/>
  <xs:element name="eNil" type="xs:int" nillable="true"/>
  <xs:element name="eNilFixed" type="xs:int" nillable="true" fixed="5"/>
  <xs:element name="eInt" type="xs:int"/>
  <xs:element name="eUnion" type="IntOrDate"/>
  <xs:element name="eNilCT" type="Base" nillable="true"/>
  <xs:element name="eDef" type="xs:int" default="7"/>
  <xs:element name="eFix" type="xs:string" fixed="x"/>
  <xs:element name="eFixCT" fixed="x"><xs:complexType mixed="true"><xs:sequence><xs:element name="a" minOccurs="0"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eMixed"><xs:complexType mixed="true"><xs:sequence><xs:element name="a" type="xs:string" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eEmpty"><xs:complexType/></xs:element>
  <xs:element name="eAnyType"/>
  <xs:element name="eSC"><xs:complexType><xs:simpleContent><xs:extension base="xs:int"><xs:attribute name="u" type="xs:int"/></xs:extension></xs:simpleContent></xs:complexType></xs:element>
  <xs:element name="eAttrs"><xs:complexType>
    <xs:attribute name="req" type="xs:int" use="required"/>
    <xs:attribute name="opt" type="xs:string" default="d"/>
    <xs:attribute name="fix" type="xs:string" fixed="f"/>
    <xs:attribute name="no" type="xs:string" use="prohibited"/>
  </xs:complexType></xs:element>
  <xs:element name="eAnyAttr"><xs:complexType><xs:anyAttribute namespace="##other" processContents="lax"/></xs:complexType></xs:element>
  <xs:element name="eAnyAttrStrict"><xs:complexType><xs:anyAttribute processContents="strict"/></xs:complexType></xs:element>
  <xs:attribute name="gAttr" type="xs:int"/>
  <xs:element name="eStrict"><xs:complexType><xs:sequence><xs:any processContents="strict" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eLax"><xs:complexType><xs:sequence><xs:any processContents="lax" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eSkip"><xs:complexType><xs:sequence><xs:any processContents="skip" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eOther"><xs:complexType><xs:sequence><xs:any namespace="##other" processContents="lax"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eList"><xs:complexType><xs:sequence><xs:any namespace="urn:a ##local" processContents="skip"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="eIDs"><xs:complexType><xs:sequence><xs:element name="item" maxOccurs="unbounded"><xs:complexType>
    <xs:attribute name="id" type="xs:ID"/><xs:attribute name="ref" type="xs:IDREF"/><xs:attribute name="refs" type="xs:IDREFS"/>
  </xs:complexType></xs:element><xs:element name="cid" type="xs:ID" minOccurs="0" maxOccurs="unbounded"/></xs:sequence></xs:complexType></xs:element>
  <xs:element name="h" type="Base"/>
  <xs:element name="m" type="Ext" substitutionGroup="h"/>
  <xs:element name="mm" substitutionGroup="m"/>
  <xs:element name="hb" type="Base" block="substitution"/>
  <xs:element name="mb" substitutionGroup="hb"/>
  <xs:element name="hx" type="Base" block="extension"/>
  <xs:element name="mx" type="Ext" substitutionGroup="hx"/>
  <xs:element name="ha" type="Base" abstract="true"/>
  <xs:element name="ma" substitutionGroup="ha"/>
  <xs:element name="eHold"><xs:complexType><xs:sequence>
    <xs:element ref="h" minOccurs="0"/><xs:element ref="hb" minOccurs="0"/><xs:element ref="hx" minOccurs="0"/><xs:element ref="ha" minOccurs="0"/>
  </xs:sequence></xs:complexType></xs:element>
  <xs:element name="eChoice"><xs:complexType><xs:choice minOccurs="2" maxOccurs="3"><xs:element name="p"/><xs:element name="q"/></xs:choice></xs:complexType></xs:element>
  <xs:element name="eAll"><xs:complexType><xs:all><xs:element name="p"/><xs:element name="q" minOccurs="0"/></xs:all></xs:complexType></xs:element>
</xs:schema>""";

  private static final String XSI = " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

  private record Inst (String name, String instance, boolean valid)
  {}

  private static final Inst [] CASES = {
    new Inst ("plain", "<eBase><a>x</a></eBase>", true),
    new Inst ("undeclared-root", "<nope/>", false),
    new Inst ("root-wrong-namespace", "<eBase xmlns=\"urn:x\"><a>x</a></eBase>", false),
    new Inst ("xsi-type-extension", "<eBase" + XSI + " xsi:type=\"Ext\"><a>x</a><b>1</b></eBase>", true),
    new Inst ("xsi-type-extension-incomplete", "<eBase" + XSI + " xsi:type=\"Ext\"><a>x</a></eBase>", false),
    new Inst ("xsi-type-restriction", "<eBase" + XSI + " xsi:type=\"Res\"><a>r</a></eBase>", true),
    new Inst ("xsi-type-restriction-fixed", "<eBase" + XSI + " xsi:type=\"Res\"><a>x</a></eBase>", false),
    new Inst ("xsi-type-not-derived", "<eBase" + XSI + " xsi:type=\"xs:int\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">1</eBase>", false),
    new Inst ("xsi-type-unknown", "<eBase" + XSI + " xsi:type=\"Nope\"><a>x</a></eBase>", false),
    new Inst ("xsi-type-not-qname", "<eBase" + XSI + " xsi:type=\"1x\"><a>x</a></eBase>", false),
    new Inst ("xsi-type-blocked-by-element", "<eBlocked" + XSI + " xsi:type=\"Ext\"><a>x</a><b>1</b></eBlocked>", false),
    new Inst ("xsi-type-restriction-not-blocked", "<eBlocked" + XSI + " xsi:type=\"Res\"><a>r</a></eBlocked>", true),
    new Inst ("xsi-type-blocked-by-type", "<eNoExt" + XSI + " xsi:type=\"NoExtD\"><a>x</a><b>1</b></eNoExt>", false),
    new Inst ("xsi-type-simple-derived", "<eInt" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:short\">5</eInt>", true),
    new Inst ("xsi-type-simple-derived-range", "<eInt" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:short\">100000</eInt>", false),
    new Inst ("xsi-type-simple-not-derived", "<eInt" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:string\">5</eInt>", false),
    new Inst ("xsi-type-union-member", "<eUnion" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:int\">5</eUnion>", true),
    new Inst ("xsi-type-union-member-value", "<eUnion" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:int\">2020-01-01</eUnion>", false),
    new Inst ("union-value", "<eUnion>2020-01-01</eUnion>", true),
    new Inst ("anytype-xsi-type", "<eAnyType" + XSI + " xsi:type=\"Base\"><a>x</a></eAnyType>", true),
    new Inst ("anytype-xsi-type-violated", "<eAnyType" + XSI + " xsi:type=\"Base\"><z/></eAnyType>", false),
    new Inst ("abstract-element", "<eAbs><a>x</a></eAbs>", false),
    new Inst ("abstract-type", "<eAbsType/>", false),
    new Inst ("abstract-type-xsi-concrete", "<eAbsType" + XSI + " xsi:type=\"Conc\"/>", true),
    new Inst ("nil-empty", "<eNil" + XSI + " xsi:nil=\"true\"/>", true),
    new Inst ("nil-numeric-true", "<eNil" + XSI + " xsi:nil=\"1\"/>", true),
    new Inst ("nil-with-content", "<eNil" + XSI + " xsi:nil=\"true\">3</eNil>", false),
    new Inst ("nil-false-content", "<eNil" + XSI + " xsi:nil=\"false\">3</eNil>", true),
    new Inst ("nil-false-empty", "<eNil" + XSI + " xsi:nil=\"false\"/>", false),
    new Inst ("nil-bad-value", "<eNil" + XSI + " xsi:nil=\"maybe\"/>", false),
    new Inst ("nil-not-nillable", "<eInt" + XSI + " xsi:nil=\"true\"/>", false),
    new Inst ("nil-and-fixed", "<eNilFixed" + XSI + " xsi:nil=\"true\"/>", false),
    new Inst ("nil-complex-empty", "<eNilCT" + XSI + " xsi:nil=\"true\"/>", true),
    new Inst ("nil-complex-with-child", "<eNilCT" + XSI + " xsi:nil=\"true\"><a>x</a></eNilCT>", false),
    new Inst ("nil-complex-missing-child", "<eNilCT/>", false),
    new Inst ("default-applied", "<eDef/>", true),
    new Inst ("default-invalid-given", "<eDef>x</eDef>", false),
    new Inst ("fixed-match", "<eFix>x</eFix>", true),
    new Inst ("fixed-empty", "<eFix/>", true),
    new Inst ("fixed-mismatch", "<eFix>y</eFix>", false),
    new Inst ("fixed-mixed-match", "<eFixCT>x</eFixCT>", true),
    new Inst ("fixed-mixed-mismatch", "<eFixCT>y</eFixCT>", false),
    new Inst ("fixed-mixed-with-child", "<eFixCT>x<a/></eFixCT>", false),
    new Inst ("mixed-text-and-elements", "<eMixed>t<a>x</a>u<a>y</a>v</eMixed>", true),
    new Inst ("mixed-missing-element", "<eMixed>t</eMixed>", false),
    new Inst ("empty-ok", "<eEmpty/>", true),
    new Inst ("empty-with-text", "<eEmpty>x</eEmpty>", false),
    new Inst ("empty-with-child", "<eEmpty><a/></eEmpty>", false),
    new Inst ("element-only-text", "<eBase>t<a>x</a></eBase>", false),
    new Inst ("element-only-comment-pi", "<eBase><!-- c --><?p x?><a>x</a></eBase>", true),
    new Inst ("anytype-anything", "<eAnyType z=\"1\">t<q><r/></q></eAnyType>", true),
    new Inst ("simple-content-ok", "<eSC u=\"1\">5</eSC>", true),
    new Inst ("simple-content-bad-attr", "<eSC u=\"x\">5</eSC>", false),
    new Inst ("simple-content-bad-value", "<eSC>x</eSC>", false),
    new Inst ("simple-content-child", "<eSC><a/></eSC>", false),
    new Inst ("attrs-required-present", "<eAttrs req=\"1\"/>", true),
    new Inst ("attrs-required-missing", "<eAttrs/>", false),
    new Inst ("attrs-required-bad", "<eAttrs req=\"x\"/>", false),
    new Inst ("attrs-fixed-match", "<eAttrs req=\"1\" fix=\"f\"/>", true),
    new Inst ("attrs-fixed-mismatch", "<eAttrs req=\"1\" fix=\"g\"/>", false),
    new Inst ("attrs-prohibited", "<eAttrs req=\"1\" no=\"z\"/>", false),
    new Inst ("attrs-undeclared", "<eAttrs req=\"1\" extra=\"z\"/>", false),
    new Inst ("attrs-xsi-allowed", "<eAttrs req=\"1\"" + XSI + " xsi:noNamespaceSchemaLocation=\"x.xsd\"/>", true),
    new Inst ("attrs-xmlns-ignored", "<eAttrs req=\"1\" xmlns:p=\"urn:p\"/>", true),
    new Inst ("any-attr-other", "<eAnyAttr xmlns:p=\"urn:p\" p:z=\"1\"/>", true),
    new Inst ("any-attr-other-local", "<eAnyAttr z=\"1\"/>", false),
    new Inst ("any-attr-strict-declared", "<eAnyAttrStrict gAttr=\"1\"/>", true),
    new Inst ("any-attr-strict-declared-bad", "<eAnyAttrStrict gAttr=\"x\"/>", false),
    new Inst ("any-attr-strict-undeclared", "<eAnyAttrStrict zz=\"1\"/>", false),
    new Inst ("strict-declared", "<eStrict><eDef>7</eDef><eFix>x</eFix></eStrict>", true),
    new Inst ("strict-declared-invalid", "<eStrict><eDef>x</eDef></eStrict>", false),
    new Inst ("strict-undeclared", "<eStrict><zz/></eStrict>", false),
    new Inst ("strict-xsi-type-undeclared", "<eStrict><zz" + XSI + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xsi:type=\"xs:int\">1</zz></eStrict>", true),
    new Inst ("lax-undeclared", "<eLax><zz><yy/></zz></eLax>", true),
    new Inst ("lax-undeclared-with-declared-inside", "<eLax><zz><eDef>x</eDef></zz></eLax>", false),
    new Inst ("lax-declared-invalid", "<eLax><eDef>x</eDef></eLax>", false),
    new Inst ("skip-declared-invalid", "<eSkip><eDef>x</eDef></eSkip>", true),
    new Inst ("other-no-namespace", "<eOther><zz/></eOther>", false),
    new Inst ("other-namespaced", "<eOther><p:zz xmlns:p=\"urn:p\"/></eOther>", true),
    new Inst ("list-local", "<eList><zz/></eList>", true),
    new Inst ("list-listed", "<eList><p:zz xmlns:p=\"urn:a\"/></eList>", true),
    new Inst ("list-unlisted", "<eList><p:zz xmlns:p=\"urn:b\"/></eList>", false),
    new Inst ("ids-ok", "<eIDs><item id=\"a\"/><item id=\"b\" ref=\"a\" refs=\"a b\"/></eIDs>", true),
    new Inst ("ids-duplicate", "<eIDs><item id=\"a\"/><item id=\"a\"/></eIDs>", false),
    new Inst ("ids-dangling-ref", "<eIDs><item id=\"a\" ref=\"b\"/></eIDs>", false),
    new Inst ("ids-dangling-refs", "<eIDs><item id=\"a\" refs=\"a c\"/></eIDs>", false),
    new Inst ("ids-forward-ref", "<eIDs><item ref=\"z\"/><item id=\"z\"/></eIDs>", true),
    new Inst ("ids-bad-lexical", "<eIDs><item id=\"1a\"/></eIDs>", false),
    new Inst ("ids-element-content", "<eIDs><item ref=\"k\"/><cid>k</cid></eIDs>", true),
    new Inst ("ids-element-content-dup", "<eIDs><item id=\"k\"/><cid>k</cid></eIDs>", false),
    new Inst ("subst-head", "<eHold><h><a>x</a></h></eHold>", true),
    new Inst ("subst-member", "<eHold><m><a>x</a><b>1</b></m></eHold>", true),
    new Inst ("subst-transitive-member", "<eHold><mm><a>x</a><b>1</b></mm></eHold>", true),
    new Inst ("subst-member-invalid-content", "<eHold><m><a>x</a></m></eHold>", false),
    new Inst ("subst-blocked-substitution", "<eHold><mb><a>x</a></mb></eHold>", false),
    new Inst ("subst-blocked-extension", "<eHold><mx><a>x</a><b>1</b></mx></eHold>", false),
    new Inst ("subst-abstract-head", "<eHold><ha><a>x</a></ha></eHold>", false),
    new Inst ("subst-abstract-head-member", "<eHold><ma><a>x</a></ma></eHold>", true),
    new Inst ("subst-order", "<eHold><hb><a>x</a></hb><m><a>x</a><b>1</b></m></eHold>", false),
    new Inst ("choice-min", "<eChoice><p/></eChoice>", false),
    new Inst ("choice-ok", "<eChoice><p/><q/><p/></eChoice>", true),
    new Inst ("choice-max", "<eChoice><p/><q/><p/><q/></eChoice>", false),
    new Inst ("all-any-order", "<eAll><q/><p/></eAll>", true),
    new Inst ("all-optional-absent", "<eAll><p/></eAll>", true),
    new Inst ("all-missing-required", "<eAll><q/></eAll>", false),
    new Inst ("all-repeated", "<eAll><p/><p/></eAll>", false),
    new Inst ("all-unknown", "<eAll><p/><z/></eAll>", false)
  };

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testInstanceValidation ()
  {
    final Tally aTally = new Tally ();
    for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
    {
      final Compiled aCompiled = XsdTestSupport.compile (PRELUDE, null, eVersion);
      if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
      {
        aTally.m_nUndecided += CASES.length;
        continue;
      }
      assertSame (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
      for (final Inst aCase : CASES)
      {
        final EVerdict eVerdict = XsdTestSupport.validate (aCompiled.schema (), aCase.instance ());
        if (eVerdict == EVerdict.UNSUPPORTED)
        {
          aTally.m_nUndecided++;
          continue;
        }
        aTally.m_nDecided++;
        if ((eVerdict == EVerdict.VALID) != aCase.valid ())
          aTally.m_aWrong.add (aCase.name () + "/" + eVersion.getID () + ": valid=" + (eVerdict == EVerdict.VALID));
      }
    }
    aTally.log ("instance validation");
    assertTrue (aTally.m_aWrong.toString (), aTally.m_aWrong.isEmpty ());
  }

  /** A not-well-formed instance and one without a document element are invalid, never "no verdict". */
  @Test
  public void testInstanceNotWellFormed ()
  {
    final Compiled aCompiled = XsdTestSupport.compile (PRELUDE, null, EXsdVersion.V10);
    if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
      return;
    for (final String sInstance : new String [] { "<eBase><a>x</eBase>", "", "<!-- only a comment -->" })
    {
      final XsdValidationResult aResult = aCompiled.schema ().validateText (sInstance);
      assertFalse (sInstance, aResult.valid ());
      assertFalse (sInstance, aResult.errors ().isEmpty ());
    }
  }

  @Test
  public void testErrorLocation ()
  {
    final Compiled aCompiled = XsdTestSupport.compile (PRELUDE, null, EXsdVersion.V10);
    if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
      return;
    final XsdValidationResult aResult;
    try
    {
      aResult = aCompiled.schema ().validateText ("<eHold>\n  <h><a>x</a></h>\n  <hb><b/></hb>\n</eHold>");
    }
    catch (final XsdUnsupportedException ex)
    {
      return;
    }
    assertFalse (aResult.valid ());
    final XsdError aError = aResult.errors ().get (0);
    assertEquals (3, aError.line ());
    assertEquals ("/eHold[1]/hb[1]", aError.path ());
  }

  /**
   * Deeply nested instance elements never overflow the stack. The caller's
   * thread has a small stack here, so everything above the inline bound runs on
   * the validator's own big-stack thread.
   */
  @Test
  public void testDeepNesting () throws InterruptedException
  {
    final String sSchema = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="n"><xs:complexType><xs:sequence>
          <xs:element ref="n" minOccurs="0"/>
        </xs:sequence></xs:complexType></xs:element>
      </xs:schema>""";
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
      return;
    final ICommonsList <Throwable> aFailures = new CommonsArrayList <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        for (final int nDepth : new int [] { 50, XsdValidationRun.MAX_DEPTH, XsdValidationRun.MAX_DEPTH + 1, 4000, 9000 })
        {
          final StringBuilder aSB = new StringBuilder ();
          for (int i = 0; i < nDepth; i++)
            aSB.append ("<n>");
          for (int i = 0; i < nDepth; i++)
            aSB.append ("</n>");
          assertSame ("depth " + nDepth, EVerdict.VALID, XsdTestSupport.validate (aCompiled.schema (), aSB.toString ()));
          // The innermost element is wrong
          final String sWrong = aSB.toString ().replace ("</n></n>", "<x/></n></n>");
          assertSame ("depth " + nDepth, EVerdict.INVALID, XsdTestSupport.validate (aCompiled.schema (), sWrong));
        }
      }
      catch (final Throwable ex)
      {
        aFailures.add (ex);
      }
    }, "deep-nesting", 512 * 1024);
    aThread.start ();
    aThread.join ();
    assertTrue (aFailures.toString (), aFailures.isEmpty ());
  }

  @Test
  public void testSchemaLocationHint () throws IOException
  {
    final Path aDir = m_aTempFolder.newFolder ().toPath ();
    Files.writeString (aDir.resolve ("hint.xsd"),
                       "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"h\" type=\"xs:int\"/></xs:schema>");
    Files.writeString (aDir.resolve ("ok.xml"),
                       "<h xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"hint.xsd\">5</h>");
    Files.writeString (aDir.resolve ("bad.xml"),
                       "<h xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"hint.xsd\">x</h>");
    Files.writeString (aDir.resolve ("broken.xml"),
                       "<h xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"missing.xsd\">5</h>");
    final ICommonsList <XsdSchemaSource> aNone = new CommonsArrayList <> ();
    final XsdSchema aSchema;
    try
    {
      aSchema = XsdSchema.compile (aNone, EXsdVersion.V10, null);
    }
    catch (final XsdUnsupportedException ex)
    {
      return;
    }
    // The empty schema set is governed by the hints of the instance, found
    // next to the instance
    try
    {
      assertTrue (aSchema.validate (aDir.resolve ("ok.xml")).valid ());
      assertFalse (aSchema.validate (aDir.resolve ("bad.xml")).valid ());
      // A hint that cannot be read is ignored: the element is then undeclared
      assertFalse (aSchema.validate (aDir.resolve ("broken.xml")).valid ());
    }
    catch (final XsdUnsupportedException ex)
    {
      // simple types of a later phase
    }
  }

  /** XSD 1.0 section 4.3.2: a hint after an element or attribute of its namespace is an error. */
  @Test
  public void testLateSchemaLocationHint ()
  {
    final String sSchema = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="r"><xs:complexType><xs:sequence><xs:any processContents="skip"/></xs:sequence></xs:complexType></xs:element>
      </xs:schema>""";
    final String sInstance = "<r xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><c xsi:noNamespaceSchemaLocation=\"x.xsd\"/></r>";
    final Compiled a10 = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    if (a10.verdict () != EVerdict.UNSUPPORTED)
    {
      final XsdValidationResult aResult = a10.schema ().validateText (sInstance);
      assertFalse (aResult.valid ());
      assertTrue (aResult.errors ().get (0).message (), aResult.errors ().get (0).message ().contains ("4.3.2"));
      assertEquals ("/r[1]/c[1]", aResult.errors ().get (0).path ());
    }
    final Compiled a11 = XsdTestSupport.compile (sSchema, null, EXsdVersion.V11);
    if (a11.verdict () != EVerdict.UNSUPPORTED)
    {
      final EVerdict eVerdict = XsdTestSupport.validate (a11.schema (), sInstance);
      assertTrue (eVerdict != EVerdict.INVALID);
    }
  }
}
