package com.squareup.anvil.annotations.internal

import com.squareup.anvil.annotations.ContributesBinding
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * An internal Anvil API used to propagate binding metadata from contributed bindings. Should not be
 * used directly.
 *
 * Note the reader of this should also check the single binds function in the annotated module to
 * read the bound type, possible qualifier, and possible `@MapKey`/`@IntoSet` annotations.
 *
 * @property originClass the origin class that contributed the binding. This is important to include
 *   because the contributed binding may be an `object` and cannot be specified as a binding
 *   parameter.
 * @property isMultibinding Whether this is a multibinding.
 * @property rank The rank of the contributed binding. Corresponds to a [ContributesBinding.rank].
 * @property qualifierKey The computed key of the qualifier annotation if present. Empty otherwise.
 */
@Target(CLASS)
@Retention(RUNTIME)
@Repeatable
public annotation class InternalBindingMarker(
  val originClass: KClass<*>,
  val isMultibinding: Boolean,
  val rank: Int = Int.MIN_VALUE,
  val qualifierKey: String = "",
)
