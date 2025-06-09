plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("com.rickbusarow.anvil")
  id("conventions.minimal")
}

kotlin {
  explicitApi()
}

anvil {
  variantFilter {
    ignore = name == "main"
  }
}

dependencies {
  testImplementation(project(":library"))
  testImplementation(testFixtures("com.rickbusarow.anvil:compiler-utils"))
  testImplementation(libs.dagger2)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  kaptTest(libs.dagger2.compiler)
}
