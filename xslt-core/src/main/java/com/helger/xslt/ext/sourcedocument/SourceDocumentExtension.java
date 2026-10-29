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
package com.helger.xslt.ext.sourcedocument;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.XPathException;
import com.helger.xpath.fn.FnUri;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltException;
import com.helger.xslt.compile.Avt;
import com.helger.xslt.compile.CompileContext;
import com.helger.xslt.compile.IXsltExtension;
import com.helger.xslt.compile.XsltExtensionRegistry;
import com.helger.xslt.ext.accumulator.AccumulatorExtension;
import com.helger.xslt.instr.CompiledInstruction;
import com.helger.xslt.instr.IInstruction;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.runtime.Engine;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.schema.ValidationRequest;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XsltNames;

/**
 * xsl:source-document (XSLT 3.0 section 18.1). This processor does not claim
 * streaming, so a document declared streamable is read in full as well; the
 * declaration still counts for the accumulator rules that depend on it
 * (XTDE3362).
 *
 * @author Philip Helger
 */
public final class SourceDocumentExtension implements IXsltExtension
{
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
