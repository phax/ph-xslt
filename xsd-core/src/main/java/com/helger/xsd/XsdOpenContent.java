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

/**
 * An XSD 1.1 {open content} property: extra children the content model admits,
 * anywhere (interleave) or after the model (suffix). Mode <code>none</code>
 * records an explicit opt-out.
 *
 * @author Philip Helger
 */
final class XsdOpenContent
{
  static final String MODE_INTERLEAVE = "interleave";
  static final String MODE_SUFFIX = "suffix";
  static final String MODE_NONE = "none";

  String m_sMode;
  XsdWildcard m_aWildcard;

  XsdOpenContent (@NonNull final String sMode, @Nullable final XsdWildcard aWildcard)
  {
    m_sMode = sMode;
    m_aWildcard = aWildcard;
  }
}
