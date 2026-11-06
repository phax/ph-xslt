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
package com.helger.xpath.bench;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.xpath.bench.BenchData.Line;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xsd.XsdSchema;
import com.helger.xsd.XsdSchemaSource;
import com.helger.xsd.XsdValidationResult;

/**
 * Validates the generated document with the native validator against the invoice schema of the
 * given XSD version: complex and simple types with facets and an identity constraint, for 1.1 also
 * an assertion per line, an assertion facet per price and a type alternative per tax element.
 * Measured: parsing the document text and assessing it, as {@link XsdSchema#validateText(String)}
 * does; not measured: compiling the schema. In the preparation a document with faults is validated
 * as well, and the number of errors is checked, so that the workload cannot silently stop checking
 * a constraint.
 *
 * @author Philip Helger
 */
public final class XsdWorkload implements IWorkload
{
  private final EXsdVersion m_eVersion;
  private final String m_sXml;
  private final XsdSchema m_aSchema;

  /**
   * @param sVersion
   *        The XSD version, <code>1.0</code> or <code>1.1</code>.
   * @param nLines
   *        The number of invoice lines of the document.
   */
  public XsdWorkload (@NonNull final String sVersion, final int nLines)
  {
    m_eVersion = EXsdVersion.getFromIDOrNull (sVersion);
    if (m_eVersion == null)
      throw new IllegalArgumentException ("Unknown XSD version '" + sVersion + "'");
    m_sXml = BenchData.create (nLines, false).getXml ();
    final String sSchema = BenchResources.getText ("xsd/invoice-" + m_eVersion.getID () + ".xsd");
    m_aSchema = XsdSchema.compile (new CommonsArrayList <> (XsdSchemaSource.ofText (sSchema, null)), m_eVersion, null);

    // The faults the schema must find: SKU pattern and quantity range, for 1.1 also the period
    // assertion and the type alternative of a tax element with rate 0
    final BenchData aFaulty = BenchData.create (nLines, true);
    int nExpectedErrors = 0;
    for (final Line aLine : aFaulty.getAllLines ())
    {
      if (!BenchExpected.isSkuValid (aLine))
        nExpectedErrors++;
      if (aLine.qty () < 1)
        nExpectedErrors++;
      if (m_eVersion == EXsdVersion.V11)
      {
        if (aLine.start ().isAfter (aLine.end ()))
          nExpectedErrors++;
        if ("0".equals (aLine.taxRate ()) && aLine.reason () == null)
          nExpectedErrors++;
      }
    }
    final XsdValidationResult aFaultyResult = m_aSchema.validateText (aFaulty.getXml ());
    BenchAssert.assertTrue ("The faulty document is invalid", !aFaultyResult.valid ());
    BenchAssert.assertEquals ("Errors of the faulty document: " + aFaultyResult.errors ().getFirstOrNull (),
                              Integer.valueOf (nExpectedErrors),
                              Integer.valueOf (aFaultyResult.errors ().size ()));
  }

  @NonNull
  public Object run ()
  {
    return m_aSchema.validateText (m_sXml);
  }

  public void verify (@NonNull final Object aResult)
  {
    final XsdValidationResult aValidation = (XsdValidationResult) aResult;
    BenchAssert.assertTrue ("The document is valid against XSD " +
                            m_eVersion.getID () +
                            ": " +
                            aValidation.errors ().getFirstOrNull (),
                            aValidation.valid ());
  }
}
