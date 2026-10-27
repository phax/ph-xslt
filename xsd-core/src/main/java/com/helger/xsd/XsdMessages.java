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

import java.util.Locale;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmNode;

/**
 * Reading of the <code>msg:message</code> convention (go-xslt
 * <code>assertMessages</code> in <code>messages.go</code>) and the choice of
 * the wording of an error by language. The convention affects
 * {@link XsdError#message()} and {@link XsdError#rule()} only, never the
 * verdict; appinfo content in any other namespace is ignored.
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

  /**
   * go-xslt <code>pickMessage</code>: replace the generic text of an error by
   * the best <code>msg:message</code>: the one in the language (exact, then
   * primary subtag), else one without <code>xml:lang</code>, else the first.
   *
   * @param aError
   *        The error.
   * @param aMessages
   *        The candidate messages; may be empty.
   * @param sLanguage
   *        The preferred language; may be empty.
   * @return The error with the chosen rule and message, or the error itself
   *         if there is no candidate.
   */
  @NonNull
  static XsdError pickMessage (@NonNull final XsdError aError,
                               @NonNull final ICommonsList <XsdMessage> aMessages,
                               @NonNull final String sLanguage)
  {
    if (aMessages.isEmpty ())
      return aError;
    final String sLang = sLanguage.toLowerCase (Locale.ROOT);
    XsdMessage aPick = null;
    if (!sLang.isEmpty ())
    {
      final int nDash = sLang.indexOf ('-');
      final String sPrimary = nDash < 0 ? sLang : sLang.substring (0, nDash);
      for (final XsdMessage aMsg : aMessages)
        if (aMsg.lang ().toLowerCase (Locale.ROOT).equals (sLang))
        {
          aPick = aMsg;
          break;
        }
      if (aPick == null)
        for (final XsdMessage aMsg : aMessages)
        {
          final String sMsgLang = aMsg.lang ().toLowerCase (Locale.ROOT);
          if (sMsgLang.equals (sPrimary) || !sMsgLang.isEmpty () && sMsgLang.startsWith (sPrimary + "-"))
          {
            aPick = aMsg;
            break;
          }
        }
    }
    if (aPick == null)
      for (final XsdMessage aMsg : aMessages)
        if (aMsg.lang ().isEmpty ())
        {
          aPick = aMsg;
          break;
        }
    if (aPick == null)
      aPick = aMessages.getFirstOrNull ();
    return new XsdError (aError.line (),
                         aError.column (),
                         aError.code (),
                         aPick.text ().isEmpty () ? aError.message () : aPick.text (),
                         aPick.rule (),
                         aError.path (),
                         aError.kind (),
                         aError.particle ());
  }
}
