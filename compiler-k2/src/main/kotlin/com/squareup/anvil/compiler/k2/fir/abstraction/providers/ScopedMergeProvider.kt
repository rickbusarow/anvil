package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.MergedComponent
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.utils.fir.classListArgumentAt
import com.squareup.anvil.compiler.k2.utils.fir.replacesArgumentOrNull
import com.squareup.anvil.compiler.k2.utils.fir.requireAnnotationCall
import com.squareup.anvil.compiler.k2.utils.fir.requireClassId
import com.squareup.anvil.compiler.k2.utils.fir.requireScopeArgument
import com.squareup.anvil.compiler.k2.utils.fir.requireTargetClassId
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import kotlin.properties.Delegates

public val FirSession.scopedMergeProvider: ScopedMergeProvider by FirSession.sessionComponentAccessor()

public class ScopedMergeProvider(session: FirSession) :
  AnvilFirExtensionSessionComponent(session) {

  @RequiresTypesResolutionPhase
  private var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

  @RequiresTypesResolutionPhase
  public val mergedComponents: List<MergedComponent> by lazyValue {
    session.anvilFirSymbolProvider.mergeComponentSymbols.map { symbol ->

      val mergeAnnotation = symbol.requireAnnotationCall(
        classId = ClassIds.anvilMergeComponent,
        session = session,
        resolveArguments = true,
      )

      MergedComponent(
        scopeType = lazyValue {
          mergeAnnotation.requireScopeArgument(typeResolveService).requireClassId()
        },
        targetType = symbol.classId,
        modules = lazyValue {
          mergeAnnotation.classListArgumentAt(Names.modules, index = 1)
            ?.map { it.requireTargetClassId(typeResolveService) }
            .orEmpty()
        },
        dependencies = lazyValue {
          mergeAnnotation.classListArgumentAt(Names.dependencies, index = 2)
            ?.map { it.requireTargetClassId(typeResolveService) }
            .orEmpty()
        },
        exclude = lazyValue {
          mergeAnnotation.classListArgumentAt(Names.exclude, index = 3)
            ?.map { it.requireTargetClassId(typeResolveService) }
            .orEmpty()
        },
        containingDeclaration = lazyValue {
          session.firProvider.getFirClassifierByFqName(symbol.classId)!!
        },
        mergeAnnotationCall = lazyValue { mergeAnnotation },
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

  private fun FirAnnotationCall.replacesClassIds() = replacesArgumentOrNull(session)
    ?.map { it.requireTargetClassId() }
    .orEmpty()
}
