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
class ContinuousIntegrationTest
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
   
   @Test
   fun testPublishSnapshotLocal()
   {
      var output: String
            
      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=local", "generateTestSuitesTest")
      
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   
//      val artifactory = System.getProperty("artifactory")
//      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots " + artifactory, "generateTestSuitesTest")
//
//      assertTrue(output.contains(Regex("Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar")))
//      assertTrue(output.contains(Regex("Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project-test/SNAPSHOT-0/your-project-test-SNAPSHOT-0.jar")))
//      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}