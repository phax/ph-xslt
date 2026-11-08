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
package com.helger.phoxtrot.xslt.ext.resultdocument;

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.XsltTransformResult;
import com.helger.phoxtrot.xslt.compile.Avt;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IOutputCustomizer;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.instr.AbstractInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.model.OutputDefinition;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.runtime.BufferedOutput;
import com.helger.phoxtrot.xslt.runtime.ContentOutput;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.SerializationChecks;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.runtime.XsltStateKey;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * xsl:result-document (XSLT 3.0 section 25.1). With an href the content
 * becomes a secondary result: built as a tree (or kept as a raw sequence with
 * build-tree="no"), serialized with the effective output definition (the
 * format named by <code>format</code>, else the unnamed one, overridden by
 * the serialization attributes of the instruction, all of them attribute value
 * templates) and recorded in the transformation result. Without an href the
 * content becomes the principal result, and the instruction's output
 * definition is used for it.
 * <p>
 * Errors: XTDE1480 in temporary output state, XTDE1490 for two results with
 * the same URI (also for content added to the principal result next to an
 * xsl:result-document without href), XTDE1500 for writing a URI that was read
 * with fn:doc or document(), XTDE1460 for an unknown format, XTDE0030 for an
 * href that is not a URI reference, SEPM0016 for an invalid serialization
 * parameter value.
 *
 * @author Philip Helger
 */
public final class ResultDocumentExtension implements IXsltExtension
{
  /**
   * The output of the secondary result whose body is being executed, so that
   * a nested xsl:result-document without href at its top level can write to
   * the principal result.
   */
  private static final XsltStateKey <IXsltOutput> SECONDARY_OUTPUT = new XsltStateKey <> ("result-document-output",
                                                                                         XsltStateKey.EScope.CLEARED_IN_FUNCTIONS);

  /** The key of the per-run set of written URIs. */
  private static final Object RUN_DATA_URIS = new Object ();

  private static final String NS_XHTML = "http://www.w3.org/1999/xhtml";

  /** Serialization parameters that take an xs:boolean value. */
  private static final ICommonsSet <String> BOOLEAN_PARAMS = new CommonsHashSet <> ("allow-duplicate-names",
                                                                                    "byte-order-mark",
                                                                                    "escape-uri-attributes",
                                                                                    "include-content-type",
                                                                                    "indent",
                                                                                    "omit-xml-declaration",
                                                                                    "undeclare-prefixes",
                                                                                    "build-tree");

  /** The compiled instruction. */
  private static final class ResultDocumentInstruction extends AbstractInstruction
  {
    private final Avt m_aHref;
    private final Avt m_aFormat;
    private final Avt m_aParameterDocument;
    private final ICommonsOrderedMap <String, Avt> m_aOutputAttrs;
    private final String m_sUseCharacterMaps;
    private final SequenceConstructor m_aBody;
    private final ValidationRequest m_aValidation;

    ResultDocumentInstruction (@NonNull final XslElement aElement,
                               @Nullable final Avt aHref,
                               @Nullable final Avt aFormat,
                               @Nullable final Avt aParameterDocument,
                               @NonNull final ICommonsOrderedMap <String, Avt> aOutputAttrs,
                               @Nullable final String sUseCharacterMaps,
                               @NonNull final SequenceConstructor aBody,
                               @Nullable final ValidationRequest aValidation)
    {
      super (aElement);
      m_aHref = aHref;
      m_aFormat = aFormat;
      m_aParameterDocument = aParameterDocument;
      m_aOutputAttrs = aOutputAttrs;
      m_sUseCharacterMaps = sUseCharacterMaps;
      m_aBody = aBody;
      m_aValidation = aValidation;
    }

    /**
     * Determine the output definition the instruction starts from. If the
     * format AVT gives a non-empty value, it is resolved as a QName against
     * the in-scope namespaces of the instruction (unprefixed means no
     * namespace), qualified with the package and looked up among the named
     * xsl:output declarations; otherwise the unnamed output definition of
     * the stylesheet is used.
     *
     * @param aCtx
     *        The run-time context.
     * @return A clone of the selected definition, safe to modify. Never
     *         <code>null</code>.
     * @throws XPathException
     *         XTDE1460 if the format is not a valid QName with a bound prefix
     *         or names no output definition.
     */
    @NonNull
    private OutputDefinition _getBaseDefinition (@NonNull final XsltContext aCtx)
    {
      final CompiledStylesheet aSheet = aCtx.getEngine ().getStylesheet ();
      final XslElement aEl = getElement ();
      if (m_aFormat != null)
      {
        final String sFormat = m_aFormat.evaluate (aCtx).trim ();
        if (!sFormat.isEmpty ())
        {
          final XdmQName aName = aEl.resolveQName (sFormat, false);
          if (aName == null)
            throw new XPathException ("XTDE1460", "The format '" + sFormat + "' is not a valid QName with a bound prefix");
          final OutputDefinition aNamed = aSheet.getNamedOutput (PackageModel.qualify (aEl, aName));
          if (aNamed == null)
            throw new XPathException ("XTDE1460", "There is no xsl:output declaration named " + aName.getEQName ());
          return aNamed.getClone ();
        }
      }
      return aSheet.getOutput ().getClone ();
    }

    /**
     * Apply the serialization parameter document named by parameter-document
     * (Serialization 3.1 section 3.1), if the attribute is present and not
     * empty. The URI is resolved against the static base URI of the
     * instruction when that is absolute and loaded through the resource
     * resolver. The last element child of the document must be
     * <code>output:serialization-parameters</code>. Of its children in that
     * namespace, the value attribute of each becomes the parameter of the
     * element's local name, and the element children (character and
     * map-string attributes) of <code>output:use-character-maps</code> are
     * added as inline character mappings; entries without both attributes or
     * whose character is not a single code point are skipped silently. The
     * parameters are merged with a precedence above that of the attributes of
     * the instruction: the parameter document takes precedence (XSLT 3.0
     * section 26.1).
     *
     * @param aCtx
     *        The run-time context.
     * @param aDef
     *        The definition to modify.
     * @throws XPathException
     *         SEPM0017 if the document cannot be retrieved or its root element
     *         is not output:serialization-parameters.
     */
    private void _readParameterDocument (@NonNull final XsltContext aCtx, @NonNull final OutputDefinition aDef)
    {
      if (m_aParameterDocument == null)
        return;
      final String sHref = m_aParameterDocument.evaluate (aCtx).trim ();
      if (sHref.isEmpty ())
        return;
      final XslElement aEl = getElement ();
      String sUri = sHref;
      final String sBase = aEl.getStaticBaseUri ();
      if (sBase != null && FnUri.isAbsoluteUri (sBase))
        sUri = FnUri.resolveUri (sHref, sBase);
      final IXdmNode aDoc = aCtx.getDynamicContext ().getResourceResolver ().resolveDocument (sUri);
      if (aDoc == null)
        throw new XPathException ("SEPM0017", "The serialization parameter document '" + sUri + "' cannot be retrieved");
      IXdmNode aRoot = null;
      for (int i = 0; i < aDoc.getChildCount (); ++i)
        if (aDoc.getChildAtIndex (i).isElement ())
          aRoot = aDoc.getChildAtIndex (i);
      if (aRoot == null ||
          !SerializationParams.NS_OUTPUT.equals (aRoot.getName ().namespaceUri ()) ||
          !"serialization-parameters".equals (aRoot.getName ().localName ()))
        throw new XPathException ("SEPM0017", "'" + sUri + "' is not a serialization parameter document");
      final ICommonsMap <String, String> aAttrs = new CommonsHashMap <> ();
      for (int i = 0; i < aRoot.getChildCount (); ++i)
      {
        final IXdmNode aParam = aRoot.getChildAtIndex (i);
        if (!aParam.isElement () || !SerializationParams.NS_OUTPUT.equals (aParam.getName ().namespaceUri ()))
          continue;
        if ("use-character-maps".equals (aParam.getName ().localName ()))
        {
          for (int j = 0; j < aParam.getChildCount (); ++j)
          {
            final IXdmNode aMap = aParam.getChildAtIndex (j);
            final IXdmNode aChar = aMap.isElement () ? aMap.getAttribute ("", "character") : null;
            final IXdmNode aString = aMap.isElement () ? aMap.getAttribute ("", "map-string") : null;
            if (aChar != null && aString != null && aChar.getValue ().codePointCount (0, aChar.getValue ().length ()) == 1)
              aDef.addInlineCharacterMapping (aChar.getValue ().codePointAt (0), aString.getValue ());
          }
          continue;
        }
        final IXdmNode aValue = aParam.getAttribute ("", "value");
        if (aValue != null)
          aAttrs.put (aParam.getName ().localName (), aValue.getValue ());
      }
      aDef.merge (aEl, Integer.MAX_VALUE, aAttrs);
    }

    /**
     * Check the lexical form of an <code>xs:decimal</code> (optional sign,
     * digits with an optional fraction or a fraction only), as required for
     * html-version.
     *
     * @param s
     *        The trimmed value.
     * @return <code>true</code> if it matches.
     */
    private static boolean _isDecimal (@NonNull final String s)
    {
      return s.matches ("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");
    }

    /**
     * @return The effective output definition: the format (or the unnamed
     *         output definition), overridden by the attributes of the
     *         instruction, overridden by the parameter document.
     */
    @NonNull
    private OutputDefinition _getEffectiveDefinition (@NonNull final XsltContext aCtx)
    {
      final OutputDefinition ret = _getBaseDefinition (aCtx);
      final ICommonsMap <String, String> aAttrs = new CommonsHashMap <> ();
      for (final var aEntry : m_aOutputAttrs.entrySet ())
      {
        final String sParam = aEntry.getKey ();
        final String sValue = aEntry.getValue ().evaluate (aCtx);
        final String sTrimmed = sValue.trim ();
        if (BOOLEAN_PARAMS.contains (sParam) && !XsltNames.isXsltBooleanLexical (sTrimmed))
          throw new XPathException ("SEPM0016", "Invalid value '" + sValue + "' for the serialization parameter " + sParam);
        if ("standalone".equals (sParam) && !"omit".equals (sTrimmed) && !XsltNames.isXsltBooleanLexical (sTrimmed))
          throw new XPathException ("SEPM0016", "Invalid value '" + sValue + "' for the serialization parameter standalone");
        if ("html-version".equals (sParam) && !_isDecimal (sTrimmed))
          throw new XPathException ("SEPM0016", "Invalid value '" + sValue + "' for the serialization parameter html-version");
        aAttrs.put (sParam, sValue);
      }
      if (m_sUseCharacterMaps != null)
        aAttrs.put ("use-character-maps", m_sUseCharacterMaps);
      try
      {
        ret.merge (getElement (), Integer.MAX_VALUE - 1, aAttrs);
      }
      catch (final XsltException ex)
      {
        // A QName in a dynamic value that cannot be resolved
        throw new XPathException ("XTDE0030", ex.getMessage ());
      }
      _readParameterDocument (aCtx, ret);
      return ret;
    }

    /**
     * Choose the default output method from the result tree (XSLT 3.0
     * section 26.1): if the first element child of the document node is
     * named <code>html</code> (compared case insensitively) in no namespace,
     * html; if it is named <code>html</code> in lower case in the XHTML
     * namespace, xhtml.
     * Any other first element, non-whitespace text before the first element,
     * or no tree at all gives xml.
     *
     * @param aTree
     *        The result tree, or <code>null</code> if none was built.
     * @return The method name. Never <code>null</code>.
     */
    @NonNull
    private static String _detectMethod (@Nullable final IXdmNode aTree)
    {
      if (aTree != null)
        for (int i = 0; i < aTree.getChildCount (); ++i)
        {
          final IXdmNode aChild = aTree.getChildAtIndex (i);
          if (aChild.isElement ())
          {
            final XdmQName aName = aChild.getName ();
            if (!aName.hasNamespace () && "html".equals (aName.localName ().toLowerCase (Locale.ROOT)))
              return SerializationParams.METHOD_HTML;
            if (NS_XHTML.equals (aName.namespaceUri ()) && "html".equals (aName.localName ()))
              return SerializationParams.METHOD_XHTML;
            return SerializationParams.METHOD_XML;
          }
          if (aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ()))
            return SerializationParams.METHOD_XML;
        }
      return SerializationParams.METHOD_XML;
    }

    /**
     * @param aEngine
     *        The transformation.
     * @return The mutable per-run set of the URIs (absolute if resolvable,
     *         else as written) secondary results were written to, created on
     *         first access.
     */
    @NonNull
    private static ICommonsSet <String> _getWrittenUris (@NonNull final Engine aEngine)
    {
      return aEngine.getRunData (RUN_DATA_URIS, CommonsHashSet::new);
    }

    /**
     * Write the content as the principal result (xsl:result-document without
     * href). The target is the principal output of the engine, or the
     * current output if the engine has none. This is only allowed at the top
     * level of the principal output or directly inside the body of a
     * secondary result, when nothing has been written to the principal
     * output yet and no earlier xsl:result-document has sealed it; otherwise
     * XTDE1490 is raised. The effective output definition becomes the
     * principal one. With validation requested, the content is buffered,
     * assembled into a document (with the item-separator as text between
     * items, unless absent) that is validated, and then replayed into the
     * target; the validated tree itself is not used for the output.
     * Afterwards the principal output is sealed at its current size, so that
     * later content counts as a second principal result.
     *
     * @param aCtx
     *        The run-time context.
     * @param aOut
     *        The current output of the instruction.
     * @param aDef
     *        The effective output definition.
     */
    private void _writePrincipal (@NonNull final XsltContext aCtx,
                                  @NonNull final IXsltOutput aOut,
                                  @NonNull final OutputDefinition aDef)
    {
      final Engine aEngine = aCtx.getEngine ();
      IXsltOutput aTarget = aEngine.getPrincipalOutput ();
      if (aTarget == null)
        aTarget = aOut;
      // Only allowed when the principal result has no other content: at the
      // top level of the principal output, or within a secondary result
      final boolean bAtTop = aOut == aTarget || aCtx.get (SECONDARY_OUTPUT) != null;
      final boolean bEmpty = !(aTarget instanceof final BufferedOutput aBuffer) || aBuffer.size () == 0;
      if (!bAtTop || !bEmpty || aEngine.getPrincipalSealedSize () >= 0)
        throw new XPathException ("XTDE1490", "xsl:result-document without href writes a second principal result");
      aEngine.setPrincipalOutputDefinition (aDef);
      if (m_aValidation != null && m_aValidation.isValidating ())
      {
        // Schema-aware: the principal result tree is validated as requested;
        // a copy of the content is built for that
        final BufferedOutput aContentBuffer = new BufferedOutput ();
        m_aBody.execute (aCtx.with (SECONDARY_OUTPUT, null), aContentBuffer);
        final IXdmNode aTree = IXdmNode.createDocument ();
        final ContentOutput aContent = new ContentOutput (aTree);
        // Sequence normalisation puts the item separator into the tree as
        // text (Serialization 3.1 section 2), where validation sees it
        final String sSeparator = aDef.getValue ("item-separator");
        for (int i = 0; i < aContentBuffer.size (); ++i)
        {
          if (i > 0 && sSeparator != null && !"#absent".equals (sSeparator))
            aContent.appendNew (IXdmNode.createText (sSeparator));
          aContent.append (aContentBuffer.get (i));
        }
        aContent.finishTree ();
        m_aValidation.apply (aTree);
        aContentBuffer.replayAll (aTarget);
      }
      else
        m_aBody.execute (aCtx.with (SECONDARY_OUTPUT, null), aTarget);
      aEngine.sealPrincipalOutput (aTarget instanceof final BufferedOutput aBuffer ? aBuffer.size () : 0);
    }

    /**
     * Serialise a secondary result. The serialization parameters are built
     * from the definition for the method, adjusted by every registered output
     * customizer (e.g. character maps) and checked against the tree
     * ({@link SerializationChecks}). The raw sequence is serialised instead of
     * the tree when no tree was built or an item-separator other than
     * <code>#absent</code> is set.
     *
     * @param aSheet
     *        The stylesheet.
     * @param aDef
     *        The effective output definition.
     * @param sMethod
     *        The effective method.
     * @param aRaw
     *        The raw result sequence.
     * @param aTree
     *        The result tree, or <code>null</code> with build-tree="no".
     * @return The serialised text. Never <code>null</code>.
     * @throws XPathException
     *         with the serialization error code of an invalid combination.
     */
    @NonNull
    private static String _serialize (@NonNull final CompiledStylesheet aSheet,
                                      @NonNull final OutputDefinition aDef,
                                      @NonNull final String sMethod,
                                      @NonNull final XdmSequence aRaw,
                                      @Nullable final IXdmNode aTree)
    {
      final SerializationParams aParams = aDef.createSerializationParams (sMethod);
      for (final IOutputCustomizer aCustomizer : XsltExtensionRegistry.getInstance ().getAllOutputCustomizers ())
        aCustomizer.customize (aSheet, aDef, aParams);
      SerializationChecks.check (aParams, aTree);
      final String sSeparator = aDef.getValue ("item-separator");
      final boolean bSerializeRaw = aTree == null || (sSeparator != null && !"#absent".equals (sSeparator));
      return XdmSerializer.serialize (bSerializeRaw ? aRaw : XdmSequence.of (aTree), aParams);
    }

    /**
     * Evaluate the instruction. In temporary output state XTDE1480 is raised.
     * The effective output definition is computed first; an absent or empty
     * href writes the principal result ({@link #_writePrincipal}).
     * <p>
     * Otherwise the href must be a URI reference, else XTDE0030 is raised
     * (XSLT 3.0 section 25.1); it is resolved against the base output URI of
     * the request (a resolution failure is reported as XTDE0030 as well);
     * without a base output URI only an already absolute href counts as
     * absolute. The URI
     * (absolute, or the href as written) must not have been read with
     * fn:doc/document() before (XTDE1500) nor written before (XTDE1490). The
     * body runs into a buffer with current-output-uri set to the absolute URI
     * (empty if there is none). Unless build-tree is off (by default it is
     * off only for the json and adaptive methods), the items are assembled
     * into a document node, with the absolute URI as base URI, and validated
     * if requested. The method is the declared one or detected from the tree
     * ({@link #_detectMethod}). The serialised result is recorded with the
     * engine under the href as written.
     * </p>
     *
     * @param aCtx
     *        The run-time context.
     * @param aOut
     *        The current output (only used for the principal result).
     */
    public void execute (@NonNull final XsltContext aCtx, @NonNull final IXsltOutput aOut)
    {
      if (Engine.isTemporaryOutputState (aCtx))
        throw new XPathException ("XTDE1480", "xsl:result-document is not allowed in temporary output state");
      final Engine aEngine = aCtx.getEngine ();
      final OutputDefinition aDef = _getEffectiveDefinition (aCtx);
      final String sHref = m_aHref == null ? "" : m_aHref.evaluate (aCtx).trim ();
      if (sHref.isEmpty ())
      {
        _writePrincipal (aCtx, aOut, aDef);
        return;
      }

      // The absolute URI of the result
      final String sBaseOutputUri = aEngine.getRequest ().getBaseOutputUri ();
      if (!FnUri.isValidUriReference (sHref))
        throw new XPathException ("XTDE0030", "The href '" + sHref + "' of xsl:result-document is not a valid URI reference");
      String sAbsolute = "";
      if (sBaseOutputUri != null && !sBaseOutputUri.isEmpty ())
        try
        {
          sAbsolute = FnUri.resolveUri (sHref, sBaseOutputUri);
        }
        catch (final XPathException ex)
        {
          throw new XPathException ("XTDE0030", "The href '" + sHref + "' cannot be resolved: " + ex.getMessage ());
        }
      else
        if (FnUri.isAbsoluteUri (sHref))
          sAbsolute = sHref;
      final String sKey = sAbsolute.isEmpty () ? sHref : sAbsolute;
      final ICommonsMap <String, Object> aRunCache = aCtx.getDynamicContext ().getRunCache ();
      if (aRunCache.containsKey ("fn:doc#" + sKey))
        throw new XPathException ("XTDE1500", "xsl:result-document writes to '" + sKey + "', which was read before");
      if (!_getWrittenUris (aEngine).add (sKey))
        throw new XPathException ("XTDE1490", "Two results are written to the URI '" + sKey + "'");

      // The content
      final BufferedOutput aBuffer = new BufferedOutput ();
      final XsltContext aBodyCtx = aCtx.with (Engine.CURRENT_OUTPUT_URI, sAbsolute).with (SECONDARY_OUTPUT, aBuffer);
      m_aBody.execute (aBodyCtx, aBuffer);

      final String sDeclaredMethod = aDef.getMethod ();
      final boolean bBuildTree = aDef.isBuildTree (sDeclaredMethod != null ? sDeclaredMethod
                                                                            : SerializationParams.METHOD_XML);
      final XdmSequence aRaw;
      IXdmNode aTree = null;
      if (bBuildTree)
      {
        final ICommonsList <IXdmItem> aItems = new CommonsArrayList <> ();
        for (int i = 0; i < aBuffer.size (); ++i)
          aItems.add (aBuffer.get (i));
        aTree = IXdmNode.createDocument ();
        if (!sAbsolute.isEmpty ())
          aTree.setBaseUri (sAbsolute);
        final ContentOutput aContent = new ContentOutput (aTree);
        aBuffer.replayAll (aContent);
        aContent.finishTree ();
        // Schema-aware: the result tree is validated as requested
        if (m_aValidation != null)
          m_aValidation.apply (aTree);
        aRaw = XdmSequence.of (aItems);
      }
      else
        aRaw = aBuffer.getSequence ();
      final String sMethod = sDeclaredMethod != null ? sDeclaredMethod : _detectMethod (aTree);
      final String sSerialized = _serialize (aEngine.getStylesheet (), aDef, sMethod, aRaw, aTree);
      aEngine.addSecondaryResult (new XsltTransformResult.SecondaryResult (sHref, aRaw, aTree, sSerialized));
    }
  }

  /**
   * Compile xsl:result-document. Every serialization attribute of
   * {@link OutputDefinition#ATTRIBUTES} except version and use-character-maps
   * is compiled as an AVT; output-version supplies the version parameter. A
   * constant value of a boolean parameter that is not an XSLT boolean, or of
   * html-version that is not a decimal, is the static error XTSE0020. Each
   * name in use-character-maps (not an AVT) must be a valid QName with a
   * bound prefix (XTSE0280); whether the maps exist is checked by the
   * character map family. href, format and parameter-document are AVTs
   * evaluated at run time.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:result-document element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compile (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final ICommonsOrderedMap <String, Avt> aOutputAttrs = new CommonsLinkedHashMap <> ();
    for (final String sAttr : OutputDefinition.ATTRIBUTES)
    {
      // "version" is the XSLT version here; the serialization parameter is
      // output-version. use-character-maps is not an AVT.
      if ("version".equals (sAttr) || "use-character-maps".equals (sAttr))
        continue;
      final Avt aAvt = aCtx.compileOptionalAvt (aEl, sAttr);
      if (aAvt != null)
        aOutputAttrs.put (sAttr, aAvt);
    }
    final Avt aOutputVersion = aCtx.compileOptionalAvt (aEl, "output-version");
    if (aOutputVersion != null)
      aOutputAttrs.put ("version", aOutputVersion);
    // A constant value can be checked statically
    for (final var aEntry : aOutputAttrs.entrySet ())
    {
      final Avt aAvt = aEntry.getValue ();
      if (aAvt.isConstant () && (BOOLEAN_PARAMS.contains (aEntry.getKey ()) || "html-version".equals (aEntry.getKey ())))
      {
        final String sValue = aAvt.getConstantValue ().trim ();
        if (BOOLEAN_PARAMS.contains (aEntry.getKey ()) ? !XsltNames.isXsltBooleanLexical (sValue)
                                                       : !ResultDocumentInstruction._isDecimal (sValue))
          throw XsltException.at (aEl,
                                  "XTSE0020",
                                  "Invalid value '" + aAvt.getConstantValue () + "' for the attribute " + aEntry.getKey ());
      }
    }
    final String sUseCharacterMaps = aEl.getAttr ("use-character-maps");
    if (sUseCharacterMaps != null)
      for (final String sToken : XsltNames.tokenize (sUseCharacterMaps))
        if (aEl.resolveQName (sToken, false) == null)
          throw XsltException.at (aEl,
                                  "XTSE0280",
                                  "'" + sToken + "' in use-character-maps is not a valid QName with a bound prefix");
    return new ResultDocumentInstruction (aEl,
                                          aCtx.compileOptionalAvt (aEl, "href"),
                                          aCtx.compileOptionalAvt (aEl, "format"),
                                          aCtx.compileOptionalAvt (aEl, "parameter-document"),
                                          aOutputAttrs,
                                          sUseCharacterMaps,
                                          aCtx.compileContent (aEl),
                                          ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.DOCUMENT));
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("result-document", ResultDocumentExtension::_compile);
  }
}
