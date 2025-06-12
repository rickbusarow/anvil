package com.squareup.anvil.compiler.k2.fir.providers

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.ContributedBinding
import com.squareup.anvil.compiler.k2.fir.ContributedModule
import com.squareup.anvil.compiler.k2.fir.ContributedSupertype
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.utils.fir.boundTypeArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.contributesToAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.getContributesBindingAnnotations
import com.squareup.anvil.compiler.k2.utils.fir.rankArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.replacesArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.requireTargetClassId
import com.squareup.anvil.compiler.k2.utils.fir.resolveConeType
import com.squareup.anvil.compiler.k2.utils.names.bindingModuleSibling
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import kotlin.properties.Delegates

public val FirSession.scopedContributionProvider: ScopedContributionProvider by FirSession.sessionComponentAccessor()

public class ScopedContributionProvider(session: FirSession) :
  AnvilFirExtensionSessionComponent(session) {

  @RequiresTypesResolutionPhase
  private var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

  @RequiresTypesResolutionPhase
  public val contributedModules: List<ContributedModule> by lazyValue {
    session.anvilFirSymbolProvider.contributesModulesSymbols.flatMap { symbol ->
      symbol.contributesToAnnotations(session).map { annotation ->

        ContributedModule(
          scopeType = lazyValue {
            annotation.requireScopeArgument(typeResolveService).requireClassId()
          },
          contributedType = symbol.classId,
          replaces = lazyValue { annotation.replacesClassIds() },
        )
      }
    }
  }

  @RequiresTypesResolutionPhase
  public val contributedSupertypes: List<ContributedSupertype> by lazyValue {
    session.anvilFirSymbolProvider.contributesSupertypeSymbols
      .flatMap { symbol ->
        symbol.contributesToAnnotations(session).map { annotation ->

          ContributedSupertype(
            scopeType = lazyValue {
              annotation.requireScopeArgument(typeResolveService).requireClassId()
            },
            contributedType = symbol.classId,
            replaces = lazyValue { annotation.replacesClassIds() },
          )
        }
      }
  }

  @RequiresTypesResolutionPhase
  private val contributedBindingsAndBindingModules = lazyValue {
    session.anvilFirSymbolProvider.contributesBindingSymbols.flatMap { symbol ->

      symbol.getContributesBindingAnnotations(session).flatMap { annotation ->

        val boundType = lazyValue {
          annotation.boundTypeArgumentOrNull(session)
            ?.resolveConeType(typeResolveService)
            ?.requireClassId()
            ?: symbol.getSuperTypes(
              useSiteSession = session,
              recursive = false,
              lookupInterfaces = true,
            )
              .singleOrNull()
              ?.requireClassId()
            ?: errorWithAttachment("No supertype found for @ContributesBinding type") {
              withFirSymbolEntry("annotated class", symbol)
            }
        }

        val scopeType = lazyValue {
          annotation.requireScopeArgument(typeResolveService).requireClassId()
        }
        val replaces = lazyValue { annotation.replacesClassIds() }

        listOf(
          ContributedBinding(
            scopeType = scopeType,
            boundType = boundType,
            contributedType = symbol.classId,
            replaces = replaces,
            rank = annotation.rankArgumentOrNull(session) ?: ContributesBinding.RANK_NORMAL,
            ignoreQualifier = false,
            isMultibinding = false,
            bindingModule = symbol.classId.bindingModuleSibling,
            qualifier = null,
          ),
          ContributedModule(
            scopeType = scopeType,
            contributedType = symbol.classId.bindingModuleSibling,
            replaces = replaces,
          ),
        )
      }
    }
  }

  @RequiresTypesResolutionPhase
  public val contributedBindings: List<ContributedBinding> by contributedBindingsAndBindingModules.map {
    it.filterIsInstance<ContributedBinding>()
  }

  @RequiresTypesResolutionPhase
  public val contributedBindingModules: List<ContributedModule> by contributedBindingsAndBindingModules.map {
    it.filterIsInstance<ContributedModule>()
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

  private fun FirAnnotationCall.replacesClassIds() = replacesArgumentOrNull(session)
    ?.map { it.requireTargetClassId() }
    .orEmpty()
}
