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

/**
 * A FOTS <code>&lt;dependency type="..." value="..." satisfied="..."/&gt;</code>.
 *
 * @param type
 *        The dependency type, e.g. <code>spec</code> or <code>feature</code>.
 * @param value
 *        The value, e.g. <code>XP31+ XQ31+</code>.
 * @param satisfied
 *        The raw <code>satisfied</code> attribute; empty if absent (which
 *        means "true").
 * @author Philip Helger
 */
public record QT3Dependency (@NonNull String type, @NonNull String value, @NonNull String satisfied)
{
  /**
   * @return <code>true</code> unless the attribute <code>satisfied</code> is
   *         literally <code>false</code>.
   */
  public boolean isSatisfied ()
  {
    return !"false".equals (satisfied);
  }
}
