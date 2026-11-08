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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Function family: fn:in-scope-prefixes, fn:namespace-uri-for-prefix.
 * <code>fn:doc-available</code> is registered by {@link FnResource}.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnNode
{
  @PresentForCodeCoverage
  private static final FnNode INSTANCE = new FnNode ();

  private FnNode ()
  {}

  /**
   * Register <code>fn:in-scope-prefixes($element as element()) as
   * xs:string*</code> (F&amp;O 3.1 section 10.2.6), which returns the prefixes
   * of the key set of {@link IXdmNode#getInScopeNamespaces()} of the element
   * as strings, and <code>fn:namespace-uri-for-prefix($prefix as xs:string?,
   * $element as element()) as xs:anyURI?</code> (section 10.2.5), which treats
   * an empty sequence as the empty prefix and returns the empty sequence if
   * the prefix is not bound on the element.
   *
   * @param aLib
   *        The library to register into.
   */
  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = XdmConstants.NS_FN;
    aLib.register (sNS, "in-scope-prefixes", "(element()) as xs:string*", (aCtx, aArgs) -> {
      final IXdmNode aElement = (IXdmNode) aArgs.get (0).get (0);
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final String sPrefix : aElement.getInScopeNamespaces ().keySet ())
        aBuilder.add (XdmAtomic.string (sPrefix));
      return aBuilder.build ();
    });
    aLib.register (sNS, "namespace-uri-for-prefix", "(xs:string?, element()) as xs:anyURI?", (aCtx, aArgs) -> {
      final XdmSequence aPrefixArg = aArgs.get (0);
      final String sPrefix = aPrefixArg.isEmpty () ? "" : ((XdmAtomic) aPrefixArg.get (0)).getString ();
      final IXdmNode aElement = (IXdmNode) aArgs.get (1).get (0);
      final String sUri = aElement.lookupNamespaceUri (sPrefix);
      return sUri == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.anyUri (sUri));
    });
  }
}
