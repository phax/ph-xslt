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
package com.helger.xpath.spec;

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static com.helger.xpath.spec.SpecTestHelper.parse;
import static com.helger.xpath.spec.SpecTestHelper.run;
import static org.junit.Assert.assertEquals;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Specification tests of the axes and node tests, ported from go-xslt's <code>axes_spec_test.go</code>.
 *
 * @author Philip Helger
 */
public final class AxesSpecTest
{


  private static final String AXES_DOC = "<r a=\"0\"><x id=\"1\"><y id=\"2\"/><y id=\"3\">t</y></x><!--c--><?pi d?><?other e?><z id=\"4\"><w id=\"5\" xml:lang=\"en\"/></z></r>";

  @NonNull
  private static XdmQName _xs (@NonNull final String sLocal)
  {
    return new XdmQName ("http://www.w3.org/2001/XMLSchema", sLocal);
  }

  @NonNull
  private static IXdmNode _el ()
  {
    return IXdmNode.createElement (new XdmQName ("a"));
  }

  @NonNull
  private static IXdmNode _doc (@NonNull final IXdmNode... aKids)
  {
    final IXdmNode aDoc = IXdmNode.createDocument ();
    for (final IXdmNode aKid : aKids)
      aDoc.appendChild (aKid);
    return aDoc;
  }

  private static void _runDoc (@NonNull final IXdmNode aDoc, @NonNull final String sWant)
  {
    run (env ().contextItem (aDoc), ok ("count(self::document-node(element(a)))", sWant));
  }

  /**
   * Checks every axis of XPath 3.1 §3.3.2.1 on a small tree, with
   * reverse-axis positional predicates counted nearest-first (§3.3.2.2) and
   * path results in document order (§3.3.1.1).
   */
  @Test
  public void caAxes ()
  {
    run (env (AXES_DOC),
         ok ("string-join(//y[@id='3']/ancestor::*/name(), ',')", "r,x"),
         ok ("//y[@id='3']/ancestor::*[1]/name()", "x"),
         ok ("//y[@id='3']/ancestor::*[last()]/name()", "r"),
         ok ("count(//y[@id='2']/ancestor-or-self::node())", "4"),
         ok ("string(//y[@id='3']/ancestor-or-self::*[1]/@id)", "3"),
         ok ("string(//y[@id='3']/ancestor-or-self::*[2]/@id)", "1"),
         ok ("count(/..)", "0"),
         ok ("count(/ancestor::node())", "0"),
         ok ("name(//y[@id='2']/parent::*)", "x"),
         ok ("name(//@id[. = '2']/..)", "y"),
         ok ("count(/r/self::r) + count(/r/self::x)", "1"),
         ok ("string(/r/descendant::*[2]/@id)", "2"),
         ok ("string(/r/descendant::*[last()]/@id)", "5"),
         ok ("string(//x/descendant-or-self::*[1]/@id)", "1"),
         ok ("string(//x/descendant-or-self::*[3]/@id)", "3"),
         ok ("count(//x/descendant::node())", "3"),
         ok ("string-join(//y[@id='2']/following-sibling::*/@id, ',')", "3"),
         ok ("string(//y[@id='3']/preceding-sibling::*[1]/@id)", "2"),
         ok ("string(//z/preceding-sibling::*[1]/@id)", "1"),
         ok ("count(//z/preceding-sibling::node())", "4"),
         ok ("count(//z/preceding-sibling::node()[2]/self::processing-instruction('pi'))", "1"),
         ok ("count(/r/preceding-sibling::node()) + count(/following-sibling::node())", "0"),
         ok ("count(//@a/following-sibling::node())", "0"),
         ok ("string-join(//y[@id='2']/following::*/@id, ',')", "3,4,5"),
         ok ("count(//y[@id='2']/following::node())", "7"),
         ok ("string(//y[@id='2']/following::*[2]/@id)", "4"),
         ok ("count(//x/following::*)", "2"),
         ok ("string-join(//w/preceding::*/@id, ',')", "1,2,3"),
         ok ("string(//w/preceding::*[1]/@id)", "3"),
         ok ("count(//w/preceding::node())", "7"),
         ok ("count(/r/preceding::node()) + count(/r/following::node())", "0"),
    // An attribute's following axis includes its parent's descendants;
    // its preceding axis excludes its ancestors (XDM 3.1 §2.4 order).
         ok ("count(//x/@id/following::*)", "4"),
         ok ("string(//x/@id/following::*[1]/@id)", "2"),
         ok ("count(//x/@id/preceding::node())", "0"),
         ok ("string(//z/@id/preceding::*[1]/@id)", "3"),
    // Node tests on non-principal axes.
         ok ("count(//processing-instruction())", "2"),
         ok ("string(//processing-instruction('pi'))", "d"),
         ok ("count(//processing-instruction('none'))", "0"),
         ok ("count(//comment()) + count(//text())", "2"),
         ok ("count(//x/node())", "2"),
         ok ("count(/r/attribute::node())", "1"),
         ok ("count(//@*)", "7"),
         ok ("count(//attribute::*:lang) + count(//@xml:lang)", "2"),
         ok ("count(//*:y) + count(//Q{}y)", "4"),
         ok ("count(/self::document-node(element(r)))", "1"),
         ok ("count(/self::document-node(element(x)))", "0"),
         ok ("count(/self::document-node())", "1"),
         ok ("count(/r/self::document-node())", "0"),
         ok ("count(//element(y)) + count(//element(*))", "8"),
         ok ("count(//attribute(id)) + count(//attribute(*))", "12"),
         ok ("count(//x/self::attribute(id)) + count(//@id/self::element(*))", "0"),
         ok ("count(//self::namespace-node())", "0")
    );
  }

  /**
   * Checks element(N,T)/attribute(N,T) against untyped
   * nodes (XPath 3.1 §2.5.5.3/§2.5.5.5): an unvalidated element is annotated
   * xs:untyped, an unvalidated attribute xs:untypedAtomic.
   */
  @Test
  public void caKindTestTypes ()
  {
    run (env ("<r><y a=\"1\"/><y/><u:y xmlns:u=\"urn:u\"/></r>").ns ("s", "http://www.w3.org/2001/XMLSchema").ns ("u", "urn:u"),
         ok ("count(//element(y, xs:untyped))", "2"),
         ok ("count(//element(y, s:untyped))", "2"),
         ok ("count(//element(y, Q{http://www.w3.org/2001/XMLSchema}anyType))", "2"),
         ok ("count(//element(*, xs:anyType))", "4"),
         ok ("count(//element(y, xs:string))", "0"),
         ok ("count(//element(y, xs:untypedAtomic))", "0"),
         ok ("count(//element(y, xs:anyAtomicType))", "0"),
         ok ("count(//element(u:y))", "1"),
         ok ("count(//element(Q{urn:u}y))", "1"),
         ok ("count(//u:*)", "1"),
         ok ("count(//@attribute(a, xs:untypedAtomic))", "1"),
         ok ("count(//@attribute(*, xs:anyAtomicType))", "1"),
         ok ("count(//@attribute(a, xs:anySimpleType))", "1"),
         ok ("count(//@attribute(a, xs:untyped))", "0"),
         ok ("count(//@attribute(a, xs:integer))", "0"),
         ok ("count(//@attribute(u:a))", "0"),
         err ("count(//zz:y)", "XPST0081")
    );
    // xpath-default-namespace applies to unprefixed element names, not to
    // attributes and not to Q{}local.
    run (env ("<r a=\"1\"><u:y xmlns:u=\"urn:u\" a=\"2\"/></r>").staticSetup (x -> x.setDefaultElementNamespace ("urn:u")),
         ok ("count(//y) + count(//element(y))", "2"),
         ok ("count(//Q{}r) + count(/r)", "1"),
         ok ("count(//@a)", "2"),
         ok ("count(//*)", "2")
    );
    // Q{uri}* matches any local name in exactly that namespace, Q{}* only
    // no-namespace names (XPath 3.1 §3.3.2.2), also on the attribute axis.
    run (env ("<r><u:a xmlns:u=\"urn:u\" u:x=\"1\" y=\"2\"/><b/><v:c xmlns:v=\"urn:v\"/></r>"),
         ok ("count(/r/Q{urn:u}*)", "1"),
         ok ("count(/r/Q{}*)", "1"),
         ok ("count(/r/Q{urn:w}*)", "0"),
         ok ("count(//@Q{urn:u}*)", "1"),
         ok ("count(//@Q{}*)", "1"),
         ok ("local-name(/r/Q{urn:v}*)", "c")
    );
  }

  /**
   * Checks the type part of element/attribute tests
   * against nodes that carry a type annotation (XPath 3.1 §2.5.5.3: matches
   * when the annotation derives from T; element(N,T) excludes nilled elements,
   * element(N,T?) does not).
   */
  @Test
  public void caKindTestAnnotated ()
  {
    final IXdmNode aDoc = parse ("<r a=\"5\" b=\"x y\"><e>7</e><n/><v/></r>");
    final IXdmNode aRoot = aDoc.getChildAtIndex (0);
    aRoot.getAttributeAtIndex (0).setTypeAnnotation (_xs ("integer"));
    aRoot.getAttributeAtIndex (1).setTypeAnnotation (_xs ("NMTOKENS"));
    aRoot.getChildAtIndex (0).setTypeAnnotation (_xs ("integer"));
    aRoot.getChildAtIndex (1).setTypeAnnotation (_xs ("integer"));
    aRoot.getChildAtIndex (1).setNilled (true);
    aRoot.getChildAtIndex (2).setTypeAnnotation (_xs ("untypedAtomic"));
    run (env ().contextItem (aDoc),
         ok ("count(/r/@attribute(a, xs:integer))", "1"),
         ok ("count(/r/@attribute(a, xs:decimal))", "1"),
         ok ("count(/r/@attribute(a, xs:short))", "0"),
         ok ("count(/r/@attribute(a, xs:string))", "0"),
         ok ("count(/r/@attribute(a, xs:untypedAtomic))", "0"),
         ok ("count(/r/@attribute(b, xs:NMTOKENS))", "1"),
         ok ("count(/r/@attribute(b, xs:NMTOKEN))", "0"),
         ok ("count(/r/@attribute(a, xs:NMTOKENS))", "0"),
         ok ("count(/r/element(e, xs:integer))", "1"),
         ok ("count(/r/element(e, xs:untyped))", "0"),
         ok ("count(/r/element(n, xs:integer))", "0"),
         ok ("count(/r/element(n, xs:integer?))", "1"),
         ok ("count(/r/element(n))", "1"),
         ok ("count(/r/element(v, xs:untypedAtomic))", "1"),
         ok ("count(/r/element(v, xs:untyped))", "0"));
  }

  /**
   * Checks the namespace axis (XPath 3.1 §3.3.2.1, XDM 3.1
   * §6.4): one node per in-scope binding including the implicit xml one, an
   * xmlns="" undeclaration removes the default, and each element has its own
   * namespace nodes.
   */
  @Test
  public void caNamespaceAxis ()
  {
    final String sSrc = "<r xmlns:p=\"urn:p\"><z xmlns:q=\"urn:q\"><w/></z><d xmlns=\"urn:d\"><s xmlns=\"\"/></d></r>";
    run (env (sSrc),
         ok ("count(/r/z/w/namespace::*)", "3"),
         ok ("string-join(sort(for $n in /r/z/w/namespace::* return name($n)), ',')", "p,q,xml"),
         ok ("string(/r/z/w/namespace::q)", "urn:q"),
         ok ("string(/r/namespace::xml)", "http://www.w3.org/XML/1998/namespace"),
         ok ("count(/r/namespace::node()) + count(/r/namespace::namespace-node())", "4"),
         ok ("count(/namespace::*) + count(/r/text()/namespace::*)", "0"),
         ok ("count(//*:d/namespace::*)", "3"),
         ok ("count(//*:d/namespace::*[name() = ''])", "1"),
         ok ("count(//s/namespace::*)", "2"),
         ok ("count(//s/namespace::*[name() = ''])", "0"),
         ok ("count(/r/z/w/namespace::* | /r/z/w/namespace::*)", "3"),
         ok ("count(/r/z/namespace::* | /r/z/w/namespace::*)", "6"),
         ok ("/r/z/namespace::q/.. is /r/z", "true"),
         ok ("/r/z/w/namespace::p/.. is /r/z/w", "true"),
         ok ("count(/r/z/w/namespace::p/ancestor::*)", "3"),
         ok ("count(/r/z/w/namespace::p/following::*)", "2"),
         ok ("count(/r/z/w/namespace::p/preceding::*)", "0"),
         ok ("count(/r/z/w/namespace::*/self::namespace-node())", "3"),
         ok ("count(/r/z/w/namespace::p/self::*)", "0")
    );

    // The namespace nodes of an element are the namespace axis itself.
    final IXdmNode aDoc = parse (sSrc);
    final IXdmNode aW = aDoc.getChildAtIndex (0).getChildAtIndex (0).getChildAtIndex (0);
    assertEquals (3, aW.getNamespaceNodes ().size ());
    assertEquals (0, aDoc.getNamespaceNodes ().size ());
    // go-xslt also checks its namespace-inheritance barrier (SetNSBarrier); IXdmNode has no such flag.
  }

  /**
   * Checks document-node(E) on constructed document
   * nodes (XPath 3.1 §2.5.5.1): it matches only a document with exactly one
   * element child, optionally with comments and PIs, and no text.
   */
  @Test
  public void caDocumentNodeTest ()
  {
    _runDoc (_doc (_el ()), "1");
    _runDoc (_doc (IXdmNode.createComment ("c"), _el ()), "1");
    _runDoc (_doc (_el (), _el ()), "0");
    _runDoc (_doc (IXdmNode.createText ("x"), _el ()), "0");
    _runDoc (_doc (IXdmNode.createText ("x")), "0");
    // The axes of an attribute without a parent are empty.
    run (env ().contextItem (IXdmNode.createAttribute (new XdmQName ("a"), "1")),
         ok ("count(following::node()) + count(preceding::node()) + count(..)", "0"));
  }

  /**
   * Checks XPST0081 for a name test whose prefix the
   * static context does not bind (XPath 3.1 §2.1.1, §3.3.2.2).
   */
  @Test
  public void caUnboundPrefix ()
  {
    run (env ("<r><y/></r>").ns ("p", "urn:p"),
         err ("count(/r/descendant::zz:y)", "XPST0081"),
         err ("count(//element(zz:y))", "XPST0081"),
         err ("count(/r/zz:*)", "XPST0081"),
         err ("count(/r/@zz:a)", "XPST0081")
    );
  }
}
