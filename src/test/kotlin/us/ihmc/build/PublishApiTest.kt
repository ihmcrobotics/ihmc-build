package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PublishApiTest
{
   @Test
   fun testOldApiStillWorks()
   {
      var command: String
      var logOutput: String
      
      command = "publish -PpublishMode=LOCAL -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"), "Didn't publish to local")
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"), "Wasn't successful")
      
      command = "publish -PpublishMode=STABLE -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      // TODO: Update to Sonatype error
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https:///your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'."), "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"))
      
//      command = "publish -PpublishMode=SNAPSHOT -PartifactoryUsername=foo -PpublishUsername=foo"
//      logOutput = runGradleTask(command, "oldPublishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar'."))
//      assertTrue(logOutput.contains("BUILD FAILED"))
   }
   
   @Test
   fun testNewApi()
   {
      var command: String
      var logOutput: String
      
      logOutput = runGradleTask("publish -PartifactoryUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=local -PartifactoryUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=LOCAL -PartifactoryUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      command = "publish -PpublishUrl=ihmcRelease -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https:///your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=ihmcVendor -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https:///maven-vendor/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
//      command = "publish -PpublishUrl=ihmcSnapshots -PsnapshotMode=true -PartifactoryUsername=foo -PpublishUsername=foo"
//      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"))
//      assertTrue(logOutput.contains("BUILD FAILED"))
//
//      command = "publish -PpublishUrl=ihmcSnapshot -PsnapshotMode=true -PartifactoryUsername=foo -PpublishUsername=foo"
//      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"))
//      assertTrue(logOutput.contains("BUILD FAILED"))
   }
   
   @Test
   fun testNewApiCustomRepos()
   {
      var command: String
      var logOutput: String
      
      // Custom publish URL
      command = "publish -PpublishUrl=myVendor -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://some.fake/my-open-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=mySecureVendor -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains("Could not write to resource 'https://some.fake/my-secure-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      // Totally custom URL
      command = "publish -PpublishUrl=https://shotgun/repo -PpublishUsername=user564 -PpublishPassword=pass1 -PartifactoryUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains("Could not write to resource 'https://shotgun/repo/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
   }
}