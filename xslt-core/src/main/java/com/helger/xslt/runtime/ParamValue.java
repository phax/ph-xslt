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
package com.helger.xslt.runtime;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.model.VariableDef;

/**
 * An evaluated xsl:with-param.
 *
 * @param name
 *        Parameter name.
 * @param value
 *        Value.
 * @param tunnel
 *        <code>true</code> for a tunnel parameter.
 * @author Philip Helger
 */
public record ParamValue (@NonNull XdmQName name, @NonNull XdmSequence value, boolean tunnel)
{
  /**
   * Evaluate xsl:with-param definitions in the context of the calling
   * instruction.
   *
   * @param aDefs
   *        The definitions.
   * @param aCtx
   *        The caller's context.
   * @return The values.
   */
  @NonNull
  public static ICommonsList <ParamValue> evaluate (@NonNull final List <VariableDef> aDefs, @NonNull final XsltContext aCtx)
  {
    final ICommonsList <ParamValue> ret = new CommonsArrayList <> (aDefs.size ());
    for (final VariableDef aDef : aDefs)
      ret.add (new ParamValue (aDef.getName (), aDef.evaluate (aCtx), aDef.isTunnel ()));
    return ret;
  }
}
