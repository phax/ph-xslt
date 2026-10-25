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

import static com.helger.xpath.spec.SpecTestHelper.env;
import static com.helger.xpath.spec.SpecTestHelper.err;
import static com.helger.xpath.spec.SpecTestHelper.ok;
import static org.junit.Assert.fail;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;

/**
 * Port of go-xslt's <code>fn_analyze_string_test.go</code>: fn:analyze-string
 * (F&amp;O 3.1 §5.6.6).
 *
 * @author Philip Helger
 */
public final class FnAnalyzeStringSpecTest
{
  private static final String NS_FN = "http://www.w3.org/2005/xpath-functions";

  /**
   * Render an fn:analyze-string result compactly: an element as its local name
   * (plus @nr for fn:group) with its children in brackets, a text node as its
   * text.
   */
  @NonNull
  private static String _render (@NonNull final XdmNode aNode)
  {
    // Iterative, with an explicit stack of pending output
    final StringBuilder aSB = new StringBuilder ();
    final ICommonsList <Object> aStack = new CommonsArrayList <> ();
    aStack.add (aNode);
    while (aStack.isNotEmpty ())
    {
      final Object aTop = aStack.removeLast ();
      if (aTop instanceof final String sText)
      {
        aSB.append (sText);
        continue;
      }
      final XdmNode aCur = (XdmNode) aTop;
      if (aCur.isText ())
      {
        aSB.append (aCur.getValue ());
        continue;
      }
      aSB.append (aCur.getName ().localName ());
      final XdmNode aNr = aCur.getAttribute ("", "nr");
      if (aNr != null)
        aSB.append (aNr.getValue ());
      aSB.append ('[');
      aStack.add ("]");
      for (int i = aCur.getChildCount () - 1; i >= 0; --i)
        aStack.add (aCur.getChildAtIndex (i));
    }
    return aSB.toString ();
  }

  private static final String [] [] CASES = { { "analyze-string(\"The cat sat on the mat.\", \"\\w+\")",
                                                "analyze-string-result[match[The]non-match[ ]match[cat]non-match[ ]match[sat]non-match[ ]match[on]non-match[ ]match[the]non-match[ ]match[mat]non-match[.]]" },
                                              { "analyze-string(\"2008-12-03\", \"^(\\d+)\\-(\\d+)\\-(\\d+)$\")",
                                                "analyze-string-result[match[group1[2008]-group2[12]-group3[03]]]" },
                                              { "analyze-string(\"A1,C15,,D24, X50,\", \"([A-Z])([0-9]+)\")",
                                                "analyze-string-result[match[group1[A]group2[1]]non-match[,]match[group1[C]group2[15]]non-match[,,]match[group1[D]group2[24]]non-match[, ]match[group1[X]group2[50]]non-match[,]]" },
                                              // Nested groups nest in the result, numbered
                                              // by opening parenthesis.
                                              { "analyze-string(\"abc\", \"(a(b))c\")",
                                                "analyze-string-result[match[group1[agroup2[b]]c]]" },
                                              // (?:...) does not capture; parentheses in a
                                              // class or escaped do not either.
                                              { "analyze-string(\"ab\", \"(?:a)(b)\")",
                                                "analyze-string-result[match[agroup1[b]]]" },
                                              { "analyze-string(\"(a\", \"[(](a)\")",
                                                "analyze-string-result[match[(group1[a]]]" },
                                              { "analyze-string(\"(a\", \"\\((a)\")",
                                                "analyze-string-result[match[(group1[a]]]" },
                                              // A group that does not participate is
                                              // omitted.
                                              { "analyze-string(\"b\", \"(a)|(b)\")",
                                                "analyze-string-result[match[group2[b]]]" },
                                              // A repeated group reports its last iteration
                                              // only.
                                              { "analyze-string(\"aab\", \"(a)+b\")",
                                                "analyze-string-result[match[agroup1[a]b]]" },
                                              // q flag: the pattern is literal, so nothing
                                              // is a group.
                                              { "analyze-string(\"x(a)\", \"(a)\", \"q\")",
                                                "analyze-string-result[non-match[x]match[(a)]]" },
                                              { "analyze-string(\"xAx\", \"a\", \"i\")",
                                                "analyze-string-result[non-match[x]match[A]non-match[x]]" },
                                              { "analyze-string(\"xyz\", \"a\")",
                                                "analyze-string-result[non-match[xyz]]" },
                                              { "analyze-string((), \"a\")", "analyze-string-result[]" },
                                              { "analyze-string(\"\", \"a\")", "analyze-string-result[]" } };

  /**
   * The REC's three examples plus nested, non-capturing, non-participating
   * and repeated groups.
   */
  @Test
  public void testAnalyzeString ()
  {
    final ICommonsList <String> aErrors = new CommonsArrayList <> ();
    for (final String [] aCase : CASES)
    {
      final XdmSequence aResult;
      try
      {
        aResult = SpecTestHelper.eval (aCase[0]);
      }
      catch (final XPathException ex)
      {
        aErrors.add (aCase[0] + ": " + ex.getMessage ());
        continue;
      }
      if (aResult.size () != 1 || !(aResult.get (0) instanceof XdmNode))
      {
        aErrors.add (aCase[0] + ": " + aResult.size () + " items, want 1 node");
        continue;
      }
      final String sGot = _render ((XdmNode) aResult.get (0));
      if (!sGot.equals (aCase[1]))
        aErrors.add (aCase[0] + " =\n   " + sGot + "\n  want\n   " + aCase[1]);
    }
    if (aErrors.isNotEmpty ())
      fail (aErrors.size () + " of " + CASES.length + " cases failed:\n  " + String.join ("\n  ", aErrors));

    CoreRenderSpecHelper.run (env (),
                              ok ("namespace-uri(analyze-string(\"a\", \"a\"))", NS_FN),
                              ok ("namespace-uri(analyze-string(\"a\", \"a\")/*)", NS_FN),
                              ok ("analyze-string(\"a\", \"(a)\")//@nr/string()", "1"),
                              err ("analyze-string(\"abc\", \"a?\")", "FORX0003"),
                              err ("analyze-string(\"abc\", \"(\")", "FORX0002"),
                              err ("analyze-string(\"abc\", \"a\", \"k\")", "FORX0001"));
  }
}
