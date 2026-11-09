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
package com.helger.phoxtrot.xslt.instr;

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
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.compile.Avt;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IInstructionCompiler;
import com.helger.phoxtrot.xslt.compile.StylesheetCompiler;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.compile.XsltElementSpecs;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.model.VariableDef;
import com.helger.phoxtrot.xslt.runtime.ContentOutput;
import com.helger.phoxtrot.xslt.runtime.NamespaceFixup;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XslText;
import com.helger.phoxtrot.xslt.tree.XsltNames;

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
            aInstrs.add (new CompiledInstruction (aParent, (aRtCtx, aRtOut) -> aRtOut.appendNew (IXdmNode.createText (aAvt.evaluate (aRtCtx)))));
        }
        else
          aInstrs.add (new LiteralText (aText, aText.getValue ()));
        continue;
      }
      final XslElement aEl = (XslElement) aNode;
      if (aEl.isXsl ("variable") || aEl.isXsl ("param"))
      {
        final VariableDef aDef = aCur.compileVariable (aEl, VariableDef.EKind.LOCAL_VARIABLE);
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

  /**
   * Compile the fallback behaviour of an instruction this processor does not
   * implement (XSLT 3.0 section 24.2.3). With xsl:fallback children the
   * result runs the content of each of them in order; the instruction itself
   * is never evaluated. Without any, the result raises XTDE1450 when (and
   * only when) it is executed.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The unavailable instruction.
   * @param sWhat
   *        The description of the instruction for the error message, such as
   *        "The extension instruction".
   * @return The fallback instruction.
   */
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
        final XdmQName aName = aCtx.getCompiler ().mapAttributeSetName (aEl, aCtx.resolveQName (aEl, sToken, false));
        ret.add (aName);
        aCtx.getCompiler ().registerAttributeSetRef (aEl, aName);
      }
    return ret;
  }

  /**
   * Compile a literal result element (XSLT 3.0 section 11.1). At compile
   * time:
   * <ul>
   * <li>The element name is mapped by xsl:namespace-alias (section 11.1.4);
   * an alias to the null namespace drops the prefix.</li>
   * <li>The namespace nodes to copy are the in-scope namespaces of the
   * stylesheet element (section 11.1.3) except the xml namespace and the
   * excluded namespaces (see {@link #getExcludedNamespaces(XslElement)}). A
   * namespace that is the source of an alias is replaced by the result
   * namespace of the alias under the result prefix (nothing for an alias to
   * the null namespace); a namespace that is the result of an alias is kept
   * even if it is excluded.</li>
   * <li>Attributes in the XSLT namespace are dropped, the others are
   * compiled as attribute value templates, with their names mapped by
   * namespace aliases like the element name (section 11.1.2).</li>
   * <li>xsl:use-attribute-sets, the content, the static base URI,
   * xsl:inherit-namespaces and the validation request are compiled.</li>
   * </ul>
   * At run time a new element is created and gets the copied namespaces
   * (except a binding of the element's own prefix to another URI, which would
   * clash with its name) and the static base URI. Then, in this order, the
   * attribute sets are applied, the literal attributes added, and the content
   * executed into it, so that later attributes replace earlier ones of the
   * same name. With xsl:inherit-namespaces="no" the namespaces are then
   * undeclared on the children, and a validation request is applied last.
   * The finished element is appended to the output; it is written while it is
   * built (see {@link ContentOutput#createFor}) only if neither of these two
   * changes it afterwards.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The literal result element.
   * @return The instruction.
   */
  @NonNull
  private static IInstruction _compileLiteralResultElement (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final StylesheetCompiler aCompiler = aCtx.getCompiler ();
    final XdmQName aSrcName = aEl.getName ();
    final StylesheetCompiler.NamespaceAlias aNameAlias = aCompiler.getNamespaceAlias (aEl, aSrcName.namespaceUri ());
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
      final StylesheetCompiler.NamespaceAlias aAlias = aCompiler.getNamespaceAlias (aEl, sUri);
      if (aAlias != null)
      {
        if (!aAlias.resultUri ().isEmpty ())
          aNamespaces.put (aAlias.resultPrefix (), aAlias.resultUri ());
        continue;
      }
      // The result namespace of an xsl:namespace-alias is not excluded
      if (aExcluded.contains (sUri) && !aCompiler.isNamespaceAliasResult (aEl, sUri))
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
      final StylesheetCompiler.NamespaceAlias aAlias = aAttrName.hasNamespace () ? aCompiler.getNamespaceAlias (aEl, aAttrName.namespaceUri ())
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
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.ELEMENT);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final IXdmNode aElement = IXdmNode.createElement (aName);
      for (final Map.Entry <String, String> aEntry : aNamespaces.entrySet ())
        if (!aEntry.getKey ().equals (aName.prefix ()) || aEntry.getValue ().equals (aName.namespaceUri ()))
          aElement.declareNamespace (aEntry.getKey (), aEntry.getValue ());
      if (sBase != null)
        aElement.setBaseUri (sBase);
      final ContentOutput aContent = ContentOutput.createFor (aElement, aRtOut, !bNoInherit && aValidation == null);
      if (aSets.isNotEmpty ())
        aRtCtx.getEngine ().applyAttributeSets (aSets, aRtCtx, aContent);
      for (int i = 0; i < aAttrNames.size (); ++i)
        aContent.appendNew (IXdmNode.createAttribute (aAttrNames.get (i), aAttrValues.get (i).evaluate (aRtCtx)));
      aBody.execute (aRtCtx, aContent);
      if (bNoInherit)
        NamespaceFixup.applyNoInherit (aContent.getParent ());
      if (aValidation != null)
        aValidation.apply (aContent.getParent ());
      aRtOut.appendNew (aContent.getParent ());
    });
  }

  /**
   * Compile an instruction element (see
   * {@link CompileContext#compileInstruction(XslElement)}), whatever its
   * effective version. In this order:
   * <ul>
   * <li>An XSLT element is compiled by the instruction compiler registered for
   * its local name. Without one, its xsl:fallback children are used if it is
   * an unknown element in forwards compatible mode (section 3.10) or a known
   * XSLT element that is no declaration (an instruction this processor does
   * not implement); otherwise it is the static error XTSE0010.</li>
   * <li>An element in an extension namespace (extension-element-prefixes on it
   * or an ancestor) compiles to its xsl:fallback children (section 24.2.3),
   * since no extension instructions are implemented.</li>
   * <li>Every other element is a literal result element.</li>
   * </ul>
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction or <code>null</code> if the registered compiler
   *         produces none.
   * @throws XsltException
   *         XTSE0010 for an XSLT element that is not allowed as an
   *         instruction and has no usable fallback, and any static error of
   *         the compiled content.
   */
  @Nullable
  private static IInstruction _compileInstruction (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    if (aEl.isXsl ())
    {
      final String sLocal = aEl.getLocalName ();
      final IInstructionCompiler aCompiler = aCtx.getCompiler ().getRegistry ().getInstructionCompiler (sLocal);
      if (aCompiler != null)
        return aCompiler.compile (aCtx, aEl);
      // XSLT 3.0 section 3.10: without xsl:fallback children an unknown
      // instruction is a static error even in forwards compatible mode
      if (!XsltElementSpecs.isKnown (sLocal) && aEl.isForwardsCompatible () && !aEl.getXslChildren ("fallback").isEmpty ())
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

  /**
   * Compile an instruction element (see
   * {@link CompileContext#compileInstruction(XslElement)}) as
   * {@link #_compileInstruction(CompileContext, XslElement)} does, so that the
   * static errors of its content are reported. An instruction with an
   * effective version below 2.0 (XSLT 3.0 section 3.9) that is compiled
   * without the backwards compatibility claim is then replaced by an
   * instruction that raises the dynamic error XTDE0160 when it is evaluated
   * (sections 3.9 and 27.4: a processor without the XSLT 1.0 compatibility
   * feature raises it only for an instruction that is actually evaluated).
   * With the claim the instruction is processed with XSLT 1.0 behaviour.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The element.
   * @return The instruction or <code>null</code> if the registered compiler
   *         produces none and no XTDE0160 is due.
   * @throws XsltException
   *         XTSE0010 for an XSLT element that is not allowed as an
   *         instruction and has no usable fallback, and any static error of
   *         the compiled content.
   */
  @Nullable
  public static IInstruction compileInstruction (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final IInstruction ret = _compileInstruction (aCtx, aEl);
    if (aEl.isXslt10Version () && !aCtx.getCompiler ().getOptions ().isBackwardsCompatibleClaim ())
      return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
        throw new XPathException ("XTDE0160",
                                  "The effective version " +
                                              aEl.getEffectiveVersion ().toPlainString () +
                                              " requests backwards compatible behaviour, which is not claimed (XsltCompileOptions.setBackwardsCompatibleClaim)");
      });
    return ret;
  }

  /**
   * @param aEl
   *        A variable or parameter element.
   * @return <code>true</code> if it has content for the purpose of the
   *         "select and content" check: an element child, a text child that
   *         is not whitespace-only, or a non-empty whitespace text child under
   *         xml:space="preserve". Whitespace that is stripped from the
   *         stylesheet does not count.
   */
  private static boolean _hasContent (@NonNull final XslElement aEl)
  {
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof XslElement || !((XslText) aNode).isWhitespaceOnly () ||
          (((XslText) aNode).isXmlSpacePreserve () && !((XslText) aNode).getValue ().isEmpty ()))
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
        throw XsltException.at (aEl, "XTSE0620", "xsl:" + aEl.getLocalName () + " has both a select attribute and content");
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
