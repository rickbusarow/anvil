package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.util.AbstractArrayMapOwner
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public class AnvilSessionComponentAccessor<T : AnvilSessionComponent>(
  private val keyQualifiedName: String,
  id: Int,
  public val default: T? = null,
) : AbstractArrayMapOwner
  .AbstractArrayMapAccessor<AnvilSessionComponent, AnvilSessionComponent, T>(id),
  ReadOnlyProperty<AbstractArrayMapOwner<AnvilSessionComponent, AnvilSessionComponent>, AnvilSessionComponent> {

  override fun getValue(
    thisRef: AbstractArrayMapOwner<AnvilSessionComponent, AnvilSessionComponent>,
    property: KProperty<*>,
  ): AnvilSessionComponent {
    TODO("Not yet implemented")
  }

  // public operator fun getValue(thisRef: AnvilFirContext, property: KProperty<*>): T {
  //   return extractValue(thisRef)
  //     ?: default
  //     ?: error("No '$keyQualifiedName'($id) in array owner: $thisRef")
  // }
}
