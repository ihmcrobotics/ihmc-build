package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Must be run from ihmc-build directory!
 */
class BasicTest
{
   @Test
   fun testGradleIsInstalled()
   {
      println("Gradle install location: $gradleExe")
      println("basicProject location: " + Paths.get("tests/basicProject").toAbsolutePath().toString())
      
      val output = runGradleTask("--version", "basicProject")
      assertTrue(output.contains(Regex("Gradle [0-9]\\.[0-9]")) && output.contains("Build time"))
   }
   
   @Test
   fun testBasicProjectSucceeds()
   {
      val output = runGradleTask("compileJava", "basicProject")
      
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
   
   @Test
   fun testBrokenProjectFails()
   {
      val output = runGradleTask("compileJava", "brokenProject")
      
      assertTrue(output.contains(Regex("BUILD FAILED")))
   }
}