package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import us.ihmc.encryptedProperties.EncryptedPropertyManager

/**
 * Must be run from ihmc-build directory!
 */
class ContinuousIntegrationTest
{
   @Disabled
   @Test
   fun testGenerateTestSuitesSucceeds()
   {
      var output = runGradleTask("test", "generateTestSuitesTest")

      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
      
      output = runGradleTask("generateTestSuites", "generateTestSuitesTest")
      
      assertTrue(output.contains(Regex("YourProjectAFast")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }

   @Disabled
   @Test
   fun testPublishSnapshotLocal()
   {
      var output: String
            
      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=local", "generateTestSuitesTest")
      
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))

      val credentials = EncryptedPropertyManager.loadEncryptedCredentials()
      val artifactoryUsername = credentials.get("artifactoryUsername")
      val artifactoryPassword = credentials.get("artifactoryPassword")

      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots " +
                                   "-PartifactoryUsername=$artifactoryUsername -PartifactoryPassword=$artifactoryPassword", "generateTestSuitesTest")
      
//      assertTrue(output.contains(Regex("Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar")))
//      assertTrue(output.contains(Regex("Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project-test/SNAPSHOT-0/your-project-test-SNAPSHOT-0.jar")))
      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }

   @Disabled
   @Test
   fun testResolveSnapshotLocal()
   {
//      var output: String
//
//      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=local", "generateTestSuitesTest")
//
//      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
//
//      val credentials = EncryptedPropertyManager.loadEncryptedCredentials()
//      val artifactoryUsername = credentials.get("artifactoryUsername")
//      val artifactoryPassword = credentials.get("artifactoryPassword")
//
//      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots " +
//                                   "-PartifactoryUsername=$artifactoryUsername -PartifactoryPassword=$artifactoryPassword", "generateTestSuitesTest")

//      assertTrue(output.contains(Regex("Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar")))
//      assertTrue(output.contains(Regex("Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project-test/SNAPSHOT-0/your-project-test-SNAPSHOT-0.jar")))
//      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}