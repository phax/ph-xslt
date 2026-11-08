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
package com.helger.phoxtrot.xslt.model;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.array.ArrayHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

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
  private final ICommonsOrderedMap <String, XsltException> m_aConflicts = new CommonsLinkedHashMap <> ();
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
    ret.m_aConflicts.putAll (m_aConflicts);
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

  /**
   * Resolve a whitespace-separated list of lexical QNames or EQNames against
   * the namespaces of the element.
   *
   * @param aEl
   *        The element for the namespace context and the error location.
   * @param sAttr
   *        The attribute name, for the error message.
   * @param sValue
   *        The attribute value.
   * @param bDefaultNamespace
   *        <code>true</code> if an unprefixed name takes the default
   *        namespace (element names in cdata-section-elements and
   *        suppress-indentation), <code>false</code> if it is in no
   *        namespace.
   * @return The resolved names in order; empty for an empty value.
   * @throws XsltException
   *         XTSE0280 for a token that is not a valid QName or has an unbound
   *         prefix.
   */
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
   * xsl:result-document evaluated at run time, merged with a precedence above
   * all declarations). cdata-section-elements and suppress-indentation add
   * their resolved element names, use-character-maps adds the names
   * qualified for the package of the element, regardless of precedence. Every
   * other attribute is trimmed (except item-separator, doctype-public and
   * doctype-system, which are kept as given) and replaces the stored value
   * unless that came from a higher precedence. A prefixed method name is
   * resolved and stored as an EQName. Attributes not in {@link #ATTRIBUTES}
   * are ignored.
   * <p>
   * A different value at the same precedence as the stored one is a conflict
   * (XTSE1560), unless a declaration of higher precedence specifies the
   * attribute (XSLT 3.0 section 26). The conflict is therefore only noted and
   * dropped when a higher precedence value is merged;
   * {@link #checkConflicts()} raises it once all declarations are merged.
   *
   * @param aEl
   *        The declaration, for namespaces and error locations.
   * @param nPrecedence
   *        Its import precedence.
   * @param aAttrs
   *        Attribute name to value.
   * @throws XsltException
   *         XTSE0280 for an invalid or unbound name in a name list, XTSE1570
   *         for a method with an unbound prefix.
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
      if (aPrev != null && aPrev.intValue () == nPrecedence)
      {
        if (!m_aValues.get (sAttr).equals (sNorm) && !m_aConflicts.containsKey (sAttr))
          m_aConflicts.put (sAttr,
                            XsltException.at (aEl,
                                              "XTSE1560",
                                              "Conflicting values '" +
                                                          m_aValues.get (sAttr) +
                                                          "' and '" +
                                                          sNorm +
                                                          "' for xsl:output/@" +
                                                          sAttr));
        // The first value stays until a higher precedence replaces it
        continue;
      }
      m_aConflicts.remove (sAttr);
      m_aValues.put (sAttr, sNorm);
      m_aPrecedences.put (sAttr, Integer.valueOf (nPrecedence));
    }
  }

  /**
   * Raise the first conflict noted by
   * {@link #merge(XslElement, int, ICommonsMap)} that no declaration of a
   * higher precedence resolved.
   *
   * @throws XsltException
   *         XTSE1560 for two different values of one attribute at the highest
   *         precedence that specifies it.
   */
  public void checkConflicts ()
  {
    if (m_aConflicts.isNotEmpty ())
      throw m_aConflicts.getFirstValue ();
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

  /**
   * Parse one name of a name list given through the API: an EQName
   * <code>Q{uri}local</code>, the JAXP form <code>{uri}local</code>, or a
   * local name in no namespace.
   *
   * @throws IllegalArgumentException
   *         for a token that is none of these
   */
  @NonNull
  private static XdmQName _parseExpandedName (@NonNull final String sToken)
  {
    String s = sToken;
    if (s.startsWith ("Q{"))
      s = s.substring (1);
    if (s.startsWith ("{"))
    {
      final int nEnd = s.indexOf ('}');
      if (nEnd < 0 || nEnd == s.length () - 1)
        throw new IllegalArgumentException ("'" + sToken + "' is not an expanded name");
      return new XdmQName (s.substring (1, nEnd), s.substring (nEnd + 1));
    }
    if (s.indexOf (':') >= 0 || s.isEmpty ())
      throw new IllegalArgumentException ("'" + sToken + "' is not an expanded name or a local name");
    return new XdmQName (s);
  }

  /**
   * Create a copy of this definition with serialization parameters supplied
   * by the calling application (XSLT 3.0 section 26.1), which take
   * precedence over all declarations. Every value replaces the stored one
   * and is trimmed like a declared value (except item-separator,
   * doctype-public and doctype-system). cdata-section-elements and
   * suppress-indentation replace the declared lists with the
   * whitespace-separated names given, each an EQName
   * (<code>Q{uri}local</code>), a JAXP name (<code>{uri}local</code>) or a
   * local name in no namespace (there are no namespace declarations to
   * resolve a prefix against). A method of the form <code>{uri}local</code>
   * becomes the EQName <code>Q{uri}local</code>.
   *
   * @param aOverrides
   *        Attribute name to value. May not be <code>null</code>.
   * @return This definition if there are no overrides, otherwise a new one.
   * @throws IllegalArgumentException
   *         for a name that is no attribute of {@link #ATTRIBUTES}, for
   *         use-character-maps (character maps are named by QNames of the
   *         stylesheet) and for an invalid name in a name list
   */
  @NonNull
  public OutputDefinition getWithOverrides (@NonNull final Map <String, String> aOverrides)
  {
    if (aOverrides.isEmpty ())
      return this;
    final OutputDefinition ret = getClone ();
    for (final Map.Entry <String, String> aEntry : aOverrides.entrySet ())
    {
      final String sAttr = aEntry.getKey ();
      final String sValue = aEntry.getValue ();
      if (!ArrayHelper.contains (ATTRIBUTES, sAttr))
        throw new IllegalArgumentException ("'" + sAttr + "' is not a serialization parameter of xsl:output");
      switch (sAttr)
      {
        case "use-character-maps":
          throw new IllegalArgumentException ("use-character-maps cannot be supplied by the API");
        case "cdata-section-elements":
        case "suppress-indentation":
        {
          final ICommonsList <XdmQName> aList = "cdata-section-elements".equals (sAttr) ? ret.m_aCdataSectionElements
                                                                                       : ret.m_aSuppressIndentation;
          aList.clear ();
          for (final String sToken : XsltNames.tokenize (sValue))
            aList.add (_parseExpandedName (sToken));
          break;
        }
        default:
        {
          String sNorm = sValue;
          if (!"item-separator".equals (sAttr) && !"doctype-public".equals (sAttr) && !"doctype-system".equals (sAttr))
            sNorm = sValue.trim ();
          if ("method".equals (sAttr) && sNorm.startsWith ("{"))
            sNorm = _parseExpandedName (sNorm).getEQName ();
          ret.m_aValues.put (sAttr, sNorm);
          ret.m_aPrecedences.put (sAttr, Integer.valueOf (Integer.MAX_VALUE));
          ret.m_aConflicts.remove (sAttr);
          break;
        }
      }
    }
    return ret;
  }

  /**
   * @return The attribute values of the merged declarations (without the
   *         name lists cdata-section-elements, suppress-indentation and
   *         use-character-maps), attribute name to value, in the order they
   *         were first given.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getAllValues ()
  {
    return m_aValues.getClone ();
  }

  /** @return The element names of cdata-section-elements. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmQName> getCdataSectionElements ()
  {
    return m_aCdataSectionElements.getClone ();
  }

  /** @return The element names of suppress-indentation. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <XdmQName> getSuppressIndentation ()
  {
    return m_aSuppressIndentation.getClone ();
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
   * Build the serialisation parameters for one result. They start with the
   * given method, omit-xml-declaration="no" and, for the html and xhtml
   * methods, indent="yes"; then every stored attribute is applied: boolean values via
   * the XSLT yes/no forms, a zero-length doctype-public or doctype-system
   * counts as absent, an html-version that is not a decimal number is
   * ignored, item-separator "#absent" means none, standalone "omit" means
   * omit. method and build-tree are not applied here, use-character-maps
   * neither. The inline character mappings, cdata-section-elements and
   * suppress-indentation are added last.
   *
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
    // XSLT 3.0 section 26.1: indent defaults to yes for html and xhtml
    if (SerializationParams.METHOD_HTML.equals (sMethod) || SerializationParams.METHOD_XHTML.equals (sMethod))
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
