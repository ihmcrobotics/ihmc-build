plugins {
   id("us.ihmc.ihmc-build") version "0.19.7"
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

dependencies {
   compile("commons-io:commons-io:2.6")

   testCompile("junit:junit:4.12")
}

mainDependencies {
}

testDependencies {

}