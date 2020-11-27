plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.4"
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
   api("org.apache.commons:commons-lang3:3.11")
}

testDependencies {

}