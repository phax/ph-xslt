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
package com.helger.xpath.fn;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.xpath.XPathException;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.context.DynamicContext;
import com.helger.xpath.context.StaticContext;
import com.helger.xpath.eval.SequenceTypeMatcher;
import com.helger.xpath.parser.XPathParser;
import com.helger.xpath.value.XdmAtomic;
import com.helger.xpath.value.XdmMap;
import com.helger.xpath.value.XdmSequence;

/**
 * Reads option maps by the option parameter conventions of F&amp;O 3.1 section
 * 1.5: an option value is converted to the required type with the function
 * conversion rules, a type mismatch is XPTY0004, and options the function
 * does not know are ignored.
 *
 * @author Philip Helger
 */
@Immutable
public final class FnOptionMap
{
  public static final SeqType STRING = _type ("xs:string");
  public static final SeqType BOOLEAN = _type ("xs:boolean");
  public static final SeqType FUNCTION_1 = _type ("function(xs:string) as xs:string");

  @PresentForCodeCoverage
  private static final FnOptionMap INSTANCE = new FnOptionMap ();

  private FnOptionMap ()
  {}

  @NonNull
  private static SeqType _type (@NonNull final String sType)
  {
    return XPathParser.parseSequenceType (sType, new StaticContext ());
  }

  /**
   * @param aOptions
   *        The option map.
   * @param sName
   *        The option name (a string key).
   * @param aType
   *        The required type of the value.
   * @param aCtx
   *        The dynamic context for the conversion.
   * @return <code>null</code> if the option is absent, the converted value
   *         otherwise.
   * @throws XPathException
   *         XPTY0004 if the value does not match the type.
   */
  @Nullable
  public static XdmSequence getOption (@NonNull final XdmMap aOptions,
                                       @NonNull final String sName,
                                       @NonNull final SeqType aType,
                                       @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = aOptions.get (XdmAtomic.string (sName));
    if (aValue == null)
      return null;
    return SequenceTypeMatcher.convert (aValue, aType, aCtx, "XPTY0004");
  }

  /**
   * @param aOptions
   *        The option map.
   * @param sName
   *        The option name.
   * @param bDefault
   *        The value if the option is absent.
   * @param aCtx
   *        The dynamic context.
   * @return The boolean value of the option.
   */
  public static boolean getBoolean (@NonNull final XdmMap aOptions,
                                    @NonNull final String sName,
                                    final boolean bDefault,
                                    @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = getOption (aOptions, sName, BOOLEAN, aCtx);
    return aValue == null ? bDefault : ((XdmAtomic) aValue.get (0)).getBoolean ();
  }

  /**
   * @param aOptions
   *        The option map.
   * @param sName
   *        The option name.
   * @param sDefault
   *        The value if the option is absent.
   * @param aCtx
   *        The dynamic context.
   * @return The string value of the option.
   */
  @Nullable
  public static String getString (@NonNull final XdmMap aOptions,
                                  @NonNull final String sName,
                                  @Nullable final String sDefault,
                                  @NonNull final DynamicContext aCtx)
  {
    final XdmSequence aValue = getOption (aOptions, sName, STRING, aCtx);
    return aValue == null ? sDefault : ((XdmAtomic) aValue.get (0)).getString ();
  }
}
