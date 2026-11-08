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
package com.helger.xpath.xdm;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Namespace URIs and other constants shared by the XDM classes.
 *
 * @author Philip Helger
 */
@Immutable
public final class XdmConstants
{
  /** The namespace URI of the <code>xml</code> prefix. */
  public static final String NS_XML = "http://www.w3.org/XML/1998/namespace";
  /** The namespace URI of namespace declaration attributes. */
  public static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
  /** The XML Schema namespace (<code>xs</code>). */
  public static final String NS_XS = "http://www.w3.org/2001/XMLSchema";
  /** The XML Schema instance namespace (<code>xsi</code>). */
  public static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
  /** The XPath functions namespace (<code>fn</code>). */
  public static final String NS_FN = "http://www.w3.org/2005/xpath-functions";
  /** The XSLT namespace (<code>xsl</code>). */
  public static final String NS_XSL = "http://www.w3.org/1999/XSL/Transform";

  @PresentForCodeCoverage
  private static final XdmConstants INSTANCE = new XdmConstants ();

  private XdmConstants ()
  {}
}
