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
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.xdm.XdmNode;

/**
 * Validation of values against simple types (go-xslt <code>simpletype.go</code>:
 * <code>validate</code>, <code>validateIn</code>, <code>check</code>,
 * <code>checkIn</code>, <code>fixedEqual</code>, <code>atomicEqual</code>,
 * <code>firstMemberRaw</code>, the primitive lexical checks).
 * <p>
 * <b>Phase V3 owns this class.</b> Until then every method is a stub. The
 * helpers the loader needed are already ported elsewhere and stay where they
 * are: {@link XsdSimpleType#resolvePrim()},
 * {@link XsdSimpleType#effectiveWhiteSpace()}, {@link XsdWhiteSpace},
 * {@link XsdLexHelper}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdSimpleTypeValidator
{
  @PresentForCodeCoverage
  private static final XsdSimpleTypeValidator INSTANCE = new XsdSimpleTypeValidator ();

  private XsdSimpleTypeValidator ()
  {}

  /**
   * go-xslt <code>SimpleType.validate</code>: apply the type's whiteSpace and
   * check the value (lexical space, facets, list items, union members). A value
   * of a QName/NOTATION-based type is checked without namespace context.
   *
   * @param aType
   *        The simple type. {@link XsdSimpleType#ABSENT} refuses every value.
   * @param sRaw
   *        The raw (not yet normalised) value.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validate (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>SimpleType.validateIn</code>: {@link #validate} with the
   * namespace context of an instance node (for QName/NOTATION prefixes).
   *
   * @param aType
   *        The simple type.
   * @param sRaw
   *        The raw value.
   * @param aAt
   *        The element or attribute node the value sits on; <code>null</code>
   *        for none.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void validateIn (@NonNull final XsdSimpleType aType, @NonNull final String sRaw, @Nullable final XdmNode aAt)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>SimpleType.check</code>: check an already whitespace
   * normalised value.
   *
   * @param aType
   *        The simple type.
   * @param sValue
   *        The normalised value.
   * @throws XsdInvalidException
   *         if the value is not valid
   */
  static void check (@NonNull final XsdSimpleType aType, @NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>SimpleType.fixedEqual</code>: whether instance content equals
   * a fixed value constraint in the type's value space.
   *
   * @param aType
   *        The simple type.
   * @param sContent
   *        The instance value.
   * @param sFixed
   *        The fixed value.
   * @return <code>true</code> if equal.
   */
  static boolean fixedEqual (@NonNull final XsdSimpleType aType,
                             @NonNull final String sContent,
                             @NonNull final String sFixed)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>SimpleType.firstMemberRaw</code>: the first union member that
   * accepts the raw value (XSD 1.1 assertions and CTA need it).
   *
   * @param aType
   *        The union type.
   * @param sRaw
   *        The raw value.
   * @return The member, or <code>null</code>.
   */
  @Nullable
  static XsdSimpleType firstMemberRaw (@NonNull final XsdSimpleType aType, @NonNull final String sRaw)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * go-xslt <code>checkPrimitiveLexical</code>.
   *
   * @param ePrim
   *        The built-in primitive.
   * @param sValue
   *        The normalised value.
   * @throws XsdInvalidException
   *         if the value is not in the primitive's lexical space
   */
  static void checkPrimitiveLexical (@NonNull final EAtomicType ePrim, @NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
