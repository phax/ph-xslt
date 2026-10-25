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
package com.helger.xslt.conformance;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsTreeMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.collection.commons.ICommonsSortedMap;
import com.helger.xpath.conformance.EQT3Status;

/**
 * The canary for schema-awareness, a port of go-xslt's
 * <code>TestXSLT30SchemaUnawareCases</code>: the 31 catalog cases that declare
 * <code>feature schema_aware satisfied="false"</code>, i.e. that are written for
 * a processor which is not schema-aware and check its XTSE1660-family
 * rejections and untyped results. Without the claim they must all run and
 * none may fail; with the claim they must all be skipped. The list is what
 * <code>grep -rl 'feature value="schema_aware" satisfied="false"' tests/</code>
 * yields case by case; derive it again if the suite is updated.
 *
 * @author Philip Helger
 */
public final class XSLT30SchemaUnawareCasesTest
{
  private static final ICommonsSet <String> CASES = new CommonsHashSet <> ("built-in-templates-0301",
                                                                          "error-1535b",
                                                                          "error-1650a",
                                                                          "error-1660a",
                                                                          "error-1660b",
                                                                          "error-1660c",
                                                                          "error-1660d",
                                                                          "error-1660e",
                                                                          "error-1665a",
                                                                          "error-3245a",
                                                                          "import-schema-191",
                                                                          "json-to-xml-typed-010",
                                                                          "strip-type-annotations-023",
                                                                          "strip-type-annotations-024",
                                                                          "strip-type-annotations-025",
                                                                          "try-015",
                                                                          "try-017",
                                                                          "try-025",
                                                                          "try-026",
                                                                          "type-0303",
                                                                          "type-available-0148",
                                                                          "validation-0101",
                                                                          "validation-0102a",
                                                                          "validation-0102b",
                                                                          "validation-0103a",
                                                                          "validation-0103b",
                                                                          "validation-0104",
                                                                          "validation-0105a",
                                                                          "validation-0105b",
                                                                          "validation-0106",
                                                                          "validation-0106a");

  /**
   * @return Case name to outcome (status and reason).
   */
  private static ICommonsSortedMap <String, String> _run (final XSLT30Catalog aCatalog, final boolean bSchemaAware) throws IOException
  {
    final XSLT30CaseRunner aRunner = new XSLT30CaseRunner (aCatalog,
                                                           IXSLT30Compiler.ENGINE,
                                                           new XSLT30StylesheetCache (),
                                                           OffsetDateTime.now (),
                                                           false,
                                                           false,
                                                           bSchemaAware);
    final ICommonsSortedMap <String, String> ret = new CommonsTreeMap <> ();
    for (final XSLT30Catalog.TestSetRef aRef : aCatalog.testSets ())
    {
      final Path aFile = aCatalog.rootDir ().resolve (aRef.file ());
      if (!Files.isRegularFile (aFile))
        continue;
      final XSLT30TestSet aSet = XSLT30CatalogReader.readTestSet (aFile);
      final XSLT30DocumentCache aDocs = new XSLT30DocumentCache ();
      for (final XSLT30TestCase aCase : aSet.testCases ())
        if (CASES.contains (aCase.name ()))
        {
          final XSLT30CaseRunner.CaseResult aResult = aRunner.run (aSet, aCase, aDocs);
          ret.put (aCase.name (), aResult.outcome ().status () + " " + aResult.outcome ().reason ());
        }
    }
    return ret;
  }

  @Test
  public void testSchemaUnawareCases () throws IOException
  {
    final XSLT30Settings aSettings = XSLT30Settings.fromSystemProperties ();
    Assume.assumeTrue ("xslt30-test suite not present at " + aSettings.suiteDir (),
                       Files.isRegularFile (aSettings.getCatalogFile ()));
    final XSLT30Catalog aCatalog = XSLT30CatalogReader.readCatalog (aSettings.getCatalogFile ());

    // Without the claim: they run, and none fails (a few are pinned to XSLT
    // 2.0 and skip on the spec version)
    final ICommonsSortedMap <String, String> aBasic = _run (aCatalog, false);
    final ICommonsList <String> aMissing = new CommonsArrayList <> ();
    for (final String sCase : CASES)
      if (!aBasic.containsKey (sCase))
        aMissing.add (sCase);
    assertTrue ("not found in the catalog (suite updated?): " + aMissing, aMissing.isEmpty ());
    int nPass = 0;
    for (final Map.Entry <String, String> aEntry : aBasic.entrySet ())
    {
      assertTrue (aEntry.getKey () + " fails without the schema-awareness claim: " + aEntry.getValue (),
                  !aEntry.getValue ().startsWith (EQT3Status.FAIL.name ()));
      if (aEntry.getValue ().startsWith (EQT3Status.PASS.name ()))
        nPass++;
    }
    assertTrue ("none of the cases ran; the canary is vacuous", nPass > 0);

    // With the claim: all are skipped
    final ICommonsSortedMap <String, String> aSchemaAware = _run (aCatalog, true);
    for (final Map.Entry <String, String> aEntry : aSchemaAware.entrySet ())
      assertTrue (aEntry.getKey () + " is not skipped with the schema-awareness claim: " + aEntry.getValue (),
                  aEntry.getValue ().startsWith (EQT3Status.SKIP.name ()));
  }
}
