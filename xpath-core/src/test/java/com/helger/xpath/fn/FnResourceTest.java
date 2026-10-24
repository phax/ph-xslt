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

import static com.helger.xpath.fn.FnNodeTestHelper.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.IResourceResolver;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * Test class for class {@link FnResource}.
 *
 * @author Philip Helger
 */
public final class FnResourceTest
{
  private static final String BASE = "http://example.org/dir/";

  private static final class TestResolver implements IResourceResolver
  {
    private final ICommonsMap <String, XdmNode> m_aDocs = new CommonsHashMap <> ();
    private final ICommonsMap <String, String> m_aTexts = new CommonsHashMap <> ();
    private int m_nDocCalls = 0;

    @Nullable
    public XdmNode resolveDocument (@NonNull final String sAbsoluteUri)
    {
      m_nDocCalls++;
      final XdmNode ret = m_aDocs.get (sAbsoluteUri);
      // A fresh tree per call: stability must come from the run cache
      return ret == null ? null : parse ("<copy/>");
    }

    @Nullable
    public String resolveText (@NonNull final String sAbsoluteUri, @Nullable final String sEncoding)
    {
      return m_aTexts.get (sAbsoluteUri);
    }

    @Nullable
    public ICommonsList <XdmNode> resolveCollection (@NonNull final String sAbsoluteUri)
    {
      if (sAbsoluteUri.isEmpty () || sAbsoluteUri.equals (BASE + "col"))
      {
        final XdmNode aDoc = parse ("<c/>");
        aDoc.setDocumentUri (BASE + "c.xml");
        return new CommonsArrayList <> (aDoc);
      }
      return null;
    }
  }

  @NonNull
  private static TestResolver _createResolver ()
  {
    final TestResolver ret = new TestResolver ();
    ret.m_aDocs.put (BASE + "a.xml", parse ("<a/>"));
    ret.m_aTexts.put (BASE + "t.txt", "line1\r\nline2\rline3\n\nline5\n");
    ret.m_aTexts.put (BASE + "bad.txt", "a\u0001b");
    return ret;
  }

  @NonNull
  private static XdmSequence _eval (@NonNull final String sExpr,
                                    @Nullable final String sBaseUri,
                                    @NonNull final IResourceResolver aResolver,
                                    @Nullable final Consumer <DynamicContext> aCustomizer)
  {
    return FnNodeTestHelper.eval (sExpr, sBaseUri, null, aCtx -> {
      aCtx.setResourceResolver (aResolver);
      if (aCustomizer != null)
        aCustomizer.accept (aCtx);
    });
  }

  @NonNull
  private static String _str (@NonNull final String sExpr)
  {
    return FnNodeTestHelper.str (_eval (sExpr, BASE, _createResolver (), null));
  }

  private static void _assertError (@NonNull final String sCode, @NonNull final String sExpr, @Nullable final String sBase)
  {
    FnNodeTestHelper.assertError (sCode, () -> _eval (sExpr, sBase, _createResolver (), null));
  }

  @Test
  public void testDoc ()
  {
    final TestResolver aResolver = _createResolver ();
    final XdmSequence aSeq = _eval ("doc('a.xml') is doc('" + BASE + "a.xml')", BASE, aResolver, null);
    assertEquals ("true", FnNodeTestHelper.str (aSeq));
    assertEquals (1, aResolver.m_nDocCalls);
    assertTrue (_eval ("doc(())", BASE, aResolver, null).isEmpty ());
    _assertError ("FODC0002", "doc('missing.xml')", BASE);
    _assertError ("FODC0005", "doc(':/')", BASE);
    _assertError ("FODC0005", "doc('%gg')", BASE);
    _assertError ("FODC0002", "doc('a.xml')", null);
    assertEquals ("true false false false", _str ("doc-available('a.xml'), doc-available('x.xml'), doc-available(':/'), doc-available(())"));
  }

  @Test
  public void testUnparsedText ()
  {
    assertEquals ("line1\r\nline2\rline3\n\nline5\n", _str ("unparsed-text('t.txt')"));
    assertEquals ("line1|line2|line3||line5",
                  String.join ("|",
                               _eval ("unparsed-text-lines('t.txt')", BASE, _createResolver (), null).getAsList ()
                                                                                                    .getAllMapped (FnAccessor::getStringValue)));
    assertTrue (_eval ("unparsed-text(())", BASE, _createResolver (), null).isEmpty ());
    assertTrue (_eval ("unparsed-text-lines(())", BASE, _createResolver (), null).isEmpty ());
    _assertError ("FOUT1170", "unparsed-text('missing.txt')", BASE);
    _assertError ("FOUT1170", "unparsed-text('t.txt#frag')", BASE);
    _assertError ("FOUT1170", "unparsed-text('t.txt')", null);
    _assertError ("FOUT1170", "unparsed-text('http://example.org/%gg')", BASE);
    _assertError ("FOUT1190", "unparsed-text('t.txt', '123')", BASE);
    _assertError ("FOUT1190", "unparsed-text('t.txt', 'no-such-charset')", BASE);
    _assertError ("FOUT1190", "unparsed-text('bad.txt')", BASE);
    assertEquals ("true true false false false",
                  _str ("unparsed-text-available('t.txt'), unparsed-text-available('t.txt', 'utf-8'), " +
                        "unparsed-text-available('t.txt', '123'), unparsed-text-available('bad.txt'), unparsed-text-available(())"));
  }

  @Test
  public void testCollections ()
  {
    assertEquals ("c", FnNodeTestHelper.str (_eval ("collection()/*/local-name()", BASE, _createResolver (), null)));
    assertEquals ("true",
                  FnNodeTestHelper.str (_eval ("collection('col') is collection('col')", BASE, _createResolver (), null)));
    assertEquals (BASE + "c.xml", _str ("uri-collection('col')"));
    assertEquals (BASE + "c.xml", _str ("uri-collection(())"));
    _assertError ("FODC0002", "collection('other')", BASE);
    _assertError ("FODC0002", "uri-collection('other')", BASE);
    _assertError ("FODC0004", "collection('%gg')", BASE);
    FnNodeTestHelper.assertError ("FODC0002", () -> FnNodeTestHelper.eval ("collection()"));
  }

  @Test
  public void testEnvironmentVariables ()
  {
    assertTrue (_eval ("available-environment-variables()", BASE, _createResolver (), null).isEmpty ());
    assertTrue (_eval ("environment-variable('PATH')", BASE, _createResolver (), null).isEmpty ());
    final Consumer <DynamicContext> aEnv = aCtx -> {
      final ICommonsMap <String, String> aVars = new CommonsHashMap <> ();
      aVars.put ("FOO", "bar");
      aCtx.setEnvironmentVariables (aVars);
    };
    assertEquals ("FOO", FnNodeTestHelper.str (_eval ("available-environment-variables()", BASE, _createResolver (), aEnv)));
    assertEquals ("bar", FnNodeTestHelper.str (_eval ("environment-variable('FOO')", BASE, _createResolver (), aEnv)));
    _assertError ("XPTY0004", "environment-variable(())", BASE);
  }
}
