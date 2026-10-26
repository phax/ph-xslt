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

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

/**
 * An attribute use of a complex type or attribute group - go-xslt
 * <code>attrUse</code>.
 *
 * @author Philip Helger
 */
final class XsdAttributeUse
{
  /** The annotation of the xs:attribute itself (with documentation only). */
  ICommonsList <XsdDocumentation> m_aDocs = new CommonsArrayList <> ();
  ICommonsList <XsdMessage> m_aMsgs = new CommonsArrayList <> ();
  XsdAttributeDecl m_aDecl;
  boolean m_bRequired;
  boolean m_bProhibited;
  String m_sDefault = "";
  String m_sFixed = "";
  boolean m_bHasDefault;
  boolean m_bHasFixed;
  /** The use-level {inheritable}, which overrides the referenced declaration's. */
  boolean m_bInheritable;

  @Override
  public String toString ()
  {
    return "XsdAttributeUse[" + (m_aDecl == null ? "?" : m_aDecl.m_aName.toString ()) + "]";
  }
}
