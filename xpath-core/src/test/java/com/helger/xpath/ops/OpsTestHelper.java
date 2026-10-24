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
package com.helger.xpath.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;

/**
 * Shared helpers for the tests of this package.
 *
 * @author Philip Helger
 */
final class OpsTestHelper
{
  /** A function item that is never called. */
  static final IXdmFunction FUNCTION = new IXdmFunction ()
  {
    public XdmQName getName ()
    {
      return null;
    }

    public int getArity ()
    {
      return 0;
    }

    public List <SeqType> getParamTypes ()
    {
      return List.of ();
    }

    public SeqType getReturnType ()
    {
      throw new UnsupportedOperationException ();
    }

    public XdmSequence call (final DynamicContext aCtx, final List <XdmSequence> aArgs)
    {
      throw new UnsupportedOperationException ();
    }
  };

  private OpsTestHelper ()
  {}

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

  @NonNull
  static DynamicContext ctx ()
  {
    return new DynamicContext (new StaticContext ());
  }

  @NonNull
  static XdmAtomic i (final long n)
  {
    return XdmAtomic.integer (n);
  }

  @NonNull
  static XdmAtomic dec (@NonNull final String s)
  {
    return XdmAtomic.decimal (new BigDecimal (s));
  }

  @NonNull
  static XdmAtomic dbl (final double d)
  {
    return XdmAtomic.dbl (d);
  }

  @NonNull
  static XdmAtomic flt (final float f)
  {
    return XdmAtomic.flt (f);
  }

  @NonNull
  static XdmAtomic str (@NonNull final String s)
  {
    return XdmAtomic.string (s);
  }

  @NonNull
  static XdmAtomic untyped (@NonNull final String s)
  {
    return XdmAtomic.untyped (s);
  }
}
