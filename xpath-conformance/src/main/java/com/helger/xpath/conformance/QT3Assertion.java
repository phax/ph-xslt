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
package com.helger.xpath.conformance;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;

/**
 * One assertion element below <code>&lt;result&gt;</code> (e.g.
 * <code>assert-eq</code>, <code>error</code>, <code>all-of</code>).
 *
 * @param kind
 *        The element local name.
 * @param attributes
 *        The attributes by local name.
 * @param value
 *        The concatenated direct text and CDATA content (not trimmed).
 * @param children
 *        Nested assertions (for all-of, any-of, not).
 * @author Philip Helger
 */
public record QT3Assertion (@NonNull String kind,
                            @NonNull ICommonsOrderedMap <String, String> attributes,
                            @NonNull String value,
                            @NonNull ICommonsList <QT3Assertion> children)
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
