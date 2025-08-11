plugins {
   `kotlin-dsl`
   `java-gradle-plugin`
   `maven-publish`
   id("com.gradle.plugin-publish") version "1.3.0"
}

group = "us.ihmc"
version = "1.2.1"

repositories {
   maven { url = uri("https://plugins.gradle.org/m2/") }  // needed for included plugins
}

dependencies {
   api("com.hierynomus:sshj:0.38.0")
   api("org.junit.platform:junit-platform-launcher:1.10.3")
   testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.withType<Test> {
   useJUnitPlatform()
}

val pluginVcsUrl = "https://github.com/ihmcrobotics/ihmc-build"

gradlePlugin {
   website.set(pluginVcsUrl)
   vcsUrl.set(pluginVcsUrl)

   plugins {
      create(project.name) {
         id = project.group as String + "." + project.name
         implementationClass = "us.ihmc.build.IHMCBuildPlugin"
         displayName = "IHMC Build Plugin"
         description = "IHMC Robotics opinions on Java builds."
         tags.set(listOf("build", "ihmc", "robotics"))
      }
   }
}
