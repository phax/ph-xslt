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

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;

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
