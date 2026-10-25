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
package com.helger.xslt.instr;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.IExpr;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IInstructionCompiler;
import com.helger.xslt.compile.StylesheetCompiler;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltElementSpecs;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.model.VariableDef;
import com.helger.xslt.runtime.ContentOutput;
import com.helger.xslt.runtime.NamespaceFixup;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

/**
 * Compilation of sequence constructors, variables, literal result elements and
 * fallback, and the registration of the core instructions.
 *
 * @author Philip Helger
 */
@Immutable
public final class CoreInstructions
{
  @PresentForCodeCoverage
  private static final CoreInstructions INSTANCE = new CoreInstructions ();

  private CoreInstructions ()
  {}

  /**
   * Register the core instructions.
   *
   * @param aRegistry
   *        The registry.
   */
  public static void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    NodeInstructions.register (aRegistry);
    ControlInstructions.register (aRegistry);
  }

  /**
   * XSLT 3.0 section 4.3: whitespace text in the stylesheet that is removed
   * even under xml:space="preserve".
   */
  private static boolean _isStrippedDespitePreserve (@NonNull final XslText aText)
  {
    final XslElement aParent = aText.getParent ();
    if (aParent == null)
      return false;
    if (aParent.isXsl ())
      switch (aParent.getLocalName ())
      {
        case "accumulator", "analyze-string", "apply-imports", "apply-templates", "attribute-set", "call-template", "character-map", "choose", "evaluate", "fork", "merge", "merge-source", "mode", "next-iteration", "next-match", "override", "package", "stylesheet", "transform", "use-package":
          return true;
        default:
          break;
      }
    final ICommonsList <XslNode> aSiblings = aParent.getChildren ();
    final int nIndex = aSiblings.indexOf (aText);
    if (nIndex + 1 < aSiblings.size () && aSiblings.get (nIndex + 1) instanceof final XslElement aNext && aNext.isXsl ())
    {
      final String s = aNext.getLocalName ();
      if ("param".equals (s) || "sort".equals (s) || "context-item".equals (s) || "on-completion".equals (s))
        return true;
    }
    return nIndex > 0 && aSiblings.get (nIndex - 1) instanceof final XslElement aPrev && aPrev.isXsl ("catch");
  }

  private static boolean _isNameChar (final char c)
  {
    return Character.isLetterOrDigit (c) || c == '_' || c == '-' || c == '.' || c == '·' || c > 0x7f;
  }

  /**
   * Find "$" [Q{...}] [prefix ":"] local, not followed by a further name
   * character, anywhere in the text.
   */
  private static boolean _textMentionsVar (@NonNull final String sText, @NonNull final String sLocal)
  {
    int nIdx = sText.indexOf ('$');
    while (nIdx >= 0)
    {
      String sRest = sText.substring (nIdx + 1).stripLeading ();
      if (sRest.startsWith ("Q{"))
      {
        final int nClose = sRest.indexOf ('}');
        if (nClose >= 0)
          sRest = sRest.substring (nClose + 1);
      }
      else
      {
        final int nColon = sRest.indexOf (':');
        if (nColon > 0 && nColon < sRest.length () - 1 && sRest.charAt (nColon + 1) != ':')
        {
          boolean bNCName = true;
          for (int i = 0; i < nColon && bNCName; ++i)
            bNCName = _isNameChar (sRest.charAt (i));
          if (bNCName)
            sRest = sRest.substring (nColon + 1);
        }
      }
      if (sRest.startsWith (sLocal) &&
          (sRest.length () == sLocal.length () || !_isNameChar (sRest.charAt (sLocal.length ()))))
        return true;
      nIdx = sText.indexOf ('$', nIdx + 1);
    }
    return false;
  }

  /**
   * Conservative check whether anything in the given nodes or their
   * descendants (attribute values and text) could refer to a variable with the
   * given local name, whatever namespace it resolves to. Iterative.
   */
  private static boolean _mentionsVar (@NonNull final List <XslNode> aNodes, @NonNull final String sLocal)
  {
    final ICommonsList <XslNode> aStack = new CommonsArrayList <> (aNodes);
    while (aStack.isNotEmpty ())
    {
      final XslNode aNode = aStack.removeLast ();
      if (aNode instanceof final XslText aText)
      {
        if (_textMentionsVar (aText.getValue (), sLocal))
          return true;
      }
      else
        if (aNode instanceof final XslElement aEl)
        {
          for (final String sValue : aEl.getAllAttributes ().values ())
            if (_textMentionsVar (sValue, sLocal))
              return true;
          aStack.addAll (aEl.getChildren ());
        }
    }
    return false;
  }

  /**
   * A local xsl:variable whose value is a bare variable reference and that no
   * following sibling (or its descendants) can refer to is never evaluated:
   * XSLT evaluates variables lazily, and param-0301 relies on such an unused
   * variable inside a function that a global variable calls not tripping the
   * circularity check. Only a bare variable reference without "as" qualifies,
   * since evaluating a richer expression can surface an error a test expects
   * to be reported.
   */
  private static boolean _isUnusedLocalVariable (@NonNull final VariableDef aDef,
                                                 @NonNull final List <XslNode> aChildren,
                                                 final int nIndex)
  {
    final XPathExpr aSelect = aDef.getSelect ();
    if (aSelect == null ||
        !(aSelect.getExpression () instanceof IExpr.VarRefExpr) ||
        aDef.getAs () != null ||
        aDef.getElement ().getChildCount () > 0)
      return false;
    return !_mentionsVar (aChildren.subList (nIndex + 1, aChildren.size ()), aDef.getName ().localName ());
  }

  /**
   * Compile a sequence constructor (see
   * {@link CompileContext#compileSequenceConstructor(XslElement, List)}).
   *
   * @param aCtx
   *        Compile context.
   * @param aParent
   *        Containing element.
   * @param aChildren
   *        The nodes.
   * @return The sequence constructor.
   */
  @NonNull
  public static SequenceConstructor compileSequenceConstructor (@NonNull final CompileContext aCtx,
                                                                @NonNull final XslElement aParent,
                                                                @NonNull final List <XslNode> aChildren)
  {
    final ICommonsList <IInstruction> aInstrs = new CommonsArrayList <> ();
    CompileContext aCur = aCtx;
    boolean bHasMarker = false;
    for (int nIndex = 0; nIndex < aChildren.size (); ++nIndex)
    {
      final XslNode aNode = aChildren.get (nIndex);
      if (aNode instanceof final XslText aText)
      {
        if (aText.isWhitespaceOnly () && (!aText.isXmlSpacePreserve () || _isStrippedDespitePreserve (aText)))
          continue;
        if (CompileContext.isExpandText (aText))
        {
          final Avt aAvt = aCur.compileTextValueTemplate (aParent, aText.getValue ());
          if (aAvt.isConstant ())
            aInstrs.add (new LiteralText (aText, aAvt.getConstantValue ()));
          else
            aInstrs.add (new CompiledInstruction (aParent, (aRtCtx, aRtOut) -> aRtOut.appendNew (XdmNode.createText (aAvt.evaluate (aRtCtx)))));
        }
        else
          aInstrs.add (new LiteralText (aText, aText.getValue ()));
        continue;
      }
      final XslElement aEl = (XslElement) aNode;
      if (aEl.isXsl ("variable") || aEl.isXsl ("param"))
      {
        final VariableDef aDef = aCur.compileVariable (aEl, VariableDef.EKind.LOCAL_VARIABLE);
        if (!_isUnusedLocalVariable (aDef, aChildren, nIndex))
          aInstrs.add (new LocalVariable (aDef));
        aCur = aCur.withVariable (aDef.getName ());
        continue;
      }
      if (aEl.isXsl ("fallback"))
        continue;
      final IInstruction aInstr = aCur.compileInstruction (aEl);
      if (aInstr != null)
      {
        if (aInstr instanceof OnEmptyInstruction)
          bHasMarker = true;
        aInstrs.add (aInstr);
      }
    }
    if (bHasMarker)
    {
      for (int i = 0; i < aInstrs.size (); ++i)
        if (aInstrs.get (i) instanceof final OnEmptyInstruction aMarker &&
            !aMarker.isOnNonEmpty () &&
            i != aInstrs.size () - 1)
          throw XsltException.at (aMarker.getElement (), "XTSE0010", "xsl:on-empty must be the last instruction of its sequence constructor");
      final ICommonsList <IInstruction> aWrapped = new CommonsArrayList <> ();
      aWrapped.add (new ConditionalContent (aParent, aInstrs));
      return new SequenceConstructor (aParent, aWrapped);
    }
    return new SequenceConstructor (aParent, aInstrs);
  }

  @NonNull
  private static IInstruction _compileFallback (@NonNull final CompileContext aCtx,
                                                @NonNull final XslElement aEl,
                                                @NonNull final String sWhat)
  {
    final ICommonsList <XslElement> aFallbacks = aEl.getXslChildren ("fallback");
    if (aFallbacks.isEmpty ())
      return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
        throw new XPathException ("XTDE1450", sWhat + " <" + aEl.getName ().getLexicalName () + "> is not available and has no xsl:fallback");
      });
    final ICommonsList <SequenceConstructor> aBodies = new CommonsArrayList <> ();
    for (final XslElement aFallback : aFallbacks)
      aBodies.add (aCtx.compileSequenceConstructor (aFallback, aFallback.getChildren ()));
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      for (final SequenceConstructor aBody : aBodies)
        aBody.execute (aRtCtx, aRtOut);
    });
  }

  /**
   * @param aEl
   *        An element.
   * @return The namespace URIs designated as extension namespaces on the
   *         element or its ancestors.
   */
  @NonNull
  public static ICommonsSet <String> getExtensionNamespaces (@NonNull final XslElement aEl)
  {
    final ICommonsSet <String> ret = new CommonsHashSet <> ();
    for (XslElement aCur = aEl; aCur != null; aCur = aCur.getParent ())
    {
      final String s = aCur.getStandardAttr ("extension-element-prefixes");
      if (s != null)
        for (final String sToken : XsltNames.tokenize (s))
        {
          final String sUri = aCur.lookupNamespaceUri ("#default".equals (sToken) ? "" : sToken);
          if (sUri != null)
            ret.add (sUri);
        }
    }
    return ret;
  }

  /**
   * @param aEl
   *        A literal result element.
   * @return The namespace URIs excluded from it (XSLT namespace, extension
   *         namespaces, exclude-result-prefixes on it and its ancestors).
   */
  @NonNull
  public static ICommonsSet <String> getExcludedNamespaces (@NonNull final XslElement aEl)
  {
    final ICommonsSet <String> ret = getExtensionNamespaces (aEl);
    ret.add (XsltNames.NS_XSL);
    for (XslElement aCur = aEl; aCur != null; aCur = aCur.getParent ())
    {
      final String s = aCur.getStandardAttr ("exclude-result-prefixes");
      if (s != null)
        for (final String sToken : XsltNames.tokenize (s))
          if ("#all".equals (sToken))
            ret.addAll (aCur.getInScopeNamespaces ().values ());
          else
          {
            final String sUri = aCur.lookupNamespaceUri ("#default".equals (sToken) ? "" : sToken);
            if (sUri != null)
              ret.add (sUri);
          }
    }
    return ret;
  }

  /**
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        Element with a use-attribute-sets attribute (in no namespace or the
   *        XSLT namespace).
   * @param bXslNamespace
   *        <code>true</code> to read xsl:use-attribute-sets.
   * @return The attribute set names (registered for the static check).
   */
  @NonNull
  public static ICommonsList <XdmQName> getUseAttributeSets (@NonNull final CompileContext aCtx,
                                                             @NonNull final XslElement aEl,
                                                             final boolean bXslNamespace)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    final String s = bXslNamespace ? aEl.getAttribute (XsltNames.NS_XSL, "use-attribute-sets") : aEl.getAttr ("use-attribute-sets");
    if (s != null)
      for (final String sToken : XsltNames.tokenize (s))
      {
        final XdmQName aName = aCtx.resolveQName (aEl, sToken, false);
        ret.add (aName);
        aCtx.getCompiler ().registerAttributeSetRef (aEl, aName);
      }
    return ret;
  }

  @NonNull
  private static IInstruction _compileLiteralResultElement (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final StylesheetCompiler aCompiler = aCtx.getCompiler ();
    final XdmQName aSrcName = aEl.getName ();
    final StylesheetCompiler.NamespaceAlias aNameAlias = aCompiler.getNamespaceAlias (aSrcName.namespaceUri ());
    final XdmQName aName = aNameAlias == null ? aSrcName
                                              : new XdmQName (aNameAlias.resultUri (),
                                                              aSrcName.localName (),
                                                              aNameAlias.resultUri ().isEmpty () ? "" : aNameAlias.resultPrefix ());
    // Namespace nodes copied from the stylesheet
    final ICommonsSet <String> aExcluded = getExcludedNamespaces (aEl);
    final ICommonsOrderedMap <String, String> aNamespaces = new CommonsLinkedHashMap <> ();
    for (final Map.Entry <String, String> aEntry : aEl.getInScopeNamespaces ().entrySet ())
    {
      final String sPrefix = aEntry.getKey ();
      final String sUri = aEntry.getValue ();
      if ("xml".equals (sPrefix))
        continue;
      final StylesheetCompiler.NamespaceAlias aAlias = aCompiler.getNamespaceAlias (sUri);
      if (aAlias != null)
      {
        if (!aAlias.resultUri ().isEmpty ())
          aNamespaces.put (aAlias.resultPrefix (), aAlias.resultUri ());
        continue;
      }
      if (aExcluded.contains (sUri))
        continue;
      aNamespaces.put (sPrefix, sUri);
    }
    // Attributes
    final ICommonsList <XdmQName> aAttrNames = new CommonsArrayList <> ();
    final ICommonsList <Avt> aAttrValues = new CommonsArrayList <> ();
    for (final Map.Entry <XdmQName, String> aEntry : aEl.getAllAttributes ().entrySet ())
    {
      final XdmQName aAttrName = aEntry.getKey ();
      if (XsltNames.NS_XSL.equals (aAttrName.namespaceUri ()))
        continue;
      final StylesheetCompiler.NamespaceAlias aAlias = aAttrName.hasNamespace () ? aCompiler.getNamespaceAlias (aAttrName.namespaceUri ())
                                                                                 : null;
      aAttrNames.add (aAlias == null ? aAttrName
                                     : new XdmQName (aAlias.resultUri (),
                                                     aAttrName.localName (),
                                                     aAlias.resultUri ().isEmpty () ? "" : aAlias.resultPrefix ()));
      aAttrValues.add (aCtx.compileAvt (aEl, aEntry.getValue ()));
    }
    final ICommonsList <XdmQName> aSets = getUseAttributeSets (aCtx, aEl, true);
    final SequenceConstructor aBody = aCtx.compileSequenceConstructor (aEl, aEl.getChildren ());
    final String sBase = aEl.getStaticBaseUri ();
    final String sInherit = aEl.getAttribute (XsltNames.NS_XSL, "inherit-namespaces");
    final boolean bNoInherit = sInherit != null && !XsltNames.isXsltTrue (sInherit);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmNode aElement = XdmNode.createElement (aName);
      for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
        if (!aEntry.getKey ().equals (aName.prefix ()) || aEntry.getValue ().equals (aName.namespaceUri ()))
          aElement.declareNamespace (aEntry.getKey (), aEntry.getValue ());
      if (sBase != null)
        aElement.setBaseUri (sBase);
      final ContentOutput aContent = new ContentOutput (aElement);
      if (aSets.isNotEmpty ())
        aRtCtx.getEngine ().applyAttributeSets (aSets, aRtCtx, aContent);
      for (int i = 0; i < aAttrNames.size (); ++i)
        aContent.appendNew (XdmNode.createAttribute (aAttrNames.get (i), aAttrValues.get (i).evaluate (aRtCtx)));
      aBody.execute (aRtCtx, aContent);
      if (bNoInherit)
        NamespaceFixup.applyNoInherit (aContent.getParent ());
      aRtOut.appendNew (aContent.getParent ());
    });
  }

  /**
   * Compile an instruction element (see
   * {@link CompileContext#compileInstruction(XslElement)}).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction or <code>null</code>.
   */
  /**
   * @param aEl
   *        An instruction element.
   * @return <code>true</code> if it is evaluated in backwards compatible mode:
   *         the nearest [xsl:]version below the module's outermost element is
   *         less than 2.0. The version of the outermost element is ignored, so
   *         stylesheets written as version 1.0 run as 3.0.
   */
  private static boolean _isBackwardsCompatible (@NonNull final XslElement aEl)
  {
    for (XslElement aCur = aEl; aCur != null && aCur.getParent () != null; aCur = aCur.getParent ())
    {
      final String s = aCur.getStandardAttr ("version");
      if (s != null)
        return XsltNames.isDecimalLexical (s.trim ()) && new BigDecimal (s.trim ()).compareTo (BigDecimal.valueOf (2)) < 0;
    }
    return false;
  }

  @Nullable
  public static IInstruction compileInstruction (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    if (_isBackwardsCompatible (aEl))
      return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
        throw new XPathException ("XTDE0160", "Backwards compatible processing is not supported");
      });
    if (aEl.isXsl ())
    {
      final String sLocal = aEl.getLocalName ();
      final IInstructionCompiler aCompiler = aCtx.getCompiler ().getRegistry ().getInstructionCompiler (sLocal);
      if (aCompiler != null)
        return aCompiler.compile (aCtx, aEl);
      if (!XsltElementSpecs.isKnown (sLocal) && aEl.isForwardsCompatible ())
        return _compileFallback (aCtx, aEl, "The XSLT instruction");
      if (XsltElementSpecs.isKnown (sLocal) && aCtx.getCompiler ().getRegistry ().getDeclarationCompiler (sLocal) == null &&
          !aEl.getXslChildren ("fallback").isEmpty ())
        return _compileFallback (aCtx, aEl, "The XSLT instruction");
      throw XsltException.at (aEl,
                              "XTSE0010",
                              XsltElementSpecs.isKnown (sLocal) ? "xsl:" + sLocal + " is not allowed here or not supported"
                                                                : "xsl:" + sLocal + " is not an XSLT instruction");
    }
    if (getExtensionNamespaces (aEl).contains (aEl.getName ().namespaceUri ()))
      return _compileFallback (aCtx, aEl, "The extension instruction");
    return _compileLiteralResultElement (aCtx, aEl);
  }

  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly () ||
          ((XslText) aNode).isXmlSpacePreserve () && !((XslText) aNode).getValue ().isEmpty ())
        return true;
    return false;
  }

  /**
   * Compile a variable or parameter (see
   * {@link CompileContext#compileVariable(XslElement, VariableDef.EKind)}).
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @param eKind
   *        Kind.
   * @return The definition.
   */
  @NonNull
  public static VariableDef compileVariable (@NonNull final CompileContext aCtx,
                                             @NonNull final XslElement aEl,
                                             final VariableDef.@NonNull EKind eKind)
  {
    final String sName = aEl.getAttr ("name");
    if (sName == null)
      throw XsltException.at (aEl, "XTSE0010", "xsl:" + aEl.getLocalName () + " requires a name attribute");
    final XdmQName aName = aCtx.resolveQName (aEl, sName, false);
    final String sAs = aEl.getAttr ("as");
    final SeqType aAs = sAs == null ? null : aCtx.compileSequenceType (aEl, sAs);
    XPathExpr aSelect = null;
    SequenceConstructor aBody = null;
    final String sSelect = aEl.getAttr ("select");
    final boolean bContent = _hasContent (aEl);
    if (sSelect != null)
    {
      if (bContent)
        throw XsltException.at (aEl,
                                aEl.isXsl ("variable") ? "XTSE0620" : "XTSE0760",
                                "xsl:" + aEl.getLocalName () + " has both a select attribute and content");
      aSelect = aCtx.compileExpression (aEl, sSelect);
    }
    else
      if (bContent)
        aBody = aCtx.compileSequenceConstructor (aEl, aEl.getChildren ());
    final boolean bTunnel = XsltNames.isXsltTrue (aEl.getAttr ("tunnel"));
    boolean bRequired = false;
    if (aEl.isXsl ("param"))
    {
      bRequired = XsltNames.isXsltTrue (aEl.getAttr ("required"));
      if (!bRequired && aSelect == null && aBody == null && aAs != null && !SequenceTypeMatcher.matches (XdmSequence.EMPTY, aAs))
        bRequired = true;
    }
    return new VariableDef (aEl, eKind, aName, aSelect, aBody, aAs, bTunnel, bRequired);
  }
}
