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
package com.helger.phoxtrot.xpath.parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.wrapper.Wrapper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XmlNames;
import com.helger.phoxtrot.xdm.schema.EXdmSimpleVariety;
import com.helger.phoxtrot.xdm.schema.IXdmAttributeDeclaration;
import com.helger.phoxtrot.xdm.schema.IXdmElementDeclaration;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xdm.schema.IXdmTypeDefinition;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.ast.EAxis;
import com.helger.phoxtrot.xpath.ast.EBinaryOp;
import com.helger.phoxtrot.xpath.ast.EComparisonOp;
import com.helger.phoxtrot.xpath.ast.ENodeTestKind;
import com.helger.phoxtrot.xpath.ast.EOccurrence;
import com.helger.phoxtrot.xpath.ast.ExprWalker;
import com.helger.phoxtrot.xpath.ast.IExpr;
import com.helger.phoxtrot.xpath.ast.IExpr.StepExpr;
import com.helger.phoxtrot.xpath.ast.ItemType;
import com.helger.phoxtrot.xpath.ast.NodeTest;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.fn.FunctionLibrary;
import com.helger.phoxtrot.xpath.parser.XPathParser.FunctionSignature;
import com.helger.phoxtrot.xpath.value.EAtomicType;
import com.helger.phoxtrot.xpath.value.XdmAtomic;

/**
 * The stateful recursive-descent parser behind {@link XPathParser}. One
 * instance parses one string. The binary operator levels are handled by an
 * operator-precedence loop and the unary/arrow/cast/treat/instance-of levels
 * by one method, so that a nesting level costs only a handful of Java stack
 * frames.
 *
 * @author Philip Helger
 */
final class XPathParserImpl
{
  /**
   * The maximum nesting depth of expressions (nested ExprSingle plus operator
   * chains that build nested AST nodes), as in go-xslt's maxParseDepth.
   */
  static final int MAX_DEPTH = 5000;
  /** The error code of a nesting violation, as go-xslt uses it. */
  static final String ERR_DEPTH = "XPDY0130";

  private static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
  private static final XdmQName FN_CONCAT = new XdmQName (XdmConstants.NS_FN, "concat");

  /** Names that may not be used as unprefixed function names (XPath 3.1 A.3). */
  private static final ICommonsSet <String> RESERVED_FUNCTION_NAMES = new CommonsHashSet <> ("array",
                                                                                               "attribute",
                                                                                               "comment",
                                                                                               "document-node",
                                                                                               "element",
                                                                                               "empty-sequence",
                                                                                               "function",
                                                                                               "if",
                                                                                               "item",
                                                                                               "map",
                                                                                               "namespace-node",
                                                                                               "node",
                                                                                               "processing-instruction",
                                                                                               "schema-attribute",
                                                                                               "schema-element",
                                                                                               "switch",
                                                                                               "text",
                                                                                               "typeswitch");
  private static final ICommonsSet <String> KIND_TEST_NAMES = new CommonsHashSet <> ("node",
                                                                                       "text",
                                                                                       "comment",
                                                                                       "processing-instruction",
                                                                                       "element",
                                                                                       "attribute",
                                                                                       "document-node",
                                                                                       "namespace-node",
                                                                                       "schema-element",
                                                                                       "schema-attribute");
  /** The functions a rooted pattern may start with (XSLT 3.0 OuterFunctionName). */
  private static final ICommonsSet <String> PATTERN_FUNCTION_NAMES = new CommonsHashSet <> ("doc",
                                                                                             "id",
                                                                                             "element-with-id",
                                                                                             "key",
                                                                                             "root");
  /** Built-in schema types in the xs namespace that are not atomic. */
  private static final ICommonsSet <String> NON_ATOMIC_XS_TYPES = new CommonsHashSet <> ("anyType",
                                                                                           "untyped",
                                                                                           "anySimpleType",
                                                                                           "IDREFS",
                                                                                           "NMTOKENS",
                                                                                           "ENTITIES");

  /** Binary operators with their precedence (higher binds tighter). */
  private enum EOp
  {
    OR (1),
    AND (2),
    GENERAL_EQ (3),
    GENERAL_NE (3),
    GENERAL_LT (3),
    GENERAL_LE (3),
    GENERAL_GT (3),
    GENERAL_GE (3),
    VALUE_EQ (3),
    VALUE_NE (3),
    VALUE_LT (3),
    VALUE_LE (3),
    VALUE_GT (3),
    VALUE_GE (3),
    NODE_IS (3),
    NODE_PRECEDES (3),
    NODE_FOLLOWS (3),
    CONCAT (4),
    TO (5),
    ADD (6),
    SUBTRACT (6),
    MULTIPLY (7),
    DIVIDE (7),
    INTEGER_DIVIDE (7),
    MODULO (7),
    UNION (8),
    INTERSECT (9),
    EXCEPT (9);

    private final int m_nPrec;

    EOp (final int nPrec)
    {
      m_nPrec = nPrec;
    }

    /** Comparisons and ranges do not chain. */
    boolean isNonAssociative ()
    {
      return m_nPrec == 3 || m_nPrec == 5;
    }
  }

  /**
   * Stack size of the helper thread that re-runs a parse that overflowed the
   * caller's stack. Big enough for {@link #MAX_DEPTH} levels even in
   * interpreted mode; only address space is reserved up front.
   */
  private static final long LARGE_STACK_SIZE = 256L * 1024 * 1024;

  static
  {
    // Initialise every class with a static initialiser the parser touches
    // here, at the top of the stack: a StackOverflowError inside a static
    // initialiser deep in a recursion would poison the class for good.
    EAtomicType.values ();
    EAxis.values ();
    EOccurrence.values ();
    ENodeTestKind.values ();
    EBinaryOp.values ();
    EComparisonOp.values ();
    IExpr.SetExpr.ESetOp.values ();
    EOp.values ();
    ETokenKind.values ();
    ItemType.Kind.values ();
    if (XdmAtomic.TRUE == null || NodeTest.ANY_NODE == null || SeqType.ANY == null || ItemType.ITEM == null)
      throw new IllegalStateException ("Class initialisation failed");
    XPathException.syntax ("init");
    new CommonsArrayList <> ().add (BigInteger.ONE);
    new CommonsHashSet <> ().add (BigDecimal.ONE);
  }

  private final String m_sSrc;
  private final ICommonsList <XPathToken> m_aTokens;
  private final StaticContext m_aCtx;
  /** Variables bound inside the expression, innermost last. */
  private final ICommonsList <XdmQName> m_aScope = new CommonsArrayList <> ();
  private int m_nPos;
  private int m_nDepth;
  /** Source text of the union operands of a pattern; only set while parsing a pattern. */
  private Map <IExpr, String> m_aPatternSources;

  /**
   * Create a parser for one source text. The whole text is tokenised here, so
   * lexical errors are raised by the constructor.
   *
   * @param sSrc
   *        The text to parse.
   * @param aCtx
   *        The static context for name resolution and the static checks.
   * @throws XPathException
   *         XPST0003 for lexical errors.
   */
  XPathParserImpl (@NonNull final String sSrc, @NonNull final StaticContext aCtx)
  {
    m_sSrc = sSrc;
    m_aTokens = XPathLexer.tokenize (sSrc);
    m_aCtx = aCtx;
  }

  @NonNull
  private XPathToken _cur ()
  {
    return m_aTokens.get (m_nPos);
  }

  /**
   * @return The token at the given offset from the current one; beyond the
   *         end the final {@link ETokenKind#EOF} token.
   */
  @NonNull
  private XPathToken _peek (final int nOffset)
  {
    final int nIdx = m_nPos + nOffset;
    return nIdx < m_aTokens.size () ? m_aTokens.get (nIdx) : m_aTokens.getLastOrNull ();
  }

  @NonNull
  private ETokenKind _peekKind (final int nOffset)
  {
    return _peek (nOffset).kind ();
  }

  /**
   * Consume the current token. At the {@link ETokenKind#EOF} token the
   * position stays where it is, so the parser can never run past the end.
   *
   * @return The token that was current before the call.
   */
  @NonNull
  private XPathToken _next ()
  {
    final XPathToken ret = m_aTokens.get (m_nPos);
    if (ret.kind () != ETokenKind.EOF)
      m_nPos++;
    return ret;
  }

  private boolean _is (@NonNull final ETokenKind eKind)
  {
    return _cur ().kind () == eKind;
  }

  /**
   * @return The source text for an error message: unchanged up to 200
   *         characters, else the first 200 characters followed by
   *         <code>...</code>.
   */
  @NonNull
  static String abbreviate (@NonNull final String sSrc)
  {
    return sSrc.length () > 200 ? sSrc.substring (0, 200) + "..." : sSrc;
  }

  @NonNull
  private String _where (@NonNull final XPathToken aTok)
  {
    return " at offset " + aTok.pos () + " in '" + abbreviate (m_sSrc) + "'";
  }

  /**
   * @return A new exception with the given error code whose message is
   *         extended by the token offset and the abbreviated source. It is
   *         returned, not thrown.
   */
  @NonNull
  private XPathException _error (@NonNull final String sCode, @NonNull final String sMsg, @NonNull final XPathToken aTok)
  {
    return XPathException.of (sCode, sMsg + _where (aTok));
  }

  /**
   * @return A new XPST0003 exception like {@link #_error(String, String,
   *         XPathToken)}.
   */
  @NonNull
  private XPathException _syntax (@NonNull final String sMsg, @NonNull final XPathToken aTok)
  {
    return XPathException.syntax (sMsg + _where (aTok));
  }

  /**
   * @param sExpected
   *        What the grammar allows at this point, for the message.
   * @return A new XPST0003 exception for the current token, quoting its
   *         display text (not quoted for the end of the expression).
   */
  @NonNull
  private XPathException _unexpected (@NonNull final String sExpected)
  {
    final XPathToken aTok = _cur ();
    return _syntax ("Unexpected " + (aTok.kind () == ETokenKind.EOF ? "" : "'") +
                    aTok.getDisplayText () +
                    (aTok.kind () == ETokenKind.EOF ? "" : "'") +
                    ", expected " +
                    sExpected,
                    aTok);
  }

  /**
   * Consume the current token if it has the given kind.
   *
   * @param sWhat
   *        The expected token for the error message.
   * @return The consumed token.
   * @throws XPathException
   *         XPST0003 if the current token is of another kind.
   */
  @NonNull
  private XPathToken _expect (@NonNull final ETokenKind eKind, @NonNull final String sWhat)
  {
    if (_cur ().kind () != eKind)
      throw _unexpected (sWhat);
    return _next ();
  }

  /**
   * Consume the current token if it is the given unprefixed name. Keywords
   * are not reserved in XPath, so a keyword is an NCName in the right place.
   *
   * @throws XPathException
   *         XPST0003 otherwise.
   */
  private void _expectKeyword (@NonNull final String sKeyword)
  {
    if (!_cur ().isNCName (sKeyword))
      throw _unexpected ("'" + sKeyword + "'");
    _next ();
  }

  /**
   * Count one more nesting level. The caller restores the depth when the
   * level is left (in a <code>finally</code> block).
   *
   * @throws XPathException
   *         {@link #ERR_DEPTH} once more than {@link #MAX_DEPTH} levels are
   *         open.
   */
  private void _enterDepth ()
  {
    m_nDepth++;
    if (m_nDepth > MAX_DEPTH)
      throw _error (ERR_DEPTH, "Expression nested more than " + MAX_DEPTH + " levels deep", _cur ());
  }

  /**
   * Resolve a NAME or BRACED_NAME token.
   *
   * @param aTok
   *        The name token.
   * @param sDefaultNs
   *        The namespace for an unprefixed name.
   */
  @NonNull
  private XdmQName _resolve (@NonNull final XPathToken aTok, @NonNull final String sDefaultNs)
  {
    if (aTok.kind () == ETokenKind.BRACED_NAME)
      return new XdmQName (aTok.uri (), aTok.text ());
    final String sText = aTok.text ();
    final int nColon = sText.indexOf (':');
    if (nColon < 0)
      return new XdmQName (sDefaultNs, sText);
    final String sPrefix = sText.substring (0, nColon);
    final String sNs = m_aCtx.getNamespaceUri (sPrefix);
    if (sNs == null || sNs.isEmpty ())
      throw _error ("XPST0081", "The prefix '" + sPrefix + "' is not bound to a namespace", aTok);
    return new XdmQName (sNs, sText.substring (nColon + 1), sPrefix);
  }

  /**
   * @param aTok
   *        A {@link ETokenKind#PREFIX_WILDCARD} token, whose text is the
   *        prefix.
   * @return The namespace URI the prefix is bound to in the static context.
   * @throws XPathException
   *         XPST0081 if the prefix is unbound or bound to the empty string.
   */
  @NonNull
  private String _resolvePrefix (@NonNull final XPathToken aTok)
  {
    final String sNs = m_aCtx.getNamespaceUri (aTok.text ());
    if (sNs == null || sNs.isEmpty ())
      throw _error ("XPST0081", "The prefix '" + aTok.text () + "' is not bound to a namespace", aTok);
    return sNs;
  }

  /**
   * Resolve the name of a function call, named function reference or arrow
   * target; an unprefixed name is in the default function namespace.
   *
   * @throws XPathException
   *         XPST0003 for an unprefixed reserved function name (XPath 3.1
   *         A.3, e.g. <code>if</code> or <code>node</code>), XPST0081 for an
   *         unbound prefix.
   */
  @NonNull
  private XdmQName _resolveFunctionName (@NonNull final XPathToken aTok)
  {
    if (aTok.isUnprefixedName () && RESERVED_FUNCTION_NAMES.contains (aTok.text ()))
      throw _syntax ("'" + aTok.text () + "' is a reserved function name and must be prefixed", aTok);
    return _resolve (aTok, m_aCtx.getDefaultFunctionNamespace ());
  }

  /**
   * Check that the function library of the static context has a function
   * with this name and arity. <code>fn:concat</code> is variadic and accepted
   * with any arity of at least 2 when the library knows the name.
   *
   * @throws XPathException
   *         XPST0017 for an unknown name/arity combination.
   */
  private void _checkFunction (@NonNull final XdmQName aName, final int nArity, @NonNull final XPathToken aTok)
  {
    final FunctionLibrary aLib = m_aCtx.getFunctionLibrary ();
    if (aLib.getFunction (aName, nArity) != null)
      return;
    // fn:concat is variadic
    if (aName.equals (FN_CONCAT) && nArity >= 2 && aLib.containsName (aName))
      return;
    throw _error ("XPST0017",
                  "Unknown function " + aName.getEQName () + "#" + nArity,
                  aTok);
  }

  /**
   * Consume a variable name (the token after <code>$</code>). An unprefixed
   * name is in no namespace.
   *
   * @throws XPathException
   *         XPST0003 if the current token is no EQName, XPST0081 for an
   *         unbound prefix.
   */
  @NonNull
  private XdmQName _parseVarName ()
  {
    final XPathToken aTok = _cur ();
    if (!aTok.isEQName ())
      throw _unexpected ("a variable name");
    _next ();
    return _resolve (aTok, "");
  }

  /**
   * @return <code>true</code> if the variable is bound by an enclosing range
   *         variable or inline function parameter of the expression, or is
   *         declared in the static context.
   */
  private boolean _isVariableInScope (@NonNull final XdmQName aName)
  {
    for (int i = m_aScope.size () - 1; i >= 0; --i)
      if (m_aScope.get (i).equals (aName))
        return true;
    return m_aCtx.isVariableDeclared (aName);
  }

  /** Remove the given number of innermost variables from the scope. */
  private void _popScope (final int nCount)
  {
    for (int i = 0; i < nCount; ++i)
      m_aScope.removeLast ();
  }

  /**
   * @return The built-in atomic type of this name, or <code>null</code> if
   *         the name is not in the xs namespace or names no atomic type.
   */
  @Nullable
  private static EAtomicType _atomicTypeOf (@NonNull final XdmQName aName)
  {
    if (!XdmConstants.NS_XS.equals (aName.namespaceUri ()))
      return null;
    return EAtomicType.getFromLocalName (aName.localName ());
  }

  /**
   * @return <code>true</code> if the name is an in-scope schema type: a
   *         built-in atomic type, one of the non-atomic built-ins
   *         <code>xs:anyType</code>, <code>xs:untyped</code>,
   *         <code>xs:anySimpleType</code> and the three list types, or a type
   *         of the schema set of the static context.
   */
  private boolean _isKnownSchemaType (@NonNull final XdmQName aName)
  {
    if (_atomicTypeOf (aName) != null ||
        (XdmConstants.NS_XS.equals (aName.namespaceUri ()) && NON_ATOMIC_XS_TYPES.contains (aName.localName ())))
      return true;
    final IXdmSchemaSet aSchemaSet = m_aCtx.getSchemaSet ();
    return aSchemaSet != null && aSchemaSet.getTypeDefinition (aName) != null;
  }

  /**
   * @return A user-defined simple type of the in-scope schema definitions, or
   *         <code>null</code>. Built-in types (in the xs namespace) are never
   *         returned.
   */
  @Nullable
  private IXdmTypeDefinition _getUserSimpleType (@NonNull final XdmQName aName)
  {
    final IXdmSchemaSet aSchemaSet = m_aCtx.getSchemaSet ();
    if (aSchemaSet == null || XdmConstants.NS_XS.equals (aName.namespaceUri ()))
      return null;
    final IXdmTypeDefinition ret = aSchemaSet.getTypeDefinition (aName);
    return ret == null || ret.isComplex () ? null : ret;
  }

  /**
   * @return The item type of a user-defined simple type: its built-in atomic
   *         base type for an atomic type, xs:anyAtomicType otherwise.
   */
  @NonNull
  private static ItemType _schemaAtomicItemType (@NonNull final IXdmTypeDefinition aType)
  {
    final XdmQName aBuiltin = aType.getBuiltinAtomicType ();
    EAtomicType eBuiltin = aBuiltin == null ? null : EAtomicType.getFromLocalName (aBuiltin.localName ());
    if (eBuiltin == null)
      eBuiltin = EAtomicType.ANY_ATOMIC_TYPE;
    return ItemType.schemaAtomic (eBuiltin, aType);
  }

  /**
   * The constructor function of a user-defined simple type as a function item
   * (XPath 3.1 section 3.14.4): an inline function with one
   * <code>xs:anyAtomicType?</code> parameter that casts it to the type. Its
   * declared return type is that cast target, except for a list type, which
   * gets none.
   *
   * @param aUserType
   *        The user-defined simple type.
   * @return The inline function expression.
   */
  @NonNull
  private static IExpr _getUserTypeConstructorFunction (@NonNull final IXdmTypeDefinition aUserType)
  {
    final XdmQName aParam = new XdmQName ("http://www.w3.org/2005/xqt-errors/ph-xslt/constructor", "arg");
    final SeqType aResult = new SeqType (false, _schemaAtomicItemType (aUserType), EOccurrence.ZERO_OR_ONE, false);
    return new IExpr.InlineFunctionExpr (new CommonsArrayList <> (new IExpr.Param (aParam,
                                                                                   new SeqType (false,
                                                                                                ItemType.atomic (EAtomicType.ANY_ATOMIC_TYPE),
                                                                                                EOccurrence.ZERO_OR_ONE,
                                                                                                false))),
                                         aUserType.getVariety () == EXdmSimpleVariety.LIST ? null : aResult,
                                         new IExpr.CastExpr (new IExpr.VarRefExpr (aParam), aResult, false));
  }

  /**
   * The constructor function of a user-defined simple type of the in-scope
   * schema definitions (XPath 3.1 section 3.14.4): a call with one argument is
   * a cast to the type; a call whose argument is the placeholder
   * <code>?</code> is a partial function application (section 3.1.5.1) and
   * yields the constructor function itself, as
   * {@link #_getUserTypeConstructorFunction(IXdmTypeDefinition)} builds it.
   *
   * @return The cast or the function, or <code>null</code> if the name is not
   *         such a type or a function of that name and arity exists.
   */
  @Nullable
  private IExpr _getSchemaConstructor (@NonNull final XdmQName aName, @NonNull final List <IExpr> aArgs)
  {
    if (aArgs.size () != 1 || m_aCtx.getSchemaSet () == null)
      return null;
    if (m_aCtx.getFunctionLibrary ().getFunction (aName, 1) != null)
      return null;
    final IXdmTypeDefinition aUserType = _getUserSimpleType (aName);
    if (aUserType == null)
      return null;
    if (aArgs.get (0) instanceof IExpr.PlaceholderExpr)
      return _getUserTypeConstructorFunction (aUserType);
    return new IExpr.CastExpr (aArgs.get (0),
                               new SeqType (false, _schemaAtomicItemType (aUserType), EOccurrence.ZERO_OR_ONE, false),
                               false);
  }

  /**
   * @throws XPathException
   *         XQST0070 if a name test uses the namespace URI reserved for
   *         <code>xmlns</code>.
   */
  private void _checkNotXmlnsNamespace (@NonNull final String sNs, @NonNull final XPathToken aTok)
  {
    if (NS_XMLNS.equals (sNs))
      throw _error ("XQST0070", "The xmlns namespace URI cannot be used in a name test", aTok);
  }

  /**
   * Parse the SingleType of a cast or castable expression: a type name and an
   * optional <code>?</code>. An unprefixed name is resolved against the
   * default element namespace. <code>xs:IDREFS</code>,
   * <code>xs:NMTOKENS</code> and <code>xs:ENTITIES</code> become their item
   * type with the list flag set; a name outside the built-in atomic types must
   * be a user-defined simple type of the in-scope schema.
   *
   * @return The target type, with occurrence one or zero-or-one.
   * @throws XPathException
   *         XPST0003 for a missing name or a name followed by
   *         <code>(</code>, XPST0080 for <code>xs:NOTATION</code>,
   *         <code>xs:anyAtomicType</code> and <code>xs:anySimpleType</code>,
   *         XQST0052 for a name that is no known simple type.
   */
  @NonNull
  private SeqType _parseSingleType ()
  {
    final XPathToken aTok = _cur ();
    if (!aTok.isEQName ())
      throw _unexpected ("an atomic type name");
    if (_peekKind (1) == ETokenKind.LPAREN)
      throw _syntax ("A cast target must be an atomic type name, not '" + aTok.getDisplayText () + "()'", aTok);
    _next ();
    final XdmQName aName = _resolve (aTok, m_aCtx.getDefaultElementNamespace ());
    EAtomicType eType = null;
    boolean bList = false;
    if (XdmConstants.NS_XS.equals (aName.namespaceUri ()))
    {
      switch (aName.localName ())
      {
        case "NOTATION":
        case "anyAtomicType":
        case "anySimpleType":
          throw _error ("XPST0080", "xs:" + aName.localName () + " is not a valid cast target", aTok);
        case "IDREFS":
          eType = EAtomicType.IDREF;
          bList = true;
          break;
        case "NMTOKENS":
          eType = EAtomicType.NMTOKEN;
          bList = true;
          break;
        case "ENTITIES":
          eType = EAtomicType.ENTITY;
          bList = true;
          break;
        default:
          eType = EAtomicType.getFromLocalName (aName.localName ());
          break;
      }
    }
    ItemType aItemType = null;
    if (eType == null)
    {
      final IXdmTypeDefinition aUserType = _getUserSimpleType (aName);
      if (aUserType == null)
        throw _error ("XQST0052", "The cast target " + aName.getEQName () + " is not a known simple type", aTok);
      aItemType = _schemaAtomicItemType (aUserType);
    }
    else
      aItemType = ItemType.atomic (eType);
    EOccurrence eOcc = EOccurrence.ONE;
    if (_is (ETokenKind.QUESTION))
    {
      _next ();
      eOcc = EOccurrence.ZERO_OR_ONE;
    }
    return new SeqType (false, aItemType, eOcc, bList);
  }

  /** element(...) / attribute(...) arguments, current token after '('. */
  @NonNull
  private NodeTest _parseElementOrAttributeTestBody (final boolean bElement)
  {
    boolean bAnyName = true;
    XdmQName aName = null;
    XdmQName aTypeName = null;
    boolean bNillable = false;
    if (!_is (ETokenKind.RPAREN))
    {
      final XPathToken aTok = _cur ();
      if (aTok.kind () == ETokenKind.STAR)
        _next ();
      else
        if (aTok.isEQName ())
        {
          _next ();
          aName = _resolve (aTok, bElement ? m_aCtx.getDefaultElementNamespace () : "");
          bAnyName = false;
        }
        else
          throw _unexpected ("a name or '*'");
      if (_is (ETokenKind.COMMA))
      {
        _next ();
        final XPathToken aTypeTok = _cur ();
        if (!aTypeTok.isEQName ())
          throw _unexpected ("a type name");
        _next ();
        aTypeName = _resolve (aTypeTok, m_aCtx.getDefaultElementNamespace ());
        if (!_isKnownSchemaType (aTypeName))
          throw _error ("XPST0008", "The type " + aTypeName.getEQName () + " is not in the in-scope schema types", aTypeTok);
        if (bElement && _is (ETokenKind.QUESTION))
        {
          _next ();
          bNillable = true;
        }
      }
    }
    final IXdmSchemaSet aSchemaSet = m_aCtx.getSchemaSet ();
    final IXdmTypeDefinition aTypeDef = aTypeName != null &&
                                        aSchemaSet != null &&
                                        !XdmConstants.NS_XS.equals (aTypeName.namespaceUri ()) ? aSchemaSet.getTypeDefinition (aTypeName)
                                                                                               : null;
    return new NodeTest (bElement ? ENodeTestKind.ELEMENT : ENodeTestKind.ATTRIBUTE,
                         bAnyName,
                         false,
                         false,
                         aName,
                         aTypeName,
                         bNillable,
                         null,
                         null,
                         null,
                         aTypeDef);
  }

  /** Parse a KindTest; the current token is the kind keyword, the next one '('. */
  @NonNull
  private NodeTest _parseKindTest ()
  {
    final XPathToken aKindTok = _next ();
    _expect (ETokenKind.LPAREN, "'('");
    final NodeTest ret;
    switch (aKindTok.text ())
    {
      case "node":
        ret = NodeTest.ANY_NODE;
        break;
      case "text":
        ret = NodeTest.ofKind (ENodeTestKind.TEXT);
        break;
      case "comment":
        ret = NodeTest.ofKind (ENodeTestKind.COMMENT);
        break;
      case "namespace-node":
        ret = NodeTest.ofKind (ENodeTestKind.NAMESPACE_NODE);
        break;
      case "processing-instruction":
      {
        final XPathToken aTok = _cur ();
        XdmQName aTarget = null;
        if (aTok.kind () == ETokenKind.NAME)
        {
          if (aTok.text ().indexOf (':') >= 0)
            throw _syntax ("'" + aTok.text () + "' is not a valid processing-instruction target", aTok);
          _next ();
          aTarget = new XdmQName (aTok.text ());
        }
        else
          if (aTok.kind () == ETokenKind.STRING)
          {
            _next ();
            final String sTarget = aTok.text ().trim ().replaceAll ("[ \\t\\r\\n]+", " ");
            if (!XmlNames.isNCName (sTarget))
              throw _error ("XPTY0004", "'" + sTarget + "' is not a valid processing-instruction target", aTok);
            aTarget = new XdmQName (sTarget);
          }
          else
            if (aTok.kind () != ETokenKind.RPAREN)
              throw _unexpected ("a processing-instruction target");
        ret = new NodeTest (ENodeTestKind.PROCESSING_INSTRUCTION, aTarget == null, false, false, aTarget, null, false, null);
        break;
      }
      case "element":
        ret = _parseElementOrAttributeTestBody (true);
        break;
      case "attribute":
        ret = _parseElementOrAttributeTestBody (false);
        break;
      case "schema-element":
      case "schema-attribute":
      {
        final boolean bElement = aKindTok.text ().equals ("schema-element");
        final XPathToken aTok = _cur ();
        if (!aTok.isEQName ())
          throw _unexpected ("a declaration name");
        _next ();
        final XdmQName aName = _resolve (aTok, bElement ? m_aCtx.getDefaultElementNamespace () : "");
        final IXdmSchemaSet aSchemaSet = m_aCtx.getSchemaSet ();
        final IXdmElementDeclaration aElementDecl = aSchemaSet != null && bElement ? aSchemaSet.getElementDeclaration (aName)
                                                                                   : null;
        final IXdmAttributeDeclaration aAttrDecl = aSchemaSet != null && !bElement ? aSchemaSet.getAttributeDeclaration (aName)
                                                                                   : null;
        if (aElementDecl == null && aAttrDecl == null)
          throw _error ("XPST0008",
                        aKindTok.text () + "(" + aName.getEQName () + ") names no declaration in the in-scope schema",
                        aTok);
        ret = new NodeTest (bElement ? ENodeTestKind.SCHEMA_ELEMENT : ENodeTestKind.SCHEMA_ATTRIBUTE,
                            false,
                            false,
                            false,
                            aName,
                            null,
                            false,
                            null,
                            aElementDecl,
                            aAttrDecl,
                            null);
        break;
      }
      case "document-node":
      {
        NodeTest aInner = null;
        if (!_is (ETokenKind.RPAREN))
        {
          final XPathToken aTok = _cur ();
          if (!(aTok.isNCName ("element") || aTok.isNCName ("schema-element")) || _peekKind (1) != ETokenKind.LPAREN)
            throw _unexpected ("element() or schema-element()");
          aInner = _parseKindTest ();
        }
        ret = new NodeTest (ENodeTestKind.DOCUMENT, false, false, false, null, null, false, aInner);
        break;
      }
      default:
        throw _syntax ("Unknown kind test '" + aKindTok.text () + "'", aKindTok);
    }
    _expect (ETokenKind.RPAREN, "')'");
    return ret;
  }

  /**
   * Parse a FunctionTest; the current token is <code>function</code>, the
   * next one <code>(</code>. <code>function(*)</code> is the any-function
   * test, otherwise the parameter types, <code>as</code> and the return type
   * follow (XPath 3.1 section 2.5.5.7).
   */
  @NonNull
  private ItemType _parseFunctionTest ()
  {
    _next ();
    _expect (ETokenKind.LPAREN, "'('");
    if (_is (ETokenKind.STAR))
    {
      _next ();
      _expect (ETokenKind.RPAREN, "')'");
      return new ItemType (ItemType.Kind.FUNCTION, null, null, true, null, null, null, null);
    }
    final ICommonsList <SeqType> aParams = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RPAREN))
      while (true)
      {
        aParams.add (_parseSequenceType ());
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RPAREN, "')' or ','");
    _expectKeyword ("as");
    final SeqType aReturn = _parseSequenceType ();
    return new ItemType (ItemType.Kind.FUNCTION, null, null, false, aParams, aReturn, null, null);
  }

  /**
   * Consume the name of a built-in atomic type; an unprefixed name is
   * resolved against the default element namespace.
   *
   * @throws XPathException
   *         XPST0051 if the name is no built-in atomic type.
   */
  @NonNull
  private EAtomicType _parseAtomicTypeName ()
  {
    final XPathToken aTok = _cur ();
    if (!aTok.isEQName ())
      throw _unexpected ("an atomic type name");
    _next ();
    final XdmQName aName = _resolve (aTok, m_aCtx.getDefaultElementNamespace ());
    final EAtomicType eType = _atomicTypeOf (aName);
    if (eType == null)
      throw _error ("XPST0051", aName.getEQName () + " is not a known atomic type", aTok);
    return eType;
  }

  /**
   * Parse a MapTest; the current token is <code>map</code>, the next one
   * <code>(</code>: <code>map(*)</code> or <code>map(K, V)</code> with a
   * built-in atomic key type K and a sequence type V.
   */
  @NonNull
  private ItemType _parseMapTest ()
  {
    _next ();
    _expect (ETokenKind.LPAREN, "'('");
    if (_is (ETokenKind.STAR))
    {
      _next ();
      _expect (ETokenKind.RPAREN, "')'");
      return new ItemType (ItemType.Kind.MAP, null, null, true, null, null, null, null);
    }
    final EAtomicType eKey = _parseAtomicTypeName ();
    _expect (ETokenKind.COMMA, "','");
    final SeqType aValue = _parseSequenceType ();
    _expect (ETokenKind.RPAREN, "')'");
    return new ItemType (ItemType.Kind.MAP, null, null, false, null, null, eKey, aValue);
  }

  /**
   * Parse an ArrayTest; the current token is <code>array</code>, the next one
   * <code>(</code>: <code>array(*)</code> or <code>array(T)</code> with a
   * member sequence type T.
   */
  @NonNull
  private ItemType _parseArrayTest ()
  {
    _next ();
    _expect (ETokenKind.LPAREN, "'('");
    if (_is (ETokenKind.STAR))
    {
      _next ();
      _expect (ETokenKind.RPAREN, "')'");
      return new ItemType (ItemType.Kind.ARRAY, null, null, true, null, null, null, null);
    }
    final SeqType aMember = _parseSequenceType ();
    _expect (ETokenKind.RPAREN, "')'");
    return new ItemType (ItemType.Kind.ARRAY, null, null, false, null, null, null, aMember);
  }

  /**
   * Parse an ItemType (XPath 3.1 section 2.5.4): a parenthesised item type,
   * <code>item()</code>, a kind test, a function, map or array test, or an
   * atomic or union type name. A type name is first looked up as a
   * user-defined non-list simple type of the in-scope schema (only if there
   * is a schema set and the name is no built-in atomic type), else it must be
   * a built-in atomic type.
   *
   * @throws XPathException
   *         XPST0003 for any other name followed by <code>(</code> and for a
   *         token that cannot start an item type, XPST0051 for an unknown
   *         type name.
   */
  @NonNull
  private ItemType _parseItemTypeInner ()
  {
    final XPathToken aTok = _cur ();
    if (aTok.kind () == ETokenKind.LPAREN)
    {
      _next ();
      final ItemType ret = _parseItemType ();
      _expect (ETokenKind.RPAREN, "')'");
      return ret;
    }
    if (aTok.isUnprefixedName () && _peekKind (1) == ETokenKind.LPAREN)
    {
      final String sName = aTok.text ();
      if (sName.equals ("item"))
      {
        _next ();
        _next ();
        _expect (ETokenKind.RPAREN, "')'");
        return ItemType.ITEM;
      }
      if (KIND_TEST_NAMES.contains (sName))
        return ItemType.node (_parseKindTest ());
      if (sName.equals ("function"))
        return _parseFunctionTest ();
      if (sName.equals ("map"))
        return _parseMapTest ();
      if (sName.equals ("array"))
        return _parseArrayTest ();
    }
    if (aTok.isEQName () && _peekKind (1) == ETokenKind.LPAREN)
      throw _syntax ("'" + aTok.getDisplayText () + "()' is not an item type", aTok);
    if (aTok.isEQName ())
    {
      if (m_aCtx.getSchemaSet () != null)
      {
        final XdmQName aName = _resolve (aTok, m_aCtx.getDefaultElementNamespace ());
        if (_atomicTypeOf (aName) == null)
        {
          final IXdmTypeDefinition aUserType = _getUserSimpleType (aName);
          if (aUserType != null && aUserType.getVariety () != EXdmSimpleVariety.LIST)
          {
            _next ();
            return _schemaAtomicItemType (aUserType);
          }
        }
      }
      return ItemType.atomic (_parseAtomicTypeName ());
    }
    throw _unexpected ("an item type");
  }

  /**
   * {@link #_parseItemTypeInner()} as one nesting level, so that deeply nested
   * types (<code>array(array(...))</code>) hit the depth limit.
   */
  @NonNull
  private ItemType _parseItemType ()
  {
    _enterDepth ();
    try
    {
      return _parseItemTypeInner ();
    }
    finally
    {
      m_nDepth--;
    }
  }

  /**
   * Parse a SequenceType: <code>empty-sequence()</code>, or an item type with
   * an optional occurrence indicator <code>?</code>, <code>*</code> or
   * <code>+</code>. The indicator is always taken when present (XPath 3.1
   * A.1.2 constraint occurrence-indicators).
   */
  @NonNull
  private SeqType _parseSequenceType ()
  {
    if (_cur ().isNCName ("empty-sequence") && _peekKind (1) == ETokenKind.LPAREN)
    {
      _next ();
      _next ();
      _expect (ETokenKind.RPAREN, "')'");
      return SeqType.EMPTY_SEQUENCE;
    }
    final ItemType aItemType = _parseItemType ();
    EOccurrence eOcc = EOccurrence.ONE;
    switch (_cur ().kind ())
    {
      case QUESTION:
        _next ();
        eOcc = EOccurrence.ZERO_OR_ONE;
        break;
      case STAR:
        _next ();
        eOcc = EOccurrence.ZERO_OR_MORE;
        break;
      case PLUS:
        _next ();
        eOcc = EOccurrence.ONE_OR_MORE;
        break;
      default:
        break;
    }
    return SeqType.of (aItemType, eOcc);
  }

  /**
   * Parse the NodeTest of an axis step: a wildcard (<code>*</code>,
   * <code>*:local</code>, <code>prefix:*</code>, <code>Q{uri}*</code>), a kind
   * test, or a name test. An unprefixed name is in the default element
   * namespace, except on the attribute and namespace axes where it is in no
   * namespace.
   *
   * @param eAxis
   *        The axis of the step, for the namespace of unprefixed names.
   * @throws XPathException
   *         XPST0081 for an unbound prefix, XQST0070 for the xmlns namespace,
   *         XPST0003 if no node test starts here.
   */
  @NonNull
  private NodeTest _parseNodeTest (@NonNull final EAxis eAxis)
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case STAR:
        _next ();
        return new NodeTest (ENodeTestKind.NAME, true, false, false, null, null, false, null);
      case LOCAL_WILDCARD:
        _next ();
        return new NodeTest (ENodeTestKind.NAME, false, true, false, new XdmQName (aTok.text ()), null, false, null);
      case PREFIX_WILDCARD:
      {
        _next ();
        final String sNs = _resolvePrefix (aTok);
        _checkNotXmlnsNamespace (sNs, aTok);
        return new NodeTest (ENodeTestKind.NAME,
                             false,
                             false,
                             true,
                             new XdmQName (sNs, "*", aTok.text ()),
                             null,
                             false,
                             null);
      }
      case BRACED_WILDCARD:
        _next ();
        _checkNotXmlnsNamespace (aTok.uri (), aTok);
        return new NodeTest (ENodeTestKind.NAME, false, false, true, new XdmQName (aTok.uri (), "*"), null, false, null);
      case NAME:
        if (aTok.isUnprefixedName () && KIND_TEST_NAMES.contains (aTok.text ()) && _peekKind (1) == ETokenKind.LPAREN)
          return _parseKindTest ();
        //$FALL-THROUGH$
      case BRACED_NAME:
      {
        _next ();
        final String sDefaultNs = eAxis == EAxis.ATTRIBUTE || eAxis == EAxis.NAMESPACE ? ""
                                                                                       : m_aCtx.getDefaultElementNamespace ();
        final XdmQName aName = _resolve (aTok, sDefaultNs);
        _checkNotXmlnsNamespace (aName.namespaceUri (), aTok);
        return new NodeTest (ENodeTestKind.NAME, false, false, false, aName, null, false, null);
      }
      default:
        throw _unexpected ("an expression");
    }
  }

  /**
   * @return The predicates <code>[expr]</code> that follow the current
   *         position, possibly none.
   */
  @NonNull
  private ICommonsList <IExpr> _parsePredicates ()
  {
    final ICommonsList <IExpr> ret = new CommonsArrayList <> ();
    while (_is (ETokenKind.LBRACKET))
    {
      _next ();
      ret.add (_parseExpr ());
      _expect (ETokenKind.RBRACKET, "']'");
    }
    return ret;
  }

  /** Arguments in parentheses, with placeholders. */
  @NonNull
  private ICommonsList <IExpr> _parseArgList ()
  {
    _expect (ETokenKind.LPAREN, "'('");
    final ICommonsList <IExpr> ret = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RPAREN))
      while (true)
      {
        if (_is (ETokenKind.QUESTION) &&
            (_peekKind (1) == ETokenKind.COMMA || _peekKind (1) == ETokenKind.RPAREN))
        {
          _next ();
          ret.add (new IExpr.PlaceholderExpr ());
        }
        else
          ret.add (_parseExprSingle ());
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RPAREN, "')' or ','");
    return ret;
  }

  /** The key specifier after '?'; <code>null</code> for the wildcard. */
  @Nullable
  private IExpr _parseLookupKey ()
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case NAME:
        if (aTok.text ().indexOf (':') >= 0)
          throw _syntax ("A lookup key must be an NCName, got '" + aTok.text () + "'", aTok);
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.string (aTok.text ()));
      case INTEGER:
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.integer (new BigInteger (aTok.text ())));
      case LPAREN:
        _next ();
        if (_is (ETokenKind.RPAREN))
        {
          _next ();
          return new IExpr.SequenceExpr (new CommonsArrayList <> ());
        }
        final IExpr ret = _parseExpr ();
        _expect (ETokenKind.RPAREN, "')'");
        return ret;
      case STAR:
        _next ();
        return null;
      default:
        throw _unexpected ("a lookup key (NCName, integer, parenthesised expression or '*')");
    }
  }

  /**
   * Parse a map constructor <code>map { k : v, ... }</code>; the current
   * token is <code>map</code>. Keys and values are kept in two parallel lists
   * in source order; duplicate keys are a dynamic error and not checked here.
   */
  @NonNull
  private IExpr _parseMapConstructor ()
  {
    _next ();
    _expect (ETokenKind.LBRACE, "'{'");
    final ICommonsList <IExpr> aKeys = new CommonsArrayList <> ();
    final ICommonsList <IExpr> aValues = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RBRACE))
      while (true)
      {
        aKeys.add (_parseExprSingle ());
        _expect (ETokenKind.COLON, "':' in map constructor entry");
        aValues.add (_parseExprSingle ());
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RBRACE, "'}' or ','");
    return new IExpr.MapConstructorExpr (aKeys, aValues);
  }

  /**
   * Parse a square array constructor <code>[a, b, ...]</code>; the current
   * token is <code>[</code>. Every ExprSingle is one member.
   */
  @NonNull
  private IExpr _parseSquareArray ()
  {
    _next ();
    final ICommonsList <IExpr> aItems = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RBRACKET))
      while (true)
      {
        aItems.add (_parseExprSingle ());
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RBRACKET, "']' or ','");
    return new IExpr.ArrayConstructorExpr (aItems, false);
  }

  /**
   * Parse a curly array constructor <code>array { expr }</code>; the current
   * token is <code>array</code>. The result holds the enclosed expression (or
   * nothing for <code>array {}</code>); each item of its value becomes one
   * member at evaluation.
   */
  @NonNull
  private IExpr _parseCurlyArray ()
  {
    _next ();
    _expect (ETokenKind.LBRACE, "'{'");
    final ICommonsList <IExpr> aItems = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RBRACE))
      aItems.add (_parseExpr ());
    _expect (ETokenKind.RBRACE, "'}'");
    return new IExpr.ArrayConstructorExpr (aItems, true);
  }

  /**
   * Parse an inline function expression <code>function ($a as T, ...) as R {
   * body }</code>; the current token is <code>function</code>. Parameter and
   * return types are optional (<code>null</code> if absent), an empty body is
   * the empty sequence. The parameters are in scope while the body is parsed.
   *
   * @throws XPathException
   *         XQST0039 for two parameters of the same name.
   */
  @NonNull
  private IExpr _parseInlineFunction ()
  {
    _next ();
    _expect (ETokenKind.LPAREN, "'('");
    final ICommonsList <IExpr.Param> aParams = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RPAREN))
      while (true)
      {
        _expect (ETokenKind.DOLLAR, "'$'");
        final XPathToken aNameTok = _cur ();
        final XdmQName aName = _parseVarName ();
        for (final IExpr.Param aPrev : aParams)
          if (aPrev.name ().equals (aName))
            throw _error ("XQST0039", "Duplicate parameter name $" + aName.getEQName (), aNameTok);
        SeqType aType = null;
        if (_cur ().isNCName ("as"))
        {
          _next ();
          aType = _parseSequenceType ();
        }
        aParams.add (new IExpr.Param (aName, aType));
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RPAREN, "')' or ','");
    SeqType aReturnType = null;
    if (_cur ().isNCName ("as"))
    {
      _next ();
      aReturnType = _parseSequenceType ();
    }
    _expect (ETokenKind.LBRACE, "'{'");
    for (final IExpr.Param aParam : aParams)
      m_aScope.add (aParam.name ());
    try
    {
      final IExpr aBody;
      if (_is (ETokenKind.RBRACE))
        aBody = new IExpr.SequenceExpr (new CommonsArrayList <> ());
      else
        aBody = _parseExpr ();
      _expect (ETokenKind.RBRACE, "'}'");
      return new IExpr.InlineFunctionExpr (aParams, aReturnType, aBody);
    }
    finally
    {
      _popScope (aParams.size ());
    }
  }

  /**
   * Parse a named function reference <code>name#arity</code>; the current
   * token is the name, the next one <code>#</code>. With a schema set, an
   * arity of 1 and no library function of that name, a user-defined simple
   * type of that name yields its constructor function (XPath 3.1 section
   * 3.14.4), as {@link #_getUserTypeConstructorFunction(IXdmTypeDefinition)}
   * builds it.
   *
   * @throws XPathException
   *         FOAR0002 for an arity that does not fit into 31 bits, XPST0017
   *         for an unknown function.
   */
  @NonNull
  private IExpr _parseNamedFunctionRef ()
  {
    final XPathToken aNameTok = _next ();
    _next ();
    final XPathToken aArityTok = _expect (ETokenKind.INTEGER, "an integer arity");
    final BigInteger aArity = new BigInteger (aArityTok.text ());
    if (aArity.bitLength () > 31)
      throw _error ("FOAR0002", "Function arity " + aArityTok.text () + " is out of range", aArityTok);
    final XdmQName aName = _resolveFunctionName (aNameTok);
    if (aArity.intValue () == 1 && m_aCtx.getSchemaSet () != null && m_aCtx.getFunctionLibrary ().getFunction (aName, 1) == null)
    {
      // The constructor function of a user-defined simple type
      final IXdmTypeDefinition aUserType = _getUserSimpleType (aName);
      if (aUserType != null)
        return _getUserTypeConstructorFunction (aUserType);
    }
    _checkFunction (aName, aArity.intValue (), aNameTok);
    return new IExpr.NamedFunctionRefExpr (aName, aArity.intValue ());
  }

  /**
   * Parse a variable reference; the current token is <code>$</code>.
   *
   * @throws XPathException
   *         XPST0008 if the variable is neither bound in the expression nor
   *         declared in the static context.
   */
  @NonNull
  private IExpr _parseVarRef ()
  {
    _next ();
    final XPathToken aTok = _cur ();
    final XdmQName aName = _parseVarName ();
    if (!_isVariableInScope (aName))
      throw _error ("XPST0008", "The variable $" + aName.getEQName () + " is not declared", aTok);
    return new IExpr.VarRefExpr (aName);
  }

  /**
   * Parse a PrimaryExpr (XPath 3.1 section 3.1): a literal, variable
   * reference, parenthesised expression (<code>()</code> is the empty
   * sequence), context item, square array, unary lookup, map or curly array
   * constructor, inline function, named function reference or static function
   * call. A call of a user-defined simple type with one argument becomes a
   * cast, or the constructor function for a placeholder argument (see
   * {@link #_getSchemaConstructor(XdmQName, List)}); every other call must
   * name a known function.
   *
   * @throws XPathException
   *         XPST0003 if no primary expression starts here, XPST0017 for an
   *         unknown function.
   */
  @NonNull
  private IExpr _parsePrimary ()
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case STRING:
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.string (aTok.text ()));
      case INTEGER:
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.integer (new BigInteger (aTok.text ())));
      case DECIMAL:
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.decimal (new BigDecimal (aTok.text ())));
      case DOUBLE:
        _next ();
        return new IExpr.LiteralExpr (XdmAtomic.dbl (Double.parseDouble (aTok.text ())));
      case DOLLAR:
        return _parseVarRef ();
      case LPAREN:
      {
        _next ();
        if (_is (ETokenKind.RPAREN))
        {
          _next ();
          return new IExpr.SequenceExpr (new CommonsArrayList <> ());
        }
        final IExpr ret = _parseExpr ();
        _expect (ETokenKind.RPAREN, "')'");
        return ret;
      }
      case DOT:
        _next ();
        return new IExpr.ContextItemExpr ();
      case LBRACKET:
        return _parseSquareArray ();
      case QUESTION:
        _next ();
        return new IExpr.LookupExpr (null, _parseLookupKey ());
      case NAME:
      case BRACED_NAME:
      {
        final ETokenKind eNext = _peekKind (1);
        if (aTok.isNCName ("map") && eNext == ETokenKind.LBRACE)
          return _parseMapConstructor ();
        if (aTok.isNCName ("array") && eNext == ETokenKind.LBRACE)
          return _parseCurlyArray ();
        if (aTok.isNCName ("function") && eNext == ETokenKind.LPAREN)
          return _parseInlineFunction ();
        if (eNext == ETokenKind.HASH)
          return _parseNamedFunctionRef ();
        if (eNext == ETokenKind.LPAREN)
        {
          _next ();
          final XdmQName aName = _resolveFunctionName (aTok);
          final ICommonsList <IExpr> aArgs = _parseArgList ();
          final IExpr aConstructor = _getSchemaConstructor (aName, aArgs);
          if (aConstructor != null)
            return aConstructor;
          _checkFunction (aName, aArgs.size (), aTok);
          return new IExpr.FunctionCallExpr (aName, aArgs);
        }
        throw _unexpected ("a primary expression");
      }
      default:
        throw _unexpected ("a primary expression");
    }
  }

  /** PostfixExpr ::= PrimaryExpr (Predicate | ArgumentList | Lookup)* */
  @NonNull
  private IExpr _parsePostfix ()
  {
    IExpr ret = _parsePrimary ();
    final int nDepthBefore = m_nDepth;
    try
    {
      ICommonsList <IExpr> aPreds = null;
      while (true)
      {
        final ETokenKind eKind = _cur ().kind ();
        if (eKind == ETokenKind.LBRACKET)
        {
          if (aPreds == null)
            aPreds = new CommonsArrayList <> ();
          _next ();
          aPreds.add (_parseExpr ());
          _expect (ETokenKind.RBRACKET, "']'");
          continue;
        }
        if (eKind != ETokenKind.LPAREN && eKind != ETokenKind.QUESTION)
          break;
        if (aPreds != null)
        {
          _enterDepth ();
          ret = new IExpr.FilterExpr (ret, aPreds);
          aPreds = null;
        }
        if (eKind == ETokenKind.LPAREN)
          ret = new IExpr.DynamicCallExpr (ret, _parseArgList ());
        else
        {
          _next ();
          ret = new IExpr.LookupExpr (ret, _parseLookupKey ());
        }
        // Each postfix operator nests the AST one level deeper
        _enterDepth ();
      }
      if (aPreds != null)
        ret = new IExpr.FilterExpr (ret, aPreds);
      return ret;
    }
    finally
    {
      m_nDepth = nDepthBefore;
    }
  }

  /**
   * Decide whether a step starts with a primary expression rather than an
   * axis step. A name followed by <code>(</code> is a function call unless it
   * is a reserved function name (kind tests, <code>if</code>, ...); a name
   * otherwise only starts a primary as <code>name#</code>,
   * <code>map {</code>, <code>array {</code> or <code>function (</code>.
   */
  private boolean _startsPrimary ()
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case DOLLAR:
      case STRING:
      case INTEGER:
      case DECIMAL:
      case DOUBLE:
      case LPAREN:
      case LBRACKET:
      case DOT:
      case QUESTION:
        return true;
      case NAME:
      case BRACED_NAME:
      {
        final ETokenKind eNext = _peekKind (1);
        if (eNext == ETokenKind.HASH)
          return true;
        if ((aTok.isNCName ("map") || aTok.isNCName ("array")) && eNext == ETokenKind.LBRACE)
          return true;
        if (aTok.isNCName ("function") && eNext == ETokenKind.LPAREN)
          return true;
        if (eNext == ETokenKind.LPAREN)
          return !(aTok.isUnprefixedName () && RESERVED_FUNCTION_NAMES.contains (aTok.text ()));
        return false;
      }
      default:
        return false;
    }
  }

  /**
   * @return <code>true</code> if the current token can start a relative path
   *         expression. Decides whether a leading <code>/</code> stands alone
   *         (XPath 3.1 A.1.2 leading-lone-slash); <code>/</code> followed by
   *         an operator name like <code>union</code> is therefore a path.
   */
  private boolean _startsRelativePath ()
  {
    switch (_cur ().kind ())
    {
      case NAME:
      case BRACED_NAME:
      case STAR:
      case PREFIX_WILDCARD:
      case LOCAL_WILDCARD:
      case BRACED_WILDCARD:
      case AT:
      case DOT:
      case DOTDOT:
      case DOLLAR:
      case STRING:
      case INTEGER:
      case DECIMAL:
      case DOUBLE:
      case LPAREN:
      case LBRACKET:
      case QUESTION:
        return true;
      default:
        return false;
    }
  }

  /**
   * @return A new <code>descendant-or-self::node()</code> step without
   *         predicates, the expansion of <code>//</code> (XPath 3.1 section
   *         3.3.5).
   */
  @NonNull
  private static StepExpr _descendantOrSelfStep ()
  {
    return new StepExpr (EAxis.DESCENDANT_OR_SELF, true, NodeTest.ANY_NODE, new CommonsArrayList <> (), null);
  }

  /**
   * <code>A//B[p]</code> is <code>A/descendant-or-self::node()/child::B[p]</code>,
   * where the context position and size of <code>p</code> count B among the
   * children of each intermediate node, but in <code>descendant::B[p]</code>
   * among all descendants of A. The two are equivalent when every predicate is
   * focus-free and non-numeric (a number is read as a position), which is what
   * this checks.
   *
   * @return The <code>descendant-or-self::node()</code> step, carrying the
   *         fused step when the equivalence holds.
   */
  @NonNull
  private static StepExpr _fuseSlashSlash (@NonNull final StepExpr aNext)
  {
    final StepExpr aDescendantOrSelf = _descendantOrSelfStep ();
    if (aNext.postfix () != null || aNext.axis () != EAxis.CHILD)
      return aDescendantOrSelf;
    for (final IExpr aPredicate : aNext.predicates ())
      if (!ExprWalker.isFocusFreeBoolean (aPredicate))
        return aDescendantOrSelf;
    final StepExpr aFused = new StepExpr (EAxis.DESCENDANT, true, aNext.test (), aNext.predicates (), null);
    return new StepExpr (aDescendantOrSelf.axis (),
                         aDescendantOrSelf.axisExplicit (),
                         aDescendantOrSelf.test (),
                         aDescendantOrSelf.predicates (),
                         null,
                         aFused);
  }

  /** StepExpr ::= PostfixExpr | AxisStep */
  @NonNull
  private StepExpr _parseStep ()
  {
    final XPathToken aTok = _cur ();
    if (aTok.kind () == ETokenKind.DOTDOT)
    {
      _next ();
      return new StepExpr (EAxis.PARENT, true, NodeTest.ANY_NODE, _parsePredicates (), null);
    }
    if (aTok.kind () == ETokenKind.AT)
    {
      _next ();
      final NodeTest aTest = _parseNodeTest (EAxis.ATTRIBUTE);
      return new StepExpr (EAxis.ATTRIBUTE, true, aTest, _parsePredicates (), null);
    }
    if (aTok.kind () == ETokenKind.NAME && _peekKind (1) == ETokenKind.COLONCOLON)
    {
      final EAxis eAxis = aTok.text ().indexOf (':') < 0 ? EAxis.getFromName (aTok.text ()) : null;
      if (eAxis == null)
        throw _syntax ("Unknown axis '" + aTok.text () + "'", aTok);
      _next ();
      _next ();
      final NodeTest aTest = _parseNodeTest (eAxis);
      return new StepExpr (eAxis, true, aTest, _parsePredicates (), null);
    }
    if (_startsPrimary ())
      return new StepExpr (null, false, null, new CommonsArrayList <> (), _parsePostfix ());

    final NodeTest aTest = _parseNodeTest (EAxis.CHILD);
    // Abbreviated syntax: attribute() and schema-attribute() select on the
    // attribute axis, namespace-node() on the namespace axis
    EAxis eAxis = EAxis.CHILD;
    if (aTest.kind () == ENodeTestKind.ATTRIBUTE || aTest.kind () == ENodeTestKind.SCHEMA_ATTRIBUTE)
      eAxis = EAxis.ATTRIBUTE;
    else
      if (aTest.kind () == ENodeTestKind.NAMESPACE_NODE)
        eAxis = EAxis.NAMESPACE;
    return new StepExpr (eAxis, false, aTest, _parsePredicates (), null);
  }

  /**
   * Parse the continuation <code>("/" | "//") StepExpr</code> as long as it
   * repeats and append the steps. <code>//</code> adds the
   * <code>descendant-or-self::node()</code> step (possibly carrying a fused
   * step, see {@link #_fuseSlashSlash(StepExpr)}) followed by the next step
   * itself.
   */
  private void _parseRelativeSteps (@NonNull final ICommonsList <StepExpr> aSteps)
  {
    while (true)
    {
      final ETokenKind eKind = _cur ().kind ();
      if (eKind == ETokenKind.SLASH)
      {
        _next ();
        aSteps.add (_parseStep ());
      }
      else
        if (eKind == ETokenKind.SLASHSLASH)
        {
          _next ();
          final StepExpr aNext = _parseStep ();
          aSteps.add (_fuseSlashSlash (aNext));
          aSteps.add (aNext);
        }
        else
          return;
    }
  }

  /**
   * Parse a PathExpr (XPath 3.1 section 3.3). A leading <code>/</code> or
   * <code>//</code> makes an absolute path without a start expression. A
   * relative path whose first step is a primary expression has it as start
   * expression and the following steps as steps; if no <code>/</code>
   * follows, the primary expression is returned on its own, not wrapped in a
   * path.
   */
  @NonNull
  private IExpr _parsePath ()
  {
    final ETokenKind eKind = _cur ().kind ();
    final ICommonsList <StepExpr> aSteps = new CommonsArrayList <> ();
    if (eKind == ETokenKind.SLASH)
    {
      _next ();
      // A lone "/" unless the next token can start a relative path
      // (XPath 3.1 A.1.2 leading-lone-slash)
      if (_startsRelativePath ())
      {
        aSteps.add (_parseStep ());
        _parseRelativeSteps (aSteps);
      }
      return new IExpr.PathExpr (null, true, aSteps);
    }
    if (eKind == ETokenKind.SLASHSLASH)
    {
      _next ();
      final StepExpr aNext = _parseStep ();
      aSteps.add (_fuseSlashSlash (aNext));
      aSteps.add (aNext);
      _parseRelativeSteps (aSteps);
      return new IExpr.PathExpr (null, true, aSteps);
    }
    final StepExpr aFirst = _parseStep ();
    final ETokenKind eNext = _cur ().kind ();
    final boolean bMore = eNext == ETokenKind.SLASH || eNext == ETokenKind.SLASHSLASH;
    if (aFirst.postfix () != null)
    {
      if (!bMore)
        return aFirst.postfix ();
      _parseRelativeSteps (aSteps);
      return new IExpr.PathExpr (aFirst.postfix (), false, aSteps);
    }
    aSteps.add (aFirst);
    _parseRelativeSteps (aSteps);
    return new IExpr.PathExpr (null, false, aSteps);
  }

  /**
   * Parse a SimpleMapExpr <code>a ! b ! c</code> (XPath 3.1 section 3.15);
   * a single path is returned unwrapped.
   */
  @NonNull
  private IExpr _parseSimpleMap ()
  {
    final IExpr aFirst = _parsePath ();
    if (!_is (ETokenKind.BANG))
      return aFirst;
    final ICommonsList <IExpr> aSteps = new CommonsArrayList <> (aFirst);
    while (_is (ETokenKind.BANG))
    {
      _next ();
      aSteps.add (_parsePath ());
    }
    return new IExpr.SimpleMapExpr (aSteps);
  }

  /** @return A new list with the first expression followed by the rest. */
  @NonNull
  private static ICommonsList <IExpr> _prepend (@NonNull final IExpr aFirst, @NonNull final List <IExpr> aRest)
  {
    final ICommonsList <IExpr> ret = new CommonsArrayList <> (aRest.size () + 1);
    ret.add (aFirst);
    ret.addAll (aRest);
    return ret;
  }

  /**
   * Parse the part after one <code>=&gt;</code> (XPath 3.1 section 3.16): a
   * function name, a variable reference or a parenthesised expression,
   * followed by an argument list. The base expression becomes the first
   * argument. With a name it is a static call (or a cast for the constructor
   * of a user-defined simple type), checked like any other; otherwise it is a
   * dynamic call.
   *
   * @param aBase
   *        The expression on the left of the arrow.
   * @throws XPathException
   *         XPST0003 if no function specifier follows, XPST0017 for an
   *         unknown function, XPST0008 for an undeclared variable.
   */
  @NonNull
  private IExpr _parseArrowStep (@NonNull final IExpr aBase)
  {
    final XPathToken aTok = _cur ();
    if (aTok.isEQName ())
    {
      _next ();
      final XdmQName aName = _resolveFunctionName (aTok);
      final ICommonsList <IExpr> aArgs = _prepend (aBase, _parseArgList ());
      final IExpr aConstructor = _getSchemaConstructor (aName, aArgs);
      if (aConstructor != null)
        return aConstructor;
      _checkFunction (aName, aArgs.size (), aTok);
      return new IExpr.FunctionCallExpr (aName, aArgs);
    }
    final IExpr aFunc;
    if (aTok.kind () == ETokenKind.DOLLAR)
      aFunc = _parseVarRef ();
    else
      if (aTok.kind () == ETokenKind.LPAREN)
      {
        _next ();
        if (_is (ETokenKind.RPAREN))
          aFunc = new IExpr.SequenceExpr (new CommonsArrayList <> ());
        else
          aFunc = _parseExpr ();
        _expect (ETokenKind.RPAREN, "')'");
      }
      else
        throw _unexpected ("a function name, variable reference or parenthesised expression after '=>'");
    return new IExpr.DynamicCallExpr (aFunc, _prepend (aBase, _parseArgList ()));
  }

  /**
   * Consume a two-word keyword such as <code>cast as</code> or
   * <code>instance of</code> if the current token is its first word.
   *
   * @return <code>false</code> (nothing consumed) if the current token is not
   *         the first word, <code>true</code> after consuming both words.
   * @throws XPathException
   *         XPST0003 if the first word is not followed by the second one.
   */
  private boolean _isKeywordPair (@NonNull final String sFirst, @NonNull final String sSecond)
  {
    if (!_cur ().isNCName (sFirst))
      return false;
    if (!_peek (1).isNCName (sSecond))
      throw _syntax ("Expected '" + sSecond + "' after '" + sFirst + "'", _peek (1));
    _next ();
    _next ();
    return true;
  }

  /**
   * UnaryExpr, ArrowExpr, CastExpr, CastableExpr, TreatExpr and InstanceofExpr
   * in one method.
   */
  @NonNull
  private IExpr _parseOperand ()
  {
    boolean bSigned = false;
    boolean bNegate = false;
    while (_is (ETokenKind.MINUS) || _is (ETokenKind.PLUS))
    {
      if (_is (ETokenKind.MINUS))
        bNegate = !bNegate;
      bSigned = true;
      _next ();
    }
    IExpr ret = _parseSimpleMap ();
    if (bSigned)
      ret = new IExpr.UnaryExpr (bNegate, ret);
    final int nDepthBefore = m_nDepth;
    try
    {
      while (_is (ETokenKind.ARROW))
      {
        _next ();
        ret = _parseArrowStep (ret);
        _enterDepth ();
      }
      if (_isKeywordPair ("cast", "as"))
        ret = new IExpr.CastExpr (ret, _parseSingleType (), false);
      if (_isKeywordPair ("castable", "as"))
        ret = new IExpr.CastExpr (ret, _parseSingleType (), true);
      if (_isKeywordPair ("treat", "as"))
        ret = new IExpr.TreatExpr (ret, _parseSequenceType ());
      if (_isKeywordPair ("instance", "of"))
        ret = new IExpr.InstanceOfExpr (ret, _parseSequenceType ());
      return ret;
    }
    finally
    {
      m_nDepth = nDepthBefore;
    }
  }

  /**
   * @return The binary operator the current token stands for, without
   *         consuming it, or <code>null</code> if it is none. Called only
   *         where an operator is expected, so <code>*</code> is multiplication
   *         and names like <code>div</code> or <code>and</code> are operators
   *         here.
   */
  @Nullable
  private EOp _peekBinaryOp ()
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case EQ:
        return EOp.GENERAL_EQ;
      case NE:
        return EOp.GENERAL_NE;
      case LT:
        return EOp.GENERAL_LT;
      case LE:
        return EOp.GENERAL_LE;
      case GT:
        return EOp.GENERAL_GT;
      case GE:
        return EOp.GENERAL_GE;
      case PRECEDES:
        return EOp.NODE_PRECEDES;
      case FOLLOWS:
        return EOp.NODE_FOLLOWS;
      case CONCAT:
        return EOp.CONCAT;
      case PLUS:
        return EOp.ADD;
      case MINUS:
        return EOp.SUBTRACT;
      case STAR:
        return EOp.MULTIPLY;
      case PIPE:
        return EOp.UNION;
      case NAME:
        switch (aTok.text ())
        {
          case "or":
            return EOp.OR;
          case "and":
            return EOp.AND;
          case "eq":
            return EOp.VALUE_EQ;
          case "ne":
            return EOp.VALUE_NE;
          case "lt":
            return EOp.VALUE_LT;
          case "le":
            return EOp.VALUE_LE;
          case "gt":
            return EOp.VALUE_GT;
          case "ge":
            return EOp.VALUE_GE;
          case "is":
            return EOp.NODE_IS;
          case "to":
            return EOp.TO;
          case "div":
            return EOp.DIVIDE;
          case "idiv":
            return EOp.INTEGER_DIVIDE;
          case "mod":
            return EOp.MODULO;
          case "union":
            return EOp.UNION;
          case "intersect":
            return EOp.INTERSECT;
          case "except":
            return EOp.EXCEPT;
          default:
            return null;
        }
      default:
        return null;
    }
  }

  /**
   * Build the AST node of one binary operation. A chain <code>a || b ||
   * c</code> becomes one {@link IExpr.StringConcatExpr} with all parts: a left
   * operand is flattened only if it was built by this same chain (it is in
   * the own-concats set), never a parenthesised one.
   *
   * @param aOwnConcats
   *        The string concatenations built by the current operator chain,
   *        by identity; a new one is added.
   */
  @NonNull
  private static IExpr _build (@NonNull final EOp eOp,
                               @NonNull final IExpr aLeft,
                               @NonNull final IExpr aRight,
                               @NonNull final Set <IExpr> aOwnConcats)
  {
    return switch (eOp)
    {
      case OR -> new IExpr.BinaryExpr (EBinaryOp.OR, aLeft, aRight);
      case AND -> new IExpr.BinaryExpr (EBinaryOp.AND, aLeft, aRight);
      case ADD -> new IExpr.BinaryExpr (EBinaryOp.ADD, aLeft, aRight);
      case SUBTRACT -> new IExpr.BinaryExpr (EBinaryOp.SUBTRACT, aLeft, aRight);
      case MULTIPLY -> new IExpr.BinaryExpr (EBinaryOp.MULTIPLY, aLeft, aRight);
      case DIVIDE -> new IExpr.BinaryExpr (EBinaryOp.DIVIDE, aLeft, aRight);
      case INTEGER_DIVIDE -> new IExpr.BinaryExpr (EBinaryOp.INTEGER_DIVIDE, aLeft, aRight);
      case MODULO -> new IExpr.BinaryExpr (EBinaryOp.MODULO, aLeft, aRight);
      case UNION -> new IExpr.SetExpr (IExpr.SetExpr.ESetOp.UNION, aLeft, aRight);
      case INTERSECT -> new IExpr.SetExpr (IExpr.SetExpr.ESetOp.INTERSECT, aLeft, aRight);
      case EXCEPT -> new IExpr.SetExpr (IExpr.SetExpr.ESetOp.EXCEPT, aLeft, aRight);
      case TO -> new IExpr.RangeExpr (aLeft, aRight);
      case CONCAT ->
      {
        final ICommonsList <IExpr> aParts = new CommonsArrayList <> ();
        if (aLeft instanceof final IExpr.StringConcatExpr aConcat && aOwnConcats.contains (aLeft))
          aParts.addAll (aConcat.parts ());
        else
          aParts.add (aLeft);
        aParts.add (aRight);
        final IExpr ret = new IExpr.StringConcatExpr (aParts);
        aOwnConcats.add (ret);
        yield ret;
      }
      default -> new IExpr.ComparisonExpr (EComparisonOp.valueOf (eOp.name ()), aLeft, aRight);
    };
  }

  /**
   * Replace the two topmost operands by the node built with the topmost
   * operator, which is removed.
   */
  private static void _reduce (@NonNull final ICommonsList <IExpr> aOperands,
                               @NonNull final ICommonsList <EOp> aOps,
                               @NonNull final Set <IExpr> aOwnConcats)
  {
    final EOp eOp = aOps.removeLast ();
    final IExpr aRight = aOperands.removeLast ();
    final IExpr aLeft = aOperands.removeLast ();
    aOperands.add (_build (eOp, aLeft, aRight, aOwnConcats));
  }

  /** OrExpr down to IntersectExceptExpr with operator precedence. */
  @NonNull
  private IExpr _parseBinary ()
  {
    final IExpr aFirst = _parseOperand ();
    EOp eOp = _peekBinaryOp ();
    if (eOp == null)
      return aFirst;
    final ICommonsList <IExpr> aOperands = new CommonsArrayList <> (aFirst);
    final ICommonsList <EOp> aOps = new CommonsArrayList <> ();
    final Set <IExpr> aOwnConcats = Collections.newSetFromMap (new IdentityHashMap <> ());
    final int nDepthBefore = m_nDepth;
    try
    {
      while (eOp != null)
      {
        final XPathToken aOpTok = _next ();
        while (aOps.isNotEmpty () && aOps.getLastOrNull ().m_nPrec >= eOp.m_nPrec)
        {
          if (aOps.getLastOrNull ().m_nPrec == eOp.m_nPrec && eOp.isNonAssociative ())
            throw _syntax ("Operator '" + aOpTok.text () + "' cannot be chained without parentheses", aOpTok);
          _reduce (aOperands, aOps, aOwnConcats);
        }
        aOps.add (eOp);
        aOperands.add (_parseOperand ());
        // Each operator of a chain may nest the AST one level deeper
        _enterDepth ();
        eOp = _peekBinaryOp ();
      }
      while (aOps.isNotEmpty ())
        _reduce (aOperands, aOps, aOwnConcats);
      return aOperands.getFirstOrNull ();
    }
    finally
    {
      m_nDepth = nDepthBefore;
    }
  }

  /**
   * Parse the comma-separated bindings <code>$x in E</code> (or
   * <code>$x := E</code> for let) and add them to the list. Each variable is
   * pushed onto the scope right after its own expression, so it is visible in
   * the following bindings and the body but not in its own expression. The
   * caller pops as many variables as bindings were added, also on an error.
   */
  private void _parseBindings (@NonNull final ICommonsList <IExpr.Binding> aBindings, final boolean bLet)
  {
    while (true)
    {
      _expect (ETokenKind.DOLLAR, "'$'");
      final XdmQName aName = _parseVarName ();
      if (bLet)
        _expect (ETokenKind.ASSIGN, "':='");
      else
        _expectKeyword ("in");
      final IExpr aExpr = _parseExprSingle ();
      aBindings.add (new IExpr.Binding (aName, null, aExpr));
      // The variable is in scope for the following bindings and the body
      m_aScope.add (aName);
      if (!_is (ETokenKind.COMMA))
        break;
      _next ();
    }
  }

  /**
   * Parse a for, let, some or every expression (XPath 3.1 sections 3.9, 3.10
   * and 3.13); the current token is the keyword, the next one
   * <code>$</code>. The body follows <code>return</code>, for the
   * quantifiers <code>satisfies</code>.
   */
  @NonNull
  private IExpr _parseForLetQuantified (@NonNull final String sKeyword)
  {
    _next ();
    final ICommonsList <IExpr.Binding> aBindings = new CommonsArrayList <> ();
    try
    {
      _parseBindings (aBindings, sKeyword.equals ("let"));
      _expectKeyword (sKeyword.equals ("for") || sKeyword.equals ("let") ? "return" : "satisfies");
      final IExpr aBody = _parseExprSingle ();
      return switch (sKeyword)
      {
        case "for" -> new IExpr.ForExpr (aBindings, aBody);
        case "let" -> new IExpr.LetExpr (aBindings, aBody);
        default -> new IExpr.QuantifiedExpr (sKeyword.equals ("every"), aBindings, aBody);
      };
    }
    finally
    {
      _popScope (aBindings.size ());
    }
  }

  /**
   * Parse <code>if (E) then A else B</code> (XPath 3.1 section 3.12); the
   * current token is <code>if</code>. The else branch is mandatory.
   */
  @NonNull
  private IExpr _parseIf ()
  {
    _next ();
    _expect (ETokenKind.LPAREN, "'('");
    final IExpr aCond = _parseExpr ();
    _expect (ETokenKind.RPAREN, "')'");
    _expectKeyword ("then");
    final IExpr aThen = _parseExprSingle ();
    _expectKeyword ("else");
    final IExpr aElse = _parseExprSingle ();
    return new IExpr.IfExpr (aCond, aThen, aElse);
  }

  /**
   * Parse an ExprSingle as one nesting level: a for, let, some or every
   * expression if the keyword is followed by <code>$</code>, an if
   * expression if <code>if</code> is followed by <code>(</code>, otherwise an
   * OrExpr.
   */
  @NonNull
  private IExpr _parseExprSingle ()
  {
    _enterDepth ();
    try
    {
      final XPathToken aTok = _cur ();
      if (aTok.kind () == ETokenKind.NAME)
      {
        final ETokenKind eNext = _peekKind (1);
        switch (aTok.text ())
        {
          case "for":
          case "let":
          case "some":
          case "every":
            if (eNext == ETokenKind.DOLLAR)
              return _parseForLetQuantified (aTok.text ());
            break;
          case "if":
            if (eNext == ETokenKind.LPAREN)
              return _parseIf ();
            break;
          default:
            break;
        }
      }
      return _parseBinary ();
    }
    finally
    {
      m_nDepth--;
    }
  }

  /**
   * Parse an Expr: comma-separated ExprSingles as a
   * {@link IExpr.SequenceExpr}, a single one unwrapped.
   */
  @NonNull
  private IExpr _parseExpr ()
  {
    final IExpr aFirst = _parseExprSingle ();
    if (!_is (ETokenKind.COMMA))
      return aFirst;
    final ICommonsList <IExpr> aItems = new CommonsArrayList <> (aFirst);
    while (_is (ETokenKind.COMMA))
    {
      _next ();
      aItems.add (_parseExprSingle ());
    }
    return new IExpr.SequenceExpr (aItems);
  }

  /** Remember the source text of a pattern operand from a start token to the current position. */
  private void _rememberPatternSource (@NonNull final IExpr aExpr, @NonNull final XPathToken aStartTok)
  {
    if (m_aPatternSources != null)
    {
      final int nEnd = _is (ETokenKind.EOF) ? m_sSrc.length () : _cur ().pos ();
      m_aPatternSources.put (aExpr, m_sSrc.substring (aStartTok.pos (), Math.max (aStartTok.pos (), nEnd)).trim ());
    }
  }

  /**
   * A step of a pattern (StepExprP): a parenthesised pattern with predicates,
   * or an axis step on one of the forward axes XSLT 3.0 allows in patterns.
   */
  @NonNull
  private StepExpr _parsePatternStep ()
  {
    final XPathToken aTok = _cur ();
    switch (aTok.kind ())
    {
      case LPAREN:
      {
        _next ();
        final IExpr aInner = _parsePatternUnion ();
        _expect (ETokenKind.RPAREN, "')'");
        return new StepExpr (null, false, null, _parsePredicates (), aInner);
      }
      case AT:
      {
        _next ();
        final NodeTest aTest = _parseNodeTest (EAxis.ATTRIBUTE);
        return new StepExpr (EAxis.ATTRIBUTE, true, aTest, _parsePredicates (), null);
      }
      case NAME:
      case BRACED_NAME:
      case STAR:
      case PREFIX_WILDCARD:
      case LOCAL_WILDCARD:
      case BRACED_WILDCARD:
        break;
      default:
        throw _unexpected ("a pattern step");
    }
    if (aTok.kind () == ETokenKind.NAME && _peekKind (1) == ETokenKind.COLONCOLON)
    {
      final EAxis eAxis = aTok.text ().indexOf (':') < 0 ? EAxis.getFromName (aTok.text ()) : null;
      if (eAxis == null)
        throw _syntax ("Unknown axis '" + aTok.text () + "'", aTok);
      switch (eAxis)
      {
        case CHILD:
        case DESCENDANT:
        case ATTRIBUTE:
        case SELF:
        case DESCENDANT_OR_SELF:
        case NAMESPACE:
          break;
        default:
          throw _syntax ("The axis '" + aTok.text () + "' is not allowed in a pattern", aTok);
      }
      _next ();
      _next ();
      final NodeTest aTest = _parseNodeTest (eAxis);
      return new StepExpr (eAxis, true, aTest, _parsePredicates (), null);
    }
    if (aTok.isEQName () &&
        _peekKind (1) == ETokenKind.LPAREN &&
        !(aTok.isUnprefixedName () && KIND_TEST_NAMES.contains (aTok.text ())))
      throw _syntax ("A function call is only allowed at the start of a pattern", aTok);
    final NodeTest aTest = _parseNodeTest (EAxis.CHILD);
    EAxis eAxis = EAxis.CHILD;
    if (aTest.kind () == ENodeTestKind.ATTRIBUTE || aTest.kind () == ENodeTestKind.SCHEMA_ATTRIBUTE)
      eAxis = EAxis.ATTRIBUTE;
    else
      if (aTest.kind () == ENodeTestKind.NAMESPACE_NODE)
        eAxis = EAxis.NAMESPACE;
    return new StepExpr (eAxis, false, aTest, _parsePredicates (), null);
  }

  /** RelativePathExprP continuation: ("/" | "//") StepExprP, repeated. */
  private void _parsePatternRelativeSteps (@NonNull final ICommonsList <StepExpr> aSteps)
  {
    while (true)
    {
      final ETokenKind eKind = _cur ().kind ();
      if (eKind == ETokenKind.SLASH)
      {
        _next ();
        aSteps.add (_parsePatternStep ());
      }
      else
        if (eKind == ETokenKind.SLASHSLASH)
        {
          _next ();
          aSteps.add (_descendantOrSelfStep ());
          aSteps.add (_parsePatternStep ());
        }
        else
          return;
    }
  }

  /**
   * @return <code>true</code> if the current token can start a StepExprP of a
   *         pattern; decides whether a leading <code>/</code> of a pattern
   *         stands alone.
   */
  private boolean _startsPatternStep ()
  {
    switch (_cur ().kind ())
    {
      case NAME:
      case BRACED_NAME:
      case STAR:
      case PREFIX_WILDCARD:
      case LOCAL_WILDCARD:
      case BRACED_WILDCARD:
      case AT:
      case LPAREN:
        return true;
      default:
        return false;
    }
  }

  /**
   * The argument of a FunctionCallP: a variable reference or a literal. The
   * context item is accepted for <code>root(.)</code>, which the XSLT 3.0
   * Recommendation uses in its own examples.
   */
  @NonNull
  private IExpr _parsePatternArgument (final boolean bAllowContextItem)
  {
    switch (_cur ().kind ())
    {
      case DOLLAR:
        return _parseVarRef ();
      case STRING:
      case INTEGER:
      case DECIMAL:
      case DOUBLE:
        return _parsePrimary ();
      case DOT:
        if (bAllowContextItem)
        {
          _next ();
          return new IExpr.ContextItemExpr ();
        }
        throw _unexpected ("a variable reference or a literal");
      default:
        throw _unexpected ("a variable reference or a literal");
    }
  }

  /** FunctionCallP: one of doc, id, element-with-id, key, root in the fn namespace. */
  @NonNull
  private IExpr _parsePatternFunctionCall ()
  {
    final XPathToken aTok = _next ();
    final XdmQName aName = _resolveFunctionName (aTok);
    final String sLocal = aName.localName ();
    if (!XdmConstants.NS_FN.equals (aName.namespaceUri ()) || !PATTERN_FUNCTION_NAMES.contains (sLocal))
      throw _syntax ("The function " + aName.getEQName () + " is not allowed at the start of a pattern", aTok);
    final boolean bRoot = sLocal.equals ("root");
    _expect (ETokenKind.LPAREN, "'('");
    final ICommonsList <IExpr> aArgs = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RPAREN))
      while (true)
      {
        aArgs.add (_parsePatternArgument (bRoot));
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RPAREN, "')' or ','");
    if (bRoot && !aArgs.isEmpty () && !(aArgs.size () == 1 && aArgs.get (0) instanceof IExpr.ContextItemExpr))
      throw _syntax ("Only root() without arguments is allowed at the start of a pattern", aTok);
    _checkFunction (aName, aArgs.size (), aTok);
    return new IExpr.FunctionCallExpr (aName, aArgs);
  }

  /** PathExprP: a rooted, absolute or relative path pattern. */
  @NonNull
  private IExpr _parsePatternPath ()
  {
    final XPathToken aTok = _cur ();
    final ICommonsList <StepExpr> aSteps = new CommonsArrayList <> ();
    if (aTok.kind () == ETokenKind.SLASH)
    {
      _next ();
      // A lone "/" unless a step follows (as in XPath, "/ union /*" is a path)
      if (_startsPatternStep ())
      {
        aSteps.add (_parsePatternStep ());
        _parsePatternRelativeSteps (aSteps);
      }
      return new IExpr.PathExpr (null, true, aSteps);
    }
    if (aTok.kind () == ETokenKind.SLASHSLASH)
    {
      _next ();
      aSteps.add (_descendantOrSelfStep ());
      aSteps.add (_parsePatternStep ());
      _parsePatternRelativeSteps (aSteps);
      return new IExpr.PathExpr (null, true, aSteps);
    }
    final boolean bVarRef = aTok.kind () == ETokenKind.DOLLAR;
    final boolean bCall = aTok.isEQName () &&
                          _peekKind (1) == ETokenKind.LPAREN &&
                          !(aTok.isUnprefixedName () && KIND_TEST_NAMES.contains (aTok.text ()));
    if (bVarRef || bCall)
    {
      IExpr aStart = bVarRef ? _parseVarRef () : _parsePatternFunctionCall ();
      final ICommonsList <IExpr> aPreds = _parsePredicates ();
      if (aPreds.isNotEmpty ())
        aStart = new IExpr.FilterExpr (aStart, aPreds);
      _parsePatternRelativeSteps (aSteps);
      return new IExpr.PathExpr (aStart, false, aSteps);
    }
    aSteps.add (_parsePatternStep ());
    _parsePatternRelativeSteps (aSteps);
    return new IExpr.PathExpr (null, false, aSteps);
  }

  /** IntersectExceptExprP */
  @NonNull
  private IExpr _parsePatternIntersectExcept ()
  {
    final XPathToken aStartTok = _cur ();
    IExpr ret = _parsePatternPath ();
    final int nDepthBefore = m_nDepth;
    try
    {
      while (_cur ().isNCName ("intersect") || _cur ().isNCName ("except"))
      {
        final IExpr.SetExpr.ESetOp eOp = _next ().text ().equals ("intersect") ? IExpr.SetExpr.ESetOp.INTERSECT
                                                                                : IExpr.SetExpr.ESetOp.EXCEPT;
        _enterDepth ();
        ret = new IExpr.SetExpr (eOp, ret, _parsePatternPath ());
      }
    }
    finally
    {
      m_nDepth = nDepthBefore;
    }
    _rememberPatternSource (ret, aStartTok);
    return ret;
  }

  /** UnionExprP */
  @NonNull
  private IExpr _parsePatternUnion ()
  {
    _enterDepth ();
    final int nDepthBefore = m_nDepth;
    try
    {
      IExpr ret = _parsePatternIntersectExcept ();
      while (_is (ETokenKind.PIPE) || _cur ().isNCName ("union"))
      {
        _next ();
        _enterDepth ();
        ret = new IExpr.SetExpr (IExpr.SetExpr.ESetOp.UNION, ret, _parsePatternIntersectExcept ());
      }
      return ret;
    }
    finally
    {
      m_nDepth = nDepthBefore - 1;
    }
  }

  /**
   * @throws XPathException
   *         XPST0003 if input is left after the parsed construct.
   */
  private void _expectEOF ()
  {
    if (!_is (ETokenKind.EOF))
      throw _unexpected ("end of expression");
  }

  /** @return The parsed expression; the whole input must be consumed. */
  @NonNull
  IExpr parseExpression ()
  {
    final IExpr ret = _parseExpr ();
    _expectEOF ();
    return ret;
  }

  /** @return The parsed sequence type; the whole input must be consumed. */
  @NonNull
  SeqType parseSequenceTypeOnly ()
  {
    final SeqType ret = _parseSequenceType ();
    _expectEOF ();
    return ret;
  }

  /** @return The parsed function signature; the whole input must be consumed. */
  @NonNull
  FunctionSignature parseFunctionSignature ()
  {
    _expect (ETokenKind.LPAREN, "'('");
    final ICommonsList <SeqType> aParams = new CommonsArrayList <> ();
    if (!_is (ETokenKind.RPAREN))
      while (true)
      {
        aParams.add (_parseSequenceType ());
        if (!_is (ETokenKind.COMMA))
          break;
        _next ();
      }
    _expect (ETokenKind.RPAREN, "')' or ','");
    _expectKeyword ("as");
    final SeqType aReturn = _parseSequenceType ();
    _expectEOF ();
    return new FunctionSignature (aParams, aReturn);
  }

  /**
   * Parse an XSLT 3.0 pattern (production Pattern30). A predicate pattern is
   * returned as {@link IExpr.ContextItemExpr} or a {@link IExpr.FilterExpr} on
   * it; every other pattern consists of {@link IExpr.SetExpr} nodes over
   * {@link IExpr.PathExpr} nodes. A parenthesised pattern is a step with a
   * postfix, the "//" abbreviation a descendant-or-self::node() step.
   *
   * @param aSources
   *        Receives the source text of every union operand (identity map).
   * @return The parsed pattern; the whole input must be consumed.
   */
  @NonNull
  IExpr parsePattern (@NonNull final Map <IExpr, String> aSources)
  {
    m_aPatternSources = aSources;
    final IExpr ret;
    if (_is (ETokenKind.DOT))
    {
      final XPathToken aStartTok = _next ();
      final ICommonsList <IExpr> aPreds = _parsePredicates ();
      ret = aPreds.isEmpty () ? new IExpr.ContextItemExpr () : new IExpr.FilterExpr (new IExpr.ContextItemExpr (), aPreds);
      _expectEOF ();
      _rememberPatternSource (ret, aStartTok);
    }
    else
    {
      ret = _parsePatternUnion ();
      _expectEOF ();
    }
    return ret;
  }

  /**
   * Run a parse. If it overflows the caller's stack (possible for deeply
   * nested input on a thread with a small stack), it is repeated once on a
   * helper thread with a large stack, so the outcome - an AST or XPDY0130
   * from the depth limit - does not depend on the caller's stack size.
   *
   * @param aParse
   *        The parse to run. It must not have side effects outside the parser.
   * @param sSrc
   *        The source text for error messages.
   * @return The parse result.
   */
  @NonNull
  static <T> T runWithStackGuard (@NonNull final Supplier <T> aParse, @NonNull final String sSrc)
  {
    try
    {
      return aParse.get ();
    }
    catch (final StackOverflowError ex)
    {
      // fall through to the large stack
    }
    final Wrapper <T> aResult = new Wrapper <> ();
    final Wrapper <Throwable> aError = new Wrapper <> ();
    final Thread aThread = new Thread (null, () -> {
      try
      {
        aResult.set (aParse.get ());
      }
      catch (final StackOverflowError ex)
      {
        aError.set (createDepthError (sSrc));
      }
      catch (final Throwable t)
      {
        aError.set (t);
      }
    }, "ph-xpath-parser", LARGE_STACK_SIZE);
    aThread.setDaemon (true);
    aThread.start ();
    try
    {
      aThread.join ();
    }
    catch (final InterruptedException ex)
    {
      Thread.currentThread ().interrupt ();
      throw createDepthError (sSrc);
    }
    final Throwable aThrowable = aError.get ();
    if (aThrowable instanceof final RuntimeException aRtEx)
      throw aRtEx;
    if (aThrowable instanceof final Error aErr)
      throw aErr;
    if (aThrowable != null)
      throw new IllegalStateException (aThrowable);
    return aResult.get ();
  }

  /**
   * @return A new {@link #ERR_DEPTH} exception for a source that is nested
   *         too deeply to be parsed even on the large stack.
   */
  @NonNull
  static XPathException createDepthError (@NonNull final String sSrc)
  {
    return XPathException.of (ERR_DEPTH,
                              "Expression nested too deeply to be parsed: '" + abbreviate (sSrc) + "'");
  }
}
