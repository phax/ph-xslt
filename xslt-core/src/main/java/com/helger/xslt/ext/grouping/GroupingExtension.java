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
package com.helger.xslt.ext.grouping;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.runtime.XsltStateKey;

/**
 * The grouping family (XSLT 3.0 section 14): xsl:for-each-group,
 * fn:current-group() and fn:current-grouping-key().
 *
 * @author Philip Helger
 */
public final class GroupingExtension implements IXsltExtension
{
  /**
   * The current group. Not set (or set to <code>null</code>) means absent.
   * Cleared in stylesheet functions, global variables and patterns.
   */
  public static final XsltStateKey <XdmSequence> CURRENT_GROUP = new XsltStateKey <> ("current-group",
                                                                                     XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);

  /**
   * The current grouping key. Not set (or set to <code>null</code>) means
   * absent, e.g. within group-starting-with.
   */
  public static final XsltStateKey <XdmSequence> CURRENT_GROUPING_KEY = new XsltStateKey <> ("current-grouping-key",
                                                                                            XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);

  @NonNull
  private static XdmSequence _currentGroup (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    final XdmSequence ret = aXCtx == null ? null : aXCtx.get (CURRENT_GROUP);
    if (ret == null)
      throw new XPathException ("XTDE1061", "current-group() is called when there is no current group");
    return ret;
  }

  @NonNull
  private static XdmSequence _currentGroupingKey (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    final XdmSequence ret = aXCtx == null ? null : aXCtx.get (CURRENT_GROUPING_KEY);
    if (ret == null)
      throw new XPathException ("XTDE1071", "current-grouping-key() is called when there is no current grouping key");
    return ret;
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("for-each-group", ForEachGroupInstruction::compile);
    aRegistry.registerFunctions (aLib -> {
      aLib.register (XdmConstants.NS_FN, "current-group", "() as item()*", GroupingExtension::_currentGroup);
      aLib.register (XdmConstants.NS_FN, "current-grouping-key", "() as xs:anyAtomicType*", GroupingExtension::_currentGroupingKey);
    });
  }
}
