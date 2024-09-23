package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PublishApiTest
{
   @Test
   fun testNewApi()
   {
      var logOutput: String = runGradleTask("publish -PnexusUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=local -PnexusUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))
      
      logOutput = runGradleTask("publish -PpublishUrl=LOCAL -PnexusUsername=foo -PpublishUsername=foo", "publishApiTest")
      assertTrue(logOutput.contains("publishMainPublicationToMavenLocalRepository"))
      assertTrue(logOutput.contains("BUILD SUCCESSFUL"))

      var command = "publish -PpublishUrl=ihmcRelease -PnexusUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https:///your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=ihmcVendor -PnexusUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
//      assertTrue(logOutput.contains(
//            "Could not write to resource 'https:///maven-vendor/your-project/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar"))
      assertTrue(logOutput.contains("BUILD FAILED"))
   }
   
   @Test
   fun testNewApiCustomRepos()
   {
      var command: String
      var logOutput: String
      
      // Custom publish URL
      command = "publish -PpublishUrl=myVendor -PnexusUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains(
            "Could not write to resource 'https://some.fake/my-open-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      command = "publish -PpublishUrl=mySecureVendor -PnexusUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains("Could not write to resource 'https://some.fake/my-secure-vendor/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
      
      // Totally custom URL
      command = "publish -PpublishUrl=https://shotgun/repo -PpublishUsername=user564 -PpublishPassword=pass1 -PnexusUsername=foo -PpublishUsername=foo"
      logOutput = runGradleTask(command, "publishApiTest")
      assertTrue(logOutput.contains("Could not write to resource 'https://shotgun/repo/us/ihmc/your-project/0.1.0/your-project-0.1.0.jar'"))
      assertTrue(logOutput.contains("BUILD FAILED"))
   }
}