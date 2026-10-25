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

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: fn:transform. It is registered only when an
 * {@link ITransformHandler} is found on the class path (see there), so a plain
 * XPath processor without an XSLT engine does not offer it.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnTransform
{
  @PresentForCodeCoverage
  private static final FnTransform INSTANCE = new FnTransform ();

  private static volatile boolean s_bLookedUp;
  private static volatile ITransformHandler s_aHandler;

  private FnTransform ()
  {}

  @Nullable
  private static ITransformHandler _load (@Nullable final ClassLoader aLoader)
  {
    try
    {
      final Iterator <ITransformHandler> it = ServiceLoader.load (ITransformHandler.class, aLoader).iterator ();
      return it.hasNext () ? it.next () : null;
    }
    catch (final ServiceConfigurationError ex)
    {
      return null;
    }
  }

  /**
   * @return The XSLT processor behind <code>fn:transform</code>, or
   *         <code>null</code> if none is on the class path. The lookup is done
   *         once.
   */
  @Nullable
  public static ITransformHandler getHandler ()
  {
    if (!s_bLookedUp)
      synchronized (FnTransform.class)
      {
        if (!s_bLookedUp)
        {
          ITransformHandler aHandler = _load (Thread.currentThread ().getContextClassLoader ());
          if (aHandler == null)
            aHandler = _load (ITransformHandler.class.getClassLoader ());
          s_aHandler = aHandler;
          s_bLookedUp = true;
        }
      }
    return s_aHandler;
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final ITransformHandler aHandler = getHandler ();
    if (aHandler == null)
      return;
    aLib.register (XdmConstants.NS_FN,
                   "transform",
                   "(map(*)) as map(*)",
                   (aCtx, aArgs) -> XdmSequence.of (aHandler.transform (aCtx, (XdmMap) aArgs.get (0).getFirst ())));
  }
}
