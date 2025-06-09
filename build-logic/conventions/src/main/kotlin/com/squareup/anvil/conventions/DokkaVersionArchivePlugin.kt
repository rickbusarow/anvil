package com.squareup.anvil.conventions

import com.rickbusarow.kgx.buildDir
import com.rickbusarow.kgx.checkProjectIsRoot
import com.rickbusarow.kgx.dependOn
import com.rickbusarow.kgx.propertyAs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

abstract class DokkaVersionArchivePlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.checkProjectIsRoot {
      "Only apply the dokka version archive plugin to a root project."
    }

    val VERSION_NAME = target.propertyAs<String>("VERSION_NAME")

    val versionWithoutSnapshot = VERSION_NAME.removeSuffix("-SNAPSHOT")

    val dokkaHtmlBuildDir = target.rootDir.resolve("build/dokka/html")
    val currentVersionBuildDirZip =
      dokkaHtmlBuildDir.resolveSibling("$versionWithoutSnapshot.zip")

    val dokkaArchiveBuildDir = target.dokkaArchiveBuildDir()
    val dokkaArchiveDir = target.dokkaArchiveDir()

    val taskGroup = "dokka versioning"

    val unzip = target.tasks
      .register("unzipDokkaArchives", Sync::class.java) { task ->
        task.group = taskGroup
        task.description = "Unzips all zip files in $dokkaArchiveDir into $dokkaArchiveBuildDir"

        task.onlyIf { dokkaArchiveDir.exists() }

        task.into(dokkaArchiveBuildDir)

        dokkaArchiveDir.walkTopDown()
          .maxDepth(1)
          .filter { file -> file.isFile }
          .filter { file -> file.extension == "zip" }
          .filter { file -> file.nameWithoutExtension != versionWithoutSnapshot }
          .forEach { zipFile -> task.from(target.zipTree(zipFile)) }
      }

    target.tasks.withType(DokkaBaseTask::class.java).dependOn(unzip)

    val zipDokkaArchive = target.tasks
      .register("zipDokkaArchive", Zip::class.java) { task ->
        task.group = taskGroup
        task.description = "Zips the contents of $dokkaArchiveBuildDir"

        task.destinationDirectory.set(dokkaHtmlBuildDir.parentFile)
        task.archiveFileName.set(currentVersionBuildDirZip.name)
        task.outputs.file(currentVersionBuildDirZip)

        task.enabled = versionWithoutSnapshot == VERSION_NAME

        task.from(dokkaHtmlBuildDir) {
          it.into(versionWithoutSnapshot)
          // Don't copy the `older/` directory into the archive, because all navigation is done using
          // the root version's copy.  Archived `older/` directories just waste space.
          it.exclude("older/**")
        }

        task.mustRunAfter(target.tasks.withType(DokkaBaseTask::class.java))
        task.dependsOn(DokkaConventionPlugin.DOKKA_HTML_TASK_NAME)
      }

    target.tasks.register("syncDokkaToArchive", Copy::class.java) { task ->
      task.group = taskGroup
      task.description =
        "sync the Dokka output for the current version to /dokka-archive/$versionWithoutSnapshot"

      task.from(currentVersionBuildDirZip)
      task.into(dokkaArchiveDir)

      val destZip = dokkaArchiveDir.resolve("$versionWithoutSnapshot.zip")

      task.outputs.file(destZip)

      task.enabled = versionWithoutSnapshot == VERSION_NAME

      task.mustRunAfter(target.tasks.withType(DokkaBaseTask::class.java))
      task.dependsOn(zipDokkaArchive)

      task.onlyIf {

        !destZip.exists() || !currentVersionBuildDirZip.exists()
      }
    }
  }

  /** Compares the contents of two zip files, ignoring metadata like timestamps. */
  private fun File.zipContentEquals(other: File): Boolean {

    require(extension == "zip") { "This file is not a zip file: file://$path" }
    require(other.extension == "zip") { "This file is not a zip file: file://$other" }

    fun ZipFile.getZipEntries(): Set<ZipEntry> {
      return entries()
        .asSequence()
        .filter { !it.isDirectory }
        .toHashSet()
    }

    return ZipFile(this).use { zip1 ->
      ZipFile(other).use use2@{ zip2 ->

        val zip1Entries = zip1.getZipEntries()
        val zip1Names = zip1Entries.mapTo(mutableSetOf()) { it.name }
        val zip2Entries = zip2.getZipEntries()
        val zip2Names = zip2Entries.mapTo(mutableSetOf()) { it.name }

        // Check if any file is contained in one archive but not the other
        if (zip1Names != zip2Names) {
          return@use false
        }

        // Check if the contents of any files with the same path are different
        for (file in zip1Names) {
          val zip1Entry = zip1.getEntry(file)
          val zip2Entry = zip2.getEntry(file)

          if (zip1Entry.size != zip2Entry.size) {
            return@use false
          }

          val inputStream1 = zip1.getInputStream(zip1Entry)
          val inputStream2 = zip2.getInputStream(zip2Entry)
          val content1 = inputStream1.readBytes()
          val content2 = inputStream2.readBytes()
          inputStream1.close()
          inputStream2.close()

          if (!content1.contentEquals(content2)) {
            return@use false
          }
        }
        return@use true
      }
    }
  }

  companion object {
    internal fun Project.dokkaArchiveDir(): File = rootDir.resolve("dokka-archive")
    internal fun Project.dokkaArchiveBuildDir(): File {
      return rootProject.buildDir().resolve("dokka/archive")
    }
  }
}
