package com.squareup.anvil.compiler.k2.factories.inject.constructor

import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.reflect.getDeclaredFieldValue
import com.squareup.anvil.compiler.testing.reflect.injectClass_Factory
import com.squareup.anvil.compiler.testing.reflect.invokeGet
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory
import javax.inject.Provider

class FirInjectConstructorFactoryGeneratorTest : CompilationModeTest(
  MODE_DEFAULTS.filter { it.isK2 && !it.useKapt },
) {
  @TestFactory
  fun `factory class is generated for @Inject annotation`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass @javax.inject.Inject constructor()
      """.trimIndent(),
    ) {
      scanResult shouldContainClass TestNames.injectClass_Factory
    }
  }

  @TestFactory
  fun `factory class is not generated if class is not annotated`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass
      """.trimIndent(),
    ) {

      scanResult shouldNotContainClass TestNames.injectClass_Factory
    }
  }

  @TestFactory
  fun `factory class creates source class with values from provider`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass @javax.inject.Inject constructor(
        val param0: String,
        val param1: Int,
      )
      """.trimIndent(),
    ) {
      val expectedString = "ExpectedString"
      val expectedInt = 77
      val factoryClass = classLoader.injectClass_Factory

      val factoryInstance = factoryClass.getMethod(
        "create",
        Provider::class.java,
        Provider::class.java,
      )
        .invoke(factoryClass, Provider { expectedString }, Provider { expectedInt })

      val injectClassInstance = factoryInstance.invokeGet()

      injectClassInstance.getDeclaredFieldValue("param0") shouldBe expectedString
      injectClassInstance.getDeclaredFieldValue("param1") shouldBe expectedInt
    }
  }

  @TestFactory
  fun `a factory is generated when there are imports`() = testFactory {

    // Imports cause `CodeGenerationExtension.hasPackage()` to be called earlier,
    // which could cause problems if lazy predicate-based values are computed before
    // those annotations can be resolved.
    compile2(
      """
      package com.squareup.test

      // Any import will do, even if it isn't used and the relevant annotation is fully qualified.
      import kotlin.Unit

      class InjectClass @javax.inject.Inject constructor(
        val param0: String,
        val param1: Int,
      )
      """.trimIndent(),
    ) {
      val expectedString = "ExpectedString"
      val expectedInt = 77
      val factoryClass = classLoader.injectClass_Factory

      val factoryInstance = factoryClass.getMethod(
        "create",
        Provider::class.java,
        Provider::class.java,
      )
        .invoke(factoryClass, Provider { expectedString }, Provider { expectedInt })

      val injectClassInstance = factoryInstance.invokeGet()

      injectClassInstance.getDeclaredFieldValue("param0") shouldBe expectedString
      injectClassInstance.getDeclaredFieldValue("param1") shouldBe expectedInt
    }
  }
}
