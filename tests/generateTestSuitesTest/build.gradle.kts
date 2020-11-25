plugins {
   id("us.ihmc.ihmc-build") version "0.23.0"
   id("us.ihmc.ihmc-ci") version "7.3"
}

// TODO: Fix up to test CI stuff maybe

artifactoryUsername = System.properties.getProperty("artifactory.username")
artifactoryPassword = System.properties.getProperty("artifactory.password")

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
   api("org.apache.commons:commons-lang3:3.11")
}

testDependencies {
}
