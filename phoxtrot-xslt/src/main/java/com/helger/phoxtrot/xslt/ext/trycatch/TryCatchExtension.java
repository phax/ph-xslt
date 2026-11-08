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
package com.helger.phoxtrot.xslt.ext.trycatch;

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.runtime.BufferedOutput;
import com.helger.phoxtrot.xslt.runtime.TryScope;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * xsl:try and xsl:catch (XSLT 3.0 section 8.3): the result of the try part is
 * buffered, so that it can be discarded when a dynamic error occurs; the first
 * xsl:catch whose errors attribute matches the error code then supplies the
 * result, with the err:* variables bound.
 *
 * @author Philip Helger
 */
public final class TryCatchExtension implements IXsltExtension
{
  /** The namespace of the err:* variables. */
  public static final String NS_ERR = XPathException.ERR_NAMESPACE;

  private static final XdmQName VAR_CODE = new XdmQName (NS_ERR, "code", "err");
  private static final XdmQName VAR_DESCRIPTION = new XdmQName (NS_ERR, "description", "err");
  private static final XdmQName VAR_VALUE = new XdmQName (NS_ERR, "value", "err");
  private static final XdmQName VAR_MODULE = new XdmQName (NS_ERR, "module", "err");
  private static final XdmQName VAR_LINE_NUMBER = new XdmQName (NS_ERR, "line-number", "err");
  private static final XdmQName VAR_COLUMN_NUMBER = new XdmQName (NS_ERR, "column-number", "err");
  private static final XdmQName VAR_ADDITIONAL = new XdmQName (NS_ERR, "additional", "err");

  /** Extension data: the locations inside global variable declarations. */
  private static final Object KEY_GLOBAL_LOCATIONS = new Object ();

  /**
   * One name test of xsl:catch/@errors; <code>null</code> parts are
   * wildcards.
   */
  private record NameTest (@Nullable String namespaceUri, @Nullable String localName)
  {
    boolean matches (@NonNull final XdmQName aCode)
    {
      return (namespaceUri == null || namespaceUri.equals (aCode.namespaceUri ())) &&
             (localName == null || localName.equals (aCode.localName ()));
    }
  }

  /**
   * One compiled xsl:catch: the name tests of its errors attribute and either
   * its select expression or its content (never both).
   */
  private record Catch (@NonNull ICommonsList <NameTest> tests,
                        @Nullable XPathExpr select,
                        @Nullable SequenceConstructor body)
  {
    boolean matches (@NonNull final XdmQName aCode)
    {
      for (final NameTest aTest : tests)
        if (aTest.matches (aCode))
          return true;
      return false;
    }
  }

  /**
   * Parse one token of xsl:catch/@errors as a NameTest (XSLT 3.0 section
   * 8.3): <code>*</code>, <code>*:local</code>, <code>Q{uri}local</code>,
   * <code>Q{uri}*</code>, <code>prefix:*</code> or a lexical QName. Prefixes
   * are resolved against the in-scope namespaces of the xsl:catch; an
   * unprefixed name is in no namespace (the default namespace does not
   * apply).
   *
   * @param aEl
   *        The xsl:catch element, for namespace resolution and the error
   *        location.
   * @param sToken
   *        One whitespace-separated token of the attribute.
   * @return The name test. Never <code>null</code>.
   * @throws XsltException
   *         XTSE0020 if the token is not a valid name test, XTSE0280 if its
   *         prefix is not bound.
   */
  @NonNull
  private static NameTest _parseNameTest (@NonNull final XslElement aEl, @NonNull final String sToken)
  {
    if ("*".equals (sToken))
      return new NameTest (null, null);
    if (sToken.startsWith ("*:"))
    {
      final String sLocal = sToken.substring (2);
      if (!XsltNames.isNCName (sLocal))
        throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid name test");
      return new NameTest (null, sLocal);
    }
    if (sToken.startsWith ("Q{"))
    {
      final int nEnd = sToken.indexOf ('}');
      if (nEnd < 0)
        throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid name test");
      final String sUri = sToken.substring (2, nEnd);
      final String sLocal = sToken.substring (nEnd + 1);
      if ("*".equals (sLocal))
        return new NameTest (sUri, null);
      if (!XsltNames.isNCName (sLocal))
        throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid name test");
      return new NameTest (sUri, sLocal);
    }
    if (sToken.endsWith (":*"))
    {
      final String sPrefix = sToken.substring (0, sToken.length () - 2);
      if (!XsltNames.isNCName (sPrefix))
        throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid name test");
      final String sUri = aEl.lookupNamespaceUri (sPrefix);
      if (sUri == null)
        throw XsltException.at (aEl, "XTSE0280", "The prefix of '" + sToken + "' is not bound");
      return new NameTest (sUri, null);
    }
    if (!XsltNames.isLexicalQName (sToken))
      throw XsltException.at (aEl, "XTSE0020", "'" + sToken + "' is not a valid name test");
    // An unprefixed name is in no namespace
    final XdmQName aName = aEl.resolveQName (sToken, false);
    if (aName == null)
      throw XsltException.at (aEl, "XTSE0280", "The prefix of '" + sToken + "' is not bound");
    return new NameTest (aName.namespaceUri (), aName.localName ());
  }

  /**
   * Compile one xsl:catch. A missing errors attribute matches every error
   * (like <code>errors="*"</code>). The select expression or the content is
   * compiled in a context in which the err:* variables (code, description,
   * value, module, line-number, column-number, additional) are in scope; the
   * content is compiled only when there is no select.
   *
   * @param aCtx
   *        Compile context of the enclosing xsl:try.
   * @param aEl
   *        The xsl:catch element.
   * @return The compiled catch clause. Never <code>null</code>.
   */
  @NonNull
  private static Catch _compileCatch (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsList <NameTest> aTests = new CommonsArrayList <> ();
    final String sErrors = aEl.getAttr ("errors");
    if (sErrors == null)
      aTests.add (new NameTest (null, null));
    else
      for (final String sToken : XsltNames.tokenize (sErrors))
        aTests.add (_parseNameTest (aEl, sToken));
    final CompileContext aCatchCtx = aCtx.withVariable (VAR_CODE)
                                         .withVariable (VAR_DESCRIPTION)
                                         .withVariable (VAR_VALUE)
                                         .withVariable (VAR_MODULE)
                                         .withVariable (VAR_LINE_NUMBER)
                                         .withVariable (VAR_COLUMN_NUMBER)
                                         .withVariable (VAR_ADDITIONAL);
    final XPathExpr aSelect = aCatchCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCatchCtx.compileContent (aEl) : null;
    return new Catch (aTests, aSelect, aBody);
  }

  /**
   * Bind the err:* variables of XSLT 3.0 section 8.3 for a caught error.
   * err:code is the error QName; err:description and err:value are empty if
   * the error has none. err:module, err:line-number and err:column-number
   * come from the error location if the error is an {@link XsltException}
   * with a location, otherwise from the xsl:try element itself; a line or
   * column of 0 or less gives the empty sequence. err:additional is always
   * empty.
   *
   * @param aCtx
   *        The context of the xsl:try instruction.
   * @param aEx
   *        The caught error.
   * @param aTryEl
   *        The xsl:try element, the fallback location.
   * @return A derived context with the seven variables bound. Never
   *         <code>null</code>.
   */
  @NonNull
  private static XsltContext _bindErrorVariables (@NonNull final XsltContext aCtx,
                                                  @NonNull final XPathException aEx,
                                                  @NonNull final XslElement aTryEl)
  {
    final String sDescription = aEx.getDescription ();
    final XdmSequence aValue = aEx.getErrorObject ();
    String sModule = null;
    int nLine = 0;
    int nColumn = 0;
    if (aEx instanceof final XsltException aXEx && aXEx.isLocated ())
    {
      sModule = aXEx.getModuleUri ();
      nLine = aXEx.getLine ();
      nColumn = aXEx.getColumn ();
    }
    else
    {
      sModule = aTryEl.getModule ().getUri ();
      nLine = aTryEl.getLine ();
      nColumn = aTryEl.getColumn ();
    }
    return aCtx.withVariable (VAR_CODE, XdmSequence.of (XdmAtomic.qname (aEx.getCodeName ())))
               .withVariable (VAR_DESCRIPTION,
                              sDescription == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (sDescription)))
               .withVariable (VAR_VALUE, aValue == null ? XdmSequence.EMPTY : aValue)
               .withVariable (VAR_MODULE, sModule == null ? XdmSequence.EMPTY : XdmSequence.of (XdmAtomic.string (sModule)))
               .withVariable (VAR_LINE_NUMBER, nLine > 0 ? XdmSequence.of (XdmAtomic.integer (nLine)) : XdmSequence.EMPTY)
               .withVariable (VAR_COLUMN_NUMBER, nColumn > 0 ? XdmSequence.of (XdmAtomic.integer (nColumn)) : XdmSequence.EMPTY)
               .withVariable (VAR_ADDITIONAL, XdmSequence.EMPTY);
  }

  /**
   * Build the key that identifies a source location, as used by
   * {@link #_collectGlobalLocations} and {@link #_isGlobalVariableError}.
   *
   * @param sModuleUri
   *        Module URI, may be <code>null</code> (then "null" is used).
   * @param nLine
   *        Line number.
   * @param nColumn
   *        Column number.
   * @return The key <code>module#line#column</code>. Never <code>null</code>.
   */
  @NonNull
  private static String _locationKey (@Nullable final String sModuleUri, final int nLine, final int nColumn)
  {
    return sModuleUri + "#" + nLine + "#" + nColumn;
  }

  /**
   * Collect the locations of all elements of the global variables and
   * parameters, so that an error raised while evaluating one can be
   * recognised.
   */
  private static void _collectGlobalLocations (@NonNull final CompileContext aCtx)
  {
    final ICommonsSet <String> aKeys = new CommonsHashSet <> ();
    final ArrayDeque <XslElement> aStack = new ArrayDeque <> ();
    for (final VariableDef aDef : aCtx.getStylesheet ().globals ().values ())
      aStack.push (aDef.getElement ());
    while (!aStack.isEmpty ())
    {
      final XslElement aEl = aStack.pop ();
      aKeys.add (_locationKey (aEl.getModule ().getUri (), aEl.getLine (), aEl.getColumn ()));
      for (final XslElement aChild : aEl.getElementChildren ())
        aStack.push (aChild);
    }
    aCtx.getStylesheet ().putExtensionData (KEY_GLOBAL_LOCATIONS, aKeys);
  }

  /**
   * @return <code>true</code> if the error was raised while evaluating a
   *         global variable: such errors are not caught (XSLT 3.0 section
   *         8.3).
   */
  @SuppressWarnings ("unchecked")
  private static boolean _isGlobalVariableError (@NonNull final XsltContext aCtx, @NonNull final XPathException aEx)
  {
    if (!(aEx instanceof final XsltException aXEx) || !aXEx.isLocated ())
      return false;
    final ICommonsSet <String> aKeys = (ICommonsSet <String>) aCtx.getEngine ().getStylesheet ().getExtensionData (KEY_GLOBAL_LOCATIONS);
    return aKeys != null && aKeys.contains (_locationKey (aXEx.getModuleUri (), aXEx.getLine (), aXEx.getColumn ()));
  }

  /**
   * Compile xsl:try (XSLT 3.0 section 8.3). The children before the first
   * xsl:catch form the try body (children after it other than xsl:catch,
   * i.e. xsl:fallback, are ignored); without any xsl:catch child the static
   * error XTSE0010 is raised. rollback-output defaults to yes.
   * <p>
   * At run time the select expression or the body is evaluated in a new
   * {@link TryScope} into a {@link BufferedOutput}; on success the buffer is
   * replayed into the real output. On a dynamic error:
   * </p>
   * <ul>
   * <li>an error raised while evaluating a global variable or a variable
   * declared outside this xsl:try is rethrown unchanged;</li>
   * <li>with rollback-output="no" and some output already buffered, the
   * dynamic error XTDE3530 is raised with the original error as cause;</li>
   * <li>otherwise the first xsl:catch (in document order) whose name tests
   * match the error code supplies the result, evaluated with the err:*
   * variables bound and written directly to the real output; the buffered
   * output of the try body is discarded;</li>
   * <li>if no xsl:catch matches, the error is rethrown.</li>
   * </ul>
   * Any other runtime exception (the non-local exits of xsl:break and
   * xsl:next-iteration) first replays the buffered output and is then
   * rethrown.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:try element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compileTry (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final String sRollback = aEl.getAttr ("rollback-output");
    final boolean bRollback = sRollback == null || XsltNames.isXsltTrue (sRollback);
    final ICommonsList <XslNode> aBodyNodes = new CommonsArrayList <> ();
    final ICommonsList <Catch> aCatches = new CommonsArrayList <> ();
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof final XslElement aChild && aChild.isXsl ("catch"))
        aCatches.add (_compileCatch (aCtx, aChild));
      else
        if (aCatches.isEmpty ())
          aBodyNodes.add (aNode);
    if (aCatches.isEmpty ())
      throw XsltException.at (aEl, "XTSE0010", "xsl:try requires at least one xsl:catch child");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileSequenceConstructor (aEl, aBodyNodes) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final BufferedOutput aBuffer = new BufferedOutput ();
      final XsltContext aBodyCtx = TryScope.enter (aRtCtx);
      try
      {
        if (aSelect != null)
          aBuffer.appendAll (aSelect.evaluate (aBodyCtx));
        else
          aBody.execute (aBodyCtx, aBuffer);
      }
      catch (final XPathException ex)
      {
        // Errors of variables declared outside are not caught (section 8.3)
        if (_isGlobalVariableError (aRtCtx, ex) || TryScope.isDeclaredOutside (aBodyCtx, ex))
          throw ex;
        if (!bRollback && aBuffer.size () > 0)
          throw new XPathException ("XTDE3530",
                                    "xsl:try with rollback-output='no' cannot recover after output was written: " + ex.getMessage (),
                                    ex);
        for (final Catch aCatch : aCatches)
          if (aCatch.matches (ex.getCodeName ()))
          {
            final XsltContext aCatchCtx = _bindErrorVariables (aRtCtx, ex, aEl);
            if (aCatch.select () != null)
              aRtOut.appendAll (aCatch.select ().evaluate (aCatchCtx));
            else
              aCatch.body ().execute (aCatchCtx, aRtOut);
            return;
          }
        throw ex;
      }
      catch (final RuntimeException ex)
      {
        // A non-local exit (xsl:break, xsl:next-iteration) is no error: keep
        // what was produced up to that point
        aBuffer.replayAll (aRtOut);
        throw ex;
      }
      aBuffer.replayAll (aRtOut);
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("try", TryCatchExtension::_compileTry);
    // Only reachable for a misplaced xsl:catch; makes element-available('xsl:catch') true
    aRegistry.registerInstruction ("catch", (aCtx, aEl) -> {
      throw XsltException.at (aEl, "XTSE0010", "xsl:catch is only allowed as a child of xsl:try");
    });
    aRegistry.registerCompileFinisher (TryCatchExtension::_collectGlobalLocations);
  }
}
