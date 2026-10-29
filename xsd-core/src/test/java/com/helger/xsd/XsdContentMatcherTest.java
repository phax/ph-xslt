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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.BitSet;
import java.util.List;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xsd.XsdTestSupport.Compiled;
import com.helger.xsd.XsdTestSupport.EVerdict;

/**
 * Tests of the content matcher ({@link XsdContentMatcher}) and the linear
 * counting matcher ({@link XsdBigMatch}), driven directly on the effective
 * content model of the declaration <code>doc</code>, so they do not depend on the
 * instance validator. The large-model cases are ported from go-xslt
 * <code>content_bigmatch_test.go</code>; they exercise the counting matcher above
 * 600 children.
 *
 * @author Philip Helger
 */
public final class XsdContentMatcherTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";

  private record BigCase (String name, String model, String extra, ICommonsList <String> kids, boolean valid)
  {}

  @SafeVarargs
  private static ICommonsList <String> _cat (final List <String>... aParts)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (final List <String> aPart : aParts)
      ret.addAll (aPart);
    return ret;
  }

  private static ICommonsList <String> _rep (final int nCount, final String... aNames)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    for (int i = 0; i < nCount; i++)
      for (final String sName : aNames)
        ret.add (sName);
      return ret;
  }

  private static ICommonsList <String> _of (final String... aNames)
  {
    return new CommonsArrayList <> (aNames);
  }

  private static List <IXdmNode> _kids (final List <String> aNames)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final String sName : aNames)
    {
      final int nClose = sName.indexOf ('}');
      final XdmQName aQName = sName.startsWith ("{") ? new XdmQName (sName.substring (1, nClose),
                                                                    sName.substring (nClose + 1))
                                                     : new XdmQName (sName);
      ret.add (IXdmNode.createElement (aQName));
    }
    return ret;
  }

  private static XsdSchema _compile (final String sModel, final String sExtra, final EXsdVersion eVersion)
  {
    final Compiled aCompiled = XsdTestSupport.compile (XS +
                                                       "<xs:element name=\"doc\"><xs:complexType>" +
                                                       sModel +
                                                       "</xs:complexType></xs:element>" +
                                                       sExtra +
                                                       "</xs:schema>",
                                                       null,
                                                       eVersion);
    assertTrue (String.valueOf (aCompiled.message ()), aCompiled.verdict () == EVerdict.VALID);
    return aCompiled.schema ();
  }

  private static XsdComplexType _docType (final XsdSchema aSchema)
  {
    final XsdElementDecl aDecl = aSchema.m_aElements.get (new XsdName ("", "doc"));
    assertNotNull (aDecl);
    return (XsdComplexType) aDecl.m_aType;
  }

  private static XsdMatchResult _match (final XsdSchema aSchema, final List <String> aKids)
  {
    return XsdContentMatcher.contentMatches (aSchema, aSchema.effectiveParticle (_docType (aSchema)), _kids (aKids));
  }

  private static void _check (final String sLabel,
                              final XsdSchema aSchema,
                              final boolean bExpected,
                              final String... aKids)
  {
    final XsdMatchResult aResult = _match (aSchema, _of (aKids));
    assertTrue (sLabel + " decided", aResult.decided ());
    assertEquals (sLabel, bExpected, aResult.matched ());
  }

  @Test
  public void testSequence ()
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\"/><xs:element name=\"b\" minOccurs=\"0\"/><xs:element name=\"c\"/></xs:sequence>",
                                          "",
                                          eVersion);
      _check ("a c", aSchema, true, "a", "c");
      _check ("a b c", aSchema, true, "a", "b", "c");
      _check ("c a", aSchema, false, "c", "a");
      _check ("a b", aSchema, false, "a", "b");
      _check ("a b b c", aSchema, false, "a", "b", "b", "c");
      _check ("empty", aSchema, false);
      _check ("namespace", aSchema, false, "{urn:x}a", "c");
    }
  }

  @Test
  public void testEmptyContent ()
  {
    final XsdSchema aSchema = _compile ("", "", EXsdVersion.V10);
    assertEquals (new XsdMatchResult (true, true), XsdContentMatcher.contentMatches (aSchema, null, _kids (_of ())));
    assertEquals (new XsdMatchResult (false, true),
                  XsdContentMatcher.contentMatches (aSchema, null, _kids (_of ("a"))));
  }

  @Test
  public void testRepeatedGroup ()
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile ("<xs:sequence minOccurs=\"2\" maxOccurs=\"3\"><xs:element name=\"a\"/><xs:element name=\"b\"/></xs:sequence>",
                                          "",
                                          eVersion);
      _check ("one round", aSchema, false, "a", "b");
      _check ("two rounds", aSchema, true, "a", "b", "a", "b");
      _check ("three rounds", aSchema, true, "a", "b", "a", "b", "a", "b");
      _check ("four rounds", aSchema, false, "a", "b", "a", "b", "a", "b", "a", "b");
      _check ("broken", aSchema, false, "a", "b", "a");
    }
  }

  @Test
  public void testSubstitutionGroups ()
  {
    final String sExtra = "<xs:element name=\"h\"/><xs:element name=\"m\" substitutionGroup=\"h\"/>" +
                          "<xs:element name=\"hh\" abstract=\"true\"/><xs:element name=\"mm\" substitutionGroup=\"hh\"/>";
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element ref=\"h\" maxOccurs=\"unbounded\"/><xs:element ref=\"hh\" minOccurs=\"0\"/></xs:sequence>",
                                        sExtra,
                                        EXsdVersion.V10);
    _check ("head", aSchema, true, "h");
    _check ("member", aSchema, true, "m", "h", "m");
    _check ("abstract head", aSchema, false, "h", "hh");
    _check ("abstract member", aSchema, true, "h", "mm");
  }

  @Test
  public void testAllGroup ()
  {
    for (final EXsdVersion eVersion : EXsdVersion.values ())
    {
      final XsdSchema aSchema = _compile ("<xs:all><xs:element name=\"a\"/><xs:element name=\"b\" minOccurs=\"0\"/><xs:element name=\"c\"/></xs:all>",
                                          "",
                                          eVersion);
      _check ("c a", aSchema, true, "c", "a");
      _check ("b c a", aSchema, true, "b", "c", "a");
      _check ("a b", aSchema, false, "a", "b");
      _check ("a a c", aSchema, false, "a", "a", "c");
      _check ("empty", aSchema, false);
    }
  }

  @Test
  public void testWildcardWithElementCompetition11 ()
  {
    // Valid in 1.1 only (the element declaration wins the competition): the
    // counting matcher declines such a model and the position-set matcher decides
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\" minOccurs=\"0\"/><xs:any processContents=\"skip\" minOccurs=\"0\" maxOccurs=\"unbounded\"/></xs:sequence>",
                                        "",
                                        EXsdVersion.V11);
    final XsdParticle aModel = aSchema.effectiveParticle (_docType (aSchema));
    assertFalse (XsdBigMatch.particleDeterministic (aSchema, aModel));
    assertFalse (XsdBigMatch.particleDeterministic (aSchema, aModel));
    _check ("a x y", aSchema, true, "a", "x", "y");
    _check ("empty", aSchema, true);
    assertEquals (new XsdMatchResult (false, false),
                  XsdBigMatch.countMatch (aSchema, aModel, _kids (_rep (700, "x"))));
    final XsdMatchResult aBig = XsdContentMatcher.contentMatches (aSchema, aModel, _kids (_rep (700, "x")));
    assertFalse (aBig.decided ());
  }

  @Test
  public void testDeterministicMemo ()
  {
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/></xs:sequence>",
                                        "",
                                        EXsdVersion.V10);
    final XsdParticle aModel = aSchema.effectiveParticle (_docType (aSchema));
    assertEquals (0, aModel.m_aDet.get ());
    assertTrue (XsdBigMatch.particleDeterministic (aSchema, aModel));
    assertEquals (1, aModel.m_aDet.get ());
    assertTrue (XsdBigMatch.particleDeterministic (aSchema, aModel));
    final XsdSchema aAll = _compile ("<xs:all><xs:element name=\"a\"/></xs:all>", "", EXsdVersion.V10);
    final XsdParticle aAllModel = aAll.effectiveParticle (_docType (aAll));
    assertFalse (XsdBigMatch.particleDeterministic (aAll, aAllModel));
    assertEquals (2, aAllModel.m_aDet.get ());
  }

  private static XsdOpenContent _open (final String sMode, final String sNamespace)
  {
    final XsdWildcard aWC = new XsdWildcard ();
    aWC.m_sNsMode = sNamespace;
    aWC.m_sProcess = XsdWildcard.PROCESS_LAX;
    return new XsdOpenContent (sMode, aWC);
  }

  @Test
  public void testOpenContent ()
  {
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\"/><xs:element name=\"b\"/></xs:sequence>",
                                        "",
                                        EXsdVersion.V11);
    final XsdParticle aModel = aSchema.effectiveParticle (_docType (aSchema));
    final XsdOpenContent aInterleave = _open (XsdOpenContent.MODE_INTERLEAVE, XsdWildcard.NS_ANY);
    final XsdOpenContent aSuffix = _open (XsdOpenContent.MODE_SUFFIX, XsdWildcard.NS_ANY);

    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "x", "b", "y")), aInterleave));
    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("x", "a", "b")), aInterleave));
    assertEquals (new XsdMatchResult (false, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "x")), aInterleave));
    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "b", "x", "y")), aSuffix));
    assertEquals (new XsdMatchResult (false, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "x", "b")), aSuffix));
    // No open content: the plain matcher
    assertEquals (new XsdMatchResult (false, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "x", "b")), null));
    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_of ("a", "b")), null));
    // Empty content with open content
    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, null, _kids (_of ("x", "y")), aInterleave));
    assertEquals (new XsdMatchResult (true, true),
                  XsdContentMatcher.contentMatchesOpen (aSchema, null, _kids (_of ("x", "y")), aSuffix));
    // Above 600 children there is no verdict
    assertFalse (XsdContentMatcher.contentMatchesOpen (aSchema, aModel, _kids (_rep (601, "x")), aInterleave)
                                  .decided ());
  }

  @Test
  public void testGreedyOpenAttribution ()
  {
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\"/></xs:sequence>", "", EXsdVersion.V11);
    final XsdParticle aModel = aSchema.effectiveParticle (_docType (aSchema));
    final XsdOpenContent aInterleave = _open (XsdOpenContent.MODE_INTERLEAVE, XsdWildcard.NS_ANY);
    // The first a belongs to the model, the second falls to the wildcard
    BitSet aOpen = XsdContentMatcher.greedyOpenAttribution (aSchema, aModel, _kids (_of ("a", "a")), aInterleave);
    assertEquals (1, aOpen.cardinality ());
    assertTrue (aOpen.get (1));
    aOpen = XsdContentMatcher.greedyOpenAttribution (aSchema, aModel, _kids (_of ("x", "a", "y")), aInterleave);
    assertTrue (aOpen.get (0));
    assertFalse (aOpen.get (1));
    assertTrue (aOpen.get (2));
    // Not applicable
    assertNull (XsdContentMatcher.greedyOpenAttribution (aSchema,
                                                         aModel,
                                                         _kids (_of ("a", "a")),
                                                         _open (XsdOpenContent.MODE_SUFFIX, XsdWildcard.NS_ANY)));
    assertNull (XsdContentMatcher.greedyOpenAttribution (aSchema, null, _kids (_of ("a")), aInterleave));

    final XsdSchema aRepeat = _compile ("<xs:sequence><xs:element name=\"a\" minOccurs=\"0\" maxOccurs=\"unbounded\"/></xs:sequence>",
                                        "",
                                        EXsdVersion.V11);
    final XsdParticle aRepeatModel = aRepeat.effectiveParticle (_docType (aRepeat));
    // A repeatable model element takes the repeat (open025)
    assertTrue (XsdContentMatcher.greedyOpenAttribution (aRepeat,
                                                         aRepeatModel,
                                                         _kids (_of ("a", "a", "a")),
                                                         aInterleave)
                                 .isEmpty ());
  }

  private static final ICommonsList <BigCase> BIG_CASES = new CommonsArrayList <> ();

  private static void _big (final String sName,
                            final String sModel,
                            final String sExtra,
                            final ICommonsList <String> aKids,
                            final boolean bValid)
  {
    BIG_CASES.add (new BigCase (sName, sModel, sExtra, aKids, bValid));
  }

  static
  {
    _big ("seq-bounded-ok",
          "<xs:sequence><xs:element name=\"a\" maxOccurs=\"1000\"/></xs:sequence>",
          "",
          _rep (1000, "a"),
          true);
    _big ("seq-min-met",
          "<xs:sequence><xs:element name=\"a\" minOccurs=\"700\" maxOccurs=\"unbounded\"/><xs:element name=\"b\"/></xs:sequence>",
          "",
          _cat (_rep (700, "a"), _of ("b")),
          true);
    _big ("repeated-pair",
          "<xs:sequence maxOccurs=\"unbounded\"><xs:element name=\"k\"/><xs:element name=\"v\"/></xs:sequence>",
          "",
          _rep (400, "k", "v"),
          true);
    _big ("repeated-pair-broken",
          "<xs:sequence maxOccurs=\"unbounded\"><xs:element name=\"k\"/><xs:element name=\"v\"/></xs:sequence>",
          "",
          _cat (_rep (400, "k", "v"), _of ("k")),
          false);
    _big ("repeated-pair-optional-v",
          "<xs:sequence maxOccurs=\"unbounded\"><xs:element name=\"k\"/><xs:element name=\"v\" minOccurs=\"0\"/></xs:sequence>",
          "",
          _cat (_rep (350, "k"), _rep (350, "k", "v")),
          true);
    _big ("choice-unbounded",
          "<xs:choice maxOccurs=\"unbounded\"><xs:element name=\"p\"/><xs:element name=\"q\"/></xs:choice>",
          "",
          _rep (300, "p", "q", "q"),
          true);
    _big ("choice-unbounded-stranger",
          "<xs:choice maxOccurs=\"unbounded\"><xs:element name=\"p\"/><xs:element name=\"q\"/></xs:choice>",
          "",
          _cat (_rep (400, "p", "q"), _of ("r")),
          false);
    _big ("choice-of-sequences",
          "<xs:choice maxOccurs=\"unbounded\"><xs:sequence><xs:element name=\"p\"/><xs:element name=\"q\"/></xs:sequence><xs:element name=\"r\"/></xs:choice>",
          "",
          _rep (300, "p", "q", "r"),
          true);
    _big ("choice-of-sequences-broken",
          "<xs:choice maxOccurs=\"unbounded\"><xs:sequence><xs:element name=\"p\"/><xs:element name=\"q\"/></xs:sequence><xs:element name=\"r\"/></xs:choice>",
          "",
          _cat (_rep (300, "p", "q", "r"), _of ("p", "r")),
          false);
    _big ("header-body-footer",
          "<xs:sequence><xs:element name=\"h\" minOccurs=\"0\"/><xs:element name=\"i\" maxOccurs=\"unbounded\"/><xs:element name=\"f\" minOccurs=\"0\"/></xs:sequence>",
          "",
          _cat (_of ("h"), _rep (900, "i"), _of ("f")),
          true);
    _big ("footer-before-items",
          "<xs:sequence><xs:element name=\"h\" minOccurs=\"0\"/><xs:element name=\"i\" maxOccurs=\"unbounded\"/><xs:element name=\"f\" minOccurs=\"0\"/></xs:sequence>",
          "",
          _cat (_of ("f"), _rep (900, "i")),
          false);
    _big ("wildcard-unbounded",
          "<xs:sequence><xs:any processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence>",
          "",
          _rep (400, "x", "y"),
          true);
    _big ("wildcard-namespace",
          "<xs:sequence><xs:any namespace=\"##other\" processContents=\"skip\" maxOccurs=\"unbounded\"/></xs:sequence>",
          "",
          _cat (_rep (700, "{urn:p}x"), _of ("local")),
          false);
    _big ("substitution-members",
          "<xs:sequence><xs:element ref=\"h\" maxOccurs=\"unbounded\"/></xs:sequence>",
          "<xs:element name=\"h\"/><xs:element name=\"m1\" substitutionGroup=\"h\"/><xs:element name=\"m2\" substitutionGroup=\"h\"/>",
          _rep (300, "h", "m1", "m2"),
          true);
    _big ("substitution-abstract-head",
          "<xs:sequence><xs:element ref=\"h\" maxOccurs=\"unbounded\"/></xs:sequence>",
          "<xs:element name=\"h\" abstract=\"true\"/><xs:element name=\"m1\" substitutionGroup=\"h\"/>",
          _cat (_rep (700, "m1"), _of ("h")),
          false);
    _big ("group-ref",
          "<xs:sequence><xs:group ref=\"g\" maxOccurs=\"unbounded\"/></xs:sequence>",
          "<xs:group name=\"g\"><xs:sequence><xs:element name=\"a\"/><xs:element name=\"b\" minOccurs=\"0\"/></xs:sequence></xs:group>",
          _cat (_rep (350, "a", "b"), _rep (10, "a")),
          true);
    _big ("removed-particle",
          "<xs:sequence><xs:element name=\"a\" maxOccurs=\"unbounded\"/><xs:element name=\"z\" minOccurs=\"0\" maxOccurs=\"0\"/></xs:sequence>",
          "",
          _cat (_rep (700, "a"), _of ("z")),
          false);
  }

  /**
   * Above 600 children the linear counting matcher decides every
   * name-deterministic model (go-xslt <code>TestLargeContentModels</code>).
   */
  @Test
  public void testLargeContentModels ()
  {
    for (final BigCase aCase : BIG_CASES)
      for (final EXsdVersion eVersion : EXsdVersion.values ())
      {
        final String sLabel = aCase.name () + "/" + eVersion.getID ();
        final XsdSchema aSchema = _compile (aCase.model (), aCase.extra (), eVersion);
        assertTrue (sLabel + " kids", aCase.kids ().size () > 600);
        final XsdMatchResult aResult = _match (aSchema, aCase.kids ());
        assertTrue (sLabel + " decided", aResult.decided ());
        assertEquals (sLabel, aCase.valid (), aResult.matched ());
      }
  }

  /**
   * The same models on instances of at most 600 children, where the counting
   * matcher's positive verdict is taken first and the position-set matcher
   * decides the rest: both engines agree on the verdict.
   */
  @Test
  public void testSmallInstancesAgreeWithCountingMatcher ()
  {
    for (final BigCase aCase : BIG_CASES)
    {
      final XsdSchema aSchema = _compile (aCase.model (), aCase.extra (), EXsdVersion.V10);
      final XsdParticle aModel = aSchema.effectiveParticle (_docType (aSchema));
      // Keep every 20th child run: both ends of the instance stay
      final ICommonsList <String> aSmall = new CommonsArrayList <> ();
      final ICommonsList <String> aKids = aCase.kids ();
      for (int i = 0; i < aKids.size (); i++)
        if (i < 8 || i >= aKids.size () - 8 || i % 20 == 0)
          aSmall.add (aKids.get (i));
      final XsdMatchResult aNfa = XsdContentMatcher.contentMatches (aSchema, aModel, _kids (aSmall));
      final XsdMatchResult aCount = XsdBigMatch.countMatch (aSchema, aModel, _kids (aSmall));
      if (aNfa.decided () && aCount.decided ())
        assertEquals (aCase.name (), aNfa.matched (), aCount.matched ());
    }
  }

  /** The bounds on recursion: very deep content models give no verdict, they do not overflow the stack. */
  @Test
  public void testDeeplyNestedModelsAreBounded ()
  {
    final XsdSchema aSchema = _compile ("<xs:sequence><xs:element name=\"a\"/></xs:sequence>", "", EXsdVersion.V10);
    final XsdElementDecl aDecl = new XsdElementDecl (new XsdName ("", "a"));
    XsdParticle aDeep = new XsdParticle (1, 1, aDecl);
    for (int i = 0; i < 20000; i++)
    {
      final XsdModelGroup aGroup = new XsdModelGroup (EXsdCompositor.SEQUENCE);
      aGroup.m_aParticles.add (aDeep);
      aDeep = new XsdParticle (1, 1, aGroup);
    }
    final List <IXdmNode> aKids = _kids (_of ("a"));
    assertFalse (XsdBigMatch.particleDeterministic (aSchema, aDeep));
    assertFalse (XsdContentMatcher.contentMatches (aSchema, aDeep, aKids).decided ());
    assertEquals (new XsdMatchResult (false, false), XsdBigMatch.countMatch (aSchema, aDeep, aKids));
    try
    {
      XsdUpa.checkUPA (aSchema, aDeep);
      fail ();
    }
    catch (final XsdUnsupportedException ex)
    {
      // expected
    }
    try
    {
      XsdUpa.upaNullable (aDeep);
      fail ();
    }
    catch (final XsdUnsupportedException ex)
    {
      // expected
    }
  }
}
