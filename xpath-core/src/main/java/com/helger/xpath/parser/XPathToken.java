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
package com.helger.xpath.parser;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A single token of an XPath expression.
 *
 * @param kind
 *        The token kind.
 * @param text
 *        The token text (see {@link ETokenKind} for the special kinds).
 * @param uri
 *        The namespace URI of {@link ETokenKind#BRACED_NAME} and
 *        {@link ETokenKind#BRACED_WILDCARD}, else <code>null</code>.
 * @param pos
 *        The character offset of the token start in the expression.
 * @author Philip Helger
 */
record XPathToken (@NonNull ETokenKind kind, @NonNull String text, @Nullable String uri, int pos)
{
  /** @return <code>true</code> for an unprefixed name with the given text. */
  public boolean isNCName (@NonNull final String sText)
  {
    return kind == ETokenKind.NAME && text.equals (sText);
  }

  /** @return <code>true</code> for {@link ETokenKind#NAME} and {@link ETokenKind#BRACED_NAME}. */
  public boolean isEQName ()
  {
    return kind == ETokenKind.NAME || kind == ETokenKind.BRACED_NAME;
  }

  /** @return <code>true</code> for a name without prefix and not in braced notation. */
  public boolean isUnprefixedName ()
  {
    return kind == ETokenKind.NAME && text.indexOf (':') < 0;
  }

  @NonNull
  public String getDisplayText ()
  {
    return switch (kind)
    {
      case EOF -> "end of expression";
      case STRING -> "string literal";
      case BRACED_NAME -> "Q{" + uri + "}" + text;
      case PREFIX_WILDCARD -> text + ":*";
      case LOCAL_WILDCARD -> "*:" + text;
      case BRACED_WILDCARD -> "Q{" + uri + "}*";
      default -> text;
    };
  }
}
