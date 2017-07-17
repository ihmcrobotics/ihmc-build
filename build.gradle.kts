plugins {
   `java-gradle-plugin`
   `kotlin-dsl`
   `maven-publish`
   id("com.gradle.plugin-publish") version "0.9.7"
}

group = "us.ihmc.gradle"
version = "0.3.0"

extra["licenseURL"] = "http://www.apache.org/licenses/LICENSE-2.0.txt"
extra["licenseName"] = "Apache License, Version 2.0"
extra["bintrayLicenseName"] = "Apache-2.0"

gradlePlugin {
   (plugins) {
      "ihmc-build" {
         id = "ihmc-build"
         implementationClass = "us.ihmc.build.IHMCBuild"
      }
   }
}

pluginBundle {
   tags = listOf("build", "ihmc", "robotics")
   website = "https://github.com/ihmcrobotics/ihmc-build"
   vcsUrl = "https://github.com/ihmcrobotics/ihmc-build"
   description = "IHMC Robotics's Gradle common build logic plugin"
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
}

dependencies {
   compile("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.5")
   compile("ca.cutterslade.gradle:gradle-dependency-analyze:1.0.3")
   compile("gradle.plugin.com.dorongold.plugins:task-tree:1.3")
   compile("us.ihmc:ihmc-ci-plugin:0.14.4")
   compile("org.jfrog.artifactory.client:artifactory-java-client-services:+")
   compile("org.jetbrains.kotlin:kotlin-stdlib:1.1.1")
}