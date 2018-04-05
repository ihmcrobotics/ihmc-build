package us.ihmc.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublishApiTest
{
   @Test
   fun testOldApiStillWorks()
   {
      var command: String
      var logOutput: String
      
      command = "publish -PpublishMode=LOCAL -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"), "Didn't publish to local")
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"), "Wasn't successful")
      
      command = "publish -PpublishMode=STABLE -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://api.bintray.com/maven/ihmcrobotics/maven-release/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      command = "publish -PpublishMode=SNAPSHOT -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
   }
   
   @Test
   fun testNewApi()
   {
      var command: String
      var logOutput: String
      
      logOutput = runGradleTask("publish -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"), "Didn't publish to local")
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"), "Wasn't successful")
      
      logOutput = runGradleTask("publish -PpublishUrl=local -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"), "Didn't publish to local")
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"), "Wasn't successful")
      
      logOutput = runGradleTask("publish -PpublishUrl=LOCAL -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"), "Didn't publish to local")
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"), "Wasn't successful")
      
      command = "publish -PpublishUrl=ihmcRelease -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://api.bintray.com/maven/ihmcrobotics/maven-release/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      command = "publish -PpublishUrl=ihmcVendor -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://api.bintray.com/maven/ihmcrobotics/maven-vendor/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      command = "publish -PpublishUrl=ihmcSnapshots -PsnapshotMode=true -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      command = "publish -PpublishUrl=ihmcSnapshot -PsnapshotMode=true -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
   }
   
   @Test
   fun testNewApiCustomRepos()
   {
      var command: String
      var logOutput: String
      
      // Custom publish URL
      command = "publish -PpublishUrl=myVendor -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://some.fake/my-open-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      command = "publish -PpublishUrl=mySecureVendor -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload https://some.fake/my-secure-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
      
      // Totally custom URL
      command = "publish -PpublishUrl=http://shotgun/repo -PpublishUsername=user564 -PpublishPassword=pass1 -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Upload http://shotgun/repo/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"),
                 "Didn't publish to release")
      assertTrue(logOutput.contains("BUILD FAILED"), "Was successful")
   }
}