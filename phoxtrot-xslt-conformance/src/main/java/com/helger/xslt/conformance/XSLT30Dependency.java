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

/**
 * One child of a <code>&lt;dependencies&gt;</code> element, e.g.
 * <code>&lt;spec value="XSLT30+"/&gt;</code> or
 * <code>&lt;feature value="streaming" satisfied="false"/&gt;</code>.
 *
 * @param type
 *        The element local name (<code>spec</code>, <code>feature</code>,
 *        <code>year_component_values</code>, ...).
 * @param value
 *        The <code>value</code> attribute, empty if absent.
 * @param satisfied
 *        The <code>satisfied</code> attribute, empty if absent (which means
 *        "true").
 * @author Philip Helger
 */
public record XSLT30Dependency (@NonNull String type, @NonNull String value, @NonNull String satisfied)
{
  /** @return <code>true</code> if the dependency is declared satisfied="false". */
  public boolean isNotSatisfied ()
  {
    return "false".equals (satisfied);
  }
}
