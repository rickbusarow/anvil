package com.squareup.anvil.compiler.testing.compilation

import com.squareup.anvil.compiler.testing.CompilationModeTest
import org.junit.jupiter.api.TestFactory

class Compile2Sample : CompilationModeTest() {

  @TestFactory
  fun compile_source_strings() = testFactory {

    compile2(
      """
      package com.squareup.test

      class KotlinClass(javaClass: JavaClass)
      """.trimIndent(),
      javaSources = listOf(
        //language=java
        """
        package com.squareup.test;

        public class JavaClass { }
        """.trimIndent(),
      ),
    ) {

      scanResult shouldContainClass "com.squareup.test.KotlinClass"
      scanResult shouldContainClass "com.squareup.test.JavaClass"
    }
  }
}
