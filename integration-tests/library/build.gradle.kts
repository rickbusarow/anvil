plugins {
  alias(libs.plugins.kotlin.jvm)
  id("com.rickbusarow.anvil")
  id("conventions.minimal")
}

if (libs.versions.config.generateDaggerFactoriesWithAnvil.get().toBoolean()) {
  anvil {
    generateDaggerFactories = true
  }
} else {
  apply(plugin = "org.jetbrains.kotlin.kapt")

  dependencies {
    "kapt"(libs.dagger2.compiler)

    // Necessary because this is what dagger uses when it runs to support instantiating annotations at runtime
    implementation(libs.auto.value.annotations)
    "kapt"(libs.auto.value.processor)
  }
}

kotlin {
  explicitApi()
}

dependencies {
  api(libs.dagger2)
}
