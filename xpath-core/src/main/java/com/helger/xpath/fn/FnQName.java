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
import com.helger.xpath.ops.XsdLexical;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:resolve-QName, QName, prefix-from-QName,
 * local-name-from-QName, namespace-uri-from-QName.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnQName
{
  @PresentForCodeCoverage
  private static final FnQName INSTANCE = new FnQName ();

  private FnQName ()
  {}

  @Nullable
  private static XdmQName _getOptionalQName (@NonNull final XdmSequence aSeq)
  {
    return aSeq.isEmpty () ? null : (XdmQName) ((XdmAtomic) aSeq.get (0)).getValue ();
  }

  @NonNull
  private static XdmSequence _ncName (@NonNull final String s)
  {
    return XdmSequence.of (XdmAtomic.of (EAtomicType.NCNAME, s));
  }

  /**
   * @return [prefix, local] or <code>null</code> if not a lexical QName.
   */
  private static String @Nullable [] _splitLexical (@NonNull final String s)
  {
    final int nColon = s.indexOf (':');
    final String sPrefix = nColon < 0 ? "" : s.substring (0, nColon);
    final String sLocal = nColon < 0 ? s : s.substring (nColon + 1);
    if (!XsdLexical.isNCName (sLocal) || (nColon >= 0 && !XsdLexical.isNCName (sPrefix)))
      return null;
    return new String [] { sPrefix, sLocal };
  }

  @NonNull
  private static XdmSequence _qname (@NonNull final List <XdmSequence> aArgs)
  {
    final XdmSequence aUriArg = aArgs.get (0);
    final String sUri = aUriArg.isEmpty () ? "" : ((XdmAtomic) aUriArg.get (0)).getString ();
    final String sLexical = ((XdmAtomic) aArgs.get (1).get (0)).getString ();
    final String [] aParts = _splitLexical (sLexical);
    if (aParts == null)
      throw XPathException.of ("FOCA0002", "'" + sLexical + "' is not a valid lexical QName");
    if (!aParts[0].isEmpty () && sUri.isEmpty ())
      throw XPathException.of ("FOCA0002", "The prefix '" + aParts[0] + "' needs a namespace URI");
    return XdmSequence.of (XdmAtomic.qname (new XdmQName (sUri, aParts[1], aParts[0])));
  }

  @NonNull
  private static XdmSequence _resolveQName (@NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final String sLexical = ((XdmAtomic) aArgs.get (0).get (0)).getString ();
    final XdmNode aElement = (XdmNode) aArgs.get (1).get (0);
    final String [] aParts = _splitLexical (sLexical);
    if (aParts == null)
      throw XPathException.of ("FOCA0002", "'" + sLexical + "' is not a valid lexical QName");
    final String sUri = aElement.lookupNamespaceUri (aParts[0]);
    if (sUri == null && !aParts[0].isEmpty ())
      throw XPathException.of ("FONS0004", "No namespace is bound to the prefix '" + aParts[0] + "'");
    return XdmSequence.of (XdmAtomic.qname (new XdmQName (sUri == null ? "" : sUri, aParts[1], aParts[0])));
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS,
                   "resolve-QName",
                   "(xs:string?, element()) as xs:QName?",
                   (aCtx, aArgs) -> _resolveQName (aArgs));
    aLib.register (sNS, "QName", "(xs:string?, xs:string) as xs:QName", (aCtx, aArgs) -> _qname (aArgs));
    aLib.register (sNS, "prefix-from-QName", "(xs:QName?) as xs:NCName?", (aCtx, aArgs) -> {
      final XdmQName aName = _getOptionalQName (aArgs.get (0));
      return aName == null || !aName.hasPrefix () ? XdmSequence.EMPTY : _ncName (aName.prefix ());
    });
    aLib.register (sNS, "local-name-from-QName", "(xs:QName?) as xs:NCName?", (aCtx, aArgs) -> {
      final XdmQName aName = _getOptionalQName (aArgs.get (0));
      return aName == null ? XdmSequence.EMPTY : _ncName (aName.localName ());
    });
    aLib.register (sNS, "namespace-uri-from-QName", "(xs:QName?) as xs:anyURI?", (aCtx, aArgs) -> {
      final XdmQName aName = _getOptionalQName (aArgs.get (0));
      return aName == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (aName.namespaceUri ()));
    });
  }
}
