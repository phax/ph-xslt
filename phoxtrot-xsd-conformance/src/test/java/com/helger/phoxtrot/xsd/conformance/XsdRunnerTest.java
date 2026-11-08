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
package com.helger.phoxtrot.xsd.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.collection.commons.CommonsArrayList;

/**
 * Test class for class {@link XsdRunner}: the counting rules on a small suite,
 * with the Xerces-J validator.
 *
 * @author Philip Helger
 */
public final class XsdRunnerTest
{
  private static final String TS = "xmlns='http://www.w3.org/XML/2004/xml-schema-test-suite/' xmlns:xlink='http://www.w3.org/1999/xlink'";

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  private Path m_aRoot;

  private void _write (@NonNull final String sName, @NonNull final String sContent) throws IOException
  {
    final Path aFile = m_aRoot.resolve (sName);
    Files.createDirectories (aFile.getParent ());
    Files.writeString (aFile, sContent, StandardCharsets.UTF_8);
  }

  @Before
  public void setUp () throws IOException
  {
    m_aRoot = m_aTmp.getRoot ().toPath ();
    _write ("suite.xml", """
        <testSuite %s>
          <testSetRef xlink:href="meta/a.testSet"/>
          <testSetRef xlink:href="meta/b.testSet"/>
        </testSuite>
        """.formatted (TS));
    _write ("data/s.xsd", """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="r" type="xs:int"/>
        </xs:schema>
        """);
    _write ("data/bad.xsd", """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="r" type="Undefined"/>
        </xs:schema>
        """);
    _write ("data/v.xml", "<r>1</r>");
    _write ("data/i.xml", "<r>x</r>");
    _write ("data/hint.xml",
            "<r xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:noNamespaceSchemaLocation='s.xsd'>2</r>");
    _write ("meta/a.testSet", """
        <testSet %s name="a">
          <testGroup name="g1">
            <schemaTest name="g1s"><schemaDocument xlink:href="../data/s.xsd"/><expected validity="valid"/></schemaTest>
            <instanceTest name="g1-ok"><instanceDocument xlink:href="../data/v.xml"/><expected validity="valid"/></instanceTest>
            <instanceTest name="g1-bad"><instanceDocument xlink:href="../data/i.xml"/><expected validity="valid"/></instanceTest>
            <instanceTest name="g1-queried">
              <instanceDocument xlink:href="../data/i.xml"/><expected validity="valid"/>
              <current status="accepted"/><current status="queried"/>
            </instanceTest>
            <instanceTest name="g1-11" version="1.1"><instanceDocument xlink:href="../data/i.xml"/><expected validity="invalid"/></instanceTest>
            <instanceTest name="g1-open"><instanceDocument xlink:href="../data/i.xml"/><expected validity="indeterminate"/></instanceTest>
          </testGroup>
          <testGroup name="g2">
            <schemaTest name="g2s"><schemaDocument xlink:href="../data/bad.xsd"/><expected validity="invalid"/></schemaTest>
            <instanceTest name="g2-never"><instanceDocument xlink:href="../data/v.xml"/><expected validity="valid"/></instanceTest>
          </testGroup>
          <testGroup name="g3">
            <instanceTest name="g3-hint"><instanceDocument xlink:href="../data/hint.xml"/><expected validity="valid"/></instanceTest>
          </testGroup>
          <testGroup name="g4">
            <schemaTest name="g4s"><schemaDocument xlink:href="../data/absent.xsd"/><expected validity="valid"/></schemaTest>
          </testGroup>
        </testSet>
        """.formatted (TS));
    _write ("meta/b.testSet", """
        <testSet %s name="b" version="1.1">
          <testGroup name="b1">
            <schemaTest name="b1s"><schemaDocument xlink:href="../data/s.xsd"/><expected validity="valid"/></schemaTest>
          </testGroup>
        </testSet>
        """.formatted (TS));
  }

  @NonNull
  private XsdRunResult _run (@NonNull final EXsdVersion eVersion) throws IOException
  {
    final XsdSettings aSettings = new XsdSettings (EXsdTestValidatorKind.XERCES,
                                                   m_aRoot,
                                                   "",
                                                   new CommonsArrayList <> (eVersion),
                                                   false,
                                                   false,
                                                   false,
                                                   true,
                                                   true,
                                                   m_aRoot.resolve ("out"),
                                                   m_aRoot.resolve ("accepted.md"));
    return new XsdRunner (aSettings, XercesXsdTestValidator::new).run (eVersion);
  }

  @Test
  public void testXsd10 () throws IOException
  {
    final XsdRunResult aResult = _run (EXsdVersion.XSD_10);
    // g1s, g1-ok, g2s, g3-hint
    assertEquals (4, aResult.getPass ());
    assertEquals (List.of ("g1-bad"), aResult.getFailNames ());
    assertEquals (1, aResult.getQueried ());
    assertEquals (0, aResult.getUnsupported ());
    // g1-open (no verdict), g4s (missing file)
    assertEquals (2, aResult.getSkip ());
    assertEquals (Integer.valueOf (1), aResult.getSkipReasons ().get ("no-instance-verdict"));
    assertEquals (Integer.valueOf (1), aResult.getSkipReasons ().get ("missing-schema-file"));
    assertTrue (aResult.getSummary ().contains ("5 applicable"));
  }

  @Test
  public void testXsd11Unsupported () throws IOException
  {
    final XsdRunResult aResult = _run (EXsdVersion.XSD_11);
    assertEquals (0, aResult.getPass ());
    assertEquals (0, aResult.getFail ());
    // g1s, g1-ok, g1-bad, g1-11, g2s, g3-hint, b1s
    assertEquals (7, aResult.getUnsupported ());
    assertEquals (1, aResult.getQueried ());
    assertEquals (2, aResult.getSkip ());
    assertTrue (aResult.getSummary ().contains ("is not implemented by"));

    aResult.writeFiles (m_aRoot.resolve ("out"));
    assertTrue (Files.isRegularFile (m_aRoot.resolve ("out/xsd11-fails.txt")));
    assertTrue (Files.isRegularFile (m_aRoot.resolve ("out/xsd11-byset.csv")));
  }

  /** A validator whose compilation always crashes. */
  private static final class CrashingValidator implements IXsdTestValidator
  {
    private final boolean m_bFullXPathInCta;

    CrashingValidator (final boolean bFullXPathInCta)
    {
      m_bFullXPathInCta = bFullXPathInCta;
    }

    @NonNull
    public String getDisplayName ()
    {
      return "crashing";
    }

    public boolean isVersionSupported (@NonNull final EXsdVersion eVersion)
    {
      return true;
    }

    @Override
    public boolean isFullXPathInCtaSupported (@NonNull final EXsdVersion eVersion)
    {
      return m_bFullXPathInCta;
    }

    @NonNull
    public XsdCompileResult compile (@NonNull final List <Path> aSchemaDocuments, @NonNull final EXsdVersion eVersion)
    {
      throw new IllegalStateException ("boom");
    }
  }

  @NonNull
  private XsdRunResult _runCrashing (final boolean bFullXPathInCta) throws IOException
  {
    final Path aRoot = m_aRoot.resolve ("crash");
    Files.createDirectories (aRoot.resolve ("meta"));
    Files.writeString (aRoot.resolve ("suite.xml"), """
        <testSuite %s><testSetRef xlink:href="meta/c.testSet"/></testSuite>
        """.formatted (TS), StandardCharsets.UTF_8);
    Files.writeString (aRoot.resolve ("meta/c.testSet"), """
        <testSet %s name="c" version="1.1">
          <testGroup name="q1">
            <schemaTest name="q1s">
              <schemaDocument xlink:href="../../data/s.xsd"/><expected validity="valid"/><current status="queried"/>
            </schemaTest>
          </testGroup>
          <testGroup name="cta1" version="full-xpath-in-CTA">
            <schemaTest name="cta1s">
              <schemaDocument xlink:href="../../data/s.xsd"/>
              <expected validity="valid" version="full-xpath-in-CTA"/>
              <expected validity="invalid" version="restricted-xpath-in-CTA"/>
            </schemaTest>
          </testGroup>
          <testGroup name="cta2">
            <schemaTest name="cta2s">
              <schemaDocument xlink:href="../../data/s.xsd"/>
              <expected validity="valid" version="full-xpath-in-CTA"/>
              <expected validity="invalid" version="restricted-xpath-in-CTA"/>
            </schemaTest>
          </testGroup>
        </testSet>
        """.formatted (TS), StandardCharsets.UTF_8);
    final XsdSettings aSettings = new XsdSettings (EXsdTestValidatorKind.XERCES,
                                                   aRoot,
                                                   "",
                                                   new CommonsArrayList <> (EXsdVersion.XSD_11),
                                                   false,
                                                   false,
                                                   false,
                                                   true,
                                                   true,
                                                   aRoot.resolve ("out"),
                                                   aRoot.resolve ("accepted.md"));
    return new XsdRunner (aSettings, () -> new CrashingValidator (bFullXPathInCta)).run (EXsdVersion.XSD_11);
  }

  @Test
  public void testQueriedCrashAndCtaTokens () throws IOException
  {
    final XsdRunResult aFull = _runCrashing (true);
    // The queried schema test crashed: queried, not failed
    assertEquals (1, aFull.getQueried ());
    assertEquals (1, aFull.getQueriedCrash ());
    assertTrue (aFull.getSummary ().contains ("compilation crashed: 1"));
    // The full-xpath-in-CTA group and verdict apply to a full XPath validator
    assertEquals (List.of ("cta1s", "cta2s"), aFull.getFailNames ());
    assertEquals (0, aFull.getSkip ());

    // A validator with the required subset only: the group is out of scope,
    // the restricted verdict applies
    final XsdRunResult aRestricted = _runCrashing (false);
    assertEquals (List.of ("cta2s"), aRestricted.getFailNames ());
    assertEquals (1, aRestricted.getQueriedCrash ());
  }
}
