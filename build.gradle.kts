import com.gradle.publish.PluginConfig
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer.id

plugins {
   `java-gradle-plugin`
   `kotlin-dsl`
   `maven-publish`
   id("com.gradle.plugin-publish") version "0.10.0"
}

group = "us.ihmc"
version = "0.15.5"

pluginBundle {
   website = "https://github.com/ihmcrobotics/ihmc-build"
   vcsUrl = "https://github.com/ihmcrobotics/ihmc-build"
   tags = listOf("build", "ihmc", "robotics")
}

gradlePlugin {
   plugins {
      create("ihmc-build") {
         id = "us.ihmc.ihmc-build"
         implementationClass = "us.ihmc.build.IHMCBuildPlugin"
      }
   }
}

pluginBundle {
   (plugins) {
      "ihmc-build" {
         id = "us.ihmc.ihmc-build"
         displayName = "IHMC Build Plugin"
         description = "IHMC Robotics opinions on Java builds"
         tags = listOf("build", "ihmc", "robotics")
         version = project.version as String
      }
   }
   
   mavenCoordinates.groupId = group as String
}

java {
   sourceCompatibility = JavaVersion.VERSION_1_8
   targetCompatibility = JavaVersion.VERSION_1_8
   
   sourceSets.getByName("test").resources.srcDir("src/test/builds")
}

repositories {
   jcenter()
   maven {
      url = uri("https://plugins.gradle.org/m2/")
   }
   maven {
      url = uri("https://repo.gradle.org/gradle/libs-snapshots-local")
   }
   maven {
      url = uri("https://dl.bintray.com/ihmcrobotics/maven-release") // TODO remove me
   }
}

dependencies {
   compile("ca.cutterslade.gradle:gradle-dependency-analyze:1.2.2")
   compile("gradle.plugin.com.dorongold.plugins:task-tree:1.3.1")
   compile("com.mashape.unirest:unirest-java:1.4.9")
   compile("us.ihmc:ihmc-commons:0.24.0")
   compile("org.jfrog.artifactory.client:artifactory-java-client-services:2.5.1")
   compile(gradleKotlinDsl())
   
   testCompile("junit:junit:4.12")
   testCompile("us.ihmc:encrypted-properties:0.1.0")
}
