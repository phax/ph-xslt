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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.xpath.CompiledXPath;
import com.helger.xpath.XPathCompiler;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;

/**
 * Evaluation helpers for the tests of the node, QName, URI and resource
 * functions.
 *
 * @author Philip Helger
 */
@Immutable
final class FnNodeTestHelper
{
  private FnNodeTestHelper ()
  {}

  @NonNull
  static IXdmNode parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new IllegalArgumentException (ex);
    }
  }

  @NonNull
  static XdmSequence eval (@NonNull final String sExpr,
                           @Nullable final String sBaseUri,
                           @Nullable final IXdmItem aContextItem,
                           @Nullable final Consumer <DynamicContext> aCustomizer)
  {
    final StaticContext aStatic = new StaticContext ();
    aStatic.setBaseUri (sBaseUri);
    final CompiledXPath aCompiled = XPathCompiler.compile (sExpr, aStatic);
    DynamicContext aCtx = aCompiled.createDynamicContext ();
    if (aCustomizer != null)
      aCustomizer.accept (aCtx);
    if (aContextItem != null)
      aCtx = aCtx.withFocus (aContextItem, 1, 1);
    return aCompiled.evaluate (aCtx);
  }

  @NonNull
  static XdmSequence eval (@NonNull final String sExpr, @Nullable final IXdmItem aContextItem)
  {
    return eval (sExpr, null, aContextItem, null);
  }

  @NonNull
  static XdmSequence eval (@NonNull final String sExpr)
  {
    return eval (sExpr, null, null, null);
  }

  /**
   * @return The string values of all items, separated by a space.
   */
  @NonNull
  static String str (@NonNull final XdmSequence aSeq)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final IXdmItem aItem : aSeq)
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (FnAccessor.getStringValue (aItem));
    }
    return aSB.toString ();
  }

  @NonNull
  static String str (@NonNull final String sExpr, @Nullable final IXdmItem aContextItem)
  {
    return str (eval (sExpr, aContextItem));
  }

  @NonNull
  static String str (@NonNull final String sExpr)
  {
    return str (eval (sExpr));
  }

  static void assertError (@NonNull final String sCode, @NonNull final Runnable aRunnable)
  {
    try
    {
      aRunnable.run ();
      fail ("Expected error " + sCode);
    }
    catch (final XPathException ex)
    {
      assertEquals (ex.getMessage (), sCode, ex.getCode ());
    }
  }

  static void assertError (@NonNull final String sCode, @NonNull final String sExpr, @Nullable final IXdmItem aItem)
  {
    assertError (sCode, () -> eval (sExpr, aItem));
  }

  static void assertError (@NonNull final String sCode, @NonNull final String sExpr)
  {
    assertError (sCode, () -> eval (sExpr));
  }
}
