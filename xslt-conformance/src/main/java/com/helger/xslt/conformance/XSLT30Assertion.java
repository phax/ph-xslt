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

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * One assertion element below <code>&lt;result&gt;</code> (e.g.
 * <code>assert</code>, <code>assert-xml</code>, <code>error</code>,
 * <code>all-of</code>).
 *
 * @param kind
 *        The element local name.
 * @param attributes
 *        The attributes by local name, without namespace declarations.
 * @param value
 *        The concatenated direct text and CDATA content (not trimmed).
 * @param namespaces
 *        The prefixed namespaces in scope on the element (XML scoping over
 *        the whole catalog file), used to evaluate its expression.
 * @param children
 *        Nested assertions (for all-of, any-of, not, assert-message,
 *        assert-result-document).
 * @author Philip Helger
 */
public record XSLT30Assertion (@NonNull String kind,
                               @NonNull ICommonsOrderedMap <String, String> attributes,
                               @NonNull String value,
                               @NonNull ICommonsOrderedMap <String, String> namespaces,
                               @NonNull ICommonsList <XSLT30Assertion> children)
{
  /**
   * @param sName
   *        Attribute local name.
   * @return The value or the empty string if absent.
   */
  @NonNull
  public String getAttribute (@NonNull final String sName)
  {
    final String ret = attributes.get (sName);
    return ret == null ? "" : ret;
  }
}
