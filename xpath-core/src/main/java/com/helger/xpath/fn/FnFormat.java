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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmDateTimeValue;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:format-integer, format-number, format-date,
 * format-dateTime, format-time. The algorithms live in {@link FormatInteger},
 * {@link FormatNumber} and {@link FormatDateTime}; named decimal formats are
 * looked up in the static context
 * ({@link StaticContext#getDecimalFormat(XdmQName)}).
 *
 * @author Philip Helger
 */
@Immutable
public final class FnFormat
{
  @PresentForCodeCoverage
  private static final FnFormat INSTANCE = new FnFormat ();

  private FnFormat ()
  {}

  @Nullable
  private static XdmAtomic _arg (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    if (nIndex >= aArgs.size ())
      return null;
    final XdmSequence aSeq = aArgs.get (nIndex);
    return aSeq.isEmpty () ? null : (XdmAtomic) aSeq.getFirst ();
  }

  @Nullable
  private static String _stringArg (@NonNull final List <XdmSequence> aArgs, final int nIndex)
  {
    final XdmAtomic aArg = _arg (aArgs, nIndex);
    return aArg == null ? null : aArg.getString ();
  }

  @NonNull
  private static XdmSequence _string (@NonNull final String s)
  {
    return XdmSequence.of (XdmAtomic.string (s));
  }

  @NonNull
  private static XdmSequence _formatInteger (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs)
  {
    final XdmAtomic aValue = _arg (aArgs, 0);
    final String sPicture = _stringArg (aArgs, 1);
    if (aValue == null)
      return _string ("");
    final String sLanguage = _stringArg (aArgs, 2);
    return _string (FormatInteger.format (aValue.getBigInteger (),
                                          sPicture == null ? "" : sPicture,
                                          sLanguage != null ? sLanguage : aCtx.getDefaultLanguage ()));
  }

  /**
   * Resolve a decimal format name (EQName, lexical QName or NCName) against
   * the static context.
   *
   * @param aStatic
   *        The static context.
   * @param sName
   *        The name as given to format-number.
   * @return The decimal format.
   * @throws XPathException
   *         FODF1280 if the name is invalid or no such format exists.
   */
  @NonNull
  public static FormatDecimalFormat resolveDecimalFormat (@NonNull final StaticContext aStatic,
                                                          @NonNull final String sName)
  {
    final String s = XdmDateTimeValue.trimXmlWhitespace (sName);
    final XdmQName aName;
    if (s.startsWith ("Q{"))
    {
      final int nEnd = s.indexOf ('}');
      if (nEnd < 0 || !FormatDateTime.isNCName (s.substring (nEnd + 1)))
        throw new XPathException ("FODF1280", "Invalid decimal format name '" + sName + "'");
      aName = new XdmQName (s.substring (2, nEnd).strip (), s.substring (nEnd + 1));
    }
    else
    {
      final int nColon = s.indexOf (':');
      if (nColon >= 0)
      {
        final String sPrefix = s.substring (0, nColon);
        final String sLocal = s.substring (nColon + 1);
        if (!FormatDateTime.isNCName (sPrefix) || !FormatDateTime.isNCName (sLocal))
          throw new XPathException ("FODF1280", "Invalid decimal format name '" + sName + "'");
        final String sUri = aStatic.getNamespaceUri (sPrefix);
        if (sUri == null)
          throw new XPathException ("FODF1280", "Unbound prefix in decimal format name '" + sName + "'");
        aName = new XdmQName (sUri, sLocal, sPrefix);
      }
      else
      {
        if (!FormatDateTime.isNCName (s))
          throw new XPathException ("FODF1280", "Invalid decimal format name '" + sName + "'");
        aName = new XdmQName (s);
      }
    }
    final FormatDecimalFormat ret = aStatic.getDecimalFormat (aName);
    if (ret == null)
      throw new XPathException ("FODF1280", "No decimal format named '" + sName + "'");
    return ret;
  }

  @NonNull
  private static XdmSequence _formatNumber (@NonNull final DynamicContext aCtx,
                                            @NonNull final List <XdmSequence> aArgs)
  {
    final StaticContext aStatic = aCtx.getStaticContext ();
    final String sFormatName = _stringArg (aArgs, 2);
    final FormatDecimalFormat aDF = sFormatName == null ? aStatic.getDefaultDecimalFormat ()
                                                        : resolveDecimalFormat (aStatic, sFormatName);
    final String sPicture = _stringArg (aArgs, 1);
    return _string (FormatNumber.format (_arg (aArgs, 0), sPicture == null ? "" : sPicture, aDF));
  }

  @NonNull
  private static IXPathFunction _formatDateTime (@NonNull final EAtomicType eType)
  {
    return (aCtx, aArgs) -> {
      final XdmAtomic aValue = _arg (aArgs, 0);
      if (aValue == null)
        return XdmSequence.EMPTY;
      final String sPicture = _stringArg (aArgs, 1);
      final String sLanguage = _stringArg (aArgs, 2);
      return _string (FormatDateTime.format ((XdmDateTimeValue) aValue.getValue (),
                                             eType,
                                             sPicture == null ? "" : sPicture,
                                             sLanguage != null ? sLanguage : aCtx.getDefaultLanguage (),
                                             _stringArg (aArgs, 3),
                                             _stringArg (aArgs, 4)));
    };
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "format-integer", "(xs:integer?, xs:string) as xs:string", FnFormat::_formatInteger);
    aLib.register (sNS,
                   "format-integer",
                   "(xs:integer?, xs:string, xs:string?) as xs:string",
                   FnFormat::_formatInteger);
    aLib.register (sNS, "format-number", "(xs:numeric?, xs:string) as xs:string", FnFormat::_formatNumber);
    aLib.register (sNS,
                   "format-number",
                   "(xs:numeric?, xs:string, xs:string?) as xs:string",
                   FnFormat::_formatNumber);
    aLib.register (sNS,
                   "format-dateTime",
                   "(xs:dateTime?, xs:string) as xs:string?",
                   _formatDateTime (EAtomicType.DATE_TIME));
    aLib.register (sNS,
                   "format-dateTime",
                   "(xs:dateTime?, xs:string, xs:string?, xs:string?, xs:string?) as xs:string?",
                   _formatDateTime (EAtomicType.DATE_TIME));
    aLib.register (sNS, "format-date", "(xs:date?, xs:string) as xs:string?", _formatDateTime (EAtomicType.DATE));
    aLib.register (sNS,
                   "format-date",
                   "(xs:date?, xs:string, xs:string?, xs:string?, xs:string?) as xs:string?",
                   _formatDateTime (EAtomicType.DATE));
    aLib.register (sNS, "format-time", "(xs:time?, xs:string) as xs:string?", _formatDateTime (EAtomicType.TIME));
    aLib.register (sNS,
                   "format-time",
                   "(xs:time?, xs:string, xs:string?, xs:string?, xs:string?) as xs:string?",
                   _formatDateTime (EAtomicType.TIME));
  }
}
