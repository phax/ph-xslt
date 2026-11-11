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
package com.helger.phoxtrot.xslt.schema;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
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
import com.helger.phoxtrot.xdm.IXdmNode;
import com.helger.phoxtrot.xdm.XdmConstants;
import com.helger.phoxtrot.xdm.XdmParseException;
import com.helger.phoxtrot.xdm.XdmParser;
import com.helger.phoxtrot.xdm.XdmQName;
import com.helger.phoxtrot.xdm.schema.IXdmSchemaSet;
import com.helger.phoxtrot.xdm.schema.XdmSchemaException;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSource;
import com.helger.phoxtrot.xdm.schema.XdmSchemaSupport;
import com.helger.phoxtrot.xpath.XPathException;
import com.helger.phoxtrot.xpath.context.IResourceResolver;
import com.helger.phoxtrot.xpath.context.ResourceUri;
import com.helger.phoxtrot.xpath.serialize.SerializationParams;
import com.helger.phoxtrot.xpath.serialize.XdmSerializer;
import com.helger.phoxtrot.xpath.value.XdmSequence;
import com.helger.phoxtrot.xslt.XsltCompileOptions;
import com.helger.phoxtrot.xslt.XsltException;
import com.helger.phoxtrot.xslt.pkg.PackageModel;
import com.helger.phoxtrot.xslt.pkg.PackageUnit;
import com.helger.phoxtrot.xslt.runtime.NodeCopier;
import com.helger.phoxtrot.xslt.tree.XslElement;
import com.helger.phoxtrot.xslt.tree.XslNode;

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

  /** The top-level children of xs:schema that must precede all others. */
  private static final String [] COMPOSITION_ELEMENTS = { "include", "import", "redefine", "override" };

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
      final IXdmNode aDoc = aSource.text () != null ? new XdmParser ().setRecordLocations (true).parse (aSource.text ())
                                                   : new XdmParser ().setRecordLocations (true).parse (Path.of (new URI (aSource.systemId ())));
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
   * Read the schema document a <code>schema-location</code> names: through
   * the resolver of the compile options if there is one (as text, so that the
   * schema processor does not read it again; in JAXP this applies the URI
   * resolver and the external access settings), otherwise from the local file
   * if {@link XsltCompileOptions#isReadLocalSchemaFiles()} allows it. The
   * network is only used if the resolver does.
   *
   * @param aUri
   *        The location: resolved, as written, and its base URI.
   * @param aOptions
   *        The compile options.
   * @return The source, or <code>null</code> if the document cannot be read
   *         (a resolver that refuses included).
   */
  @Nullable
  private static XdmSchemaSource _readLocation (@NonNull final ResourceUri aUri, @NonNull final XsltCompileOptions aOptions)
  {
    final String sUri = aUri.absoluteUri ();
    final IResourceResolver aResolver = aOptions.getResolver ();
    if (aResolver != null)
    {
      try
      {
        final IXdmNode aDoc = aResolver.resolveDocument (aUri);
        if (aDoc == null)
          return null;
        final SerializationParams aParams = new SerializationParams ();
        aParams.setOmitXmlDeclaration (true);
        return new XdmSchemaSource (sUri, XdmSerializer.serialize (XdmSequence.of (aDoc), aParams));
      }
      catch (final XPathException ex)
      {
        // A resolver that refuses: unavailable, like a missing file
        return null;
      }
    }
    if (!aOptions.isReadLocalSchemaFiles ())
      return null;
    try
    {
      // No network access: only local files are read
      return sUri.startsWith ("file:") && Files.isRegularFile (Path.of (new URI (sUri))) ? new XdmSchemaSource (sUri, null)
                                                                                          : null;
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      return null;
    }
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
   * <li>otherwise the location resolved against the static base URI, read
   * by {@link #_readLocation(String, XsltCompileOptions)}. A location that
   * cannot be read is only a hint and falls back to
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
    final String sBase = aEl.getStaticBaseUri ();
    try
    {
      if (sBase != null)
        sUri = new URI (sBase).resolve (new URI (sUri)).toString ();
    }
    catch (final URISyntaxException | IllegalArgumentException ex)
    {
      // keep it as written
    }
    final XdmSchemaSource aSource = _readLocation (ResourceUri.of (sUri, sLocation.trim (), sBase), aOptions);
    final String sTns = aSource != null ? _getTargetNamespace (aSource) : null;
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
   * @param aNode
   *        A node.
   * @return <code>true</code> if it is an xs:include, xs:import, xs:redefine
   *         or xs:override element.
   */
  private static boolean _isComposition (@NonNull final IXdmNode aNode)
  {
    for (final String sLocalName : COMPOSITION_ELEMENTS)
      if (aNode.getName ().isSameName (XdmConstants.NS_XS, sLocalName))
        return true;
    return false;
  }

  /**
   * @param aRoot
   *        An xs:schema element.
   * @param sAttr
   *        "elementFormDefault" or "attributeFormDefault".
   * @return The trimmed value of the attribute, "unqualified" if it is absent.
   */
  @NonNull
  private static String _getFormDefault (@NonNull final IXdmNode aRoot, @NonNull final String sAttr)
  {
    final IXdmNode aAttr = aRoot.getAttribute ("", sAttr);
    return aAttr == null ? "unqualified" : aAttr.getValue ().trim ();
  }

  /**
   * Give every local element (or attribute) declaration below a top-level
   * child of a schema document that has neither a <code>ref</code> nor a
   * <code>form</code> attribute an explicit <code>form</code>, so that it
   * keeps the form default of its own schema document when it is moved into
   * a document with another default. A declaration is local unless its
   * parent is xs:schema, xs:redefine or xs:override. The walk is iterative.
   *
   * @param aTopLevel
   *        A copied top-level child of xs:schema; modified in place.
   * @param sLocalName
   *        "element" or "attribute".
   * @param sForm
   *        The form to set: "qualified" or "unqualified".
   */
  private static void _setLocalForms (@NonNull final IXdmNode aTopLevel,
                                      @NonNull final String sLocalName,
                                      @NonNull final String sForm)
  {
    final ArrayDeque <IXdmNode> aStack = new ArrayDeque <> ();
    for (final IXdmNode aChild : aTopLevel.getAllChildren ())
      if (aChild.isElement ())
        aStack.push (aChild);
    while (!aStack.isEmpty ())
    {
      final IXdmNode aNode = aStack.pop ();
      if (aNode.getName ().isSameName (XdmConstants.NS_XS, sLocalName) &&
          aNode.getAttribute ("", "ref") == null &&
          aNode.getAttribute ("", "form") == null)
        aNode.addAttribute (NodeCopier.createAttribute (new XdmQName ("form"), sForm));
      for (final IXdmNode aChild : aNode.getAllChildren ())
        if (aChild.isElement ())
          aStack.push (aChild);
    }
  }

  /**
   * Copy a top-level child of a schema document for the merged document. If
   * the element or attribute form default of its schema document differs
   * from that of the merged document, the local declarations below it get
   * an explicit <code>form</code> (see {@link #_setLocalForms}). Top-level
   * xs:redefine and xs:override hold top-level declarations, so their
   * children are treated like top-level children.
   *
   * @param aChild
   *        The top-level child.
   * @param aRoot
   *        Its xs:schema element.
   * @param aMerged
   *        The root of the merged document.
   * @return The copy.
   */
  @NonNull
  private static IXdmNode _copyTopLevel (@NonNull final IXdmNode aChild,
                                         @NonNull final IXdmNode aRoot,
                                         @NonNull final IXdmNode aMerged)
  {
    final IXdmNode aCopy = NodeCopier.copyDeep (aChild, true);
    final boolean bContainer = aCopy.getName ().isSameName (XdmConstants.NS_XS, "redefine") ||
                               aCopy.getName ().isSameName (XdmConstants.NS_XS, "override");
    for (final String sKind : new String [] { "element", "attribute" })
    {
      final String sAttr = sKind + "FormDefault";
      final String sForm = _getFormDefault (aRoot, sAttr);
      if (!sForm.equals (_getFormDefault (aMerged, sAttr)))
      {
        if (bContainer)
        {
          for (final IXdmNode aGrandChild : aCopy.getAllChildren ())
            if (aGrandChild.isElement ())
              _setLocalForms (aGrandChild, sKind, sForm);
        }
        else
          _setLocalForms (aCopy, sKind, sForm);
      }
    }
    return aCopy;
  }

  /**
   * Of several schema documents for the same target namespace a schema
   * processor reads only the first (the namespace is then known). Inline
   * schemas of the same target namespace (e.g. no-namespace schemas of
   * different packages) are therefore merged into one document.
   * <p>
   * The merged document is a shallow copy of the first schema's root with its
   * namespaces and attributes; the namespaces of the following roots are
   * declared on it where the prefix is still free. The xs:include,
   * xs:import, xs:redefine and xs:override children of all roots come first
   * (XML Schema requires them before the declarations), followed by the
   * other element children of all roots, each group in order. A local
   * declaration of a following root whose <code>elementFormDefault</code> or
   * <code>attributeFormDefault</code> differs from the first root's gets an
   * explicit <code>form</code> (see {@link #_copyTopLevel}). Other attributes
   * of the following roots (<code>blockDefault</code>,
   * <code>finalDefault</code>, <code>defaultAttributes</code>,
   * <code>xpathDefaultNamespace</code>) are not taken over. The merged
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
          final ICommonsList <IXdmNode> aCompositions = new CommonsArrayList <> ();
          final ICommonsList <IXdmNode> aOthers = new CommonsArrayList <> ();
          for (final XdmSchemaSource aSource : aGroup)
          {
            IXdmNode aRoot = null;
            for (final IXdmNode aChild : new XdmParser ().setRecordLocations (true).parse (aSource.text ()).getAllChildren ())
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
                (_isComposition (aChild) ? aCompositions : aOthers).add (_copyTopLevel (aChild, aRoot, aMerged));
          }
          for (final IXdmNode aChild : aCompositions)
            aMerged.appendChild (aChild);
          for (final IXdmNode aChild : aOthers)
            aMerged.appendChild (aChild);
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
   * grouped by package (by identity) and imported namespace; within a group
   * only those with the highest import precedence are used (XSLT 3.0 section
   * 3.15), and
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
    // Grouped by package unit (by identity), then by imported namespace
    final Map <PackageUnit, ICommonsOrderedMap <String, ICommonsList <Decl>>> aByUnit = new IdentityHashMap <> ();
    final ICommonsList <ICommonsList <Decl>> aGroups = new CommonsArrayList <> ();
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
      final ICommonsOrderedMap <String, ICommonsList <Decl>> aByNamespace = aByUnit.computeIfAbsent (aPackages.getUnit (aEl),
                                                                                                      k -> new CommonsLinkedHashMap <> ());
      aByNamespace.computeIfAbsent (_getImportedNamespace (aEl, aInline), k -> {
        final ICommonsList <Decl> aGroup = new CommonsArrayList <> ();
        aGroups.add (aGroup);
        return aGroup;
      }).add (new Decl (aSource, aEl.getPrecedence ()));
    }
    final ICommonsList <XdmSchemaSource> aSources = new CommonsArrayList <> ();
    final ICommonsSet <String> aSeen = new CommonsHashSet <> ();
    for (final ICommonsList <Decl> aDecls : aGroups)
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
      return XdmSchemaSupport.compile (aSources, aOptions.isReadLocalSchemaFiles ());
    }
    catch (final XdmSchemaException ex)
    {
      throw XsltException.at (aFirst, "XTSE0220", "The imported schema is not valid: " + ex.getMessage ());
    }
  }
}
