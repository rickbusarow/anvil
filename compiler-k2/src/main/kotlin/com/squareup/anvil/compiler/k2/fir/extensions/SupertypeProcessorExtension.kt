package com.squareup.anvil.compiler.k2.fir.extensions

import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.providers.anvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.fir.providers.daggerThingProvider
import com.squareup.anvil.compiler.k2.fir.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.providers.scopedMergeProvider
import com.squareup.anvil.compiler.k2.utils.fir.AnvilPredicates
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.ExperimentalSupertypesGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.ClassId

public class SupertypeProcessorExtension(
  session: FirSession,
) : FirSupertypeGenerationExtension(session) {

  private val processorsByClassId = mutableMapOf<ClassId, List<SupertypeProcessor>>()

  override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {

    val processors = session.anvilFirProcessorProvider.supertypeProcessors

    val todo = processors.filter { it.shouldProcess(declaration) }

    if (todo.isNotEmpty()) {
      processorsByClassId[declaration.classId] = todo
    }

    return todo.isNotEmpty() || !session.scopedContributionProvider.isInitialized()
  }

  override fun computeAdditionalSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    session.scopedContributionProvider.bindTypeResolveService(typeResolver)
    session.scopedMergeProvider.bindTypeResolveService(typeResolver)
    session.daggerThingProvider.bindTypeResolveService(typeResolver)

    return processorsByClassId.remove(classLikeDeclaration.classId)
      ?.flatMap { it.addSupertypes(classLikeDeclaration, resolvedSupertypes, typeResolver) }
      ?: emptyList()
  }

  @ExperimentalSupertypesGenerationApi
  override fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: TypeResolveService,
  ): List<FirResolvedTypeRef> {
    return processorsByClassId.remove(klass.classId)
      ?.flatMap { it.computeAdditionalSupertypesForGeneratedNestedClass(klass, typeResolver) }
      ?: emptyList()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(AnvilPredicates.hasAnyAnvilContributes)
  }
}
