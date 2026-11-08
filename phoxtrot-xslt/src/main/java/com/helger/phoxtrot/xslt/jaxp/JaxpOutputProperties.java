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

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.base.array.ArrayHelper;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xslt.model.OutputDefinition;

/**
 * The JAXP output properties: the names accepted, the properties of a
 * stylesheet and their defaults, and the conversion of the properties set on
 * a transformer to the serialization parameters of the request.
 * <p>
 * The accepted names are those of {@link OutputKeys} and the other
 * serialization attributes of XSLT 3.0 <code>xsl:output</code> (such as
 * <code>html-version</code>, <code>item-separator</code>,
 * <code>suppress-indentation</code>), except
 * <code>use-character-maps</code>, which names character maps by QNames of
 * the stylesheet. Names in a namespace (<code>{uri}local</code>, e.g. the
 * Xalan <code>indent-amount</code>) are accepted and kept but have no effect.
 * Name lists (<code>cdata-section-elements</code>,
 * <code>suppress-indentation</code>) use the JAXP form
 * <code>{uri}local</code> for names in a namespace.
 *
 * @author Philip Helger
 */
@Immutable
final class JaxpOutputProperties
{
  @PresentForCodeCoverage
  private static final JaxpOutputProperties INSTANCE = new JaxpOutputProperties ();

  private JaxpOutputProperties ()
  {}

  /**
   * @return <code>true</code> for a name in a namespace, written
   *         <code>{uri}local</code>.
   */
  static boolean isNamespaced (@NonNull final String sName)
  {
    return sName.startsWith ("{");
  }

  /**
   * @throws NullPointerException
   *         for a <code>null</code> name
   * @throws IllegalArgumentException
   *         for a name that is neither a supported serialization parameter
   *         nor in a namespace
   */
  static void checkName (@NonNull final String sName)
  {
    if (sName == null)
      throw new NullPointerException ("Output property name");
    if (isNamespaced (sName))
      return;
    if ("use-character-maps".equals (sName) || !ArrayHelper.contains (OutputDefinition.ATTRIBUTES, sName))
      throw new IllegalArgumentException ("The output property '" + sName + "' is not supported");
  }

  /**
   * @return The JAXP form of an expanded name: <code>{uri}local</code>, or
   *         the local name in no namespace.
   */
  @NonNull
  private static String _getJaxpName (@NonNull final XdmQName aName)
  {
    return aName.hasNamespace () ? "{" + aName.namespaceUri () + "}" + aName.localName () : aName.localName ();
  }

  @NonNull
  private static String _getJaxpNames (@NonNull final List <XdmQName> aNames)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final XdmQName aName : aNames)
    {
      if (!aSB.isEmpty ())
        aSB.append (' ');
      aSB.append (_getJaxpName (aName));
    }
    return aSB.toString ();
  }

  /**
   * @return The defaults the engine serializes with when the stylesheet does
   *         not say otherwise, for the method (the xml method if
   *         <code>null</code>): method, encoding UTF-8, indent (yes for html
   *         and xhtml), omit-xml-declaration no, and the version (1.0 for xml
   *         and xhtml, 5.0 for html).
   */
  @NonNull
  static Properties createDefaults (@Nullable final String sMethod)
  {
    final String sEffective = sMethod != null ? sMethod : SerializationParams.METHOD_XML;
    final boolean bHtml = SerializationParams.METHOD_HTML.equals (sEffective);
    final boolean bXhtml = SerializationParams.METHOD_XHTML.equals (sEffective);
    final Properties ret = new Properties ();
    ret.setProperty (OutputKeys.METHOD, sEffective);
    ret.setProperty (OutputKeys.ENCODING, "UTF-8");
    ret.setProperty (OutputKeys.INDENT, bHtml || bXhtml ? "yes" : "no");
    ret.setProperty (OutputKeys.OMIT_XML_DECLARATION, "no");
    if (bHtml)
      ret.setProperty (OutputKeys.VERSION, "5.0");
    else
      if (bXhtml || SerializationParams.METHOD_XML.equals (sEffective))
        ret.setProperty (OutputKeys.VERSION, "1.0");
    return ret;
  }

  /**
   * The output properties a stylesheet declares: the values of its unnamed
   * output definition (a method EQName <code>Q{uri}local</code> in the JAXP
   * form <code>{uri}local</code>, the name lists as JAXP names).
   *
   * @param aOutputDef
   *        The unnamed output definition, <code>null</code> if unknown.
   * @return New properties without defaults. Never <code>null</code>.
   */
  @NonNull
  static Properties fromDefinition (@Nullable final OutputDefinition aOutputDef)
  {
    final Properties ret = new Properties ();
    if (aOutputDef != null)
    {
      for (final Map.Entry <String, String> aEntry : aOutputDef.getAllValues ().entrySet ())
      {
        String sValue = aEntry.getValue ();
        if (OutputKeys.METHOD.equals (aEntry.getKey ()) && sValue.startsWith ("Q{"))
          sValue = sValue.substring (1);
        ret.setProperty (aEntry.getKey (), sValue);
      }
      if (aOutputDef.getCdataSectionElements ().isNotEmpty ())
        ret.setProperty (OutputKeys.CDATA_SECTION_ELEMENTS, _getJaxpNames (aOutputDef.getCdataSectionElements ()));
      if (aOutputDef.getSuppressIndentation ().isNotEmpty ())
        ret.setProperty ("suppress-indentation", _getJaxpNames (aOutputDef.getSuppressIndentation ()));
    }
    return ret;
  }

  /**
   * @param aProperties
   *        The properties set on a transformer.
   * @return The serialization parameters to supply with the request: all of
   *         them except the names in a namespace.
   */
  @NonNull
  static ICommonsOrderedMap <String, String> toOverrides (@NonNull final Properties aProperties)
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    for (final String sName : aProperties.stringPropertyNames ())
      if (!isNamespaced (sName))
        ret.put (sName, aProperties.getProperty (sName));
    return ret;
  }
}
