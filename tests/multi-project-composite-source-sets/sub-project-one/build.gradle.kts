plugins {
   id("us.ihmc.ihmc-build") version "0.20.1"
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
   compile("org.apache.commons:commons-lang3:3.9")

   testCompile("org.junit.jupiter:junit-jupiter-api:5.4.0")
}

mainDependencies {

}

testDependencies {

}