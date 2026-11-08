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

import javax.xml.transform.Source;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.phoxtrot.xdm.IXdmNode;

/**
 * A JAXP {@link Source} that holds an XDM node, so that a tree built or
 * parsed with phoxtrot is used as it is: as the source of
 * {@link PhoxtrotTransformer#transform(Source, javax.xml.transform.Result)}
 * (the node becomes the global context item), as a stylesheet, or as the
 * result of a {@link javax.xml.transform.URIResolver}. Only the phoxtrot JAXP
 * classes understand it.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class XdmSource implements Source
{
  /**
   * The feature name for
   * {@link javax.xml.transform.TransformerFactory#getFeature(String)}: the
   * factory accepts this source.
   */
  public static final String FEATURE = "http://helger.com/phoxtrot/jaxp/XdmSource/feature";

  private final IXdmNode m_aNode;
  private String m_sSystemId;

  /**
   * @param aNode
   *        The node. May not be <code>null</code>. Its document URI (or base
   *        URI) is the initial system ID.
   */
  public XdmSource (@NonNull final IXdmNode aNode)
  {
    ValueEnforcer.notNull (aNode, "Node");
    m_aNode = aNode;
    final IXdmNode aRoot = aNode.getRoot ();
    m_sSystemId = aRoot.getDocumentUri () != null ? aRoot.getDocumentUri () : aRoot.getBaseUri ();
  }

  /** @return The node. Never <code>null</code>. */
  @NonNull
  public final IXdmNode getNode ()
  {
    return m_aNode;
  }

  /**
   * Set the system ID. It is the base URI of a stylesheet given as this
   * source; the node itself is not changed.
   *
   * @param sSystemId
   *        The system ID, may be <code>null</code>.
   */
  public void setSystemId (@Nullable final String sSystemId)
  {
    m_sSystemId = sSystemId;
  }

  @Nullable
  public String getSystemId ()
  {
    return m_sSystemId;
  }
}
