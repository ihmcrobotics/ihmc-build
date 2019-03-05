package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Must be run from ihmc-build directory!
 */
class MultiProjectTest
{
   @Test
   fun testGroupProjectSucceeds()
   {
      val output = runGradleTask("compositeTask -PtaskName=compileJava", "multi-project")
      
      assertTrue(output.contains(Regex("Including build: sub-project-one")))
      assertTrue(output.contains(Regex("Including build: sub-project-two")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
   
   @Test
   fun testSubProjectsSucceed()
   {
      var output = runGradleTask("compileJava", "multi-project/sub-project-one")
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
      
      output = runGradleTask("compileJava", "multi-project/sub-project-two")
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
   
   @Test
   fun testInconsistentSubProjectSucceeds()
   {
      var output = runGradleTask("compileJava", "multi-project-inconsistent-excludes/sub-project-one")
      assertTrue(output.contains(Regex("\\[ihmc-build\\] Including build: \\.\\..sub-project-two")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}