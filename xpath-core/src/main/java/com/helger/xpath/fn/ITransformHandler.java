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
package com.helger.xpath.fn;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmMap;

/**
 * The XSLT processor behind <code>fn:transform</code> (F&amp;O 3.1 section
 * 16.3.2). The XPath engine cannot depend on an XSLT engine, so the
 * implementation is found with {@link java.util.ServiceLoader}: a module that
 * provides one lists its class in
 * <code>META-INF/services/com.helger.xpath.fn.ITransformHandler</code>. When
 * no implementation is on the class path, <code>fn:transform</code> is not
 * part of the function library at all, so <code>function-available</code>
 * and <code>function-lookup</code> report it as unavailable.
 * Implementations must be thread-safe and have a public no-argument
 * constructor.
 *
 * @author Philip Helger
 */
public interface ITransformHandler
{
  /**
   * Run the transformation the options describe.
   *
   * @param aCtx
   *        The dynamic context of the calling expression. Its static context
   *        gives the static base URI, its resource resolver the documents and
   *        texts the transformation may read, and its host object (if any) the
   *        calling transformation.
   * @param aOptions
   *        The options map, the single argument of <code>fn:transform</code>.
   * @return The result map of <code>fn:transform</code>.
   * @throws com.helger.xpath.XPathException
   *         FOXT0001 to FOXT0004, or any error raised by the stylesheet.
   */
  @NonNull
  XdmMap transform (@NonNull DynamicContext aCtx, @NonNull XdmMap aOptions);
}
