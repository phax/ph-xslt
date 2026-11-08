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
package com.helger.phoxtrot.xpath.fn;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ops.XsdLexical;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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

  /**
   * @param aSeq
   *        An <code>xs:QName?</code> argument.
   * @return The QName, or <code>null</code> for the empty sequence.
   */
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

  /**
   * <code>fn:QName($paramURI as xs:string?, $paramQName as xs:string) as
   * xs:QName</code> (F&amp;O 3.1 section 10.1.2): build a QName from a
   * namespace URI (empty sequence or empty string for no namespace) and a
   * lexical QName, keeping the prefix.
   *
   * @param aArgs
   *        The URI and the lexical QName.
   * @return The QName.
   * @throws XPathException
   *         FOCA0002 if the second argument is not a lexical QName, or if it
   *         has a prefix but the URI is empty.
   */
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

  /**
   * <code>fn:resolve-QName($qname as xs:string?, $element as element()) as
   * xs:QName?</code> (F&amp;O 3.1 section 10.1.1): resolve the prefix of a
   * lexical QName against the in-scope namespaces of the element. An
   * unprefixed name takes the default namespace of the element, or no
   * namespace if it has none.
   *
   * @param aArgs
   *        The lexical QName and the element.
   * @return The empty sequence if the first argument is empty, the resolved
   *         QName otherwise.
   * @throws XPathException
   *         FOCA0002 if the string is not a lexical QName, FONS0004 if its
   *         prefix is not bound on the element.
   */
  @NonNull
  private static XdmSequence _resolveQName (@NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final String sLexical = ((XdmAtomic) aArgs.get (0).get (0)).getString ();
    final IXdmNode aElement = (IXdmNode) aArgs.get (1).get (0);
    final String [] aParts = _splitLexical (sLexical);
    if (aParts == null)
      throw XPathException.of ("FOCA0002", "'" + sLexical + "' is not a valid lexical QName");
    final String sUri = aElement.lookupNamespaceUri (aParts[0]);
    if (sUri == null && !aParts[0].isEmpty ())
      throw XPathException.of ("FONS0004", "No namespace is bound to the prefix '" + aParts[0] + "'");
    return XdmSequence.of (XdmAtomic.qname (new XdmQName (sUri == null ? "" : sUri, aParts[1], aParts[0])));
  }

  /**
   * Register the QName functions of F&amp;O 3.1 section 10:
   * <code>fn:resolve-QName</code>, <code>fn:QName</code>, and the accessors
   * <code>fn:prefix-from-QName</code> (empty for an empty argument or a QName
   * without prefix), <code>fn:local-name-from-QName</code> (both as xs:NCName)
   * and <code>fn:namespace-uri-from-QName</code> (as xs:anyURI, the empty
   * string for no namespace). The accessors return the empty sequence for an
   * empty argument.
   *
   * @param aLib
   *        The library to register into.
   */
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
