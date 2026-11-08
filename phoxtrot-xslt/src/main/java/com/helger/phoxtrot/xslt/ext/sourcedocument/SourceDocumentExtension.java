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
package com.helger.phoxtrot.xslt.ext.sourcedocument;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.XdmStreamReader;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.fn.FnUri;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.compile.Avt;
import com.helger.phoxtrot.xslt.compile.CompileContext;
import com.helger.phoxtrot.xslt.compile.IXsltExtension;
import com.helger.phoxtrot.xslt.compile.XsltExtensionRegistry;
import com.helger.phoxtrot.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.phoxtrot.xslt.instr.CompiledInstruction;
import com.helger.phoxtrot.xslt.instr.IInstruction;
import com.helger.phoxtrot.xslt.instr.SequenceConstructor;
import com.helger.phoxtrot.xslt.model.CompiledStylesheet;
import com.helger.phoxtrot.xslt.runtime.Engine;
import com.helger.phoxtrot.xslt.runtime.IXsltOutput;
import com.helger.phoxtrot.xslt.runtime.NodeCopier;
import com.helger.phoxtrot.xslt.runtime.XsltContext;
import com.helger.phoxtrot.xslt.schema.ValidationRequest;
import com.helger.phoxtrot.xslt.stream.StreamConsumer;
import com.helger.phoxtrot.xslt.stream.StreamPlanner;
import com.helger.phoxtrot.xslt.stream.StreamedRun;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XsltNames;

/**
 * xsl:source-document (XSLT 3.0 section 18.1). With the streaming claim, a
 * document declared streamable whose body the streamability analysis proved
 * guaranteed streamable, and whose body has the shape the dispatcher runs
 * ({@link StreamPlanner}), is read as a stream ({@link StreamedRun}).
 * Otherwise it is read in full; the declaration still counts for the
 * accumulator rules that depend on it (XTDE3362).
 *
 * @author Philip Helger
 */
public final class SourceDocumentExtension implements IXsltExtension
{
  /**
   * Run the body over the document read as a stream.
   *
   * @return <code>false</code> if the document is not streamed (and nothing
   *         was done).
   */
  private static boolean _runStreamed (@NonNull final XsltContext aCtx,
                                       @NonNull final IXsltOutput aOut,
                                       @NonNull final String sUri,
                                       @NonNull final XslElement aConsumer,
                                       @NonNull final SequenceConstructor aBody,
                                       @NonNull final ICommonsList <XdmQName> aAccumulators)
  {
    final Engine aEngine = aCtx.getEngine ();
    final XdmStreamReader aReader = aEngine.openStreamedDocument (sUri);
    if (aReader == null)
      return false;
    final IXdmNode aRoot = aReader.getDocument ();
    final StreamedRun aRun = new StreamedRun (aReader, aConsumer);
    // Without use-accumulators no accumulator applies to the document
    final Object aPrevious = AccumulatorExtension.restrictTree (aEngine, aRoot, aAccumulators, true);
    aRun.push (aEngine);
    try
    {
      aRun.setAccumulators (AccumulatorExtension.startStreamed (aCtx, aRoot, aAccumulators));
      aBody.execute (aCtx.withFocus (aRoot, 1, 1), aOut);
      if (!aRun.isDriven ())
        throw new IllegalStateException ("The consumer of the streamed document was not executed");
    }
    finally
    {
      aRun.pop (aEngine);
      AccumulatorExtension.restoreTree (aEngine, aRoot, aPrevious);
      try
      {
        aReader.close ();
      }
      catch (final XdmParseException ex)
      {
        // Everything needed was read
      }
    }
    return true;
  }

  /**
   * Compile xsl:source-document (XSLT 3.0 section 18.1). Static checks:
   * streamable must be an XSLT boolean (XTSE0020); validation must be one of
   * strict, lax, preserve or strip (XTSE0020), and strict or lax as well as a
   * type attribute need a schema-aware stylesheet (XTSE1660). The
   * use-accumulators names are registered with the accumulator family.
   * <p>
   * At run time the href AVT is trimmed, converted from an IRI and resolved
   * against the static base URI if that is absolute (a resolution failure is
   * FODC0005). The document is streamed through {@link #_runStreamed} only if
   * streamable is yes, no validation is requested, the streamability verdicts
   * say the instruction is guaranteed streamable, the {@link StreamPlanner}
   * finds a consumer that is registered as one, and all used accumulators
   * are streamable. Otherwise the document is loaded in full through the
   * document cache of the engine (a <code>null</code> result, i.e. a fragment
   * identifier that selects nothing, is FODC0002); with validation other than
   * preserve a deep copy (type annotations dropped for strip) is validated
   * and used instead, so the cached document stays unchanged. The body runs
   * with the loaded node as context item (position and size 1) while the
   * accumulators applicable to the tree are restricted to the
   * use-accumulators names.
   *
   * @param aCtx
   *        Compile context.
   * @param aEl
   *        The xsl:source-document element.
   * @return The compiled instruction. Never <code>null</code>.
   */
  @NonNull
  private static IInstruction _compileSourceDocument (@NonNull final CompileContext aCtx, @NonNull final XslElement aEl)
  {
    final Avt aHref = aCtx.compileAvt (aEl, aEl.getAttr ("href"));
    final String sStreamable = aEl.getAttr ("streamable");
    if (sStreamable != null && !XsltNames.isXsltBooleanLexical (sStreamable))
      throw XsltException.at (aEl, "XTSE0020", "streamable='" + sStreamable + "' is not an XSLT boolean");
    final boolean bStreamable = XsltNames.isXsltTrue (sStreamable);
    final String sValidation = aEl.getAttr ("validation");
    final boolean bSchemaAware = aCtx.getStylesheet ().isSchemaAware ();
    if (sValidation != null)
    {
      final String s = sValidation.trim ();
      if (("strict".equals (s) || "lax".equals (s)) && !bSchemaAware)
        throw XsltException.at (aEl, "XTSE1660", "validation='" + s + "' requires a schema-aware processor");
      if (!"preserve".equals (s) && !"strip".equals (s) && !"strict".equals (s) && !"lax".equals (s))
        throw XsltException.at (aEl, "XTSE0020", "validation='" + sValidation + "' is not a valid value");
    }
    if (aEl.hasAttr ("type") && !bSchemaAware)
      throw XsltException.at (aEl, "XTSE1660", "The type attribute requires a schema-aware processor");
    // Schema-aware: the document is validated (as a copy, the document itself
    // is shared by all retrievals of its URI)
    final ValidationRequest aValidation = ValidationRequest.compile (aCtx, aEl, ValidationRequest.EKind.SOURCE_DOCUMENT);
    final ICommonsList <XdmQName> aAccumulators = AccumulatorExtension.registerUseAccumulators (aCtx, aEl);
    final SequenceConstructor aBody = aCtx.compileContent (aEl);
    final String sBase = aEl.getStaticBaseUri ();
    // Streamed execution: a body of the shape the dispatcher runs, without
    // validation; whether it and the accumulators are guaranteed streamable is
    // known at the end of compilation
    final boolean bStreamCandidate = bStreamable && (aValidation == null || !aValidation.isValidating ());
    final CompiledStylesheet aSheet = aCtx.getStylesheet ();
    return new CompiledInstruction (aEl, (aRtCtx, aRtOut) -> {
      final String sHref = FnUri.iriToUri (aHref.evaluate (aRtCtx).trim ());
      final String sUri;
      try
      {
        sUri = sBase != null && FnUri.isAbsoluteUri (sBase) ? FnUri.resolveUri (sHref, sBase) : sHref;
      }
      catch (final XPathException ex)
      {
        throw new XPathException ("FODC0005", "Invalid URI '" + sHref + "' in xsl:source-document");
      }
      final Engine aEngine = aRtCtx.getEngine ();
      final XslElement aConsumer = bStreamCandidate &&
                                   aSheet.getStreamVerdicts () != null &&
                                   aSheet.getStreamVerdicts ().isSourceDocumentStreamable (aEl)
                                                                                                ? StreamPlanner.findConsumer (aEl,
                                                                                                                              aSheet.getStreamVerdicts ())
                                                                                                : null;
      if (aConsumer != null &&
          StreamConsumer.isConsumer (aSheet, aConsumer) &&
          AccumulatorExtension.isStreamable (aSheet, aAccumulators) &&
          _runStreamed (aRtCtx, aRtOut, sUri, aConsumer, aBody, aAccumulators))
        return;
      IXdmNode aNode = aEngine.getDocument (aRtCtx, sUri);
      if (aNode == null)
        throw new XPathException ("FODC0002", "The document '" + sUri + "' has no element with the given ID");
      if (aValidation != null && !aValidation.isPreserve ())
      {
        final IXdmNode aCopy = NodeCopier.copyDeep (aNode.getRoot (), true, !aValidation.isStrip ());
        aCopy.setDocumentUri (aNode.getRoot ().getDocumentUri ());
        aValidation.apply (aCopy);
        aNode = aCopy;
      }
      final IXdmNode aRoot = aNode.getRoot ();
      // Without use-accumulators no accumulator applies to the document
      final Object aPrevious = AccumulatorExtension.restrictTree (aEngine, aRoot, aAccumulators, bStreamable);
      try
      {
        aBody.execute (aRtCtx.withFocus (aNode, 1, 1), aRtOut);
      }
      finally
      {
        AccumulatorExtension.restoreTree (aEngine, aRoot, aPrevious);
      }
    });
  }

  public void register (@NonNull final XsltExtensionRegistry aRegistry)
  {
    aRegistry.registerInstruction ("source-document", SourceDocumentExtension::_compileSourceDocument);
  }
}
