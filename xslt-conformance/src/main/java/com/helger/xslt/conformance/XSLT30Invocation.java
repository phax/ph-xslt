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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.xpath.xdm.XdmQName;

/**
 * The <code>&lt;test&gt;</code> element of a test case: what to compile and how
 * to invoke it.
 *
 * @param stylesheets
 *        The <code>&lt;stylesheet&gt;</code> children.
 * @param packages
 *        The <code>&lt;package&gt;</code> children.
 * @param initialTemplate
 *        The initial template, or <code>null</code>.
 * @param initialMode
 *        The initial mode, or <code>null</code>.
 * @param initialFunction
 *        The initial function, or <code>null</code>.
 * @param params
 *        Stylesheet parameters (static and non-static).
 * @param contextItemSelect
 *        The <code>&lt;context-item select&gt;</code>, or <code>null</code>.
 * @param output
 *        The <code>&lt;output&gt;</code>, or <code>null</code>.
 * @param namespaces
 *        The prefixed namespaces in scope on the <code>&lt;test&gt;</code>
 *        element.
 * @author Philip Helger
 */
public record XSLT30Invocation (@NonNull ICommonsList <XSLT30Environment.Module> stylesheets,
                                @NonNull ICommonsList <XSLT30Environment.Module> packages,
                                @Nullable Entry initialTemplate,
                                @Nullable Entry initialMode,
                                @Nullable Entry initialFunction,
                                @NonNull ICommonsList <XSLT30Param> params,
                                @Nullable String contextItemSelect,
                                @Nullable Output output,
                                @NonNull ICommonsOrderedMap <String, String> namespaces)
{
  /**
   * An <code>&lt;initial-template&gt;</code>, <code>&lt;initial-mode&gt;</code>
   * or <code>&lt;initial-function&gt;</code>.
   *
   * @param name
   *        The expanded name; <code>null</code> if the attribute is absent or
   *        its prefix is not bound in the catalog.
   * @param lexicalName
   *        The name as written, empty if absent.
   * @param select
   *        The initial match selection of an initial mode, empty if absent.
   * @param params
   *        Template parameters, or function arguments in order.
   * @param namespaces
   *        The prefixed namespaces in scope on the element.
   */
  public record Entry (@Nullable XdmQName name,
                       @NonNull String lexicalName,
                       @NonNull String select,
                       @NonNull ICommonsList <XSLT30Param> params,
                       @NonNull ICommonsOrderedMap <String, String> namespaces)
  {
    /** @return <code>true</code> if a name is written but its prefix is unbound. */
    public boolean isNameUnresolved ()
    {
      return name == null && !lexicalName.isEmpty ();
    }
  }

  /**
   * An <code>&lt;output&gt;</code>.
   *
   * @param file
   *        Where the principal result goes (establishes the base output URI);
   *        empty if absent, "#absent" for none.
   * @param serialize
   *        The <code>serialize</code> attribute, may be empty.
   * @param resultVar
   *        The variable the raw result is bound to in assertions, may be
   *        empty (then <code>$result</code>).
   */
  public record Output (@NonNull String file, @NonNull String serialize, @NonNull String resultVar, @NonNull String tree)
  {
    /**
     * Constructor without the tree attribute.
     *
     * @param file
     *        Output file.
     * @param serialize
     *        Serialize attribute.
     * @param resultVar
     *        Result variable name.
     */
    public Output (@NonNull final String file, @NonNull final String serialize, @NonNull final String resultVar)
    {
      this (file, serialize, resultVar, "");
    }
  }
}
