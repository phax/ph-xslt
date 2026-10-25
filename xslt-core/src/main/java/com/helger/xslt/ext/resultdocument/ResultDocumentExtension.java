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
package com.helger.xslt.ext.resultdocument;

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
import com.helger.xpath.XPathException;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IOutputCustomizer;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.instr.AbstractInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.model.CompiledStylesheet;
import com.helger.xslt.model.OutputDefinition;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.runtime.BufferedOutput;
import com.helger.xslt.runtime.ContentOutput;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.IXsltOutput;
import com.helger.xslt.runtime.SerializationChecks;
import com.helger.xslt.runtime.XsltContext;
import com.helger.xslt.schema.ValidationRequest;
import com.helger.xslt.runtime.XsltStateKey;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

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
 * with fn:doc or document(), XTDE1460 for an unknown format, SEPM0016 for an
 * invalid serialization parameter value.
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
      final XdmNode aDoc = aCtx.getDynamicContext ().getResourceResolver ().resolveDocument (sUri);
      if (aDoc == null)
        throw new XPathException ("SEPM0017", "The serialization parameter document '" + sUri + "' cannot be retrieved");
      XdmNode aRoot = null;
      for (int i = 0; i < aDoc.getChildCount (); ++i)
        if (aDoc.getChildAtIndex (i).isElement ())
          aRoot = aDoc.getChildAtIndex (i);
      if (aRoot == null || !SerializationParams.NS_OUTPUT.equals (aRoot.getName ().namespaceUri ()))
        throw new XPathException ("SEPM0017", "'" + sUri + "' is not a serialization parameter document");
      final ICommonsMap <String, String> aAttrs = new CommonsHashMap <> ();
      for (int i = 0; i < aRoot.getChildCount (); ++i)
      {
        final XdmNode aParam = aRoot.getChildAtIndex (i);
        if (!aParam.isElement () || !SerializationParams.NS_OUTPUT.equals (aParam.getName ().namespaceUri ()))
          continue;
        if ("use-character-maps".equals (aParam.getName ().localName ()))
        {
          for (int j = 0; j < aParam.getChildCount (); ++j)
          {
            final XdmNode aMap = aParam.getChildAtIndex (j);
            final XdmNode aChar = aMap.isElement () ? aMap.getAttribute ("", "character") : null;
            final XdmNode aString = aMap.isElement () ? aMap.getAttribute ("", "map-string") : null;
            if (aChar != null && aString != null && aChar.getValue ().codePointCount (0, aChar.getValue ().length ()) == 1)
              aDef.addInlineCharacterMapping (aChar.getValue ().codePointAt (0), aString.getValue ());
          }
          continue;
        }
        final XdmNode aValue = aParam.getAttribute ("", "value");
        if (aValue != null)
          aAttrs.put (aParam.getName ().localName (), aValue.getValue ());
      }
      aDef.merge (aEl, Integer.MAX_VALUE - 1, aAttrs);
    }

    private static boolean _isDecimal (@NonNull final String s)
    {
      return s.matches ("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");
    }

    /**
     * @return The effective output definition: the format (or the unnamed
     *         output definition), the parameter document and the attributes
     *         of the instruction.
     */
    @NonNull
    private OutputDefinition _getEffectiveDefinition (@NonNull final XsltContext aCtx)
    {
      final OutputDefinition ret = _getBaseDefinition (aCtx);
      _readParameterDocument (aCtx, ret);
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
        ret.merge (getElement (), Integer.MAX_VALUE, aAttrs);
      }
      catch (final XsltException ex)
      {
        // A QName in a dynamic value that cannot be resolved
        throw new XPathException ("XTDE0030", ex.getMessage ());
      }
      return ret;
    }

    @NonNull
    private static String _detectMethod (@Nullable final XdmNode aTree)
    {
      if (aTree != null)
        for (int i = 0; i < aTree.getChildCount (); ++i)
        {
          final XdmNode aChild = aTree.getChildAtIndex (i);
          if (aChild.isElement ())
          {
            final XdmQName aName = aChild.getName ();
            if ("html".equals (aName.localName ().toLowerCase (Locale.ROOT)))
            {
              if (!aName.hasNamespace ())
                return SerializationParams.METHOD_HTML;
              if (NS_XHTML.equals (aName.namespaceUri ()))
                return SerializationParams.METHOD_XHTML;
            }
            return SerializationParams.METHOD_XML;
          }
          if (aChild.isText () && !XsltNames.isWhitespaceOnly (aChild.getValue ()))
            return SerializationParams.METHOD_XML;
        }
      return SerializationParams.METHOD_XML;
    }

    @NonNull
    private static ICommonsSet <String> _getWrittenUris (@NonNull final Engine aEngine)
    {
      return aEngine.getRunData (RUN_DATA_URIS, CommonsHashSet::new);
    }

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
      m_aBody.execute (aCtx.with (SECONDARY_OUTPUT, null), aTarget);
      aEngine.sealPrincipalOutput (aTarget instanceof final BufferedOutput aBuffer ? aBuffer.size () : 0);
    }

    @NonNull
    private static String _serialize (@NonNull final CompiledStylesheet aSheet,
                                      @NonNull final OutputDefinition aDef,
                                      @NonNull final String sMethod,
                                      @NonNull final XdmSequence aRaw,
                                      @Nullable final XdmNode aTree)
    {
      final SerializationParams aParams = aDef.createSerializationParams (sMethod);
      for (final IOutputCustomizer aCustomizer : XsltExtensionRegistry.getInstance ().getAllOutputCustomizers ())
        aCustomizer.customize (aSheet, aDef, aParams);
      SerializationChecks.check (aParams, aTree);
      final String sSeparator = aDef.getValue ("item-separator");
      final boolean bSerializeRaw = aTree == null || sSeparator != null && !"#absent".equals (sSeparator);
      return XdmSerializer.serialize (bSerializeRaw ? aRaw : XdmSequence.of (aTree), aParams);
    }

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
      String sAbsolute = "";
      if (sBaseOutputUri != null && !sBaseOutputUri.isEmpty ())
        try
        {
          sAbsolute = FnUri.resolveUri (sHref, sBaseOutputUri);
        }
        catch (final XPathException ex)
        {
          throw new XPathException ("XTDE1460", "The href '" + sHref + "' cannot be resolved: " + ex.getMessage ());
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
      XdmNode aTree = null;
      if (bBuildTree)
      {
        final ICommonsList <IXdmItem> aItems = new CommonsArrayList <> ();
        for (int i = 0; i < aBuffer.size (); ++i)
          aItems.add (aBuffer.get (i));
        aTree = XdmNode.createDocument ();
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
