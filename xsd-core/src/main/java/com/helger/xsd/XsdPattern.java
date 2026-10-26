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

import com.helger.xpath.context.EXsdVersion;

/**
 * A compiled XSD pattern facet (regular expression of XML Schema Part 2,
 * Appendix F; go-xslt <code>compileXSDPattern</code> /
 * <code>validateXSDRegex</code>).
 * <p>
 * <b>Phase V3 owns this class.</b> Until then every method is a stub.
 *
 * @author Philip Helger
 */
final class XsdPattern
{
  private final String m_sSource;

  private XsdPattern (@NonNull final String sSource)
  {
    m_sSource = sSource;
  }

  /** @return The pattern as written in the schema. */
  @NonNull
  String getSource ()
  {
    return m_sSource;
  }

  /**
   * Check a pattern against the XSD regular expression grammar of the given
   * version (go-xslt <code>validateXSDRegex</code>).
   *
   * @param sPattern
   *        The facet value.
   * @param eVersion
   *        The XSD version.
   * @throws XsdInvalidException
   *         if the pattern is not a valid XSD regular expression (the schema
   *         is then invalid)
   */
  static void validateSyntax (@NonNull final String sPattern, @NonNull final EXsdVersion eVersion)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * Compile a pattern that passed {@link #validateSyntax(String, EXsdVersion)}
   * (go-xslt <code>compileXSDPattern</code>). A pattern the engine cannot
   * execute must throw {@link XsdUnsupportedException}, never an invalid
   * verdict.
   *
   * @param sPattern
   *        The facet value.
   * @param eVersion
   *        The XSD version.
   * @return The compiled pattern, anchored at both ends as XSD requires.
   */
  @NonNull
  static XsdPattern compile (@NonNull final String sPattern, @NonNull final EXsdVersion eVersion)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }

  /**
   * @param sValue
   *        The (whitespace-normalised) value.
   * @return <code>true</code> if the whole value matches.
   */
  boolean matches (@NonNull final String sValue)
  {
    throw new UnsupportedOperationException ("Not implemented yet");
  }
}
