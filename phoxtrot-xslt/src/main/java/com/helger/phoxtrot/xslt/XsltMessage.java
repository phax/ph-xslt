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
package com.helger.phoxtrot.xslt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.value.XdmSequence;

/**
 * One message of a transformation: an executed <code>xsl:message</code>, a
 * failed <code>xsl:assert</code> or a processor warning, as passed to an
 * {@link IXsltMessageListener} and attached to an {@link XsltException}.
 *
 * @param kind
 *        What produced the message.
 * @param text
 *        The text of the message, as in
 *        {@link XsltTransformResult#messages()}: the items of the content,
 *        atomic values separated by a space and nodes serialized as XML.
 *        When evaluating the content of an <code>xsl:message</code> raised a
 *        dynamic error, the text is "Error in xsl:message: " and the error
 *        message.
 * @param content
 *        The content of the message (select value followed by the result of
 *        the sequence constructor); empty for a warning and for an
 *        <code>xsl:message</code> whose content raised an error.
 * @param terminate
 *        <code>true</code> if the message ends the transformation: an
 *        <code>xsl:message</code> with <code>terminate="yes"</code> and every
 *        failed <code>xsl:assert</code>.
 * @param errorCode
 *        The code of the error the message ends the transformation with
 *        (XTMM9000, XTMM9001 or the effective <code>error-code</code>),
 *        <code>null</code> if it does not terminate.
 * @param moduleUri
 *        The URI of the stylesheet module of the instruction, or
 *        <code>null</code> if unknown.
 * @param line
 *        The 1-based line of the instruction, 0 if unknown.
 * @param column
 *        The 1-based column of the instruction, 0 if unknown.
 * @author Philip Helger
 */
@Immutable
public record XsltMessage (@NonNull EXsltMessageKind kind,
                           @NonNull String text,
                           @NonNull XdmSequence content,
                           boolean terminate,
                           @Nullable XdmQName errorCode,
                           @Nullable String moduleUri,
                           int line,
                           int column)
{}
