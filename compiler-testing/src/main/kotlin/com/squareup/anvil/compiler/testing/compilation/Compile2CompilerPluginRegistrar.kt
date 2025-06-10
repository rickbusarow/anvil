package com.squareup.anvil.compiler.testing.compilation

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
internal class Compile2CompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val factories = threadLocalParams.get()?.firExtensionFactories

    if (!factories.isNullOrEmpty()) {
      FirExtensionRegistrarAdapter.registerExtension(
        Compile2FirExtensionRegistrar(
          messageCollector = configuration.messageCollector,
          factories = factories,
        ),
      )
    }
  }

  companion object {
    internal val threadLocalParams = ThreadLocal<Compile2RegistrarParams>()
  }

  data class Compile2RegistrarParams(
    val firExtensionFactories: List<FirExtension.Factory<*>>,
  )
}

public class Compile2FirExtensionRegistrar(
  private val messageCollector: MessageCollector,
  private val factories: List<FirExtension.Factory<*>>,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {

    check(factories.isEmpty()) {
      """
        |Custom factories are specified, but they're not supported currently...
        |
        |${factories.joinToString(separator = "\n") { it::class.qualifiedName ?: "<unknown>" }}
      """.trimMargin()
    }
  }
}
