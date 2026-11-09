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
package com.helger.phoxtrot.xdm.schema;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phoxtrot.xdm.XdmQName;

/**
 * A top-level element declaration.
 *
 * @author Philip Helger
 */
public interface IXdmElementDeclaration
{
  @NonNull
  XdmQName getName ();

  @NonNull
  IXdmTypeDefinition getTypeDefinition ();

  /**
   * @return <code>true</code> if the declaration has <code>nillable="true"</code>, so that an
   *         instance may carry <code>xsi:nil="true"</code>.
   */
  boolean isNillable ();

  /**
   * @return <code>true</code> if the declaration has <code>abstract="true"</code>, so that only
   *         members of its substitution group may appear in an instance.
   */
  boolean isAbstract ();

  /**
   * @param aName
   *        An element name.
   * @return This declaration if it has the name, else the declaration with that name in the
   *         substitution group of this declaration (transitively), else <code>null</code>.
   */
  @Nullable
  IXdmElementDeclaration getSubstitutionMember (@NonNull XdmQName aName);
}
