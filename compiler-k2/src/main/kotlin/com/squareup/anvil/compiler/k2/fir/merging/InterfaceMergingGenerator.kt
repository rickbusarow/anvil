package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.providers.anvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.providers.scopedMergeProvider
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId

@AutoService(AnvilFirProcessor.Factory::class)
internal class InterfaceMergingGeneratorFactory :
  AbstractAnvilFirProcessorFactory(::InterfaceMergingGenerator)

/**
 * This extension finds all contributed component interfaces and adds them as super types to Dagger
 * components annotated with `@MergeComponent`
 */
public class InterfaceMergingGenerator(session: FirSession) :
  SupertypeProcessor(session, isFlushing = true) {

  private val mergedComponentIds by lazyValue {
    session.anvilFirSymbolProvider.mergeComponentSymbols.mapToSet { it.classId }
  }

  @RequiresTypesResolutionPhase
  private val mergedSupertypesByScope by lazyValue {
    val sourceSupers = session.scopedContributionProvider.contributedSupertypes
    val dependencySupers = session.anvilFirDependencyHintProvider.allDependencyContributedComponents

    (sourceSupers + dependencySupers).groupBy { it.scopeType.getValue() }
  }

  override fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean {
    return declaration.classId in mergedComponentIds
  }

  @OptIn(RequiresTypesResolutionPhase::class)
  override fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ConeKotlinType> {

    val existingSupertypes = resolvedSupertypes.mapToSet { it.coneType.classId }

    val mergedComponent =
      session.scopedMergeProvider.mergedComponents
        .single { it.containingDeclaration.getValue().classId == classLikeDeclaration.classId }

    val mergeScopeId = mergedComponent.scopeType.getValue()

    return mergedSupertypesByScope[mergeScopeId]
      ?.filter { it.contributedType !in existingSupertypes }
      ?.map { it.contributedType.createConeType(session) }
      .orEmpty()
  }
}
