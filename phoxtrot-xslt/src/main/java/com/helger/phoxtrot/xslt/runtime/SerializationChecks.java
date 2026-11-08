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
package com.helger.phoxtrot.xslt.runtime;

import java.math.BigDecimal;
import java.util.ArrayDeque;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.phoxtrot.xdm.ENodeKind;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;

/**
 * Serialization errors that depend on the result tree and that the serializer
 * does not detect itself (a port of go-xslt's checkSerializationErrors):
 * SEPM0004, SESU0013 for the html method, SEPM0016 for doctype-public,
 * SERE0014 and SERE0015.
 *
 * @author Philip Helger
 */
@Immutable
public final class SerializationChecks
{
  @PresentForCodeCoverage
  private static final SerializationChecks INSTANCE = new SerializationChecks ();

  private SerializationChecks ()
  {}

  /**
   * The requested HTML version (Serialization 3.1 section 7.4.1): html-version
   * if present, else version.
   *
   * @return <code>true</code> if it is 5.0 or above, or if neither parameter
   *         is set; <code>false</code> for a lower version and for a version
   *         that is not a decimal number.
   */
  private static boolean _isHtml5 (@NonNull final SerializationParams aParams)
  {
    BigDecimal aVersion = aParams.getHtmlVersion ();
    if (aVersion == null && aParams.getVersion () != null)
      try
      {
        aVersion = new BigDecimal (aParams.getVersion ().trim ());
      }
      catch (final NumberFormatException ex)
      {
        return false;
      }
    return aVersion == null || aVersion.compareTo (BigDecimal.valueOf (5)) >= 0;
  }

  /**
   * @return <code>true</code> if the string contains a character in
   *         #x7F-#x9F, which HTML before HTML5 does not permit.
   */
  private static boolean _hasC1 (@NonNull final String s)
  {
    for (int i = 0; i < s.length (); ++i)
    {
      final char c = s.charAt (i);
      if (c >= 0x7F && c <= 0x9F)
        return true;
    }
    return false;
  }

  /**
   * The checks of the html output method on the content of a result tree
   * (SERE0014, SERE0015; Serialization 3.1 section 7.3). The tree is walked
   * iteratively: a processing instruction whose value contains
   * <code>&gt;</code> raises SERE0015; for a requested HTML version below 5.0
   * a text node or an attribute value with a character in #x7F-#x9F raises
   * SERE0014 (comments and processing instructions are not checked for
   * these characters).
   *
   * @param aParams
   *        The serialization parameters of the html method.
   * @param aRoot
   *        The node to check.
   * @param bDeep
   *        <code>true</code> to check its subtree, <code>false</code> for the
   *        node and its attributes only.
   * @throws XPathException
   *         SERE0014 or SERE0015
   */
  static void checkHtmlContent (@NonNull final SerializationParams aParams, @NonNull final IXdmNode aRoot, final boolean bDeep)
  {
    final boolean bCheckC1 = !_isHtml5 (aParams);
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    aStack.push (aRoot);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aCur = aStack.pop ();
      if (aCur.getKind () == ENodeKind.PROCESSING_INSTRUCTION && aCur.getValue ().indexOf ('>') >= 0)
        throw new XPathException ("SERE0015", "The html output method cannot write '>' in a processing instruction");
      if (bCheckC1)
      {
        if (aCur.isText () && _hasC1 (aCur.getValue ()))
          throw new XPathException ("SERE0014", "The html output method cannot write a character in #x7F-#x9F");
        for (int i = 0; i < aCur.getAttributeCount (); ++i)
          if (_hasC1 (aCur.getAttributeAtIndex (i).getValue ()))
            throw new XPathException ("SERE0014", "The html output method cannot write a character in #x7F-#x9F");
      }
      if (bDeep)
        for (int i = 0; i < aCur.getChildCount (); ++i)
          aStack.push (aCur.getChildAtIndex (i));
    }
  }

  /**
   * Check the serialisation parameters and the result tree before the result
   * is serialised:
   * <ul>
   * <li>html method with a version that is not a decimal number of at least
   * 1.0: SESU0013;</li>
   * <li>xml or xhtml method with a doctype-public that contains a character
   * outside the XML <code>PubidChar</code> production: SEPM0016;</li>
   * <li>xml or xhtml method with standalone other than <code>omit</code> or
   * with doctype-system, and more than one element child or a text child of
   * the tree root: SEPM0004 (Serialization 3.1 section 5);</li>
   * <li>html method: the content checks of
   * {@link #checkHtmlContent(SerializationParams, IXdmNode, boolean)} on the
   * whole tree.</li>
   * </ul>
   * The parameter checks run even without a tree; the tree checks are skipped
   * for <code>null</code>.
   *
   * @param aParams
   *        The effective parameters.
   * @param aTree
   *        The result tree or <code>null</code>.
   * @throws XPathException
   *         with the serialization error code
   */
  public static void check (@NonNull final SerializationParams aParams, @Nullable final IXdmNode aTree)
  {
    final String sMethod = aParams.getMethod ();
    final boolean bXml = SerializationParams.METHOD_XML.equals (sMethod) || SerializationParams.METHOD_XHTML.equals (sMethod);
    final boolean bHtml = SerializationParams.METHOD_HTML.equals (sMethod);
    if (bHtml && aParams.getVersion () != null)
    {
      boolean bOk;
      try
      {
        bOk = new BigDecimal (aParams.getVersion ().trim ()).compareTo (BigDecimal.ONE) >= 0;
      }
      catch (final NumberFormatException ex)
      {
        bOk = false;
      }
      if (!bOk)
        throw new XPathException ("SESU0013", "Unsupported HTML version '" + aParams.getVersion () + "'");
    }
    final String sPublic = aParams.getDoctypePublic ();
    if (bXml && sPublic != null)
      for (int i = 0; i < sPublic.length (); ++i)
      {
        final char c = sPublic.charAt (i);
        if (!(c == ' ' || c == '\r' || c == '\n' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
              "-'()+,./:=?;!*#@$_%".indexOf (c) >= 0))
          throw new XPathException ("SEPM0016", "doctype-public '" + sPublic + "' is not a valid public identifier");
      }
    if (aTree == null)
      return;
    if (bXml && (!SerializationParams.STANDALONE_OMIT.equals (aParams.getStandalone ()) || aParams.getDoctypeSystem () != null))
    {
      int nElements = 0;
      for (int i = 0; i < aTree.getChildCount (); ++i)
      {
        final IXdmNode aChild = aTree.getChildAtIndex (i);
        if (aChild.isElement ())
          nElements++;
        else
          if (aChild.isText ())
            throw new XPathException ("SEPM0004", "A result with a top-level text node cannot be serialized with standalone or doctype-system");
      }
      if (nElements > 1)
        throw new XPathException ("SEPM0004",
                                  "A result with " + nElements + " top-level elements cannot be serialized with standalone or doctype-system");
    }
    if (bHtml)
      checkHtmlContent (aParams, aTree, true);
  }

}
