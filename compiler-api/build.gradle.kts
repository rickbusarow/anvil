plugins {
  id("conventions.library")
  id("conventions.publish")
}

conventions {
  explicitApi = true
}

publish {
  configurePom(
    artifactId = "compiler-api",
    pomName = "Anvil Compiler API",
    pomDescription = "API definitions for creating custom code generators that integrate with Anvil",
  )
}

dependencies {
  api(project(":annotations"))
  api(libs.kotlin.compiler)

  implementation(platform(libs.kotlin.bom))

  testImplementation(libs.junit4)
  testImplementation(libs.kase)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
}
