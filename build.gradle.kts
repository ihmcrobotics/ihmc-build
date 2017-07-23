import com.gradle.publish.PluginConfig

plugins {
   `java-gradle-plugin`
   `kotlin-dsl`
   `maven-publish`
   id("com.gradle.plugin-publish") version "0.9.7"
}

group = "us.ihmc.build"
version = "0.3.34"

gradlePlugin {
   (plugins) {
      "ihmc-build" {
         id = "us.ihmc.build.ihmc-build"
         implementationClass = "us.ihmc.build.IHMCBuild"
      }
   }
}

pluginBundle {
   tags = listOf("build", "ihmc", "robotics")
   website = "https://github.com/ihmcrobotics/ihmc-build"
   vcsUrl = "https://github.com/ihmcrobotics/ihmc-build"
   description = "IHMC Robotics opinions on Java builds"
   
   plugins(closureOf<NamedDomainObjectContainer<PluginConfig>> {
      val plugin = PluginConfig("ihmc-build")
      plugin.id = "us.ihmc.build.ihmc-build"
      plugin.displayName = "IHMC Build Plugin"
      add(plugin)
   })
}

java {
   sourceCompatibility = JavaVersion.VERSION_1_8
   targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
   jcenter()
   maven {
      url = uri("https://plugins.gradle.org/m2/")
   }
   maven {
      url = uri("https://repo.gradle.org/gradle/libs-snapshots-local")
   }
}

dependencies {
   compile("ca.cutterslade.gradle:gradle-dependency-analyze:1.2.0")
   compile("gradle.plugin.com.dorongold.plugins:task-tree:1.3")
   compile("us.ihmc:ihmc-ci-plugin:0.14.4")
   compile("org.jfrog.artifactory.client:artifactory-java-client-services:2.5.1")
}
