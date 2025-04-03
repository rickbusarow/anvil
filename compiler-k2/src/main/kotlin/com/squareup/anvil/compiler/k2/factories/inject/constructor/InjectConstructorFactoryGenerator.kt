package com.squareup.anvil.compiler.k2.factories.inject.constructor

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AbstractAnvilFirProcessorFactory
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.GeneratedClassLikeDeclaration
import com.squareup.anvil.compiler.k2.fir.GeneratedCompanionObject
import com.squareup.anvil.compiler.k2.fir.GeneratedMemberFunction
import com.squareup.anvil.compiler.k2.fir.GeneratedMemberProperty
import com.squareup.anvil.compiler.k2.fir.GeneratedTopLevelClass
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.ValueParameter
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.daggerThingProvider
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.fir.requireClassLikeSymbol
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import com.squareup.anvil.compiler.k2.utils.names.requireClassId
import com.squareup.anvil.compiler.k2.utils.stdlib.mapToSet
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
import org.jetbrains.kotlin.name.Name

@AutoService(AnvilFirProcessor.Factory::class)
internal class FirInjectConstructorFactoryGeneratorFactory :
  AbstractAnvilFirProcessorFactory(initializer = ::FirInjectConstructorFactoryGenerator)

/**
 * Given this kotlin source:
 * class InjectClass @Inject constructor(private val param0: String)
 *
 *
 * Using a FirDeclarationGenerationExtension in kotlin k2 fir plugin generate a class file
 * representing the following:
 *
 * public class InjectClass_Factory(
 *   private val param0: Provider<String>
 * ) : com.internal.Dagger.Factory<InjectClass> {
 *   public override fun `get`(): InjectClass = newInstance(param0.get())
 *
 *   public companion object {
 *     @JvmStatic
 *     public fun create(param0: dagger.Provider<String>): InjectClass_Factory =
 *         InjectClass_Factory(param0)
 *
 *     @JvmStatic
 *     public fun newInstance(param0: String): InjectClass = InjectClass(param0)
 *   }
 * }
 */
internal class FirInjectConstructorFactoryGenerator(session: FirSession) :
  TopLevelClassProcessor(session) {

  private val injectConstructorsByClassId
    by lazyValue {
      session.daggerThingProvider.injectConstructors
        .associateBy { it.classId.getValue() }
    }

  private val injectConstructorsByFactoryClassId
    by lazyValue {
      session.daggerThingProvider.injectConstructors
        .associateBy { it.classId.getValue().factoryJoined }
    }

  private val factoryClassIds by lazyValue {
    injectConstructorsByClassId.keys.mapToSet { it.factoryJoined }
  }

  private val factoriesByFactoryClassId = cachesFactory.createCache { factoryId: ClassId ->

    val injectedClass = injectConstructorsByFactoryClassId.getValue(factoryId)

    val providerSymbol = ClassIds.javaxProvider.requireClassLikeSymbol(session)

    val constructorProperties = injectedClass.constructor.map { constructor ->
      constructor.valueParameterSymbols.map { paramSymbol ->
        ValueParameter(
          name = paramSymbol.name,
          type = providerSymbol.constructType(arrayOf(paramSymbol.resolvedReturnType)),
        )
      }
    }

    GeneratedTopLevelClass(
      classId = factoryId,
      key = InjectConstructorFactoryGeneratorKey,
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
            key = InjectConstructorFactoryGeneratorKey,
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

        val constructorParamsByName = lazyValue {
          ownerSymbol.primaryConstructorSymbol(
            session,
          )!!.valueParameterSymbols.associateBy { it.name }
        }

        listOf(
          *constructorProperties.getValue().map { (name, type) ->
            GeneratedMemberProperty(
              name = name,
              returnType = lazyValue { type },
              ownerSymbol = lazyValue { ownerSymbol },
              key = InjectConstructorFactoryGeneratorKey,
              isVal = false,
              visibility = Visibilities.Public,
              cachesFactory = cachesFactory,
              firExtension = firExtension,
              initializer = lazyValue {
                // Assigns property values from the constructor arguments
                buildPropertyAccessExpression {
                  coneTypeOrNull = type
                  calleeReference = buildPropertyFromParameterResolvedNamedReference ref@{
                    this@ref.name = name
                    resolvedSymbol = constructorParamsByName.getValue().getValue(name)
                  }
                }
              },
            )
          }.toTypedArray(),
          GeneratedMemberFunction(
            name = Names.get,
            returnType = injectedClass.constructor.map { it.resolvedReturnType },
            ownerSymbol = lazyValue { ownerSymbol },
            key = InjectConstructorFactoryGeneratorKey,
            visibility = Visibilities.Public,
            cachesFactory = cachesFactory,
            firExtension = firExtension,
          ),
        )
      },
      nestedClasses = { outerOwnerSymbol ->
        listOf(
          GeneratedCompanionObject(
            ownerSymbol = lazyValue { outerOwnerSymbol },
            key = InjectConstructorFactoryGeneratorKey,
            visibility = Visibilities.Public,
            annotations = lazyValue { emptyList() },
            firExtension = firExtension,
            members = { ownerSymbol ->
              listOf(
                GeneratedMemberFunction(
                  name = Names.create,
                  returnType = lazyValue { factoryId.constructClassLikeType() },
                  ownerSymbol = lazyValue { ownerSymbol },
                  key = InjectConstructorFactoryGeneratorKey,
                  visibility = Visibilities.Public,
                  cachesFactory = cachesFactory,
                  valueParameters = constructorProperties,
                  annotations = lazyValue { listOf(createFirAnnotation(ClassIds.kotlinJvmStatic)) },
                  firExtension = firExtension,
                ),
                GeneratedMemberFunction(
                  name = Names.newInstance,
                  returnType = injectedClass.classId.map { it.constructClassLikeType() },
                  ownerSymbol = lazyValue { ownerSymbol },
                  key = InjectConstructorFactoryGeneratorKey,
                  visibility = Visibilities.Public,
                  cachesFactory = cachesFactory,
                  valueParameters = injectedClass.constructor.map { constructor ->
                    constructor.valueParameterSymbols.map { symbol ->
                      ValueParameter(name = symbol.name, type = symbol.resolvedReturnType)
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

  override fun getTopLevelClassIds(): Set<ClassId> = factoryClassIds

  // override fun hasPackage(packageFqName: FqName): Boolean {
  //   return factoryClassIds.any { it.packageFqName == packageFqName }
  // }

  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): GeneratedTopLevelClass {
    return factoriesByFactoryClassId.getValue(classId)
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassLikeSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    return getGeneratedTopOrNested(classSymbol.classId).members.mapToSet { it.name }
  }

  private fun getGeneratedTopOrNested(classId: ClassId): GeneratedClassLikeDeclaration {
    return factoriesByFactoryClassId.getValueIfComputed(classId)
      ?: factoriesByFactoryClassId.getValue(classId.outermostClassId)
        .nestedClasses.single { it.name == classId.shortClassName }
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> = getGeneratedTopOrNested(callableId.requireClassId()).members
    .filterIsInstance<GeneratedMemberFunction>()
    .filter { it.name == callableId.callableName }
    .map { it.generatedFunction.getValue().symbol }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): GeneratedCompanionObject = factoriesByFactoryClassId.getValue(owner.classId)
    .let { factoryClass ->

      factoryClass.nestedClasses.single { it.name == name } as GeneratedCompanionObject
    }
}
