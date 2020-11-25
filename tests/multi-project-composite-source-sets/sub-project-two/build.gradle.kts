plugins {
   id("us.ihmc.ihmc-build") version "0.22.0"
   id("us.ihmc.ihmc-ci") version "7.1"
}

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = false

   configureDependencyResolution()
   configurePublications()
}

println("hello")

mainDependencies {
   api("commons-io:commons-io:2.8.0")
}

testDependencies {

}