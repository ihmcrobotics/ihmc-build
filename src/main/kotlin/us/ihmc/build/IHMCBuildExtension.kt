package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.dependencies
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import us.ihmc.commons.nio.FileTools
import us.ihmc.continuousIntegration.AgileTestingTools
import us.ihmc.continuousIntegration.TestSuiteConfiguration
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter

open class IHMCBuildExtension(val project: Project)
{
   val ihmcGroup = "us.ihmc"
   val ihmcVersion = "0.10.0"
   
   var vcsUrl: String = "invalid repo"
   var licenseURL: String = "no license"
   var licenseName: String = "no license"
   var companyName: String = "IHMC"
   var maintainer: String = "IHMC"
   
   fun configureProjectForOpenRobotics(project: Project)
   {
      vcsUrl = "https://github.com/ihmcrobotics/ihmc-open-robotics-software"
      licenseURL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
      licenseName = "Apache License, Version 2.0"
      companyName = "IHMC"
      maintainer = "Rosie"
      
      project.allprojects {
         (this as Project).run {
            group = ihmcGroup
            version = ihmcVersion
            
            val publishMode: String = property("publishMode") as String
            if (publishMode == "SNAPSHOT")
            {
               version = getSnapshotVersion(version as String, property("buildNumber") as String)
            }
            else if (publishMode == "NIGHTLY")
            {
               version = getNightlyVersion(version as String)
            }
            
            val testSuites = extensions.getByType(TestSuiteConfiguration::class.java)
            testSuites.bambooPlanKeys = arrayOf("LIBS-UI2", "LIBS-FAST2", "LIBS-FLAKY2", "LIBS-SLOW2", "LIBS-VIDEO2", "LIBS-INDEVELOPMENT2")
            
            addIHMCMavenRepositories()
            addThirdPartyMavenRepositories()
            addPublicMavenRepositories()
            addLocalMavenRepository()
            
            setupJavaSourceSets()
            
            setupCommonJARConfiguration()
            
            setupCopyTestsTask()
            
            if (publishMode == "SNAPSHOT")
            {
               declareArtifactory("snapshots")
            }
            else if (publishMode == "NIGHTLY")
            {
               declareArtifactory("nightlies")
            }
            else if (publishMode == "STABLE")
            {
               declareBintray()
            }
            
            val java = convention.getPlugin(JavaPluginConvention::class.java)
            
            declarePublication(name, configurations.getByName("compile"), java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
         }
      }
      try
      {
         val testProject = project.project(":" + project.property("hyphenatedName") as String + "-test")
         testProject.dependencies {
            add("compile", project)
         }
      }
      catch (e: UnknownProjectException)
      {
      }
   }
   
   fun getSnapshotVersion(version: String, buildNumber: String): String
   {
      return version + "-SNAPSHOT-" + buildNumber
   }
   
   fun getNightlyVersion(version: String): String
   {
      return version + "-NIGHTLY-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
   }
   
   fun Project.setupJavaSourceSets()
   {
      val java = convention.getPlugin(JavaPluginConvention::class.java)
      
      java.sourceCompatibility = JavaVersion.VERSION_1_8
      java.targetCompatibility = JavaVersion.VERSION_1_8
      
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(file("src")))
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(file("src")))
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(file("resources")))
   }
   
   fun Project.addIHMCMavenRepositories()
   {
      repositories.run {
         maven {}.url = uri("http://dl.bintray.com/ihmcrobotics/maven-vendor")
         maven {}.url = uri("http://dl.bintray.com/ihmcrobotics/maven-release")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/releases/")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/thirdparty/")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/snapshots/")
      }
   }
   
   fun Project.addThirdPartyMavenRepositories()
   {
      repositories.run {
         maven {}.url = uri("http://clojars.org/repo/")
         maven {}.url = uri("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
         maven {}.url = uri("https://oss.sonatype.org/content/repositories/snapshots")
      }
   }
   
   fun Project.addPublicMavenRepositories()
   {
      repositories.run {
         jcenter()
         mavenCentral()
      }
   }
   
   fun Project.addLocalMavenRepository()
   {
      repositories.mavenLocal()
   }
   
   fun getBuildVersion(groupId: String, artifactId: String, dependencyMode: Any?): Map<String, String>
   {
      dependencyMode as String
      
      val buildVersion: String;
      if (dependencyMode.startsWith("STABLE"))
      {
         val firstDash: Int = dependencyMode.indexOf("-");
         if (firstDash > 0)
         {
            buildVersion = dependencyMode.substring(firstDash + 1);
         }
         else
         {
            message("Incorrect syntax for dependencyMode: $dependencyMode should be of the form STABLE-[version]")
            message("Setting buildVersion to 'error'")
            buildVersion = "error"
         }
      }
      else if (dependencyMode == "SNAPSHOT-LATEST")
      {
         buildVersion = latestArtifactoryVersion(artifactId, "SNAPSHOT")
      }
      else
      {
         buildVersion = "$ihmcVersion-$dependencyMode";
      }
      
      if (isArtifactIncluded(artifactId))
      {
         message(project.name + ": Using from source: " + AgileTestingTools.hyphenatedToPascalCased(artifactId))
      }
      else
      {
         message(project.name + ": Using from JAR: $groupId:$artifactId:$buildVersion")
      }
      
      return mapOf("group" to groupId, "name" to artifactId, "version" to buildVersion);
   }
   
   fun isArtifactIncluded(artifactId: String): Boolean
   {
      for (includedBuild in project.gradle.includedBuilds)
      {
         if (artifactId.startsWith(includedBuild.name))
         {
            return true
         }
      }
      
      return false;
   }
   
   fun isIncludedBuild(buildName: String): Boolean
   {
      for (includedBuild in project.gradle.includedBuilds)
      {
         if (buildName == includedBuild.name)
         {
            return true
         }
      }
      
      return false;
   }
   
   fun getIncludedBuild(artifactId: String): IncludedBuild?
   {
      for (includedBuild in project.gradle.includedBuilds)
      {
         if (artifactId == includedBuild.name)
         {
            return includedBuild
         }
      }
      
      return null;
   }
   
   fun isIncludedBuild(): Boolean
   {
      return !project.gradle.startParameter.isSearchUpwards();
   }
   
   fun latestArtifactoryVersion(artifactId: String, dependencyMode: String): String
   {
      val username = project.property("artifactoryUsername") as String
      val password = project.property("artifactoryPassword") as String
      val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
      builder.url = "https://artifactory.ihmc.us/artifactory"
      builder.username = username
      builder.password = password
      val artifactory: Artifactory = builder.build()
      val snapshots: List<RepoPath> = artifactory.searches().artifactsByGavc().repositories("snapshots").groupId("us.ihmc").artifactId(artifactId).doSearch()
      
      var latestVersion: String = "error"
      var latestBuildNumber: Int = -1
      for (repoPath in snapshots)
      {
         if (repoPath.itemPath.contains(dependencyMode))
         {
            if (repoPath.itemPath.endsWith("sources.jar") || repoPath.itemPath.endsWith(".pom"))
            {
               continue
            }
            
            val version: String = itemPathToVersion(repoPath.itemPath, artifactId)
            val buildNumber: Int = buildNumber(version)
            
            // Found exact nightly
            if (version.endsWith(dependencyMode))
            {
               latestVersion = itemPathToVersion(repoPath.itemPath, artifactId)
            }
            
            if (latestVersion == "error")
            {
               latestVersion = version
               latestBuildNumber = buildNumber
            }
            else if (buildNumber > latestBuildNumber)
            {
               latestVersion = version
               latestBuildNumber = buildNumber
            }
         }
      }
      
      return latestVersion
   }
   
   fun buildNumber(version: String): Int
   {
      return Integer.parseInt(version.split("-")[2])
   }
   
   fun itemPathToVersion(itemPath: String, artifactId: String): String
   {
      val split: List<String> = itemPath.split("/")
      val artifact: String = split[split.size - 1]
      val withoutDotJar: String = artifact.split(".jar")[0]
      val version: String = withoutDotJar.substring(artifactId.length + 1)
      
      return version
   }
   
   fun Project.setupCommonJARConfiguration()
   {
      setupCommonJARConfiguration(maintainer, companyName, licenseURL)
   }
   
   fun Project.setupCommonJARConfiguration(maintainer: String, companyName: String, licenseURL: String)
   {
      tasks.getByName("jar") {
         (this as Jar).run {
            manifest.attributes.apply {
               put("Created-By", maintainer)
               put("Implementation-Title", name)
               put("Implementation-Version", version)
               put("Implementation-Vendor", companyName)
               
               put("Bundle-Name", name)
               put("Bundle-Version", version)
               put("Bundle-License", licenseURL)
               put("Bundle-Vendor", companyName)
            }
         }
      }
   }
   
   fun Project.declareArtifactory(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "Artifactory"
         url = uri("https://artifactory.ihmc.us/artifactory/" + repoName)
         credentials.username = property("artifactoryUsername") as String
         credentials.password = property("artifactoryPassword") as String
      })
   }
   
   fun Project.declareBintray()
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "BintrayRelease"
         url = uri("https://api.bintray.com/maven/ihmcrobotics/maven-release/" + name)
         credentials.username = property("bintray_user") as String
         credentials.password = property("bintray_key") as String
      })
   }
   
   fun Project.declarePublication(artifactName: String, configuration: Configuration,
                                  sourceSet: SourceSet, vararg internalDependencies: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = group as String
      publication.artifactId = artifactName
      publication.version = version as String
      
      publication.pom.withXml() {
         (this as XmlProvider).run {
            val dependenciesNode = asNode().appendNode("dependencies")
            
            internalDependencies.forEach {
               val internalDependency = dependenciesNode.appendNode("dependency")
               internalDependency.appendNode("groupId", group)
               internalDependency.appendNode("artifactId", it)
               internalDependency.appendNode("version", version)
            }
            
            configuration.allDependencies.forEach {
               if (it.name != "unspecified")
               {
                  val dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", it.group)
                  dependencyNode.appendNode("artifactId", it.name)
                  dependencyNode.appendNode("version", it.version)
               }
            }
            
            asNode().appendNode("name", name)
            asNode().appendNode("url", vcsUrl)
            val licensesNode = asNode().appendNode("licenses")
            
            val licenseNode = licensesNode.appendNode("license")
            licenseNode.appendNode("name", licenseName)
            licenseNode.appendNode("url", licenseURL)
            licenseNode.appendNode("distribution", "repo")
         }
      }
      
      publication.artifact(task(mapOf("type" to Jar::class.java), sourceSet.name + "ClassesJar", closureOf<Jar> {
         from(sourceSet.output)
      }))
      
      publication.artifact(task(mapOf("type" to Jar::class.java), sourceSet.name + "SourcesJar", closureOf<Jar> {
         from(sourceSet.allJava)
         classifier = "sources"
      }))
   }
   
   /**
    * Used for artifact-test-runner to keep easy Bamboo configuration.
    * Job names are pascal cased on Bamboo and use this method to
    * resolve their hyphenated artifact counterparts.
    */
   fun convertJobNameToHyphenatedName(jobName: String): String
   {
      return AgileTestingTools.pascalCasedToHyphenatedWithoutJob(jobName)
   }
   
   fun message(message: String)
   {
      println("[ihmc-build] " + message)
   }
   
   /**
    * This is temporary to keep the feature branch clean.
    */
   fun Project.setupCopyTestsTask()
   {
      task("deleteTests", closureOf<Task> {
         doLast {
            val testFolder = projectDir.toPath().resolve("test")
            if (Files.exists(testFolder))
            {
               val testSrcFolder = testFolder.resolve("src")
               val testSrcUsFolder = testSrcFolder.resolve("us")
               val testUsFolder = testFolder.resolve("us")
               
               if (Files.exists(testUsFolder))
               {
                  println(testSrcFolder)
                  println(testSrcUsFolder)
                  println(testUsFolder)
                  
                  FileTools.deleteQuietly(testSrcUsFolder)
               }
            }
         }
      })
      
      task("copyTests", closureOf<Task> {
         doLast {
            val testFolder = projectDir.toPath().resolve("test")
            if (Files.exists(testFolder))
            {
               val testSrcFolder = testFolder.resolve("src")
               val testSrcUsFolder = testSrcFolder.resolve("us")
               val testUsFolder = testFolder.resolve("us")
               
               if (Files.exists(testUsFolder))
               {
                  println(testSrcFolder)
                  println(testSrcUsFolder)
                  println(testUsFolder)
                  
                  FileTools.deleteQuietly(testSrcUsFolder)
                  
                  try
                  {
                     FileUtils.copyDirectory(testUsFolder.toFile(), testSrcUsFolder.toFile())
                  }
                  catch (e: Exception)
                  {
                     println("Failed: " + e.printStackTrace())
                  }
               }
            }
         }
      })
   }
}