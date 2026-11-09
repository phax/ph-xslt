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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.fn.FnAccessor;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.compile.Avt;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.XPathExpr;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.runtime.ContentOutput;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.NamespaceFixup;
import com.helger.phoxtrot.xslt.runtime.NodeCopier;
import com.helger.phoxtrot.xslt.runtime.SequenceOutput;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.stream.IStreamHandlerFactory;
import com.helger.phoxtrot.xslt.stream.StreamConsumer;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;
import com.helger.phoxtrot.xslt.tree.XslText;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * The node constructing instructions (XSLT 3.0 sections 11 and 12):
 * xsl:value-of, xsl:text, xsl:element, xsl:attribute, xsl:comment,
 * xsl:processing-instruction, xsl:namespace, xsl:document, xsl:copy and
 * xsl:copy-of.
 *
 * @author Philip Helger
 */
@Immutable
public final class NodeInstructions
{
  private static final String NS_XMLNS = XdmConstants.NS_XMLNS;

  @PresentForCodeCoverage
  private static final NodeInstructions INSTANCE = new NodeInstructions ();

  private NodeInstructions ()
  {}

  /**
   * The value of an instruction with select or content and a separator
   * (simple content).
   */
  @NonNull
  private static String _evaluateValue (@NonNull final XsltContext aCtx,
                                        @Nullable final XPathExpr aSelect,
                                        @Nullable final SequenceConstructor aBody,
                                        @Nullable final Avt aSeparator,
                                        final boolean bAlwaysSpace)
  {
    final String sSep = aSeparator != null ? aSeparator.evaluate (aCtx) : aSelect != null || bAlwaysSpace ? " " : "";
    final XdmSequence aValue;
    if (aSelect != null)
      aValue = aSelect.evaluate (aCtx);
    else
      if (aBody != null)
      {
        final SequenceOutput aOut = new SequenceOutput ();
        aBody.execute (aCtx, aOut);
        aValue = aOut.getSequence ();
      }
      else
        aValue = XdmSequence.EMPTY;
    return SimpleContent.join (aValue, sSep);
  }

  /**
   * xsl:value-of (XSLT 3.0 section 11.4.3): a new text node whose value is
   * the simple content (section 5.7.2) of the select value or else of the
   * result of the content. The default separator is a single space with a
   * select attribute and the zero-length string with content; the separator
   * attribute value template overrides both. The text node is created even if
   * its value is empty, and is marked for disabled output escaping if
   * disable-output-escaping is yes. Processed with XSLT 1.0 behaviour, a
   * select attribute without separator gives the string value of the first
   * atomized item only (section 11.4.3).
   */
  @NonNull
  private static IInstruction _compileValueOf (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    final Avt aSep = aCtx.compileOptionalAvt (aEl, "separator");
    final boolean bDoe = XsltNames.isXsltTrue (aEl.getAttr ("disable-output-escaping"));
    final boolean bFirstItemOnly = aSelect != null && aSep == null && aCtx.isXslt10Behaviour (aEl);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final String sValue = bFirstItemOnly ? SimpleContent.getFirstItemString (aSelect.evaluate (aRtCtx))
                                           : _evaluateValue (aRtCtx, aSelect, aBody, aSep, false);
      final IXdmNode aText = IXdmNode.createText (sValue);
      if (bDoe)
        aText.setDisableOutputEscaping (true);
      aRtOut.appendNew (aText);
    });
  }

  /**
   * xsl:text (XSLT 3.0 section 11.4.2): the concatenated text children of the
   * element, as one text node. With expand-text in effect the text is a text
   * value template; a constant one (and any text without expand-text) becomes
   * a {@link LiteralText}. disable-output-escaping marks the text node.
   */
  @NonNull
  private static IInstruction _compileText (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof final XslText aText)
        aSB.append (aText.getValue ());
    final String sText = aSB.toString ();
    final boolean bDoe = XsltNames.isXsltTrue (aEl.getAttr ("disable-output-escaping"));
    if (CompileContext.isExpandText (aEl))
    {
      final Avt aAvt = aCtx.compileTextValueTemplate (aEl, sText);
      if (!aAvt.isConstant ())
        return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
          final IXdmNode aText = IXdmNode.createText (aAvt.evaluate (aRtCtx));
          if (bDoe)
            aText.setDisableOutputEscaping (true);
          aRtOut.appendNew (aText);
        });
      return new LiteralText (aEl, aAvt.getConstantValue (), bDoe);
    }
    return new LiteralText (aEl, sText, bDoe);
  }

  /**
   * The name of a node built by xsl:element (<code>bElement</code>) or
   * xsl:attribute, from the effective values of the name and namespace
   * attributes (XSLT 3.0 sections 11.2.2 and 11.3):
   * <ul>
   * <li>The name, trimmed of XML whitespace, must be an EQName (lexical QName
   * or <code>Q{uri}local</code>), otherwise XTDE0820 / XTDE0850.</li>
   * <li>With a namespace attribute its value is the namespace URI (also for a
   * <code>Q{}</code> name); the prefix of the name is kept unless the URI is
   * empty or the prefix is xmlns. The XMLNS namespace is XTDE0835 /
   * XTDE0865.</li>
   * <li>Without a namespace attribute a <code>Q{}</code> name gives the URI
   * directly. Otherwise a prefix is resolved against the in-scope namespaces
   * of the instruction (xml always to the XML namespace; unbound is XTDE0830 /
   * XTDE0860); an unprefixed element name takes the default namespace of the
   * instruction, an unprefixed attribute name no namespace, and the
   * attribute name xmlns is XTDE0855.</li>
   * <li>An attribute prefix xmlns is XTDE0860. A prefix that does not fit the
   * URI with respect to the XML namespace is replaced (by xml for the XML
   * namespace, else dropped), and the XML namespace always gets the prefix
   * xml. A resulting XMLNS namespace is XTDE0835 / XTDE0865.</li>
   * </ul>
   * A dropped or missing prefix of a name in a namespace is chosen later by
   * the namespace fixup.
   *
   * @param aCtx
   *        Run-time context for the attribute value templates.
   * @param aEl
   *        The instruction, for the in-scope namespaces.
   * @param aName
   *        The name attribute value template.
   * @param aNamespace
   *        The namespace attribute value template, or <code>null</code>.
   * @param bElement
   *        <code>true</code> for xsl:element, <code>false</code> for
   *        xsl:attribute (selects the error codes and the default namespace
   *        rule).
   * @return The name of the new node.
   */
  @NonNull
  private static XdmQName _computeName (@NonNull final XsltContext aCtx,
                                        @NonNull final XslElement aEl,
                                        @NonNull final Avt aName,
                                        @Nullable final Avt aNamespace,
                                        final boolean bElement)
  {
    final String sName = XsltNames.trimXmlWhitespace (aName.evaluate (aCtx));
    final String sNameCode = bElement ? "XTDE0820" : "XTDE0850";
    if (!XsltNames.isEQName (sName))
      throw new XPathException (sNameCode, "'" + sName + "' is not a valid " + (bElement ? "element" : "attribute") + " name");
    String sPrefix = "";
    String sLocal = sName;
    String sUri = null;
    if (sName.startsWith ("Q{"))
    {
      final int n = sName.indexOf ('}');
      sUri = sName.substring (2, n);
      sLocal = sName.substring (n + 1);
    }
    else
    {
      final int n = sName.indexOf (':');
      if (n > 0)
      {
        sPrefix = sName.substring (0, n);
        sLocal = sName.substring (n + 1);
      }
    }
    if (aNamespace != null)
    {
      sUri = aNamespace.evaluate (aCtx);
      if (NS_XMLNS.equals (sUri))
        throw new XPathException (bElement ? "XTDE0835" : "XTDE0865", "The namespace '" + sUri + "' is reserved");
      if (sUri.isEmpty ())
        sPrefix = "";
      else
        if ("xmlns".equals (sPrefix))
          sPrefix = "";
    }
    else
      if (sUri == null)
      {
        if (!bElement && sPrefix.isEmpty () && "xmlns".equals (sLocal))
          throw new XPathException ("XTDE0855", "An attribute cannot be named xmlns");
        if (sPrefix.isEmpty ())
          sUri = bElement && aEl.lookupNamespaceUri ("") != null ? aEl.lookupNamespaceUri ("") : "";
        else
        {
          sUri = "xml".equals (sPrefix) ? XdmConstants.NS_XML : aEl.lookupNamespaceUri (sPrefix);
          if (sUri == null)
            throw new XPathException (bElement ? "XTDE0830" : "XTDE0860", "The prefix '" + sPrefix + "' is not bound");
        }
      }
    if (!bElement && "xmlns".equals (sPrefix))
      throw new XPathException ("XTDE0860", "The prefix xmlns cannot be used for an attribute");
    if (XdmConstants.NS_XML.equals (sUri) != "xml".equals (sPrefix) && !sPrefix.isEmpty ())
      sPrefix = XdmConstants.NS_XML.equals (sUri) ? "xml" : "";
    if (XdmConstants.NS_XML.equals (sUri))
      sPrefix = "xml";
    if (NS_XMLNS.equals (sUri))
      throw new XPathException (bElement ? "XTDE0835" : "XTDE0865", "The namespace '" + sUri + "' is reserved");
    return new XdmQName (sUri, sLocal, sPrefix);
  }

  /**
   * xsl:element (XSLT 3.0 section 11.2): a new element with the computed name
   * (see {@link #_computeName}); no namespaces of the stylesheet are copied.
   * The attribute sets are applied first, then the content runs into the
   * element; with inherit-namespaces="no" the namespaces are undeclared on
   * the children, and a validation request is applied last. The element is
   * appended to the output when complete, and written while it is built only
   * if neither of these two changes it afterwards (see
   * {@link ContentOutput#createFor}).
   */
  @NonNull
  private static IInstruction _compileElement (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final Avt aNamespace = aCtx.compileOptionalAvt (aEl, "namespace");
    final ICommonsList <XdmQName> aSets = CoreInstructions.getUseAttributeSets (aCtx, aEl, false);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final boolean bNoInherit = aEl.hasAttr ("inherit-namespaces") && !XsltNames.isXsltTrue (aEl.getAttr ("inherit-namespaces"));
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.ELEMENT);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmQName aQName = _computeName (aRtCtx, aEl, aName, aNamespace, true);
      final IXdmNode aElement = IXdmNode.createElement (aQName);
      final ContentOutput aContent = ContentOutput.createFor (aElement, aRtOut, !bNoInherit && aValidation == null);
      if (aSets.isNotEmpty ())
        aRtCtx.getEngine ().applyAttributeSets (aSets, aRtCtx, aContent);
      aBody.execute (aRtCtx, aContent);
      if (bNoInherit)
        NamespaceFixup.applyNoInherit (aContent.getParent ());
      if (aValidation != null)
        aValidation.apply (aContent.getParent ());
      aRtOut.appendNew (aContent.getParent ());
    });
  }

  /**
   * xsl:attribute (XSLT 3.0 section 11.3): a new attribute with the computed
   * name (see {@link #_computeName}; the name is computed before the value)
   * and the simple content of the select value or the content as value
   * (default separator a single space with select, zero-length otherwise). A
   * validation request other than strip is applied to the new attribute.
   */
  @NonNull
  private static IInstruction _compileAttribute (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final Avt aNamespace = aCtx.compileOptionalAvt (aEl, "namespace");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    final Avt aSep = aCtx.compileOptionalAvt (aEl, "separator");
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.ATTRIBUTE);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final XdmQName aQName = _computeName (aRtCtx, aEl, aName, aNamespace, false);
      final String sValue = _evaluateValue (aRtCtx, aSelect, aBody, aSep, false);
      final IXdmNode aAttr = IXdmNode.createAttribute (aQName, sValue);
      if (aValidation != null && !aValidation.isStrip ())
        aValidation.apply (aAttr);
      aRtOut.appendNew (aAttr);
    });
  }

  /**
   * xsl:comment (XSLT 3.0 section 11.8): a new comment whose value is the
   * simple content of the select value or the content, with a single space as
   * separator. A space is inserted after every hyphen that is followed by
   * another hyphen or ends the value, so the comment can be serialised.
   */
  @NonNull
  private static IInstruction _compileComment (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      String s = _evaluateValue (aRtCtx, aSelect, aBody, null, true);
      while (s.contains ("--"))
        s = s.replace ("--", "- -");
      if (s.endsWith ("-"))
        s = s + " ";
      aRtOut.appendNew (IXdmNode.createComment (s));
    });
  }

  /**
   * xsl:processing-instruction (XSLT 3.0 section 11.6): the name, trimmed of
   * XML whitespace, must be an NCName other than xml in any case (XTDE0890).
   * The value is the simple content of the select value or the content, with
   * a single space as separator; leading whitespace is removed and a space is
   * inserted between the characters of every <code>?&gt;</code>. The base
   * URI of the instruction is not set on the new node.
   */
  @NonNull
  private static IInstruction _compileProcessingInstruction (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final String sName = XsltNames.trimXmlWhitespace (aName.evaluate (aRtCtx));
      if (!XsltNames.isNCName (sName) || "xml".equalsIgnoreCase (sName))
        throw new XPathException ("XTDE0890", "'" + sName + "' is not a valid processing instruction name");
      String s = _evaluateValue (aRtCtx, aSelect, aBody, null, true);
      int nStart = 0;
      while (nStart < s.length () && " \t\r\n".indexOf (s.charAt (nStart)) >= 0)
        nStart++;
      s = s.substring (nStart);
      while (s.contains ("?>"))
        s = s.replace ("?>", "? >");
      aRtOut.appendNew (IXdmNode.createProcessingInstruction (sName, s));
    });
  }

  /**
   * xsl:namespace (XSLT 3.0 section 11.7): a new namespace node. The prefix
   * is the name trimmed of XML whitespace: empty for the default namespace,
   * otherwise an NCName other than xmlns (XTDE0920). The URI is the simple
   * content of the select value or the content, with a single space as
   * separator; it must not be empty (XTDE0930) or the XMLNS namespace
   * (XTDE0905), and the prefix xml and the XML namespace must go together
   * (XTDE0925).
   */
  @NonNull
  private static IInstruction _compileNamespace (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final String sPrefix = XsltNames.trimXmlWhitespace (aName.evaluate (aRtCtx));
      if ((!sPrefix.isEmpty () && !XsltNames.isNCName (sPrefix)) || "xmlns".equals (sPrefix))
        throw new XPathException ("XTDE0920", "'" + sPrefix + "' is not a valid namespace prefix");
      final String sUri = _evaluateValue (aRtCtx, aSelect, aBody, null, true);
      if (sUri.isEmpty ())
        throw new XPathException ("XTDE0930", "The namespace URI of xsl:namespace is empty");
      if (NS_XMLNS.equals (sUri))
        throw new XPathException ("XTDE0905", "The namespace '" + sUri + "' is reserved");
      if ("xml".equals (sPrefix) != XdmConstants.NS_XML.equals (sUri))
        throw new XPathException ("XTDE0925", "The xml prefix and the XML namespace must be used together");
      aRtOut.appendNew (IXdmNode.createNamespace (sPrefix, sUri));
    });
  }

  /**
   * xsl:document (XSLT 3.0 section 11.5): a new document node with the static
   * base URI of the instruction, filled by the content; a validation request
   * is applied to it before it is appended to the output.
   */
  @NonNull
  private static IInstruction _compileDocument (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final String sBase = aEl.getStaticBaseUri ();
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.DOCUMENT);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final IXdmNode aDoc = IXdmNode.createDocument ();
      if (sBase != null)
        aDoc.setBaseUri (sBase);
      aBody.execute (aRtCtx, new ContentOutput (aDoc));
      if (aValidation != null)
        aValidation.apply (aDoc);
      aRtOut.appendNew (aDoc);
    });
  }

  /**
   * xsl:copy (XSLT 3.0 section 11.9.1), a shallow copy of the item selected by
   * the select expression (which becomes the focus for the content) or else
   * of the context item:
   * <ul>
   * <li>An empty select value produces nothing; more than one item is
   * XTTE3180, an absent context item without select XTTE0945.</li>
   * <li>An atomic value or function item is appended unchanged; the content
   * is not evaluated.</li>
   * <li>A document node: the content runs straight into the output if the
   * output only needs the content (see
   * {@link IXsltOutput#getDocumentContentOutput()}); otherwise a new document
   * node gets the unparsed entities and base URI of the original, the
   * content, and the validation request, and is appended.</li>
   * <li>An element: a shallow copy with the base URI of the original and, for
   * copy-namespaces="yes" (the default), its in-scope namespaces; then as for
   * xsl:element the attribute sets, the content, inherit-namespaces="no" and
   * the validation request.</li>
   * <li>Any other node is copied without evaluating the content or the
   * attribute sets; a validation request other than strip is applied (with
   * the XTTE0950 check for preserve and, for an attribute, its type
   * annotation kept).</li>
   * </ul>
   */
  @NonNull
  private static IInstruction _compileCopy (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") || XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    final ICommonsList <XdmQName> aSets = CoreInstructions.getUseAttributeSets (aCtx, aEl, false);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final boolean bNoInherit = aEl.hasAttr ("inherit-namespaces") && !XsltNames.isXsltTrue (aEl.getAttr ("inherit-namespaces"));
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.COPY);
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      XsltContext aCtx2 = aRtCtx;
      final IXdmItem aItem;
      if (aSelect != null)
      {
        final XdmSequence aSeq = aSelect.evaluate (aRtCtx);
        if (aSeq.isEmpty ())
          return;
        if (aSeq.size () > 1)
          throw new XPathException ("XTTE3180", "The select expression of xsl:copy returns more than one item");
        aItem = aSeq.get (0);
        aCtx2 = aRtCtx.withFocus (aItem, 1, 1);
      }
      else
      {
        aItem = aRtCtx.getContextItem ();
        if (aItem == null)
          throw new XPathException ("XTTE0945", "xsl:copy is used while the context item is absent");
      }
      if (!(aItem instanceof final IXdmNode aNode))
      {
        aRtOut.append (aItem);
        return;
      }
      switch (aNode.getKind ())
      {
        case DOCUMENT:
        {
          final IXsltOutput aDocOut = aRtOut.getDocumentContentOutput ();
          if (aDocOut != null)
            aBody.execute (aCtx2, aDocOut);
          else
          {
            final IXdmNode aDoc = IXdmNode.createDocument ();
            NodeCopier.copyUnparsedEntities (aNode, aDoc);
            // The copy keeps the base URI of the original (base-uri-053)
            final String sDocBase = FnAccessor.getBaseUri (aNode);
            if (sDocBase != null)
              aDoc.setBaseUri (sDocBase);
            aBody.execute (aCtx2, new ContentOutput (aDoc));
            if (aValidation != null)
              aValidation.apply (aDoc);
            aRtOut.appendNew (aDoc);
          }
          break;
        }
        case ELEMENT:
        {
          final IXdmNode aCopy = NodeCopier.copyShallow (aNode);
          aCopy.setBaseUri (FnAccessor.getBaseUri (aNode));
          if (bCopyNamespaces)
            NodeCopier.copyNamespaces (aNode, aCopy, true);
          final ContentOutput aContent = ContentOutput.createFor (aCopy, aRtOut, !bNoInherit && aValidation == null);
          if (aSets.isNotEmpty ())
            aCtx2.getEngine ().applyAttributeSets (aSets, aCtx2, aContent);
          aBody.execute (aCtx2, aContent);
          if (bNoInherit)
            NamespaceFixup.applyNoInherit (aContent.getParent ());
          if (aValidation != null)
            aValidation.apply (aContent.getParent ());
          aRtOut.appendNew (aContent.getParent ());
          break;
        }
        default:
        {
          final IXdmNode aCopy = NodeCopier.copyShallow (aNode);
          if (aValidation != null && !aValidation.isStrip ())
          {
            if (aValidation.isPreserve ())
              ValidationRequest.checkPreserveCopy (aNode, bCopyNamespaces);
            if (aNode.isAttribute ())
              NodeCopier.copyAnnotation (aNode, aCopy);
            aValidation.apply (aCopy);
          }
          aRtOut.appendNew (aCopy);
          break;
        }
      }
    });
  }

  /**
   * xsl:copy-of (XSLT 3.0 section 11.9.2): every node of the select value is
   * deep-copied (with its namespaces unless copy-namespaces="no"), every
   * other item is appended unchanged. A copied element without a base URI
   * gets the static base URI of the instruction. With a validation request
   * the copies keep their type annotations until the request is applied to
   * each copy (for preserve after the XTTE0950 check). Without validation the
   * instruction can be the consumer of a streamed run: then every streamed
   * record is copied as it arrives (always without type annotations).
   */
  @NonNull
  private static IInstruction _compileCopyOf (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") || XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    final String sStaticBase = aEl.getStaticBaseUri ();
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.COPY_OF);
    // Schema-aware: copies keep their annotations (and schema ID properties)
    // until the request is applied; strip keeps the ID properties
    final boolean bKeepAnnotations = aValidation != null;
    // Over the records of a streamed document, if it is the consumer
    final IStreamHandlerFactory aFactory = (aRtCtx, aRtOut, aRun) -> (aRecord, nPosition) -> {
      final IXdmNode aCopy = NodeCopier.copyDeep (aRecord, bCopyNamespaces, false);
      if (aCopy.isElement () && aCopy.getBaseUri () == null)
        aCopy.setBaseUri (sStaticBase);
      aRtOut.appendNew (aCopy);
    };
    final StreamConsumer aStreamed = aValidation == null ? StreamConsumer.create (aCtx, aEl, aSelect, aFactory) : null;
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      if (aStreamed != null && aStreamed.drive (aRtCtx, aRtOut))
        return;
      for (final IXdmItem aItem : aSelect.evaluate (aRtCtx))
        if (aItem instanceof final IXdmNode aNode)
        {
          if (bKeepAnnotations && aValidation.isPreserve ())
            ValidationRequest.checkPreserveCopy (aNode, bCopyNamespaces);
          final IXdmNode aCopy = NodeCopier.copyDeep (aNode, bCopyNamespaces, bKeepAnnotations);
          if (aCopy.isElement () && aCopy.getBaseUri () == null)
            aCopy.setBaseUri (sStaticBase);
          if (bKeepAnnotations)
            aValidation.apply (aCopy);
          aRtOut.appendNew (aCopy);
        }
        else
          aRtOut.append (aItem);
    });
  }

  /**
   * @param aRegistry
   *        Registry to register the node constructing instructions in.
   */
  static void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("value-of", NodeInstructions::_compileValueOf);
    aRegistry.registerInstruction ("text", NodeInstructions::_compileText);
    aRegistry.registerInstruction ("element", NodeInstructions::_compileElement);
    aRegistry.registerInstruction ("attribute", NodeInstructions::_compileAttribute);
    aRegistry.registerInstruction ("comment", NodeInstructions::_compileComment);
    aRegistry.registerInstruction ("processing-instruction", NodeInstructions::_compileProcessingInstruction);
    aRegistry.registerInstruction ("namespace", NodeInstructions::_compileNamespace);
    aRegistry.registerInstruction ("document", NodeInstructions::_compileDocument);
    aRegistry.registerInstruction ("copy", NodeInstructions::_compileCopy);
    aRegistry.registerInstruction ("copy-of", NodeInstructions::_compileCopyOf);
  }
}
