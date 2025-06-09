package com.squareup.anvil.conventions

import com.rickbusarow.kgx.applyOnce
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.isRootProject
import com.rickbusarow.kgx.projectDependency
import com.rickbusarow.kgx.propertyAs
import com.rickbusarow.kgx.withType
import com.squareup.anvil.conventions.DokkaVersionArchivePlugin.Companion.dokkaArchiveBuildDir
import com.squareup.anvil.conventions.DokkaVersionArchivePlugin.Companion.dokkaArchiveDir
import com.squareup.anvil.conventions.utils.libs
import com.vanniktech.maven.publish.tasks.JavadocJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.dokka.gradle.engine.plugins.DokkaVersioningPluginParameters
import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask
import org.jetbrains.kotlin.gradle.plugin.extraProperties

abstract class DokkaConventionPlugin : Plugin<Project> {

  private val semverRegex = buildString {
    append("(?:0|[1-9]\\d*)\\.")
    append("(?:0|[1-9]\\d*)\\.")
    append("(?:0|[1-9]\\d*)")
    append("(?:-(?:(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)")
    append("(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?")
    append("(?:\\+(?:[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?")
  }

  override fun apply(target: Project) {

    target.extraProperties.apply {
      set("org.jetbrains.dokka.experimental.gradle.pluginMode", "V2Enabled")
      set("org.jetbrains.dokka.experimental.gradle.pluginMode.noWarn", "true")
    }

    target.plugins.applyOnce("org.jetbrains.dokka")

    target.extensions.configure(DokkaExtension::class.java) { dokka ->

      val version = target.propertyAs<String>("VERSION_NAME")
      val versionStable = version.substringBefore("-")

      val fullModuleName = target.path.removePrefix(":")
      dokka.moduleName.set(fullModuleName)
      dokka.moduleVersion.set(versionStable)

      dokka.dokkaSourceSets.configureEach { ss ->

        val ssName = ss.name

        ss.documentedVisibilities(*VisibilityModifier.entries.toTypedArray())

        ss.languageVersion.set(target.libs.versions.kotlinLanguageVersion)
        ss.jdkVersion.set(target.libs.versions.jvm.target.library.map { it.toInt() })

        // include all project sources when resolving kdoc samples
        ss.samples.setFrom(target.fileTree(target.file("src")))

        if (!target.isRootProject()) {
          val readmeFile = target.projectDir.resolve("README.md")
          if (readmeFile.exists()) {
            ss.includes.from(readmeFile)
          }
        }

        ss.sourceLink { spec ->

          // spec.localDirectory.files(kotlinSS.kotlin.sourceDirectories)
          spec.localDirectory.files("src/$ssName")

          val modulePath = target.path.replace(":", "/")
            .replaceFirst("/", "")

          val sourceWebsite = target.propertyAs<String>("SOURCE_WEBSITE")

          // URL showing where the source code can be accessed through the web browser
          spec.remoteUrl("$sourceWebsite/blob/main/$modulePath/src/$ssName")
          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          spec.remoteLineSuffix.set("#L")
        }
      }

      target.tasks.withType(DokkaBaseTask::class.java).configureEach { task ->

        if (target.isRootProject()) {
          task.dependsOn("unzipDokkaArchives")
        }

        // Dokka uses their outputs but doesn't explicitly depend upon them.
        // task.mustRunAfter(target.tasks.withType(KotlinCompile::class.java))
        // task.mustRunAfter(target.tasks.withType(KtLintTask::class.java))
      }

      if (target.isRootProject()) {

        val config = target.configurations.getByName("dokka")

        config.dependencies.addAllLater(
          target.provider {
            target.subprojects
              .filter { sub -> sub.subprojects.isEmpty() }
              .map { sub -> target.projectDependency(sub.path) }
          },
        )

        target.dependencies.add("dokkaPlugin", target.libs.dokka.versioning)

        val dokkaArchiveBuildDir = target.dokkaArchiveBuildDir()

        dokka.pluginsConfiguration.withType<DokkaVersioningPluginParameters>()
          .configureEach { versioning ->
            versioning.version.set(version)
            versioning.renderVersionsNavigationOnAllPages.set(true)

            if (target.dokkaArchiveDir().exists()) {
              versioning.olderVersionsDir.set(dokkaArchiveBuildDir)
            }
          }
      }
    }

    target.plugins.withType(MavenPublishPlugin::class.java).configureEach {

      val checkJavadocJarIsNotVersioned = target.tasks
        .register("checkJavadocJarIsNotVersioned") { task ->

          task.description =
            "Ensures that generated javadoc.jar artifacts don't include old Dokka versions"
          task.group = "dokka versioning"

          val javadocTasks = target.tasks.withType(JavadocJar::class.java)
          task.dependsOn(javadocTasks)

          task.inputs.files(javadocTasks.map { it.outputs })

          val zipTrees = javadocTasks.map { target.zipTree(it.archiveFile) }

          task.doLast {

            val jsonReg = """older/($semverRegex)/version\.json""".toRegex()

            val versions = zipTrees.flatMap { tree ->
              tree
                .filter { it.path.startsWith("older/") }
                .filter { it.isFile }
                .mapNotNull { jsonReg.find(it.path)?.groupValues?.get(1) }
            }

            check(versions.isEmpty()) {
              "Found old Dokka versions in javadoc.jar: $versions"
            }
          }
        }

      target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME)
        .dependsOn(checkJavadocJarIsNotVersioned)
    }
  }

  companion object {
    const val DOKKA_HTML_TASK_NAME = "dokkaGenerate"
  }
}
