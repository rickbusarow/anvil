package com.squareup.anvil.compiler.k2.fir.abstraction

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.GeneratedMemberFunction
import com.squareup.anvil.compiler.k2.fir.GeneratedTopLevelClass
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.ValueParameter
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.GeneratedBindingDeclarationKey
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import com.squareup.anvil.compiler.k2.utils.names.requireClassId
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@AutoService(AnvilFirProcessor.Factory::class)
public class BindingModuleGeneratorFactory :
  AbstractAnvilFirProcessorFactory(::BindingModuleGenerator)

internal class BindingModuleGenerator(session: FirSession) : TopLevelClassProcessor(session) {

  @OptIn(RequiresTypesResolutionPhase::class)
  private val contributedBindingsByModuleId by lazyValue {
    session.scopedContributionProvider.contributedBindings.associateBy { it.bindingModule }
  }

  private val bindingModulesByModuleId = cachesFactory.createCache { moduleId: ClassId ->

    val contributedBinding = contributedBindingsByModuleId.getValue(moduleId)
    GeneratedTopLevelClass(
      classId = moduleId,
      key = GeneratedBindingDeclarationKey,
      classKind = ClassKind.INTERFACE,
      visibility = Visibilities.Public,
      firExtension = firExtension,
      members = { classSymbol ->
        listOf(
          GeneratedMemberFunction(
            name = contributedBinding.bindingCallableName,
            returnType = contributedBinding.boundType.map { it.createConeType(session) },
            ownerSymbol = lazyValue { classSymbol },
            key = GeneratedBindingDeclarationKey,
            visibility = Visibilities.Public,
            cachesFactory = cachesFactory,
            firExtension = firExtension,
            modality = Modality.ABSTRACT,
            valueParameters = lazyValue {
              listOf(
                ValueParameter(
                  name = Name.identifier("concreteType"),
                  type = contributedBinding.contributedType.createConeType(session),
                ),
              )
            },
            annotations = lazyValue { listOf(createFirAnnotation(ClassIds.daggerBinds)) },
          ),
        )
      },
      annotations = lazyValue { listOf(createFirAnnotation(ClassIds.daggerModule)) },
    )
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage
  }

  override fun getTopLevelClassIds(): Set<ClassId> {
    return contributedBindingsByModuleId.keys
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): GeneratedTopLevelClass =
    bindingModulesByModuleId.getValue(classId)

  override fun getCallableNamesForClass(
    classSymbol: FirClassLikeSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = bindingModulesByModuleId
    .getValueIfComputed(classSymbol.classId)
    ?.members
    ?.mapToSet { it.name }
    .orEmpty()

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    return bindingModulesByModuleId.getValue(callableId.requireClassId())
      .members
      .filterIsInstance<GeneratedMemberFunction>()
      .filter { it.name == callableId.callableName }
      .map { it.generatedFunction.getValue().symbol }
  }
}
