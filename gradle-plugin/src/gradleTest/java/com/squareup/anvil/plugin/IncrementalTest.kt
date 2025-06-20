package com.squareup.anvil.plugin

import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.asClueCatching
import com.rickbusarow.kase.files.DirectoryBuilder
import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.getValue
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.kases
import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.wrap
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment
import com.squareup.anvil.plugin.testing.BaseGradleTest
import com.squareup.anvil.plugin.testing.allBoundTypes
import com.squareup.anvil.plugin.testing.allDirectInterfaces
import com.squareup.anvil.plugin.testing.allMergedModulesForComponent
import com.squareup.anvil.plugin.testing.anvil
import com.squareup.anvil.plugin.testing.classGraphResult
import com.squareup.anvil.plugin.testing.names
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.stream.Stream

class IncrementalTest : BaseGradleTest() {

  @TestFactory
  fun `compileKotlin should execute again if anvil-generated output is deleted`() = testFactory {

    rootProject {
      dir("src/main/java") {
        injectClass()
        kotlinFile(
          "com/squareup/test/OtherClass.kt",
          """
          package com.squareup.test
      
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava")

    val injectClassFactory = rootAnvilMainGenerated.injectClassFactory

    injectClassFactory.shouldExist()
    injectClassFactory.deleteOrFail()

    shouldSucceed("compileJava") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    injectClassFactory.shouldExist()
  }

  @TestFactory
  fun `a generated factory is deleted if the source file is deleted`() = testFactory {

    val injectClassPath = "com/squareup/test/InjectClass.kt"

    rootProject {
      dir("src/main/java") {

        injectClass()

        kotlinFile(
          "com.squareup/test/OtherClass.kt",
          """
          package com.squareup.test
      
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("jar")

    rootProject.classGraphResult().allClasses shouldContainExactly listOf(
      "com.squareup.test.InjectClass",
      "com.squareup.test.InjectClass_Factory",
      "com.squareup.test.OtherClass",
      "com.squareup.test.OtherClass_Factory",
    )

    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()

    workingDir.resolve("src/main/java")
      .resolve(injectClassPath)
      .delete()

    shouldSucceed("jar") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootProject.classGraphResult().allClasses shouldContainExactly listOf(
      "com.squareup.test.OtherClass",
      "com.squareup.test.OtherClass_Factory",
    )

    rootAnvilMainGenerated.injectClassFactory.shouldNotExist()
    otherClassFactory.shouldExist()
  }

  @TestFactory
  fun `everything in the generated directory is deleted if the cache file doesn't exist`() =
    testFactory {

      rootProject {
        dir("src/main/kotlin") {
          injectClass()
        }
        gradlePropertiesFile(
          """
            com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      val notRealKotlinFile = rootAnvilMainGenerated
        .resolve("com/squareup/test/NotRealKotlin.kt")
        .createSafely("This won't compile")

      rootProject.path.buildAnvilMainCaches.shouldNotExist()

      shouldSucceed("jar")

      rootProject.classGraphResult().allClasses shouldContainExactly listOf(
        "com.squareup.test.InjectClass",
        "com.squareup.test.InjectClass_Factory",
      )

      notRealKotlinFile.shouldNotExist()

      rootAnvilMainGenerated.injectClassFactory.shouldExist()
    }

  @TestFactory
  fun `a generated factory is deleted if the @Inject annotation is removed`() =
    // This should work with or without the `trackSourceFiles` feature toggle.
    testFactoryWithTrackSourceFiles { (trackSourceFiles) ->

      lateinit var injectClassPath: File

      rootProject {
        dir("src/main/java") {

          injectClassPath = injectClass()
        }
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=$trackSourceFiles
          """.trimIndent(),
        )
      }

      shouldSucceed("jar")

      rootProject.classGraphResult().allClasses shouldContainExactly listOf(
        "com.squareup.test.InjectClass",
        "com.squareup.test.InjectClass_Factory",
      )

      rootAnvilMainGenerated.injectClassFactory.shouldExist()

      injectClassPath.kotlin(
        """
        package com.squareup.test

        class InjectClass
        """.trimIndent(),
      )

      shouldSucceed("jar") {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootProject.classGraphResult().allClasses shouldContainExactly listOf(
        "com.squareup.test.InjectClass",
      )

      rootAnvilMainGenerated.injectClassFactory.shouldNotExist()
    }

  @TestFactory
  fun `a generated binding contribution is deleted if the annotation is removed`() = testFactory {

    rootProject {
      gradlePropertiesFile(
        """
          com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    rootProject.project("lib1") {

      buildFile {
        plugins {
          kotlin("jvm")
          anvil()
        }
        anvil {
          generateDaggerFactories.set(true)
        }
        dependencies {
          implementation(libs.dagger2.annotations)
        }
      }

      dir("src/main/java") {

        simpleInterface(
          packageName = "com.squareup.test.lib1",
          simpleName = "ServiceInterface",
        )

        multiboundClass(
          packageName = "com.squareup.test.lib1",
          simpleName = "Service1",
          boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
          scopeFqName = "kotlin.Any",
        )

        multiboundClass(
          packageName = "com.squareup.test.lib1",
          simpleName = "Service2",
          boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
          scopeFqName = "kotlin.Any",
        )
      }
    }

    rootProject.project("lib2") {
      buildFile {
        plugins {
          kotlin("jvm")
          anvil()
        }
        dependencies {
          implementation(libs.dagger2.annotations)
          api(project(":lib1"))
        }
      }
      dir("src/main/java") {

        kotlinFile(
          path = "com/squareup/test/lib2/Merging.kt",
          content = """
            package com.squareup.test.lib2
    
            import com.squareup.anvil.annotations.MergeComponent
            import com.squareup.anvil.annotations.compat.MergeModules
      
            @MergeComponent(Any::class)
            interface AnyComponent
      
            @MergeModules(Any::class)
            abstract class AnyModule
          """.trimIndent(),
        )
      }
    }

    rootProject.settingsFileAsFile.appendText(
      """

        include(":lib1")
        include(":lib2")
      """.trimIndent(),
    )

    val lib1 by rootProject.subprojects
    val lib2 by rootProject.subprojects

    shouldSucceed(":lib2:jar") {
      task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    fun includedModules(): List<String> {
      return lib2.classGraphResult(lib1)
        .allMergedModulesForComponent("com.squareup.test.lib2.AnyComponent")
        .names()
    }

    includedModules() shouldBe listOf(
      "com.squareup.test.lib1.Service1_ServiceInterface_Any_MultiBindingModule_a39836ad",
      "com.squareup.test.lib1.Service2_ServiceInterface_Any_MultiBindingModule_4e7cdd78",
    )

    lib1.dir("src/main/java") {
      // Replace the @ContributesMultibinding class declaration with one that isn't bound.
      injectClass(packageName = "com.squareup.test.lib1", simpleName = "Service1")
    }

    shouldSucceed(":lib2:jar") {

      task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    includedModules() shouldBe listOf(
      "com.squareup.test.lib1.Service2_ServiceInterface_Any_MultiBindingModule_4e7cdd78",
    )
  }

  @TestFactory
  fun `a generated multibinding is updated if contributions are removed and added in the same compilation`() =
    testFactory {

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      rootProject.project("lib1") {

        buildFile {
          plugins {
            kotlin("jvm")
            id("com.rickbusarow.anvil")
          }
          anvil {
            generateDaggerFactories.set(true)
          }
          dependencies {
            implementation(libs.dagger2.annotations)
          }
        }

        dir("src/main/java") {

          simpleInterface(
            packageName = "com.squareup.test.lib1",
            simpleName = "ServiceInterface",
          )

          multiboundClass(
            packageName = "com.squareup.test.lib1",
            simpleName = "Service1",
            boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
            scopeFqName = "kotlin.Any",
          )

          multiboundClass(
            packageName = "com.squareup.test.lib1",
            simpleName = "Service2",
            boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
            scopeFqName = "kotlin.Any",
          )
        }
      }

      rootProject.project("lib2") {
        buildFile {
          plugins {
            kotlin("jvm")
            id("com.rickbusarow.anvil")
          }
          dependencies {
            implementation(libs.dagger2.annotations)
            api(project(":lib1"))
          }
        }
        dir("src/main/java") {

          kotlinFile(
            path = "com/squareup/test/lib2/Merging.kt",
            content = """
            package com.squareup.test.lib2
    
            import com.squareup.anvil.annotations.MergeComponent
            import com.squareup.anvil.annotations.compat.MergeModules
      
            @MergeComponent(Any::class)
            interface AnyComponent
      
            @MergeModules(Any::class)
            abstract class AnyModule
            """.trimIndent(),
          )
        }
      }

      rootProject.settingsFileAsFile.appendText(
        """

        include(":lib1")
        include(":lib2")
        """.trimIndent(),
      )

      val lib1 by rootProject.subprojects
      val lib2 by rootProject.subprojects

      shouldSucceed(":lib2:jar") {
        task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      fun includedModules(): List<String> {
        return lib2.classGraphResult(lib1)
          .allMergedModulesForComponent("com.squareup.test.lib2.AnyComponent")
          .names()
      }

      includedModules() shouldBe listOf(
        "com.squareup.test.lib1.Service1_ServiceInterface_Any_MultiBindingModule_a39836ad",
        "com.squareup.test.lib1.Service2_ServiceInterface_Any_MultiBindingModule_4e7cdd78",
      )

      lib1.dir("src/main/java") {
        // Replace the @ContributesMultibinding class declaration with one that isn't bound.
        injectClass(packageName = "com.squareup.test.lib1", simpleName = "Service1")

        multiboundClass(
          packageName = "com.squareup.test.lib1",
          simpleName = "Service3",
          boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
          scopeFqName = "kotlin.Any",
        )
      }

      shouldSucceed(":lib2:jar") {

        task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      includedModules() shouldBe listOf(
        "com.squareup.test.lib1.Service2_ServiceInterface_Any_MultiBindingModule_4e7cdd78",
        "com.squareup.test.lib1.Service3_ServiceInterface_Any_MultiBindingModule_a6a5fcfa",
      )
    }

  @TestFactory
  fun `a generated binding contribution is added if an annotation and merge target are added in the same compilation`() =
    testFactory {

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      rootProject.project("lib1") {

        buildFile {
          plugins {
            kotlin("jvm")
            id("com.rickbusarow.anvil")
          }
          anvil {
            generateDaggerFactories.set(true)
          }
          dependencies {
            implementation(libs.dagger2.annotations)
          }
        }

        dir("src/main/java") {

          simpleInterface(
            packageName = "com.squareup.test.lib1",
            simpleName = "ServiceInterface",
          )

          multiboundClass(
            packageName = "com.squareup.test.lib1",
            simpleName = "Service1",
            boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
            scopeFqName = "kotlin.Any",
          )
        }
      }

      rootProject.project("lib2") {
        buildFile {
          plugins {
            kotlin("jvm")
            id("com.rickbusarow.anvil")
          }
          dependencies {
            implementation(libs.dagger2.annotations)
            api(project(":lib1"))
          }
        }
        dir("src/main/java") {
          kotlinFile(
            path = "com/squareup/test/lib2/Merging.kt",
            content = """
              package com.squareup.test.lib2
      
              interface AnyComponent
            """.trimIndent(),
          )
        }
      }

      rootProject.settingsFileAsFile.appendText(
        """

          include(":lib1")
          include(":lib2")
        """.trimIndent(),
      )

      val lib1 by rootProject.subprojects
      val lib2 by rootProject.subprojects

      shouldSucceed(":lib2:jar") {
        task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      lib1.dir("src/main/java") {

        multiboundClass(
          packageName = "com.squareup.test.lib1",
          simpleName = "Service2",
          boundTypeFqName = "com.squareup.test.lib1.ServiceInterface",
          scopeFqName = "kotlin.Any",
        )
      }

      lib2.dir("src/main/java") {
        kotlinFile(
          path = "com/squareup/test/lib2/Merging.kt",
          content = """
            package com.squareup.test.lib2
    
            import com.squareup.anvil.annotations.MergeComponent
            import com.squareup.anvil.annotations.compat.MergeModules
      
            @MergeComponent(Any::class)
            interface AnyComponent
      
            @MergeModules(Any::class)
            abstract class AnyModule
          """.trimIndent(),
        )
      }

      shouldSucceed(":lib2:jar") {

        task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      val includes = lib2.classGraphResult(lib1)
        .allMergedModulesForComponent("com.squareup.test.lib2.AnyComponent")
        .names()

      includes shouldBe listOf(
        "com.squareup.test.lib1.Service1_ServiceInterface_Any_MultiBindingModule_a39836ad",
        "com.squareup.test.lib1.Service2_ServiceInterface_Any_MultiBindingModule_4e7cdd78",
      )
    }

  @TestFactory
  fun `a contributed supertype can be added as part of an incremental compilation`() = testFactory {

    rootProject {
      gradlePropertiesFile(
        """
          com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    rootProject.project("lib1") {

      buildFile {
        plugins {
          kotlin("jvm")
          anvil()
        }
        anvil {
          generateDaggerFactories.set(true)
        }
        dependencies {
          implementation(libs.dagger2.annotations)
        }
      }

      dir("src/main/java") {

        kotlinFile(
          path = "com/squareup/test/lib1/Contributed1.kt",
          content = """
            package com.squareup.test.lib1

            import com.squareup.anvil.annotations.ContributesTo
            
            @ContributesTo(Any::class)
            interface Contributed1
          """.trimIndent(),
        )
      }
    }

    rootProject.project("lib2") {
      buildFile {
        plugins {
          kotlin("jvm")
          anvil()
        }
        dependencies {
          implementation(libs.dagger2.annotations)
          api(project(":lib1"))
        }
      }
      dir("src/main/java") {
        kotlinFile(
          path = "com/squareup/test/lib2/AnyComponent.kt",
          content = """
            package com.squareup.test.lib2

            import com.squareup.anvil.annotations.compat.MergeInterfaces
      
            @MergeInterfaces(Any::class)
            interface AnyComponent
          """.trimIndent(),
        )
      }
    }

    rootProject.settingsFileAsFile.appendText(
      """

        include(":lib1")
        include(":lib2")
      """.trimIndent(),
    )

    val lib1 by rootProject.subprojects
    val lib2 by rootProject.subprojects

    shouldSucceed(":lib2:jar") {
      task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    lib1.dir("src/main/java") {

      simpleInterface(
        packageName = "com.squareup.test.lib1",
        simpleName = "Contributed1",
      )

      kotlinFile(
        path = "com/squareup/test/lib1/Contributed2.kt",
        content = """
          package com.squareup.test.lib1
   
          import com.squareup.anvil.annotations.ContributesTo
   
          @ContributesTo(Any::class)
          interface Contributed2
        """.trimIndent(),
      )
    }

    workingDir.resolve("build/reports").deleteRecursivelyOrFail()

    shouldSucceed(":lib2:jar") {

      task(":lib1:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib2:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    val superTypes = lib2.classGraphResult(lib1)
      .allDirectInterfaces("com.squareup.test.lib2.AnyComponent")
      .names()

    superTypes shouldBe listOf("com.squareup.test.lib1.Contributed2")
  }

  @TestFactory
  fun `a generated factory is updated if the source class constructor is changed`() =
    testFactory {

      lateinit var injectClassPath: File

      rootProject {
        dir("src/main/java") {
          injectClassPath = injectClass()
        }
        gradlePropertiesFile(
          """
            com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      shouldSucceed("compileJava")

      // no constructor parameters
      rootAnvilMainGenerated.injectClassFactory shouldExistWithTextContaining """
        public object InjectClass_Factory : Factory<InjectClass>
      """.trimIndent()

      injectClassPath.kotlin(
        """
        package com.squareup.test

        import javax.inject.Inject

        class InjectClass @Inject constructor(val name: String)
        """.trimIndent(),
      )

      shouldSucceed("jar") {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootProject.classGraphResult().allClasses shouldContainExactly listOf(
        "com.squareup.test.InjectClass",
        "com.squareup.test.InjectClass_Factory",
        "com.squareup.test.InjectClass_Factory\$Companion",
      )

      // now we have constructor parameters
      rootAnvilMainGenerated.injectClassFactory shouldExistWithTextContaining """
        public class InjectClass_Factory(
          private val name: Provider<String>,
        ) : Factory<InjectClass>
      """.trimIndent()
    }

  @TestFactory
  fun `generated files are restored if they're deleted without any changes to source files`() =
    testFactory {

      rootProject {
        dir("src/main/java") {

          injectClass()
          boundClass("com.squareup.test.TypeA")
          simpleInterface("TypeA")
        }
        gradlePropertiesFile(
          """
            com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      shouldSucceed("compileJava")

      val firstRunGeneratedPaths = rootAnvilMainGenerated.listRelativeFilePaths()

      rootAnvilMainGenerated.deleteRecursivelyOrFail()

      shouldSucceed("jar") {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootAnvilMainGenerated.listRelativeFilePaths() shouldBe firstRunGeneratedPaths
    }

  @TestFactory
  fun `generated files are retained after an unrelated incremental change`() = testFactory {

    val otherClassPath = "com/squareup/test/OtherClass.kt"

    rootProject {
      dir("src/main/java") {

        kotlinFile(
          otherClassPath,
          """
          package com.squareup.test
          
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
        injectClass()
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("jar")

    rootProject.classGraphResult().allClasses shouldContainExactly listOf(
      "com.squareup.test.InjectClass",
      "com.squareup.test.InjectClass_Factory",
      "com.squareup.test.OtherClass",
      "com.squareup.test.OtherClass_Factory",
    )

    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()

    rootProject {
      dir("src/main/java") {
        kotlinFile(
          otherClassPath,
          """
           package com.squareup.test
     
           class OtherClass
          """.trimIndent(),
        )
      }
    }

    shouldSucceed("jar") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootProject.classGraphResult().allClasses shouldContainExactly listOf(
      "com.squareup.test.InjectClass",
      "com.squareup.test.InjectClass_Factory",
      "com.squareup.test.OtherClass",
    )

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldNotExist()
  }

  @TestFactory
  fun `compilation re-runs when a @ContributesBinding type supertype changes`() = testFactory {
    // TODO fix
    fun DirectoryBuilder.otherClassContent(
      superType: String,
      packageName: String = "com.squareup.test",
    ): File {
      return kotlinFile(
        path = packageName.replace(".", "/") / "OtherClass.kt",
        content = """
          package $packageName
          
          import com.squareup.anvil.annotations.MergeComponent
          import com.squareup.anvil.annotations.ContributesBinding
          import javax.inject.Inject
          
          @ContributesBinding(Any::class)
          class OtherClass @Inject constructor() : $superType
          
          interface TypeA
          interface TypeB
          
          class Consumer @Inject constructor(
           private val dep: $superType
          )
          
          @MergeComponent(Any::class)
          interface AppComponent
        """.trimIndent(),
      )
    }

    rootProject {
      dir("src/main/java") {

        otherClassContent(superType = "TypeA", packageName = "com.squareup.test")

        injectClass()
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("jar")

    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")
    val otherClassAHint = rootAnvilMainGenerated
      .anvilHint
      .resolve("com_squareup_test_OtherClass_TypeA_Any_BindingModule_9faa8fec_fd2f0594.kt")
    val otherClassBHint = rootAnvilMainGenerated
      .anvilHint
      .resolve("com_squareup_test_OtherClass_TypeB_Any_BindingModule_4e4aa26e_ac9ddbab.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()
    otherClassAHint.shouldExist()
    otherClassBHint.shouldNotExist()

    rootProject.classGraphResult().allBoundTypes() shouldBe listOf(
      "com.squareup.test.OtherClass" to "com.squareup.test.TypeA",
    )

    rootProject {
      dir("src/main/java") {
        otherClassContent(superType = "TypeB", packageName = "com.squareup.test")
      }
    }

    shouldSucceed("jar") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassAHint.shouldNotExist()
    otherClassBHint.shouldExist()

    rootProject.classGraphResult().allBoundTypes() shouldBe listOf(
      "com.squareup.test.OtherClass" to "com.squareup.test.TypeB",
    )
  }

  @TestFactory
  fun `compilation re-runs when a dependency module's @ContributesBinding type supertype changes`() =
    // TODO fix
    testFactory {

      fun DirectoryBuilder.otherClassContent(
        superType: String,
        packageName: String = "com.squareup.test",
      ): File {
        return kotlinFile(
          path = packageName.replace(".", "/") / "OtherClass.kt",
          content = """
            package $packageName
            
            import com.squareup.anvil.annotations.MergeComponent
            import com.squareup.anvil.annotations.ContributesBinding
            import javax.inject.Inject
            
            @ContributesBinding(Any::class)
            class OtherClass @Inject constructor() : $superType
            
            interface TypeA
            interface TypeB
            
            class Consumer @Inject constructor(
             private val dep: $superType
            )
          """.trimIndent(),
        )
      }

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          com.squareup.anvil.addOptionalAnnotations=true
          """.trimIndent(),
        )

        settingsFileAsFile.appendText(
          """

          include(":lib")
          include(":app")
          """.trimIndent(),
        )

        project("lib") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.rickbusarow.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              implementation(libs.dagger2.annotations)
            }
          }
          dir("src/main/java") {
            otherClassContent("TypeA", "com.squareup.test.lib")
          }
        }

        project("app") {

          buildFile {
            plugins {
              kotlin("jvm")
              kotlin("kapt")
              id("com.rickbusarow.anvil")
            }
            dependencies {
              compileOnly(libs.dagger2.annotations)
              implementation(project(":lib"))
              kapt(libs.dagger2.compiler)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/app/AppComponent.kt",
              """
              package com.squareup.test.app

              import com.squareup.anvil.annotations.MergeComponent
              import javax.inject.Singleton
              import com.squareup.anvil.annotations.optional.SingleIn

              @SingleIn(Any::class)
              @MergeComponent(Any::class)
              interface AppComponent
              """.trimIndent(),
            )
          }
        }
      }

      val lib by rootProject.subprojects
      val app by rootProject.subprojects

      shouldSucceed("jar")

      app.classGraphResult(lib).allBoundTypes() shouldBe listOf(
        "com.squareup.test.lib.OtherClass" to "com.squareup.test.lib.TypeA",
      )

      lib.dir("src/main/java") {
        otherClassContent("TypeB", "com.squareup.test.lib")
      }

      shouldSucceed(":app:jar") {
        task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      app.classGraphResult(lib).allBoundTypes() shouldBe listOf(
        "com.squareup.test.lib.OtherClass" to "com.squareup.test.lib.TypeB",
      )
    }

  @TestFactory
  fun `a generated merged Subcomponent is deleted when a dependency module's @ContributesSubcomponent type is deleted`() =
    testFactory {

      // repro for https://github.com/square/anvil/issues/898

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          com.squareup.anvil.addOptionalAnnotations=true
          """.trimIndent(),
        )

        settingsFileAsFile.appendText(
          """

          include(":lib")
          include(":app")
          """.trimIndent(),
        )

        project("lib") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.rickbusarow.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              implementation(libs.dagger2.annotations)
            }
          }
          dir("src/main/java") {

            injectClass(packageName = "com.squareup.test.lib")

            kotlinFile(
              "com/squareup/test/lib/LibComponent.kt",
              """
              package com.squareup.test.lib

              import com.squareup.anvil.annotations.ContributesTo
              import com.squareup.anvil.annotations.optional.SingleIn

              @ContributesTo(Any::class)
              interface LibComponent {
                fun injectClass(): InjectClass
              }
              """.trimIndent(),
            )
          }
        }

        project("app") {

          buildFile {
            plugins {
              kotlin("jvm")
              kotlin("kapt")
              id("com.rickbusarow.anvil")
            }
            dependencies {
              compileOnly(libs.dagger2.annotations)
              implementation(project(":lib"))
              kapt(libs.dagger2.compiler)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/app/AppComponent.kt",
              """
              package com.squareup.test.app

              import com.squareup.anvil.annotations.MergeComponent
              import com.squareup.anvil.annotations.optional.SingleIn

              @SingleIn(Any::class)
              @MergeComponent(Any::class)
              interface AppComponent
              """.trimIndent(),
            )
          }
        }
      }

      val lib by rootProject.subprojects
      val app by rootProject.subprojects

      val subcomponentSrc = lib.path.resolve("src/main/java")
        .resolve("com/squareup/test/lib/LibSubcomponent.kt")

      "the first compilation happens with LibSubcomponent in the dependency module".asClueCatching {

        subcomponentSrc.kotlin(
          """
          package com.squareup.test.lib

          import com.squareup.anvil.annotations.ContributesTo
          import com.squareup.anvil.annotations.ContributesSubcomponent

          @ContributesSubcomponent(Unit::class, Any::class)
          interface LibSubcomponent {
            fun injectClass(): InjectClass
            
            @ContributesSubcomponent.Factory
            interface Factory {
              fun create(): LibSubcomponent
            }

            @ContributesTo(Any::class)
            interface ParentComponent {
              fun libSubcomponentFactory(): Factory
            }
          }
          """.trimIndent(),
        )

        shouldSucceed("jar")

        app.classGraphResult(lib).apply {

          allMergedModulesForComponent("com.squareup.test.app.AppComponent")
            .names() shouldBe listOf(
            "anvil.component.com.squareup.test.app.appcomponent.LibSubcomponent_84607c40\$SubcomponentModule",
          )
        }
      }

      "The second compilation happens after deleting LibSubcomponent".asClueCatching {

        subcomponentSrc.deleteOrFail()

        shouldSucceed("jar") {
          task(":lib:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
          task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        }

        app.classGraphResult(lib).apply {

          allMergedModulesForComponent("com.squareup.test.app.AppComponent")
            .names() shouldBe emptyList()
        }
      }
    }

  @TestFactory
  fun `compilation re-runs when a dependency module's @AssistedInject constructor changes`() =
    testFactory {

      // minimal reproducer for https://github.com/square/anvil/issues/876

      fun DirectoryBuilder.assistedClassContent(vararg assistedParams: String): File {
        return kotlinFile(
          "com/squareup/test/lib/AssistedClass.kt",
          """
            |package com.squareup.test.lib
            |
            |import dagger.assisted.Assisted
            |import dagger.assisted.AssistedFactory
            |import dagger.assisted.AssistedInject
            |
            |class AssistedClass @AssistedInject constructor(
            |  ${assistedParams.joinToString(",\n")}
            |) {
            |
            |  @AssistedFactory
            |  interface Factory {
            |    fun create(
            |      ${assistedParams.joinToString(",\n")}
            |    ): AssistedClass
            |  }
            |}
          """.trimMargin(),
        )
      }

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          com.squareup.anvil.addOptionalAnnotations=true
          """.trimIndent(),
        )

        settingsFileAsFile.appendText(
          """

          include(":lib")
          include(":app")
          """.trimIndent(),
        )

        project("lib") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.rickbusarow.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              implementation(libs.dagger2.annotations)
            }
          }

          dir("src/main/java") {

            assistedClassContent("""@Assisted("arg1") arg1: String""")

            kotlinFile(
              "com/squareup/test/lib/LibComponent.kt",
              """
                package com.squareup.test.lib
                
                import com.squareup.anvil.annotations.ContributesTo
                import com.squareup.test.lib.AssistedClass
                
                @ContributesTo(Any::class)
                interface LibComponent {
                  fun assistedClassFactory(): AssistedClass.Factory
                }
              """.trimIndent(),
            )
          }
        }

        project("app") {

          buildFile {
            plugins {
              kotlin("jvm")
              kotlin("kapt")
              id("com.rickbusarow.anvil")
            }
            dependencies {
              compileOnly(libs.dagger2.annotations)
              implementation(project(":lib"))
              kapt(libs.dagger2.compiler)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/app/AppComponent.kt",
              """
              package com.squareup.test.app

              import com.squareup.anvil.annotations.MergeComponent
              import javax.inject.Singleton
              import com.squareup.anvil.annotations.optional.SingleIn

              @SingleIn(Any::class)
              @MergeComponent(Any::class)
              interface AppComponent
              """.trimIndent(),
            )
          }
        }
      }

      shouldSucceed("jar")

      val lib by rootProject.subprojects

      val assistedClassFactoryImpl = lib.generatedDir()
        .resolve("com/squareup/test/lib/AssistedClass_Factory_Impl.kt")

      assistedClassFactoryImpl shouldExistWithTextContaining """
        public class AssistedClass_Factory_Impl(
          private val delegateFactory: AssistedClass_Factory,
        ) : AssistedClass.Factory {
          override fun create(arg1: String): AssistedClass = delegateFactory.get(arg1)
      """.trimIndent()

      lib.dir("src/main/java") {

        assistedClassContent(
          """@Assisted("arg1") arg1: String""",
          """@Assisted("arg2") arg2: String""",
        )
      }

      shouldSucceed("jar") {
        task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      assistedClassFactoryImpl shouldExistWithTextContaining """
        public class AssistedClass_Factory_Impl(
          private val delegateFactory: AssistedClass_Factory,
        ) : AssistedClass.Factory {
          override fun create(arg1: String, arg2: String): AssistedClass = delegateFactory.get(arg1, arg2)
      """.trimIndent()
    }

  @TestFactory
  fun `build cache restores generated source files in a different project location`() =
    testFactory {

      // The testKit directory has the daemon and build cache.
      // We'll use the same testKit directory for both projects
      // to simulate having a shared remote build cache.
      val runner = gradleRunner
        .withTestKitDir(workingDir / "testKit")
        .withArguments("jar", "--stacktrace")

      val rootA = rootProject(workingDir.resolve("a/root-a")) {}
      val rootB = rootProject(workingDir.resolve("b/root-b")) {}

      for (root in listOf(rootA, rootB)) {
        root.apply {
          // Copy the normal root project's build and settings files to the new projects.
          rootProject.buildFileAsFile.copyTo(buildFileAsFile)
          rootProject.settingsFileAsFile.copyTo(settingsFileAsFile)

          dir("src/main/java") {
            injectClass()
          }
          gradlePropertiesFile(
            """
            org.gradle.caching=true
            com.squareup.anvil.trackSourceFiles=true
            """.trimIndent(),
          )
        }
      }

      // Delete the normal auto-generated "root" Gradle files
      // since we created other projects to work with.
      rootProject.buildFileAsFile.delete()
      rootProject.settingsFileAsFile.delete()

      with(runner.withProjectDir(rootA.path).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootA.path.deleteRecursivelyOrFail()

      with(runner.withProjectDir(rootB.path).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE

        // This file wasn't generated in the `root-b` project,
        // but it was cached and restored even though it isn't part of the normal 'classes' output.
        rootB.generatedDir().injectClassFactory.shouldExist()

        rootB.classGraphResult().allClasses shouldContainExactly listOf(
          "com.squareup.test.InjectClass",
          "com.squareup.test.InjectClass_Factory",
        )
      }
    }

  @TestFactory
  fun `build cache is restored when there is an uncached middle build`() = testFactory {

    // reproducer for https://github.com/square/anvil/issues/1054

    rootProject {

      gradlePropertiesFile(
        """
        org.gradle.caching=true
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
      dir("src/main/java") {
        dir("src/main/java") {
          kotlinFile(
            "com/squareup/test/AppComponent.kt",
            """
              package com.squareup.test
    
              import com.squareup.anvil.annotations.MergeComponent
              import javax.inject.Singleton
    
              @MergeComponent(Any::class)
              interface AppComponent
            """.trimIndent(),
          )
          kotlinFile(
            "com/squareup/test/BoundClass.kt",
            """
              package com.squareup.test

              import com.squareup.anvil.annotations.ContributesBinding
              import javax.inject.Inject

              interface SomeInterface

              @ContributesBinding(Any::class)
              class BoundClass @Inject constructor() : SomeInterface
            """.trimIndent(),
          )
        }
      }
    }

    shouldSucceed("compileKotlin", withHermeticTestKit = true) {
      task(":compileKotlin")!!.outcome shouldBe TaskOutcome.SUCCESS
    }

    val otherFile = rootProject.path / "src/main/java" / "com/squareup/test/OtherFile.kt"
    otherFile.createSafely(
      """
      package com.squareup.test
      
      class OtherFile
      """.trimIndent(),
    )

    // Change the artifacts in the project's `build` directory.
    // The artifacts from the previous build will exist in the cache,
    // so the subsequent build should be "from cache" but not "up to date".
    shouldSucceed("compileKotlin", "--no-build-cache", withHermeticTestKit = true) {
      task(":compileKotlin")!!.outcome shouldBe TaskOutcome.SUCCESS
    }

    otherFile.deleteOrFail()

    shouldSucceed("compileKotlin", withHermeticTestKit = true) {
      task(":compileKotlin")!!.outcome shouldBe TaskOutcome.FROM_CACHE
    }
  }

  @TestFactory
  fun `build cache restores generated source files in a different project location and custom build dir location`() =
    testFactory {

      // The testKit directory has the daemon and build cache.
      // We'll use the same testKit directory for both projects
      // to simulate having a shared remote build cache.
      val runner = gradleRunner
        .withTestKitDir(workingDir / "testKit")
        .withArguments("jar", "--stacktrace")

      val rootA = rootProject(workingDir.resolve("a/root-a")) {}
      val rootB = rootProject(workingDir.resolve("b/root-b")) {}

      val rootBBuildDir = rootB.path.resolveSibling("root-b-build")

      for (root in listOf(rootA, rootB)) {
        root.apply {
          // Copy the normal root project's build and settings files to the new projects.
          rootProject.buildFileAsFile.copyTo(buildFileAsFile)
          rootProject.settingsFileAsFile.copyTo(settingsFileAsFile)

          dir("src/main/java") {
            injectClass()
          }
          gradlePropertiesFile(
            """
            org.gradle.caching=true
            com.squareup.anvil.trackSourceFiles=true
            """.trimIndent(),
          )
        }
      }

      rootB.buildFileAsFile.appendText(
        """
          
        layout.buildDirectory.set(file("../root-b-build"))
        """.trimIndent(),
      )

      // Delete the normal auto-generated "root" Gradle files
      // since we created other projects to work with.
      rootProject.buildFileAsFile.delete()
      rootProject.settingsFileAsFile.delete()

      with(runner.withProjectDir(rootA.path).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootA.path.deleteRecursivelyOrFail()

      with(runner.withProjectDir(rootB.path).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE

        // This file wasn't generated in the `root-b` project,
        // but it was cached and restored even though it isn't part of the normal 'classes' output.
        rootBBuildDir.resolve("anvil/main/generated")
          .injectClassFactory.shouldExist()

        rootB.classGraphResult(buildDir = rootBBuildDir).allClasses shouldContainExactly listOf(
          "com.squareup.test.InjectClass",
          "com.squareup.test.InjectClass_Factory",
        )
      }
    }

  private fun testFactoryWithTrackSourceFiles(
    testAction: suspend AnvilGradleTestEnvironment.(Kase1<Boolean>) -> Unit,
  ): Stream<out DynamicNode> = params.asContainers { versions ->

    kases(listOf(true, false)) { "trackSourceFiles: $a1" }
      .asTests(AnvilGradleTestEnvironment.Factory().wrap(versions)) { testAction(it) }
  }
}
