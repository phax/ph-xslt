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
package com.helger.xpath.context;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.helger.xpath.XPathCompiler;
import com.helger.xpath.value.XdmAtomic;

/**
 * Test class for the default language of class {@link DynamicContext}.
 *
 * @author Philip Helger
 */
public final class DynamicContextTest
{
  private static String _eval (final String sExpr, final String sDefaultLanguage)
  {
    final StaticContext aStatic = new StaticContext ();
    final DynamicContext aCtx = new DynamicContext (aStatic);
    if (sDefaultLanguage != null)
      aCtx.setDefaultLanguage (sDefaultLanguage);
    return ((XdmAtomic) XPathCompiler.compile (sExpr, aStatic).evaluate (aCtx).getFirst ()).getLexical ();
  }

  @Test
  public void testDefaultLanguage ()
  {
    assertEquals (DynamicContext.DEFAULT_LANGUAGE, new DynamicContext (new StaticContext ()).getDefaultLanguage ());
    assertEquals ("en", _eval ("default-language()", null));
    assertEquals ("fr-CA", _eval ("default-language()", "fr-CA"));
    assertEquals ("true", _eval ("default-language() instance of xs:language", "fr-CA"));
  }

  @Test
  public void testFormattingUsesDefaultLanguage ()
  {
    assertEquals ("three", _eval ("format-integer(3, 'w')", null));
    assertEquals ("drei", _eval ("format-integer(3, 'w')", "de"));
    // An explicit language wins
    assertEquals ("three", _eval ("format-integer(3, 'w', 'en')", "de"));
    assertEquals ("drei", _eval ("format-integer(3, 'w', ())", "de"));
    assertEquals ("Montag", _eval ("format-date(xs:date('2024-01-01'), '[FNn]')", "de"));
    assertEquals ("Monday", _eval ("format-date(xs:date('2024-01-01'), '[FNn]')", null));
  }
}
