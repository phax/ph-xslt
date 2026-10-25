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
import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.EAtomicType;
import com.helger.xpath.value.IXdmFunction;
import com.helger.xpath.value.XdmArray;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmItem;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * The adaptive output method (Serialization 3.1 section 10). Nested maps and
 * arrays are processed with an explicit work stack.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class AdaptiveWriter
{
  private record Member (XdmSequence value)
  {}

  private final SerializationParams m_aParams;
  private final OutputCharset m_aCharset;
  private final StringBuilder m_aSB;

  AdaptiveWriter (@NonNull final SerializationParams aParams,
                  @NonNull final OutputCharset aCharset,
                  @NonNull final StringBuilder aSB)
  {
    m_aParams = aParams;
    m_aCharset = aCharset;
    m_aSB = aSB;
  }

  /**
   * Format an xs:double like
   * <code>format-number($d, '0.0##########################e0')</code>.
   *
   * @param d
   *        The value.
   * @return The formatted value.
   */
  @NonNull
  static String formatDouble (final double d)
  {
    if (Double.isNaN (d))
      return "NaN";
    if (Double.isInfinite (d))
      return d > 0 ? "INF" : "-INF";
    if (d == 0)
      return (1 / d < 0 ? "-" : "") + "0.0e0";
    final BigDecimal aBD = new BigDecimal (Double.toString (d)).stripTrailingZeros ();
    final String sDigits = aBD.unscaledValue ().abs ().toString ();
    final int nExp = sDigits.length () - 1 - aBD.scale ();
    final StringBuilder aSB = new StringBuilder ();
    if (aBD.signum () < 0)
      aSB.append ('-');
    aSB.append (sDigits.charAt (0)).append ('.');
    aSB.append (sDigits.length () > 1 ? sDigits.substring (1) : "0");
    aSB.append ('e').append (nExp);
    return aSB.toString ();
  }

  @NonNull
  private static String _getConventionalPrefix (@NonNull final String sNamespaceUri)
  {
    switch (sNamespaceUri)
    {
      case XdmConstants.NS_FN:
        return "fn";
      case XdmConstants.NS_XS:
        return "xs";
      case FunctionLibrary.NS_MATH:
        return "math";
      case FunctionLibrary.NS_MAP:
        return "map";
      case FunctionLibrary.NS_ARRAY:
        return "array";
      default:
        return "";
    }
  }

  private void _appendQuoted (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    aSB.append ('"');
    m_aCharset.mapAndEscape (aSB, s, (aTarget, sRun) -> aTarget.append (sRun.replace ("\"", "\"\"")));
    aSB.append ('"');
  }

  private void _appendAttrValue (@NonNull final StringBuilder aSB, @NonNull final String s)
  {
    aSB.append ('"');
    m_aCharset.mapAndEscape (aSB, s, (aTarget, sRun) -> {
      for (int i = 0; i < sRun.length (); ++i)
      {
        final char c = sRun.charAt (i);
        switch (c)
        {
          case '&':
            aTarget.append ("&amp;");
            break;
          case '<':
            aTarget.append ("&lt;");
            break;
          case '"':
            aTarget.append ("&quot;");
            break;
          default:
            aTarget.append (c);
        }
      }
    });
    aSB.append ('"');
  }

  private void _writeNode (@NonNull final XdmNode aNode)
  {
    if (aNode.getKind () == ENodeKind.ATTRIBUTE)
    {
      final XdmQName aName = aNode.getName ();
      if (aName.hasNamespace () && !aName.hasPrefix ())
        m_aSB.append (aName.getEQName ());
      else
        m_aSB.append (aName.getLexicalName ());
      m_aSB.append ('=');
      _appendAttrValue (m_aSB, aNode.getValue ());
      return;
    }
    if (aNode.getKind () == ENodeKind.NAMESPACE)
    {
      m_aSB.append (aNode.getName () == null ? "xmlns" : "xmlns:" + aNode.getName ().localName ()).append ('=');
      _appendAttrValue (m_aSB, aNode.getValue ());
      return;
    }
    final SerializationParams aNodeParams = new SerializationParams (m_aParams);
    aNodeParams.setMethod (SerializationParams.METHOD_XML);
    aNodeParams.setItemSeparator (null);
    aNodeParams.setByteOrderMark (false);
    m_aSB.append (XdmSerializer.serialize (XdmSequence.of (aNode), aNodeParams));
  }

  private void _writeAtomic (@NonNull final XdmAtomic aAtomic)
  {
    final EAtomicType eType = aAtomic.getType ();
    if (eType == EAtomicType.BOOLEAN)
      m_aSB.append (aAtomic.getBoolean () ? "true()" : "false()");
    else
      if (eType.isStringFamily () || eType == EAtomicType.UNTYPED_ATOMIC || eType.isDerivedFrom (EAtomicType.ANY_URI))
        _appendQuoted (m_aSB, aAtomic.getString ());
      else
        if (eType == EAtomicType.DOUBLE)
          m_aSB.append (formatDouble (aAtomic.getAsDouble ()));
        else
          if (eType.isDerivedFrom (EAtomicType.DECIMAL))
            m_aSB.append (aAtomic.getLexical ());
          else
            if (eType == EAtomicType.QNAME || eType == EAtomicType.NOTATION)
            {
              final XdmQName aName = (XdmQName) aAtomic.getValue ();
              m_aSB.append ("Q{").append (aName.namespaceUri ()).append ('}').append (aName.localName ());
            }
            else
            {
              // Serialization 3.1 §10: "xs:TYPE" names the primitive type
              EAtomicType ePrimitive = eType;
              while (ePrimitive.getParent () != null && ePrimitive.getParent () != EAtomicType.ANY_ATOMIC_TYPE)
                ePrimitive = ePrimitive.getParent ();
              m_aSB.append (ePrimitive.getQualifiedName ()).append ('(');
              _appendQuoted (m_aSB, aAtomic.getLexical ());
              m_aSB.append (')');
            }
  }

  private void _writeFunction (@NonNull final IXdmFunction aFunc)
  {
    final XdmQName aName = aFunc.getName ();
    if (aName == null)
      m_aSB.append ("(anonymous-function)");
    else
    {
      final String sPrefix = _getConventionalPrefix (aName.namespaceUri ());
      if (!sPrefix.isEmpty ())
        m_aSB.append (sPrefix).append (':').append (aName.localName ());
      else
        if (aName.hasNamespace ())
          m_aSB.append (aName.getEQName ());
        else
          m_aSB.append (aName.localName ());
    }
    m_aSB.append ('#').append (aFunc.getArity ());
  }

  /**
   * Write the items of a sequence separated by the item separator (default a
   * newline).
   *
   * @param aSeq
   *        The sequence.
   */
  void write (@NonNull final XdmSequence aSeq)
  {
    final String sSep = m_aParams.getItemSeparator () != null ? m_aParams.getItemSeparator () : "\n";
    final ArrayDeque <Object> aStack = new ArrayDeque <> ();
    for (int i = aSeq.size () - 1; i >= 0; --i)
    {
      aStack.push (aSeq.get (i));
      if (i > 0)
        aStack.push (sSep);
    }
    while (!aStack.isEmpty ())
    {
      final Object aTop = aStack.pop ();
      if (aTop instanceof final String sLiteral)
        m_aSB.append (sLiteral);
      else
        if (aTop instanceof final Member aMember)
        {
          final XdmSequence aValue = aMember.value ();
          if (aValue.isSingleton ())
            aStack.push (aValue.get (0));
          else
          {
            aStack.push (")");
            for (int i = aValue.size () - 1; i >= 0; --i)
            {
              aStack.push (aValue.get (i));
              if (i > 0)
                aStack.push (",");
            }
            aStack.push ("(");
          }
        }
        else
        {
          final IXdmItem aItem = (IXdmItem) aTop;
          if (aItem instanceof final XdmMap aMap)
          {
            m_aSB.append ("map{");
            aStack.push ("}");
            final var aEntries = aMap.getAllEntries ();
            for (int i = aEntries.size () - 1; i >= 0; --i)
            {
              final XdmMap.Entry aEntry = aEntries.get (i);
              aStack.push (new Member (aEntry.value ()));
              aStack.push (":");
              aStack.push (aEntry.key ());
              if (i > 0)
                aStack.push (",");
            }
          }
          else
            if (aItem instanceof final XdmArray aArray)
            {
              m_aSB.append ('[');
              aStack.push ("]");
              for (int i = aArray.size () - 1; i >= 0; --i)
              {
                aStack.push (new Member (aArray.get (i)));
                if (i > 0)
                  aStack.push (",");
              }
            }
            else
              if (aItem instanceof final XdmNode aNode)
                _writeNode (aNode);
              else
                if (aItem instanceof final XdmAtomic aAtomic)
                  _writeAtomic (aAtomic);
                else
                  if (aItem instanceof final IXdmFunction aFunc)
                    _writeFunction (aFunc);
        }
    }
  }
}
