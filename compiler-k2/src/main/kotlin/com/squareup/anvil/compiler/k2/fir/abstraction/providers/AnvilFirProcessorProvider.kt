package com.squareup.anvil.compiler.k2.fir.abstraction.providers

import com.squareup.anvil.annotations.internal.InternalAnvilApi
import com.squareup.anvil.compiler.k2.fir.AdditionalProcessorsHolder
import com.squareup.anvil.compiler.k2.fir.AnvilFirExtensionSessionComponent
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.ProcessorFlushingCheck
import com.squareup.anvil.compiler.k2.fir.SupertypeProcessor
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.getValue
import java.util.ServiceLoader

public val FirSession.anvilFirProcessorProvider: AnvilFirProcessorProvider by FirSession.sessionComponentAccessor()

public class AnvilFirProcessorProvider(session: FirSession) :
  AnvilFirExtensionSessionComponent(session) {

  @OptIn(InternalAnvilApi::class)
  private val processors by lazyValue {

    val loaded = ServiceLoader.load(
      AnvilFirProcessor.Factory::class.java,
      AnvilFirProcessor.Factory::class.java.classLoader,
    )

    val threadLocal = AdditionalProcessorsHolder.additionalProcessors.get()

    loaded.plus(threadLocal)
      .map { it.create(session) }
      .sortedWith(
        compareBy(
          {
            @OptIn(ProcessorFlushingCheck::class)
            it.isFlushing
          },
          { it::class.qualifiedName },
        ),
      )
      .groupBy {
        when (it) {
          is SupertypeProcessor -> SupertypeProcessor::class
          is TopLevelClassProcessor -> TopLevelClassProcessor::class
        }
      }
  }

  public val topLevelClassProcessors: List<TopLevelClassProcessor> by lazyValue {
    @Suppress("UNCHECKED_CAST")
    processors[TopLevelClassProcessor::class].orEmpty() as List<TopLevelClassProcessor>
  }

  public val supertypeProcessors: List<SupertypeProcessor> by lazyValue {
    @Suppress("UNCHECKED_CAST")
    processors[SupertypeProcessor::class].orEmpty() as List<SupertypeProcessor>
  }
}
