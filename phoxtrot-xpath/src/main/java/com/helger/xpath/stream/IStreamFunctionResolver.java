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
package com.helger.xpath.stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.xpath.xdm.XdmQName;

/**
 * Resolves a call to a stylesheet function for the streamability analysis
 * (XSLT 3.0 section 19.8.5). The XSLT layer implements it over the compiled
 * stylesheet. A name it does not know yields <code>null</code>, and the call
 * then fails closed exactly as an extension function does.
 *
 * @author Philip Helger
 */
@FunctionalInterface
public interface IStreamFunctionResolver
{
  /**
   * @param aName
   *        The expanded function name. Never in the <code>fn</code> namespace.
   * @param nArity
   *        The number of arguments of the call (placeholders included).
   * @return The call-site signature of the stylesheet function, or
   *         <code>null</code> if there is no such stylesheet function.
   */
  @Nullable
  StreamFunctionSignature getStreamFunction (@NonNull XdmQName aName, int nArity);
}
