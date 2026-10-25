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
package com.helger.xslt.ext.merge;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.ops.Atomizer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.runtime.XsltStateKey;

/**
 * The merge family (XSLT 3.0 section 15): xsl:merge, fn:current-merge-group()
 * and fn:current-merge-key().
 *
 * @author Philip Helger
 */
public final class MergeExtension implements IXsltExtension
{
  /**
   * The current merge group; not set means absent. Cleared in template and
   * function calls.
   */
  public static final XsltStateKey <MergeGroup> CURRENT_MERGE_GROUP = new XsltStateKey <> ("current-merge-group",
                                                                                          XsltStateKey.EScope.CLEARED_IN_CALLS);

  /**
   * The current merge key; not set means absent. Cleared in template and
   * function calls.
   */
  public static final XsltStateKey <XdmSequence> CURRENT_MERGE_KEY = new XsltStateKey <> ("current-merge-key",
                                                                                         XsltStateKey.EScope.CLEARED_IN_CALLS);

  @NonNull
  private static MergeGroup _getGroup (@NonNull final DynamicContext aCtx)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    final MergeGroup ret = aXCtx == null ? null : aXCtx.get (CURRENT_MERGE_GROUP);
    if (ret == null)
      throw new XPathException ("XTDE3480", "current-merge-group() is called when there is no current merge group");
    return ret;
  }

  @NonNull
  private static XdmSequence _currentMergeGroup (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final MergeGroup aGroup = _getGroup (aCtx);
    if (aArgs.isEmpty ())
      return aGroup.items ();
    final String sName = Atomizer.getStringValue (aArgs.get (0).get (0));
    final XdmSequence ret = aGroup.bySource ().get (sName);
    if (ret == null)
      throw new XPathException ("XTDE3490", "current-merge-group('" + sName + "'): there is no merge source with this name");
    return ret;
  }

  @NonNull
  private static XdmSequence _currentMergeKey (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    final XsltContext aXCtx = XsltContext.of (aCtx);
    final XdmSequence ret = aXCtx == null ? null : aXCtx.get (CURRENT_MERGE_KEY);
    if (ret == null)
      throw new XPathException ("XTDE3510", "current-merge-key() is called when there is no current merge key");
    return ret;
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("merge", MergeInstruction::compile);
    aRegistry.registerFunctions (aLib -> {
      aLib.register (XdmConstants.NS_FN, "current-merge-group", "() as item()*", MergeExtension::_currentMergeGroup);
      aLib.register (XdmConstants.NS_FN, "current-merge-group", "(xs:string) as item()*", MergeExtension::_currentMergeGroup);
      aLib.register (XdmConstants.NS_FN, "current-merge-key", "() as xs:anyAtomicType*", MergeExtension::_currentMergeKey);
    });
  }
}
