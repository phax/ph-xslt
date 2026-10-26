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

/**
 * Hand-written lexical scanners for the hot datatypes (go-xslt
 * <code>lexscan.go</code>).
 * <p>
 * <b>Phase V3 owns this class.</b> Until then every method is a stub.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdLexScan
{
  @PresentForCodeCoverage
  private static final XsdLexScan INSTANCE = new XsdLexScan ();

  private XsdLexScan ()
  {}

  /** @return <code>true</code> for <code>[+-]?[0-9]+</code>. */
  static boolean isIntegerLex (@NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /** @return <code>true</code> for the xs:decimal lexical space. */
  static boolean isDecimalLex (@NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /** @return <code>true</code> for the NCName production. */
  static boolean isNCNameLex (@NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
