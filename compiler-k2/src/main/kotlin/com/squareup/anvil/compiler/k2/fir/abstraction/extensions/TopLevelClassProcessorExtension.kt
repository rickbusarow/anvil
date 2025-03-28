package com.squareup.anvil.compiler.k2.fir.abstraction.extensions

import com.squareup.anvil.annotations.internal.InternalAnvilApi
import com.squareup.anvil.compiler.k2.fir.GeneratedMemberFunction
import com.squareup.anvil.compiler.k2.fir.GeneratedMemberProperty
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import com.squareup.anvil.compiler.k2.utils.fir.wrapInSyntheticFile
import com.squareup.anvil.compiler.k2.utils.names.requireClassId
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

public class TopLevelClassProcessorExtension(session: FirSession) :
  FirDeclarationGenerationExtension(session) {

  private val topLevelByClassId = mutableMapOf<ClassId, TopLevelClassProcessor>()

  private val generatedTopLevelClassByClassId =
    session.firCachesFactory.createCache { classId: ClassId ->
      topLevelByClassId[classId]?.generateTopLevelClassLikeDeclaration(classId)
    }

  private val nestedByClassId = session.firCachesFactory.createCache { classId: ClassId ->

    classId.parentClassId?.let { parent ->
      generatedTopLevelClassByClassId.getValue(parent)?.let { top ->
        top.nestedClasses.firstOrNull { it.name == classId.shortClassName }
      }
    }
  }

  private val generators by session.firCachesFactory.createLazyValue {
    session.anvilFirProcessorProvider.topLevelClassProcessors
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return generators.any { it.hasPackage(packageFqName) }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {

    for (generator in generators) {
      @OptIn(InternalAnvilApi::class)
      generator.bindFirExtension(this)

      for (id in generator.getTopLevelClassIds()) {
        topLevelByClassId[id] = generator
      }
    }

    return topLevelByClassId.keys
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    return generatedTopLevelClassByClassId.getValue(classId)
      ?.generatedClass
      ?.getValue()
      ?.wrapInSyntheticFile(session)
      ?.symbol
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {

    val type = generatedTopLevelClassByClassId.getValue(classSymbol.classId)
      ?: nestedByClassId.getValue(classSymbol.classId)

    if (type == null) return emptySet()

    val all = setOf(SpecialNames.INIT, *type.members.mapToSet { it.name }.toTypedArray())

    return all
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {

    val type = generatedTopLevelClassByClassId.getValue(callableId.requireClassId())
      ?: nestedByClassId.getValue(callableId.requireClassId())
      ?: return emptyList()

    return type.members
      .filterIsInstance<GeneratedMemberFunction>()
      .filter { it.name == callableId.callableName }
      .map { it.generatedFunction.getValue().symbol }
  }

  override fun generateProperties(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirPropertySymbol> {

    val type = generatedTopLevelClassByClassId.getValue(callableId.requireClassId())
      ?: nestedByClassId.getValue(callableId.requireClassId())
      ?: return emptyList()

    return type.members
      .filterIsInstance<GeneratedMemberProperty>()
      .filter { it.name == callableId.callableName }
      .map { it.generatedProperty.getValue().symbol }
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {

    val topLevel = generatedTopLevelClassByClassId.getValue(context.owner.classId)

    if (topLevel != null) {
      return topLevel.constructors.invoke(context).map { it.symbol }
    }

    val nested = nestedByClassId.getValue(context.owner.classId)!!

    val nextedConstructors = nested.constructors.invoke(context)

    return nextedConstructors.map { it.symbol }
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {

    val topLevel = generatedTopLevelClassByClassId.getValue(classSymbol.classId)
      ?: return emptySet()

    return topLevel.nestedClasses.mapToSet { it.name }
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    val topLevel = generatedTopLevelClassByClassId.getValue(owner.classId)
      ?: return null

    val nested = topLevel.nestedClasses.firstOrNull { it.name == name }

    return nested?.generatedClass?.getValue()?.symbol
  }
}
