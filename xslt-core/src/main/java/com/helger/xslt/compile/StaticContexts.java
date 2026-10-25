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
package com.helger.xslt.compile;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.collation.CollationRegistry;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.fn.FormatDecimalFormat;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * Builds the XPath static context of an expression written on a stylesheet
 * element (XSLT 3.0 section 5.4.1).
 *
 * @author Philip Helger
 */
@Immutable
public final class StaticContexts
{
  @PresentForCodeCoverage
  private static final StaticContexts INSTANCE = new StaticContexts ();

  private StaticContexts ()
  {}

  /**
   * @param aEl
   *        The element.
   * @return The effective default collation URI of the element.
   * @throws XsltException
   *         XTSE0125 if [xsl:]default-collation names no supported collation
   */
  @NonNull
  public static String getDefaultCollation (@NonNull final XslElement aEl)
  {
    final XslElement aOwner = aEl.getStandardAttrOwner ("default-collation");
    if (aOwner == null)
      return CollationRegistry.CODEPOINT_URI;
    final String sValue = aOwner.getStandardAttr ("default-collation");
    for (final String sToken : XsltNames.tokenize (sValue))
    {
      final String sUri = CollationRegistry.resolveUri (sToken, aOwner.getStaticBaseUri ());
      if (CollationRegistry.getCollation (sUri) != null)
        return sUri;
    }
    throw XsltException.at (aOwner, "XTSE0125", "default-collation='" + sValue + "' names no supported collation");
  }

  /**
   * Create the static context of an element.
   *
   * @param aEl
   *        The element.
   * @param aFunctions
   *        The function library.
   * @param aVariables
   *        The variables in scope.
   * @param aDefaultDecimalFormat
   *        The unnamed decimal format or <code>null</code> for the default.
   * @param aDecimalFormats
   *        The named decimal formats or <code>null</code>.
   * @return A new static context.
   */
  @NonNull
  public static StaticContext create (@NonNull final XslElement aEl,
                                      @NonNull final FunctionLibrary aFunctions,
                                      @NonNull final Iterable <XdmQName> aVariables,
                                      @Nullable final FormatDecimalFormat aDefaultDecimalFormat,
                                      @Nullable final Map <XdmQName, FormatDecimalFormat> aDecimalFormats)
  {
    final StaticContext ret = new StaticContext ();
    for (final Map.Entry <String, String> aEntry : aEl.getInScopeNamespaces ().entrySet ())
      if (!aEntry.getKey ().isEmpty () && !"xml".equals (aEntry.getKey ()))
        ret.declareNamespace (aEntry.getKey (), aEntry.getValue ());
    final String sXpathDefault = aEl.getInheritedStandardAttr ("xpath-default-namespace");
    if (sXpathDefault != null)
      ret.setDefaultElementNamespace (sXpathDefault.trim ());
    ret.setBaseUri (aEl.getStaticBaseUri ());
    ret.setFunctionLibrary (aFunctions);
    ret.setDefaultCollation (getDefaultCollation (aEl));
    for (final XdmQName aName : aVariables)
      ret.declareVariable (aName);
    if (aDefaultDecimalFormat != null)
      ret.setDefaultDecimalFormat (aDefaultDecimalFormat);
    if (aDecimalFormats != null)
      for (final Map.Entry <XdmQName, FormatDecimalFormat> aEntry : aDecimalFormats.entrySet ())
        ret.addDecimalFormat (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }
}
