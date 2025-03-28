package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.Name

public sealed interface PendingMemberCallable {
  public val name: Name
  public val returnType: FirLazyValue<ConeKotlinType>
  public val ownerSymbol: FirLazyValue<FirClassSymbol<*>>
  public val key: GeneratedDeclarationKey
  public val visibility: Visibility
  public val annotations: FirLazyValue<List<FirAnnotation>>
}

public class PendingMemberProperty(
  override val name: Name,
  override val returnType: FirLazyValue<ConeKotlinType>,
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  public val isVal: Boolean,
  override val visibility: Visibility,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
  public val initializer: FirLazyValue<FirExpression>? = null,
  override val annotations: FirLazyValue<List<FirAnnotation>> = cachesFactory.createLazyValue { emptyList() },
) : PendingMemberCallable {

  public val generatedProperty: FirLazyValue<FirProperty> = cachesFactory.createLazyValue {
    firExtension.createMemberProperty(
      owner = ownerSymbol.getValue(),
      key = key,
      name = name,
      returnType = returnType.getValue(),
      isVal = isVal,
    ) {
      this@createMemberProperty.visibility = this@PendingMemberProperty.visibility
    }.apply {
      if (this@PendingMemberProperty.initializer != null) {
        replaceInitializer(this@PendingMemberProperty.initializer.getValue())
      }
      replaceAnnotations(this@PendingMemberProperty.annotations.getValue())
    }
  }
}

public class PendingMemberFunction(
  override val name: Name,
  override val returnType: FirLazyValue<ConeKotlinType>,
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  override val visibility: Visibility,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
  public val valueParameters: FirLazyValue<List<Pair<Name, ConeKotlinType>>> = cachesFactory.createLazyValue { emptyList() },
  override val annotations: FirLazyValue<List<FirAnnotation>> = cachesFactory.createLazyValue { emptyList() },
) : PendingMemberCallable {

  public val generatedFunction: FirLazyValue<FirSimpleFunction> = cachesFactory.createLazyValue {
    firExtension.createMemberFunction(
      owner = ownerSymbol.getValue(),
      key = key,
      name = name,
      returnType = returnType.getValue(),
    ) {
      this@createMemberFunction.visibility = this@PendingMemberFunction.visibility
      for ((name, type) in valueParameters.getValue()) {
        valueParameter(name = name, type = type)
      }
    }.apply {
      replaceAnnotations(this@PendingMemberFunction.annotations.getValue())
    }
  }
}
