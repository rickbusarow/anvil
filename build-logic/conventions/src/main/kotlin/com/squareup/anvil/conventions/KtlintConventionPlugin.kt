package com.squareup.anvil.conventions

import com.rickbusarow.kgx.EagerGradleApi
import com.rickbusarow.kgx.library
import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.matchingName
import com.rickbusarow.kgx.version
import com.rickbusarow.ktlint.KtLintExtension
import com.rickbusarow.ktlint.KtLintPlugin
import com.rickbusarow.ktlint.KtLintTask
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Plugin
import org.gradle.api.Project

open class KtlintConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.plugins.apply(KtLintPlugin::class.java)

    target
      .dependencies
      .add("ktlint", target.libsCatalog.library("ktrules"))

    target.extensions.configure(KtLintExtension::class.java) { ext ->
      ext.ktlintVersion.set(target.libsCatalog.version("ktlint-lib"))
    }

    @OptIn(EagerGradleApi::class)
    target.tasks.withType(KtLintTask::class.java).configureEach { task ->
      task.mustRunAfter(
        target.tasks.matchingName("apiDump"),
        target.tasks.matchingName("dependencyGuard"),
        target.tasks.matchingName("dependencyGuardBaseline"),
        target.tasks.withType(KotlinApiBuildTask::class.java),
        target.tasks.withType(KotlinApiCompareTask::class.java),
      )
    }
  }
}
