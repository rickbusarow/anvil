package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.classIds
import com.squareup.anvil.compiler.testing.classgraph.injectClass
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.junit.jupiter.api.TestFactory

class CustomGeneratorsTest : CompilationModeTest() {

  @TestFactory
  fun `a custom additional generator is invoked`() = params
    .filter { it.isK2 }
    .asTests {

      compile2(
        """
        package com.squareup.test

        import javax.inject.Inject

        interface ParentInterface

        class InjectClass
        """,
        firProcessors = listOf(
          AnvilFirProcessor.Factory { session ->
            object : SupertypeProcessor(session, false) {
              override fun shouldProcess(declaration: FirClassLikeDeclaration): Boolean {
                return declaration.classId == TestNames.injectClass
              }

              override fun addSupertypes(
                classLikeDeclaration: FirClassLikeDeclaration,
                resolvedSupertypes: List<FirResolvedTypeRef>,
                typeResolver: FirSupertypeGenerationExtension.TypeResolveService,
              ): List<ConeKotlinType> = listOf(TestNames.parentInterface.constructClassLikeType())
            }
          },
        ),
      ) {

        scanResult.injectClass.interfaces.classIds() shouldBe listOf(TestNames.parentInterface)
      }
    }
}
