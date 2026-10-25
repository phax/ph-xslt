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
package com.helger.xslt.model;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.instr.SequenceConstructor;
import com.helger.xslt.tree.XslElement;

/**
 * An attribute set: all xsl:attribute-set declarations of one name, in order
 * of import precedence and declaration. Each declaration expands its own
 * use-attribute-sets before its own attributes.
 *
 * @author Philip Helger
 */
public final class AttributeSet
{
  /** One xsl:attribute-set declaration. */
  public record Decl (@NonNull XslElement element,
                      @NonNull ICommonsList <XdmQName> uses,
                      @NonNull SequenceConstructor body)
  {}

  private final XdmQName m_aName;
  private final ICommonsList <Decl> m_aDecls = new CommonsArrayList <> ();

  public AttributeSet (@NonNull final XdmQName aName)
  {
    m_aName = aName;
  }

  @NonNull
  public XdmQName getName ()
  {
    return m_aName;
  }

  public void addDecl (@NonNull final Decl aDecl)
  {
    m_aDecls.add (aDecl);
  }

  /** @return The declarations in expansion order (do not modify). */
  @NonNull
  public ICommonsList <Decl> decls ()
  {
    return m_aDecls;
  }
}
