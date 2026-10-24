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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.XPathException;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:error and fn:trace. The output of <code>fn:trace</code>
 * goes to a pluggable {@link ITraceListener}; by default it is logged at debug
 * level.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class FnError
{
  /** Receives the values passed to <code>fn:trace</code>. */
  @FunctionalInterface
  public interface ITraceListener
  {
    /**
     * @param aValue
     *        The traced value.
     * @param sLabel
     *        The label, <code>null</code> for the single-argument form.
     */
    void onTrace (@NonNull XdmSequence aValue, @Nullable String sLabel);
  }

  /** The default code of <code>fn:error</code>. */
  public static final XdmQName FOER0000 = new XdmQName (XPathException.ERR_NAMESPACE, "FOER0000", "err");

  private static final Logger LOGGER = LoggerFactory.getLogger (FnError.class);

  /** The default trace listener: logs at debug level. */
  public static final ITraceListener DEFAULT_TRACE_LISTENER = (aValue, sLabel) -> {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ((sLabel == null ? "" : "[" + sLabel + "] ") + getTraceString (aValue));
  };

  private static volatile ITraceListener s_aTraceListener = DEFAULT_TRACE_LISTENER;

  @PresentForCodeCoverage
  private static final FnError INSTANCE = new FnError ();

  private FnError ()
  {}

  @NonNull
  private static String _getItemString (@NonNull final IXdmItem aItem)
  {
    try
    {
      return Atomizer.getStringValue (aItem);
    }
    catch (final XPathException ex)
    {
      return aItem.toString ();
    }
  }

  /**
   * @param aValue
   *        A traced value.
   * @return A readable form: the string values of the items, separated by
   *         blanks.
   */
  @NonNull
  public static String getTraceString (@NonNull final XdmSequence aValue)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aValue)
    {
      if (aSB.length () > 0)
        aSB.append (' ');
      aSB.append (_getItemString (aItem));
    }
    return aSB.toString ();
  }

  /** @return The current trace listener. */
  @NonNull
  public static ITraceListener getTraceListener ()
  {
    return s_aTraceListener;
  }

  /**
   * @param aListener
   *        The new trace listener; {@link #DEFAULT_TRACE_LISTENER} restores
   *        the default.
   */
  public static void setTraceListener (@NonNull final ITraceListener aListener)
  {
    ValueEnforcer.notNull (aListener, "Listener");
    s_aTraceListener = aListener;
  }

  @NonNull
  private static XdmSequence _error (@NonNull final List <XdmSequence> aArgs)
  {
    XdmQName aCode = FOER0000;
    if (!aArgs.isEmpty () && !aArgs.get (0).isEmpty ())
      aCode = (XdmQName) ((XdmAtomic) aArgs.get (0).get (0)).getValue ();
    final String sDescription = aArgs.size () > 1 ? ((XdmAtomic) aArgs.get (1).get (0)).getString () : null;
    final XdmSequence aErrorObject = aArgs.size () > 2 ? aArgs.get (2) : null;
    throw new XPathException (aCode, sDescription, aErrorObject);
  }

  @NonNull
  private static XdmSequence _trace (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aValue = aArgs.get (0);
    final String sLabel = aArgs.size () > 1 ? ((XdmAtomic) aArgs.get (1).get (0)).getString () : null;
    s_aTraceListener.onTrace (aValue, sLabel);
    return aValue;
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    // The declared return type of fn:error is "none", which is not a
    // SequenceType; item()* is the closest expressible type
    aLib.register (sNS, "error", "() as item()*", (aCtx, aArgs) -> _error (aArgs));
    aLib.register (sNS, "error", "(xs:QName?) as item()*", (aCtx, aArgs) -> _error (aArgs));
    aLib.register (sNS, "error", "(xs:QName?, xs:string) as item()*", (aCtx, aArgs) -> _error (aArgs));
    aLib.register (sNS, "error", "(xs:QName?, xs:string, item()*) as item()*", (aCtx, aArgs) -> _error (aArgs));
    aLib.register (sNS, "trace", "(item()*) as item()*", (aCtx, aArgs) -> _trace (aArgs));
    aLib.register (sNS, "trace", "(item()*, xs:string) as item()*", (aCtx, aArgs) -> _trace (aArgs));
  }
}
