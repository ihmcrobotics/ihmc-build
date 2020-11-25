plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.0"
}

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = false

   configureDependencyResolution()
   configurePublications()
}

ihmc.sourceSetProject("test").dependencies {
   api(ihmc.sourceSetProject("other-set"))
}