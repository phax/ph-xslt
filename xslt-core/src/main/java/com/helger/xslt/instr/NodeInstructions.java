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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.XPathExpr;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.runtime.ContentOutput;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.NamespaceFixup;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.runtime.SequenceOutput;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;
import com.helger.xslt.tree.XslText;
import com.helger.xslt.tree.XsltNames;

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

  @NonNull
  private static IInstruction _compileValueOf (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    final Avt aSep = aCtx.compileOptionalAvt (aEl, "separator");
    return new CompiledInstruction (aEl, (c, o) -> o.appendNew (XdmNode.createText (_evaluateValue (c, aSelect, aBody, aSep, false))));
  }

  @NonNull
  private static IInstruction _compileText (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final XslNode aNode : aEl.getChildren ())
      if (aNode instanceof final XslText aText)
        aSB.append (aText.getValue ());
    final String sText = aSB.toString ();
    if (CompileContext.isExpandText (aEl))
    {
      final Avt aAvt = aCtx.compileTextValueTemplate (aEl, sText);
      if (!aAvt.isConstant ())
        return new CompiledInstruction (aEl, (c, o) -> o.appendNew (XdmNode.createText (aAvt.evaluate (c))));
      return new LiteralText (aEl, aAvt.getConstantValue ());
    }
    return new LiteralText (aEl, sText);
  }

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

  @NonNull
  private static IInstruction _compileElement (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final Avt aNamespace = aCtx.compileOptionalAvt (aEl, "namespace");
    final ICommonsList <XdmQName> aSets = CoreInstructions.getUseAttributeSets (aCtx, aEl, false);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final boolean bNoInherit = aEl.hasAttr ("inherit-namespaces") && !XsltNames.isXsltTrue (aEl.getAttr ("inherit-namespaces"));
    return new CompiledInstruction (aEl, (c, o) -> {
      final XdmQName aQName = _computeName (c, aEl, aName, aNamespace, true);
      final XdmNode aElement = XdmNode.createElement (aQName);
      final ContentOutput aContent = new ContentOutput (aElement);
      if (aSets.isNotEmpty ())
        c.getEngine ().applyAttributeSets (aSets, c, aContent);
      aBody.execute (c, aContent);
      if (bNoInherit)
        NamespaceFixup.applyNoInherit (aContent.getParent ());
      o.appendNew (aContent.getParent ());
    });
  }

  @NonNull
  private static IInstruction _compileAttribute (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final Avt aNamespace = aCtx.compileOptionalAvt (aEl, "namespace");
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    final Avt aSep = aCtx.compileOptionalAvt (aEl, "separator");
    return new CompiledInstruction (aEl, (c, o) -> {
      final XdmQName aQName = _computeName (c, aEl, aName, aNamespace, false);
      final String sValue = _evaluateValue (c, aSelect, aBody, aSep, false);
      o.appendNew (XdmNode.createAttribute (aQName, sValue));
    });
  }

  @NonNull
  private static IInstruction _compileComment (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (c, o) -> {
      String s = _evaluateValue (c, aSelect, aBody, null, true);
      while (s.contains ("--"))
        s = s.replace ("--", "- -");
      if (s.endsWith ("-"))
        s = s + " ";
      o.appendNew (XdmNode.createComment (s));
    });
  }

  @NonNull
  private static IInstruction _compileProcessingInstruction (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (c, o) -> {
      final String sName = XsltNames.trimXmlWhitespace (aName.evaluate (c));
      if (!XsltNames.isNCName (sName) || "xml".equalsIgnoreCase (sName))
        throw new XPathException ("XTDE0890", "'" + sName + "' is not a valid processing instruction name");
      String s = _evaluateValue (c, aSelect, aBody, null, true);
      int nStart = 0;
      while (nStart < s.length () && " \t\r\n".indexOf (s.charAt (nStart)) >= 0)
        nStart++;
      s = s.substring (nStart);
      while (s.contains ("?>"))
        s = s.replace ("?>", "? >");
      o.appendNew (XdmNode.createProcessingInstruction (sName, s));
    });
  }

  @NonNull
  private static IInstruction _compileNamespace (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aName = aCtx.compileAvt (aEl, aEl.getAttr ("name"));
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final SequenceConstructor aBody = aSelect == null ? aCtx.compileContent (aEl) : null;
    return new CompiledInstruction (aEl, (c, o) -> {
      final String sPrefix = XsltNames.trimXmlWhitespace (aName.evaluate (c));
      if (!sPrefix.isEmpty () && !XsltNames.isNCName (sPrefix) || "xmlns".equals (sPrefix))
        throw new XPathException ("XTDE0920", "'" + sPrefix + "' is not a valid namespace prefix");
      final String sUri = _evaluateValue (c, aSelect, aBody, null, true);
      if (sUri.isEmpty ())
        throw new XPathException ("XTDE0930", "The namespace URI of xsl:namespace is empty");
      if (NS_XMLNS.equals (sUri))
        throw new XPathException ("XTDE0905", "The namespace '" + sUri + "' is reserved");
      if ("xml".equals (sPrefix) != XdmConstants.NS_XML.equals (sUri))
        throw new XPathException ("XTDE0925", "The xml prefix and the XML namespace must be used together");
      o.appendNew (XdmNode.createNamespace (sPrefix, sUri));
    });
  }

  @NonNull
  private static IInstruction _compileDocument (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final String sBase = aEl.getStaticBaseUri ();
    return new CompiledInstruction (aEl, (c, o) -> {
      final XdmNode aDoc = XdmNode.createDocument ();
      if (sBase != null)
        aDoc.setBaseUri (sBase);
      aBody.execute (c, new ContentOutput (aDoc));
      o.appendNew (aDoc);
    });
  }

  @NonNull
  private static IInstruction _compileCopy (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileOptionalExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") || XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    final ICommonsList <XdmQName> aSets = CoreInstructions.getUseAttributeSets (aCtx, aEl, false);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final boolean bNoInherit = aEl.hasAttr ("inherit-namespaces") && !XsltNames.isXsltTrue (aEl.getAttr ("inherit-namespaces"));
    return new CompiledInstruction (aEl, (c, o) -> {
      XsltContext aCtx2 = c;
      final IXdmItem aItem;
      if (aSelect != null)
      {
        final XdmSequence aSeq = aSelect.evaluate (c);
        if (aSeq.isEmpty ())
          return;
        if (aSeq.size () > 1)
          throw new XPathException ("XTTE3180", "The select expression of xsl:copy returns more than one item");
        aItem = aSeq.get (0);
        aCtx2 = c.withFocus (aItem, 1, 1);
      }
      else
      {
        aItem = c.getContextItem ();
        if (aItem == null)
          throw new XPathException ("XTTE0945", "xsl:copy is used while the context item is absent");
      }
      if (!(aItem instanceof final XdmNode aNode))
      {
        o.append (aItem);
        return;
      }
      switch (aNode.getKind ())
      {
        case DOCUMENT:
        {
          final IXsltOutput aDocOut = o.getDocumentContentOutput ();
          if (aDocOut != null)
            aBody.execute (aCtx2, aDocOut);
          else
          {
            final XdmNode aDoc = XdmNode.createDocument ();
            aBody.execute (aCtx2, new ContentOutput (aDoc));
            o.appendNew (aDoc);
          }
          break;
        }
        case ELEMENT:
        {
          final XdmNode aCopy = NodeCopier.copyShallow (aNode);
          if (bCopyNamespaces)
            NodeCopier.copyNamespaces (aNode, aCopy, true);
          final ContentOutput aContent = new ContentOutput (aCopy);
          if (aSets.isNotEmpty ())
            aCtx2.getEngine ().applyAttributeSets (aSets, aCtx2, aContent);
          aBody.execute (aCtx2, aContent);
          if (bNoInherit)
            NamespaceFixup.applyNoInherit (aContent.getParent ());
          o.appendNew (aContent.getParent ());
          break;
        }
        default:
          o.appendNew (NodeCopier.copyShallow (aNode));
          break;
      }
    });
  }

  @NonNull
  private static IInstruction _compileCopyOf (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final XPathExpr aSelect = aCtx.compileRequiredExpression (aEl, "select");
    final boolean bCopyNamespaces = !aEl.hasAttr ("copy-namespaces") || XsltNames.isXsltTrue (aEl.getAttr ("copy-namespaces"));
    return new CompiledInstruction (aEl, (c, o) -> {
      for (final IXdmItem aItem : aSelect.evaluate (c))
        if (aItem instanceof final XdmNode aNode)
          o.appendNew (NodeCopier.copyDeep (aNode, bCopyNamespaces));
        else
          o.append (aItem);
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
