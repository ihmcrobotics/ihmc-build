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

dependencies {
   compile("us.ihmc:sub-project-one-test:source")

   testCompile("junit:junit:4.12")
}

mainDependencies {
}

oneTestDependencies {
   ihmc.sourceSetProject("one-test").dependencies.compile("us.ihmc:sub-project-one-test:source")
}