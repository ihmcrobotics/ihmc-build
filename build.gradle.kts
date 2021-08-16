import com.gradle.publish.MavenCoordinates

plugins {
   `kotlin-dsl`
   `java-gradle-plugin`
   `maven-publish`
   id("com.gradle.plugin-publish") version "0.14.0"
}

group = "us.ihmc"
version = "0.26.3"

repositories {
   jcenter()
   maven { url = uri("https://plugins.gradle.org/m2/") }  // needed for included plugins
}

dependencies {
   api("ca.cutterslade.gradle:gradle-dependency-analyze:1.7.1") {
      exclude("junit", "junit")
   }
   api("com.dorongold.plugins:task-tree:2.1.0")
   api("com.konghq:unirest-java:3.11.12")
   api("org.jfrog.artifactory.client:artifactory-java-client-services:2.9.2")
   api("guru.nidi:graphviz-kotlin:0.18.1")

   testApi("org.junit.jupiter:junit-jupiter-api:5.7.2")
   testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}

tasks.withType<Test> {
   useJUnitPlatform()
}

val pluginDisplayName = "IHMC Build Plugin"
val pluginDescription = "IHMC Robotics opinions on Java builds."
val pluginVcsUrl = "https://github.com/ihmcrobotics/ihmc-build"
val pluginTags = listOf("build", "ihmc", "robotics")

gradlePlugin {
   plugins.register(project.name) {
      id = project.group as String + "." + project.name
      implementationClass = "us.ihmc.build.IHMCBuildPlugin"
      displayName = pluginDisplayName
      description = pluginDescription
   }
}

pluginBundle {
   website = pluginVcsUrl
   vcsUrl = pluginVcsUrl
   description = pluginDescription
   tags = pluginTags

   plugins.getByName(project.name) {
      id = project.group as String + "." + project.name
      version = project.version as String
      displayName = pluginDisplayName
      description = pluginDescription
      tags = pluginTags
   }

   mavenCoordinates(closureOf<MavenCoordinates> {
      groupId = project.group as String
      artifactId = project.name
      version = project.version as String
   })
}