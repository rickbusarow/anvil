package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Modality
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

public sealed interface GeneratedMemberCallable {
  public val name: Name
  public val returnType: FirLazyValue<ConeKotlinType>
  public val ownerSymbol: FirLazyValue<FirClassSymbol<*>>
  public val key: GeneratedDeclarationKey
  public val visibility: Visibility
  public val modality: Modality
  public val annotations: FirLazyValue<List<FirAnnotation>>
}

public class GeneratedMemberProperty(
  override val name: Name,
  override val returnType: FirLazyValue<ConeKotlinType>,
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  public val isVal: Boolean,
  override val visibility: Visibility,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
  override val modality: Modality = Modality.FINAL,
  public val initializer: FirLazyValue<FirExpression>? = null,
  override val annotations: FirLazyValue<List<FirAnnotation>> =
    cachesFactory.createLazyValue { emptyList() },
) : GeneratedMemberCallable {

  public val generatedProperty: FirLazyValue<FirProperty> = cachesFactory.createLazyValue {
    firExtension.createMemberProperty(
      owner = ownerSymbol.getValue(),
      key = key,
      name = name,
      returnType = returnType.getValue(),
      isVal = isVal,
    ) {
      this@createMemberProperty.visibility = this@GeneratedMemberProperty.visibility
      this@createMemberProperty.modality = this@GeneratedMemberProperty.modality
    }.apply {
      if (this@GeneratedMemberProperty.initializer != null) {
        replaceInitializer(this@GeneratedMemberProperty.initializer.getValue())
      }
      replaceAnnotations(this@GeneratedMemberProperty.annotations.getValue())
    }
  }
}

public class GeneratedMemberFunction(
  override val name: Name,
  override val returnType: FirLazyValue<ConeKotlinType>,
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  override val visibility: Visibility,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
  override val modality: Modality = Modality.FINAL,
  public val valueParameters: FirLazyValue<List<ValueParameter>> =
    cachesFactory.createLazyValue { emptyList() },
  override val annotations: FirLazyValue<List<FirAnnotation>> =
    cachesFactory.createLazyValue { emptyList() },
) : GeneratedMemberCallable {

  public val generatedFunction: FirLazyValue<FirSimpleFunction> = cachesFactory.createLazyValue {
    firExtension.createMemberFunction(
      owner = ownerSymbol.getValue(),
      key = key,
      name = name,
      returnType = returnType.getValue(),
    ) {
      this@createMemberFunction.visibility = this@GeneratedMemberFunction.visibility
      this@createMemberFunction.modality = this@GeneratedMemberFunction.modality
      for ((name, type) in valueParameters.getValue()) {
        valueParameter(name = name, type = type)
      }
    }.apply {
      replaceAnnotations(this@GeneratedMemberFunction.annotations.getValue())

      // replaceBody(
      //   buildSingleExpressionBlock(
      //     buildReturnExpression {
      //       target = FirFunctionTarget(null, isLambda = false)
      //       result = buildFunctionCall {
      //         calleeReference = buildResolvedNamedReference {
      //           name = returnType.getValue().classId!!.shortClassName
      //           resolvedSymbol = ownerSymbol.getValue()
      //         }
      //       }
      //     },
      //   ),
      // )
    }
  }
}
