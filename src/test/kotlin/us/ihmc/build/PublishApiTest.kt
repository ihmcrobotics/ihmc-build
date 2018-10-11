package us.ihmc.build

import org.junit.Assert.*
import org.junit.Test

class PublishApiTest
{
   @Test
   fun testOldApiStillWorks()
   {
      var command: String
      var logOutput: String
      
      command = "publish -PpublishMode=LOCAL -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue("Didn't publish to local", logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue("Wasn't successful", logOutput.contains("BUILD SUCCESSFUL"))
      
      command = "publish -PpublishMode=STABLE -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue("Didn't publish to release", logOutput.contains(
            "Could not write to resource 'https://api.bintray.com/maven/ihmcrobotics/maven-release/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'."))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishMode=SNAPSHOT -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "oldPublishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar'."))
      assertTrue(logOutput.contains("BUILD FAILED"))
   }
   
   @Test
   fun testNewApi()
   {
      var command: String
      var logOutput: String
      
      logOutput = runGradleTask("publish -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=local -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=LOCAL -PartifactoryUsername=foo -PbintrayUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      command = "publish -PpublishUrl=ihmcRelease -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://api.bintray.com/maven/ihmcrobotics/maven-release/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=ihmcVendor -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://api.bintray.com/maven/ihmcrobotics/maven-vendor/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=ihmcSnapshots -PsnapshotMode=true -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=ihmcSnapshot -PsnapshotMode=true -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://artifactory.ihmc.us/artifactory/snapshots/us/ihmc/your-project/SNAPSHOT-0/your-project-SNAPSHOT-0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
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
            "Could not transfer artifact us.ihmc:your-project:jar:0.1.0 from/to remote (https://some.fake/my-open-vendor): Could not write to resource 'us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=mySecureVendor -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not transfer artifact us.ihmc:your-project:jar:0.1.0 from/to remote (https://some.fake/my-secure-vendor): Could not write to resource 'us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      // Totally custom URL
      command = "publish -PpublishUrl=http://shotgun/repo -PpublishUsername=user564 -PpublishPassword=pass1 -PartifactoryUsername=foo -PbintrayUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not transfer artifact us.ihmc:your-project:jar:0.1.0 from/to remote (http://shotgun/repo): Could not write to resource 'us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
   }
}