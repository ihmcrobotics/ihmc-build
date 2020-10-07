plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.log-tools-plugin") version "0.5.0"
   id("us.ihmc.ihmc-ci") version "6.4"
   id("us.ihmc.ihmc-cd") version "1.8"
}

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = true

   configureDependencyResolution()
   configurePublications()
}

//categories.configure("all") {
//
//}

//ihmc.sourceSetProject("test").test {
//
//}

mainDependencies {
   api("org.apache.commons:commons-lang3:3.9")
}

testDependencies {
}
