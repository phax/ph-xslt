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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.ast.SeqType;
import com.helger.phoxtrot.xpath.context.StaticContext;
import com.helger.phoxtrot.xpath.eval.SequenceTypeMatcher;
import com.helger.phoxtrot.xpath.parser.XPathParser;

/**
 * A set of functions, looked up by expanded name and arity. A function is
 * registered once per arity with its signature in the notation of the F&amp;O
 * 3.1 specification, e.g.
 * <code>"(xs:string?, xs:double, xs:double) as xs:string"</code> (parameter
 * names are left out). The signature is parsed lazily with the standard
 * prefixes (xs, fn, map, array, math), and the evaluator applies the function
 * conversion rules from it.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class FunctionLibrary
{
  /** The namespace of the <code>math:</code> functions. */
  public static final String NS_MATH = "http://www.w3.org/2005/xpath-functions/math";
  /** The namespace of the <code>map:</code> functions. */
  public static final String NS_MAP = "http://www.w3.org/2005/xpath-functions/map";
  /** The namespace of the <code>array:</code> functions. */
  public static final String NS_ARRAY = "http://www.w3.org/2005/xpath-functions/array";

  /** A registered function with its parsed signature. */
  public static final class FunctionDef
  {
    private final XdmQName m_aName;
    private final String m_sSignature;
    private final IXPathFunction m_aImpl;
    private final boolean m_bFocusDependent;
    private volatile IXPathFunction m_aDynamicCallImpl;
    private volatile List <SeqType> m_aParamTypes;
    private volatile SeqType m_aReturnType;

    /**
     * @param aParamTypes
     *        The parameter types.
     * @param aReturnType
     *        The return type.
     * @return The signature in the notation of {@link #getSignature()}, e.g.
     *         <code>(xs:string?, xs:integer) as xs:string</code>.
     */
    @NonNull
    private static String _signatureText (@NonNull final List <SeqType> aParamTypes, @NonNull final SeqType aReturnType)
    {
      final StringBuilder aSB = new StringBuilder ("(");
      for (int i = 0; i < aParamTypes.size (); ++i)
      {
        if (i > 0)
          aSB.append (", ");
        aSB.append (SequenceTypeMatcher.getAsString (aParamTypes.get (i)));
      }
      return aSB.append (") as ").append (SequenceTypeMatcher.getAsString (aReturnType)).toString ();
    }

    /**
     * Create a definition from a signature text, which is parsed only when the
     * types are first needed.
     *
     * @param aName
     *        Expanded function name.
     * @param sSignature
     *        Signature as in the F&amp;O spec, without parameter names.
     * @param aImpl
     *        Implementation for static (and, by default, dynamic) calls.
     * @param bFocusDependent
     *        <code>true</code> if the implementation reads the focus.
     */
    FunctionDef (@NonNull final XdmQName aName,
                 @NonNull final String sSignature,
                 @NonNull final IXPathFunction aImpl,
                 final boolean bFocusDependent)
    {
      m_aName = aName;
      m_sSignature = sSignature;
      m_aImpl = aImpl;
      m_bFocusDependent = bFocusDependent;
    }

    /**
     * Create a definition from already parsed types. The signature text is built
     * from them for {@link #getSignature()}; it is never parsed again.
     *
     * @param aName
     *        Expanded function name.
     * @param aParamTypes
     *        The parameter types (copied).
     * @param aReturnType
     *        The return type.
     * @param aImpl
     *        Implementation.
     * @param bFocusDependent
     *        <code>true</code> if the implementation reads the focus.
     */
    FunctionDef (@NonNull final XdmQName aName,
                 @NonNull final List <SeqType> aParamTypes,
                 @NonNull final SeqType aReturnType,
                 @NonNull final IXPathFunction aImpl,
                 final boolean bFocusDependent)
    {
      this (aName, _signatureText (aParamTypes, aReturnType), aImpl, bFocusDependent);
      m_aParamTypes = new CommonsArrayList <> (aParamTypes);
      m_aReturnType = aReturnType;
    }

    /**
     * Parse the signature text into the parameter and return types on first use,
     * with a fresh {@link StaticContext} (so only the standard prefixes are
     * known). Concurrent first calls may parse twice, with the same result.
     */
    private void _parse ()
    {
      if (m_aReturnType == null)
      {
        final XPathParser.FunctionSignature aSig = XPathParser.parseFunctionSignature (m_sSignature,
                                                                                       new StaticContext ());
        m_aParamTypes = aSig.paramTypes ();
        m_aReturnType = aSig.returnType ();
      }
    }

    @NonNull
    public XdmQName getName ()
    {
      return m_aName;
    }

    /**
     * @return The number of parameters. Parses the signature on first use.
     */
    public int getArity ()
    {
      return getParamTypes ().size ();
    }

    /**
     * @return The parameter types in order. Parses the signature on first use.
     */
    @NonNull
    public List <SeqType> getParamTypes ()
    {
      _parse ();
      return m_aParamTypes;
    }

    /**
     * @return The declared return type. Parses the signature on first use.
     */
    @NonNull
    public SeqType getReturnType ()
    {
      _parse ();
      return m_aReturnType;
    }

    @NonNull
    public IXPathFunction getImpl ()
    {
      return m_aImpl;
    }

    /**
     * @return The implementation used when the function is called through a
     *         function item (dynamic function call). Normally the same as
     *         {@link #getImpl()}; XSLT functions such as current-group() whose
     *         context is not available in a dynamic call register their own.
     */
    @NonNull
    public IXPathFunction getDynamicCallImpl ()
    {
      final IXPathFunction ret = m_aDynamicCallImpl;
      return ret != null ? ret : m_aImpl;
    }

    /**
     * @return <code>true</code> if the function reads the focus (e.g.
     *         <code>position()</code>, <code>name()</code> without argument).
     *         A named function reference to such a function captures the
     *         focus at the time the reference is evaluated.
     */
    public boolean isFocusDependent ()
    {
      return m_bFocusDependent;
    }

    @NonNull
    public String getSignature ()
    {
      return m_sSignature;
    }
  }

  private static final SimpleReadWriteLock DEFAULT_LOCK = new SimpleReadWriteLock ();
  private static volatile FunctionLibrary s_aDefault;

  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  private final ICommonsMap <String, FunctionDef> m_aMap = new CommonsHashMap <> ();
  // Variadic functions (fn:concat): name key to the registration data
  private final ICommonsMap <String, Object []> m_aVariadic = new CommonsHashMap <> ();
  // A library created by createChild: functions not found here are looked up
  // in the parent, unless hidden here
  private final FunctionLibrary m_aParent;
  private final ICommonsSet <String> m_aHidden = new CommonsHashSet <> ();
  // What fn:function-lookup finds instead of the static binding; a null value
  // means it finds nothing
  private final ICommonsMap <String, FunctionDef> m_aLookup = new CommonsHashMap <> ();
  // Incremented under the write lock by every change of the registrations of
  // this library (not by the on-demand functions of a variadic registration,
  // which do not change any lookup result)
  private volatile long m_nModCount;
  // Set by freeze (and for the shared default library): every registration, hiding or lookup change is refused.
  // Only set under the write lock, after the parent was frozen
  private volatile boolean m_bFrozen;

  /**
   * Create an empty library without parent. Use {@link #createDefault()} for
   * one with the built-in functions.
   */
  public FunctionLibrary ()
  {
    this (null);
  }

  /**
   * @param aParent
   *        The library whose functions this one sees, or <code>null</code> for
   *        a root library. See {@link #createChild()}.
   */
  private FunctionLibrary (@Nullable final FunctionLibrary aParent)
  {
    m_aParent = aParent;
  }

  /**
   * Refuse a change of the registrations of a frozen library (see {@link #freeze()}), such as the shared default
   * library (see {@link #getDefault()}) or the library of a compiled stylesheet. Called under the write lock.
   *
   * @throws IllegalStateException
   *         if this library is frozen.
   */
  private void _checkWritable ()
  {
    if (m_bFrozen)
      throw new IllegalStateException ("The function library is frozen; register into a child library (createChild) instead");
  }

  /**
   * @param sNamespaceUri
   *        Function namespace.
   * @param sLocalName
   *        Local name.
   * @param nArity
   *        Arity.
   * @return The map key <code>{uri}local#arity</code>.
   */
  @NonNull
  private static String _key (@NonNull final String sNamespaceUri, @NonNull final String sLocalName, final int nArity)
  {
    return "{" + sNamespaceUri + "}" + sLocalName + "#" + nArity;
  }

  /**
   * Count the parameters of a signature text: the commas at nesting depth zero
   * inside the outer parentheses, plus one if the parentheses contain anything
   * other than whitespace. Nested parentheses such as in <code>map(*)</code>
   * or <code>function(item()) as item()</code> are skipped.
   *
   * @param sSignature
   *        Signature as in the F&amp;O spec, starting with the parameter list.
   * @return The arity.
   */
  private static int _countParams (@NonNull final String sSignature)
  {
    // Count top-level commas inside the outer parameter parentheses
    final int nOpen = sSignature.indexOf ('(');
    int nDepth = 0;
    int nCount = 0;
    boolean bAny = false;
    for (int i = nOpen + 1; i < sSignature.length (); ++i)
    {
      final char c = sSignature.charAt (i);
      if (c == '(')
        nDepth++;
      else
        if (c == ')')
        {
          if (nDepth == 0)
            break;
          nDepth--;
        }
        else
          if (c == ',' && nDepth == 0)
            nCount++;
          else
            if (!Character.isWhitespace (c))
              bAny = true;
    }
    return bAny ? nCount + 1 : 0;
  }

  /**
   * Register a function.
   *
   * @param sNamespaceUri
   *        Function namespace, e.g. {@link com.helger.phoxtrot.xdm.XdmConstants#NS_FN}.
   * @param sLocalName
   *        Local name.
   * @param sSignature
   *        Signature as in the F&amp;O spec, without parameter names.
   * @param aImpl
   *        Implementation.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary register (@NonNull final String sNamespaceUri,
                                   @NonNull final String sLocalName,
                                   @NonNull final String sSignature,
                                   @NonNull final IXPathFunction aImpl)
  {
    return register (sNamespaceUri, sLocalName, sSignature, aImpl, false);
  }

  /**
   * Register a function that depends on the focus.
   *
   * @param sNamespaceUri
   *        Function namespace.
   * @param sLocalName
   *        Local name.
   * @param sSignature
   *        Signature as in the F&amp;O spec, without parameter names.
   * @param aImpl
   *        Implementation.
   * @param bFocusDependent
   *        <code>true</code> if the implementation reads the focus.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary register (@NonNull final String sNamespaceUri,
                                   @NonNull final String sLocalName,
                                   @NonNull final String sSignature,
                                   @NonNull final IXPathFunction aImpl,
                                   final boolean bFocusDependent)
  {
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    ValueEnforcer.notEmpty (sLocalName, "LocalName");
    ValueEnforcer.notEmpty (sSignature, "Signature");
    ValueEnforcer.notNull (aImpl, "Impl");
    final int nArity = _countParams (sSignature);
    final XdmQName aName = new XdmQName (sNamespaceUri, sLocalName);
    final FunctionDef aDef = new FunctionDef (aName, sSignature, aImpl, bFocusDependent);
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aMap.put (_key (sNamespaceUri, sLocalName, nArity), aDef);
      m_nModCount++;
    });
    return this;
  }

  /**
   * Register a function with a separate implementation for dynamic function
   * calls (calls through a function item, e.g. <code>current-group#0</code>).
   *
   * @param sNamespaceUri
   *        Function namespace.
   * @param sLocalName
   *        Local name.
   * @param sSignature
   *        Signature as in the F&amp;O spec, without parameter names.
   * @param aImpl
   *        Implementation for static calls.
   * @param bFocusDependent
   *        <code>true</code> if the implementation reads the focus.
   * @param aDynamicCallImpl
   *        Implementation for dynamic calls.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary register (@NonNull final String sNamespaceUri,
                                   @NonNull final String sLocalName,
                                   @NonNull final String sSignature,
                                   @NonNull final IXPathFunction aImpl,
                                   final boolean bFocusDependent,
                                   @NonNull final IXPathFunction aDynamicCallImpl)
  {
    ValueEnforcer.notNull (aDynamicCallImpl, "DynamicCallImpl");
    register (sNamespaceUri, sLocalName, sSignature, aImpl, bFocusDependent);
    final int nArity = _countParams (sSignature);
    m_aRWLock.readLockedGet ( () -> m_aMap.get (_key (sNamespaceUri, sLocalName, nArity))).m_aDynamicCallImpl = aDynamicCallImpl;
    return this;
  }

  /**
   * Register a function whose signature is already parsed, e.g. an XSLT
   * stylesheet function whose types use the namespaces of the stylesheet.
   * Replaces a function with the same name and arity.
   *
   * @param aName
   *        Expanded function name.
   * @param aParamTypes
   *        The parameter types; their number is the arity.
   * @param aReturnType
   *        The return type.
   * @param aImpl
   *        Implementation. The evaluator converts the arguments to the
   *        parameter types before calling it.
   * @param bFocusDependent
   *        <code>true</code> if the implementation reads the focus.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary registerParsed (@NonNull final XdmQName aName,
                                         @NonNull final List <SeqType> aParamTypes,
                                         @NonNull final SeqType aReturnType,
                                         @NonNull final IXPathFunction aImpl,
                                         final boolean bFocusDependent)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aParamTypes, "ParamTypes");
    ValueEnforcer.notNull (aReturnType, "ReturnType");
    ValueEnforcer.notNull (aImpl, "Impl");
    final FunctionDef aDef = new FunctionDef (aName, aParamTypes, aReturnType, aImpl, bFocusDependent);
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aMap.put (_key (aName.namespaceUri (), aName.localName (), aParamTypes.size ()), aDef);
      m_nModCount++;
    });
    return this;
  }

  /**
   * Register a function that accepts any number of arguments from a minimum
   * on, all of the same type (only <code>fn:concat</code> in F&amp;O 3.1). A
   * {@link FunctionDef} per requested arity is created on demand.
   *
   * @param sNamespaceUri
   *        Function namespace.
   * @param sLocalName
   *        Local name.
   * @param nMinArity
   *        Minimum number of arguments.
   * @param sParamType
   *        The SequenceType of every parameter, e.g.
   *        <code>xs:anyAtomicType?</code>.
   * @param sReturnType
   *        The return SequenceType.
   * @param aImpl
   *        Implementation.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary registerVariadic (@NonNull final String sNamespaceUri,
                                           @NonNull final String sLocalName,
                                           final int nMinArity,
                                           @NonNull final String sParamType,
                                           @NonNull final String sReturnType,
                                           @NonNull final IXPathFunction aImpl)
  {
    ValueEnforcer.notNull (sNamespaceUri, "NamespaceUri");
    ValueEnforcer.notEmpty (sLocalName, "LocalName");
    ValueEnforcer.isGE0 (nMinArity, "MinArity");
    ValueEnforcer.notEmpty (sParamType, "ParamType");
    ValueEnforcer.notEmpty (sReturnType, "ReturnType");
    ValueEnforcer.notNull (aImpl, "Impl");
    final Object [] aData = new Object [] { Integer.valueOf (nMinArity), sParamType, sReturnType, aImpl };
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aVariadic.put ("{" + sNamespaceUri + "}" + sLocalName, aData);
      m_nModCount++;
    });
    return this;
  }

  /**
   * @param sVariadicKey
   *        The name key of a variadic registration.
   * @return <code>true</code> if this library itself (not a parent) has a
   *         variadic registration of that name.
   */
  private boolean _hasOwnVariadic (@NonNull final String sVariadicKey)
  {
    return m_aRWLock.readLockedBoolean ( () -> m_aVariadic.containsKey (sVariadicKey));
  }

  /**
   * The function of a variadic registration of this library itself (not of a
   * parent) for one arity. It is created on the first request and kept with
   * the other functions of this library.
   *
   * @param aName
   *        Expanded function name.
   * @param nArity
   *        Number of arguments.
   * @param sKey
   *        The key of name and arity.
   * @return <code>null</code> if there is no such registration or the arity
   *         is below its minimum.
   */
  @Nullable
  private FunctionDef _getOwnVariadic (@NonNull final XdmQName aName, final int nArity, @NonNull final String sKey)
  {
    final Object [] aVar = m_aRWLock.readLockedGet ( () -> m_aVariadic.get ("{" +
                                                                            aName.namespaceUri () +
                                                                            "}" +
                                                                            aName.localName ()));
    if (aVar == null || nArity < ((Integer) aVar[0]).intValue ())
      return null;
    final StringBuilder aSB = new StringBuilder ("(");
    for (int i = 0; i < nArity; ++i)
    {
      if (i > 0)
        aSB.append (", ");
      aSB.append ((String) aVar[1]);
    }
    aSB.append (") as ").append ((String) aVar[2]);
    final FunctionDef aNew = new FunctionDef (aName, aSB.toString (), (IXPathFunction) aVar[3], false);
    return m_aRWLock.writeLockedGet ( () -> {
      // Another thread may have created it in the meantime
      final FunctionDef aExisting = m_aMap.get (sKey);
      if (aExisting != null)
        return aExisting;
      m_aMap.put (sKey, aNew);
      return aNew;
    });
  }

  /**
   * A stamp of the registrations this library and its parent chain see: the
   * sum of the change counters of the libraries in the chain. Every
   * registration, hiding or lookup change in this library or a parent
   * increases it; the on-demand creation of a function of a variadic
   * registration does not, since it changes no lookup result. Two equal stamps
   * of the same library therefore mean that
   * {@link #getFunction(XdmQName, int)} gives the same result for every name
   * and arity. Read the stamp before a lookup whose result is cached with it,
   * so that a change in between makes the cached entry stale instead of
   * hiding the change.
   *
   * @return The stamp.
   */
  long getModificationStamp ()
  {
    long ret = 0;
    for (FunctionLibrary aLib = this; aLib != null; aLib = aLib.m_aParent)
      ret += aLib.m_nModCount;
    return ret;
  }

  /**
   * Find a function: one registered in this library; otherwise, unless name
   * and arity are hidden here, one of a variadic registration of this library
   * or else the one of the parent library.
   *
   * @param aName
   *        Expanded function name.
   * @param nArity
   *        Number of arguments.
   * @return <code>null</code> if there is no such function.
   */
  @Nullable
  public FunctionDef getFunction (@NonNull final XdmQName aName, final int nArity)
  {
    final String sKey = _key (aName.namespaceUri (), aName.localName (), nArity);
    final FunctionDef aOwn = m_aRWLock.readLockedGet ( () -> m_aMap.get (sKey));
    if (aOwn != null)
      return aOwn;
    if (m_aRWLock.readLockedBoolean ( () -> m_aHidden.contains (sKey)))
      return null;
    final FunctionDef aVariadic = _getOwnVariadic (aName, nArity, sKey);
    if (aVariadic != null)
      return aVariadic;
    // The parent is called without holding this library's lock
    return m_aParent != null ? m_aParent.getFunction (aName, nArity) : null;
  }

  /**
   * The function that <code>fn:function-lookup</code> finds. It is the one of
   * {@link #getFunction(XdmQName, int)}, unless this library or a parent
   * changed it with {@link #registerForLookup(XdmQName, List, SeqType, IXPathFunction, boolean)}
   * or {@link #hideFromLookup(XdmQName, int)}.
   *
   * @param aName
   *        Expanded function name.
   * @param nArity
   *        Number of arguments.
   * @return <code>null</code> if there is no such function.
   */
  @Nullable
  public FunctionDef getFunctionForLookup (@NonNull final XdmQName aName, final int nArity)
  {
    final String sKey = _key (aName.namespaceUri (), aName.localName (), nArity);
    final Object [] aOwn = m_aRWLock.readLockedGet ( () -> {
      if (m_aLookup.containsKey (sKey))
        return new Object [] { m_aLookup.get (sKey) };
      if (m_aMap.containsKey (sKey))
        return new Object [] { m_aMap.get (sKey) };
      return m_aHidden.contains (sKey) ? new Object [] { null } : null;
    });
    if (aOwn != null)
      return (FunctionDef) aOwn[0];
    final FunctionDef aVariadic = _getOwnVariadic (aName, nArity, sKey);
    if (aVariadic != null)
      return aVariadic;
    // The parent is called without holding this library's lock
    return m_aParent != null ? m_aParent.getFunctionForLookup (aName, nArity) : null;
  }

  /**
   * @param aName
   *        Expanded function name.
   * @return <code>true</code> if a function of that name exists with any arity:
   *         registered in this library, from a variadic registration of this
   *         library or any parent (which covers more arities than can be
   *         hidden), or a function of the parent chain that is not hidden here.
   */
  public boolean containsName (@NonNull final XdmQName aName)
  {
    final String sPrefix = "{" + aName.namespaceUri () + "}" + aName.localName () + "#";
    final String sVariadicKey = "{" + aName.namespaceUri () + "}" + aName.localName ();
    if (m_aRWLock.readLockedBoolean ( () -> {
      if (m_aVariadic.containsKey (sVariadicKey))
        return true;
      for (final String sKey : m_aMap.keySet ())
        if (sKey.startsWith (sPrefix))
          return true;
      return false;
    }))
      return true;
    // The parents are called without holding this library's lock
    for (FunctionLibrary aLib = m_aParent; aLib != null; aLib = aLib.m_aParent)
      if (aLib._hasOwnVariadic (sVariadicKey))
        return true;
    if (m_aParent != null)
      for (final FunctionDef aDef : m_aParent.getAllFunctions ())
        if (aDef.getName ().equals (aName))
        {
          final String sKey = _key (aName.namespaceUri (), aName.localName (), aDef.getArity ());
          if (!m_aRWLock.readLockedBoolean ( () -> m_aHidden.contains (sKey)))
            return true;
        }
    return false;
  }

  /**
   * @return All functions registered in this library and, for a child library,
   *         those of the parent chain that are neither redefined (same name
   *         and arity) nor hidden here. The functions of a variadic
   *         registration are only included for the arities that
   *         {@link #getFunction(XdmQName, int)} has already created. Never
   *         <code>null</code>; a new list.
   */
  @NonNull
  public ICommonsList <FunctionDef> getAllFunctions ()
  {
    final ICommonsList <FunctionDef> ret = m_aRWLock.readLockedGet ( () -> new CommonsArrayList <> (m_aMap.values ()));
    if (m_aParent != null)
    {
      // The parent is called without holding this library's lock
      final ICommonsList <FunctionDef> aParentDefs = m_aParent.getAllFunctions ();
      m_aRWLock.readLocked ( () -> {
        for (final FunctionDef aDef : aParentDefs)
        {
          final String sKey = _key (aDef.getName ().namespaceUri (), aDef.getName ().localName (), aDef.getArity ());
          if (!m_aMap.containsKey (sKey) && !m_aHidden.contains (sKey))
            ret.add (aDef);
        }
      });
    }
    return ret;
  }

  /**
   * Freeze this library: from now on every registration, hiding or lookup change throws an
   * {@link IllegalStateException}, so that {@link #getFunction(XdmQName, int)} gives the same result forever (only the
   * functions of a variadic registration are still created on demand, which changes no lookup result). A call site
   * can then keep a resolved function without reading the modification stamp on each call (see
   * {@link FunctionCallSite}). The parent must be frozen first, so that a frozen library always has a frozen parent
   * chain. Freezing a frozen library does nothing. A frozen library can still get child libraries
   * ({@link #createChild()}), which are writable.
   *
   * @return this for chaining
   * @throws IllegalStateException
   *         if the parent library is not frozen.
   */
  @NonNull
  public FunctionLibrary freeze ()
  {
    if (m_aParent != null && !m_aParent.isFrozen ())
      throw new IllegalStateException ("The parent function library must be frozen first");
    m_aRWLock.writeLocked ( () -> {
      m_bFrozen = true;
    });
    return this;
  }

  /**
   * @return <code>true</code> if this library is frozen (see {@link #freeze()}); its parent chain is then frozen too.
   */
  public boolean isFrozen ()
  {
    return m_bFrozen;
  }

  /**
   * Create a library that sees all functions of this one, except those hidden
   * in it with {@link #hide(XdmQName, int)}; functions registered in it take
   * precedence. Used for the function scope of an XSLT package.
   *
   * @return A new child library.
   */
  @NonNull
  public FunctionLibrary createChild ()
  {
    return new FunctionLibrary (this);
  }

  /**
   * Make a function of the parent library invisible in this child library.
   *
   * @param aName
   *        Expanded function name.
   * @param nArity
   *        Arity.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary hide (@NonNull final XdmQName aName, final int nArity)
  {
    ValueEnforcer.notNull (aName, "Name");
    final String sKey = _key (aName.namespaceUri (), aName.localName (), nArity);
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aHidden.add (sKey);
      m_aMap.remove (sKey);
      m_nModCount++;
    });
    return this;
  }

  /**
   * Let <code>fn:function-lookup</code> find another function than a static
   * call does, in this library and its children. In XSLT, a used package looks
   * up its own function where a static call reaches the override.
   *
   * @param aName
   *        Expanded function name.
   * @param aParamTypes
   *        The parameter types; their number is the arity.
   * @param aReturnType
   *        The return type.
   * @param aImpl
   *        Implementation.
   * @param bFocusDependent
   *        <code>true</code> if the implementation reads the focus.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary registerForLookup (@NonNull final XdmQName aName,
                                            @NonNull final List <SeqType> aParamTypes,
                                            @NonNull final SeqType aReturnType,
                                            @NonNull final IXPathFunction aImpl,
                                            final boolean bFocusDependent)
  {
    ValueEnforcer.notNull (aName, "Name");
    ValueEnforcer.notNull (aParamTypes, "ParamTypes");
    ValueEnforcer.notNull (aReturnType, "ReturnType");
    ValueEnforcer.notNull (aImpl, "Impl");
    final FunctionDef aDef = new FunctionDef (aName, aParamTypes, aReturnType, aImpl, bFocusDependent);
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aLookup.put (_key (aName.namespaceUri (), aName.localName (), aParamTypes.size ()), aDef);
      m_nModCount++;
    });
    return this;
  }

  /**
   * Make a function invisible to <code>fn:function-lookup</code> in this
   * library and its children; static calls still reach it.
   *
   * @param aName
   *        Expanded function name.
   * @param nArity
   *        Arity.
   * @return this for chaining
   */
  @NonNull
  public FunctionLibrary hideFromLookup (@NonNull final XdmQName aName, final int nArity)
  {
    ValueEnforcer.notNull (aName, "Name");
    m_aRWLock.writeLocked ( () -> {
      _checkWritable ();
      m_aLookup.put (_key (aName.namespaceUri (), aName.localName (), nArity), null);
      m_nModCount++;
    });
    return this;
  }

  /**
   * Build a new library with all built-in functions of F&amp;O 3.1. This registers several hundred functions and
   * parses no signature yet; to see the built-in functions, prefer a child of the shared {@link #getDefault()}.
   *
   * @return A new library with all built-in functions of F&amp;O 3.1, writable.
   */
  @NonNull
  public static FunctionLibrary createDefault ()
  {
    final FunctionLibrary ret = new FunctionLibrary ();
    FnAccessor.register (ret);
    FnContext.register (ret);
    FnBoolean.register (ret);
    FnString.register (ret);
    FnRegex.register (ret);
    FnNumeric.register (ret);
    FnMath.register (ret);
    FnSequence.register (ret);
    FnNode.register (ret);
    FnQName.register (ret);
    FnUri.register (ret);
    FnDateTime.register (ret);
    FnFormat.register (ret);
    FnHigherOrder.register (ret);
    FnMap.register (ret);
    FnArray.register (ret);
    FnJson.register (ret);
    FnResource.register (ret);
    FnTransform.register (ret);
    FnError.register (ret);
    FnConstructor.register (ret);
    return ret;
  }

  /**
   * The library of the built-in functions shared by all static contexts and compiled stylesheets, built once with
   * {@link #createDefault()} on the first call. It is frozen (see {@link #freeze()}): every registration, hiding or
   * lookup change throws an {@link IllegalStateException}, so its modification stamp never changes; register into a {@link #createChild()}
   * instead. Only the functions of a variadic registration (<code>fn:concat</code>) are still created on demand,
   * under its lock.
   *
   * @return The shared default library.
   */
  @NonNull
  public static FunctionLibrary getDefault ()
  {
    final FunctionLibrary ret = s_aDefault;
    if (ret != null)
      return ret;
    return DEFAULT_LOCK.writeLockedGet ( () -> {
      FunctionLibrary aLib = s_aDefault;
      if (aLib == null)
      {
        aLib = createDefault ().freeze ();
        s_aDefault = aLib;
      }
      return aLib;
    });
  }
}
