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
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Direct tests of {@link XsdRestrictionChecker}: the wildcard namespace subset
 * and the bounds of the recursion. The schema level behaviour is covered by
 * {@link XsdRestrictionDerivationTest} and {@link XsdWildcardRestrictionTest}.
 *
 * @author Philip Helger
 */
public final class XsdRestrictionCheckerTest
{
  private static XsdWildcard _any ()
  {
    final XsdWildcard ret = new XsdWildcard ();
    ret.m_sNsMode = XsdWildcard.NS_ANY;
    ret.m_sProcess = XsdWildcard.PROCESS_LAX;
    return ret;
  }

  private static XsdWildcard _other (final String sTarget)
  {
    final XsdWildcard ret = new XsdWildcard ();
    ret.m_sNsMode = XsdWildcard.NS_OTHER;
    ret.m_sTargetNS = sTarget;
    ret.m_sProcess = XsdWildcard.PROCESS_LAX;
    return ret;
  }

  private static XsdWildcard _list (final String... aNamespaces)
  {
    final XsdWildcard ret = new XsdWildcard ();
    ret.m_sNsMode = XsdWildcard.NS_LIST;
    ret.m_aNamespaces.addAll (Arrays.asList (aNamespaces));
    ret.m_sProcess = XsdWildcard.PROCESS_LAX;
    return ret;
  }

  private static XsdWildcard _not (final XsdWildcard aBase, final String... aNotNamespaces)
  {
    aBase.m_aNotNS.addAll (Arrays.asList (aNotNamespaces));
    return aBase;
  }

  @Test
  public void testNsSubsetPositive ()
  {
    assertTrue (XsdRestrictionChecker.nsSubset (_any (), _any ()));
    assertTrue (XsdRestrictionChecker.nsSubset (_other ("urn:t"), _any ()));
    assertTrue (XsdRestrictionChecker.nsSubset (_list ("urn:a"), _any ()));
    assertTrue (XsdRestrictionChecker.nsSubset (_other ("urn:t"), _other ("urn:t")));
    assertTrue (XsdRestrictionChecker.nsSubset (_list ("urn:a", "urn:b"), _other ("urn:t")));
    assertTrue (XsdRestrictionChecker.nsSubset (_list ("urn:a"), _list ("urn:a", "urn:b")));
    assertTrue (XsdRestrictionChecker.nsSubset (_list (), _list ("urn:a")));
  }

  @Test
  public void testNsSubsetNegative ()
  {
    assertFalse (XsdRestrictionChecker.nsSubset (_any (), _other ("urn:t")));
    assertFalse (XsdRestrictionChecker.nsSubset (_other ("urn:u"), _other ("urn:t")));
    assertFalse (XsdRestrictionChecker.nsSubset (_list ("urn:t"), _other ("urn:t")));
    assertFalse (XsdRestrictionChecker.nsSubset (_list (""), _other ("urn:t")));
    assertFalse (XsdRestrictionChecker.nsSubset (_any (), _list ("urn:a")));
    assertFalse (XsdRestrictionChecker.nsSubset (_other ("urn:t"), _list ("urn:a")));
    assertFalse (XsdRestrictionChecker.nsSubset (_list ("urn:a", "urn:c"), _list ("urn:a", "urn:b")));
  }

  @Test
  public void testNsSubsetNegated ()
  {
    // b = any but urn:x
    assertTrue (XsdRestrictionChecker.nsSubset (_list ("urn:y"), _not (_any (), "urn:x")));
    assertFalse (XsdRestrictionChecker.nsSubset (_list ("urn:x"), _not (_any (), "urn:x")));
    assertTrue (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:x", "urn:y"), _not (_any (), "urn:x")));
    assertFalse (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:y"), _not (_any (), "urn:x")));
    assertFalse (XsdRestrictionChecker.nsSubset (_other ("urn:t"), _not (_any (), "urn:x")));
    assertTrue (XsdRestrictionChecker.nsSubset (_other ("urn:t"), _not (_any (), "urn:t", "")));
    assertFalse (XsdRestrictionChecker.nsSubset (_any (), _not (_any (), "urn:x")));
    // a = any but urn:x
    assertTrue (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:x"), _any ()));
    assertFalse (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:x"), _list ("urn:y")));
    assertTrue (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:t", ""), _other ("urn:t")));
    assertFalse (XsdRestrictionChecker.nsSubset (_not (_any (), "urn:t"), _other ("urn:t")));
  }

  @Test
  public void testFlags ()
  {
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>",
                                                       null,
                                                       EXsdVersion.V10);
    assertEquals (EVerdict.VALID, aCompiled.verdict ());
    final XsdRestrictionChecker aChecker = new XsdRestrictionChecker (aCompiled.schema ());
    assertFalse (aChecker.isInMapAndSum ());
    assertTrue (new XsdRestrictionChecker (aCompiled.schema (), true).isInMapAndSum ());
    assertTrue (aChecker.getSchema () == aCompiled.schema ());
    // Both empty
    assertTrue (aChecker.particleRestrictsOK (null, null));
    final XsdParticle aRequired = new XsdParticle (1, 1, new XsdElementDecl (new XsdName ("", "a")));
    assertFalse (aChecker.particleRestrictsOK (null, aRequired));
    assertTrue (aChecker.particleRestrictsOK (null, new XsdParticle (0, 1, aRequired.m_aTerm)));
    assertFalse (aChecker.particleRestrictsOK (aRequired, null));
    assertTrue (XsdRestrictionChecker.matchesOnlyEmpty (new XsdParticle (1, 1, new XsdModelGroup ())));
    assertFalse (XsdRestrictionChecker.matchesOnlyEmpty (aRequired));
  }

  /** Very deep content models give no verdict instead of overflowing the stack. */
  @Test
  public void testDeeplyNestedModelsAreBounded ()
  {
    final Compiled aCompiled = XsdTestSupport.compile ("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>",
                                                       null,
                                                       EXsdVersion.V11);
    final XsdElementDecl aDecl = new XsdElementDecl (new XsdName ("", "a"));
    XsdParticle aDeep = new XsdParticle (1, 1, aDecl);
    XsdParticle aDeepChoice = new XsdParticle (1, 2, aDecl);
    for (int i = 0; i < 20000; i++)
    {
      final XsdModelGroup aSeq = new XsdModelGroup (EXsdCompositor.SEQUENCE);
      aSeq.m_aParticles.add (aDeep);
      aSeq.m_aParticles.add (new XsdParticle (0, 1, aDecl));
      aDeep = new XsdParticle (1, 1, aSeq);
      final XsdModelGroup aChoice = new XsdModelGroup (EXsdCompositor.CHOICE);
      aChoice.m_aParticles.add (aDeepChoice);
      aDeepChoice = new XsdParticle (1, 2, aChoice);
    }
    final XsdRestrictionChecker aChecker = new XsdRestrictionChecker (aCompiled.schema ());
    try
    {
      aChecker.particleRestrictsOK (aDeep, aDeepChoice);
      fail ();
    }
    catch (final XsdUnsupportedException ex)
    {
      // expected
    }
    try
    {
      XsdRestrictionChecker.matchesOnlyEmpty (aDeep);
      fail ();
    }
    catch (final XsdUnsupportedException ex)
    {
      // expected
    }
    // The checker stays usable after the bound was hit
    assertTrue (aChecker.particleRestrictsOK (null, null));
  }
}
