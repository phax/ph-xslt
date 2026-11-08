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
package com.helger.phoxtrot.xpath.fn;

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
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmValidationException;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.DynamicContext;
import com.helger.phoxtrot.xpath.eval.XPathEvaluator;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.SerializationParamsReader;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.AtomicLexical;
import com.helger.phoxtrot.xpath.value.XdmArray;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmMap;
import com.helger.phoxtrot.xpath.value.XdmSequence;

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

    /**
     * Put a finished value into the innermost open container: as a member of an
     * array, or under the pending key of a map, where a key that is already
     * present raises FOJS0003 with <code>duplicates=reject</code>, replaces the
     * value with <code>use-last</code> and keeps the first value otherwise. With
     * no open container it becomes the result.
     *
     * @param aValue
     *        The value (the empty sequence for JSON null).
     * @throws XPathException
     *         FOJS0003 for a duplicate key with <code>duplicates=reject</code>.
     */
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

    /**
     * A JSON number becomes an xs:double via {@link Double#parseDouble(String)};
     * a magnitude beyond the double range becomes an infinity.
     *
     * @param sLexical
     *        The number as written.
     */
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
    private final IXdmNode m_aElement;
    private final boolean m_bObject;
    private final ICommonsSet <String> m_aKeys;
    private String m_sKey;
    private boolean m_bKeyEscaped;

    XmlFrame (@NonNull final IXdmNode aElement, final boolean bObject)
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
    private IXdmNode m_aResult;

    XmlHandler (@NonNull final String sDuplicates)
    {
      m_sDuplicates = sDuplicates;
    }

    /**
     * @param sLocalName
     *        A local name of the XML representation of JSON.
     * @return A new element in the fn namespace without prefix.
     */
    @NonNull
    private static IXdmNode _element (@NonNull final String sLocalName)
    {
      return IXdmNode.createElement (new XdmQName (NS_FN, sLocalName, ""));
    }

    /**
     * Put a finished element into the innermost open <code>fn:map</code> or
     * <code>fn:array</code> element, or make it the result if there is none. In
     * a map it gets the pending key as <code>key</code> attribute (plus
     * <code>escaped-key="true"</code> for an escaped key); keys are compared
     * after unescaping. A duplicate key raises FOJS0003 with
     * <code>duplicates=reject</code>, is dropped with <code>use-first</code> and
     * kept with <code>retain</code>.
     *
     * @param aElement
     *        The element of the value.
     * @throws XPathException
     *         FOJS0003 for a duplicate key with <code>duplicates=reject</code>.
     */
    private void _value (@NonNull final IXdmNode aElement)
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
        aElement.addAttribute (IXdmNode.createAttribute (new XdmQName ("key"), aTop.m_sKey));
        if (aTop.m_bKeyEscaped)
          aElement.addAttribute (IXdmNode.createAttribute (new XdmQName ("escaped-key"), "true"));
      }
      aTop.m_aElement.appendChild (aElement);
    }

    /**
     * @param sLocalName
     *        A local name of the XML representation of JSON.
     * @param sText
     *        The content; no text node is created for the empty string.
     * @return A new element in the fn namespace with that text content.
     */
    @NonNull
    private static IXdmNode _textElement (@NonNull final String sLocalName, @NonNull final String sText)
    {
      final IXdmNode ret = _element (sLocalName);
      if (!sText.isEmpty ())
        ret.appendChild (IXdmNode.createText (sText));
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
      final IXdmNode aElement = _textElement ("string", sValue);
      if (bEscaped)
        aElement.addAttribute (IXdmNode.createAttribute (new XdmQName ("escaped"), "true"));
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

  /**
   * Read the options map of <code>fn:parse-json</code>,
   * <code>fn:json-doc</code> or <code>fn:json-to-xml</code> (F&amp;O 3.1
   * sections 17.5.1 and 17.5.3). <code>liberal</code> is type-checked but has
   * no effect (the parser is always strict); <code>validate</code> is only
   * read for json-to-xml. <code>duplicates</code> defaults to
   * <code>use-first</code> for parse-json and to <code>retain</code> (or
   * <code>reject</code> with validation) for json-to-xml. A
   * <code>fallback</code> function is wrapped so that it is called with the
   * escape sequence of an invalid character and its string result is inserted.
   *
   * @param aOptionsArg
   *        The options argument, <code>null</code> or empty for the defaults.
   * @param aCtx
   *        The dynamic context of the call.
   * @param bJsonToXml
   *        <code>true</code> for <code>fn:json-to-xml</code>.
   * @return The resolved options.
   * @throws XPathException
   *         XPTY0004 for an option of the wrong type; FOJS0005 for a
   *         <code>duplicates</code> value the function does not allow,
   *         <code>validate=true</code> with <code>duplicates=retain</code>, or
   *         <code>escape=true</code> together with a fallback.
   */
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
                                                      bJsonToXml ? bValidate ? "reject" : "retain" : "use-first",
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

  /**
   * @param sJson
   *        The JSON text.
   * @param aOptions
   *        The resolved options.
   * @return The XDM value of the text: maps, arrays, xs:string, xs:double,
   *         xs:boolean, and the empty sequence for <code>null</code>.
   * @throws XPathException
   *         FOJS0001 for invalid JSON, FOJS0003 for a rejected duplicate key.
   */
  @NonNull
  private static XdmSequence _parseJson (@NonNull final String sJson, @NonNull final JsonOptions aOptions)
  {
    final XdmHandler aHandler = new XdmHandler (aOptions.duplicates ());
    new JsonParser (sJson, aOptions.escape (), aOptions.fallback ()).parse (aHandler);
    return aHandler.m_aResult;
  }

  /**
   * <code>fn:parse-json($json-text as xs:string?[, $options as map(*)]) as
   * item()?</code> (F&amp;O 3.1 section 17.5.1).
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The parsed value; the empty sequence for an empty argument.
   * @throws XPathException
   *         FOJS0001, FOJS0003 or FOJS0005.
   */
  @NonNull
  private static XdmSequence _fnParseJson (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final JsonOptions aOptions = _readOptions (aArgs.size () > 1 ? aArgs.get (1) : null, aCtx, false);
    return _parseJson (((XdmAtomic) aArgs.get (0).get (0)).getString (), aOptions);
  }

  /**
   * Resolve the <code>$href</code> of <code>fn:json-doc</code> against the
   * static base URI with {@link URI#resolve(URI)}; an absolute URI is returned
   * as it is.
   *
   * @param sHref
   *        The URI as given.
   * @param aCtx
   *        The dynamic context of the call.
   * @return The absolute URI.
   * @throws XPathException
   *         FOUT1170 for an invalid URI or a relative one without static base
   *         URI.
   */
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

  /**
   * <code>fn:json-doc($href as xs:string?[, $options as map(*)]) as
   * item()?</code> (F&amp;O 3.1 section 17.5.2): read the resource as text
   * through the resource resolver (without a requested encoding; the result is
   * not cached) and parse it like <code>fn:parse-json</code>.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The parsed value; the empty sequence for an empty argument.
   * @throws XPathException
   *         FOUT1170 if the URI is invalid or the resource cannot be
   *         retrieved, and the errors of <code>fn:parse-json</code>.
   */
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

  /**
   * <code>fn:json-to-xml($json-text as xs:string?[, $options as map(*)]) as
   * document-node()?</code> (F&amp;O 3.1 section 17.5.3): build the XML
   * representation of JSON (section 17.4.2) in the fn namespace as default
   * namespace, in a new document node with the static base URI. With
   * <code>validate=true</code> the root is validated against the in-scope
   * schema definitions, which must declare it.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The document; the empty sequence for an empty argument.
   * @throws XPathException
   *         FOJS0001, FOJS0003 or FOJS0005 as for <code>fn:parse-json</code>;
   *         FOJS0004 if validation is requested but no schema declares the
   *         root element, or the validation fails.
   */
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
    // validate=true needs the schema for the namespace among the in-scope
    // schema definitions (F&O 3.1 section 17.5.1)
    final IXdmSchemaSet aSchemaSet = aCtx.getStaticContext ().getSchemaSet ();
    final IXdmNode aRoot = aHandler.m_aResult;
    if (aOptions.validate () && (aSchemaSet == null || aSchemaSet.getElementDeclaration (aRoot.getName ()) == null))
      throw XPathException.of ("FOJS0004", "The option validate=true requires a schema-aware processor");
    final IXdmNode aDoc = IXdmNode.createDocument ();
    aRoot.declareNamespace ("", NS_FN);
    aDoc.appendChild (aRoot);
    if (aOptions.validate ())
      try
      {
        aSchemaSet.validateElement (aRoot, null, true);
      }
      catch (final XdmValidationException ex)
      {
        throw XPathException.of ("FOJS0004", "The result could not be validated: " + ex.getMessage ());
      }
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    aDoc.assignDocumentOrder ();
    return XdmSequence.of (aDoc);
  }

  /**
   * @param s
   *        A string.
   * @return <code>true</code> if it consists of XML whitespace (space, tab,
   *         line feed, carriage return) only, also for the empty string.
   */
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

  /**
   * @param sMessage
   *        What is wrong.
   * @return A FOJS0006 error for an invalid XML representation of JSON; the
   *         caller throws it.
   */
  @NonNull
  private static XPathException _invalid (@NonNull final String sMessage)
  {
    return XPathException.of ("FOJS0006", "Invalid XML representation of JSON: " + sMessage);
  }

  /** @return The value of a boolean attribute, <code>false</code> if absent. */
  private static boolean _getBooleanAttr (@NonNull final IXdmNode aElement, @NonNull final String sName)
  {
    final IXdmNode aAttr = aElement.getAttribute ("", sName);
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

  /**
   * Check one element of the input of <code>fn:xml-to-json</code> against the
   * schema of F&amp;O 3.1 appendix C.2, as far as it can be checked locally:
   * it must be in the fn namespace and be one of map, array, string, number,
   * boolean or null; attributes in no namespace are restricted to
   * <code>key</code> and <code>escaped-key</code> (and <code>escaped</code> on
   * string), attributes in the fn namespace are forbidden, others are ignored;
   * a child of a map needs a <code>key</code>; map and array may only contain
   * whitespace text, the simple types no element children.
   *
   * @param aElement
   *        The element.
   * @param bInMap
   *        <code>true</code> if its parent is a map.
   * @throws XPathException
   *         FOJS0006 if a rule is violated.
   */
  private static void _checkElement (@NonNull final IXdmNode aElement, final boolean bInMap)
  {
    final XdmQName aName = aElement.getName ();
    if (!NS_FN.equals (aName.namespaceUri ()))
      throw _invalid ("element " + aName.getEQName () + " is not in the namespace " + NS_FN);
    final String sLocal = aName.localName ();
    for (final IXdmNode aAttr : aElement.getAllAttributes ())
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
                               ("string".equals (sLocal) && "escaped".equals (sAttr));
      if (!bAllowed)
        throw _invalid ("unexpected attribute " + sAttr + " on " + sLocal);
    }
    if (bInMap && aElement.getAttribute ("", "key") == null)
      throw _invalid ("a child of map has no key attribute");
    switch (sLocal)
    {
      case "map":
      case "array":
        for (final IXdmNode aChild : aElement.getAllChildren ())
          if (aChild.getKind () == ENodeKind.TEXT && !_isWhitespace (aChild.getValue ()))
            throw _invalid ("text content in " + sLocal);
        break;
      case "string":
      case "number":
      case "boolean":
      case "null":
        for (final IXdmNode aChild : aElement.getAllChildren ())
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
          if (c < 0x20 || (c >= 0x7F && c <= 0x9F))
            aSB.append (String.format (Locale.ROOT, "\\u%04X", Integer.valueOf (c)));
          else
            aSB.append (c);
      }
      i++;
    }
    return aSB.toString ();
  }

  /**
   * Append a JSON string literal with quotes: an escaped value (attribute
   * <code>escaped</code> or <code>escaped-key</code>) keeps its valid escape
   * sequences, see {@link #_reescape(String)}; any other value is escaped by
   * {@link XdmSerializer#appendJsonString}.
   *
   * @param aSB
   *        The target.
   * @param s
   *        The value.
   * @param bEscaped
   *        <code>true</code> if the value is marked as escaped.
   * @throws XPathException
   *         FOJS0007 for an invalid escape sequence in an escaped value.
   */
  private static void _appendJsonString (@NonNull final StringBuilder aSB,
                                         @NonNull final String s,
                                         final boolean bEscaped)
  {
    if (bEscaped)
      aSB.append ('"').append (_reescape (s)).append ('"');
    else
      XdmSerializer.appendJsonString (aSB, s);
  }

  /**
   * With indentation, append a line feed and two spaces per depth level;
   * otherwise nothing.
   *
   * @param aSB
   *        The target.
   * @param bIndent
   *        <code>true</code> to indent.
   * @param nDepth
   *        The nesting depth.
   */
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
  private record XmlTask (IXdmNode element, boolean inMap, int depth, String literal)
  {}

  /**
   * Serialise the XML representation of JSON as JSON text, iteratively: an
   * explicit stack holds the elements still to write and the literal pieces
   * (separators, keys, closing brackets) between them, so deep nesting cannot
   * overflow the Java stack. Every element is checked with
   * {@link #_checkElement(IXdmNode, boolean)}; map keys must be unique after
   * unescaping; a number must match the JSON-like number pattern (leading
   * <code>+</code> allowed) and be finite, and is written in the canonical
   * xs:double form; a boolean accepts <code>true</code>, <code>false</code>,
   * <code>1</code> and <code>0</code> (trimmed); null must be empty or
   * whitespace. With indentation every member starts on a new line, indented
   * by two spaces per level, and keys are followed by <code>": "</code>.
   *
   * @param aRoot
   *        The root element.
   * @param bIndent
   *        The <code>indent</code> option.
   * @return The JSON text.
   * @throws XPathException
   *         FOJS0006 for an invalid representation, FOJS0007 for an invalid
   *         escape sequence.
   */
  @NonNull
  private static String _xmlToJson (@NonNull final IXdmNode aRoot, final boolean bIndent)
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
      final IXdmNode aElement = aTask.element ();
      _checkElement (aElement, aTask.inMap ());
      final int nDepth = aTask.depth ();
      switch (aElement.getName ().localName ())
      {
        case "map":
        case "array":
        {
          final boolean bMap = "map".equals (aElement.getName ().localName ());
          final ICommonsList <IXdmNode> aChildren = new CommonsArrayList <> ();
          for (final IXdmNode aChild : aElement.getAllChildren ())
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
            final IXdmNode aChild = aChildren.get (i);
            final StringBuilder aPrefix = new StringBuilder ();
            if (i > 0)
              aPrefix.append (',');
            _newline (aPrefix, bIndent, nDepth + 1);
            if (bMap)
            {
              final IXdmNode aKeyAttr = aChild.getAttribute ("", "key");
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

  /**
   * <code>fn:xml-to-json($input as node()?[, $options as map(*)]) as
   * xs:string?</code> (F&amp;O 3.1 section 17.5.4). A document node must have
   * exactly one element child and no text other than whitespace; that element
   * (or the element given) is converted with
   * {@link #_xmlToJson(IXdmNode, boolean)}. Only the <code>indent</code>
   * option is read.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The JSON text; the empty sequence for an empty argument.
   * @throws XPathException
   *         FOJS0006 for any other node kind or an invalid representation,
   *         FOJS0007 for an invalid escape sequence, XPTY0004 if
   *         <code>indent</code> is not a boolean.
   */
  @NonNull
  private static XdmSequence _fnXmlToJson (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final boolean bIndent = aArgs.size () > 1 &&
                            !aArgs.get (1).isEmpty () &&
                            FnOptionMap.getBoolean ((XdmMap) aArgs.get (1).get (0), "indent", false, aCtx);
    IXdmNode aNode = (IXdmNode) aArgs.get (0).get (0);
    if (aNode.getKind () == ENodeKind.DOCUMENT)
    {
      IXdmNode aElement = null;
      for (final IXdmNode aChild : aNode.getAllChildren ())
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

  /**
   * <code>fn:serialize($arg as item()*[, $params as item()?]) as
   * xs:string</code> (F&amp;O 3.1 section 14.7.3). The parameters are a map
   * (read by {@link SerializationParamsReader#fromMap}) or an
   * <code>output:serialization-parameters</code> element; absent or empty
   * means the defaults. The work is done by {@link XdmSerializer#serialize}.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The arguments.
   * @return The serialised string.
   * @throws XPathException
   *         XPTY0004 if the parameters are neither, and the errors of
   *         reading the parameters and of the serialiser.
   */
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
          aParams = SerializationParamsReader.fromElement ((IXdmNode) aParamItem);
        else
          throw XPathException.type ("The serialization parameters must be a map or an output:serialization-parameters element");
    }
    if (aParams == null)
      aParams = new SerializationParams ();
    return XdmSequence.of (XdmAtomic.string (XdmSerializer.serialize (aArgs.get (0), aParams)));
  }

  /**
   * @param sXml
   *        An XML document as a string.
   * @return The document node built by a new {@link XdmParser}.
   * @throws XPathException
   *         FODC0006 if the string is not well-formed, with the parse
   *         exception as cause.
   */
  @NonNull
  private static IXdmNode _parse (@NonNull final String sXml)
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

  /**
   * <code>fn:parse-xml($arg as xs:string?) as
   * document-node(element(*))?</code> (F&amp;O 3.1 section 14.7.1). The
   * document has no document URI and the static base URI as base URI.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The string.
   * @return The document; the empty sequence for an empty argument.
   * @throws XPathException
   *         FODC0006 if the string is not a well-formed document.
   */
  @NonNull
  private static XdmSequence _fnParseXml (@NonNull final DynamicContext aCtx, @NonNull final List <XdmSequence> aArgs)
  {
    if (aArgs.get (0).isEmpty ())
      return XdmSequence.EMPTY;
    final IXdmNode aDoc = _parse (((XdmAtomic) aArgs.get (0).get (0)).getString ());
    aDoc.setDocumentUri (null);
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    return XdmSequence.of (aDoc);
  }

  /** Deep copy of a node into a new parent, iteratively. */
  private static void _copyChildren (@NonNull final IXdmNode aFrom, @NonNull final IXdmNode aTo)
  {
    final ArrayDeque <IXdmNode []> aStack = new ArrayDeque <> ();
    aStack.push (new IXdmNode [] { aFrom, aTo });
    while (!aStack.isEmpty ())
    {
      final IXdmNode [] aPair = aStack.pop ();
      for (final IXdmNode aChild : aPair[0].getAllChildren ())
      {
        final IXdmNode aCopy;
        switch (aChild.getKind ())
        {
          case ELEMENT:
            aCopy = IXdmNode.createElement (aChild.getName ());
            for (final var aEntry : aChild.getDeclaredNamespaces ().entrySet ())
              aCopy.declareNamespace (aEntry.getKey (), aEntry.getValue ());
            for (final IXdmNode aAttr : aChild.getAllAttributes ())
            {
              final IXdmNode aAttrCopy = IXdmNode.createAttribute (aAttr.getName (), aAttr.getValue ());
              aAttrCopy.setIDKind (aAttr.getIDKind ());
              aCopy.addAttribute (aAttrCopy);
            }
            aStack.push (new IXdmNode [] { aChild, aCopy });
            break;
          case TEXT:
            aCopy = IXdmNode.createText (aChild.getValue ());
            break;
          case COMMENT:
            aCopy = IXdmNode.createComment (aChild.getValue ());
            break;
          case PROCESSING_INSTRUCTION:
            aCopy = IXdmNode.createProcessingInstruction (aChild.getName ().localName (), aChild.getValue ());
            break;
          default:
            continue;
        }
        aPair[1].appendChild (aCopy);
      }
    }
  }

  /**
   * <code>fn:parse-xml-fragment($arg as xs:string?) as document-node()?</code>
   * (F&amp;O 3.1 section 14.7.2). A leading byte order mark is dropped and a
   * leading text declaration (<code>&lt;?xml</code> with optional version and
   * required encoding) is checked and removed; the rest is parsed inside a
   * wrapper element and its children are copied into a new document node with
   * the static base URI. A document type declaration cannot occur inside the
   * wrapper element, so the parser rejects it as not well-formed;
   * <code>&lt;!DOCTYPE</code> inside a comment or CDATA section is just
   * content.
   *
   * @param aCtx
   *        The dynamic context of the call.
   * @param aArgs
   *        The string.
   * @return The document; the empty sequence for an empty argument.
   * @throws XPathException
   *         FODC0006 for an invalid text declaration, a DOCTYPE or content
   *         that is not a well-formed external entity.
   */
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
    final IXdmNode aWrapperDoc = _parse ("<fragment-wrapper>" + s + "</fragment-wrapper>");
    final IXdmNode aWrapper = aWrapperDoc.getChildAtIndex (0);
    final IXdmNode aDoc = IXdmNode.createDocument ();
    _copyChildren (aWrapper, aDoc);
    aDoc.setBaseUri (aCtx.getStaticContext ().getBaseUri ());
    aDoc.assignDocumentOrder ();
    return XdmSequence.of (aDoc);
  }

  /**
   * Register <code>fn:parse-json</code>, <code>fn:json-doc</code>,
   * <code>fn:json-to-xml</code> and <code>fn:xml-to-json</code> (each with and
   * without options), <code>fn:serialize</code> (arity 1 and 2),
   * <code>fn:parse-xml</code> and <code>fn:parse-xml-fragment</code>.
   *
   * @param aLib
   *        The library to register into.
   */
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
