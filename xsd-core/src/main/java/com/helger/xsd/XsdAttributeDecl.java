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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * A compiled attribute declaration - go-xslt <code>AttributeDecl</code>.
 *
 * @author Philip Helger
 */
final class XsdAttributeDecl
{
  XsdName m_aName;
  ICommonsList <XsdDocumentation> m_aDocs = new CommonsArrayList <> ();
  ICommonsList <XsdMessage> m_aMsgs = new CommonsArrayList <> ();
  XsdSimpleType m_aType;
  /** The XSD 1.1 {inheritable} property. */
  boolean m_bInheritable;
  /** The declaration's own value constraint, which a reference may repeat but never contradict. */
  String m_sDefault = "";
  String m_sFixed = "";
  boolean m_bHasDefault;
  boolean m_bHasFixed;

  XsdAttributeDecl (@NonNull final XsdName aName)
  {
    m_aName = aName;
  }

  XsdAttributeDecl (@NonNull final XsdName aName, @NonNull final XsdSimpleType aType)
  {
    m_aName = aName;
    m_aType = aType;
  }

  @Override
  public String toString ()
  {
    return "XsdAttributeDecl[" + m_aName + "]";
  }
}
