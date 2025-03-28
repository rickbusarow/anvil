package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.utils.exceptions.withFirSymbolEntry
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment

public fun FirConstructorSymbol.requireContainingClassSymbol(): FirRegularClassSymbol {
  val symbol = getContainingClassSymbol()
  checkWithAttachment(
    symbol != null,
    { "Could not resolve a containing class symbol for the constructor." },
  ) {
    withFirSymbolEntry("constructor", this@requireContainingClassSymbol)
  }
  return symbol as FirRegularClassSymbol
}
