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
package com.helger.phoxtrot.xslt.jaxp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;

import javax.xml.namespace.QName;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.IXdmItem;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XmlNames;
import com.helger.phoxtrot.xdm.bridge.DomToXdm;
import com.helger.phoxtrot.xpath.value.XdmAtomic;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * Converts the Java values of the JAXP API into XDM: stylesheet parameter
 * values and names.
 *
 * @author Philip Helger
 */
@Immutable
public final class JaxpValueConverter
{
  @PresentForCodeCoverage
  private static final JaxpValueConverter INSTANCE = new JaxpValueConverter ();

  private JaxpValueConverter ()
  {}

  /**
   * Convert a value without flattening: the item it gives.
   *
   * @throws IllegalArgumentException
   *         for an unsupported type
   */
  @NonNull
  private static IXdmItem _toItem (@NonNull final Object aValue)
  {
    if (aValue instanceof final String sValue)
      return XdmAtomic.untyped (sValue);
    if (aValue instanceof final Boolean aBool)
      return XdmAtomic.bool (aBool.booleanValue ());
    if (aValue instanceof final BigInteger aBI)
      return XdmAtomic.integer (aBI);
    if (aValue instanceof final BigDecimal aBD)
      return XdmAtomic.decimal (aBD);
    if (aValue instanceof final Double aDouble)
      return XdmAtomic.dbl (aDouble.doubleValue ());
    if (aValue instanceof final Float aFloat)
      return XdmAtomic.flt (aFloat.floatValue ());
    if (aValue instanceof final Number aNumber)
      return XdmAtomic.integer (aNumber.longValue ());
    if (aValue instanceof final Character aChar)
      return XdmAtomic.string (aChar.toString ());
    if (aValue instanceof final URI aURI)
      return XdmAtomic.anyUri (aURI.toString ());
    if (aValue instanceof final QName aQName)
      return XdmAtomic.qname (new XdmQName (aQName.getNamespaceURI (), aQName.getLocalPart (), aQName.getPrefix ()));
    if (aValue instanceof final Node aNode)
      return DomToXdm.convert (aNode, null);
    throw new IllegalArgumentException ("A value of type " + aValue.getClass ().getName () + " cannot be converted to XDM");
  }

  /**
   * Convert a Java value into an XDM sequence:
   * <ul>
   * <li><code>null</code> gives the empty sequence;</li>
   * <li>an {@link XdmSequence} or {@link IXdmItem} (a phoxtrot node, atomic
   * value, map, array or function) is taken as it is;</li>
   * <li>a {@link String} gives an <code>xs:untypedAtomic</code>, so that the
   * function conversion rules cast it to the declared type of the parameter
   * (an <code>as="xs:integer"</code> parameter accepts "42"), as for a value
   * given on a command line; pass <code>XdmAtomic.string (...)</code> for an
   * <code>xs:string</code>;</li>
   * <li>{@link Boolean} gives <code>xs:boolean</code>; {@link Integer},
   * {@link Long}, {@link Short}, {@link Byte}, {@link BigInteger} and other
   * whole numbers <code>xs:integer</code>; {@link BigDecimal}
   * <code>xs:decimal</code>; {@link Double} <code>xs:double</code>;
   * {@link Float} <code>xs:float</code>; {@link Character}
   * <code>xs:string</code>; {@link URI} <code>xs:anyURI</code>; a JAXP
   * {@link QName} <code>xs:QName</code>;</li>
   * <li>a W3C DOM {@link Node} gives the corresponding XDM node of a copy of
   * its whole tree ({@link DomToXdm#convert(Node, String)}); each DOM node
   * passed is converted on its own, so two nodes of one DOM document end up
   * in different XDM trees;</li>
   * <li>an object array or an {@link Iterable} gives the concatenation of
   * its converted members (nested ones flattened, as XDM sequences
   * are).</li>
   * </ul>
   *
   * @param aValue
   *        The value. May be <code>null</code>.
   * @return The sequence. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         for a value of another type (including primitive arrays and
   *         maps)
   */
  @NonNull
  public static XdmSequence toXdm (@Nullable final Object aValue)
  {
    if (aValue == null)
      return XdmSequence.EMPTY;
    if (aValue instanceof final XdmSequence aSeq)
      return aSeq;
    // Before Iterable: an XDM array or map is one item
    if (aValue instanceof final IXdmItem aItem)
      return XdmSequence.of (aItem);
    if (aValue instanceof final Object [] aArray)
    {
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final Object aMember : aArray)
        aBuilder.addAll (toXdm (aMember));
      return aBuilder.build ();
    }
    if (aValue instanceof final Iterable <?> aIterable)
    {
      final XdmSequence.Builder aBuilder = new XdmSequence.Builder ();
      for (final Object aMember : aIterable)
        aBuilder.addAll (toXdm (aMember));
      return aBuilder.build ();
    }
    return XdmSequence.of (_toItem (aValue));
  }

  /**
   * Parse a parameter name as JAXP gives it: <code>{uri}local</code> (the
   * JAXP form), <code>Q{uri}local</code> (an EQName) or a local name in no
   * namespace.
   *
   * @param sName
   *        The name. May not be <code>null</code>.
   * @return The expanded name. Never <code>null</code>.
   * @throws IllegalArgumentException
   *         if the name is none of these forms or the local part is no
   *         NCName
   */
  @NonNull
  public static XdmQName parseName (@NonNull final String sName)
  {
    ValueEnforcer.notNull (sName, "Name");
    String sUri = "";
    String sLocal = sName;
    final String s = sName.startsWith ("Q{") ? sName.substring (1) : sName;
    if (s.startsWith ("{"))
    {
      final int nEnd = s.indexOf ('}');
      if (nEnd < 0)
        throw new IllegalArgumentException ("The name '" + sName + "' has no closing brace");
      sUri = s.substring (1, nEnd);
      sLocal = s.substring (nEnd + 1);
    }
    if (!XmlNames.isNCName (sLocal))
      throw new IllegalArgumentException ("The name '" + sName + "' is not of the form {uri}local or local");
    return new XdmQName (sUri, sLocal);
  }
}
