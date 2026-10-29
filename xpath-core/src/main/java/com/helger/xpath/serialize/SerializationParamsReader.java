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
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * Builds {@link SerializationParams} from the two forms <code>fn:serialize</code>
 * accepts (F&amp;O 3.1 section 14.7.3): an option map, or an
 * <code>output:serialization-parameters</code> element.
 *
 * @author Philip Helger
 */
@Immutable
public final class SerializationParamsReader
{
  private static final SeqType BOOLEAN_OPT = _type ("xs:boolean?");
  private static final SeqType STRING_OPT = _type ("xs:string?");
  private static final SeqType DECIMAL_OPT = _type ("xs:decimal?");
  private static final SeqType QNAME_STAR = _type ("xs:QName*");
  private static final SeqType ANY_ATOMIC_OPT = _type ("xs:anyAtomicType?");
  private static final SeqType CHARMAP_OPT = _type ("map(xs:string, xs:string)?");

  private static final Set <String> PARAM_NAMES = Set.of ("allow-duplicate-names",
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
                                                          "method",
                                                          "normalization-form",
                                                          "omit-xml-declaration",
                                                          "standalone",
                                                          "suppress-indentation",
                                                          "undeclare-prefixes",
                                                          "use-character-maps",
                                                          "version");
  private static final Set <String> METHODS = Set.of (SerializationParams.METHOD_XML,
                                                      SerializationParams.METHOD_XHTML,
                                                      SerializationParams.METHOD_HTML,
                                                      SerializationParams.METHOD_TEXT,
                                                      SerializationParams.METHOD_JSON,
                                                      SerializationParams.METHOD_ADAPTIVE);

  @PresentForCodeCoverage
  private static final SerializationParamsReader INSTANCE = new SerializationParamsReader ();

  private SerializationParamsReader ()
  {}

  @NonNull
  private static SeqType _type (@NonNull final String sType)
  {
    return XPathParser.parseSequenceType (sType, new StaticContext ());
  }

  @NonNull
  private static String _normaliseNormalizationForm (@NonNull final String sValue, @NonNull final String sErrorCode)
  {
    final String s = sValue.trim ();
    switch (s.toUpperCase (Locale.ROOT))
    {
      case "NFC":
      case "NFD":
      case "NFKC":
      case "NFKD":
        return s.toUpperCase (Locale.ROOT);
      case "NONE":
        return "none";
      case "FULLY-NORMALIZED":
        return "fully-normalized";
      default:
        throw XPathException.of (sErrorCode, "Invalid normalization-form '" + sValue + "'");
    }
  }

  @NonNull
  private static String _checkMethod (@NonNull final String sMethod, @NonNull final String sErrorCode)
  {
    if (!METHODS.contains (sMethod))
      throw XPathException.of (sErrorCode, "Unsupported serialization method '" + sMethod + "'");
    return sMethod;
  }

  /**
   * method and json-node-output-method have the type
   * <code>union(xs:string, xs:QName)</code>.
   */
  @Nullable
  private static String _getMethodValue (@NonNull final XdmSequence aValue, @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aConv = SequenceTypeMatcher.convert (aValue, ANY_ATOMIC_OPT, aCtx, "XPTY0004");
    if (aConv.isEmpty ())
      return null;
    final XdmAtomic aAtomic = (XdmAtomic) aConv.get (0);
    if (aAtomic.getType () == EAtomicType.QNAME)
    {
      final XdmQName aName = (XdmQName) aAtomic.getValue ();
      if (aName.hasNamespace ())
        throw XPathException.of ("SEPM0016", "Unsupported serialization method " + aName.getEQName ());
      return _checkMethod (aName.localName (), "SEPM0016");
    }
    if (!aAtomic.isStringLike ())
      throw XPathException.type ("The serialization method must be a string or a QName");
    return _checkMethod (aAtomic.getString (), "SEPM0016");
  }

  @Nullable
  private static XdmAtomic _single (@NonNull final XdmSequence aValue)
  {
    return aValue.isEmpty () ? null : (XdmAtomic) aValue.get (0);
  }

  private static void _applyMapParam (@NonNull final SerializationParams aParams,
                                      @NonNull final String sName,
                                      @NonNull final XdmSequence aRaw,
                                      @NonNull final DynamicContext aCtx)
  {
    switch (sName)
    {
      case "allow-duplicate-names":
      case "byte-order-mark":
      case "escape-uri-attributes":
      case "include-content-type":
      case "indent":
      case "omit-xml-declaration":
      case "undeclare-prefixes":
      case "standalone":
      {
        final XdmAtomic aBool = _single (SequenceTypeMatcher.convert (aRaw, BOOLEAN_OPT, aCtx, "XPTY0004"));
        if (aBool == null)
        {
          if ("standalone".equals (sName))
            aParams.setStandalone (SerializationParams.STANDALONE_OMIT);
          return;
        }
        final boolean b = aBool.getBoolean ();
        switch (sName)
        {
          case "allow-duplicate-names":
            aParams.setAllowDuplicateNames (b);
            break;
          case "byte-order-mark":
            aParams.setByteOrderMark (b);
            break;
          case "escape-uri-attributes":
            aParams.setEscapeUriAttributes (b);
            break;
          case "include-content-type":
            aParams.setIncludeContentType (b);
            break;
          case "indent":
            aParams.setIndent (b);
            break;
          case "omit-xml-declaration":
            aParams.setOmitXmlDeclaration (b);
            break;
          case "undeclare-prefixes":
            aParams.setUndeclarePrefixes (b);
            break;
          default:
            aParams.setStandalone (b ? SerializationParams.STANDALONE_YES : SerializationParams.STANDALONE_NO);
            break;
        }
        return;
      }
      case "cdata-section-elements":
      case "suppress-indentation":
      {
        for (final IXdmItem aItem : SequenceTypeMatcher.convert (aRaw, QNAME_STAR, aCtx, "XPTY0004"))
        {
          final XdmQName aQName = (XdmQName) ((XdmAtomic) aItem).getValue ();
          if ("cdata-section-elements".equals (sName))
            aParams.addCdataSectionElement (aQName);
          else
            aParams.addSuppressIndentation (aQName);
        }
        return;
      }
      case "html-version":
      {
        final XdmAtomic aDec = _single (SequenceTypeMatcher.convert (aRaw, DECIMAL_OPT, aCtx, "XPTY0004"));
        if (aDec != null)
          aParams.setHtmlVersion (aDec.getValue () instanceof final BigDecimal aBD ? aBD
                                                                                   : new BigDecimal (aDec.getBigInteger ()));
        return;
      }
      case "method":
      {
        final String s = _getMethodValue (aRaw, aCtx);
        if (s != null)
          aParams.setMethod (s);
        return;
      }
      case "json-node-output-method":
      {
        final String s = _getMethodValue (aRaw, aCtx);
        if (s != null)
          aParams.setJsonNodeOutputMethod (s);
        return;
      }
      case "use-character-maps":
      {
        final XdmSequence aConv = SequenceTypeMatcher.convert (aRaw, CHARMAP_OPT, aCtx, "XPTY0004");
        if (aConv.isEmpty ())
          return;
        for (final XdmMap.Entry aEntry : ((XdmMap) aConv.get (0)).entries ())
        {
          final String sChar = aEntry.key ().getString ();
          if (sChar.codePointCount (0, sChar.length ()) != 1)
            throw XPathException.of ("SEPM0016", "A character map key must be a single character: '" + sChar + "'");
          aParams.addCharacterMapping (sChar.codePointAt (0), ((XdmAtomic) aEntry.value ().get (0)).getString ());
        }
        return;
      }
      default:
      {
        final XdmAtomic aStr = _single (SequenceTypeMatcher.convert (aRaw, STRING_OPT, aCtx, "XPTY0004"));
        if (aStr == null)
          return;
        final String s = aStr.getString ();
        switch (sName)
        {
          case "doctype-public":
            aParams.setDoctypePublic (s);
            break;
          case "doctype-system":
            aParams.setDoctypeSystem (s);
            break;
          case "encoding":
            aParams.setEncoding (s);
            break;
          case "item-separator":
            aParams.setItemSeparator (s);
            break;
          case "media-type":
            aParams.setMediaType (s);
            break;
          case "normalization-form":
            aParams.setNormalizationForm (_normaliseNormalizationForm (s, "SEPM0016"));
            break;
          case "version":
            aParams.setVersion (s);
            break;
          default:
            break;
        }
      }
    }
  }

  /**
   * Read the map form. Entries whose key is not the name of a standard
   * parameter (including keys of type xs:QName) are ignored.
   *
   * @param aMap
   *        The parameter map.
   * @param aCtx
   *        Context for the function conversion rules.
   * @return The parameters, starting from the fn:serialize defaults.
   * @throws XPathException
   *         XPTY0004 for a value of the wrong type, SEPM0016 for an invalid
   *         value.
   */
  @NonNull
  public static SerializationParams fromMap (@NonNull final XdmMap aMap, @NonNull final DynamicContext aCtx)
  {
    final SerializationParams ret = new SerializationParams ();
    for (final String sName : new String [] { "method",
                                              "allow-duplicate-names",
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
                                              "version" })
    {
      final XdmSequence aValue = aMap.get (XdmAtomic.string (sName));
      if (aValue != null)
        _applyMapParam (ret, sName, aValue, aCtx);
    }
    return ret;
  }

  private static boolean _parseBoolean (@NonNull final String sValue, @NonNull final String sName)
  {
    switch (sValue.trim ())
    {
      case "yes":
      case "true":
      case "1":
        return true;
      case "no":
      case "false":
      case "0":
        return false;
      default:
        throw XPathException.of ("SEPM0017", "Invalid boolean value '" + sValue + "' for parameter '" + sName + "'");
    }
  }

  @NonNull
  private static XdmQName _resolveQName (@NonNull final String sLexical, @NonNull final IXdmNode aContext)
  {
    String sPrefix = "";
    String sLocal = sLexical;
    if (sLexical.startsWith ("Q{"))
    {
      final int nEnd = sLexical.indexOf ('}');
      if (nEnd < 0)
        throw XPathException.of ("SEPM0017", "Invalid EQName '" + sLexical + "'");
      return new XdmQName (sLexical.substring (2, nEnd), sLexical.substring (nEnd + 1));
    }
    final int nColon = sLexical.indexOf (':');
    if (nColon >= 0)
    {
      sPrefix = sLexical.substring (0, nColon);
      sLocal = sLexical.substring (nColon + 1);
    }
    if (sLocal.isEmpty () || sLocal.indexOf (':') >= 0)
      throw XPathException.of ("SEPM0017", "Invalid QName '" + sLexical + "'");
    if (sPrefix.isEmpty ())
      return new XdmQName (sLocal);
    final String sUri = aContext.lookupNamespaceUri (sPrefix);
    if (sUri == null)
      throw XPathException.of ("SEPM0017", "Unbound prefix '" + sPrefix + "' in '" + sLexical + "'");
    return new XdmQName (sUri, sLocal, sPrefix);
  }

  private static void _readCharacterMaps (@NonNull final SerializationParams aParams, @NonNull final IXdmNode aElement)
  {
    if (aElement.getAttributeCount () > 0)
      throw XPathException.of ("SEPM0017", "use-character-maps must not have attributes");
    final ICommonsSet <Integer> aSeen = new CommonsHashSet <> ();
    for (final IXdmNode aChild : aElement.getAllChildren ())
    {
      if (aChild.getKind () == ENodeKind.TEXT)
      {
        if (!aChild.getValue ().isBlank ())
          throw XPathException.of ("SEPM0017", "Text is not allowed in use-character-maps");
        continue;
      }
      if (aChild.getKind () != ENodeKind.ELEMENT)
        continue;
      if (!aChild.getName ().isSameName (SerializationParams.NS_OUTPUT, "character-map"))
        throw XPathException.of ("SEPM0017", "Unexpected element " + aChild.getName () + " in use-character-maps");
      String sChar = null;
      String sMapString = null;
      for (final IXdmNode aAttr : aChild.getAllAttributes ())
      {
        final XdmQName aAttrName = aAttr.getName ();
        if (aAttrName.isSameName ("", "character"))
          sChar = aAttr.getValue ();
        else
          if (aAttrName.isSameName ("", "map-string"))
            sMapString = aAttr.getValue ();
          else
            if (!aAttrName.hasNamespace ())
              throw XPathException.of ("SEPM0017", "Unexpected attribute " + aAttrName + " on character-map");
      }
      if (sChar == null || sMapString == null)
        throw XPathException.of ("SEPM0017", "character-map needs the attributes character and map-string");
      if (sChar.codePointCount (0, sChar.length ()) != 1)
        throw XPathException.of ("SEPM0017", "character-map/@character must be a single character");
      final Integer aCP = Integer.valueOf (sChar.codePointAt (0));
      if (!aSeen.add (aCP))
        throw XPathException.of ("SEPM0018", "Character '" + sChar + "' is mapped more than once");
      aParams.addCharacterMapping (aCP.intValue (), sMapString);
    }
  }

  private static void _applyElementParam (@NonNull final SerializationParams aParams,
                                          @NonNull final String sName,
                                          @NonNull final String sRawValue,
                                          @NonNull final IXdmNode aElement)
  {
    final String sValue = "item-separator".equals (sName) ? sRawValue : sRawValue.trim ();
    switch (sName)
    {
      case "allow-duplicate-names":
        aParams.setAllowDuplicateNames (_parseBoolean (sValue, sName));
        break;
      case "byte-order-mark":
        aParams.setByteOrderMark (_parseBoolean (sValue, sName));
        break;
      case "escape-uri-attributes":
        aParams.setEscapeUriAttributes (_parseBoolean (sValue, sName));
        break;
      case "include-content-type":
        aParams.setIncludeContentType (_parseBoolean (sValue, sName));
        break;
      case "indent":
        aParams.setIndent (_parseBoolean (sValue, sName));
        break;
      case "omit-xml-declaration":
        aParams.setOmitXmlDeclaration (_parseBoolean (sValue, sName));
        break;
      case "undeclare-prefixes":
        aParams.setUndeclarePrefixes (_parseBoolean (sValue, sName));
        break;
      case "standalone":
        if ("omit".equals (sValue))
          aParams.setStandalone (SerializationParams.STANDALONE_OMIT);
        else
          aParams.setStandalone (_parseBoolean (sValue, sName) ? SerializationParams.STANDALONE_YES
                                                               : SerializationParams.STANDALONE_NO);
        break;
      case "cdata-section-elements":
      case "suppress-indentation":
        for (final String sToken : sValue.split ("\\s+"))
          if (!sToken.isEmpty ())
          {
            final XdmQName aQName = _resolveQName (sToken, aElement);
            if ("cdata-section-elements".equals (sName))
              aParams.addCdataSectionElement (aQName);
            else
              aParams.addSuppressIndentation (aQName);
          }
        break;
      case "html-version":
        try
        {
          aParams.setHtmlVersion (new BigDecimal (sValue));
        }
        catch (final NumberFormatException ex)
        {
          throw XPathException.of ("SEPM0017", "Invalid html-version '" + sValue + "'");
        }
        break;
      case "method":
      case "json-node-output-method":
      {
        final XdmQName aQName = _resolveQName (sValue, aElement);
        if (aQName.hasNamespace ())
          throw XPathException.of ("SEPM0016", "Unsupported serialization method " + aQName.getEQName ());
        final String sMethod = _checkMethod (aQName.localName (), "SEPM0017");
        if ("method".equals (sName))
          aParams.setMethod (sMethod);
        else
          aParams.setJsonNodeOutputMethod (sMethod);
        break;
      }
      case "doctype-public":
        aParams.setDoctypePublic (sValue);
        break;
      case "doctype-system":
        aParams.setDoctypeSystem (sValue);
        break;
      case "encoding":
        aParams.setEncoding (sValue);
        break;
      case "item-separator":
        aParams.setItemSeparator (sValue);
        break;
      case "media-type":
        aParams.setMediaType (sValue);
        break;
      case "normalization-form":
        aParams.setNormalizationForm (_normaliseNormalizationForm (sValue, "SEPM0017"));
        break;
      case "version":
        aParams.setVersion (sValue);
        break;
      default:
        throw new IllegalStateException (sName);
    }
  }

  /**
   * @param aItem
   *        An item.
   * @return <code>true</code> if it is an element named
   *         <code>output:serialization-parameters</code>.
   */
  public static boolean isParamsElement (@NonNull final IXdmItem aItem)
  {
    return aItem instanceof final IXdmNode aNode &&
           aNode.getKind () == ENodeKind.ELEMENT &&
           aNode.getName ().isSameName (SerializationParams.NS_OUTPUT, "serialization-parameters");
  }

  /**
   * Read the element form: an <code>output:serialization-parameters</code>
   * element that must be valid against the schema for serialization
   * parameters.
   *
   * @param aElement
   *        The element.
   * @return The parameters, starting from the fn:serialize defaults.
   * @throws XPathException
   *         SEPM0017 for an invalid element, SEPM0018 for a character mapped
   *         twice, SEPM0019 for a parameter given twice.
   */
  @NonNull
  public static SerializationParams fromElement (@NonNull final IXdmNode aElement)
  {
    final SerializationParams ret = new SerializationParams ();
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
      if (!aAttr.getName ().hasNamespace ())
        throw XPathException.of ("SEPM0017",
                                 "Unexpected attribute " + aAttr.getName () + " on serialization-parameters");
    final ICommonsSet <XdmQName> aSeen = new CommonsHashSet <> ();
    for (final IXdmNode aChild : aElement.getAllChildren ())
    {
      if (aChild.getKind () == ENodeKind.TEXT)
      {
        if (!aChild.getValue ().isBlank ())
          throw XPathException.of ("SEPM0017", "Text is not allowed in serialization-parameters");
        continue;
      }
      if (aChild.getKind () != ENodeKind.ELEMENT)
        continue;
      final XdmQName aName = aChild.getName ();
      if (!aSeen.add (new XdmQName (aName.namespaceUri (), aName.localName ())))
        throw XPathException.of ("SEPM0019", "Serialization parameter " + aName + " is given more than once");
      if (!aName.hasNamespace ())
        throw XPathException.of ("SEPM0017", "Serialization parameter " + aName + " is in no namespace");
      if (!SerializationParams.NS_OUTPUT.equals (aName.namespaceUri ()))
      {
        // An implementation-defined parameter that is not supported
        continue;
      }
      final String sLocal = aName.localName ();
      if (!PARAM_NAMES.contains (sLocal))
        throw XPathException.of ("SEPM0017", "Unknown serialization parameter '" + sLocal + "'");
      if ("use-character-maps".equals (sLocal))
      {
        _readCharacterMaps (ret, aChild);
        continue;
      }
      String sValue = null;
      for (final IXdmNode aAttr : aChild.getAllAttributes ())
      {
        if (aAttr.getName ().isSameName ("", "value"))
          sValue = aAttr.getValue ();
        else
          if (!aAttr.getName ().hasNamespace ())
            throw XPathException.of ("SEPM0017", "Unexpected attribute " + aAttr.getName () + " on " + sLocal);
      }
      for (final IXdmNode aGrandChild : aChild.getAllChildren ())
        if (aGrandChild.getKind () == ENodeKind.ELEMENT ||
            aGrandChild.getKind () == ENodeKind.TEXT && !aGrandChild.getValue ().isBlank ())
          throw XPathException.of ("SEPM0017", "Serialization parameter " + sLocal + " must be empty");
      if (sValue == null)
        throw XPathException.of ("SEPM0017", "Serialization parameter " + sLocal + " has no value attribute");
      _applyElementParam (ret, sLocal, sValue, aChild);
    }
    return ret;
  }
}
