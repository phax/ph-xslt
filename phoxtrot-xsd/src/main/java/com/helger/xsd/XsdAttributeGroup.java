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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * A compiled attribute group definition (its flattened uses and wildcard) -
 * go-xslt <code>attrGroupDef</code>.
 *
 * @author Philip Helger
 */
final class XsdAttributeGroup
{
  ICommonsList <XsdAttributeUse> m_aUses = new CommonsArrayList <> ();
  XsdWildcard m_aWildcard;

  XsdAttributeGroup ()
  {}

  XsdAttributeGroup (@NonNull final ICommonsList <XsdAttributeUse> aUses, @Nullable final XsdWildcard aWildcard)
  {
    m_aUses = aUses;
    m_aWildcard = aWildcard;
  }

  /** Copy every field of another group into this one. */
  void assignFrom (@NonNull final XsdAttributeGroup aOther)
  {
    m_aUses = aOther.m_aUses;
    m_aWildcard = aOther.m_aWildcard;
  }

  /** @return A shallow copy. */
  @NonNull
  XsdAttributeGroup getShallowCopy ()
  {
    return new XsdAttributeGroup (m_aUses, m_aWildcard);
  }
}
