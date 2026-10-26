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
package com.helger.xsd.conformance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test class for class {@link XercesXsdTestValidator}.
 *
 * @author Philip Helger
 */
public final class XercesXsdTestValidatorTest
{
  /** A port nobody listens on: a request that slipped through fails instead of hanging. */
  private static final String REMOTE = "http://127.0.0.1:9/";

  private static final String SCHEMA = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:include schemaLocation="inc.xsd"/>
        <xs:element name="r" type="T"/>
      </xs:schema>
      """;

  private static final String INCLUDED = """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:simpleType name="T">
          <xs:restriction base="xs:int"><xs:maxInclusive value="10"/></xs:restriction>
        </xs:simpleType>
      </xs:schema>
      """;

  @Rule
  public final TemporaryFolder m_aTmp = new TemporaryFolder ();

  @NonNull
  private Path _write (@NonNull final String sName, @NonNull final String sContent) throws IOException
  {
    final Path ret = m_aTmp.getRoot ().toPath ().resolve (sName);
    Files.writeString (ret, sContent, StandardCharsets.UTF_8);
    return ret;
  }

  @Test
  public void testCompileAndValidate () throws IOException
  {
    final Path aSchema = _write ("s.xsd", SCHEMA);
    _write ("inc.xsd", INCLUDED);
    final XercesXsdTestValidator aValidator = new XercesXsdTestValidator ();
    assertTrue (aValidator.getDisplayName ().contains ("2.12.2"));

    final XsdCompileResult aCompiled = aValidator.compile (List.of (aSchema), EXsdVersion.XSD_10);
    assertEquals (EXsdVerdict.VALID, aCompiled.verdict ().verdict ());
    final IXsdTestSchema aCompiledSchema = aCompiled.schema ();
    assertNotNull (aCompiledSchema);
    assertEquals (EXsdVerdict.VALID, aCompiledSchema.validate (_write ("v.xml", "<r>7</r>")).verdict ());
    assertEquals (EXsdVerdict.INVALID, aCompiledSchema.validate (_write ("i1.xml", "<r>11</r>")).verdict ());
    // Undeclared document element
    assertEquals (EXsdVerdict.INVALID, aCompiledSchema.validate (_write ("i2.xml", "<x>1</x>")).verdict ());
    // Not well-formed
    assertEquals (EXsdVerdict.INVALID, aCompiledSchema.validate (_write ("i3.xml", "<r>1</x>")).verdict ());
    assertTrue (aValidator.getResourceResolver ().getBlocked ().isEmpty ());
  }

  @Test
  public void testInvalidSchema () throws IOException
  {
    final Path aSchema = _write ("bad.xsd", """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="r" type="Undefined"/>
        </xs:schema>
        """);
    final XsdCompileResult aCompiled = new XercesXsdTestValidator ().compile (List.of (aSchema), EXsdVersion.XSD_10);
    assertEquals (EXsdVerdict.INVALID, aCompiled.verdict ().verdict ());
    assertNotNull (aCompiled.verdict ().message ());
    assertNull (aCompiled.schema ());
  }

  @Test
  public void testXsd11Unsupported () throws IOException
  {
    final Path aSchema = _write ("s.xsd", SCHEMA);
    _write ("inc.xsd", INCLUDED);
    final XercesXsdTestValidator aValidator = new XercesXsdTestValidator ();
    assertTrue (aValidator.isVersionSupported (EXsdVersion.XSD_10));
    assertFalse (aValidator.isVersionSupported (EXsdVersion.XSD_11));
    assertEquals (EXsdVerdict.UNSUPPORTED,
                  aValidator.compile (List.of (aSchema), EXsdVersion.XSD_11).verdict ().verdict ());
    assertEquals (EXsdVerdict.UNSUPPORTED, aValidator.compile (List.of (), EXsdVersion.XSD_11).verdict ().verdict ());
  }

  @Test
  public void testSchemaHintsOnly () throws IOException
  {
    _write ("s.xsd", SCHEMA);
    _write ("inc.xsd", INCLUDED);
    final XercesXsdTestValidator aValidator = new XercesXsdTestValidator ();
    final IXsdTestSchema aSchema = aValidator.compile (List.of (), EXsdVersion.XSD_10).schema ();
    assertNotNull (aSchema);
    final String sHint = "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:noNamespaceSchemaLocation=";
    assertEquals (EXsdVerdict.VALID, aSchema.validate (_write ("v.xml", "<r " + sHint + "'s.xsd'>3</r>")).verdict ());
    assertEquals (EXsdVerdict.INVALID, aSchema.validate (_write ("i.xml", "<r " + sHint + "'s.xsd'>30</r>")).verdict ());
    // Without hint nothing declares the element
    assertEquals (EXsdVerdict.INVALID, aSchema.validate (_write ("n.xml", "<r>3</r>")).verdict ());
  }

  @Test
  public void testNoNetworkForSchemaReferences () throws IOException
  {
    final Path aSchema = _write ("net.xsd", """
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:x="urn:x">
          <xs:import namespace="urn:x" schemaLocation="%sx.xsd"/>
          <xs:element name="r" type="x:T"/>
        </xs:schema>
        """.formatted (REMOTE));
    final XercesXsdTestValidator aValidator = new XercesXsdTestValidator ();
    final XsdCompileResult aCompiled = aValidator.compile (List.of (aSchema), EXsdVersion.XSD_10);
    // The import cannot be read, so x:T is unresolved
    assertEquals (EXsdVerdict.INVALID, aCompiled.verdict ().verdict ());
    assertEquals (List.of (REMOTE + "x.xsd"), aValidator.getResourceResolver ().getBlocked ());
  }

  @Test
  public void testNoNetworkForInstanceReferences () throws IOException
  {
    final XercesXsdTestValidator aValidator = new XercesXsdTestValidator ();
    final IXsdTestSchema aSchema = aValidator.compile (List.of (), EXsdVersion.XSD_10).schema ();
    assertNotNull (aSchema);
    assertEquals (EXsdVerdict.INVALID,
                  aSchema.validate (_write ("h.xml",
                                            "<r xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'" +
                                                     " xsi:noNamespaceSchemaLocation='" +
                                                     REMOTE +
                                                     "r.xsd'>3</r>"))
                         .verdict ());
    assertEquals (EXsdVerdict.INVALID,
                  aSchema.validate (_write ("d.xml", "<!DOCTYPE r SYSTEM '" + REMOTE + "r.dtd'><r>3</r>")).verdict ());
    assertEquals (List.of (REMOTE + "r.xsd", REMOTE + "r.dtd"), aValidator.getResourceResolver ().getBlocked ());
  }
}
