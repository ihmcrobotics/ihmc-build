package us.ihmc.build

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

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

      val nexusUsername = ""
      val nexusPassword = ""

      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots " +
                                   "-PnexusUsername=$nexusUsername -PnexusPassword=$nexusPassword", "generateTestSuitesTest")

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
//      val nexusUsername = credentials.get("nexusUsername")
//      val nexusPassword = credentials.get("nexusPassword")
//
//      output = runGradleTask("publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots " +
//                                   "-PnexusUsername=$nexusUsername -PnexusPassword=$nexusPassword", "generateTestSuitesTest")

//      assertTrue(output.contains(Regex("BUILD SUCCESSFUL")))
   }
}