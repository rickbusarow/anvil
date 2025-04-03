package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.annotations.internal.InternalAnvilApi
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.properties.Delegates

public abstract class HasFirCachesFactory(
  protected val cachesFactory: FirCachesFactory,
) {
  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = lazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {
    return cachesFactory.createLazyValue { initializer() }
  }
}

@RequiresOptIn("Is this actually necessary?", level = RequiresOptIn.Level.WARNING)
public annotation class ProcessorFlushingCheck

/**
 * @param session
 * @property isFlushing If true, this processor will be invoked only after all non-isFlushing processors of the same type
 */
public sealed class AnvilFirProcessor(
  protected val session: FirSession,
  @property:ProcessorFlushingCheck
  public val isFlushing: Boolean,
) : HasFirCachesFactory(session.firCachesFactory),
  HasAnvilFirContext {

  override val anvilContext: AnvilFirContext
    get() = session.anvilContext

  protected fun lazySymbols(
    predicate: LookupPredicate,
  ): FirLazyValue<List<FirBasedSymbol<*>>> = lazyValue {
    session.predicateBasedProvider.getSymbolsByPredicate(predicate)
  }

  protected inline fun <reified T> lazySymbolsOf(
    predicate: LookupPredicate,
  ): FirLazyValue<List<T>> = lazySymbols(predicate).map { it.filterIsInstance<T>() }

  public fun interface Factory {
    public fun create(session: FirSession): AnvilFirProcessor
  }
}

public abstract class AbstractAnvilFirProcessorFactory(
  private val initializer: (FirSession) -> AnvilFirProcessor,
) : AnvilFirProcessor.Factory {
  final override fun create(session: FirSession): AnvilFirProcessor =
    initializer(session)
}

public abstract class TopLevelClassProcessor(session: FirSession) :
  AnvilFirProcessor(session, isFlushing = false) {

  protected var firExtension: FirDeclarationGenerationExtension by Delegates.notNull()
    private set

  @InternalAnvilApi
  public fun bindFirExtension(firExtension: FirDeclarationGenerationExtension) {
    this.firExtension = firExtension
  }

  public abstract fun getTopLevelClassIds(): Set<ClassId>
  public open fun hasPackage(packageFqName: FqName): Boolean = false

  public abstract fun generateTopLevelClassLikeDeclaration(classId: ClassId): GeneratedTopLevelClass

  public open fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): GeneratedNestedClassLikeDeclaration? = null

  public open fun getCallableNamesForClass(
    classSymbol: FirClassLikeSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> = emptySet()

  public open fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> = emptyList()
}

public abstract class SupertypeProcessor(
  session: FirSession,
  isFlushing: Boolean,
) : AnvilFirProcessor(session, isFlushing) {

  public abstract fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean
  public open fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<ConeKotlinType> = emptyList()

  public open fun computeAdditionalSupertypesForGeneratedNestedClass(
    klass: FirRegularClass,
    typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
  ): List<FirResolvedTypeRef> = emptyList()
}

public abstract class AnnotatingSupertypeProcessor(session: FirSession) :
  SupertypeProcessor(session, isFlushing = true) {

  @OptIn(RequiresTypesResolutionPhase::class)
  public final override fun addSupertypes(
    classLikeDeclaration: FirClassLikeDeclaration,
    resolvedSupertypes: List<FirResolvedTypeRef>,
    typeResolver: TypeResolveService,
  ): List<ConeKotlinType> {

    classLikeDeclaration.replaceAnnotations(
      classLikeDeclaration.annotations + generateAnnotation(classLikeDeclaration),
    )

    return emptyList()
  }

  @RequiresTypesResolutionPhase
  public abstract fun generateAnnotation(
    classLikeDeclaration: FirClassLikeDeclaration,
  ): FirAnnotationCall
}
