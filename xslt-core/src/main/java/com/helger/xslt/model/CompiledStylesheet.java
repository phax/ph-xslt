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
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.ast.SeqType;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xpath.value.XdmSequence;
import com.helger.xpath.xdm.XdmQName;
import com.helger.xslt.XsltExecutable;
import com.helger.xslt.XsltTransformRequest;
import com.helger.xslt.XsltTransformResult;
import com.helger.xslt.pkg.PackageModel;
import com.helger.xslt.runtime.XsltTransformer;
import com.helger.xslt.tree.XslModule;

/**
 * A compiled stylesheet. Built by the compiler; after compilation it is not
 * modified any more and can be used by several transformations at once.
 *
 * @author Philip Helger
 */
public final class CompiledStylesheet implements XsltExecutable
{
  /** The global context item declaration. */
  public record GlobalContextItem (@Nullable SeqType as, @NonNull String use)
  {}

  private final FunctionLibrary m_aFunctions;
  private final XslModule m_aPrincipalModule;
  private final ICommonsList <Template> m_aTemplates = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, Template> m_aNamedTemplates = new CommonsHashMap <> ();
  private final ICommonsOrderedMap <XdmQName, Mode> m_aModes = new CommonsLinkedHashMap <> ();
  private XdmQName m_aDefaultMode = Mode.UNNAMED;
  private final ICommonsOrderedMap <XdmQName, VariableDef> m_aGlobals = new CommonsLinkedHashMap <> ();
  private final ICommonsMap <XdmQName, XdmSequence> m_aStaticValues = new CommonsHashMap <> ();
  private final ICommonsList <UserFunction> m_aFunctionsList = new CommonsArrayList <> ();
  private final ICommonsMap <XdmQName, ICommonsList <KeyDefinition>> m_aKeys = new CommonsHashMap <> ();
  private final OutputDefinition m_aOutput = new OutputDefinition (null);
  private final ICommonsMap <XdmQName, OutputDefinition> m_aNamedOutputs = new CommonsHashMap <> ();
  private final ICommonsMap <XdmQName, AttributeSet> m_aAttributeSets = new CommonsHashMap <> ();
  private final SpaceRules m_aSpaceRules = new SpaceRules ();
  private GlobalContextItem m_aGlobalContextItem;
  private final ICommonsMap <Object, Object> m_aExtensionData = new CommonsHashMap <> ();
  private final ICommonsList <XslModule> m_aModules = new CommonsArrayList <> ();
  private boolean m_bPackage;
  private final ICommonsSet <XdmQName> m_aExposedTemplates = new CommonsHashSet <> ();
  private final ICommonsSet <XdmQName> m_aPrivateModes = new CommonsHashSet <> ();
  private PackageModel m_aPackages;

  public CompiledStylesheet (@NonNull final FunctionLibrary aFunctions, @NonNull final XslModule aPrincipalModule)
  {
    m_aFunctions = aFunctions;
    m_aPrincipalModule = aPrincipalModule;
  }

  /** @return The function library of the stylesheet (built-in, XSLT and stylesheet functions). */
  @NonNull
  public FunctionLibrary getFunctionLibrary ()
  {
    return m_aFunctions;
  }

  @NonNull
  public XslModule getPrincipalModule ()
  {
    return m_aPrincipalModule;
  }

  /**
   * @return <code>true</code> if the principal module is an xsl:package (named
   *         templates and modes are then private unless declared otherwise).
   */
  public boolean isPackage ()
  {
    return m_bPackage;
  }

  public void setPackage (final boolean bPackage)
  {
    m_bPackage = bPackage;
  }

  /**
   * @return The names of the named templates an xsl:expose makes public
   *         (<code>null</code> stands for all).
   */
  /** @return The packages of the stylesheet, <code>null</code> before compilation finished. */
  @Nullable
  public PackageModel getPackages ()
  {
    return m_aPackages;
  }

  public void setPackages (@Nullable final PackageModel aPackages)
  {
    m_aPackages = aPackages;
  }

  /** @return <code>true</code> if more than one package takes part. */
  public boolean isMultiPackage ()
  {
    return m_aPackages != null && m_aPackages.isMultiPackage ();
  }

  @NonNull
  public ICommonsSet <XdmQName> exposedTemplates ()
  {
    return m_aExposedTemplates;
  }

  /** @return The modes an xsl:expose makes private (not eligible as initial mode). */
  @NonNull
  public ICommonsSet <XdmQName> privateModes ()
  {
    return m_aPrivateModes;
  }

  /** @return All stylesheet modules (for document('')). */
  @NonNull
  public ICommonsList <XslModule> modules ()
  {
    return m_aModules;
  }

  public void addTemplate (@NonNull final Template aTemplate)
  {
    m_aTemplates.add (aTemplate);
  }

  @NonNull
  public ICommonsList <Template> templates ()
  {
    return m_aTemplates;
  }

  /** @return The named templates (the winning one of each name). */
  @NonNull
  public ICommonsMap <XdmQName, Template> namedTemplates ()
  {
    return m_aNamedTemplates;
  }

  @Nullable
  public Template getNamedTemplate (@NonNull final XdmQName aName)
  {
    return m_aNamedTemplates.get (aName);
  }

  /**
   * @param aName
   *        Mode name ({@link Mode#UNNAMED} for the unnamed mode).
   * @return The mode, created if needed (during compilation only).
   */
  @NonNull
  public Mode getOrCreateMode (@NonNull final XdmQName aName)
  {
    return m_aModes.computeIfAbsent (aName, Mode::new);
  }

  @Nullable
  public Mode getMode (@NonNull final XdmQName aName)
  {
    return m_aModes.get (aName);
  }

  @NonNull
  public ICommonsOrderedMap <XdmQName, Mode> modes ()
  {
    return m_aModes;
  }

  /** @return The default mode of the principal module ([xsl:]default-mode). */
  @NonNull
  public XdmQName getDefaultModeName ()
  {
    return m_aDefaultMode;
  }

  public void setDefaultModeName (@NonNull final XdmQName aName)
  {
    m_aDefaultMode = aName;
  }

  /** @return The global variables and parameters by name (the winning declaration of each name). */
  @NonNull
  public ICommonsOrderedMap <XdmQName, VariableDef> globals ()
  {
    return m_aGlobals;
  }

  /** @return The values of static variables and parameters, settled at compile time. */
  @NonNull
  public ICommonsMap <XdmQName, XdmSequence> staticValues ()
  {
    return m_aStaticValues;
  }

  @NonNull
  public ICommonsList <UserFunction> functions ()
  {
    return m_aFunctionsList;
  }

  /**
   * @param aName
   *        Key name.
   * @return The declarations of that key, or <code>null</code>.
   */
  @Nullable
  public ICommonsList <KeyDefinition> getKey (@NonNull final XdmQName aName)
  {
    return m_aKeys.get (aName);
  }

  public void addKey (@NonNull final KeyDefinition aKey)
  {
    m_aKeys.computeIfAbsent (aKey.getName (), k -> new CommonsArrayList <> ()).add (aKey);
  }

  @NonNull
  public ICommonsMap <XdmQName, ICommonsList <KeyDefinition>> keys ()
  {
    return m_aKeys;
  }

  /** @return The unnamed output definition. */
  @NonNull
  public OutputDefinition getOutput ()
  {
    return m_aOutput;
  }

  /**
   * @param aName
   *        Name.
   * @return The named output definition, created if needed (during
   *         compilation only).
   */
  @NonNull
  public OutputDefinition getOrCreateNamedOutput (@NonNull final XdmQName aName)
  {
    return m_aNamedOutputs.computeIfAbsent (aName, OutputDefinition::new);
  }

  @Nullable
  public OutputDefinition getNamedOutput (@NonNull final XdmQName aName)
  {
    return m_aNamedOutputs.get (aName);
  }

  @NonNull
  public ICommonsMap <XdmQName, AttributeSet> attributeSets ()
  {
    return m_aAttributeSets;
  }

  @Nullable
  public AttributeSet getAttributeSet (@NonNull final XdmQName aName)
  {
    return m_aAttributeSets.get (aName);
  }

  @NonNull
  public SpaceRules getSpaceRules ()
  {
    return m_aSpaceRules;
  }

  @Nullable
  public GlobalContextItem getGlobalContextItem ()
  {
    return m_aGlobalContextItem;
  }

  public void setGlobalContextItem (@Nullable final GlobalContextItem aDecl)
  {
    m_aGlobalContextItem = aDecl;
  }

  /**
   * Compile-time data of instruction families (accumulators, character maps,
   * ...). Write it only during compilation.
   *
   * @param aKey
   *        Key, compared with equals.
   * @return The value or <code>null</code>.
   */
  @Nullable
  public Object getExtensionData (@NonNull final Object aKey)
  {
    return m_aExtensionData.get (aKey);
  }

  public void putExtensionData (@NonNull final Object aKey, @Nullable final Object aValue)
  {
    m_aExtensionData.put (aKey, aValue);
  }

  /** @return All user functions with the given name. */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <UserFunction> getFunctionsByName (@NonNull final XdmQName aName)
  {
    return m_aFunctionsList.getAll (x -> x.getName ().equals (aName));
  }

  @NonNull
  public XsltTransformResult transform (@NonNull final XsltTransformRequest aRequest)
  {
    return new XsltTransformer (this, aRequest).run ();
  }
}
