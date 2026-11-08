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
package com.helger.xpath.serialize;

import java.math.BigDecimal;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.xpath.xdm.XdmQName;

/**
 * The serialization parameters of Serialization 3.1 section 3. The defaults
 * are those of <code>fn:serialize</code> (F&amp;O 3.1 section 14.7.3), e.g.
 * <code>omit-xml-declaration</code> is <code>true</code>. Instances are
 * mutable; the setters return this for chaining.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public final class SerializationParams
{
  /** The namespace of the serialization parameter elements. */
  public static final String NS_OUTPUT = "http://www.w3.org/2010/xslt-xquery-serialization";

  public static final String METHOD_XML = "xml";
  public static final String METHOD_XHTML = "xhtml";
  public static final String METHOD_HTML = "html";
  public static final String METHOD_TEXT = "text";
  public static final String METHOD_JSON = "json";
  public static final String METHOD_ADAPTIVE = "adaptive";

  public static final String STANDALONE_YES = "yes";
  public static final String STANDALONE_NO = "no";
  public static final String STANDALONE_OMIT = "omit";

  private String m_sMethod = METHOD_XML;
  private boolean m_bAllowDuplicateNames = false;
  private boolean m_bByteOrderMark = false;
  private final ICommonsOrderedSet <XdmQName> m_aCdataSectionElements = new CommonsLinkedHashSet <> ();
  private String m_sDoctypePublic;
  private String m_sDoctypeSystem;
  private String m_sEncoding = "UTF-8";
  private boolean m_bEscapeUriAttributes = true;
  private BigDecimal m_aHtmlVersion;
  private boolean m_bIncludeContentType = true;
  private boolean m_bIndent = false;
  private String m_sItemSeparator;
  private String m_sJsonNodeOutputMethod = METHOD_XML;
  private String m_sMediaType;
  private String m_sNormalizationForm = "none";
  private boolean m_bOmitXmlDeclaration = true;
  private String m_sStandalone = STANDALONE_OMIT;
  private final ICommonsOrderedSet <XdmQName> m_aSuppressIndentation = new CommonsLinkedHashSet <> ();
  private boolean m_bUndeclarePrefixes = false;
  private final ICommonsMap <Integer, String> m_aCharacterMap = new CommonsHashMap <> ();
  private String m_sVersion;

  public SerializationParams ()
  {}

  /**
   * Copy constructor.
   *
   * @param aOther
   *        The parameters to copy.
   */
  public SerializationParams (@NonNull final SerializationParams aOther)
  {
    ValueEnforcer.notNull (aOther, "Other");
    m_sMethod = aOther.m_sMethod;
    m_bAllowDuplicateNames = aOther.m_bAllowDuplicateNames;
    m_bByteOrderMark = aOther.m_bByteOrderMark;
    m_aCdataSectionElements.addAll (aOther.m_aCdataSectionElements);
    m_sDoctypePublic = aOther.m_sDoctypePublic;
    m_sDoctypeSystem = aOther.m_sDoctypeSystem;
    m_sEncoding = aOther.m_sEncoding;
    m_bEscapeUriAttributes = aOther.m_bEscapeUriAttributes;
    m_aHtmlVersion = aOther.m_aHtmlVersion;
    m_bIncludeContentType = aOther.m_bIncludeContentType;
    m_bIndent = aOther.m_bIndent;
    m_sItemSeparator = aOther.m_sItemSeparator;
    m_sJsonNodeOutputMethod = aOther.m_sJsonNodeOutputMethod;
    m_sMediaType = aOther.m_sMediaType;
    m_sNormalizationForm = aOther.m_sNormalizationForm;
    m_bOmitXmlDeclaration = aOther.m_bOmitXmlDeclaration;
    m_sStandalone = aOther.m_sStandalone;
    m_aSuppressIndentation.addAll (aOther.m_aSuppressIndentation);
    m_bUndeclarePrefixes = aOther.m_bUndeclarePrefixes;
    m_aCharacterMap.putAll (aOther.m_aCharacterMap);
    m_sVersion = aOther.m_sVersion;
  }

  /** @return The output method: xml, xhtml, html, text, json or adaptive. */
  @NonNull
  public String getMethod ()
  {
    return m_sMethod;
  }

  @NonNull
  public SerializationParams setMethod (@NonNull final String sMethod)
  {
    ValueEnforcer.notEmpty (sMethod, "Method");
    m_sMethod = sMethod;
    return this;
  }

  public boolean isAllowDuplicateNames ()
  {
    return m_bAllowDuplicateNames;
  }

  @NonNull
  public SerializationParams setAllowDuplicateNames (final boolean b)
  {
    m_bAllowDuplicateNames = b;
    return this;
  }

  public boolean isByteOrderMark ()
  {
    return m_bByteOrderMark;
  }

  @NonNull
  public SerializationParams setByteOrderMark (final boolean b)
  {
    m_bByteOrderMark = b;
    return this;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedSet <XdmQName> getAllCdataSectionElements ()
  {
    return m_aCdataSectionElements.getClone ();
  }

  public boolean isCdataSectionElement (@NonNull final XdmQName aName)
  {
    return m_aCdataSectionElements.contains (aName);
  }

  @NonNull
  public SerializationParams addCdataSectionElement (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aCdataSectionElements.add (aName);
    return this;
  }

  @Nullable
  public String getDoctypePublic ()
  {
    return m_sDoctypePublic;
  }

  @NonNull
  public SerializationParams setDoctypePublic (@Nullable final String s)
  {
    m_sDoctypePublic = s;
    return this;
  }

  @Nullable
  public String getDoctypeSystem ()
  {
    return m_sDoctypeSystem;
  }

  @NonNull
  public SerializationParams setDoctypeSystem (@Nullable final String s)
  {
    m_sDoctypeSystem = s;
    return this;
  }

  @NonNull
  public String getEncoding ()
  {
    return m_sEncoding;
  }

  @NonNull
  public SerializationParams setEncoding (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "Encoding");
    m_sEncoding = s;
    return this;
  }

  public boolean isEscapeUriAttributes ()
  {
    return m_bEscapeUriAttributes;
  }

  @NonNull
  public SerializationParams setEscapeUriAttributes (final boolean b)
  {
    m_bEscapeUriAttributes = b;
    return this;
  }

  /** @return The html-version parameter, <code>null</code> if absent. */
  @Nullable
  public BigDecimal getHtmlVersion ()
  {
    return m_aHtmlVersion;
  }

  @NonNull
  public SerializationParams setHtmlVersion (@Nullable final BigDecimal a)
  {
    m_aHtmlVersion = a;
    return this;
  }

  public boolean isIncludeContentType ()
  {
    return m_bIncludeContentType;
  }

  @NonNull
  public SerializationParams setIncludeContentType (final boolean b)
  {
    m_bIncludeContentType = b;
    return this;
  }

  public boolean isIndent ()
  {
    return m_bIndent;
  }

  @NonNull
  public SerializationParams setIndent (final boolean b)
  {
    m_bIndent = b;
    return this;
  }

  /** @return The item-separator, <code>null</code> if absent. */
  @Nullable
  public String getItemSeparator ()
  {
    return m_sItemSeparator;
  }

  @NonNull
  public SerializationParams setItemSeparator (@Nullable final String s)
  {
    m_sItemSeparator = s;
    return this;
  }

  @NonNull
  public String getJsonNodeOutputMethod ()
  {
    return m_sJsonNodeOutputMethod;
  }

  @NonNull
  public SerializationParams setJsonNodeOutputMethod (@NonNull final String s)
  {
    ValueEnforcer.notEmpty (s, "JsonNodeOutputMethod");
    m_sJsonNodeOutputMethod = s;
    return this;
  }

  @Nullable
  public String getMediaType ()
  {
    return m_sMediaType;
  }

  @NonNull
  public SerializationParams setMediaType (@Nullable final String s)
  {
    m_sMediaType = s;
    return this;
  }

  /** @return NFC, NFD, NFKC, NFKD, fully-normalized or none. */
  @NonNull
  public String getNormalizationForm ()
  {
    return m_sNormalizationForm;
  }

  @NonNull
  public SerializationParams setNormalizationForm (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "NormalizationForm");
    m_sNormalizationForm = s;
    return this;
  }

  public boolean isOmitXmlDeclaration ()
  {
    return m_bOmitXmlDeclaration;
  }

  @NonNull
  public SerializationParams setOmitXmlDeclaration (final boolean b)
  {
    m_bOmitXmlDeclaration = b;
    return this;
  }

  /** @return yes, no or omit. */
  @NonNull
  public String getStandalone ()
  {
    return m_sStandalone;
  }

  @NonNull
  public SerializationParams setStandalone (@NonNull final String s)
  {
    ValueEnforcer.notNull (s, "Standalone");
    m_sStandalone = s;
    return this;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedSet <XdmQName> getAllSuppressIndentation ()
  {
    return m_aSuppressIndentation.getClone ();
  }

  public boolean isSuppressIndentation (@NonNull final XdmQName aName)
  {
    return m_aSuppressIndentation.contains (aName);
  }

  @NonNull
  public SerializationParams addSuppressIndentation (@NonNull final XdmQName aName)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aSuppressIndentation.add (aName);
    return this;
  }

  public boolean isUndeclarePrefixes ()
  {
    return m_bUndeclarePrefixes;
  }

  @NonNull
  public SerializationParams setUndeclarePrefixes (final boolean b)
  {
    m_bUndeclarePrefixes = b;
    return this;
  }

  /**
   * @return The character map (use-character-maps), code point to
   *         replacement.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsMap <Integer, String> getCharacterMap ()
  {
    return m_aCharacterMap.getClone ();
  }

  public boolean hasCharacterMap ()
  {
    return !m_aCharacterMap.isEmpty ();
  }

  /**
   * @param nCodePoint
   *        A code point.
   * @return The replacement or <code>null</code> if the character is not
   *         mapped.
   */
  @Nullable
  public String getCharacterMapping (final int nCodePoint)
  {
    return m_aCharacterMap.get (Integer.valueOf (nCodePoint));
  }

  @NonNull
  public SerializationParams setCharacterMap (@NonNull final Map <Integer, String> aMap)
  {
    ValueEnforcer.notNull (aMap, "Map");
    m_aCharacterMap.setAll (aMap);
    return this;
  }

  @NonNull
  public SerializationParams addCharacterMapping (final int nCodePoint, @NonNull final String sReplacement)
  {
    ValueEnforcer.notNull (sReplacement, "Replacement");
    m_aCharacterMap.put (Integer.valueOf (nCodePoint), sReplacement);
    return this;
  }

  /** @return The version parameter, <code>null</code> if absent. */
  @Nullable
  public String getVersion ()
  {
    return m_sVersion;
  }

  @NonNull
  public SerializationParams setVersion (@Nullable final String s)
  {
    m_sVersion = s;
    return this;
  }

  /** @return <code>true</code> if the XML version to write is 1.1. */
  public boolean isXml11 ()
  {
    return "1.1".equals (m_sVersion);
  }
}
