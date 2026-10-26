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
package com.helger.xsd;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmNode;

/**
 * Reading of the <code>msg:message</code> convention (go-xslt
 * <code>assertMessages</code> in <code>messages.go</code>). Choosing and
 * formatting the message for an error is phase V7.
 *
 * @author Philip Helger
 */
@Immutable
final class XsdMessages
{
  @PresentForCodeCoverage
  private static final XsdMessages INSTANCE = new XsdMessages ();

  private XsdMessages ()
  {}

  /**
   * @param aNode
   *        An xs:assert, xs:assertion, xs:element, xs:attribute or model group
   *        element.
   * @return The msg:message entries of its xs:annotation/xs:appinfo children.
   */
  @NonNull
  @ReturnsMutableCopy
  static ICommonsList <XsdMessage> messagesOf (@NonNull final XdmNode aNode)
  {
    final ICommonsList <XsdMessage> ret = new CommonsArrayList <> ();
    for (final XdmNode aAnn : XsdNodes.children (aNode))
    {
      if (!XsdNodes.isXS (aAnn, "annotation"))
        continue;
      for (final XdmNode aInfo : XsdNodes.children (aAnn))
      {
        if (!XsdNodes.isXS (aInfo, "appinfo"))
          continue;
        for (final XdmNode aMsg : XsdNodes.children (aInfo))
        {
          if (!aMsg.isElement () ||
              !XsdConstants.NS_MESSAGES.equals (XsdNodes.namespaceUri (aMsg)) ||
              !"message".equals (XsdNodes.localName (aMsg)))
            continue;
          final String sLang = XsdNodes.attrNS (aMsg, XsdConstants.NS_XML, "lang");
          ret.add (new XsdMessage (XsdNodes.attrOrEmpty (aMsg, "rule"),
                                   sLang == null ? "" : sLang.strip (),
                                   aMsg.getStringValue ().strip (),
                                   XsdNodes.attrOrEmpty (aMsg, "on")));
        }
      }
    }
    return ret;
  }
}
