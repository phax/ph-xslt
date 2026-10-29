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
package com.helger.xslt.conformance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.conformance.QT3XmlCompare;
import com.helger.xpath.xdm.ENodeKind;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;

/**
 * The comparison behind the XSLT <code>assert-xml</code>, a port of Go's
 * <code>xmlTreeEqual</code>/<code>xsltNodeEqual</code>. It differs from the
 * QT3 one: whitespace-only text children (indentation of hand-formatted
 * expected results) are ignored on both sides, prefixes are not compared, and
 * <code>normalize-space="true"</code> collapses whitespace inside text,
 * comments and processing instructions. If either side is not well-formed,
 * the texts are compared with whitespace normalised. Iterative, so deep trees
 * cannot overflow the stack.
 *
 * @author Philip Helger
 */
@Immutable
public final class XSLT30XmlCompare
{
  // Unicode White_Space, like Go's unicode.IsSpace behind strings.Fields
  private static final Pattern WHITESPACE = Pattern.compile ("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern EDGE_WHITESPACE = Pattern.compile ("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

  private record Pair (@NonNull IXdmNode left, @NonNull IXdmNode right)
  {}

  @PresentForCodeCoverage
  private static final XSLT30XmlCompare INSTANCE = new XSLT30XmlCompare ();

  private XSLT30XmlCompare ()
  {}

  private static boolean _isBlankText (@NonNull final IXdmNode aNode)
  {
    return aNode.getKind () == ENodeKind.TEXT && trimSpace (aNode.getValue ()).isEmpty ();
  }

  @NonNull
  private static ICommonsList <IXdmNode> _significantChildren (@NonNull final IXdmNode aNode)
  {
    final ICommonsList <IXdmNode> ret = new CommonsArrayList <> ();
    for (final IXdmNode aChild : aNode.getAllChildren ())
      if (!_isBlankText (aChild))
        ret.add (aChild);
    return ret;
  }

  private static boolean _isSameName (@NonNull final XdmQName aLeft, @NonNull final XdmQName aRight)
  {
    return aLeft.localName ().equals (aRight.localName ()) && aLeft.namespaceUri ().equals (aRight.namespaceUri ());
  }

  private static boolean _isSameAttributes (@NonNull final IXdmNode aLeft, @NonNull final IXdmNode aRight)
  {
    if (aLeft.getAttributeCount () != aRight.getAttributeCount ())
      return false;
    for (final IXdmNode aLA : aLeft.getAllAttributes ())
    {
      final IXdmNode aRA = aRight.getAttribute (aLA.getName ().namespaceUri (), aLA.getName ().localName ());
      if (aRA == null || !Objects.equals (aLA.getValue (), aRA.getValue ()))
        return false;
    }
    return true;
  }

  @NonNull
  private static String _value (@NonNull final IXdmNode aNode, final boolean bNormalizeSpace)
  {
    final String s = Objects.toString (aNode.getValue (), "");
    return bNormalizeSpace ? normalizeSpace (s) : s;
  }

  /**
   * Remove leading and trailing whitespace, like Go's
   * <code>strings.TrimSpace</code>.
   *
   * @param s
   *        Source string.
   * @return Trimmed string.
   */
  @NonNull
  public static String trimSpace (@NonNull final String s)
  {
    return EDGE_WHITESPACE.matcher (s).replaceAll ("");
  }

  /**
   * Collapse runs of whitespace and trim, like Go's
   * <code>strings.Join(strings.Fields(s), " ")</code>.
   *
   * @param s
   *        Source string.
   * @return Normalised string.
   */
  @NonNull
  public static String normalizeSpace (@NonNull final String s)
  {
    return trimSpace (WHITESPACE.matcher (s).replaceAll (" "));
  }

  /**
   * Structural comparison of two trees.
   *
   * @param aLeft
   *        First node.
   * @param aRight
   *        Second node.
   * @param bNormalizeSpace
   *        <code>true</code> to compare text, comment and PI values with
   *        whitespace normalised.
   * @return <code>true</code> if equal.
   */
  public static boolean isNodeEqual (@NonNull final IXdmNode aLeft,
                                     @NonNull final IXdmNode aRight,
                                     final boolean bNormalizeSpace)
  {
    final Deque <Pair> aStack = new ArrayDeque <> ();
    aStack.push (new Pair (aLeft, aRight));
    while (!aStack.isEmpty ())
    {
      final Pair aPair = aStack.pop ();
      final IXdmNode a = aPair.left ();
      final IXdmNode b = aPair.right ();
      if (a.getKind () != b.getKind ())
        return false;
      switch (a.getKind ())
      {
        case ELEMENT:
        case DOCUMENT:
        {
          if (a.isElement () && (!_isSameName (a.getName (), b.getName ()) || !_isSameAttributes (a, b)))
            return false;
          final ICommonsList <IXdmNode> aLC = _significantChildren (a);
          final ICommonsList <IXdmNode> aRC = _significantChildren (b);
          if (aLC.size () != aRC.size ())
            return false;
          for (int i = 0; i < aLC.size (); ++i)
            aStack.push (new Pair (aLC.get (i), aRC.get (i)));
          break;
        }
        case PROCESSING_INSTRUCTION:
          if (!a.getName ().localName ().equals (b.getName ().localName ()) ||
              !_value (a, bNormalizeSpace).equals (_value (b, bNormalizeSpace)))
            return false;
          break;
        default:
          if (!_value (a, bNormalizeSpace).equals (_value (b, bNormalizeSpace)))
            return false;
          break;
      }
    }
    return true;
  }

  /**
   * Plain text comparison: whitespace normalised, or just trimmed.
   *
   * @param sExpected
   *        Expected text.
   * @param sActual
   *        Actual text.
   * @param bNormalizeSpace
   *        <code>true</code> to normalise whitespace.
   * @return <code>true</code> if equal.
   */
  public static boolean isSerialEqual (@NonNull final String sExpected,
                                       @NonNull final String sActual,
                                       final boolean bNormalizeSpace)
  {
    if (bNormalizeSpace)
      return normalizeSpace (sExpected).equals (normalizeSpace (sActual));
    return trimSpace (sExpected).equals (trimSpace (sActual));
  }

  /**
   * @param sExpected
   *        The expected XML from the catalog.
   * @param sActual
   *        The serialised result.
   * @param bNormalizeSpace
   *        The assertion's <code>normalize-space</code>.
   * @return <code>true</code> if equal as trees, or (if one side does not
   *         parse) as texts with whitespace normalised.
   */
  public static boolean isXmlEqual (@NonNull final String sExpected,
                                    @NonNull final String sActual,
                                    final boolean bNormalizeSpace)
  {
    final IXdmNode aExpected = QT3XmlCompare.parseFragment (sExpected);
    final IXdmNode aActual = QT3XmlCompare.parseFragment (sActual);
    if (aExpected == null || aActual == null)
      return isSerialEqual (sExpected, sActual, true);
    return isNodeEqual (aExpected, aActual, bNormalizeSpace);
  }
}
