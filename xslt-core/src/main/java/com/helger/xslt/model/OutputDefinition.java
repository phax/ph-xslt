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
package com.helger.xslt.model;

import java.math.BigDecimal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * An output definition: the merged xsl:output declarations of one name (XSLT
 * 3.0 section 26). Each attribute takes the value of the declaration with the
 * highest import precedence that specifies it; two declarations of the same
 * precedence with different values are XTSE1560. cdata-section-elements,
 * suppress-indentation and use-character-maps accumulate.
 *
 * @author Philip Helger
 */
public final class OutputDefinition
{
  /** All serialization attributes of xsl:output. */
  public static final String [] ATTRIBUTES = { "method",
                                               "allow-duplicate-names",
                                               "build-tree",
                                               "byte-order-mark",
                                               "cdata-section-elements",
                                               "doctype-public",
                                               "doctype-system",
                                               "encoding",
                                               "escape-uri-attributes",
                                               "html-version",
                                               "include-content-type",
                                               "indent",
                                               "item-separator",
                                               "json-node-output-method",
                                               "media-type",
                                               "normalization-form",
                                               "omit-xml-declaration",
                                               "standalone",
                                               "suppress-indentation",
                                               "undeclare-prefixes",
                                               "use-character-maps",
                                               "version" };

  private final XdmQName m_aName;
  private final ICommonsOrderedMap <String, String> m_aValues = new CommonsLinkedHashMap <> ();
  private final ICommonsMap <String, Integer> m_aPrecedences = new CommonsHashMap <> ();
  private final ICommonsList <XdmQName> m_aCdataSectionElements = new CommonsArrayList <> ();
  private final ICommonsList <XdmQName> m_aSuppressIndentation = new CommonsArrayList <> ();
  private final ICommonsList <XdmQName> m_aUseCharacterMaps = new CommonsArrayList <> ();
  private final ICommonsMap <Integer, String> m_aInlineCharacterMap = new CommonsHashMap <> ();

  /**
   * @param aName
   *        Name, <code>null</code> for the unnamed output definition.
   */
  public OutputDefinition (@Nullable final XdmQName aName)
  {
    m_aName = aName;
  }

  /**
   * @return A copy of this definition (xsl:result-document starts from an
   *         output definition and merges its own attributes).
   */
  @NonNull
  public OutputDefinition getClone ()
  {
    final OutputDefinition ret = new OutputDefinition (m_aName);
    ret.m_aValues.putAll (m_aValues);
    ret.m_aPrecedences.putAll (m_aPrecedences);
    ret.m_aCdataSectionElements.addAll (m_aCdataSectionElements);
    ret.m_aSuppressIndentation.addAll (m_aSuppressIndentation);
    ret.m_aUseCharacterMaps.addAll (m_aUseCharacterMaps);
    ret.m_aInlineCharacterMap.putAll (m_aInlineCharacterMap);
    return ret;
  }

  @Nullable
  public XdmQName getName ()
  {
    return m_aName;
  }

  @NonNull
  private static ICommonsList <XdmQName> _resolveNames (@NonNull final XslElement aEl,
                                                         @NonNull final String sAttr,
                                                         @NonNull final String sValue,
                                                         final boolean bDefaultNamespace)
  {
    final ICommonsList <XdmQName> ret = new CommonsArrayList <> ();
    for (final String sToken : XsltNames.tokenize (sValue))
    {
      final XdmQName aName = aEl.resolveQName (sToken, bDefaultNamespace);
      if (aName == null)
        throw XsltException.at (aEl, "XTSE0280", "'" + sToken + "' in " + sAttr + " is not a valid QName with a bound prefix");
      ret.add (aName);
    }
    return ret;
  }

  /**
   * Merge one xsl:output declaration (or the attributes of
   * xsl:result-document evaluated at run time).
   *
   * @param aEl
   *        The declaration, for namespaces and error locations.
   * @param nPrecedence
   *        Its import precedence.
   * @param aAttrs
   *        Attribute name to value.
   */
  public void merge (@NonNull final XslElement aEl, final int nPrecedence, @NonNull final ICommonsMap <String, String> aAttrs)
  {
    for (final String sAttr : ATTRIBUTES)
    {
      final String sValue = aAttrs.get (sAttr);
      if (sValue == null)
        continue;
      switch (sAttr)
      {
        case "cdata-section-elements":
          m_aCdataSectionElements.addAll (_resolveNames (aEl, sAttr, sValue, true));
          continue;
        case "suppress-indentation":
          m_aSuppressIndentation.addAll (_resolveNames (aEl, sAttr, sValue, true));
          continue;
        case "use-character-maps":
          for (final XdmQName aName : _resolveNames (aEl, sAttr, sValue, false))
            m_aUseCharacterMaps.add (PackageModel.qualify (aEl, aName));
          continue;
        default:
          break;
      }
      final Integer aPrev = m_aPrecedences.get (sAttr);
      if (aPrev != null && aPrev.intValue () > nPrecedence)
        continue;
      String sNorm = sValue;
      if (!"item-separator".equals (sAttr) && !"doctype-public".equals (sAttr) && !"doctype-system".equals (sAttr))
        sNorm = sValue.trim ();
      if ("method".equals (sAttr) && sNorm.indexOf (':') > 0 && !sNorm.startsWith ("Q{"))
      {
        final XdmQName aMethod = aEl.resolveQName (sNorm, false);
        if (aMethod == null)
          throw XsltException.at (aEl, "XTSE1570", "The output method '" + sNorm + "' has an unbound prefix");
        sNorm = aMethod.getEQName ();
      }
      if (aPrev != null && aPrev.intValue () == nPrecedence && !m_aValues.get (sAttr).equals (sNorm))
        throw XsltException.at (aEl,
                                "XTSE1560",
                                "Conflicting values '" + m_aValues.get (sAttr) + "' and '" + sNorm + "' for xsl:output/@" + sAttr);
      m_aValues.put (sAttr, sNorm);
      m_aPrecedences.put (sAttr, Integer.valueOf (nPrecedence));
    }
  }

  /**
   * @param sAttr
   *        Attribute name.
   * @return The merged value or <code>null</code> if no declaration specifies
   *         it.
   */
  @Nullable
  public String getValue (@NonNull final String sAttr)
  {
    return m_aValues.get (sAttr);
  }

  /** @return The method, or <code>null</code> if not specified. */
  @Nullable
  public String getMethod ()
  {
    return m_aValues.get ("method");
  }

  /**
   * Add a character mapping given inline (in a serialization parameter
   * document).
   *
   * @param nCodePoint
   *        Character.
   * @param sReplacement
   *        Replacement string.
   */
  public void addInlineCharacterMapping (final int nCodePoint, @NonNull final String sReplacement)
  {
    m_aInlineCharacterMap.put (Integer.valueOf (nCodePoint), sReplacement);
  }

  /** @return The QNames of use-character-maps. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmQName> getUseCharacterMaps ()
  {
    return m_aUseCharacterMaps.getClone ();
  }

  /**
   * @param sMethod
   *        The effective method (when the definition gives none, the caller
   *        decides from the result tree).
   * @return <code>true</code> if a result tree is built (build-tree, by
   *         default yes except for the json and adaptive methods).
   */
  public boolean isBuildTree (@NonNull final String sMethod)
  {
    final String s = m_aValues.get ("build-tree");
    if (s != null)
      return XsltNames.isXsltTrue (s);
    return !SerializationParams.METHOD_JSON.equals (sMethod) && !SerializationParams.METHOD_ADAPTIVE.equals (sMethod);
  }

  /**
   * @param sMethod
   *        The effective method.
   * @return New serialization parameters (character maps are applied by the
   *         output customizers of the extension registry).
   */
  @NonNull
  public SerializationParams createSerializationParams (@NonNull final String sMethod)
  {
    final SerializationParams ret = new SerializationParams ();
    ret.setMethod (sMethod);
    ret.setOmitXmlDeclaration (false);
    if (SerializationParams.METHOD_HTML.equals (sMethod))
      ret.setIndent (true);
    for (final var aEntry : m_aValues.entrySet ())
    {
      final String v = aEntry.getValue ();
      switch (aEntry.getKey ())
      {
        case "allow-duplicate-names" -> ret.setAllowDuplicateNames (XsltNames.isXsltTrue (v));
        case "byte-order-mark" -> ret.setByteOrderMark (XsltNames.isXsltTrue (v));
        // A zero-length string is the same as omitting the attribute
        case "doctype-public" -> ret.setDoctypePublic (v.isEmpty () ? null : v);
        case "doctype-system" -> ret.setDoctypeSystem (v.isEmpty () ? null : v);
        case "encoding" -> ret.setEncoding (v);
        case "escape-uri-attributes" -> ret.setEscapeUriAttributes (XsltNames.isXsltTrue (v));
        case "html-version" ->
        {
          try
          {
            ret.setHtmlVersion (new BigDecimal (v));
          }
          catch (final NumberFormatException ex)
          {
            // ignore an invalid version
          }
        }
        case "include-content-type" -> ret.setIncludeContentType (XsltNames.isXsltTrue (v));
        case "indent" -> ret.setIndent (XsltNames.isXsltTrue (v));
        case "item-separator" -> ret.setItemSeparator ("#absent".equals (v) ? null : v);
        case "json-node-output-method" -> ret.setJsonNodeOutputMethod (v);
        case "media-type" -> ret.setMediaType (v);
        case "normalization-form" -> ret.setNormalizationForm (v);
        case "omit-xml-declaration" -> ret.setOmitXmlDeclaration (XsltNames.isXsltTrue (v));
        case "standalone" -> ret.setStandalone ("omit".equals (v) ? SerializationParams.STANDALONE_OMIT
                                                                   : XsltNames.isXsltTrue (v) ? SerializationParams.STANDALONE_YES
                                                                                              : SerializationParams.STANDALONE_NO);
        case "undeclare-prefixes" -> ret.setUndeclarePrefixes (XsltNames.isXsltTrue (v));
        case "version" -> ret.setVersion (v);
        default ->
        {
          // method and build-tree handled elsewhere
        }
      }
    }
    for (final var aEntry : m_aInlineCharacterMap.entrySet ())
      ret.addCharacterMapping (aEntry.getKey ().intValue (), aEntry.getValue ());
    for (final XdmQName aName : m_aCdataSectionElements)
      ret.addCdataSectionElement (aName);
    for (final XdmQName aName : m_aSuppressIndentation)
      ret.addSuppressIndentation (aName);
    return ret;
  }
}
