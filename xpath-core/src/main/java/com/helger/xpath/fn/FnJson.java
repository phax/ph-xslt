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
package com.helger.xpath.fn;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.XPathException;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.eval.XPathEvaluator;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.SerializationParamsReader;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.AtomicLexical;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * Function family: fn:parse-json, json-doc, json-to-xml, xml-to-json,
 * serialize, parse-xml, parse-xml-fragment.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnJson
{
  private static final String NS_FN = XdmConstants.NS_FN;

  /** The resolved options of the JSON parsing functions. */
  private record JsonOptions (String duplicates, boolean escape, UnaryOperator <String> fallback, boolean validate)
  {}

  /** A container being built while parsing into XDM maps and arrays. */
  private static final class XdmFrame
  {
    private final XdmMap.Builder m_aMap;
    private final XdmArray.Builder m_aArray;
    private String m_sKey;

    XdmFrame (final boolean bObject)
    {
      m_aMap = bObject ? new XdmMap.Builder () : null;
      m_aArray = bObject ? null : new XdmArray.Builder ();
    }
  }

  /** Builds the XDM value of fn:parse-json. */
  private static final class XdmHandler implements JsonParser.IHandler
  {
    private final String m_sDuplicates;
    private final ArrayDeque <XdmFrame> m_aStack = new ArrayDeque <> ();
    private XdmSequence m_aResult = XdmSequence.EMPTY;

    XdmHandler (@NonNull final String sDuplicates)
    {
      m_sDuplicates = sDuplicates;
    }

    private void _value (@NonNull final XdmSequence aValue)
    {
      final XdmFrame aTop = m_aStack.peek ();
      if (aTop == null)
        m_aResult = aValue;
      else
        if (aTop.m_aArray != null)
          aTop.m_aArray.add (aValue);
        else
        {
          final XdmAtomic aKey = XdmAtomic.string (aTop.m_sKey);
          if (aTop.m_aMap.containsKey (aKey))
          {
            switch (m_sDuplicates)
            {
              case "reject":
                throw XPathException.of ("FOJS0003", "Duplicate key '" + aTop.m_sKey + "' in the JSON input");
              case "use-last":
                aTop.m_aMap.put (aKey, aValue);
                break;
              default:
                break;
            }
          }
          else
            aTop.m_aMap.put (aKey, aValue);
        }
    }

    public void startObject ()
    {
      m_aStack.push (new XdmFrame (true));
    }

    public void key (final String sKey, final boolean bEscaped)
    {
      m_aStack.peek ().m_sKey = sKey;
    }

    public void endObject ()
    {
      _value (XdmSequence.of (m_aStack.pop ().m_aMap.build ()));
    }

    public void startArray ()
    {
      m_aStack.push (new XdmFrame (false));
    }

    public void endArray ()
    {
      _value (XdmSequence.of (m_aStack.pop ().m_aArray.build ()));
    }

    public void string (final String sValue, final boolean bEscaped)
    {
      _value (XdmSequence.of (XdmAtomic.string (sValue)));
    }

    public void number (final String sLexical)
    {
      _value (XdmSequence.of (XdmAtomic.dbl (Double.parseDouble (sLexical))));
    }

    public void bool (final boolean bValue)
    {
      _value (XdmSequence.of (XdmAtomic.bool (bValue)));
    }

    public void nul ()
    {
      _value (XdmSequence.EMPTY);
    }
  }

  /** A map or array element being built by fn:json-to-xml. */
  private static final class XmlFrame
  {
    private final XdmNode m_aElement;
    private final boolean m_bObject;
    private final ICommonsSet <String> m_aKeys;
    private String m_sKey;
    private boolean m_bKeyEscaped;

    XmlFrame (@NonNull final XdmNode aElement, final boolean bObject)
    {
      m_aElement = aElement;
      m_bObject = bObject;
      m_aKeys = bObject ? new CommonsHashSet <> () : null;
    }
  }

  /** Builds the XML representation of fn:json-to-xml. */
  private static final class XmlHandler implements JsonParser.IHandler
  {
    private final String m_sDuplicates;
    private final ArrayDeque <XmlFrame> m_aStack = new ArrayDeque <> ();
    private XdmNode m_aResult;

    XmlHandler (@NonNull final String sDuplicates)
    {
      m_sDuplicates = sDuplicates;
    }

    @NonNull
    private static XdmNode _element (@NonNull final String sLocalName)
    {
      return XdmNode.createElement (new XdmQName (NS_FN, sLocalName, ""));
    }

    private void _value (@NonNull final XdmNode aElement)
    {
      final XmlFrame aTop = m_aStack.peek ();
      if (aTop == null)
      {
        m_aResult = aElement;
        return;
      }
      if (aTop.m_bObject)
      {
        if (!aTop.m_aKeys.add (aTop.m_bKeyEscaped ? _unescape (aTop.m_sKey) : aTop.m_sKey))
        {
          switch (m_sDuplicates)
          {
            case "reject":
              throw XPathException.of ("FOJS0003", "Duplicate key '" + aTop.m_sKey + "' in the JSON input");
            case "use-first":
              return;
            default:
              break;
          }
        }
        aElement.addAttribute (XdmNode.createAttribute (new XdmQName ("key"), aTop.m_sKey));
        if (aTop.m_bKeyEscaped)
          aElement.addAttribute (XdmNode.createAttribute (new XdmQName ("escaped-key"), "true"));
      }
      aTop.m_aElement.appendChild (aElement);
    }

    @NonNull
    private static XdmNode _textElement (@NonNull final String sLocalName, @NonNull final String sText)
    {
      final XdmNode ret = _element (sLocalName);
      if (!sText.isEmpty ())
        ret.appendChild (XdmNode.createText (sText));
      return ret;
    }

    public void startObject ()
    {
      m_aStack.push (new XmlFrame (_element ("map"), true));
    }

    public void key (final String sKey, final boolean bEscaped)
    {
      final XmlFrame aTop = m_aStack.peek ();
      aTop.m_sKey = sKey;
      aTop.m_bKeyEscaped = bEscaped;
    }

    public void endObject ()
    {
      _value (m_aStack.pop ().m_aElement);
    }

    public void startArray ()
    {
      m_aStack.push (new XmlFrame (_element ("array"), false));
    }

    public void endArray ()
    {
      _value (m_aStack.pop ().m_aElement);
    }

    public void string (final String sValue, final boolean bEscaped)
    {
      final XdmNode aElement = _textElement ("string", sValue);
      if (bEscaped)
        aElement.addAttribute (XdmNode.createAttribute (new XdmQName ("escaped"), "true"));
      _value (aElement);
    }

    public void number (final String sLexical)
    {
      _value (_textElement ("number", sLexical));
    }

    public void bool (final boolean bValue)
    {
      _value (_textElement ("boolean", bValue ? "true" : "false"));
    }

    public void nul ()
    {
      _value (_element ("null"));
    }
  }

  @PresentForCodeCoverage
  private static final FnJson INSTANCE = new FnJson ();

  private FnJson ()
  {}

  /**
   * Decode the JSON escape sequences of an escaped string or key. Used to
   * compare keys.
   */
  @NonNull
  private static String _unescape (@NonNull final String s)
  {
    if (s.indexOf ('\\') < 0)
      return s;
    final StringBuilder aSB = new StringBuilder (s.length ());
    int i = 0;
    while (i < s.length ())
    {
      final char c = s.charAt (i);
      if (c != '\\')
      {
        aSB.append (c);
        i++;
        continue;
      }
      if (i + 1 >= s.length ())
        throw XPathException.of ("FOJS0007", "Trailing backslash in an escaped string");
      final char cEsc = s.charAt (i + 1);
      switch (cEsc)
      {
        case '"':
        case '\\':
        case '/':
          aSB.append (cEsc);
          break;
        case 'b':
          aSB.append ('\b');
          break;
        case 'f':
          aSB.append ('\f');
          break;
        case 'n':
          aSB.append ('\n');
          break;
        case 'r':
          aSB.append ('\r');
          break;
        case 't':
          aSB.append ('\t');
          break;
        case 'u':
        {
          if (i + 6 > s.length ())
            throw XPathException.of ("FOJS0007", "Truncated \\u escape in an escaped string");
          int n = 0;
          for (int j = i + 2; j < i + 6; ++j)
          {
            final int d = Character.digit (s.charAt (j), 16);
            if (d < 0)
              throw XPathException.of ("FOJS0007", "Invalid \\u escape in an escaped string");
            n = n * 16 + d;
          }
          aSB.append ((char) n);
          i += 6;
          continue;
        }
        default:
          throw XPathException.of ("FOJS0007", "Invalid escape \\" + cEsc + " in an escaped string");
      }
      i += 2;
    }
    return aSB.toString ();
  }

  @NonNull
  private static JsonOptions _readOptions (@Nullable final XdmSequence aOptionsArg,
                                           @NonNull final DynamicContext aCtx,
                                           final boolean bJsonToXml)
  {
    if (aOptionsArg == null || aOptionsArg.isEmpty ())
      return new JsonOptions (bJsonToXml ? "retain" : "use-first", false, null, false);
    final XdmMap aMap = (XdmMap) aOptionsArg.get (0);
    FnOptionMap.getBoolean (aMap, "liberal", false, aCtx);
    final boolean bValidate = bJsonToXml && FnOptionMap.getBoolean (aMap, "validate", false, aCtx);
    final boolean bEscape = FnOptionMap.getBoolean (aMap, "escape", false, aCtx);
    final String sDuplicates = FnOptionMap.getString (aMap,
                                                      "duplicates",
                                                      bJsonToXml && !bValidate ? "retain" : "use-first",
                                                      aCtx);
    final boolean bValidDuplicates = bJsonToXml ? "reject".equals (sDuplicates) ||
                                                  "use-first".equals (sDuplicates) ||
                                                  "retain".equals (sDuplicates)
                                                : "reject".equals (sDuplicates) ||
                                                  "use-first".equals (sDuplicates) ||
                                                  "use-last".equals (sDuplicates);
    if (!bValidDuplicates)
      throw XPathException.of ("FOJS0005", "Invalid value '" + sDuplicates + "' for option 'duplicates'");
    if (bValidate && "retain".equals (sDuplicates))
      throw XPathException.of ("FOJS0005", "The options validate=true and duplicates=retain cannot be combined");
    final XdmSequence aFallback = FnOptionMap.getOption (aMap, "fallback", FnOptionMap.FUNCTION_1, aCtx);
    UnaryOperator <String> aFallbackFunc = null;
    if (aFallback != null && !aFallback.isEmpty ())
    {
      if (bEscape)
        throw XPathException.of ("FOJS0005", "The options escape=true and fallback cannot be combined");
      final IXdmItem aFunc = aFallback.get (0);
      aFallbackFunc = sSeq -> {
        final XdmSequence aResult = XPathEvaluator.callFunction (aCtx, aFunc, List.of (XdmSequence.of (XdmAtomic.string (sSeq))));
        return ((XdmAtomic) aResult.get (0)).getString ();
      };
    }
    return new JsonOptions (sDuplicates, bEscape, aFallbackFunc, bValidate);
  }

  @NonNull
  private static XdmSequence _parseJson (@NonNull final String sJson, @NonNull final JsonOptions aOptions)
  {
    final XdmHandler aHandler = new XdmHandler (aOptions.duplicates ());
    new JsonParser (sJson, aOptions.escape (), aOptions.fallback ()).parse (aHandler);
    return aHandler.m_aResult;
  }

  @NonNull
  private static XdmSequence _fnParseJson (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final JsonOptions aOptions = _readOptions (aArgs.size () > 1 ? aArgs.get (1) : null, aCtx, false);
    return _parseJson (((XdmAtomic) aArgs.get (0).get (0)).getString (), aOptions);
  }

  @NonNull
  private static String _resolveUri (@NonNull final String sHref, @NonNull final DynamicContext aCtx)
  {
    try
    {
      final URI aUri = new URI (sHref);
      if (aUri.isAbsolute ())
        return aUri.toString ();
      final String sBase = aCtx.getStaticContext ().getBaseUri ();
      if (sBase == null)
        throw XPathException.of ("FOUT1170", "Cannot resolve the relative URI '" + sHref + "' without a base URI");
      return new URI (sBase).resolve (aUri).toString ();
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      throw XPathException.of ("FOUT1170", "Invalid URI '" + sHref + "'");
    }
  }

  @NonNull
  private static XdmSequence _fnJsonDoc (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final String sHref = ((XdmAtomic) aArgs.get (0).get (0)).getString ();
    final JsonOptions aOptions = _readOptions (aArgs.size () > 1 ? aArgs.get (1) : null, aCtx, false);
    final String sUri = _resolveUri (sHref, aCtx);
    final String sText = aCtx.getResourceResolver ().resolveText (sUri, null);
    if (sText == null)
      throw XPathException.of ("FOUT1170", "Cannot retrieve the resource '" + sUri + "'");
    return _parseJson (sText, aOptions);
  }

  @NonNull
  private static XdmSequence _fnJsonToXml (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final JsonOptions aOptions = _readOptions (aArgs.size () > 1 ? aArgs.get (1) : null, aCtx, true);
    final XmlHandler aHandler = new XmlHandler (aOptions.duplicates ());
    new JsonParser (((XdmAtomic) aArgs.get (0).get (0)).getString (),
                    aOptions.escape (),
                    aOptions.fallback ()).parse (aHandler);
    if (aOptions.validate ())
      throw XPathException.of ("FOJS0004", "The option validate=true requires a schema-aware processor");
    final XdmNode aDoc = XdmNode.createDocument ();
    final XdmNode aRoot = aHandler.m_aResult;
    aRoot.declareNamespace ("", NS_FN);
    aDoc.appendChild (aRoot);
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    aDoc.assignDocumentOrder ();
    return XdmSequence.of (aDoc);
  }

  private static boolean _isWhitespace (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
        return false;
    }
    return true;
  }

  @NonNull
  private static XPathException _invalid (@NonNull final String sMessage)
  {
    return XPathException.of ("FOJS0006", "Invalid XML representation of JSON: " + sMessage);
  }

  /** @return The value of a boolean attribute, <code>false</code> if absent. */
  private static boolean _getBooleanAttr (@NonNull final XdmNode aElement, @NonNull final String sName)
  {
    final XdmNode aAttr = aElement.getAttribute ("", sName);
    if (aAttr == null)
      return false;
    switch (aAttr.getValue ().trim ())
    {
      case "true":
      case "1":
        return true;
      case "false":
      case "0":
        return false;
      default:
        throw _invalid ("attribute " + sName + "='" + aAttr.getValue () + "' is not an xs:boolean");
    }
  }

  private static void _checkElement (@NonNull final XdmNode aElement, final boolean bInMap)
  {
    final XdmQName aName = aElement.getName ();
    if (!NS_FN.equals (aName.namespaceUri ()))
      throw _invalid ("element " + aName.getEQName () + " is not in the namespace " + NS_FN);
    final String sLocal = aName.localName ();
    for (final XdmNode aAttr : aElement.getAllAttributes ())
    {
      final XdmQName aAttrName = aAttr.getName ();
      if (aAttrName.hasNamespace ())
      {
        if (NS_FN.equals (aAttrName.namespaceUri ()))
          throw _invalid ("attribute " + aAttrName.getEQName () + " is in the namespace " + NS_FN);
        continue;
      }
      final String sAttr = aAttrName.localName ();
      // key and escaped-key are only significant within a map but allowed
      // everywhere
      final boolean bAllowed = "key".equals (sAttr) ||
                               "escaped-key".equals (sAttr) ||
                               "string".equals (sLocal) && "escaped".equals (sAttr);
      if (!bAllowed)
        throw _invalid ("unexpected attribute " + sAttr + " on " + sLocal);
    }
    if (bInMap && aElement.getAttribute ("", "key") == null)
      throw _invalid ("a child of map has no key attribute");
    switch (sLocal)
    {
      case "map":
      case "array":
        for (final XdmNode aChild : aElement.getAllChildren ())
          if (aChild.getKind () == ENodeKind.TEXT && !_isWhitespace (aChild.getValue ()))
            throw _invalid ("text content in " + sLocal);
        break;
      case "string":
      case "number":
      case "boolean":
      case "null":
        for (final XdmNode aChild : aElement.getAllChildren ())
          if (aChild.getKind () == ENodeKind.ELEMENT)
            throw _invalid ("element content in " + sLocal);
        break;
      default:
        throw _invalid ("unexpected element " + sLocal);
    }
  }

  /**
   * Validate an escaped string and return the form to write between quotes:
   * valid escape sequences are kept, other special characters are escaped.
   */
  @NonNull
  private static String _reescape (@NonNull final String s)
  {
    // Validates the escape sequences (FOJS0007)
    _unescape (s);
    final StringBuilder aSB = new StringBuilder (s.length ());
    int i = 0;
    while (i < s.length ())
    {
      final char c = s.charAt (i);
      if (c == '\\')
      {
        final int nLen = s.charAt (i + 1) == 'u' ? 6 : 2;
        aSB.append (s, i, i + nLen);
        i += nLen;
        continue;
      }
      switch (c)
      {
        case '"':
          aSB.append ("\\\"");
          break;
        case '/':
          aSB.append ("\\/");
          break;
        case '\b':
          aSB.append ("\\b");
          break;
        case '\f':
          aSB.append ("\\f");
          break;
        case '\n':
          aSB.append ("\\n");
          break;
        case '\r':
          aSB.append ("\\r");
          break;
        case '\t':
          aSB.append ("\\t");
          break;
        default:
          if (c < 0x20 || c >= 0x7F && c <= 0x9F)
            aSB.append (String.format (Locale.ROOT, "\\u%04X", Integer.valueOf (c)));
          else
            aSB.append (c);
      }
      i++;
    }
    return aSB.toString ();
  }

  private static void _appendJsonString (@NonNull final StringBuilder aSB,
                                         @NonNull final String s,
                                         final boolean bEscaped)
  {
    if (bEscaped)
      aSB.append ('"').append (_reescape (s)).append ('"');
    else
      XdmSerializer.appendJsonString (aSB, s);
  }

  private static void _newline (@NonNull final StringBuilder aSB, final boolean bIndent, final int nDepth)
  {
    if (bIndent)
    {
      aSB.append ('\n');
      for (int i = 0; i < nDepth; ++i)
        aSB.append ("  ");
    }
  }

  /** A pending element of fn:xml-to-json, or a literal to write. */
  private record XmlTask (XdmNode element, boolean inMap, int depth, String literal)
  {}

  @NonNull
  private static String _xmlToJson (@NonNull final XdmNode aRoot, final boolean bIndent)
  {
    final StringBuilder aSB = new StringBuilder ();
    final ArrayDeque <XmlTask> aStack = new ArrayDeque <> ();
    aStack.push (new XmlTask (aRoot, false, 0, null));
    while (!aStack.isEmpty ())
    {
      final XmlTask aTask = aStack.pop ();
      if (aTask.literal () != null)
      {
        aSB.append (aTask.literal ());
        continue;
      }
      final XdmNode aElement = aTask.element ();
      _checkElement (aElement, aTask.inMap ());
      final int nDepth = aTask.depth ();
      switch (aElement.getName ().localName ())
      {
        case "map":
        case "array":
        {
          final boolean bMap = "map".equals (aElement.getName ().localName ());
          final ICommonsList <XdmNode> aChildren = new CommonsArrayList <> ();
          for (final XdmNode aChild : aElement.getAllChildren ())
            if (aChild.getKind () == ENodeKind.ELEMENT)
              aChildren.add (aChild);
          aSB.append (bMap ? '{' : '[');
          final ICommonsSet <String> aKeys = new CommonsHashSet <> ();
          final StringBuilder aClose = new StringBuilder ();
          if (!aChildren.isEmpty ())
            _newline (aClose, bIndent, nDepth);
          aClose.append (bMap ? '}' : ']');
          aStack.push (new XmlTask (null, false, 0, aClose.toString ()));
          final String [] aPrefixes = new String [aChildren.size ()];
          for (int i = 0; i < aChildren.size (); ++i)
          {
            final XdmNode aChild = aChildren.get (i);
            final StringBuilder aPrefix = new StringBuilder ();
            if (i > 0)
              aPrefix.append (',');
            _newline (aPrefix, bIndent, nDepth + 1);
            if (bMap)
            {
              final XdmNode aKeyAttr = aChild.getAttribute ("", "key");
              if (aKeyAttr == null)
                throw _invalid ("a child of map has no key attribute");
              final boolean bKeyEscaped = _getBooleanAttr (aChild, "escaped-key");
              final String sKey = aKeyAttr.getValue ();
              if (!aKeys.add (bKeyEscaped ? _unescape (sKey) : sKey))
                throw _invalid ("duplicate key '" + sKey + "'");
              _appendJsonString (aPrefix, sKey, bKeyEscaped);
              aPrefix.append (bIndent ? ": " : ":");
            }
            aPrefixes[i] = aPrefix.toString ();
          }
          for (int i = aChildren.size () - 1; i >= 0; --i)
          {
            aStack.push (new XmlTask (aChildren.get (i), bMap, nDepth + 1, null));
            aStack.push (new XmlTask (null, false, 0, aPrefixes[i]));
          }
          break;
        }
        case "string":
          _appendJsonString (aSB, aElement.getStringValue (), _getBooleanAttr (aElement, "escaped"));
          break;
        case "number":
        {
          final String sValue = aElement.getStringValue ().trim ();
          final double d;
          try
          {
            d = Double.parseDouble (sValue);
          }
          catch (final NumberFormatException ex)
          {
            throw _invalid ("invalid number '" + sValue + "'");
          }
          if (Double.isNaN (d) || Double.isInfinite (d) || !sValue.matches ("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?"))
            throw _invalid ("invalid number '" + sValue + "'");
          aSB.append (AtomicLexical.formatDouble (d));
          break;
        }
        case "boolean":
          switch (aElement.getStringValue ().trim ())
          {
            case "true":
            case "1":
              aSB.append ("true");
              break;
            case "false":
            case "0":
              aSB.append ("false");
              break;
            default:
              throw _invalid ("invalid boolean '" + aElement.getStringValue () + "'");
          }
          break;
        case "null":
          if (!_isWhitespace (aElement.getStringValue ()))
            throw _invalid ("null must be empty");
          aSB.append ("null");
          break;
        default:
          throw _invalid ("unexpected element " + aElement.getName ().localName ());
      }
    }
    return aSB.toString ();
  }

  @NonNull
  private static XdmSequence _fnXmlToJson (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final boolean bIndent = aArgs.size () > 1 &&
                            !aArgs.get (1).isEmpty () &&
                            FnOptionMap.getBoolean ((XdmMap) aArgs.get (1).get (0), "indent", false, aCtx);
    XdmNode aNode = (XdmNode) aArgs.get (0).get (0);
    if (aNode.getKind () == ENodeKind.DOCUMENT)
    {
      XdmNode aElement = null;
      for (final XdmNode aChild : aNode.getAllChildren ())
        switch (aChild.getKind ())
        {
          case ELEMENT:
            if (aElement != null)
              throw _invalid ("the document has more than one element child");
            aElement = aChild;
            break;
          case TEXT:
            if (!_isWhitespace (aChild.getValue ()))
              throw _invalid ("the document has text content");
            break;
          default:
            break;
        }
      if (aElement == null)
        throw _invalid ("the document has no element child");
      aNode = aElement;
    }
    if (aNode.getKind () != ENodeKind.ELEMENT)
      throw _invalid ("the input must be a document or element node");
    return XdmSequence.of (XdmAtomic.string (_xmlToJson (aNode, bIndent)));
  }

  @NonNull
  private static XdmSequence _fnSerialize (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    SerializationParams aParams = null;
    if (aArgs.size () > 1 && !aArgs.get (1).isEmpty ())
    {
      final IXdmItem aParamItem = aArgs.get (1).get (0);
      if (aParamItem instanceof final XdmMap aMap)
        aParams = SerializationParamsReader.fromMap (aMap, aCtx);
      else
        if (SerializationParamsReader.isParamsElement (aParamItem))
          aParams = SerializationParamsReader.fromElement ((XdmNode) aParamItem);
        else
          throw XPathException.type ("The serialization parameters must be a map or an output:serialization-parameters element");
    }
    if (aParams == null)
      aParams = new SerializationParams ();
    return XdmSequence.of (XdmAtomic.string (XdmSerializer.serialize (aArgs.get (0), aParams)));
  }

  @NonNull
  private static XdmNode _parse (@NonNull final String sXml)
  {
    try
    {
      return new XdmParser ().parse (sXml);
    }
    catch (final XdmParseException ex)
    {
      throw new XPathException ("FODC0006", "The string is not a well-formed XML document: " + ex.getMessage (), ex);
    }
  }

  @NonNull
  private static XdmSequence _fnParseXml (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final XdmNode aDoc = _parse (((XdmAtomic) aArgs.get (0).get (0)).getString ());
    aDoc.setDocumentUri (null);
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    return XdmSequence.of (aDoc);
  }

  /** Deep copy of a node into a new parent, iteratively. */
  private static void _copyChildren (@NonNull final XdmNode aFrom, @NonNull final XdmNode aTo)
  {
    final ArrayDeque <XdmNode []> aStack = new ArrayDeque <> ();
    aStack.push (new XdmNode [] { aFrom, aTo });
    while (!aStack.isEmpty ())
    {
      final XdmNode [] aPair = aStack.pop ();
      for (final XdmNode aChild : aPair[0].getAllChildren ())
      {
        final XdmNode aCopy;
        switch (aChild.getKind ())
        {
          case ELEMENT:
            aCopy = XdmNode.createElement (aChild.getName ());
            for (final var aEntry : aChild.getDeclaredNamespaces ().entrySet ())
              aCopy.declareNamespace (aEntry.getKey (), aEntry.getValue ());
            for (final XdmNode aAttr : aChild.getAllAttributes ())
            {
              final XdmNode aAttrCopy = XdmNode.createAttribute (aAttr.getName (), aAttr.getValue ());
              aAttrCopy.setIDKind (aAttr.getIDKind ());
              aCopy.addAttribute (aAttrCopy);
            }
            aStack.push (new XdmNode [] { aChild, aCopy });
            break;
          case TEXT:
            aCopy = XdmNode.createText (aChild.getValue ());
            break;
          case COMMENT:
            aCopy = XdmNode.createComment (aChild.getValue ());
            break;
          case PROCESSING_INSTRUCTION:
            aCopy = XdmNode.createProcessingInstruction (aChild.getName ().localName (), aChild.getValue ());
            break;
          default:
            continue;
        }
        aPair[1].appendChild (aCopy);
      }
    }
  }

  @NonNull
  private static XdmSequence _fnParseXmlFragment (@NonNull final DynamicContext aCtx,
                                                  @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    String s = ((XdmAtomic) aArgs.get (0).get (0)).getString ();
    if (s.startsWith ("\uFEFF"))
      s = s.substring (1);
    if (s.startsWith ("<?xml") && s.length () > 5 && Character.isWhitespace (s.charAt (5)))
    {
      final int nEnd = s.indexOf ("?>");
      if (nEnd < 0)
        throw XPathException.of ("FODC0006", "Unterminated text declaration");
      final String sDecl = s.substring (0, nEnd);
      if (!sDecl.matches ("<\\?xml(\\s+version\\s*=\\s*(\"1\\.[0-9]+\"|'1\\.[0-9]+'))?\\s+encoding\\s*=\\s*(\"[A-Za-z][A-Za-z0-9._-]*\"|'[A-Za-z][A-Za-z0-9._-]*')\\s*"))
        throw XPathException.of ("FODC0006", "Invalid text declaration in an XML fragment");
      s = s.substring (nEnd + 2);
    }
    if (s.contains ("<!DOCTYPE"))
      throw XPathException.of ("FODC0006", "A DOCTYPE declaration is not allowed in an XML fragment");
    final XdmNode aWrapperDoc = _parse ("<fragment-wrapper>" + s + "</fragment-wrapper>");
    final XdmNode aWrapper = aWrapperDoc.getChildAtIndex (0);
    final XdmNode aDoc = XdmNode.createDocument ();
    _copyChildren (aWrapper, aDoc);
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    aDoc.assignDocumentOrder ();
    return XdmSequence.of (aDoc);
  }

  public static void register (@NonNull final FunctionLibrary aLib)
  {
    final String sNS = NS_FN;
    aLib.register (sNS, "parse-json", "(xs:string?) as item()?", FnJson::_fnParseJson);
    aLib.register (sNS, "parse-json", "(xs:string?, map(*)) as item()?", FnJson::_fnParseJson);
    aLib.register (sNS, "json-doc", "(xs:string?) as item()?", FnJson::_fnJsonDoc);
    aLib.register (sNS, "json-doc", "(xs:string?, map(*)) as item()?", FnJson::_fnJsonDoc);
    aLib.register (sNS, "json-to-xml", "(xs:string?) as document-node()?", FnJson::_fnJsonToXml);
    aLib.register (sNS, "json-to-xml", "(xs:string?, map(*)) as document-node()?", FnJson::_fnJsonToXml);
    aLib.register (sNS, "xml-to-json", "(node()?) as xs:string?", FnJson::_fnXmlToJson);
    aLib.register (sNS, "xml-to-json", "(node()?, map(*)) as xs:string?", FnJson::_fnXmlToJson);
    aLib.register (sNS, "serialize", "(item()*) as xs:string", FnJson::_fnSerialize);
    aLib.register (sNS, "serialize", "(item()*, item()?) as xs:string", FnJson::_fnSerialize);
    aLib.register (sNS,
                   "parse-xml",
                   "(xs:string?) as document-node(element(*))?",
                   FnJson::_fnParseXml);
    aLib.register (sNS,
                   "parse-xml-fragment",
                   "(xs:string?) as document-node()?",
                   FnJson::_fnParseXmlFragment);
  }
}
