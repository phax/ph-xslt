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
package com.helger.xpath.conformance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.xdm.XdmNode;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.XdmQName;

/**
 * The comparison behind <code>assert-xml</code>, a port of Go's
 * <code>qt3XMLEqual</code>: the expected fragment and the serialised result
 * are both parsed inside a wrapper element (they may be fragments) and
 * compared node by node: names by namespace URI and, unless prefixes are
 * ignored, prefix; attributes as a set; every child including
 * whitespace-only text, comments and processing instructions. Namespace
 * declarations are not compared, only the names they bind. Iterative, so deep
 * trees cannot overflow the stack.
 *
 * @author Philip Helger
 */
@Immutable
public final class QT3XmlCompare
{
  private static final String WRAPPER = "_x_";

  private record Pair (@NonNull XdmNode left, @NonNull XdmNode right)
  {}

  @PresentForCodeCoverage
  private static final QT3XmlCompare INSTANCE = new QT3XmlCompare ();

  private QT3XmlCompare ()
  {}

  @NonNull
  private static String _stripDecl (@NonNull final String s)
  {
    final String ret = s.strip ();
    if (ret.startsWith ("<?xml"))
    {
      final int i = ret.indexOf ("?>");
      if (i >= 0)
        return ret.substring (i + 2).strip ();
    }
    return ret;
  }

  private static boolean _isSameName (@Nullable final XdmQName aLeft,
                                      @Nullable final XdmQName aRight,
                                      final boolean bIgnorePrefixes)
  {
    if (aLeft == null || aRight == null)
      return aLeft == aRight;
    return aLeft.localName ().equals (aRight.localName ()) &&
           aLeft.namespaceUri ().equals (aRight.namespaceUri ()) &&
           (bIgnorePrefixes || aLeft.prefix ().equals (aRight.prefix ()));
  }

  private static boolean _isSameAttributes (@NonNull final XdmNode aLeft,
                                            @NonNull final XdmNode aRight,
                                            final boolean bIgnorePrefixes)
  {
    if (aLeft.getAttributeCount () != aRight.getAttributeCount ())
      return false;
    for (final XdmNode aLA : aLeft.getAllAttributes ())
    {
      boolean bFound = false;
      for (final XdmNode aRA : aRight.getAllAttributes ())
        if (_isSameName (aLA.getName (), aRA.getName (), bIgnorePrefixes) &&
            Objects.equals (aLA.getValue (), aRA.getValue ()))
        {
          bFound = true;
          break;
        }
      if (!bFound)
        return false;
    }
    return true;
  }

  /**
   * Parse an expected or actual XML fragment inside a wrapper element.
   *
   * @param sXml
   *        The fragment; a leading XML declaration is removed.
   * @return The document node, or <code>null</code> if it is not well-formed
   *         (also not as XML 1.1).
   */
  @Nullable
  public static XdmNode parseFragment (@NonNull final String sXml)
  {
    final String sWrapped = "<" + WRAPPER + ">" + _stripDecl (sXml) + "</" + WRAPPER + ">";
    try
    {
      return new XdmParser ().parse (sWrapped);
    }
    catch (final XdmParseException ex)
    {
      try
      {
        return new XdmParser ().parse ("<?xml version=\"1.1\"?>" + sWrapped);
      }
      catch (final XdmParseException ex2)
      {
        return null;
      }
    }
  }

  /**
   * Structural comparison of two trees.
   *
   * @param aLeft
   *        First node.
   * @param aRight
   *        Second node.
   * @param bIgnorePrefixes
   *        <code>true</code> to compare names without prefixes.
   * @return <code>true</code> if equal.
   */
  public static boolean isNodeEqual (@NonNull final XdmNode aLeft,
                                     @NonNull final XdmNode aRight,
                                     final boolean bIgnorePrefixes)
  {
    final Deque <Pair> aStack = new ArrayDeque <> ();
    aStack.push (new Pair (aLeft, aRight));
    while (!aStack.isEmpty ())
    {
      final Pair aPair = aStack.pop ();
      final XdmNode a = aPair.left ();
      final XdmNode b = aPair.right ();
      if (a.getKind () != b.getKind ())
        return false;
      switch (a.getKind ())
      {
        case ELEMENT:
        case DOCUMENT:
          if (a.isElement () &&
              (!_isSameName (a.getName (), b.getName (), bIgnorePrefixes) ||
               !_isSameAttributes (a, b, bIgnorePrefixes)))
            return false;
          if (a.getChildCount () != b.getChildCount ())
            return false;
          for (int i = 0; i < a.getChildCount (); ++i)
            aStack.push (new Pair (a.getChildAtIndex (i), b.getChildAtIndex (i)));
          break;
        case PROCESSING_INSTRUCTION:
          if (!a.getName ().localName ().equals (b.getName ().localName ()) ||
              !Objects.equals (a.getValue (), b.getValue ()))
            return false;
          break;
        default:
          // text, comment, attribute, namespace
          if (!Objects.equals (a.getValue (), b.getValue ()))
            return false;
          break;
      }
    }
    return true;
  }

  /**
   * @param sExpected
   *        The expected XML from the catalog.
   * @param sActual
   *        The serialised result.
   * @param bIgnorePrefixes
   *        The assertion's <code>ignore-prefixes</code>.
   * @return <code>true</code> if both parse and are structurally equal.
   */
  public static boolean isXmlEqual (@NonNull final String sExpected,
                                    @NonNull final String sActual,
                                    final boolean bIgnorePrefixes)
  {
    final XdmNode aExpected = parseFragment (sExpected);
    final XdmNode aActual = parseFragment (sActual);
    if (aExpected == null || aActual == null)
      return false;
    return isNodeEqual (aExpected, aActual, bIgnorePrefixes);
  }
}
