plugins {
   id("us.ihmc.ihmc-build")
}

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
