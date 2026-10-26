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
package com.helger.xpath.xdm.jaxp;

import java.io.StringReader;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jspecify.annotations.NonNull;
import org.xml.sax.SAXException;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.xpath.xdm.schema.IXdmSchemaProvider;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;

/**
 * Schema provider on the JDK's own validator (always the JDK's, also when
 * another JAXP implementation is on the class path). It needs no further
 * library, but it only validates: the schema sets it compiles have no schema
 * component model, so they are enough for schema-validated input documents
 * (typed values of built-in types, <code>fn:nilled</code>, schema IDs,
 * <code>element(N, T)</code> tests by type name), not for schema-awareness of
 * XSLT or for <code>schema-element()</code>. Unlike the Xerces-J provider of
 * {@link com.helger.xpath.xdm.schema.XdmSchemaSupport} it is never chosen
 * automatically.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class JdkSchemaProvider implements IXdmSchemaProvider
{
  private static final String EMPTY_SCHEMA = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>";

  public JdkSchemaProvider ()
  {}

  /**
   * Compile schema documents with the JDK schema factory, with its default
   * settings.
   *
   * @param aSources
   *        The schema documents. May be empty for the built-in types only.
   * @return The compiled schema set.
   * @throws XdmSchemaException
   *         if the documents cannot be read or are not a valid schema
   */
  @NonNull
  public IXdmSchemaSet compile (@NonNull final List <XdmSchemaSource> aSources) throws XdmSchemaException
  {
    ValueEnforcer.notNull (aSources, "Sources");
    final Source [] aJaxpSources;
    if (aSources.isEmpty ())
      aJaxpSources = new Source [] { new StreamSource (new StringReader (EMPTY_SCHEMA)) };
    else
    {
      aJaxpSources = new Source [aSources.size ()];
      for (int i = 0; i < aJaxpSources.length; ++i)
      {
        final XdmSchemaSource aSource = aSources.get (i);
        final StreamSource aSS = aSource.text () != null ? new StreamSource (new StringReader (aSource.text ()))
                                                         : new StreamSource ();
        aSS.setSystemId (aSource.systemId ());
        aJaxpSources[i] = aSS;
      }
    }
    final Schema aSchema;
    try
    {
      aSchema = SchemaFactory.newDefaultInstance ().newSchema (aJaxpSources);
    }
    catch (final SAXException ex)
    {
      throw new XdmSchemaException ("Invalid schema: " + ex.getMessage (), ex);
    }
    return new JdkSchemaSet (aSchema);
  }
}
