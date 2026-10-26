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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;

/**
 * Attribute assessment of an element against a complex type (go-xslt
 * <code>attributes.go</code>).
 * <p>
 * <b>Phase V5 owns this class.</b> Until then every method is a stub.
 * <code>lexEqualCollapsed</code> is already ported as
 * {@link XsdLexHelper#lexEqualCollapsed(String, String)}.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdAttributeValidator
{
  @PresentForCodeCoverage
  private static final XsdAttributeValidator INSTANCE = new XsdAttributeValidator ();

  private XsdAttributeValidator ()
  {}

  /**
   * go-xslt <code>validateAttributes</code>.
   *
   * @param aRun
   *        The validation run.
   * @param aType
   *        The governing complex type.
   * @param aElement
   *        The instance element.
   * @throws XsdInvalidException
   *         if an attribute is not valid
   */
  static void validateAttributes (@NonNull final XsdValidationRun aRun,
                                  @NonNull final XsdComplexType aType,
                                  @NonNull final XdmNode aElement)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
