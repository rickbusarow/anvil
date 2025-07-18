package com.squareup.anvil.compiler.k2.utils.fir

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.k2.utils.names.classId
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.declarations.getKClassArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.exceptions.KotlinIllegalArgumentExceptionWithAttachments
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

public fun FirAnnotation.classId(session: FirSession): ClassId? = fqName(session)?.classId()

public fun FirAnnotation.requireClassId(session: FirSession): ClassId {
  val expandedSymbol = annotationTypeRef.coneType.toRegularClassSymbol(session)
  checkWithAttachment(expandedSymbol != null, { "Annotation is not resolved" }) {
    withFirEntry("annotation", this@requireClassId)
  }
  return expandedSymbol.classId
}

public fun FirAnnotation.replacesArgumentOrNull(session: FirSession): List<FirGetClassCall>? {
  return classListArgumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
  )
}

public fun FirAnnotation.boundTypeArgumentOrNull(session: FirSession): FirGetClassCall? {
  return argumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
    unwrapNamedArguments = true,
  )?.let { it as FirGetClassCall }
}

public fun FirAnnotation.rankArgumentOrNull(session: FirSession): Int? {
  val arg = argumentAt(
    name = Names.rank,
    index = rankIndex(requireClassId(session)),
    unwrapNamedArguments = true,
  ) ?: return null
  return arg.evaluateAs<FirLiteralExpression>(session)?.value as? Int
}

public fun FirAnnotation.requireReplacesArgument(session: FirSession): List<FirGetClassCall> {
  return classListArgumentAt(
    name = Names.replaces,
    index = replacesIndex(requireClassId(session)),
  ).orEmpty()
}

private fun rankIndex(annotationClassId: ClassId): Int {
  return when (annotationClassId) {
    ClassIds.anvilContributesBinding, ClassIds.anvilContributesMultibinding -> 6
    else -> throw NotImplementedError(
      "Couldn't find index of rank argument for $annotationClassId.",
    )
  }
}

private fun replacesIndex(annotationClassId: ClassId): Int {
  return when (annotationClassId) {
    ClassIds.anvilContributesTo -> 1
    ClassIds.anvilContributesBinding, ClassIds.anvilContributesMultibinding -> 2
    ClassIds.anvilContributesSubcomponent -> 4
    else -> errorWithAttachment(
      "Couldn't find index of replaces argument for $annotationClassId.",
    )
  }
}

public fun FirAnnotation.requireScopeArgument(session: FirSession): ConeKotlinType {
  return getKClassArgument(Names.scope, session)
    ?: errorWithAttachment("Scope argument is not resolved yet: ${this@requireScopeArgument.render()}") {
      withFirEntry("scope argument expression", this@requireScopeArgument)
    }
}

public fun FirAnnotation.requireScopeArgument(
  typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
): ConeKotlinType {
  val getClass = requireScopeArgument()
  val ct = getClass.resolveConeType(typeResolveService)
  val typeArgs = ct.typeArguments

  return typeArgs.singleOrNull()?.type ?: ct
}

public fun FirAnnotation.requireScopeArgument(): FirGetClassCall {
  return requireArgumentAt(
    name = Names.scope,
    index = 0,
    unwrapNamedArguments = true,
  ) as FirGetClassCall
}

public fun FirAnnotation.classListArgumentAt(name: Name, index: Int): List<FirGetClassCall>? {
  val arrayArg = argumentAt(
    name = name,
    index = index,
    unwrapNamedArguments = true,
  ) as? FirArrayLiteral
    ?: return null

  return arrayArg.arguments.map { it as FirGetClassCall }
}

/**
 * Returns the argument expression for the given name or index, or throws if it cannot be found.
 *
 * If the argument is specified by name, the [index] is ignored.
 *
 * If the argument is named and [unwrapNamedArguments] is `false`, the [FirNamedArgumentExpression]
 * is returned. If it is `true`, the argument is unwrapped.
 *
 * | unwrapNamedArguments | Result                   |
 * |----------------------|--------------------------|
 * | true                 | `scope = MyScope::class` |
 * | false                | `MyScope::class`         |
 *
 * @param name The name of the argument.
 * @param index The index of the argument if no name is used.
 * @param unwrapNamedArguments If true, the argument is unwrapped if it is a
 *   [FirNamedArgumentExpression].
 * @return The argument expression.
 * @throws KotlinIllegalArgumentExceptionWithAttachments If the argument cannot be found.
 */
public fun FirAnnotation.requireArgumentAt(
  name: Name,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression = argumentAt(
  name = name,
  index = index,
  unwrapNamedArguments = unwrapNamedArguments,
)
  ?: errorWithAttachment(
    """
    Could not find required argument for annotation.
                          Required name: $name
    Required index (if no name is used): $index
    """.trimIndent(),
  ) {
    withFirEntry("annotation", this@requireArgumentAt)
  }

/**
 * Returns the argument expression for the given name or index, or throws if it cannot be found.
 *
 * If the argument is specified by name, the [index] is ignored.
 *
 * If the argument is named and [unwrapNamedArguments] is `false`, the [FirNamedArgumentExpression]
 * is returned. If it is `true`, the argument is unwrapped.
 *
 * | unwrapNamedArguments | Result                   |
 * |----------------------|--------------------------|
 * | true                 | `scope = MyScope::class` |
 * | false                | `MyScope::class`         |
 *
 * @param name The name of the argument.
 * @param index The index of the argument if no name is used.
 * @param unwrapNamedArguments If true, the argument is unwrapped if it is a
 *   [FirNamedArgumentExpression].
 * @return The argument expression.
 * @throws KotlinIllegalArgumentExceptionWithAttachments If the argument cannot be found.
 */
public fun FirAnnotation.argumentAt(
  name: Name,
  index: Int,
  unwrapNamedArguments: Boolean,
): FirExpression? {
  argumentMapping.mapping[name]?.let { return it }

  if (this !is FirAnnotationCall) return null

  var nameUsed = false
  // NB: we have to consider both cases, because deserializer does not create argument mapping
  for ((i, argument) in arguments.withIndex()) {
    if (argument is FirNamedArgumentExpression) {
      nameUsed = true
      if (argument.name == name) {
        return if (unwrapNamedArguments) {
          argument.unwrapArgument()
        } else {
          argument
        }
      }
    } else if (!nameUsed && i == index) {
      return argument
    }
  }

  return null
}

/**
 * Creates a [FirAnnotation] instance with the specified type, argument mapping, source, and
 * use-site target.
 *
 * @param type The [ClassId] representing the annotation type.
 * @param argumentMapping Named parameters. Defaults to empty.
 * @param source `null` or a [org.jetbrains.kotlin.KtFakeSourceElement] is typically fine.
 * @param useSiteTarget `get`, `set`, `field`, `file`, etc. see [AnnotationUseSiteTarget].
 * @return A [FirAnnotation] instance.
 */
public fun createFirAnnotation(
  type: ClassId,
  argumentMapping: FirAnnotationArgumentMapping = FirEmptyAnnotationArgumentMapping,
  source: KtSourceElement? = null,
  useSiteTarget: AnnotationUseSiteTarget? = null,
): FirAnnotation = buildAnnotation {
  this.argumentMapping = argumentMapping
  this.source = source
  this.useSiteTarget = useSiteTarget
  annotationTypeRef = buildResolvedTypeRef {
    coneType = type.constructClassLikeType()
  }
}

public fun createOptInAnnotation(optInType: ClassId, session: FirSession): FirAnnotation {
  return createFirAnnotation(
    type = OptInNames.OPT_IN_CLASS_ID,
    argumentMapping = buildAnnotationArgumentMapping {
      mapping[OptInNames.OPT_IN_ANNOTATION_CLASS] =
        optInType.requireClassLikeSymbol(session).toGetClassCall()
    },
  )
}

/**
 * Creates a [FirAnnotation] instance with the specified type, argument mapping, source, and
 * use-site target.
 *
 * @param type The [ClassId] representing the annotation type.
 * @param containingDeclarationSymbol The symbol of the declaration to be annotated.
 * @param argumentList Named parameters. Defaults to empty.
 * @param source `null` or a [org.jetbrains.kotlin.KtFakeSourceElement] is typically fine.
 * @param useSiteTarget `get`, `set`, `field`, `file`, etc. see [AnnotationUseSiteTarget].
 * @return A [FirAnnotation] instance.
 */
public fun createFirAnnotationCall(
  type: ClassId,
  containingDeclarationSymbol: FirBasedSymbol<*>,
  argumentList: FirArgumentList = FirEmptyArgumentList,
  source: KtSourceElement? = null,
  useSiteTarget: AnnotationUseSiteTarget? = null,
  ktPsiFactory: KtPsiFactory? = null,
): FirAnnotationCall = buildAnnotationCall {
  setAnnotationType(type, ktPsiFactory)
  this.containingDeclarationSymbol = containingDeclarationSymbol
  this.argumentList = argumentList
  this.source = source
  this.useSiteTarget = useSiteTarget
  annotationTypeRef = buildResolvedTypeRef {
    coneType = type.constructClassLikeType()
  }
}
