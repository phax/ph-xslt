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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.ops.AtomicCaster;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;

/**
 * Function family: the constructor functions xs:TYPE($arg) for every built-in
 * atomic type (F&amp;O 3.1 section 18), including the union type xs:numeric
 * and xs:error, and the built-in list types xs:NMTOKENS, xs:IDREFS and
 * xs:ENTITIES. There are no constructor functions for the abstract types
 * xs:anyAtomicType and xs:NOTATION.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnConstructor
{
  @PresentForCodeCoverage
  private static final FnConstructor INSTANCE = new FnConstructor ();

  private FnConstructor ()
  {}

  @NonNull
  private static XdmSequence _construct (@NonNull final DynamicContext aCtx,
                                         @NonNull final List <XdmSequence> aArgs,
                                         @NonNull final EAtomicType eTarget)
  {
    final XdmSequence aArg = aArgs.get (0);
    if (aArg.isEmpty ())
      return XdmSequence.EMPTY;
    return XdmSequence.of (AtomicCaster.cast ((XdmAtomic) aArg.get (0), eTarget, aCtx.getStaticContext ()));
  }

  private static boolean _isXmlWhitespace (final char c)
  {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  @NonNull
  private static XdmSequence _constructList (@NonNull final DynamicContext aCtx,
                                             @NonNull final List <XdmSequence> aArgs,
                                             @NonNull final EAtomicType eItemType)
  {
    final XdmSequence aArg = aArgs.get (0);
    if (aArg.isEmpty ())
      return XdmSequence.EMPTY;
    final XdmAtomic aValue = (XdmAtomic) aArg.get (0);
    if (!aValue.isStringLike () || aValue.getType () == EAtomicType.ANY_URI)
      throw XPathException.type ("Cannot cast " +
                                 aValue.getType ().getQualifiedName () +
                                 " to the list type of " +
                                 eItemType.getQualifiedName ());
    final String s = aValue.getString ();
    final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
    int nStart = -1;
    for (int i = 0; i <= s.length (); ++i)
    {
      final boolean bWS = i == s.length () || _isXmlWhitespace (s.charAt (i));
      if (bWS)
      {
        if (nStart >= 0)
        {
          aBuilder.add (AtomicCaster.castFromString (s.substring (nStart, i), eItemType, aCtx.getStaticContext ()));
          nStart = -1;
        }
      }
      else
        if (nStart < 0)
          nStart = i;
    }
    if (aBuilder.size () == 0)
      throw XPathException.invalidValue ("A value of a list type must have at least one item");
    return aBuilder.build ();
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_XS;
    for (final EAtomicType eType : EAtomicType.values ())
    {
      if (eType == EAtomicType.ANY_ATOMIC_TYPE || eType == EAtomicType.NOTATION)
        continue;
      final String sType = eType.getQualifiedName ();
      aLib.register (sNS,
                     eType.getLocalName (),
                     "(xs:anyAtomicType?) as " + sType + "?",
                     (aCtx, aArgs) -> _construct (aCtx, aArgs, eType));
    }
    aLib.register (sNS,
                   "NMTOKENS",
                   "(xs:anyAtomicType?) as xs:NMTOKEN*",
                   (aCtx, aArgs) -> _constructList (aCtx, aArgs, EAtomicType.NMTOKEN));
    aLib.register (sNS,
                   "IDREFS",
                   "(xs:anyAtomicType?) as xs:IDREF*",
                   (aCtx, aArgs) -> _constructList (aCtx, aArgs, EAtomicType.IDREF));
    aLib.register (sNS,
                   "ENTITIES",
                   "(xs:anyAtomicType?) as xs:ENTITY*",
                   (aCtx, aArgs) -> _constructList (aCtx, aArgs, EAtomicType.ENTITY));
  }
}
