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
package com.helger.phoxtrot.xpath.regex;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.wrapper.Wrapper;
import com.helger.collection.map.ConcurrentBoundedCache;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;

/**
 * A compiled XPath 3.1 regular expression (F&amp;O 3.1 section 5.6) with its
 * flags. Instances are immutable and may be shared between threads; compiled
 * expressions are kept in a bounded cache.
 * <p>
 * The general categories are those of Unicode 15.0 on every JDK. Where the
 * running JDK has a newer Unicode version, a category class is corrected by
 * the code points whose category changed, and a corrected class is far slower
 * to match (each of its ranges is one more nested predicate in Java). Such an
 * expression is therefore also compiled with the uncorrected built-in classes,
 * which {@link #getPattern(String)} and {@link #matcher(DynamicContext, String)}
 * use for every input without a changed code point.
 *
 * @author Philip Helger
 */
@Immutable
public final class XPathRegex
{
  /** Maximum number of compiled expressions kept */
  public static final int CACHE_SIZE = 1_000;
  /** Stack size of the fallback thread for deeply recursing matches */
  private static final long LARGE_STACK_SIZE = 512L * 1024 * 1024;

  // Lock-free reads; when full, about a quarter of the entries is dropped (not LRU)
  private static final ConcurrentBoundedCache <String, XPathRegex> CACHE = new ConcurrentBoundedCache <> (CACHE_SIZE);

  private final Pattern m_aPattern;
  // Null if no category class needed a correction
  private final Pattern m_aBuiltInPattern;
  private final boolean m_bMatchesEmptyString;
  private final int m_nGroupCount;
  private final int [] m_aGroupParents;
  private final int [] m_aJavaGroups;
  private final boolean m_bLiteral;

  /**
   * A view of a string for the Java matcher that checks the interrupt flag of
   * the dynamic context now and then, so that a catastrophically backtracking
   * expression can be cancelled.
   */
  private static final class InterruptibleCharSequence implements CharSequence
  {
    private final String m_sValue;
    private final DynamicContext m_aCtx;
    private int m_nCount;

    InterruptibleCharSequence (@NonNull final String sValue, @NonNull final DynamicContext aCtx)
    {
      m_sValue = sValue;
      m_aCtx = aCtx;
    }

    public int length ()
    {
      return m_sValue.length ();
    }

    /**
     * Return the char at the index; every 2^20th access (over the lifetime of
     * this view) calls {@link DynamicContext#checkInterrupt()}, which may
     * throw to abort the match.
     */
    public char charAt (final int nIndex)
    {
      if ((++m_nCount & 0xFFFFF) == 0)
        m_aCtx.checkInterrupt ();
      return m_sValue.charAt (nIndex);
    }

    public CharSequence subSequence (final int nStart, final int nEnd)
    {
      return m_sValue.substring (nStart, nEnd);
    }

    @Override
    public String toString ()
    {
      return m_sValue;
    }
  }

  /**
   * Wrap a compiled Java pattern. Whether it matches the empty string is
   * determined once here, by a full match against <code>""</code>.
   *
   * @param aPattern
   *        The translated Java pattern.
   * @param aBuiltInPattern
   *        The translated Java pattern with the uncorrected built-in category
   *        classes, or <code>null</code> if it equals <code>aPattern</code>.
   * @param nGroupCount
   *        The number of capturing groups of the XPath expression.
   * @param aGroupParents
   *        Per XPath group (index 1..groupCount) the enclosing group.
   * @param aJavaGroups
   *        Per XPath group (index 1..groupCount) the Java group number.
   * @param bLiteral
   *        <code>true</code> if the 'q' flag was given.
   */
  private XPathRegex (@NonNull final Pattern aPattern,
                      @Nullable final Pattern aBuiltInPattern,
                      final int nGroupCount,
                      @NonNull final int [] aGroupParents,
                      @NonNull final int [] aJavaGroups,
                      final boolean bLiteral)
  {
    m_aPattern = aPattern;
    m_aBuiltInPattern = aBuiltInPattern;
    m_nGroupCount = nGroupCount;
    m_aGroupParents = aGroupParents;
    m_aJavaGroups = aJavaGroups;
    m_bLiteral = bLiteral;
    // Asked by tokenize, replace and analyze-string on every call
    m_bMatchesEmptyString = aPattern.matcher ("").matches ();
  }

  /**
   * Compile without the cache: parse the flags (F&amp;O 3.1 section 5.6.2),
   * translate the expression with {@link RegexTranslator} (with 'q' as a
   * literal string, where the flags 's', 'm' and 'x' have no effect; with 'x'
   * after removing the whitespace) and compile the Java pattern, and the one
   * with the built-in category classes if the translation has it. Repeated
   * flags are allowed.
   *
   * @param sPattern
   *        The XPath regular expression.
   * @param sFlags
   *        The flags, never <code>null</code>.
   * @param bXsd11Classes
   *        <code>true</code> for the XSD 1.1 character class grammar.
   * @return The compiled expression.
   * @throws XPathException
   *         FORX0001 for a flag character other than <code>smixq</code>,
   *         FORX0002 for an invalid expression (also if Java rejects the
   *         translation), XPDY0130 if it is nested too deeply or Java runs out
   *         of stack compiling it
   */
  @NonNull
  private static XPathRegex _compile (@NonNull final String sPattern,
                                      @NonNull final String sFlags,
                                      final boolean bXsd11Classes)
  {
    boolean bCaseInsensitive = false;
    boolean bDotAll = false;
    boolean bMultiLine = false;
    boolean bIgnoreWhitespace = false;
    boolean bLiteral = false;
    for (int i = 0; i < sFlags.length (); ++i)
    {
      final char c = sFlags.charAt (i);
      switch (c)
      {
        case 'i':
          bCaseInsensitive = true;
          break;
        case 's':
          bDotAll = true;
          break;
        case 'm':
          bMultiLine = true;
          break;
        case 'x':
          bIgnoreWhitespace = true;
          break;
        case 'q':
          bLiteral = true;
          break;
        default:
          throw XPathException.of ("FORX0001", "Invalid regular expression flags '" + sFlags + "'");
      }
    }
    final RegexTranslator.Result aResult;
    if (bLiteral)
      aResult = RegexTranslator.translateLiteral (sPattern, bCaseInsensitive);
    else
      aResult = RegexTranslator.translate (bIgnoreWhitespace ? RegexTranslator.stripWhitespace (sPattern) : sPattern,
                                           bCaseInsensitive,
                                           bDotAll,
                                           bMultiLine,
                                           bXsd11Classes);
    final Pattern aPattern;
    final Pattern aBuiltInPattern;
    try
    {
      aPattern = Pattern.compile (aResult.javaPattern ());
      final String sBuiltIn = aResult.builtInJavaPattern ();
      aBuiltInPattern = sBuiltIn == null ? null : Pattern.compile (sBuiltIn);
    }
    catch (final PatternSyntaxException ex)
    {
      // Should not happen after validation
      throw XPathException.of ("FORX0002", "Invalid regular expression '" + sPattern + "': " + ex.getDescription ());
    }
    catch (final StackOverflowError ex)
    {
      throw XPathException.of ("XPDY0130", "The regular expression '" + sPattern + "' is too complex");
    }
    return new XPathRegex (aPattern,
                           aBuiltInPattern,
                           aResult.groupCount (),
                           aResult.groupParents (),
                           aResult.javaGroups (),
                           bLiteral);
  }

  /**
   * Compile an XPath regular expression with the XSD 1.1 character class
   * grammar (F&amp;O 3.1 leaves the XSD version to the processor; a '-'
   * directly after a range is a literal), using the cache.
   *
   * @param sPattern
   *        The regular expression.
   * @param sFlags
   *        The flags (any of "smixq"). May be <code>null</code> for none.
   * @return The compiled expression.
   * @throws XPathException
   *         FORX0001 for invalid flags, FORX0002 for an invalid expression
   */
  @NonNull
  public static XPathRegex compile (@NonNull final String sPattern, @Nullable final String sFlags)
  {
    return compile (sPattern, sFlags, true);
  }

  /**
   * Compile an XPath regular expression, using the cache.
   *
   * @param sPattern
   *        The regular expression.
   * @param sFlags
   *        The flags (any of "smixq"). May be <code>null</code> for none.
   * @param bXsd11Classes
   *        <code>true</code> for the XSD 1.1 character class grammar (a '-'
   *        directly after a range is a literal), <code>false</code> for the
   *        XSD 1.0 one.
   * @return The compiled expression.
   * @throws XPathException
   *         FORX0001 for invalid flags, FORX0002 for an invalid expression
   */
  @NonNull
  public static XPathRegex compile (@NonNull final String sPattern,
                                    @Nullable final String sFlags,
                                    final boolean bXsd11Classes)
  {
    final String sRealFlags = sFlags == null ? "" : sFlags;
    final String sKey = (bXsd11Classes ? "1" : "0") + sRealFlags + '\u0000' + sPattern;
    // A failed compilation throws and is not cached
    return CACHE.getOrCompute (sKey, k -> _compile (sPattern, sRealFlags, bXsd11Classes));
  }

  /**
   * @return The Java pattern this expression was translated into, with the
   *         Unicode 15.0 categories. It is correct for every input;
   *         {@link #getPattern(String)} may be faster.
   */
  @NonNull
  public Pattern getPattern ()
  {
    return m_aPattern;
  }

  /**
   * Get the Java pattern to match one input with: the one with the uncorrected
   * built-in category classes if there is one and the input contains no code
   * point whose category in the running JDK differs from Unicode 15.0 (which
   * costs one scan of the input), otherwise {@link #getPattern()}. Both match
   * the input the same way.
   *
   * @param sInput
   *        The string that will be matched.
   * @return The pattern. Never <code>null</code>.
   */
  @NonNull
  public Pattern getPattern (@NonNull final String sInput)
  {
    if (m_aBuiltInPattern != null && !UnicodeCategories.containsDifference (sInput))
      return m_aBuiltInPattern;
    return m_aPattern;
  }

  /** @return The number of capturing groups (0 with the 'q' flag). */
  public int getGroupCount ()
  {
    return m_nGroupCount;
  }

  /**
   * @param nGroup
   *        A capturing group number (1-based).
   * @return The number of the capturing group it is directly nested in, 0 for
   *         none.
   */
  public int getParentGroup (final int nGroup)
  {
    return nGroup > 0 && nGroup < m_aGroupParents.length ? m_aGroupParents[nGroup] : 0;
  }

  /**
   * The Java pattern may contain more capturing groups than the XPath
   * expression (empty marker groups for back-references).
   *
   * @param nGroup
   *        An XPath capturing group number (1-based), or 0 for the whole
   *        match.
   * @return The number of the corresponding group of the Java matcher.
   */
  public int getJavaGroup (final int nGroup)
  {
    return nGroup > 0 && nGroup < m_aJavaGroups.length ? m_aJavaGroups[nGroup] : 0;
  }

  /** @return <code>true</code> if the 'q' flag was given. */
  public boolean isLiteral ()
  {
    return m_bLiteral;
  }

  /**
   * @param aCtx
   *        Dynamic context, for interruption.
   * @param sInput
   *        The string to match against.
   * @return A new matcher of {@link #getPattern(String)}.
   */
  @NonNull
  public Matcher matcher (@NonNull final DynamicContext aCtx, @NonNull final String sInput)
  {
    return getPattern (sInput).matcher (new InterruptibleCharSequence (sInput, aCtx));
  }

  /**
   * @param aCtx
   *        Dynamic context, for interruption.
   * @param sInput
   *        The string to match against.
   * @return <code>true</code> if some substring of the input matches.
   */
  public boolean matches (@NonNull final DynamicContext aCtx, @NonNull final String sInput)
  {
    return withStackFallback ( () -> Boolean.valueOf (matcher (aCtx, sInput).find ())).booleanValue ();
  }

  /** @return <code>true</code> if the expression matches the empty string. */
  public boolean matchesEmptyString ()
  {
    return m_bMatchesEmptyString;
  }

  @NonNull
  private static XPathException _createTooComplex ()
  {
    return XPathException.of ("XPDY0130", "The regular expression is too complex for the input string");
  }

  /**
   * Run a matching action. The Java matcher recurses for alternations and
   * groups under a quantifier, so a long input can exhaust the stack (e.g.
   * <code>(a|b)*</code> over 100,000 characters). In that case the action is
   * run again in a thread with a large stack; if that is not enough either,
   * XPDY0130 is raised. The action must be repeatable from scratch.
   *
   * @param <T>
   *        Result type
   * @param aAction
   *        The action.
   * @return The result of the action.
   */
  public static <T> T withStackFallback (@NonNull final Supplier <T> aAction)
  {
    try
    {
      return aAction.get ();
    }
    catch (final StackOverflowError ex)
    {
      // fall through
    }
    final Wrapper <T> aResult = new Wrapper <> ();
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        aResult.set (aAction.get ());
      }
      catch (final Throwable ex)
      {
        aError.set (ex);
      }
    }, "ph-xpath-regex", LARGE_STACK_SIZE);
    aThread.start ();
    try
    {
      aThread.join ();
    }
    catch (final InterruptedException ex)
    {
      Thread.currentThread ().interrupt ();
      aThread.interrupt ();
      throw _createTooComplex ();
    }
    final Throwable aThrowable = aError.get ();
    if (aThrowable == null)
      return aResult.get ();
    if (aThrowable instanceof StackOverflowError)
      throw _createTooComplex ();
    if (aThrowable instanceof final RuntimeException aRuntimeEx)
      throw aRuntimeEx;
    if (aThrowable instanceof final Error aErr)
      throw aErr;
    throw new IllegalStateException (aThrowable);
  }
}
