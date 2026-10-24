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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * Test class for class {@link XSLT30Dependencies}.
 *
 * @author Philip Helger
 */
public final class XSLT30DependenciesTest
{
  private static XSLT30Dependency _d (final String sType, final String sValue)
  {
    return new XSLT30Dependency (sType, sValue, "");
  }

  private static XSLT30Dependency _not (final String sType, final String sValue)
  {
    return new XSLT30Dependency (sType, sValue, "false");
  }

  @Nullable
  private static String _skip (final XSLT30Dependency... aCaseDeps)
  {
    return XSLT30Dependencies.getSkipReason (new CommonsArrayList <> (), new CommonsArrayList <> (aCaseDeps));
  }

  @Test
  public void testSpec ()
  {
    assertNull (_skip ());
    assertNull (_skip (_d ("spec", "XSLT10+")));
    assertNull (_skip (_d ("spec", "XSLT20+")));
    assertNull (_skip (_d ("spec", "XSLT30+")));
    assertNull (_skip (_d ("spec", "XP31+")));
    assertEquals ("spec:XSLT20", _skip (_d ("spec", "XSLT20")));
    assertEquals ("spec:XSLT10", _skip (_d ("spec", "XSLT10")));
    assertEquals ("spec:XSLT10 XSLT20", _skip (_d ("spec", "XSLT10 XSLT20")));
    // satisfied="false" never restricts the spec
    assertNull (_skip (_not ("spec", "XSLT20")));
  }

  @Test
  public void testFeatures ()
  {
    assertEquals ("feat:streaming", _skip (_d ("feature", "streaming")));
    assertEquals ("feat:schema_aware", _skip (_d ("feature", "schema_aware")));
    assertEquals ("feat:backwards_compatibility", _skip (_d ("feature", "backwards_compatibility")));
    assertEquals ("feat:streaming-fallback", _skip (_d ("feature", "streaming-fallback")));
    // A processor without streaming / schema-awareness is what we are
    assertNull (_skip (_not ("feature", "streaming")));
    assertNull (_skip (_not ("feature", "schema_aware")));
    // Claimed features: the variants for a processor without them do not apply
    assertEquals ("feat-not:dynamic_evaluation", _skip (_not ("feature", "dynamic_evaluation")));
    assertEquals ("feat-not:disabling_output_escaping", _skip (_not ("feature", "disabling_output_escaping")));
    assertEquals ("feat-not:XPath_3.1", _skip (_not ("feature", "XPath_3.1")));
    assertEquals ("feat-not:XSD_1.1", _skip (_not ("feature", "XSD_1.1")));
    // Neither claimed nor unsupported: always runs
    assertNull (_skip (_d ("feature", "higher_order_functions")));
    assertNull (_skip (_d ("feature", "serialization")));
    assertNull (_skip (_d ("feature", "namespace_axis")));
    assertNull (_skip (_not ("feature", "namespace_axis")));
    assertNull (_skip (_d ("feature", "dynamic_evaluation")));
  }

  @Test
  public void testOtherDependencies ()
  {
    assertEquals ("xsd1.0", _skip (_d ("xsd-version", "1.0")));
    assertNull (_skip (_d ("xsd-version", "1.1")));
    assertNull (_skip (_d ("unicode-version", "9.0")));
    assertEquals ("year-component-values-unsupported-variant",
                  _skip (_not ("year_component_values", "support negative year")));
    assertNull (_skip (_d ("year_component_values", "support year above 9999")));
    assertNull (_skip (_d ("languages_for_numbering", "de")));
    assertNull (_skip (_d ("languages_for_numbering", "fr-CA")));
    assertEquals ("lang:ja", _skip (_d ("languages_for_numbering", "ja")));
    assertNull (_skip (_d ("default_html_version", "5")));
    assertEquals ("default_html_version:4", _skip (_d ("default_html_version", "4")));
    assertNull (_skip (_d ("enable_assertions", "")));
    assertEquals ("feat:assertions-disabled", _skip (_not ("enable_assertions", "")));
    assertEquals ("feat:ignore_doc_failure", _skip (new XSLT30Dependency ("ignore_doc_failure", "", "true")));
    assertNull (_skip (_not ("ignore_doc_failure", "")));
    assertNull (_skip (_d ("package_version_resolution", "highest_version")));
    assertNull (_skip (_d ("package_version_resolution", "unspecified")));
    assertEquals ("package_version_resolution:lowest_version",
                  _skip (_d ("package_version_resolution", "lowest_version")));
    // Not checked at all
    assertNull (_skip (_d ("combinations_for_numbering", "CIRCLED DIGIT ONE")));
  }

  @Test
  public void testOrder ()
  {
    // The test set's dependencies come first
    final ICommonsList <XSLT30Dependency> aSet = new CommonsArrayList <> (_d ("feature", "schema_aware"));
    final ICommonsList <XSLT30Dependency> aCase = new CommonsArrayList <> (_d ("spec", "XSLT20"));
    assertEquals ("feat:schema_aware", XSLT30Dependencies.getSkipReason (aSet, aCase));
    // Within one element, spec before feature regardless of document order
    assertEquals ("spec:XSLT20", _skip (_d ("feature", "streaming"), _d ("spec", "XSLT20")));
  }
}
