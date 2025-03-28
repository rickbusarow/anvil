package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.internal.InternalAnvilApi
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.util.AbstractArrayMapOwner
import org.jetbrains.kotlin.util.ComponentArrayOwner
import org.jetbrains.kotlin.util.TypeRegistry
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

public class AnvilSessionComponentAccessor<T : AnvilSessionComponent>(
  private val keyQualifiedName: String,
  id: Int,
  public val default: T? = null,
) : AbstractArrayMapOwner.AbstractArrayMapAccessor<AnvilSessionComponent, AnvilSessionComponent, T>(
  id,
),
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

private class DelegatingComponentArrayOwner(
  override val typeRegistry: TypeRegistry<AnvilSessionComponent, AnvilSessionComponent>,
) : ComponentArrayOwner<AnvilSessionComponent, AnvilSessionComponent>()

public abstract class AnvilSessionComponent(
  session: FirSession,
) : HasFirCachesFactory(session.firCachesFactory),
  HasAnvilFirContext {

  override val anvilContext: AnvilFirContext by lazyValue { session.anvilContext }
}

public class MyAnvilSessionComponent(session: FirSession) : AnvilSessionComponent(session)

// public val AnvilFirContext.myAnvilSessionComponent: MyAnvilSessionComponent by AnvilFirContext.sessionComponentAccessor()

public class AnvilFirExtensionSessionComponent2(session: FirSession) :
  FirExtensionSessionComponent(session) {

  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = session.firCachesFactory.createLazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {

    return session.firCachesFactory.createLazyValue { initializer() }
  }

  protected inline fun <reified T> lazySymbols(predicate: LookupPredicate): FirLazyValue<List<T>> {
    return lazyValue {
      session.predicateBasedProvider.getSymbolsByPredicate(predicate)
        .filterIsInstance<T>()
    }
  }
}

public abstract class AnvilFirExtensionSessionComponent(
  session: FirSession,
) : FirExtensionSessionComponent(session) {

  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = session.firCachesFactory.createLazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {

    return session.firCachesFactory.createLazyValue { initializer() }
  }

  protected inline fun <reified T> lazySymbols(predicate: LookupPredicate): FirLazyValue<List<T>> {
    return lazyValue {
      session.predicateBasedProvider.getSymbolsByPredicate(predicate)
        .filterIsInstance<T>()
    }
  }

  // protected inline fun <reified T> lazySymbols(predicate: DeclarationPredicate): FirLazyValue<List<T>> {
  //   return lazyValue {
  //     session.predicateBasedProvider.getSymbolsByPredicate(predicate)
  //       .filterIsInstance<T>()
  //   }
  // }
}

@InternalAnvilApi
@ExperimentalAnvilApi
public object AdditionalProcessorsHolder {
  public val additionalProcessors: ThreadLocal<List<AnvilFirProcessor.Factory>> =
    ThreadLocal.withInitial { emptyList() }
}
