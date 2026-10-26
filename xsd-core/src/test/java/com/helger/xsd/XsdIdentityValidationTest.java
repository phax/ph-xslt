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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;
import com.helger.xsd.XsdTestSupport.Tally;

/**
 * Identity constraints on instances (Structures section 3.11): cvc-identity-constraint
 * (field tuples, value equality, key presence, keyref resolution), and
 * the namespaces of selectors and fields (go-xslt
 * <code>identity_spec_test.go</code>). The selector and field XPath subset is
 * tested in {@link XsdIdentityCompilerTest}. A case that depends on a phase not
 * ported yet is counted as undecided; a decided verdict must be the expected one.
 *
 * @author Philip Helger
 */
public final class XsdIdentityValidationTest
{
  private static final String KEY_ISBN = "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@isbn\"/></xs:key>";

  private record Ic (String name, String ic, String instance, boolean valid)
  {}

  private static final Ic [] CASES = {
    new Ic ("key-ok", KEY_ISBN, "<lib><book isbn=\"1\"/><book isbn=\"2\"/></lib>", true),
    new Ic ("key-duplicate", KEY_ISBN, "<lib><book isbn=\"1\"/><book isbn=\"1\"/></lib>", false),
    new Ic ("key-missing-field", KEY_ISBN, "<lib><book isbn=\"1\"/><book/></lib>", false),
    new Ic ("unique-missing-field", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@isbn\"/></xs:unique>", "<lib><book isbn=\"1\"/><book/><book/></lib>", true),
    new Ic ("unique-duplicate", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@isbn\"/></xs:unique>", "<lib><book isbn=\"1\"/><book isbn=\"1\"/></lib>", false),
    new Ic ("key-element-field", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"title\"/></xs:key>", "<lib><book><title>A</title></book><book><title>B</title></book></lib>", true),
    new Ic ("key-element-field-dup", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"title\"/></xs:key>", "<lib><book><title>A</title></book><book><title>A</title></book></lib>", false),
    new Ic ("field-multiple-nodes", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"author\"/></xs:unique>", "<lib><book><author>x</author><author>y</author></book></lib>", false),
    new Ic ("field-complex-element", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"meta\"/></xs:unique>", "<lib><book><meta><m/></meta></book></lib>", false),
    new Ic ("multi-field-distinct", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@a\"/><xs:field xpath=\"@b\"/></xs:key>", "<lib><book a=\"1\" b=\"1\"/><book a=\"1\" b=\"2\"/></lib>", true),
    new Ic ("multi-field-dup", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@a\"/><xs:field xpath=\"@b\"/></xs:key>", "<lib><book a=\"1\" b=\"2\"/><book a=\"1\" b=\"2\"/></lib>", false),
    new Ic ("decimal-value-equal", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@n\"/></xs:unique>", "<lib><book n=\"1.0\"/><book n=\"01\"/></lib>", false),
    new Ic ("decimal-value-distinct", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@n\"/></xs:unique>", "<lib><book n=\"1.0\"/><book n=\"1.5\"/></lib>", true),
    new Ic ("string-lexical-distinct", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@s\"/></xs:unique>", "<lib><book s=\"1.0\"/><book s=\"1\"/></lib>", true),
    new Ic ("selector-union", "<xs:unique name=\"u\"><xs:selector xpath=\"book | ref\"/><xs:field xpath=\"@isbn\"/></xs:unique>", "<lib><book isbn=\"1\"/><book isbn=\"2\"/></lib>", true),
    new Ic ("selector-descendant", "<xs:unique name=\"u\"><xs:selector xpath=\".//author\"/><xs:field xpath=\".\"/></xs:unique>", "<lib><book><author>x</author></book><book><author>x</author></book></lib>", false),
    new Ic ("selector-child-axis", "<xs:unique name=\"u\"><xs:selector xpath=\"child::book/child::title\"/><xs:field xpath=\".\"/></xs:unique>", "<lib><book><title>x</title></book><book><title>x</title></book></lib>", false),
    new Ic ("selector-wildcard", "<xs:unique name=\"u\"><xs:selector xpath=\"*\"/><xs:field xpath=\"@isbn\"/></xs:unique>", "<lib><book isbn=\"1\"/><book isbn=\"1\"/></lib>", false),
    new Ic ("field-attribute-axis", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"attribute::isbn\"/></xs:unique>", "<lib><book isbn=\"1\"/><book isbn=\"1\"/></lib>", false),
    new Ic ("field-descendant", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\".//m\"/></xs:unique>", "<lib><book><meta><m>1</m></meta></book><book><meta><m>1</m></meta></book></lib>", false),
    new Ic ("keyref-ok", KEY_ISBN + "<xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@to\"/></xs:keyref>", "<lib><book isbn=\"1\"/><ref to=\"1\"/><ref/></lib>", true),
    new Ic ("keyref-dangling", KEY_ISBN + "<xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@to\"/></xs:keyref>", "<lib><book isbn=\"1\"/><ref to=\"2\"/></lib>", false),
    new Ic ("keyref-to-unique", "<xs:unique name=\"u\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@isbn\"/></xs:unique><xs:keyref name=\"r\" refer=\"u\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@to\"/></xs:keyref>", "<lib><book isbn=\"1\"/><ref to=\"1\"/></lib>", true),
    new Ic ("keyref-value-space", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@n\"/></xs:key><xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@num\"/></xs:keyref>", "<lib><book n=\"2\"/><ref num=\"2.00\"/></lib>", true),
    new Ic ("keyref-integer-vs-decimal", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@n\"/></xs:key><xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@int\"/></xs:keyref>", "<lib><book n=\"3.0\"/><ref int=\"3\"/></lib>", true),
    new Ic ("keyref-string-vs-decimal", "<xs:key name=\"k\"><xs:selector xpath=\"book\"/><xs:field xpath=\"@n\"/></xs:key><xs:keyref name=\"r\" refer=\"k\"><xs:selector xpath=\"ref\"/><xs:field xpath=\"@to\"/></xs:keyref>", "<lib><book n=\"3\"/><ref to=\"3\"/></lib>", false)
  };

  /** lib holds book children; the constraints are inserted into lib's declaration. */
  private static String _lib (final String sIdentityConstraints)
  {
    return """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="lib"><xs:complexType><xs:sequence>
          <xs:element name="book" minOccurs="0" maxOccurs="unbounded"><xs:complexType><xs:sequence>
            <xs:element name="title" type="xs:string" minOccurs="0"/>
            <xs:element name="author" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
            <xs:element name="meta" minOccurs="0"><xs:complexType><xs:sequence><xs:element name="m" minOccurs="0"/></xs:sequence></xs:complexType></xs:element>
          </xs:sequence>
          <xs:attribute name="isbn" type="xs:string"/><xs:attribute name="n" type="xs:decimal"/>
          <xs:attribute name="s" type="xs:string"/><xs:attribute name="a" type="xs:string"/><xs:attribute name="b" type="xs:string"/>
          </xs:complexType></xs:element>
          <xs:element name="ref" minOccurs="0" maxOccurs="unbounded"><xs:complexType>
            <xs:attribute name="to" type="xs:string"/><xs:attribute name="num" type="xs:decimal"/><xs:attribute name="int" type="xs:integer"/>
          </xs:complexType></xs:element>
        </xs:sequence></xs:complexType>""" + sIdentityConstraints + """
        </xs:element>
      </xs:schema>""";
  }

  @Test
  public void testIdentityConstraintSemantics ()
  {
    final Tally aTally = new Tally ();
    for (final Ic aCase : CASES)
      for (final EXsdVersion eVersion : new EXsdVersion [] { EXsdVersion.V10, EXsdVersion.V11 })
      {
        final Compiled aCompiled = XsdTestSupport.compile (_lib (aCase.ic ()), null, eVersion);
        if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
        {
          aTally.m_nUndecided++;
          continue;
        }
        assertTrue (aCase.name () + ": " + aCompiled.message (), aCompiled.verdict () == EVerdict.VALID);
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
    aTally.log ("identity constraints");
    assertTrue (aTally.m_aWrong.toString (), aTally.m_aWrong.isEmpty ());
  }

  private static String _nsSchema (final String sIdentityConstraint, final String sSchemaAttrs)
  {
    return """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t" xmlns:t="urn:t" elementFormDefault="qualified" attributeFormDefault="qualified"\
      """ + sSchemaAttrs + """
      >
        <xs:element name="root"><xs:complexType><xs:sequence>
          <xs:element name="it" maxOccurs="unbounded"><xs:complexType><xs:attribute name="id" type="xs:string"/></xs:complexType></xs:element>
        </xs:sequence></xs:complexType>""" + sIdentityConstraint + """
        </xs:element></xs:schema>""";
  }

  /**
   * Namespaced selectors and fields: prefixes resolve against the schema
   * document's in-scope namespaces; 1.1 xpathDefaultNamespace sets the
   * namespace of unprefixed names.
   */
  @Test
  public void testIdentityConstraintNamespaces ()
  {
    final String sDup = "<t:root xmlns:t=\"urn:t\"><t:it t:id=\"1\"/><t:it t:id=\"1\"/></t:root>";
    // name, versions (0 both, 2 only 1.1), constraint, schema attributes, verdict on the duplicate
    final Object [] [] aCases = {
      { "prefixed", Integer.valueOf (0), "<xs:unique name=\"u\"><xs:selector xpath=\"t:it\"/><xs:field xpath=\"@t:id\"/></xs:unique>", "", Boolean.FALSE },
      // Unprefixed names are in no namespace: the selector selects nothing, so no duplicates are found
      { "unprefixed-no-namespace", Integer.valueOf (0), "<xs:unique name=\"u\"><xs:selector xpath=\"it\"/><xs:field xpath=\"@t:id\"/></xs:unique>", "", Boolean.TRUE },
      { "ns-wildcard", Integer.valueOf (0), "<xs:unique name=\"u\"><xs:selector xpath=\"t:*\"/><xs:field xpath=\"@t:id\"/></xs:unique>", "", Boolean.FALSE },
      { "xpath-default-ns-selector", Integer.valueOf (2), "<xs:unique name=\"u\"><xs:selector xpath=\"it\" xpathDefaultNamespace=\"##targetNamespace\"/><xs:field xpath=\"@t:id\"/></xs:unique>", "", Boolean.FALSE },
      { "xpath-default-ns-schema", Integer.valueOf (2), "<xs:unique name=\"u\"><xs:selector xpath=\"it\"/><xs:field xpath=\"@t:id\"/></xs:unique>", " xpathDefaultNamespace=\"urn:t\"", Boolean.FALSE },
      { "xpath-default-ns-local", Integer.valueOf (2), "<xs:unique name=\"u\"><xs:selector xpath=\"it\"/><xs:field xpath=\"@t:id\"/></xs:unique>", " xpathDefaultNamespace=\"##local\"", Boolean.TRUE } };
    final Tally aTally = new Tally ();
    for (final Object [] aCase : aCases)
      for (final EXsdVersion eVersion : XsdTestSupport.versionsOf (((Integer) aCase[1]).intValue ()))
      {
        final Compiled aCompiled = XsdTestSupport.compile (_nsSchema ((String) aCase[2], (String) aCase[3]), null, eVersion);
        if (aCompiled.verdict () == EVerdict.UNSUPPORTED)
        {
          aTally.m_nUndecided++;
          continue;
        }
        assertTrue (aCase[0] + ": " + aCompiled.message (), aCompiled.verdict () == EVerdict.VALID);
        final EVerdict eVerdict = XsdTestSupport.validate (aCompiled.schema (), sDup);
        if (eVerdict == EVerdict.UNSUPPORTED)
        {
          aTally.m_nUndecided++;
          continue;
        }
        aTally.m_nDecided++;
        if ((eVerdict == EVerdict.VALID) != ((Boolean) aCase[4]).booleanValue ())
          aTally.m_aWrong.add (aCase[0] + "/" + eVersion.getID () + ": valid=" + (eVerdict == EVerdict.VALID));
      }
    aTally.log ("identity constraint namespaces");
    assertTrue (aTally.m_aWrong.toString (), aTally.m_aWrong.isEmpty ());
  }
}
