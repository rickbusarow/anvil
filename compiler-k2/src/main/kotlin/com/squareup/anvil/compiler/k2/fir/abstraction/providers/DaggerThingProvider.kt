package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.InjectedConstructor
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.utils.fir.requireContainingClassSymbol
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import kotlin.properties.Delegates

public val FirSession.daggerThingProvider: DaggerThingProvider by FirSession.sessionComponentAccessor()

public class DaggerThingProvider(session: FirSession) : AnvilFirExtensionSessionComponent(session) {

  @RequiresTypesResolutionPhase
  private var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

  public val injectConstructors: List<InjectedConstructor> by lazyValue {

    session.anvilFirSymbolProvider.injectConstructorSymbols.map { symbol ->
      val containingSymbol = lazyValue { symbol.requireContainingClassSymbol() }
      InjectedConstructor(
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
