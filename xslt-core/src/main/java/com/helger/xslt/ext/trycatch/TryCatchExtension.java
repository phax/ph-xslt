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
package com.helger.xslt.ext.trycatch;

import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.BufferedOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XsltNames;

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
      try
      {
        if (aSelect != null)
          aBuffer.appendAll (aSelect.evaluate (aRtCtx));
        else
          aBody.execute (aRtCtx, aBuffer);
      }
      catch (final XPathException ex)
      {
        if (_isGlobalVariableError (aRtCtx, ex))
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
