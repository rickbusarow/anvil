plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("conventions.minimal")
}

conventions {
  kotlinCompilerArgs.add(
    "-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi",
  )
}

dependencies {
  api("com.rickbusarow.anvil:compiler-api")
  implementation("com.rickbusarow.anvil:compiler-utils")

  compileOnly(libs.auto.service.annotations)
  kapt(libs.auto.service.processor)

  testImplementation(testFixtures("com.rickbusarow.anvil:compiler-utils"))
  testImplementation(libs.junit4)
  testImplementation(libs.truth)
}
