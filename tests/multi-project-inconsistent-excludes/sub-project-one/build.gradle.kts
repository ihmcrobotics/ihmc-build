plugins {
   id("us.ihmc.ihmc-build") version "0.22.0"
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
   api("us.ihmc:sub-project-two:$version")
}

testDependencies {

}