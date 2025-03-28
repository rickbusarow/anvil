package com.squareup.anvil.compiler.k2.constructor.inject

import com.squareup.anvil.compiler.k2.constructor.inject.FirInjectConstructorFactoryGenerationExtension.Key
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.fir.wrapInSyntheticFile
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal class InjectConstructorGenerationModel(
  private val extension: FirExtension,
  private val session: FirSession,
  val matchedConstructorSymbol: FirConstructorSymbol,
) {
  val matchedClassId: ClassId by lazy { matchedConstructorSymbol.callableId.classId!! }

  val generatedClassId: ClassId by lazy { matchedClassId.factoryJoined }
  val generatedClassSymbol: FirClassSymbol<*> by lazy {

    extension.createTopLevelClass(generatedClassId, Key) {
      superType(
        ClassIds.daggerFactory.requireClassLikeSymbol(session)
          .constructType(
            typeArguments = arrayOf(matchedClassId.constructClassLikeType()),
          ),
      )
    }
      .wrapInSyntheticFile(session).symbol
  }

  val generatedConstructor: FirConstructor by lazy {
    extension.createConstructor(
      owner = generatedClassSymbol,
      key = Key,
      isPrimary = true,
      generateDelegatedNoArgConstructorCall = true,
    ) {
      val providerSymbol = ClassIds.javaxProvider.requireClassLikeSymbol(session)
      for (param in matchedConstructorSymbol.valueParameterSymbols) {
        valueParameter(
          name = param.name,
          type = providerSymbol.constructType(
            typeArguments = arrayOf(param.resolvedReturnType),
          ),
        )
      }
    }
  }

  val generatedCallableIdToParameters: Map<CallableId, FirValueParameterSymbol> by lazy {
    generatedConstructor.symbol.valueParameterSymbols.associateBy {
      CallableId(classId = generatedClassId, callableName = it.name)
    }
  }

  val generatedCompanionClass: FirClassSymbol<*> by lazy {
    extension.createCompanionObject(generatedClassSymbol, Key) {
      this@createCompanionObject.visibility = Visibilities.Public
    }.symbol
  }

  val generatedCompanionConstructor: FirConstructor by lazy {
    extension.createDefaultPrivateConstructor(generatedCompanionClass, Key)
  }

  val factoryGetFunction: FirNamedFunctionSymbol by lazy {
    extension.createMemberFunction(
      owner = generatedClassSymbol,
      key = Key,
      name = Names.get,
      returnType = matchedConstructorSymbol.resolvedReturnType,
    ) {
      visibility = Visibilities.Public
      modality = Modality.FINAL
    }.symbol
  }

  val companionCreateFunction: FirNamedFunctionSymbol by lazy {
    extension.createMemberFunction(
      owner = generatedCompanionClass,
      key = Key,
      name = Names.create,
      returnType = generatedClassSymbol.constructType(),
    ) {
      generatedConstructor.symbol.valueParameterSymbols.forEach { symbol ->
        valueParameter(
          name = symbol.name,
          type = symbol.resolvedReturnType,
        )
      }
    }
      .apply { replaceAnnotations(listOf(createFirAnnotation(ClassIds.kotlinJvmStatic))) }
      .symbol
  }

  val companionNewInstanceFunction: FirNamedFunctionSymbol by lazy {
    extension.createMemberFunction(
      owner = generatedCompanionClass,
      key = Key,
      name = Names.newInstance,
      returnType = matchedClassId.constructClassLikeType(),
    ) {
      matchedConstructorSymbol.valueParameterSymbols.forEach { symbol ->
        valueParameter(
          name = symbol.name,
          type = symbol.resolvedReturnType,
        )
      }
    }
      .apply { replaceAnnotations(listOf(createFirAnnotation(ClassIds.kotlinJvmStatic))) }
      .symbol
  }
}
