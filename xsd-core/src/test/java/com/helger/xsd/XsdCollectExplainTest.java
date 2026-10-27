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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.context.EXsdVersion;

/**
 * Error collection, content-model explanations and the `msg:message`
 * convention (go-xslt <code>collect_explain_test.go</code> plus tests of the
 * port's own: first-error identity, error limit, naming and wording).
 *
 * @author Philip Helger
 */
public final class XsdCollectExplainTest
{
  private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";
  private static final String MSG = "xmlns:msg=\"" + XsdConstants.NS_MESSAGES + "\"";

  @NonNull
  private static XsdSchema _compile (@NonNull final String sSchema, @NonNull final EXsdVersion eVersion)
  {
    return XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, null)),
                              eVersion,
                              new XsdCompileOptions ());
  }

  @Nullable
  private static XsdError _byRule (@NonNull final ICommonsList <XsdError> aErrors, @NonNull final String sRule)
  {
    for (final XsdError aError : aErrors)
      if (aError.rule ().equals (sRule))
        return aError;
    return null;
  }

  @NonNull
  private static ICommonsSet <String> _kinds (@NonNull final XsdValidationResult aResult)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (final XsdError aError : aResult.errors ())
      ret.add (aError.kind ());
    return ret;
  }

  @Test
  public void testMessageNamespace ()
  {
    assertEquals ("urn:fdc:helger.com:ph-xslt:xsd:messages", XsdConstants.NS_MESSAGES);
  }

  @Test
  public void testErrorCollection ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='doc'><xs:complexType><xs:sequence>" +
                           "<xs:element name='row' maxOccurs='unbounded'><xs:complexType><xs:sequence>" +
                           "<xs:element name='n' type='xs:int'/>" +
                           "</xs:sequence><xs:attribute name='id' type='xs:int' use='required'/></xs:complexType></xs:element>" +
                           "</xs:sequence></xs:complexType></xs:element></xs:schema>";
    final String sInst = "<doc>\n" +
                         "  <row id='1'><n>x</n></row>\n" +
                         "  <row><n>2</n></row>\n" +
                         "  <row id='y'><n>3</n></row>\n" +
                         "</doc>";
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile (sSchema, eVersion);
      XsdValidationResult aResult = aSchema.validateText (sInst);
      assertFalse (aResult.valid ());
      assertEquals (3, aResult.errors ().size ());
      for (int i = 1; i < aResult.errors ().size (); i++)
      {
        final XsdError a = aResult.errors ().get (i - 1);
        final XsdError b = aResult.errors ().get (i);
        assertTrue ("document order: " + a + " then " + b,
                    a.line () < b.line () || a.line () == b.line () && a.column () <= b.column ());
      }
      final String [] aPaths = { "/doc[1]/row[1]/n[1]", "/doc[1]/row[2]", "/doc[1]/row[3]" };
      for (int i = 0; i < 3; i++)
      {
        assertEquals (aPaths[i], aResult.errors ().get (i).path ());
        assertTrue (aResult.errors ().get (i).line () > 0);
      }
      assertEquals (2, aSchema.validateText (sInst, new XsdValidationOptions ().setMaxErrors (2)).errors ().size ());
      assertEquals (1, aSchema.validateText (sInst, new XsdValidationOptions ().setMaxErrors (1)).errors ().size ());
      aResult = aSchema.validateText ("<doc><row id='1'><n>1</n></row></doc>",
                                      new XsdValidationOptions ().setMaxErrors (1));
      assertTrue (aResult.valid ());
      assertTrue (aResult.errors ().isEmpty ());
    }
  }

  @Test
  public void testContentModelExplanation ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a'/><xs:element name='b'/><xs:element name='c' minOccurs='0' maxOccurs='2'/>" +
                           "</xs:sequence></xs:complexType></xs:element>" +
                           "<xs:element name='ch'><xs:complexType><xs:choice>" +
                           "<xs:element name='x'/><xs:element name='y'/>" +
                           "</xs:choice></xs:complexType></xs:element></xs:schema>";
    final String [][] aCases = { { "<s><a/><c/></s>", "missing", "b" },
                                 { "<s><a/><b/><c/><c/><c/></s>", "too-many", "c" },
                                 { "<s><a/><b/><z/></s>", "unexpected", "z" },
                                 { "<s><b/><a/></s>", "order", "" },
                                 { "<s><a/></s>", "missing", "b" } };
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile (sSchema, eVersion);
      for (final String [] aCase : aCases)
      {
        final XsdValidationResult aResult = aSchema.validateText (aCase[0]);
        assertFalse (aCase[0], aResult.valid ());
        boolean bFound = false;
        for (final XsdError aError : aResult.errors ())
          if (aError.kind ().equals (aCase[1]) && (aCase[2].isEmpty () || aError.particle ().endsWith (aCase[2])))
            bFound = true;
        assertTrue (aCase[0] + " has no " + aCase[1] + " error for " + aCase[2] + ": " + aResult.errors (), bFound);
      }
      // Two independent defects are both reported
      final ICommonsSet <String> aKinds = _kinds (aSchema.validateText ("<s><a/><c/><c/><c/></s>"));
      assertTrue (aKinds.toString (), aKinds.contains ("missing") && aKinds.contains ("too-many"));
      // An empty choice that requires one branch
      final XsdValidationResult aEmpty = aSchema.validateText ("<ch/>");
      assertFalse (aEmpty.valid ());
      assertEquals ("missing", aEmpty.errors ().get (0).kind ());
      assertEquals ("cvc-complex-type.2.4", aEmpty.errors ().get (0).code ());
    }
  }

  @Test
  public void testExplanationWording ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           " xmlns='urn:t' targetNamespace='urn:t' elementFormDefault='qualified'>" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a'/><xs:element name='b'/></xs:sequence></xs:complexType></xs:element>" +
                           "<xs:element name='g'><xs:complexType><xs:sequence><xs:choice>" +
                           "<xs:element name='x'/><xs:element name='y'/></xs:choice><xs:element name='z'/>" +
                           "</xs:sequence></xs:complexType></xs:element>" +
                           "</xs:schema>";
    final XsdSchema aSchema = _compile (sSchema, EXsdVersion.V10);
    XsdError aError = aSchema.validateText ("<s xmlns='urn:t'><a/></s>").errors ().get (0);
    assertEquals ("element b is missing in s", aError.message ());
    assertEquals ("{urn:t}b", aError.particle ());
    assertEquals ("/s[1]", aError.path ());
    aError = aSchema.validateText ("<t:s xmlns:t='urn:t'><t:a/></t:s>").errors ().get (0);
    assertEquals ("element t:b is missing in t:s", aError.message ());
    aError = aSchema.validateText ("<s xmlns='urn:t'><a/><b/><z xmlns=''/></s>").errors ().get (0);
    assertEquals ("unexpected", aError.kind ());
    assertEquals ("element {}z is not allowed in s".replace ("{}", ""), aError.message ());
    aError = aSchema.validateText ("<s xmlns='urn:t'><b/><a/></s>").errors ().get (0);
    assertEquals ("order", aError.kind ());
    assertEquals ("element b is not allowed at this position in s, expected a", aError.message ());
    aError = aSchema.validateText ("<g xmlns='urn:t'><x/><y/><z/></g>").errors ().get (0);
    assertEquals ("too-many", aError.kind ());
    assertEquals ("element y: the choice of x, y occurs more often than allowed (maximum 1) in g", aError.message ());
  }

  @Test
  public void testCardinalityMessages ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           " " +
                           MSG +
                           ">" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a'><xs:annotation><xs:appinfo>" +
                           "<msg:message on='missing' rule='R1' xml:lang='en'>a is required</msg:message>" +
                           "<msg:message on='missing' rule='R1' xml:lang='de'>a fehlt</msg:message>" +
                           "<msg:message on='too-many' rule='R2'>only one a</msg:message>" +
                           "</xs:appinfo></xs:annotation></xs:element>" +
                           "<xs:element name='b' minOccurs='0'/>" +
                           "</xs:sequence>" +
                           "<xs:attribute name='k' use='required'><xs:annotation><xs:appinfo>" +
                           "<msg:message on='missing' rule='R3' xml:lang='fr'>k manque</msg:message>" +
                           "<msg:message on='missing' rule='R3'>k missing</msg:message>" +
                           "</xs:appinfo></xs:annotation></xs:attribute>" +
                           "</xs:complexType></xs:element></xs:schema>";
    final String [][] aCases = { { "<s k='1'><b/></s>", "de", "R1", "a fehlt" },
                                 { "<s k='1'><b/></s>", "de-CH", "R1", "a fehlt" },
                                 { "<s k='1'><b/></s>", "DE", "R1", "a fehlt" },
                                 { "<s k='1'><b/></s>", "en", "R1", "a is required" },
                                 { "<s k='1'><a/><a/></s>", "en", "R2", "only one a" },
                                 { "<s><a/></s>", "fr", "R3", "k manque" },
                                 { "<s><a/></s>", "it", "R3", "k missing" } };
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile (sSchema, eVersion);
      for (final String [] aCase : aCases)
      {
        final XsdValidationResult aResult = aSchema.validateText (aCase[0],
                                                                  new XsdValidationOptions ().setLanguage (aCase[1]));
        assertFalse (aCase[0], aResult.valid ());
        final XsdError aError = _byRule (aResult.errors (), aCase[2]);
        assertNotNull (aCase[0] + " lang=" + aCase[1] + ": " + aResult.errors (), aError);
        assertEquals (aCase[3], aError.message ());
      }
      // without a language: no xml:lang first, else the first entry
      final XsdError aNoLang = aSchema.validateText ("<s><a/></s>").errors ().get (0);
      assertEquals ("R3", aNoLang.rule ());
      assertEquals ("k missing", aNoLang.message ());
    }
  }

  @Test
  public void testGoXsltNamespaceIsNotRecognised ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           " xmlns:msg='https://github.com/tim-riep/go-xslt/xsd/messages'>" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a'><xs:annotation><xs:appinfo>" +
                           "<msg:message on='missing' rule='R1'>a is required</msg:message>" +
                           "</xs:appinfo></xs:annotation></xs:element>" +
                           "</xs:sequence></xs:complexType></xs:element></xs:schema>";
    final XsdError aError = _compile (sSchema, EXsdVersion.V10).validateText ("<s/>").errors ().get (0);
    assertEquals ("", aError.rule ());
    assertEquals ("element a is missing in s", aError.message ());
  }

  @Test
  public void testMissingAttributesAreAllReported ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='e'><xs:complexType>" +
                           "<xs:attribute name='p' use='required'/><xs:attribute name='q' use='required'/>" +
                           "<xs:attribute name='r' type='xs:int'/>" +
                           "</xs:complexType></xs:element></xs:schema>";
    final XsdSchema aSchema = _compile (sSchema, EXsdVersion.V10);
    final XsdValidationResult aResult = aSchema.validateText ("<e/>");
    assertFalse (aResult.valid ());
    // p and q are missing: both, in schema order
    assertEquals (2, aResult.errors ().size ());
    assertEquals ("required attribute p is missing", aResult.errors ().get (0).message ());
    assertEquals ("required attribute q is missing", aResult.errors ().get (1).message ());
    // an invalid value ends the attribute check of that element (as in go-xslt)
    assertEquals (1, aSchema.validateText ("<e r='x'/>").errors ().size ());
  }

  @Test
  public void testFirstErrorIdentity ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a' type='xs:int' maxOccurs='3'/><xs:element name='b'/>" +
                           "</xs:sequence><xs:attribute name='k' use='required'/></xs:complexType></xs:element>" +
                           "</xs:schema>";
    final XsdSchema aSchema = _compile (sSchema, EXsdVersion.V10);
    final String [] aInstances = { "<s k='1'><a>1</a><b/></s>",
                                   "<s><a>1</a><b/></s>",
                                   "<s k='1'><a>x</a><b/></s>",
                                   "<s k='1'><a>1</a></s>",
                                   "<s k='1'><b/><a>1</a></s>",
                                   "<s k='1'><a>1</a><a>1</a><a>1</a><a>1</a><b/></s>",
                                   "<s k='1'><a>1</a><b/><z/></s>",
                                   "<z/>",
                                   "<s k='1'>" };
    for (final String sInst : aInstances)
    {
      final XsdValidationResult aFirst = aSchema.validateTextFirstError (sInst);
      final XsdValidationResult aAll = aSchema.validateText (sInst);
      assertEquals (sInst, aFirst.valid (), aAll.valid ());
      if (!aFirst.valid ())
      {
        assertEquals (sInst, 1, aFirst.errors ().size ());
        boolean bFound = false;
        for (final XsdError aError : aAll.errors ())
          if (aError.code ().equals (aFirst.errors ().get (0).code ()))
            bFound = true;
        assertTrue (sInst + ": " + aFirst.errors () + " not in " + aAll.errors (), bFound);
      }
    }
  }

  @Test
  public void testUnexplainableModelsKeepTheGenericMessage ()
  {
    // xs:all and huge occurrence bounds are not explained
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='all'><xs:complexType><xs:all>" +
                           "<xs:element name='a'/><xs:element name='b'/></xs:all></xs:complexType></xs:element>" +
                           "<xs:element name='big'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a' minOccurs='1000' maxOccurs='1000'/></xs:sequence></xs:complexType></xs:element>" +
                           "</xs:schema>";
    final XsdSchema aSchema = _compile (sSchema, EXsdVersion.V10);
    for (final String sInst : new String [] { "<all><a/></all>", "<big><a/></big>" })
    {
      final XsdValidationResult aResult = aSchema.validateText (sInst);
      assertFalse (sInst, aResult.valid ());
      assertEquals (1, aResult.errors ().size ());
      assertEquals ("cvc-complex-type.2.4", aResult.errors ().get (0).code ());
      assertEquals ("", aResult.errors ().get (0).kind ());
      assertTrue (aResult.errors ().get (0).message (), aResult.errors ().get (0).message ().contains ("does not match"));
    }
  }

  @Test
  public void testLargeContentIsExplainedWithoutBlowingUp ()
  {
    final String sSchema = "<xs:schema " +
                           XS +
                           ">" +
                           "<xs:element name='s'><xs:complexType><xs:sequence>" +
                           "<xs:element name='a' maxOccurs='unbounded'/><xs:element name='b'/>" +
                           "</xs:sequence></xs:complexType></xs:element></xs:schema>";
    final XsdSchema aSchema = _compile (sSchema, EXsdVersion.V10);
    final StringBuilder aSB = new StringBuilder ("<s>");
    for (int i = 0; i < 20000; i++)
      aSB.append ("<a/>");
    aSB.append ("</s>");
    final XsdValidationResult aResult = aSchema.validateText (aSB.toString ());
    assertFalse (aResult.valid ());
    assertEquals (1, aResult.errors ().size ());
    // more children than are explained: the generic message
    assertEquals ("", aResult.errors ().get (0).kind ());
    final XsdValidationResult aSmall = aSchema.validateText ("<s>" + "<a/>".repeat (500) + "</s>");
    assertEquals ("missing", aSmall.errors ().get (0).kind ());
  }

  @Test
  public void testNotWellFormedAndMissingRoot ()
  {
    final XsdSchema aSchema = _compile ("<xs:schema " + XS + "><xs:element name='e'/></xs:schema>", EXsdVersion.V10);
    final XsdValidationResult aResult = aSchema.validateText ("<e>");
    assertFalse (aResult.valid ());
    assertTrue (aResult.errors ().get (0).message ().startsWith ("instance not well-formed"));
    try
    {
      aSchema.validateText ("<e/>", null);
      fail ();
    }
    catch (final NullPointerException | IllegalArgumentException ex)
    {
      // expected
    }
  }
}
