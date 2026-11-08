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

import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.CompiledXPath;

/**
 * An XSD 1.1 <code>xs:alternative</code> (conditional type assignment). An empty
 * {@link #m_sRawTest} is the default alternative.
 *
 * @author Philip Helger
 */
final class XsdTypeAlternative
{
  /** The <code>@test</code> source text, empty for the default alternative. */
  String m_sRawTest = "";
  IXsdType m_aType;
  ICommonsOrderedMap <String, String> m_aNS = new CommonsLinkedHashMap <> ();
  String m_sDefaultNS = "";
  /** The compiled test; <code>null</code> only for the default alternative. */
  CompiledXPath m_aXPath;
}
