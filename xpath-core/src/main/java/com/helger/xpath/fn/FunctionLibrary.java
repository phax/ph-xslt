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

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.xdm.XdmQName;

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
  public static final String NS_MATH = "http://www.w3.org/2005/xpath-functions/math";
  public static final String NS_MAP = "http://www.w3.org/2005/xpath-functions/map";
  public static final String NS_ARRAY = "http://www.w3.org/2005/xpath-functions/array";

  /** A registered function with its parsed signature. */
  public static final class FunctionDef
  {
    private final XdmQName m_aName;
    private final String m_sSignature;
    private final IXPathFunction m_aImpl;
    private final boolean m_bFocusDependent;
    private volatile List <SeqType> m_aParamTypes;
    private volatile SeqType m_aReturnType;

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

    public int getArity ()
    {
      return getParamTypes ().size ();
    }

    @NonNull
    public List <SeqType> getParamTypes ()
    {
      _parse ();
      return m_aParamTypes;
    }

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

  private static volatile FunctionLibrary s_aDefault;

  private final ICommonsMap <String, FunctionDef> m_aMap = new CommonsHashMap <> ();
  // Variadic functions (fn:concat): name key to the registration data
  private final ICommonsMap <String, Object []> m_aVariadic = new CommonsHashMap <> ();

  @NonNull
  private static String _key (@NonNull final String sNamespaceUri, @NonNull final String sLocalName, final int nArity)
  {
    return "{" + sNamespaceUri + "}" + sLocalName + "#" + nArity;
  }

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
   *        Function namespace, e.g. {@link com.helger.xpath.xdm.XdmConstants#NS_FN}.
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
    synchronized (m_aMap)
    {
      m_aMap.put (_key (sNamespaceUri, sLocalName, nArity), new FunctionDef (aName, sSignature, aImpl, bFocusDependent));
    }
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
    synchronized (m_aMap)
    {
      m_aVariadic.put ("{" + sNamespaceUri + "}" + sLocalName,
                       new Object [] { Integer.valueOf (nMinArity), sParamType, sReturnType, aImpl });
    }
    return this;
  }

  /**
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
    synchronized (m_aMap)
    {
      FunctionDef ret = m_aMap.get (sKey);
      if (ret == null)
      {
        final Object [] aVar = m_aVariadic.get ("{" + aName.namespaceUri () + "}" + aName.localName ());
        if (aVar != null && nArity >= ((Integer) aVar[0]).intValue ())
        {
          final StringBuilder aSB = new StringBuilder ("(");
          for (int i = 0; i < nArity; ++i)
          {
            if (i > 0)
              aSB.append (", ");
            aSB.append ((String) aVar[1]);
          }
          aSB.append (") as ").append ((String) aVar[2]);
          ret = new FunctionDef (aName, aSB.toString (), (IXPathFunction) aVar[3], false);
          m_aMap.put (sKey, ret);
        }
      }
      return ret;
    }
  }

  /**
   * @param aName
   *        Expanded function name.
   * @return <code>true</code> if a function of that name exists with any arity.
   */
  public boolean containsName (@NonNull final XdmQName aName)
  {
    final String sPrefix = "{" + aName.namespaceUri () + "}" + aName.localName () + "#";
    synchronized (m_aMap)
    {
      if (m_aVariadic.containsKey ("{" + aName.namespaceUri () + "}" + aName.localName ()))
        return true;
      for (final String sKey : m_aMap.keySet ())
        if (sKey.startsWith (sPrefix))
          return true;
    }
    return false;
  }

  @NonNull
  public ICommonsList <FunctionDef> getAllFunctions ()
  {
    synchronized (m_aMap)
    {
      return new CommonsArrayList <> (m_aMap.values ());
    }
  }

  /**
   * @return A new library with all built-in functions of F&amp;O 3.1.
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
    FnError.register (ret);
    FnConstructor.register (ret);
    return ret;
  }

  /** @return The shared default library. Do not register into it. */
  @NonNull
  public static FunctionLibrary getDefault ()
  {
    FunctionLibrary ret = s_aDefault;
    if (ret == null)
      synchronized (FunctionLibrary.class)
      {
        ret = s_aDefault;
        if (ret == null)
          s_aDefault = ret = createDefault ();
      }
    return ret;
  }
}
