package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createTopLevelProperty
import org.jetbrains.kotlin.name.CallableId

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
internal class GeneratedTopLevelProperty(
  val callableId: CallableId,
  val key: GeneratedDeclarationKey,
  val visibility: Visibility,
  val annotations: FirLazyValue<List<FirAnnotation>>,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
) {

  val generatedProperty: FirLazyValue<FirProperty> = cachesFactory.createLazyValue {
    firExtension.createTopLevelProperty(
      key = key,
      callableId = callableId,
      returnType = firExtension.session.builtinTypes.unitType.coneType,
    ) {
      this@createTopLevelProperty.visibility = this@GeneratedTopLevelProperty.visibility
    }.apply {
      replaceAnnotations(this@GeneratedTopLevelProperty.annotations.getValue())
    }
  }
}
