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
package com.helger.xsd.conformance;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmValidationException;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xsd.XsdError;
import com.helger.xsd.XsdFileAccess;
import com.helger.xsd.XsdInvalidException;
import com.helger.xsd.XsdSchema;
import com.helger.xsd.XsdSchemaProvider;
import com.helger.xsd.XsdUnsupportedException;
import com.helger.xsd.XsdValidationResult;

/**
 * The differential gate of the bridge (go-xslt <code>TestValidateNodeMatchesValidate</code>):
 * validating a tree in place reuses the whole validation engine but reaches it down a different
 * road, a detached, text-normalised clone instead of a freshly parsed document. That road is where
 * "a node built outside the normal path is missing an invariant other code depends on" bugs live,
 * so the guard is empirical: every instance document of the xsdtests suite (XSD 1.0) goes through
 * both entry points and the verdicts must agree, case by case. Skipped when the suite is absent
 * (<code>-Dxsd.dir=...</code>).
 *
 * @author Philip Helger
 */
public final class XsdBridgeDifferentialTest
{
  // Qualified: the harness has its own EXsdVersion of the same name
  private static final com.helger.xpath.context.EXsdVersion ENGINE_VERSION = com.helger.xpath.context.EXsdVersion.V10;

  private static final class Tally
  {
    final AtomicInteger m_aAgree = new AtomicInteger ();
    /** Not well-formed: a parser question, not a bridge one. */
    final AtomicInteger m_aParseSkip = new AtomicInteger ();
    /** One side (or both) reported "no verdict". */
    final AtomicInteger m_aNoVerdict = new AtomicInteger ();
    final ConcurrentLinkedQueue <String> m_aMismatches = new ConcurrentLinkedQueue <> ();
  }

  /**
   * Validate reads the schema location hints of the document element before walking; the bridge
   * deliberately does not (the component set of an XSLT host is static). Replicate the merge, so
   * that what is compared is the bridge and not that documented difference in where components come
   * from.
   */
  @NonNull
  private static XsdSchema _withHints (@NonNull final XsdSchema aSchema,
                                       @NonNull final ICommonsList <Path> aDocs,
                                       @NonNull final Path aInstance,
                                       @NonNull final XdmNode aRoot)
  {
    final ICommonsList <String> aLocations = new CommonsArrayList <> ();
    final XdmNode aSchemaLocation = aRoot.getAttribute (XdmConstants.NS_XSI, "schemaLocation");
    if (aSchemaLocation != null)
    {
      final String [] aFields = aSchemaLocation.getValue ().trim ().split ("\\s+");
      for (int i = 0; i + 1 < aFields.length; i += 2)
        aLocations.add (aFields[i + 1]);
    }
    final XdmNode aNoNamespace = aRoot.getAttribute (XdmConstants.NS_XSI, "noNamespaceSchemaLocation");
    if (aNoNamespace != null)
      aLocations.add (aNoNamespace.getValue ().trim ());
    final Path aBaseDir = aSchema.getBaseDir () != null ? aSchema.getBaseDir ()
                                                        : aInstance.toAbsolutePath ().getParent ();
    final ICommonsList <Path> aMerged = new CommonsArrayList <> (aDocs);
    boolean bAdded = false;
    for (final String sLocation : aLocations)
    {
      final Path aPath = XsdFileAccess.resolveLocation (aBaseDir, sLocation);
      if (aPath != null && Files.isRegularFile (aPath))
      {
        aMerged.add (aPath);
        bAdded = true;
      }
    }
    if (!bAdded)
      return aSchema;
    try
    {
      return XsdSchema.compileFiles (aMerged, ENGINE_VERSION, null);
    }
    catch (final IOException | XsdInvalidException | XsdUnsupportedException ex)
    {
      // keeps the schema without the hints
      return aSchema;
    }
  }

  private static void _compareOne (@NonNull final XsdSchema aSchema,
                                   @NonNull final ICommonsList <Path> aDocs,
                                   @NonNull final XsdInstanceTest aTest,
                                   @NonNull final Path aInstance,
                                   @NonNull final Tally aTally)
  {
    final XdmNode aDocument;
    try
    {
      aDocument = new XdmParser ().parse (aInstance);
    }
    catch (final XdmParseException ex)
    {
      aTally.m_aParseSkip.incrementAndGet ();
      return;
    }
    XdmNode aRoot = null;
    for (final XdmNode aChild : aDocument.getAllChildren ())
      if (aChild.isElement ())
        aRoot = aChild;
    if (aRoot == null)
    {
      aTally.m_aParseSkip.incrementAndGet ();
      return;
    }

    final boolean bText;
    final XsdValidationResult aTextResult;
    try
    {
      aTextResult = aSchema.validate (aInstance);
      bText = aTextResult.valid ();
    }
    catch (final XsdUnsupportedException ex)
    {
      aTally.m_aNoVerdict.incrementAndGet ();
      return;
    }

    final IXdmSchemaSet aSet = XsdSchemaProvider.createSchemaSet (_withHints (aSchema, aDocs, aInstance, aRoot));
    boolean bInPlace = true;
    String sDetail = "valid";
    try
    {
      aSet.validateDocument (aDocument);
    }
    catch (final XdmValidationException ex)
    {
      bInPlace = false;
      sDetail = ex.getMessage ();
    }
    catch (final XsdUnsupportedException ex)
    {
      aTally.m_aNoVerdict.incrementAndGet ();
      return;
    }
    if (bText == bInPlace)
    {
      // The first error of the in-place validation (first-error mode) is among
      // the errors the collecting validation of the text reports: same code or
      // the very same text (a content-model error is worded more precisely)
      boolean bFound = bInPlace;
      if (!bInPlace)
        for (final XsdError aError : aTextResult.errors ())
          if (sDetail.endsWith (aError.toString ()) || sDetail.contains (aError.code () + ": ") && !aError.code ().isEmpty ())
            bFound = true;
      if (bFound)
        aTally.m_aAgree.incrementAndGet ();
      else
        aTally.m_aMismatches.add (aTest.name () +
                                  ": first error of the in-place validation is not among the collected ones: " +
                                  sDetail +
                                  " vs " +
                                  aTextResult.errors ());
    }
    else
      aTally.m_aMismatches.add (aTest.name () +
                                ": text says valid=" +
                                bText +
                                ", in place says valid=" +
                                bInPlace +
                                " (" +
                                sDetail +
                                ")");
  }

  private static void _compareGroup (@NonNull final XsdTestGroup aGroup,
                                     @NonNull final Path aDir,
                                     @NonNull final Tally aTally)
  {
    final EXsdVersion eVersion = EXsdVersion.XSD_10;
    final XsdSchemaTest aSchemaTest = aGroup.schemaTest ();
    if (!XsdSuiteRules.isVersionApplicable (aGroup.version (), eVersion) ||
        aSchemaTest == null ||
        !XsdSuiteRules.isVersionApplicable (aSchemaTest.version (), eVersion) ||
        aSchemaTest.documents ().isEmpty ())
      return;
    // Only groups whose schema compiles have instances worth comparing
    final ICommonsList <Path> aDocs = new CommonsArrayList <> ();
    for (final String sHref : aSchemaTest.documents ())
    {
      final Path aDoc = aDir.resolve (sHref).normalize ();
      if (!Files.isRegularFile (aDoc))
        return;
      aDocs.add (aDoc);
    }
    final XsdSchema aSchema;
    try
    {
      aSchema = XsdSchema.compileFiles (aDocs, ENGINE_VERSION, null);
    }
    catch (final IOException | XsdInvalidException | XsdUnsupportedException ex)
    {
      return;
    }
    for (final XsdInstanceTest aTest : aGroup.instanceTests ())
    {
      if (!XsdSuiteRules.isVersionApplicable (aTest.version (), eVersion))
        continue;
      final Path aInstance = aDir.resolve (aTest.document ()).normalize ();
      if (!Files.isRegularFile (aInstance))
        continue;
      try
      {
        _compareOne (aSchema, aDocs, aTest, aInstance, aTally);
      }
      catch (final RuntimeException | StackOverflowError ex)
      {
        // A crash can only come from the bridge: the text path survives this corpus
        aTally.m_aMismatches.add (aTest.name () + ": crashed: " + ex);
      }
    }
  }

  private static void _compareSet (@NonNull final String sHref,
                                   @NonNull final XsdSettings aSettings,
                                   @NonNull final Tally aTally)
  {
    final XsdTestSet aSet;
    try
    {
      aSet = XsdCatalogReader.readTestSet (aSettings.suiteDir ().resolve (sHref));
    }
    catch (final IOException ex)
    {
      return;
    }
    if (!XsdSuiteRules.isVersionApplicable (aSet.version (), EXsdVersion.XSD_10))
      return;
    for (final XsdTestGroup aGroup : aSet.groups ())
      _compareGroup (aGroup, aSet.dir (), aTally);
  }

  @Test
  public void testInPlaceMatchesFromText () throws IOException
  {
    final XsdSettings aSettings = XsdSettings.fromSystemProperties ();
    Assume.assumeTrue ("xsdtests suite not present at " + aSettings.suiteDir (),
                       Files.isRegularFile (aSettings.getSuiteFile ()));
    final ICommonsList <String> aRefs = new CommonsArrayList <> ();
    for (final String sHref : XsdCatalogReader.readSuite (aSettings.getSuiteFile ()))
      if (aSettings.only ().isEmpty () || sHref.contains (aSettings.only ()))
        aRefs.add (sHref);

    final Tally aTally = new Tally ();
    aRefs.parallelStream ().forEach (sHref -> _compareSet (sHref, aSettings, aTally));

    int nShown = 0;
    for (final String sMismatch : aTally.m_aMismatches)
      if (nShown++ < 25)
        System.err.println ("MISMATCH " + sMismatch);
    System.out.println ("XSD 1.0: " +
                        aTally.m_aAgree.get () +
                        " instances agree, " +
                        aTally.m_aMismatches.size () +
                        " mismatch, " +
                        aTally.m_aNoVerdict.get () +
                        " no verdict, " +
                        aTally.m_aParseSkip.get () +
                        " unparsable");
    assertTrue ("in place and from text differ: " + aTally.m_aMismatches, aTally.m_aMismatches.isEmpty ());
    if (aSettings.only ().isEmpty ())
      assertTrue ("coverage too thin to mean anything: only " + aTally.m_aAgree.get () + " instances compared",
                  aTally.m_aAgree.get () > 1000);
  }
}
