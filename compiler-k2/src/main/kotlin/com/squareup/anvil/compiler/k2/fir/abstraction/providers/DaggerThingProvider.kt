package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.Qualifier
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.utils.fir.requireContainingClassSymbol
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import kotlin.properties.Delegates

public val FirSession.daggerThingProvider: DaggerThingProvider by FirSession.sessionComponentAccessor()

public class DaggerThingProvider(session: FirSession) : AnvilFirExtensionSessionComponent(session) {

  @RequiresTypesResolutionPhase
  private var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

  public val injectConstructors: List<InjectedClass> by lazyValue {
    session.anvilFirSymbolProvider.injectConstructorSymbols.map { symbol ->
      val containingSymbol = lazyValue { symbol.requireContainingClassSymbol() }
      InjectedClass(
        classId = lazyValue { symbol.callableId.classId!! },
        qualifier = null,
        containingDeclaration = containingSymbol,
        constructor = lazyValue { symbol },
      )
    }
  }

  private var typeResolverSet = false
  internal fun isInitialized() = typeResolverSet

  @OptIn(RequiresTypesResolutionPhase::class)
  internal fun bindTypeResolveService(
    typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  ) {
    if (!typeResolverSet) {
      typeResolverSet = true
      this.typeResolveService = typeResolveService
    }
  }
}

public class InjectedClass(
  public val classId: FirLazyValue<ClassId>,
  public val qualifier: Qualifier?,
  public val containingDeclaration: FirLazyValue<FirRegularClassSymbol>,
  public val constructor: FirLazyValue<FirConstructorSymbol>,
)

public sealed interface ValueParameter {
  public val name: Name
  public val type: ClassId
  public val nullable: Boolean
  public val visibility: Visibility
  public val qualifier: Qualifier?
}

public data class ConstructorValueParameter(
  override val name: Name,
  override val type: ClassId,
  override val nullable: Boolean,
  override val visibility: Visibility,
  override val qualifier: Qualifier?,
) : ValueParameter

public data class ConstructorProperty(
  override val name: Name,
  override val type: ClassId,
  public val mutable: Boolean,
  override val nullable: Boolean,
  override val visibility: Visibility,
  override val qualifier: Qualifier?,
) : ValueParameter
