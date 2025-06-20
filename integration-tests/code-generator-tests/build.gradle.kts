plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  id("com.rickbusarow.anvil")
  id("conventions.minimal")
}

anvil {
  generateDaggerFactories = true
}

dependencies {
  anvil(project(":code-generator"))

  implementation(libs.dagger2)

  testImplementation(testFixtures("com.rickbusarow.anvil:compiler-utils"))
  testImplementation(libs.junit4)
  testImplementation(libs.truth)

  // Notice that Kapt is only enabled in tests for compiling our Dagger components. We also
  // generate a Dagger component in one of the code generators and this custom code generator
  // is triggered in tests.
  kaptTest(libs.dagger2.compiler)
}
