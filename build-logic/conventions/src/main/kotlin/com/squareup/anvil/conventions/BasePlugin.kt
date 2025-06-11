package com.squareup.anvil.conventions

import com.rickbusarow.kgx.fromInt
import com.rickbusarow.kgx.getValue
import com.rickbusarow.kgx.javaExtension
import com.rickbusarow.kgx.provideDelegate
import com.rickbusarow.kgx.withKotlinJvmPlugin
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

abstract class BasePlugin : Plugin<Project> {

  open fun beforeApply(target: Project) = Unit
  open fun afterApply(target: Project) = Unit

  abstract fun Project.jvmTargetInt(): Int

  final override fun apply(target: Project) {

    val extension = target.extensions.create("conventions", ConventionsExtension::class.java)

    beforeApply(target)

    target.plugins.apply("base")

    target.plugins.apply(KtlintConventionPlugin::class.java)

    configureGradleProperties(target)

    configureJava(target)

    target.plugins.withType(KotlinBasePluginWrapper::class.java) {
      configureKotlin(target, extension)
    }

    configureTests(target)

    afterApply(target)
  }

  private fun configureGradleProperties(target: Project) {
    target.version = target.property("VERSION_NAME") as String
    target.group = target.property("GROUP") as String
  }

  private fun configureKotlin(
    target: Project,
    extension: ConventionsExtension,
  ) {

    target.tasks.withType(KotlinCompile::class.java).configureEach { task ->
      task.compilerOptions {
        allWarningsAsErrors.set(
          target.libs.versions.config.warningsAsErrors.get().toBoolean() ||
            extension.warningsAsErrors.get(),
        )

        val sourceSetName = task.sourceSetName.getOrElse(
          task.name.substringAfter("compile")
            .substringBefore("Kotlin")
            .replaceFirstChar(Char::lowercase),
        )

        // Only add the experimental opt-in if the project has the `annotations` dependency,
        // otherwise the compiler will throw a warning and fail in CI.
        if (target.hasAnnotationDependency(sourceSetName)) {
          freeCompilerArgs.add("-opt-in=com.squareup.anvil.annotations.ExperimentalAnvilApi")
        }

        freeCompilerArgs.addAll(extension.kotlinCompilerArgs.get())
        // We do not yet support K2, and KAPT + Kotlin 2.0 generates a warning about falling back to
        // language version 1.9. Because we treat all warnings as errors, we need to suppress this
        // specific warning to not fail the build. Related YT ticket:
        // https://youtrack.jetbrains.com/issue/KT-68400/K2-w-Kapt-currently-doesnt-support-language-version-2.0.-Falling-back-to-1.9.
        freeCompilerArgs.add("-Xsuppress-version-warnings")

        fun isTestSourceSet(): Boolean {
          val regex = """(?:gradle|Unit|[aA]ndroid)Test""".toRegex()
          return sourceSetName == "test" || sourceSetName.matches(regex)
        }

        if (extension.explicitApi.get() && !isTestSourceSet()) {
          freeCompilerArgs.add("-Xexplicit-api=strict")
        }

        languageVersion.set(
          target.libs.versions.kotlinLanguageVersion.map { KotlinVersion.fromVersion(it) },
        )

        jvmTarget.set(JvmTarget.fromInt(target.jvmTargetInt()))
      }
    }
  }

  /**
   * This is an imperfect but pretty good heuristic
   * to determine if the receiver has the `annotations` dependency,
   * without actually resolving the dependency graph.
   */
  private fun Project.hasAnnotationDependency(sourceSetName: String): Boolean {

    val compileClasspath = when (sourceSetName) {
      "main" -> "compileClasspath"
      else -> "${sourceSetName}CompileClasspath"
    }
      .let { configurations.findByName(it) }
      ?: return false

    val configs = generateSequence(sequenceOf(compileClasspath)) { configs ->
      configs.flatMap { it.extendsFrom }
        .mapNotNull { configurations.findByName(it.name) }
        .takeIf { it.iterator().hasNext() }
    }
      .flatten()
      .distinct()

    // The -api and -utils projects declare the annotations as an `api` dependency.
    val providingProjects = setOf("annotations", "compiler-api", "compiler-utils")

    val projectGroup = this@hasAnnotationDependency.group

    return configs.any { cfg ->
      cfg.dependencies.any { dep ->
        dep.group == projectGroup && dep.name in providingProjects
      }
    }
  }

  private fun configureJava(target: Project) {
    // Sets the toolchain and target versions for java compilation. This waits for the 'java-base'
    // plugin instead of just 'java' for the sake of the KMP integration test project.
    target.plugins.withId("java-base") {
      target.javaExtension.toolchain {
        it.languageVersion.set(JavaLanguageVersion.of(target.libs.versions.jvm.toolchain.get()))
      }
      target.javaExtension.targetCompatibility = JavaVersion.toVersion(target.jvmTargetInt())
    }

    // Don't set the release version for Android projects.  It will be set by the Android plugin.
    target.plugins.withKotlinJvmPlugin {
      target.tasks.withType(JavaCompile::class.java).configureEach { task ->
        task.options.release.set(target.jvmTargetInt())
      }
    }
  }

  private fun configureTests(target: Project) {

    val runtimeDeps = listOf(
      target.libs.junit.jupiter.engine,
      target.libs.junit.vintage.engine,
      target.libs.junit.platform.launcher,
    )
    target.plugins.withId("test-suite-base") {
      @Suppress("UnstableApiUsage")
      target.extensions.getByType(TestingExtension::class.java)
        .suites
        .withType(JvmTestSuite::class.java)
        .configureEach { suite ->

          suite.useJUnitJupiter(target.libs.versions.jUnit5)
          suite.dependencies {
            // https://junit.org/junit5/docs/current/user-guide/#running-tests-build-gradle-bom
            // https://github.com/junit-team/junit5/issues/4374#issuecomment-2704880447
            it.implementation.add(target.libs.junit.jupiter.asProvider())

            for (dep in runtimeDeps) {
              it.runtimeOnly.add(dep)
            }
          }
        }
    }

    target.plugins.withAGP {
      val testImplementation by target.configurations
      val testRuntimeOnly by target.configurations

      for (dep in runtimeDeps) {
        testRuntimeOnly.dependencies.addLater(dep)
      }
      testImplementation.dependencies.addLater(target.libs.junit.jupiter.asProvider())
    }

    target.tasks.withType(Test::class.java).configureEach { task ->

      task.maxParallelForks = Runtime.getRuntime().availableProcessors()

      task.useJUnitPlatform {
        it.includeEngines("junit-jupiter", "junit-vintage")
      }

      task.systemProperties.putAll(
        mapOf(
          // remove parentheses from test display names
          "junit.jupiter.displayname.generator.default" to
            "org.junit.jupiter.api.DisplayNameGenerator\$Simple",

          // Allow unit tests to run in parallel
          // https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution-config-properties
          "junit.jupiter.execution.parallel.enabled" to true,
          "junit.jupiter.execution.parallel.mode.default" to "concurrent",
          "junit.jupiter.execution.parallel.mode.classes.default" to "concurrent",
        ),
      )

      task.jvmArgs(
        // Fixes illegal reflective operation warnings during tests. It's a Kotlin issue.
        // https://github.com/pinterest/ktlint/issues/1618
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        // Fixes IllegalAccessError: class org.jetbrains.kotlin.kapt3.base.KaptContext [...] in KCT tests
        // https://youtrack.jetbrains.com/issue/KT-45545/Kapt-is-not-compatible-with-JDK-16
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
      )

      task.testLogging { logging ->
        logging.events("skipped", "failed")
        logging.exceptionFormat = FULL
        logging.showCauses = true
        logging.showExceptions = true
        logging.showStackTraces = true
        logging.showStandardStreams = false
      }
    }
  }

  private fun PluginContainer.withAGP(action: (Plugin<*>) -> Unit) {

    val agpPlugins = listOf(
      "com.android.library",
      "com.android.application",
      "com.android.test",
      "com.android.dynamic-feature",
    )
    agpPlugins.forEach { pluginId ->
      withId(pluginId, action)
    }
  }
}
