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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Namespace URIs and other constants of the XSD validator.
 *
 * @author Philip Helger
 */
@Immutable
public final class XsdConstants
{
  /** The XML Schema namespace. */
  public static final String NS_XS = "http://www.w3.org/2001/XMLSchema";
  /** The XML Schema instance namespace. */
  public static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
  /** The XLink namespace. */
  public static final String NS_XLINK = "http://www.w3.org/1999/xlink";
  /** The XML namespace. */
  public static final String NS_XML = "http://www.w3.org/XML/1998/namespace";
  /**
   * The XSD versioning namespace: <code>vc:minVersion</code> and friends mark
   * schema elements for conditional inclusion.
   */
  public static final String NS_VC = "http://www.w3.org/2007/XMLSchema-versioning";
  /**
   * The namespace of the <code>msg:message</code> convention for readable
   * diagnostics in <code>xs:appinfo</code> (go-xslt's own, kept for
   * compatibility of schemas written for it).
   */
  public static final String NS_MESSAGES = "https://github.com/tim-riep/go-xslt/xsd/messages";
  /** The maximum occurrence of <code>maxOccurs="unbounded"</code>. */
  public static final int UNBOUNDED = -1;

  @PresentForCodeCoverage
  private static final XsdConstants INSTANCE = new XsdConstants ();

  private XsdConstants ()
  {}
}
