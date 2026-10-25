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
package com.helger.xslt.compile;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.xpath.fn.FunctionLibrary;
import com.helger.xslt.fn.XsltFunctions;
import com.helger.xslt.instr.CoreInstructions;

/**
 * The registry of XSLT instructions, declarations, functions and hooks. The
 * core registers itself first; then every instruction family listed in
 * {@link #EXTENSION_CLASS_NAMES} whose class is on the class path is
 * instantiated and registers its own parts. Adding a family therefore needs
 * no change of the core: create the class with the agreed name (or add a name
 * to the list) and register from it.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class XsltExtensionRegistry
{
  /**
   * The classes of the instruction families, each implementing
   * {@link IXsltExtension}. Missing classes are skipped.
   */
  public static final String [] EXTENSION_CLASS_NAMES = { "com.helger.xslt.ext.number.NumberExtension",
                                                          "com.helger.xslt.ext.grouping.GroupingExtension",
                                                          "com.helger.xslt.ext.merge.MergeExtension",
                                                          "com.helger.xslt.ext.iterate.IterateExtension",
                                                          "com.helger.xslt.ext.trycatch.TryCatchExtension",
                                                          "com.helger.xslt.ext.resultdocument.ResultDocumentExtension",
                                                          "com.helger.xslt.ext.accumulator.AccumulatorExtension",
                                                          "com.helger.xslt.ext.evaluate.EvaluateExtension",
                                                          "com.helger.xslt.ext.map.MapExtension",
                                                          "com.helger.xslt.ext.sourcedocument.SourceDocumentExtension",
                                                          "com.helger.xslt.ext.assertion.AssertExtension",
                                                          "com.helger.xslt.ext.fork.ForkExtension",
                                                          "com.helger.xslt.ext.charactermap.CharacterMapExtension",
                                                          "com.helger.xslt.ext.transform.TransformExtension" };

  /** Declarations the stylesheet compiler handles itself. */
  public static final String [] CORE_DECLARATIONS = { "attribute-set",
                                                      "decimal-format",
                                                      "function",
                                                      "global-context-item",
                                                      "import",
                                                      "include",
                                                      "key",
                                                      "mode",
                                                      "namespace-alias",
                                                      "output",
                                                      "param",
                                                      "preserve-space",
                                                      "strip-space",
                                                      "template",
                                                      "variable" };

  private static final Logger LOGGER = LoggerFactory.getLogger (XsltExtensionRegistry.class);

  private static final SimpleReadWriteLock INSTANCE_LOCK = new SimpleReadWriteLock ();
  private static volatile XsltExtensionRegistry s_aInstance;

  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();

  private final ICommonsMap <String, IInstructionCompiler> m_aInstructions = new CommonsHashMap <> ();
  private final ICommonsMap <String, IDeclarationCompiler> m_aDeclarations = new CommonsHashMap <> ();
  private final ICommonsList <Consumer <FunctionLibrary>> m_aFunctionContributors = new CommonsArrayList <> ();
  private final ICommonsList <IOutputCustomizer> m_aOutputCustomizers = new CommonsArrayList <> ();
  private final ICommonsList <ICompileFinisher> m_aFinishers = new CommonsArrayList <> ();
  private final ICommonsSet <String> m_aSystemPropertiesYes = new CommonsHashSet <> ();

  private XsltExtensionRegistry ()
  {}

  @NonNull
  private static XsltExtensionRegistry _create ()
  {
    final XsltExtensionRegistry ret = new XsltExtensionRegistry ();
    CoreInstructions.register (ret);
    ret.registerFunctions (XsltFunctions::registerRuntime);
    for (final String sClassName : EXTENSION_CLASS_NAMES)
    {
      final Class <?> aClass;
      try
      {
        aClass = Class.forName (sClassName);
      }
      catch (final ClassNotFoundException ex)
      {
        continue;
      }
      try
      {
        final IXsltExtension aExt = (IXsltExtension) aClass.getDeclaredConstructor ().newInstance ();
        aExt.register (ret);
      }
      catch (final ReflectiveOperationException | ClassCastException ex)
      {
        LOGGER.error ("Failed to register the XSLT extension '" + sClassName + "'", ex);
      }
    }
    return ret;
  }

  /** @return The registry, created on first use. */
  @NonNull
  public static XsltExtensionRegistry getInstance ()
  {
    final XsltExtensionRegistry ret = s_aInstance;
    if (ret != null)
      return ret;
    // Created without holding the lock, because it loads and initialises the
    // extension classes; if two threads race, the first published one wins
    final XsltExtensionRegistry aNew = _create ();
    return INSTANCE_LOCK.writeLockedGet ( () -> {
      XsltExtensionRegistry aCurrent = s_aInstance;
      if (aCurrent == null)
        s_aInstance = aCurrent = aNew;
      return aCurrent;
    });
  }

  /**
   * Register the compiler of an instruction in the XSLT namespace.
   *
   * @param sLocalName
   *        Local name, e.g. <code>number</code>.
   * @param aCompiler
   *        The compiler.
   * @return this for chaining
   * @throws IllegalStateException
   *         if the instruction is already registered
   */
  @NonNull
  public XsltExtensionRegistry registerInstruction (@NonNull final String sLocalName,
                                                    @NonNull final IInstructionCompiler aCompiler)
  {
    ValueEnforcer.notEmpty (sLocalName, "LocalName");
    ValueEnforcer.notNull (aCompiler, "Compiler");
    m_aRWLock.writeLocked ( () -> {
      if (m_aInstructions.containsKey (sLocalName))
        throw new IllegalStateException ("The instruction xsl:" + sLocalName + " is already registered");
      m_aInstructions.put (sLocalName, aCompiler);
    });
    return this;
  }

  /**
   * Replace the compiler of an instruction the core provides only in a basic
   * form.
   *
   * @param sLocalName
   *        Local name.
   * @param aCompiler
   *        The compiler.
   * @return this for chaining
   */
  @NonNull
  public XsltExtensionRegistry replaceInstruction (@NonNull final String sLocalName,
                                                   @NonNull final IInstructionCompiler aCompiler)
  {
    ValueEnforcer.notEmpty (sLocalName, "LocalName");
    ValueEnforcer.notNull (aCompiler, "Compiler");
    m_aRWLock.writeLocked ( () -> m_aInstructions.put (sLocalName, aCompiler));
    return this;
  }

  /**
   * Register the compiler of a top-level declaration in the XSLT namespace.
   *
   * @param sLocalName
   *        Local name, e.g. <code>accumulator</code>.
   * @param aCompiler
   *        The compiler.
   * @return this for chaining
   */
  @NonNull
  public XsltExtensionRegistry registerDeclaration (@NonNull final String sLocalName,
                                                    @NonNull final IDeclarationCompiler aCompiler)
  {
    ValueEnforcer.notEmpty (sLocalName, "LocalName");
    ValueEnforcer.notNull (aCompiler, "Compiler");
    m_aRWLock.writeLocked ( () -> {
      if (m_aDeclarations.containsKey (sLocalName))
        throw new IllegalStateException ("The declaration xsl:" + sLocalName + " is already registered");
      m_aDeclarations.put (sLocalName, aCompiler);
    });
    return this;
  }

  /**
   * Register functions: the contributor is called for the function library of
   * every stylesheet before any expression of it is compiled.
   *
   * @param aContributor
   *        Registers functions into the library.
   * @return this for chaining
   */
  @NonNull
  public XsltExtensionRegistry registerFunctions (@NonNull final Consumer <FunctionLibrary> aContributor)
  {
    ValueEnforcer.notNull (aContributor, "Contributor");
    m_aRWLock.writeLocked ( () -> m_aFunctionContributors.add (aContributor));
    return this;
  }

  @NonNull
  public XsltExtensionRegistry registerOutputCustomizer (@NonNull final IOutputCustomizer aCustomizer)
  {
    ValueEnforcer.notNull (aCustomizer, "Customizer");
    m_aRWLock.writeLocked ( () -> m_aOutputCustomizers.add (aCustomizer));
    return this;
  }

  @NonNull
  public XsltExtensionRegistry registerCompileFinisher (@NonNull final ICompileFinisher aFinisher)
  {
    ValueEnforcer.notNull (aFinisher, "Finisher");
    m_aRWLock.writeLocked ( () -> m_aFinishers.add (aFinisher));
    return this;
  }

  /**
   * Make system-property() return "yes" for a property in the XSLT namespace
   * (e.g. <code>supports-dynamic-evaluation</code>).
   *
   * @param sLocalName
   *        Local name of the property.
   * @return this for chaining
   */
  @NonNull
  public XsltExtensionRegistry registerSystemPropertyYes (@NonNull final String sLocalName)
  {
    m_aRWLock.writeLocked ( () -> m_aSystemPropertiesYes.add (sLocalName));
    return this;
  }

  @Nullable
  public IInstructionCompiler getInstructionCompiler (@NonNull final String sLocalName)
  {
    return m_aRWLock.readLockedGet ( () -> m_aInstructions.get (sLocalName));
  }

  @Nullable
  public IDeclarationCompiler getDeclarationCompiler (@NonNull final String sLocalName)
  {
    return m_aRWLock.readLockedGet ( () -> m_aDeclarations.get (sLocalName));
  }

  /**
   * @param sLocalName
   *        Local name of an XSLT element.
   * @return <code>true</code> if the processor implements it (instruction or
   *         declaration), for element-available().
   */
  public boolean isImplemented (@NonNull final String sLocalName)
  {
    if (m_aRWLock.readLockedBoolean ( () -> m_aInstructions.containsKey (sLocalName) ||
                                           m_aDeclarations.containsKey (sLocalName)))
      return true;
    for (final String s : CORE_DECLARATIONS)
      if (s.equals (sLocalName))
        return true;
    return false;
  }

  public boolean isSystemPropertyYes (@NonNull final String sLocalName)
  {
    return m_aRWLock.readLockedBoolean ( () -> m_aSystemPropertiesYes.contains (sLocalName));
  }

  /**
   * @param aLibrary
   *        The library of a new stylesheet.
   */
  public void contributeFunctions (@NonNull final FunctionLibrary aLibrary)
  {
    // The contributors are called without holding the lock
    final ICommonsList <Consumer <FunctionLibrary>> aList = m_aRWLock.readLockedGet (m_aFunctionContributors::getClone);
    for (final Consumer <FunctionLibrary> aContributor : aList)
      aContributor.accept (aLibrary);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IOutputCustomizer> getAllOutputCustomizers ()
  {
    return m_aRWLock.readLockedGet (m_aOutputCustomizers::getClone);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <ICompileFinisher> getAllCompileFinishers ()
  {
    return m_aRWLock.readLockedGet (m_aFinishers::getClone);
  }
}
