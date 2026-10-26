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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Unique Particle Attribution ({@link XsdUpa}): schema verdicts of the loader's
 * <code>checkAllUPA</code> step on content models whose determinism is known
 * from XSD Structures section 3.8.6 (Schema Component Constraint: Unique Particle
 * Attribution), plus the helper predicates.
 *
 * @author Philip Helger
 */
public final class XsdUpaTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";
  private static final String A = "<xs:element name=\"a\"/>";
  private static final String B = "<xs:element name=\"b\"/>";
  private static final String C = "<xs:element name=\"c\"/>";

  private static void _check (final String sName,
                              final EXsdVersion eVersion,
                              final String sModel,
                              final String sExtra,
                              final boolean bValid)
  {
    final String sSchema = XS +
                           "<xs:element name=\"doc\"><xs:complexType>" +
                           sModel +
                           "</xs:complexType></xs:element>" +
                           sExtra +
                           "</xs:schema>";
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, eVersion);
    assertFalse (sName + "/" + eVersion + " unsupported: " + aCompiled.message (),
                 aCompiled.verdict () == EVerdict.UNSUPPORTED);
    assertEquals (sName + "/" + eVersion + " " + aCompiled.message (),
                  bValid,
                  aCompiled.verdict () == EVerdict.VALID);
  }

  private static void _both (final String sName, final String sModel, final String sExtra, final boolean bValid)
  {
    _check (sName, EXsdVersion.V10, sModel, sExtra, bValid);
    _check (sName, EXsdVersion.V11, sModel, sExtra, bValid);
  }

  @Test
  public void testDeterministicModels ()
  {
    _both ("sequence", "<xs:sequence>" + A + B + "</xs:sequence>", "", true);
    _both ("star-then-other", "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + B + "</xs:sequence>", "", true);
    _both ("choice-star", "<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">" + A + B + "</xs:choice>", "", true);
    _both ("counted-then-same", "<xs:sequence><xs:element name=\"a\" minOccurs=\"2\" maxOccurs=\"2\"/>" + A + "</xs:sequence>", "", true);
    _both ("different-namespaces",
           "<xs:sequence><xs:element name=\"a\"/><xs:element name=\"a\" form=\"qualified\"/></xs:sequence>",
           "",
           true);
    _both ("optional-then-other", "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/>" + B + C + "</xs:sequence>", "", true);
    _both ("all-distinct", "<xs:all>" + A + B + "</xs:all>", "", true);
    _both ("substitution-sequence",
           "<xs:sequence><xs:element ref=\"h\"/><xs:element ref=\"m\"/></xs:sequence>",
           "<xs:element name=\"h\"/><xs:element name=\"m\" substitutionGroup=\"h\"/>",
           true);
    // One named group reached twice contributes two distinct sets of positions
    _both ("shared-group-twice",
           "<xs:sequence><xs:group ref=\"g\"/>" + C + "<xs:group ref=\"g\"/></xs:sequence>",
           "<xs:group name=\"g\"><xs:sequence>" + A + "<xs:element name=\"b\" minOccurs=\"0\"/></xs:sequence></xs:group>",
           true);
    _both ("empty", "", "", true);
    _both ("removed-particle-does-not-count",
           "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"0\"/>" + A + "</xs:sequence>",
           "",
           true);
  }

  @Test
  public void testNondeterministicModels ()
  {
    _both ("choice-same-name", "<xs:choice>" + A + A + "</xs:choice>", "", false);
    _both ("optional-then-same", "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/>" + A + "</xs:sequence>", "", false);
    _both ("star-then-same",
           "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + A + "</xs:sequence>",
           "",
           false);
    _both ("counter-ambiguity", "<xs:sequence><xs:element name=\"a\" maxOccurs=\"2\"/>" + A + "</xs:sequence>", "", false);
    _both ("sequences-with-common-start",
           "<xs:choice><xs:sequence>" + A + B + "</xs:sequence><xs:sequence>" + A + C + "</xs:sequence></xs:choice>",
           "",
           false);
    _both ("choice-star-repeat",
           "<xs:choice maxOccurs=\"unbounded\">" + A + "<xs:sequence>" + A + B + "</xs:sequence></xs:choice>",
           "",
           false);
    _both ("substitution-overlap",
           "<xs:choice><xs:element ref=\"h\"/><xs:element ref=\"m\"/></xs:choice>",
           "<xs:element name=\"h\"/><xs:element name=\"m\" substitutionGroup=\"h\"/>",
           false);
    _both ("overlapping-wildcards",
           "<xs:sequence><xs:any namespace=\"##other\" minOccurs=\"0\"/><xs:any namespace=\"##any\"/></xs:sequence>",
           "",
           false);
  }

  @Test
  public void testElementAgainstWildcard ()
  {
    final String sModel = "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/><xs:any namespace=\"##local\"/></xs:sequence>";
    // Version-dependent: invalid in 1.0, the element wins in 1.1
    _check ("element-vs-wildcard", EXsdVersion.V10, sModel, "", false);
    _check ("element-vs-wildcard", EXsdVersion.V11, sModel, "", true);
    // A wildcard in another namespace does not compete
    _both ("element-vs-other-wildcard",
           "<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/><xs:any namespace=\"urn:x\"/></xs:sequence>",
           "",
           true);
  }

  @Test
  public void testWildcardsOverlap ()
  {
    final XsdWildcard aAny = new XsdWildcard ();
    aAny.m_sNsMode = XsdWildcard.NS_ANY;
    final XsdWildcard aOther = new XsdWildcard ();
    aOther.m_sNsMode = XsdWildcard.NS_OTHER;
    aOther.m_sTargetNS = "urn:t";
    final XsdWildcard aListA = new XsdWildcard ();
    aListA.m_sNsMode = XsdWildcard.NS_LIST;
    aListA.m_aNamespaces.add ("urn:a");
    final XsdWildcard aListLocal = new XsdWildcard ();
    aListLocal.m_sNsMode = XsdWildcard.NS_LIST;
    aListLocal.m_aNamespaces.add ("");
    final XsdWildcard aListT = new XsdWildcard ();
    aListT.m_sNsMode = XsdWildcard.NS_LIST;
    aListT.m_aNamespaces.add ("urn:t");
    aListT.m_aNamespaces.add ("");

    assertTrue (XsdUpa.wildcardsOverlap (aAny, aAny));
    assertTrue (XsdUpa.wildcardsOverlap (aAny, aOther));
    assertTrue (XsdUpa.wildcardsOverlap (aOther, aOther));
    assertTrue (XsdUpa.wildcardsOverlap (aOther, aListA));
    assertTrue (XsdUpa.wildcardsOverlap (aListA, aOther));
    assertFalse (XsdUpa.wildcardsOverlap (aOther, aListLocal));
    assertFalse (XsdUpa.wildcardsOverlap (aListT, aOther));
    assertFalse (XsdUpa.wildcardsOverlap (aListA, aListLocal));
    assertTrue (XsdUpa.wildcardsOverlap (aListA, aListA));

    // An ##any wildcard excluding a finite list still overlaps everything but
    // a finite list inside the exclusion
    final XsdWildcard aAnyNotA = new XsdWildcard ();
    aAnyNotA.m_sNsMode = XsdWildcard.NS_ANY;
    aAnyNotA.m_aNotNS.add ("urn:a");
    assertFalse (XsdUpa.wildcardsOverlap (aAnyNotA, aListA));
    assertFalse (XsdUpa.wildcardsOverlap (aListA, aAnyNotA));
    assertTrue (XsdUpa.wildcardsOverlap (aAnyNotA, aListT));
    assertTrue (XsdUpa.wildcardsOverlap (aAnyNotA, aOther));

    // A union counts as overlapping nothing
    final XsdWildcard aUnion = new XsdWildcard ();
    aUnion.m_aParts.add (aListA);
    aUnion.m_aParts.add (aOther);
    assertFalse (XsdUpa.wildcardsOverlap (aUnion, aAny));
  }

  @Test
  public void testNullable ()
  {
    final XsdElementDecl aDecl = new XsdElementDecl (new XsdName ("", "a"));
    assertTrue (XsdUpa.upaNullable (null));
    assertFalse (XsdUpa.upaNullable (new XsdParticle (1, 1, aDecl)));
    assertTrue (XsdUpa.upaNullable (new XsdParticle (0, 1, aDecl)));

    final XsdModelGroup aSeq = new XsdModelGroup (EXsdCompositor.SEQUENCE);
    assertTrue (XsdUpa.upaNullable (new XsdParticle (1, 1, aSeq)));
    aSeq.m_aParticles.add (new XsdParticle (0, 1, aDecl));
    assertTrue (XsdUpa.upaNullable (new XsdParticle (1, 1, aSeq)));
    aSeq.m_aParticles.add (new XsdParticle (1, 1, aDecl));
    assertFalse (XsdUpa.upaNullable (new XsdParticle (1, 1, aSeq)));

    // An empty choice matches nothing, not even the empty sequence
    final XsdModelGroup aChoice = new XsdModelGroup (EXsdCompositor.CHOICE);
    assertFalse (XsdUpa.upaNullable (new XsdParticle (1, 1, aChoice)));
    aChoice.m_aParticles.add (new XsdParticle (1, 1, aDecl));
    aChoice.m_aParticles.add (new XsdParticle (0, 1, aDecl));
    assertTrue (XsdUpa.upaNullable (new XsdParticle (1, 1, aChoice)));

    // A cyclic group is not finitely nullable
    final XsdModelGroup aCycle = new XsdModelGroup (EXsdCompositor.SEQUENCE);
    aCycle.m_aParticles.add (new XsdParticle (1, 1, aCycle));
    assertFalse (XsdUpa.upaNullable (new XsdParticle (1, 1, aCycle)));
  }

  /** A deep but legal nesting is checked, a pathologically deep one gives no verdict. */
  @Test
  public void testNestingBound ()
  {
    final StringBuilder aOpen = new StringBuilder ();
    final StringBuilder aClose = new StringBuilder ();
    for (int i = 0; i < 100; i++)
    {
      aOpen.append ("<xs:sequence>");
      aClose.append ("</xs:sequence>");
    }
    _both ("deep-100", aOpen + A + aClose, "", true);

    aOpen.setLength (0);
    aClose.setLength (0);
    for (int i = 0; i < 600; i++)
    {
      aOpen.append ("<xs:sequence>");
      aClose.append ("</xs:sequence>");
    }
    final Compiled aCompiled = XsdTestSupport.compile (XS +
                                                       "<xs:element name=\"doc\"><xs:complexType>" +
                                                       aOpen +
                                                       A +
                                                       aClose +
                                                       "</xs:complexType></xs:element></xs:schema>",
                                                       null,
                                                       EXsdVersion.V10);
    assertEquals (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
  }
}
