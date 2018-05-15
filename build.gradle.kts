import com.gradle.publish.PluginConfig

plugins {
   `java-gradle-plugin`
   `kotlin-dsl`
   `maven-publish`
   id("com.gradle.plugin-publish") version "0.9.9"
}

group = "us.ihmc"
version = "0.14.0"

gradlePlugin {
   (plugins) {
      "ihmc-build" {
         id = "us.ihmc.ihmc-build"
         implementationClass = "us.ihmc.build.IHMCBuildPlugin"
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
      plugin.id = "us.ihmc.ihmc-build"
      plugin.displayName = "IHMC Build Plugin"
      add(plugin)
   })
   
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
   compile("ca.cutterslade.gradle:gradle-dependency-analyze:1.2.0")
   compile("gradle.plugin.com.dorongold.plugins:task-tree:1.3")
   compile("us.ihmc:ihmc-ci-plugin:0.17.14")
   compile("com.mashape.unirest:unirest-java:1.4.8")
   compile("us.ihmc:ihmc-commons:0.19.1")
   compile("org.jfrog.artifactory.client:artifactory-java-client-services:2.5.1")
   compile(gradleKotlinDsl())
   
   testCompile("junit:junit:4.12")
}
