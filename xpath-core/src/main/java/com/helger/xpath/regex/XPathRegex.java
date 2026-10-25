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
package com.helger.xpath.regex;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.wrapper.Wrapper;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;

/**
 * A compiled XPath 3.1 regular expression (F&amp;O 3.1 section 5.6) with its
 * flags. Instances are immutable and may be shared between threads; compiled
 * expressions are kept in a bounded cache.
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

  // A get reorders the access-ordered map, so every access takes the write lock
  private static final SimpleReadWriteLock CACHE_LOCK = new SimpleReadWriteLock ();
  private static final Map <String, XPathRegex> CACHE = new LinkedHashMap <> (64, 0.75f, true)
  {
    @Override
    protected boolean removeEldestEntry (final Map.Entry <String, XPathRegex> aEldest)
    {
      return size () > CACHE_SIZE;
    }
  };

  private final Pattern m_aPattern;
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

  private XPathRegex (@NonNull final Pattern aPattern,
                      final int nGroupCount,
                      @NonNull final int [] aGroupParents,
                      @NonNull final int [] aJavaGroups,
                      final boolean bLiteral)
  {
    m_aPattern = aPattern;
    m_nGroupCount = nGroupCount;
    m_aGroupParents = aGroupParents;
    m_aJavaGroups = aJavaGroups;
    m_bLiteral = bLiteral;
  }

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
    try
    {
      aPattern = Pattern.compile (aResult.javaPattern ());
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
    return new XPathRegex (aPattern, aResult.groupCount (), aResult.groupParents (), aResult.javaGroups (), bLiteral);
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
    final XPathRegex aCached = CACHE_LOCK.writeLockedGet ( () -> CACHE.get (sKey));
    if (aCached != null)
      return aCached;
    final XPathRegex ret = _compile (sPattern, sRealFlags, bXsd11Classes);
    CACHE_LOCK.writeLocked ( () -> CACHE.put (sKey, ret));
    return ret;
  }

  /** @return The Java pattern this expression was translated into. */
  @NonNull
  public Pattern getPattern ()
  {
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
   * @return A new matcher.
   */
  @NonNull
  public Matcher matcher (@NonNull final DynamicContext aCtx, @NonNull final String sInput)
  {
    return m_aPattern.matcher (new InterruptibleCharSequence (sInput, aCtx));
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
    return m_aPattern.matcher ("").matches ();
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
