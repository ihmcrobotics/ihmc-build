plugins {
   id("us.ihmc.ihmc-build") version "0.22.0"
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

ihmc.sourceSetProject("test").test {

   forkEvery = 1
   maxParallelForks = 20

   systemProperties = [
         'junit.jupiter.execution.parallel.enabled': 'true',
         'junit.jupiter.execution.parallel.config.strategy': 'dynamic',
//         'junit.jupiter.execution.parallel.config.fixed.parallelism': '2'
   ]
}

mainDependencies {
   api("org.apache.commons:commons-lang3:3.9")
}

testDependencies {
}
