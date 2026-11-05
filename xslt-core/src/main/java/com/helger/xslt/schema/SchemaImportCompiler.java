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
package com.helger.xslt.schema;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.PresentForCodeCoverage;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.serialize.SerializationParams;
import com.helger.xpath.serialize.XdmSerializer;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.IXdmNode;
import com.helger.xpath.xdm.XdmConstants;
import com.helger.xpath.xdm.XdmParseException;
import com.helger.xpath.xdm.XdmParser;
import com.helger.xpath.xdm.schema.IXdmSchemaSet;
import com.helger.xpath.xdm.schema.XdmSchemaException;
import com.helger.xpath.xdm.schema.XdmSchemaSource;
import com.helger.xpath.xdm.schema.XdmSchemaSupport;
import com.helger.xslt.XsltCompileOptions;
import com.helger.xslt.XsltException;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.runtime.NodeCopier;
import com.helger.xslt.tree.XslElement;
import com.helger.xslt.tree.XslNode;

/**
 * xsl:import-schema (XSLT 3.0 section 3.15), the static half of
 * schema-awareness: the declarations of all modules are compiled into one
 * schema set before any expression is parsed, so that the type and
 * declaration names of expressions, patterns and <code>as</code> attributes
 * resolve against it. A port of go-xslt's <code>decl_import_schema.go</code>.
 *
 * @author Philip Helger
 */
@Immutable
public final class SchemaImportCompiler
{
  private static final String NS_XML = XdmConstants.NS_XML;

  /**
   * The four attributes of the XML namespace, which this processor knows
   * without a schema location (XSLT 3.0 section 3.15 allows components to be
   * known "implicitly from knowledge of the namespace").
   */
  private static final String XML_NAMESPACE_SCHEMA = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='" +
                                                     NS_XML +
                                                     "'>" +
                                                     "<xs:attribute name='lang'><xs:simpleType><xs:union memberTypes='xs:language'>" +
                                                     "<xs:simpleType><xs:restriction base='xs:string'><xs:enumeration value=''/></xs:restriction></xs:simpleType>" +
                                                     "</xs:union></xs:simpleType></xs:attribute>" +
                                                     "<xs:attribute name='space'><xs:simpleType><xs:restriction base='xs:NCName'>" +
                                                     "<xs:enumeration value='default'/><xs:enumeration value='preserve'/>" +
                                                     "</xs:restriction></xs:simpleType></xs:attribute>" +
                                                     "<xs:attribute name='base' type='xs:anyURI'/>" +
                                                     "<xs:attribute name='id' type='xs:ID'/>" +
                                                     "</xs:schema>";

  private record Decl (@NonNull XdmSchemaSource source, int precedence)
  {}

  @PresentForCodeCoverage
  private static final SchemaImportCompiler INSTANCE = new SchemaImportCompiler ();

  private SchemaImportCompiler ()
  {}

  /**
   * Parse a schema document (its inline text, else the local file its
   * system ID names) and read the <code>targetNamespace</code> attribute of
   * the document element.
   *
   * @return The target namespace of a schema document ("" for none), or
   *         <code>null</code> if it cannot be read: a parse error, a system
   *         ID that is not a file URI, or no document element.
   */
  @Nullable
  private static String _getTargetNamespace (@NonNull final XdmSchemaSource aSource)
  {
    try
    {
      final IXdmNode aDoc = aSource.text () != null ? new XdmParser ().parse (aSource.text ())
                                                   : new XdmParser ().parse (Path.of (new URI (aSource.systemId ())));
      for (final IXdmNode aChild : aDoc.getAllChildren ())
        if (aChild.isElement ())
        {
          final IXdmNode aTns = aChild.getAttribute ("", "targetNamespace");
          return aTns == null ? "" : aTns.getValue ().trim ();
        }
      return null;
    }
    catch (final XdmParseException | URISyntaxException | IllegalArgumentException ex)
    {
      return null;
    }
  }

  /**
   * Look up a schema the host registered in the compile options for a
   * namespace. A host schema with an explicit namespace matches by that
   * namespace; one without is parsed to compare its target namespace. The
   * first match in registration order wins.
   *
   * @param aOptions
   *        The compile options carrying the host schemas.
   * @param sNamespace
   *        The imported namespace, "" for no namespace.
   * @return A source referring to the host schema by system ID, or
   *         <code>null</code> if the host knows no schema for the namespace.
   */
  @Nullable
  private static XdmSchemaSource _getHostSchema (@NonNull final XsltCompileOptions aOptions, @NonNull final String sNamespace)
  {
    for (final XsltCompileOptions.HostSchema aHost : aOptions.hostSchemas ())
    {
      final XdmSchemaSource aSource = new XdmSchemaSource (aHost.systemId (), null);
      if (aHost.namespace () != null ? aHost.namespace ().equals (sNamespace)
                                     : sNamespace.equals (_getTargetNamespace (aSource)))
        return aSource;
    }
    return null;
  }

  /**
   * @param aEl
   *        The xsl:import-schema element.
   * @param aInline
   *        Its inline xs:schema child, or <code>null</code>.
   * @return The namespace the declaration imports: the trimmed
   *         <code>namespace</code> attribute, else the trimmed
   *         <code>targetNamespace</code> of the inline schema, else "" (no
   *         namespace).
   */
  @NonNull
  private static String _getImportedNamespace (@NonNull final XslElement aEl, @Nullable final XslElement aInline)
  {
    final String sNs = aEl.getAttr ("namespace");
    if (sNs != null)
      return sNs.trim ();
    if (aInline != null)
    {
      final String sTns = aInline.getAttr ("targetNamespace");
      if (sTns != null)
        return sTns.trim ();
    }
    return "";
  }

  /**
   * Serialise an inline xs:schema element (without XML declaration) so that
   * the schema processor can read it like a schema document. The copy
   * declares every namespace in scope on the stylesheet element, so prefixes
   * used in QName-valued attributes stay resolvable.
   *
   * @param aInline
   *        The xs:schema child of xsl:import-schema.
   * @return The serialised schema document.
   */
  @NonNull
  private static String _serializeInline (@NonNull final XslElement aInline)
  {
    // The copy declares all namespaces in scope, so that QName-valued
    // attributes of the schema keep their prefixes
    final IXdmNode aCopy = NodeCopier.copyDeep (aInline.getSource (), true);
    final SerializationParams aParams = new SerializationParams ();
    aParams.setOmitXmlDeclaration (true);
    return XdmSerializer.serialize (XdmSequence.of (aCopy), aParams);
  }

  /**
   * Determine the schema document of one xsl:import-schema declaration
   * (XSLT 3.0 section 3.15), in this order:
   * <ul>
   * <li>an inline xs:schema child, serialised, with the element's static
   * base URI as system ID;</li>
   * <li>without (or with a blank) <code>schema-location</code>: a host
   * schema for the namespace, else the built-in schema of the XML namespace
   * for that namespace, else nothing;</li>
   * <li>otherwise the location resolved against the static base URI. Only
   * existing local <code>file:</code> resources are read (no network
   * access). A location that cannot be read is only a hint and falls back to
   * a host schema; one whose target namespace differs from the imported
   * namespace also falls back to a host schema before it is an error.</li>
   * </ul>
   *
   * @param aEl
   *        The xsl:import-schema element.
   * @param aOptions
   *        The compile options (host schemas).
   * @return The schema document of one declaration, or <code>null</code> if it
   *         names none.
   * @throws XsltException
   *         XTSE0020 for a zero-length <code>namespace</code> attribute;
   *         XTSE0010 for a child that is not xs:schema or a second xs:schema
   *         child; XTSE0215 for an inline schema together with a
   *         <code>schema-location</code>, or a <code>namespace</code>
   *         attribute that differs from the inline schema's target namespace
   *         (also when the inline schema has none); XTSE0220 for a readable
   *         schema document whose target namespace differs from the
   *         imported namespace and no host schema for that namespace.
   */
  @Nullable
  private static XdmSchemaSource _getSource (@NonNull final XslElement aEl, @NonNull final XsltCompileOptions aOptions)
  {
    final String sNs = aEl.getAttr ("namespace");
    if (sNs != null && sNs.trim ().isEmpty ())
      throw XsltException.at (aEl, "XTSE0020", "The namespace attribute of xsl:import-schema must not be a zero-length string");
    XslElement aInline = null;
    for (final XslNode aChild : aEl.getChildren ())
      if (aChild instanceof final XslElement aChildEl)
      {
        if (!aChildEl.getName ().isSameName (XdmConstants.NS_XS, "schema") || aInline != null)
          throw XsltException.at (aChildEl, "XTSE0010", "Only a single xs:schema element is allowed in xsl:import-schema");
        aInline = aChildEl;
      }
    final String sLocation = aEl.getAttr ("schema-location");
    final String sNamespace = _getImportedNamespace (aEl, aInline);
    if (aInline != null)
    {
      if (sLocation != null)
        throw XsltException.at (aEl, "XTSE0215", "xsl:import-schema with an inline xs:schema must not have a schema-location");
      final String sTns = aInline.getAttr ("targetNamespace");
      if (sNs != null && (sTns == null || !sTns.trim ().equals (sNs.trim ())))
        throw XsltException.at (aEl, "XTSE0215", "The namespace attribute conflicts with the target namespace of the inline schema");
      return new XdmSchemaSource (aEl.getStaticBaseUri (), _serializeInline (aInline));
    }
    if (sLocation == null || sLocation.isBlank ())
    {
      final XdmSchemaSource aHost = _getHostSchema (aOptions, sNamespace);
      if (aHost != null)
        return aHost;
      if (NS_XML.equals (sNamespace))
        return new XdmSchemaSource (null, XML_NAMESPACE_SCHEMA);
      // A namespace-only declaration without a schema document is not an
      // error in itself
      return null;
    }
    String sUri = sLocation.trim ();
    try
    {
      final String sBase = aEl.getStaticBaseUri ();
      if (sBase != null)
        sUri = new URI (sBase).resolve (new URI (sUri)).toString ();
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      // keep it as written
    }
    final XdmSchemaSource aSource = new XdmSchemaSource (sUri, null);
    final boolean bReadable;
    try
    {
      // No network access: only local files are read
      bReadable = sUri.startsWith ("file:") && Files.isRegularFile (Path.of (new URI (sUri)));
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      return _getHostSchema (aOptions, sNamespace);
    }
    final String sTns = bReadable ? _getTargetNamespace (aSource) : null;
    if (sTns == null)
    {
      // The location is a hint: one that leads nowhere contributes nothing,
      // but the host may know the namespace
      return _getHostSchema (aOptions, sNamespace);
    }
    if (!sTns.equals (sNamespace))
    {
      final XdmSchemaSource aHost = _getHostSchema (aOptions, sNamespace);
      if (aHost != null)
        return aHost;
      throw XsltException.at (aEl,
                              "XTSE0220",
                              "xsl:import-schema names namespace '" +
                                          sNamespace +
                                          "' but " +
                                          sLocation +
                                          " has target namespace '" +
                                          sTns +
                                          "'");
    }
    return aSource;
  }

  /**
   * Of several schema documents for the same target namespace a schema
   * processor reads only the first (the namespace is then known). Inline
   * schemas of the same target namespace (e.g. no-namespace schemas of
   * different packages) are therefore merged into one document.
   * <p>
   * The merged document is a shallow copy of the first schema's root with its
   * namespaces and attributes; the namespaces of the following roots are
   * declared on it where the prefix is still free, and the element children
   * of all roots are appended in order. Attributes of the following roots
   * (e.g. <code>elementFormDefault</code>) are not taken over. The merged
   * source replaces the group in the list at the position of its first
   * member and keeps that member's system ID. Sources read from a location
   * are never merged; a parse error leaves the list as it is for the
   * schema processor to report.
   *
   * @param aSources
   *        The schema documents to compile, modified in place.
   */
  private static void _mergeInlineSchemas (@NonNull final ICommonsList <XdmSchemaSource> aSources)
  {
    final ICommonsOrderedMap <String, ICommonsList <XdmSchemaSource>> aByNamespace = new CommonsLinkedHashMap <> ();
    for (final XdmSchemaSource aSource : aSources)
      if (aSource.text () != null)
      {
        final String sTns = _getTargetNamespace (aSource);
        if (sTns != null)
          aByNamespace.computeIfAbsent (sTns, k -> new CommonsArrayList <> ()).add (aSource);
      }
    for (final ICommonsList <XdmSchemaSource> aGroup : aByNamespace.values ())
      if (aGroup.size () > 1)
        try
        {
          IXdmNode aMerged = null;
          for (final XdmSchemaSource aSource : aGroup)
          {
            IXdmNode aRoot = null;
            for (final IXdmNode aChild : new XdmParser ().parse (aSource.text ()).getAllChildren ())
              if (aChild.isElement ())
                aRoot = aChild;
            if (aRoot == null)
              return;
            if (aMerged == null)
            {
              aMerged = NodeCopier.copyShallow (aRoot);
              NodeCopier.copyNamespaces (aRoot, aMerged, true);
              for (final IXdmNode aAttr : aRoot.getAllAttributes ())
                aMerged.addAttribute (NodeCopier.createAttribute (aAttr.getName (), aAttr.getValue ()));
            }
            else
              for (final Map.Entry <String, String> aNs : aRoot.getInScopeNamespaces ().entrySet ())
                if (aMerged.getDeclaredNamespaces ().get (aNs.getKey ()) == null && !"xml".equals (aNs.getKey ()))
                  aMerged.declareNamespace (aNs.getKey (), aNs.getValue ());
            for (final IXdmNode aChild : aRoot.getAllChildren ())
              if (aChild.isElement ())
                aMerged.appendChild (NodeCopier.copyDeep (aChild, true));
          }
          final SerializationParams aParams = new SerializationParams ();
          aParams.setOmitXmlDeclaration (true);
          final XdmSchemaSource aNew = new XdmSchemaSource (aGroup.getFirstOrNull ().systemId (),
                                                            XdmSerializer.serialize (XdmSequence.of (aMerged), aParams));
          final int nIndex = aSources.indexOf (aGroup.getFirstOrNull ());
          aSources.removeAll (aGroup);
          aSources.add (Math.min (nIndex, aSources.size ()), aNew);
        }
        catch (final XdmParseException ex)
        {
          // Leave it to the schema processor to report
        }
  }

  /**
   * Compile the schema of a schema-aware stylesheet. The declarations are
   * grouped by package and imported namespace; within a group only those
   * with the highest import precedence are used (XSLT 3.0 section 3.15), and
   * the same document (same inline text or same system ID) contributes only
   * once. Inline schemas of one target namespace are merged before the
   * whole set is compiled together.
   *
   * @param aDeclarations
   *        All top-level declarations in import precedence order (lowest
   *        first).
   * @param aPackages
   *        The package model (the precedence rule is applied per package).
   * @param aOptions
   *        The compile options (host schemas).
   * @return The in-scope schema definitions: the imported schemas, or only the
   *         built-in types if no declaration names a schema document.
   * @throws XsltException
   *         the errors of a single declaration (see
   *         {@link #_getSource(XslElement, XsltCompileOptions)}), and
   *         XTSE0220, reported at the first xsl:import-schema, if the schema
   *         processor rejects the collected documents.
   */
  @NonNull
  public static IXdmSchemaSet compile (@NonNull final Iterable <XslElement> aDeclarations,
                                       @NonNull final PackageModel aPackages,
                                       @NonNull final XsltCompileOptions aOptions)
  {
    // Section 3.15: of several declarations for the same namespace only those
    // with the highest import precedence are used (per package)
    final ICommonsOrderedMap <String, ICommonsList <Decl>> aByNamespace = new CommonsLinkedHashMap <> ();
    XslElement aFirst = null;
    for (final XslElement aEl : aDeclarations)
    {
      if (!aEl.isXsl ("import-schema"))
        continue;
      if (aFirst == null)
        aFirst = aEl;
      final XdmSchemaSource aSource = _getSource (aEl, aOptions);
      if (aSource == null)
        continue;
      XslElement aInline = null;
      for (final XslElement aChild : aEl.getElementChildren ())
        aInline = aChild;
      final String sKey = System.identityHashCode (aPackages.getUnit (aEl)) + "\u0000" + _getImportedNamespace (aEl, aInline);
      aByNamespace.computeIfAbsent (sKey, k -> new CommonsArrayList <> ()).add (new Decl (aSource, aEl.getPrecedence ()));
    }
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> ();
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    for (final ICommonsList <Decl> aDecls : aByNamespace.values ())
    {
      int nTop = Integer.MIN_VALUE;
      for (final Decl aDecl : aDecls)
        nTop = Math.max (nTop, aDecl.precedence ());
      for (final Decl aDecl : aDecls)
      {
        if (aDecl.precedence () != nTop)
          continue;
        final XdmSchemaSource aSource = aDecl.source ();
        // The same document imported twice contributes its components once
        if (aSeen.add (aSource.text () != null ? "T" + aSource.text () : "U" + aSource.systemId ()))
          aSources.add (aSource);
      }
    }
    if (aSources.isEmpty ())
      return XdmSchemaSupport.getBuiltinSchemaSet ();
    _mergeInlineSchemas (aSources);
    try
    {
      return XdmSchemaSupport.compile (aSources);
    }
    catch (final XdmSchemaException ex)
    {
      throw XsltException.at (aFirst, "XTSE0220", "The imported schema is not valid: " + ex.getMessage ());
    }
  }
}
