package com.squareup.anvil.conventions

import com.rickbusarow.kgx.applyOnce
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.kotlinJvmExtensionSafe
import com.rickbusarow.kgx.names.DomainObjectName
import com.rickbusarow.kgx.names.SourceSetName
import com.rickbusarow.kgx.names.SourceSetName.Companion.addPrefix
import com.rickbusarow.kgx.names.SourceSetName.Companion.isMain
import com.rickbusarow.kgx.project
import com.rickbusarow.kgx.withJavaGradlePluginPlugin
import com.squareup.anvil.conventions.PublishConventionPlugin.Companion.PUBLISH_TO_BUILD_M2
import com.squareup.anvil.conventions.utils.javaSourceSet
import com.squareup.anvil.conventions.utils.libs
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testing.base.TestingExtension

/**
 * This plugin will:
 * - add a `gradleTest` source set to the project.
 * - declare that source set as a "test" source set in the IDE.
 * - register a `gradleTest` task that runs the tests in the `gradleTest` source set.
 * - make the `check` task depend upon the `gradleTest` task.
 * - make the `gradleTest` task depend upon the `publishToBuildM2` task..
 */
abstract class GradleTestsPlugin : Plugin<Project> {

  @Suppress("UnstableApiUsage")
  override fun apply(target: Project) {

    target.plugins.applyOnce("jvm-test-suite")

    val testingExtension = target.extensions.getByType(TestingExtension::class.java)

    val suite = testingExtension.suites
      .register(GRADLE_TEST, JvmTestSuite::class.java) { suite ->
        suite.useJUnitJupiter(target.libs.versions.jUnit5)

        suite.dependencies {
          it.implementation.add(target.dependencies.project(target.path))
          it.implementation.add(target.dependencies.gradleTestKit())
        }
      }

    // Tells the `java-gradle-plugin` plugin to inject its TestKit logic
    // into the `gradleTest` source set.
    target.gradlePluginExtensionSafe { extension ->
      extension.testSourceSets(target.javaSourceSet(GRADLE_TEST))
    }

    target.kotlinJvmExtensionSafe { kotlinExtension ->

      val compilations = kotlinExtension.target.compilations

      compilations.named(GRADLE_TEST) {
        it.associateWith(compilations.getByName("main"))
      }
    }

    suite.configure { st ->
      st.targets.configureEach { suiteTarget ->
        suiteTarget.testTask.configure { testTask ->

          testTask.dependsOn(target.rootProject.tasks.named(PUBLISH_TO_BUILD_M2))
        }
      }
    }

    // Make `check` depend upon `gradleTest`
    target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(GRADLE_TEST)
  }

  companion object {
    private const val GRADLE_TEST = "gradleTest"
  }
}

/** */
public abstract class DefaultMahoutPublishTask : DefaultTask()

internal fun MavenPublication.isPluginMarker(): Boolean = name.endsWith("PluginMarkerMaven")
internal fun MavenPublication.nameWithoutMarker(): String = name.removeSuffix("PluginMarkerMaven")
internal fun Publication.isPluginMarker(): Boolean =
  (this as? MavenPublication)?.isPluginMarker() ?: false

internal val Project.mavenPublishBaseExtension: MavenPublishBaseExtension
  get() = extensions.getByType(MavenPublishBaseExtension::class.java)

internal val Project.gradlePublishingExtension: PublishingExtension
  get() = extensions.getByType(PublishingExtension::class.java)

internal val Project.gradlePluginExtension: GradlePluginDevelopmentExtension
  get() = extensions.getByType(GradlePluginDevelopmentExtension::class.java)

internal fun Project.gradlePluginExtensionSafe(action: Action<GradlePluginDevelopmentExtension>) {
  plugins.withJavaGradlePluginPlugin {
    action.execute(gradlePluginExtension)
  }
}

internal val Project.mavenPublications: NamedDomainObjectSet<MavenPublication>
  get() = gradlePublishingExtension.publications.withType(MavenPublication::class.java)

@JvmInline
internal value class TestSuiteName(override val value: String) : DomainObjectName<Publication> {

  companion object {

    fun forSourceSetName(baseName: String, sourceSetName: String): TestSuiteName {
      return forSourceSetName(baseName, SourceSetName(sourceSetName))
    }

    fun forSourceSetName(baseName: String, sourceSetName: SourceSetName): TestSuiteName {
      return if (sourceSetName.isMain()) {
        TestSuiteName(baseName)
      } else {
        TestSuiteName(sourceSetName.addPrefix(baseName))
      }
    }

    fun String.asTestSuiteName(): TestSuiteName = TestSuiteName(this)
  }
}
