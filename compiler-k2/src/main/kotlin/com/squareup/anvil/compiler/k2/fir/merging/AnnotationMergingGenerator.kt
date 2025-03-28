package com.squareup.anvil.compiler.k2.fir.merging

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.FlushingSupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedMergeProvider
import com.squareup.anvil.compiler.k2.utils.fir.createClassListArgument
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotationCall
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.psi.ktPsiFactory
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.toKtPsiSourceElement

@AutoService(AnvilFirProcessor.Factory::class)
public class AnnotationMergingGeneratorFactory :
  AbstractAnvilFirProcessorFactory(::AnnotationMergingGenerator)

/**
 * This generator merges all contributed Dagger modules on the classpath and includes them on the
 * component annotated with `@MergeComponent`.
 */
public class AnnotationMergingGenerator(session: FirSession) : FlushingSupertypeProcessor(session) {

  private val mergedComponentIds by lazyValue {
    session.anvilFirSymbolProvider.mergeComponentSymbols.mapToSet { it.classId }
  }

  @RequiresTypesResolutionPhase
  private val mergedModulesByScope by lazyValue {
    val sourceModules = session.scopedContributionProvider.contributedModules
    val generatedModules = session.scopedContributionProvider.contributedBindingModules
    val dependencyModules = session.anvilFirDependencyHintProvider.allDependencyContributedModules

    (sourceModules + generatedModules + dependencyModules).groupBy { it.scopeType.getValue() }
  }

  override fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean =
    declaration.classId in mergedComponentIds

  @RequiresTypesResolutionPhase
  public override fun generateAnnotation(
    classLikeDeclaration: FirClassLikeDeclaration,
  ): FirAnnotationCall {
    val mergedComponent =
      session.scopedMergeProvider.mergedComponents
        .single { it.containingDeclaration.getValue().classId == classLikeDeclaration.classId }

    val mergeScopeId = mergedComponent.scopeType.getValue()

    val containingDeclaration = mergedComponent.containingDeclaration.getValue()

    val annotationModules = mergedComponent.modules.getValue()

    val mergedModules = mergedModulesByScope[mergeScopeId].orEmpty()
      .map { it.contributedType }
      .plus(annotationModules)
      .sortedBy { it.asString() }

    val mergeAnnotation = mergedComponent.mergeAnnotationCall.getValue()

    val newAnnotationCallPsi = mergeAnnotation.psi?.let { psiEntry ->
      buildNewAnnotationPsi(
        ktPsiFactory = psiEntry.ktPsiFactory(),
        mergedModules = mergedModules,
      )
    }

    val newSource =
      newAnnotationCallPsi?.toKtPsiSourceElement(KtFakeSourceElementKind.PluginGenerated)
        ?: mergeAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

    return createFirAnnotationCall(
      type = ClassIds.daggerComponent,
      containingDeclarationSymbol = containingDeclaration.symbol,
      argumentList = buildArgumentList {

        source = mergeAnnotation.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

        if (mergedModules.isNotEmpty()) {
          arguments += createClassListArgument(Names.modules, mergedModules, session)
        }

        val mergeDeps = mergedComponent.dependencies.getValue()
        if (mergeDeps.isEmpty()) {
          arguments += createClassListArgument(Names.dependencies, mergeDeps, session)
        }
      },
      source = newSource,
      ktPsiFactory = containingDeclaration.psi?.ktPsiFactory(),
    )
  }

  private fun buildNewAnnotationPsi(
    ktPsiFactory: KtPsiFactory,
    mergedModules: List<ClassId>,
  ): KtAnnotationEntry {

    val classArgList = mergedModules
      .joinToString(separator = ", ") { "${it.asFqNameString()}::class" }

    val newModulesText = "modules = [$classArgList]"

    val componentCall = ClassIds.daggerComponent.asFqNameString()

    return ktPsiFactory.createAnnotationEntry("@$componentCall($newModulesText)")
  }
}
