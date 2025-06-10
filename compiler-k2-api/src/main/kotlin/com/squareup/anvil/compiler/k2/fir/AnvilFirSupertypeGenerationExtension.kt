package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension

public abstract class AnvilFirSupertypeGenerationExtension(
  session: FirSession,
) : FirSupertypeGenerationExtension(session),
  HasAnvilFirContext {

  override val anvilContext: AnvilFirContext
    get() = session.anvilContext
}
