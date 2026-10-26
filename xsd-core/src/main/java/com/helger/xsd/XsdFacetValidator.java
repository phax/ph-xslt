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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.context.EXsdVersion;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * Constraining facets (go-xslt <code>facets.go</code>): the compile-time check
 * of facet values against the base type, and the facet checks of a value.
 * <p>
 * <b>Phase V3 owns this class.</b> Until then every method is a stub. The
 * narrowing rules between a restriction step and its base
 * (<code>checkFacetConsistency</code> in go-xslt <code>loader.go</code>) are
 * part of the loader ({@link XsdCompiler}).
 *
 * @author Philip Helger
 */
@Immutable
final class XsdFacetValidator
{
  @PresentForCodeCoverage
  private static final XsdFacetValidator INSTANCE = new XsdFacetValidator ();

  private XsdFacetValidator ()
  {}

  /**
   * go-xslt <code>validateFacetValues</code>: the facets declared at this
   * restriction step must be applicable to the type and their values valid
   * (e.g. a maxInclusive that is a valid value of the base, a length on a
   * type that has one, bounds in order). Called by the loader after a simple
   * type restriction is parsed.
   *
   * @param aType
   *        The simple type just parsed.
   * @param eVersion
   *        The XSD version.
   * @throws XsdInvalidException
   *         if a facet is not applicable or its value invalid (schema error)
   */
  static void validateFacetValues (@NonNull final XsdSimpleType aType, @NonNull final EXsdVersion eVersion)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>facetSet.checkIn</code>: check a normalised value against the
   * facets of one restriction step.
   *
   * @param aFacets
   *        The facets.
   * @param sValue
   *        The normalised value.
   * @param ePrim
   *        The primitive the value belongs to.
   * @param aAt
   *        The instance node for QName/NOTATION resolution; may be
   *        <code>null</code>.
   * @throws XsdInvalidException
   *         if a facet is violated
   */
  static void checkIn (@NonNull final XsdFacetSet aFacets,
                       @NonNull final String sValue,
                       @NonNull final EAtomicType ePrim,
                       @Nullable final XdmNode aAt)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>facetSet.checkPatternEnum</code>: only the pattern and
   * enumeration facets (the bridge uses it).
   *
   * @param aFacets
   *        The facets.
   * @param sValue
   *        The normalised value.
   * @param ePrim
   *        The primitive.
   * @throws XsdInvalidException
   *         if a facet is violated
   */
  static void checkPatternEnum (@NonNull final XsdFacetSet aFacets,
                                @NonNull final String sValue,
                                @NonNull final EAtomicType ePrim)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>facetSet.checkListLength</code>.
   *
   * @param aFacets
   *        The facets of a list type step.
   * @param nItems
   *        The number of list items.
   * @throws XsdInvalidException
   *         if a length facet is violated
   */
  static void checkListLength (@NonNull final XsdFacetSet aFacets, final int nItems)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
