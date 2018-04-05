package us.ihmc.build

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Must be run from ihmc-build directory!
 */
class BasicTest
{
   @Test
   fun testGradleIsInstalled()
   {
      println("Gradle install location: $gradleExe")
      println("basicProject location: " + Paths.get("src/test/builds/basicProject").toAbsolutePath().toString())
      
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
   
   fun String.runCommand(workingDir: Path): String? {
      try {
         val parts = this.split("\\s".toRegex())
         val proc = ProcessBuilder(*parts.toTypedArray())
               .directory(workingDir.toFile())
               .redirectOutput(ProcessBuilder.Redirect.PIPE)
               .redirectError(ProcessBuilder.Redirect.PIPE)
               .start()
         
         proc.waitFor(60, TimeUnit.MINUTES)
         return proc.inputStream.bufferedReader().readText() + proc.errorStream.bufferedReader().readText()
      } catch(e: IOException) {
         e.printStackTrace()
         return null
      }
   }
}