package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue

public abstract class AnvilSessionComponent(
  session: FirSession,
) : HasFirCachesFactory(session.firCachesFactory),
  HasAnvilFirContext {

  override val anvilContext: AnvilFirContext by lazyValue { session.anvilContext }
}

public abstract class HasFirCachesFactory(
  protected val cachesFactory: FirCachesFactory,
) {
  protected inline fun <T, R> FirLazyValue<T>.map(
    crossinline transform: (T) -> R,
  ): FirLazyValue<R> = lazyValue { transform(this.getValue()) }

  protected inline fun <T> lazyValue(crossinline initializer: () -> T): FirLazyValue<T> {
    return cachesFactory.createLazyValue { initializer() }
  }
}
