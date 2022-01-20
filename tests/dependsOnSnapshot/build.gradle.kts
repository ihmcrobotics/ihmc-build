plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.6"
}

testSuites {
   disableBambooConfigurationCheck = true
}

println testSuites.convertJobNameToHyphenatedName("AtlasAFast")

nexusUsername = System.properties.getProperty("nexus.username")
nexusPassword = System.properties.getProperty("nexus.password")

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = true

   configureDependencyResolution()
   addPublishUrl("myVendor", "https://some.fake/my-open-vendor")
   addPublishUrl("mySecureVendor", "https://some.fake/my-secure-vendor", "someUsername", "somePassword")
   configurePublications()
}

mainDependencies {
   api("org.apache.commons:commons-lang3:3.12.0")
}

testDependencies {
}
