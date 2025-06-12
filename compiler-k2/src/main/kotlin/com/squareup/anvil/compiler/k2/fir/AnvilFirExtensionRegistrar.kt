package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.fir.extensions.SupertypeProcessorExtension
import com.squareup.anvil.compiler.k2.fir.extensions.TopLevelClassProcessorExtension
import com.squareup.anvil.compiler.k2.fir.providers.AnvilFirDependencyHintProvider
import com.squareup.anvil.compiler.k2.fir.providers.AnvilFirProcessorProvider
import com.squareup.anvil.compiler.k2.fir.providers.AnvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.providers.DaggerThingProvider
import com.squareup.anvil.compiler.k2.fir.providers.ScopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.providers.ScopedMergeProvider
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    +FirExtensionSessionComponent.Factory { AnvilFirContext(messageCollector, it) }

    +::SupertypeProcessorExtension
    +::TopLevelClassProcessorExtension

    +::AnvilFirDependencyHintProvider
    +::AnvilFirProcessorProvider
    +::AnvilFirSymbolProvider
    +::ScopedContributionProvider
    +::ScopedMergeProvider
    +::DaggerThingProvider

    val factories = ServiceLoader.load(FirExtensionSessionComponent.Factory::class.java)

    for (factory in factories) {
      +factory
    }
  }
}
