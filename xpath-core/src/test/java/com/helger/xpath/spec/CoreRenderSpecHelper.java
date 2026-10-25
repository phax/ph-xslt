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
package com.helger.xpath.spec;

import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.spec.SpecTestHelper.Env;
import com.helger.xpath.spec.SpecTestHelper.SpecCase;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt's <code>coreRun</code>/<code>coreRender</code>: a result is
 * rendered as the string values of all its items joined by "|", the empty
 * sequence as "()".
 *
 * @author Philip Helger
 */
final class CoreRenderSpecHelper
{
  @PresentForCodeCoverage
  private static final CoreRenderSpecHelper INSTANCE = new CoreRenderSpecHelper ();

  private CoreRenderSpecHelper ()
  {}

  @NonNull
  static String render (@NonNull final XdmSequence aSeq)
  {
    if (aSeq.isEmpty ())
      return "()";
    final StringBuilder aSB = new StringBuilder ();
    for (int i = 0; i < aSeq.size (); ++i)
    {
      if (i > 0)
        aSB.append ('|');
      final IXdmItem aItem = aSeq.get (i);
      if (aItem instanceof final XdmAtomic aAtomic)
        aSB.append (aAtomic.getLexical ());
      else
        if (aItem instanceof final XdmNode aNode)
          aSB.append (aNode.getStringValue ());
    }
    return aSB.toString ();
  }

  @Nullable
  private static String _check (@NonNull final Env aEnv, @NonNull final SpecCase aCase)
  {
    final XdmSequence aResult;
    try
    {
      aResult = SpecTestHelper.eval (aCase.expr (), aEnv);
    }
    catch (final XPathException ex)
    {
      if (aCase.code () == null)
        return aCase.expr () + ": unexpected error " + ex.getCode () + ": " + ex.getMessage ();
      if (!aCase.code ().equals (ex.getCode ()))
        return aCase.expr () + ": error " + ex.getCode () + " (" + ex.getMessage () + "), want " + aCase.code ();
      return null;
    }
    catch (final RuntimeException ex)
    {
      return aCase.expr () + ": unexpected exception " + ex;
    }
    final String sGot = render (aResult);
    if (aCase.code () != null)
      return aCase.expr () + ": got '" + sGot + "', want error " + aCase.code ();
    if (!sGot.equals (aCase.want ()))
      return aCase.expr () + " = '" + sGot + "', want '" + aCase.want () + "'";
    return null;
  }

  /**
   * Run a table of cases with the "|"-joined rendering and fail with all
   * mismatches.
   *
   * @param aEnv
   *        Environment.
   * @param aCases
   *        Cases.
   */
  static void run (@NonNull final Env aEnv, @NonNull final SpecCase... aCases)
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final SpecCase aCase : aCases)
    {
      final String sError = _check (aEnv, aCase);
      if (sError != null)
        aErrors.add (sError);
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + aCases.length + " cases failed:\n  " + String.join ("\n  ", aErrors));
  }
}
