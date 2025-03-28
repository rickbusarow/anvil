package com.squareup.anvil.compiler.k2.constructor.inject

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.PendingClassLikeDeclaration
import com.squareup.anvil.compiler.k2.fir.PendingCompanionObject
import com.squareup.anvil.compiler.k2.fir.PendingMemberFunction
import com.squareup.anvil.compiler.k2.fir.PendingMemberProperty
import com.squareup.anvil.compiler.k2.fir.PendingTopLevelClass
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.daggerThingProvider
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.references.builder.buildPropertyFromParameterResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@AutoService(AnvilFirProcessor.Factory::class)
public class InjectConstructorFactoryGeneratorFactory :
  AbstractAnvilFirProcessorFactory(initializer = ::InjectConstructorFactoryGenerator)

internal class InjectConstructorFactoryGenerator(session: FirSession) :
  TopLevelClassProcessor(session) {

  private val injectConstructorsByClassId by lazyValue {
    session.daggerThingProvider.injectConstructors
      .associateBy { it.classId.getValue() }
  }

  private val injectConstructorsByFactoryClassId by lazyValue {
    session.daggerThingProvider.injectConstructors
      .associateBy { it.classId.getValue().factoryJoined }
  }

  private val factoryClassIds by lazyValue { injectConstructorsByClassId.keys.mapToSet { it.factoryJoined } }

  private val factoriesByFactoryClassId = cachesFactory.createCache { factoryId: ClassId ->

    val injectedClass = injectConstructorsByFactoryClassId.getValue(factoryId)

    val providerSymbol = ClassIds.javaxProvider.requireClassLikeSymbol(session)

    val constructorProperties = injectedClass.constructor.map { constructor ->
      constructor.valueParameterSymbols.map { paramSymbol ->
        paramSymbol.name to providerSymbol.constructType(arrayOf(paramSymbol.resolvedReturnType))
      }
    }

    PendingTopLevelClass(
      classId = factoryId,
      key = Key,
      classKind = ClassKind.CLASS,
      visibility = Visibilities.Public,
      annotations = lazyValue { emptyList() },
      firExtension = firExtension,
      supertypes = injectedClass.classId.map { matchedClassId ->
        listOf(
          ClassIds.daggerFactory.constructClassLikeType(
            typeArguments = arrayOf(matchedClassId.constructClassLikeType()),
          ),
        )
      },
      constructors = { ctx ->
        listOf(
          firExtension.createConstructor(
            owner = ctx.owner,
            key = Key,
            isPrimary = true,
            generateDelegatedNoArgConstructorCall = true,
          ) {
            for ((name, type) in constructorProperties.getValue()) {
              valueParameter(name = name, type = type)
            }
          },
        )
      },
      members = { ownerSymbol ->
        val primaryConstructor = ownerSymbol.primaryConstructorSymbol(session)!!

        val constructorParamsByName =
          primaryConstructor.valueParameterSymbols.associateBy { it.name }

        listOf(
          *constructorProperties.getValue().map { (name, type) ->
            PendingMemberProperty(
              name = name,
              returnType = lazyValue { type },
              ownerSymbol = lazyValue { ownerSymbol },
              key = Key,
              isVal = false,
              visibility = Visibilities.Public,
              cachesFactory = cachesFactory,
              firExtension = firExtension,
              initializer = constructorParamsByName[name]?.let { symbol ->
                lazyValue {
                  // Assigns property values from the constructor arguments
                  buildPropertyAccessExpression {
                    coneTypeOrNull = type
                    calleeReference = buildPropertyFromParameterResolvedNamedReference ref@{
                      this@ref.name = name
                      resolvedSymbol = symbol
                    }
                  }
                }
              },
            )
          }.toTypedArray(),
          PendingMemberFunction(
            name = Names.get,
            returnType = injectedClass.constructor.map { it.resolvedReturnType },
            ownerSymbol = lazyValue { ownerSymbol },
            key = Key,
            visibility = Visibilities.Public,
            cachesFactory = cachesFactory,
            firExtension = firExtension,
          ),
        )
      },
      nestedClasses = { outerOwnerSymbol ->
        listOf(
          PendingCompanionObject(
            ownerSymbol = lazyValue { outerOwnerSymbol },
            key = Key,
            visibility = Visibilities.Public,
            annotations = lazyValue { emptyList() },
            firExtension = firExtension,
            members = { ownerSymbol ->
              listOf(
                PendingMemberFunction(
                  name = Names.create,
                  returnType = lazyValue { factoryId.constructClassLikeType() },
                  ownerSymbol = lazyValue { ownerSymbol },
                  key = Key,
                  visibility = Visibilities.Public,
                  cachesFactory = cachesFactory,
                  valueParameters = lazyValue {
                    val providerSymbol = ClassIds.javaxProvider.requireClassLikeSymbol(session)
                    injectedClass.constructor.getValue().valueParameterSymbols.map { paramSymbol ->
                      paramSymbol.name to providerSymbol.constructType(arrayOf(paramSymbol.resolvedReturnType))
                    }
                  },
                  annotations = lazyValue { listOf(createFirAnnotation(ClassIds.kotlinJvmStatic)) },
                  firExtension = firExtension,
                ),
                PendingMemberFunction(
                  name = Names.newInstance,
                  returnType = injectedClass.classId.map { it.constructClassLikeType() },
                  ownerSymbol = lazyValue { ownerSymbol },
                  key = Key,
                  visibility = Visibilities.Public,
                  cachesFactory = cachesFactory,
                  valueParameters = injectedClass.constructor.map { constructor ->
                    constructor.valueParameterSymbols.map { symbol ->
                      symbol.name to symbol.resolvedReturnType
                    }
                  },
                  annotations = lazyValue { listOf(createFirAnnotation(ClassIds.kotlinJvmStatic)) },
                  firExtension = firExtension,
                ),
              )
            },
          ),
        )
      },
    )
  }

  private val companionsByCompanionClassId =
    cachesFactory.createCache { companionClassId: ClassId ->
    }

  override fun getTopLevelClassIds(): Set<ClassId> = factoryClassIds

  override fun hasPackage(packageFqName: FqName): Boolean {
    return factoryClassIds.any { it.packageFqName == packageFqName }
  }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): PendingTopLevelClass {
    return factoriesByFactoryClassId.getValue(classId)
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassLikeSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    return getGeneratedTopOrNested(classSymbol.classId).members.mapToSet { it.name }
  }

  private fun getGeneratedTopOrNested(classId: ClassId): PendingClassLikeDeclaration {
    return factoriesByFactoryClassId.getValueIfComputed(classId)
      ?: factoriesByFactoryClassId.getValue(classId.outermostClassId)
        .nestedClasses.single { it.name == classId.shortClassName }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> = getGeneratedTopOrNested(callableId.classId!!).members
    .filterIsInstance<PendingMemberFunction>()
    .map { it.generatedFunction.getValue().symbol }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): PendingCompanionObject = factoriesByFactoryClassId.getValue(owner.classId)
    .let { factoryClass ->

      factoryClass.nestedClasses.single { it.name == name } as PendingCompanionObject
    }

  companion object Key : GeneratedDeclarationKey()
}
