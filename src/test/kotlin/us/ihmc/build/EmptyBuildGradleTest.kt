package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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