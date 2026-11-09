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
package com.helger.phoxtrot.xsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.context.EXsdVersion;
import com.helger.phoxtrot.xsd.XsdTestSupport.Compiled;
import com.helger.phoxtrot.xsd.XsdTestSupport.EVerdict;

/**
 * Input-controlled nesting and cycles never overflow the stack: they end in a
 * verdict or in "unsupported" (go-xslt bounds every recursive walk the same
 * way).
 *
 * @author Philip Helger
 */
public final class XsdLimitsTest
{
  private static final String XS = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  /**
   * Run a check on a new thread with the given stack size and rethrow what it
   * failed with: how deep the compiler gets before the stack overflows
   * depends on the JVM and the platform, so the stack of the test thread
   * cannot decide which limit is hit.
   */
  private static void _runWithStack (@NonNull final Runnable aCheck, final long nStackSize) throws InterruptedException
  {
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        aCheck.run ();
      }
      catch (final Throwable t)
      {
        aError.set (t);
      }
    }, "xsd-limits-stack", nStackSize);
    aThread.start ();
    aThread.join ();
    if (aError.get () instanceof final Error aErr)
      throw aErr;
    if (aError.get () instanceof final RuntimeException aRtEx)
      throw aRtEx;
    assertNull (aError.get ());
  }

  @NonNull
  private static String _nestedParticles (final int nDepth)
  {
    final StringBuilder aSB = new StringBuilder (XS + "<xs:element name=\"e\"><xs:complexType>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("<xs:sequence>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("</xs:sequence>");
    aSB.append ("</xs:complexType></xs:element></xs:schema>");
    return aSB.toString ();
  }

  @NonNull
  private static String _nestedLocalElements (final int nDepth)
  {
    final StringBuilder aSB = new StringBuilder (XS);
    for (int i = 0; i < nDepth; i++)
      aSB.append ("<xs:element name=\"e").append (i).append ("\"><xs:complexType><xs:sequence>");
    for (int i = 0; i < nDepth; i++)
      aSB.append ("</xs:sequence></xs:complexType></xs:element>");
    aSB.append ("</xs:schema>");
    return aSB.toString ();
  }

  /** On a stack large enough for it, the nesting limit of the compiler is hit. */
  @Test
  public void testDeeplyNestedParticles () throws InterruptedException
  {
    final String sSchema = _nestedParticles (3000);
    _runWithStack ( () -> {
      final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
      assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
      assertTrue (aCompiled.message (), aCompiled.message ().contains ("nested deeper than " + XsdCompiler.MAX_NESTING));
    }, 64L * 1024 * 1024);
  }

  /** On a stack large enough for it, the nesting limit of the compiler is hit. */
  @Test
  public void testDeeplyNestedLocalElements () throws InterruptedException
  {
    final String sSchema = _nestedLocalElements (2000);
    _runWithStack ( () -> {
      final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
      assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
      assertTrue (aCompiled.message (), aCompiled.message ().contains ("nested deeper than " + XsdCompiler.MAX_NESTING));
    }, 64L * 1024 * 1024);
  }

  /**
   * On a small stack the compiler overflows before its nesting limit; that is
   * "unsupported" as well, never a {@link StackOverflowError}.
   */
  @Test
  public void testDeepNestingOnSmallStack () throws InterruptedException
  {
    // Classes initialised on this thread, only the deep compilations run on
    // the small stack
    assertSame (EVerdict.VALID, XsdTestSupport.compile (_nestedLocalElements (2), null, EXsdVersion.V10).verdict ());
    final String sParticles = _nestedParticles (3000);
    final String sElements = _nestedLocalElements (2000);
    _runWithStack ( () -> {
      for (final String sSchema : new String [] { sParticles, sElements })
      {
        final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
        assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
        assertTrue (aCompiled.message (), aCompiled.message ().contains ("stack of the calling thread"));
      }
    }, 128L * 1024);
  }

  @Test
  public void testLongSimpleTypeChain ()
  {
    // Each type restricts the next one: a dependency chain longer than the
    // nesting bound
    final int nLength = 3000;
    final StringBuilder aSB = new StringBuilder (XS);
    for (int i = 0; i < nLength; i++)
      aSB.append ("<xs:simpleType name=\"t")
         .append (i)
         .append ("\"><xs:restriction base=\"")
         .append (i + 1 < nLength ? "t" + (i + 1) : "xs:string")
         .append ("\"/></xs:simpleType>");
    aSB.append ("</xs:schema>");
    final Compiled aCompiled = XsdTestSupport.compile (aSB.toString (), null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.UNSUPPORTED, aCompiled.verdict ());
  }

  @Test
  public void testCycles ()
  {
    // Circular type derivation, group and attribute group references, and
    // substitution groups are schema errors, found without recursion trouble
    for (final String sBody : new String [] { "<xs:complexType name=\"a\"><xs:complexContent><xs:extension base=\"b\"/></xs:complexContent></xs:complexType>" +
                                              "<xs:complexType name=\"b\"><xs:complexContent><xs:extension base=\"a\"/></xs:complexContent></xs:complexType>",
                                              "<xs:group name=\"g\"><xs:sequence><xs:group ref=\"h\"/></xs:sequence></xs:group>" +
                                                                                                                                                 "<xs:group name=\"h\"><xs:sequence><xs:group ref=\"g\"/></xs:sequence></xs:group>",
                                              "<xs:attributeGroup name=\"g\"><xs:attributeGroup ref=\"h\"/></xs:attributeGroup>" +
                                                                                                                                                                                                      "<xs:attributeGroup name=\"h\"><xs:attributeGroup ref=\"g\"/></xs:attributeGroup>",
                                              "<xs:element name=\"a\" substitutionGroup=\"b\"/><xs:element name=\"b\" substitutionGroup=\"a\"/>",
                                              "<xs:simpleType name=\"s\"><xs:restriction base=\"u\"/></xs:simpleType><xs:simpleType name=\"u\"><xs:list itemType=\"s\"/></xs:simpleType>" })
    {
      final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, EXsdVersion.V10);
      assertSame (sBody + ": " + aCompiled.message (), EVerdict.INVALID, aCompiled.verdict ());
    }
  }

  /**
   * A union that names itself as a member is a tolerated pure-union cycle in
   * XSD 1.0; walking its members must stay bounded (go-xslt 2168ab5 overflows
   * its stack in checkNotationUsable here).
   */
  @Test
  public void testSelfReferencingUnion ()
  {
    for (final String sBody : new String [] { "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:element name=\"e\" type=\"u\"/>",
                                              "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:string\"/></xs:simpleType><xs:simpleType name=\"v\"><xs:restriction base=\"u\"/></xs:simpleType>" })
    {
      final Compiled aCompiled = XsdTestSupport.compile (XS + sBody + "</xs:schema>", null, EXsdVersion.V10);
      assertTrue (sBody + ": " + aCompiled.message (), aCompiled.verdict () != EVerdict.INVALID);
    }
  }

  /**
   * A wide self-referencing union used as an element type must not be walked
   * exponentially by the NOTATION usability check.
   */
  @Test(timeout = 20000)
  public void testWideSelfReferencingUnion ()
  {
    final int nMembers = 40;
    final StringBuilder aSB = new StringBuilder (XS + "<xs:simpleType name=\"u\"><xs:union memberTypes=\"");
    for (int i = 0; i < nMembers; i++)
      aSB.append ("u ");
    aSB.append ("xs:string\"/></xs:simpleType><xs:element name=\"e\" type=\"u\"/></xs:schema>");
    final Compiled aCompiled = XsdTestSupport.compile (aSB.toString (), null, EXsdVersion.V10);
    assertTrue (aCompiled.message (), aCompiled.verdict () != EVerdict.INVALID);
  }

  /**
   * go-xslt TestNoStackOverflow, issue 12: a union listing itself compiles in
   * XSD 1.0 with the members that do not lead back as its value space.
   */
  @Test
  public void testSelfMemberUnionValidates ()
  {
    final String sSchema = XS +
                           "<xs:simpleType name=\"u\"><xs:union memberTypes=\"u xs:int\"/></xs:simpleType>" +
                           "<xs:simpleType name=\"v\"><xs:restriction base=\"u\"/></xs:simpleType>" +
                           "<xs:element name=\"e\" type=\"v\"/></xs:schema>";
    final Compiled aCompiled = XsdTestSupport.compile (sSchema, null, EXsdVersion.V10);
    assertSame (aCompiled.message (), EVerdict.VALID, aCompiled.verdict ());
    assertSame (EVerdict.VALID, XsdTestSupport.validate (aCompiled.schema (), "<e>7</e>"));
    assertSame (EVerdict.INVALID, XsdTestSupport.validate (aCompiled.schema (), "<e>x</e>"));
  }

  @Test
  public void testCyclicIncludesTerminate () throws IOException
  {
    final Path aDir = m_aTempFolder.newFolder ().toPath ();
    Files.writeString (aDir.resolve ("a.xsd"),
                       XS + "<xs:include schemaLocation=\"b.xsd\"/><xs:element name=\"a\" type=\"xs:string\"/></xs:schema>");
    Files.writeString (aDir.resolve ("b.xsd"),
                       XS + "<xs:include schemaLocation=\"a.xsd\"/><xs:element name=\"b\" type=\"xs:string\"/></xs:schema>");
    // Not the collection constructor: a Path is itself Iterable
    final ICommonsList <Path> aFiles = new CommonsArrayList <> ();
    aFiles.add (aDir.resolve ("a.xsd"));
    final XsdSchema aSchema = XsdSchema.compileFiles (aFiles, EXsdVersion.V10, null);
    assertEquals (2, aSchema.getView ().getAllGlobalElements ().size ());
  }

  @Test
  public void testDeepOverrideSignature ()
  {
    // The dedup key of an xs:override walks its children's subtrees, which
    // xs:appinfo content can nest arbitrarily deep
    final IXdmNode aOverride = IXdmNode.createElement (new XdmQName (XsdConstants.NS_XS, "override"));
    IXdmNode aCur = aOverride;
    for (int i = 0; i < 100_000; i++)
    {
      final IXdmNode aChild = IXdmNode.createElement (new XdmQName ("urn:x", "n"));
      aCur.appendChild (aChild);
      aCur = aChild;
    }
    try
    {
      XsdOverride.signature (aOverride);
      fail ("expected XsdUnsupportedException");
    }
    catch (final XsdUnsupportedException ex)
    {
      assertTrue (ex.getMessage (), ex.getMessage ().contains ("nested deeper"));
    }
  }
}
