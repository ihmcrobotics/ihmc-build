package us.ihmc.build

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Must be run from ihmc-build directory!
 */
class GenerateTestSuitesTest
{
   @Test
   fun testGenerateTestSuitesSucceeds()
   {
      var output = runGradleTask("test", "generateTestSuitesTest")

      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
      
      output = runGradleTask("generateTestSuites", "generateTestSuitesTest")
      
      assertTrue(output.contains(Regex("YourProjectAFast")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}