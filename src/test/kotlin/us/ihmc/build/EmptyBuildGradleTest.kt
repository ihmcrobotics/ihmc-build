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
class EmptyBuildGradleTest
{
   @Test
   fun testEmptyBuildGradleGivesError()
   {
      val output = runGradleTask("", "emptyBuildGradleProject")
      
      assertTrue(output.contains(Regex("Build file is empty")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}