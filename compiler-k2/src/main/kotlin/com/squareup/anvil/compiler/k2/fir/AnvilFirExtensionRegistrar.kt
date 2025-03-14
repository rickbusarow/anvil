package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.constructor.inject.FirInjectConstructorFactoryGenerationExtension
import com.squareup.anvil.compiler.k2.fir.contributions.ContributesBindingFirExtension
import com.squareup.anvil.compiler.k2.fir.contributions.ContributesBindingSessionComponent
import com.squareup.anvil.compiler.k2.fir.merging.AnvilFirAnnotationMergingExtension
import com.squareup.anvil.compiler.k2.fir.merging.AnvilFirInterfaceMergingExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import java.util.ServiceLoader

public class AnvilFirExtensionRegistrar(
  private val messageCollector: MessageCollector,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    +FirExtensionSessionComponent.Factory { AnvilFirContext(messageCollector, it) }

    FirExtensionSessionComponent.Factory { session ->
      val ctx = AnvilFirContext2(session, messageCollector)
      AnvilFirProcessorProvider(ctx)
    }
      .unaryPlus()

    +::SupertypeProcessorExtension
    +::TopLevelClassProcessorExtension

    val factories = ServiceLoader.load(FirExtensionSessionComponent.Factory::class.java)

    for (factory in factories) {
      +factory
    }

    +::ContributesBindingFirExtension
    +::FirInjectConstructorFactoryGenerationExtension
    +::AnvilFirAnnotationMergingExtension
    +::AnvilFirInterfaceMergingExtension
    +::ContributesBindingSessionComponent
  }
}
