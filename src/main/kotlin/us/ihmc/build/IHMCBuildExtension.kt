package us.ihmc.build

import groovy.util.Eval
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import us.ihmc.continuousIntegration.AgileTestingTools
import java.io.File
import java.io.FileInputStream
import java.util.*

open class IHMCBuildExtension(val project: Project)
{
   var group = "unset.group"
   var version = "UNSET-VERSION"
   var vcsUrl: String = "unset_vcs_url"
   var openSource: Boolean = false
   var licenseURL: String = "proprietary"
   var licenseName: String = "Proprietary"
   var companyName: String = "IHMC"
   var maintainer: String = "Rosie (dragon_ryderz@ihmc.us)"
   
   private val bintrayUser: String
   private val bintrayApiKey: String
   private val artifactoryUsername: String
   private val artifactoryPassword: String
   
   private val publishModeProperty: String
   private val hyphenatedNameProperty: String
   private val groupDependencyVersionProperty: String
   
   // Bamboo variables
   private val isChildBuild: Boolean
   private val buildNumber: String
   private lateinit var publishVersion: String
   private val isBranchBuild: Boolean
   private val branchName: String
   
   init
   {
      bintrayUser = setupPropertyWithDefault("bintray_user", "unset_user")
      bintrayApiKey = setupPropertyWithDefault("bintray_key", "unset_api_key")
      artifactoryUsername = setupPropertyWithDefault("artifactoryUsername", "unset_username")
      artifactoryPassword = setupPropertyWithDefault("artifactoryPassword", "unset_password")
      
      groupDependencyVersionProperty = setupPropertyWithDefault("groupDependencyVersion", "SNAPSHOT-LATEST")
      publishModeProperty = setupPropertyWithDefault("publishMode", "SNAPSHOT")
      hyphenatedNameProperty = setupPropertyWithDefault("hyphenatedName", "")
      
      val bambooBuildNumberProperty = setupPropertyWithDefault("bambooBuildNumber", "0")
      val bambooBranchNameProperty = setupPropertyWithDefault("bambooBranchName", "")
      val bambooParentBuildKeyProperty = setupPropertyWithDefault("bambooParentBuildKey", "")
      
      isChildBuild = !bambooParentBuildKeyProperty.isEmpty();
      if (isChildBuild)
      {
         buildNumber = bambooParentBuildKeyProperty.split("-").last()
      }
      else
      {
         buildNumber = bambooBuildNumberProperty
      }
      isBranchBuild = !bambooBranchNameProperty.isEmpty() && bambooBranchNameProperty != "develop"
      branchName = bambooBranchNameProperty
   }
   
   fun setupPropertyWithDefault(propertyName: String, defaultValue: String): String
   {
      if (project.hasProperty(propertyName) && !(project.property(propertyName) as String).startsWith("$"))
      {
         return project.property(propertyName) as String
      }
      else
      {
         if (propertyName == "artifactoryUsername" || propertyName == "artifactoryPassword")
            printQuiet("Please set artifactoryUsername and artifactoryPassword in /path/to/user/.gradle/gradle.properties.")
         if (propertyName == "bintray_user" || propertyName == "bintray_key")
            printQuiet("Please set bintray_user and bintray_key in /path/to/user/.gradle/gradle.properties.")
         
         printQuiet("No value found for $propertyName. Using default value: $defaultValue")
         project.extra.set(propertyName, defaultValue)
         return defaultValue
      }
   }
   
   fun loadProductProperties(propertiesFilePath: String)
   {
      val properties = Properties()
      properties.load(FileInputStream(project.projectDir.toPath().resolve(propertiesFilePath).toFile()))
      for (property in properties)
      {
         if (property.key as String == "group")
         {
            group = property.value as String
            printQuiet("Loaded group: " + group)
         }
         if (property.key as String == "version")
         {
            version = property.value as String
            printQuiet("Loaded version: " + version)
         }
         if (property.key as String == "vcsUrl")
         {
            vcsUrl = property.value as String
            printQuiet("Loaded vcsUrl: " + vcsUrl)
         }
         if (property.key as String == "openSource")
         {
            openSource = Eval.me(property.value as String) as Boolean
            printQuiet("Loaded openSource: " + openSource)
         }
      }
   }
   
   fun configureDependencyResolution()
   {
      project.allprojects {
         (this as Project).run {
            addIHMCMavenRepositories()
            addThirdPartyMavenRepositories()
            addWorldMavenRepositories()
            addLocalMavenRepository()
         }
      }
      
      setupJavaSourceSets()
      
      try
      {
         val testProject = project.project(":" + hyphenatedNameProperty + "-test")
         testProject.dependencies {
            add("compile", project)
         }
      }
      catch (e: UnknownProjectException)
      {
      }
   }
   
   fun repository(url: String)
   {
      project.allprojects {
         (this as Project).run {
            repositories.run {
               maven {}.url = uri(url)
            }
         }
      }
   }
   
   fun repository(url: String, username: String, password: String)
   {
      project.allprojects {
         (this as Project).run {
            repositories.run {
               val maven = maven {}
               maven.url = uri(url)
               maven.credentials.username = username
               maven.credentials.password = password
            }
         }
      }
   }
   
   fun configurePublications()
   {
      if (openSource)
      {
         licenseURL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
         licenseName = "Apache License, Version 2.0"
      }
      
      val productGroup = group
      project.allprojects {
         (this as Project).run {
            group = productGroup
            publishVersion = getPublishVersion()
            version = publishVersion
            
            configureJarManifest(maintainer, companyName, licenseURL)
            
            if (publishModeProperty == "SNAPSHOT")
            {
               if (openSource)
               {
                  declareArtifactory("snapshots")
               }
               else
               {
                  declareArtifactory("proprietary-snapshots")
                  
               }
            }
            else if (publishModeProperty == "STABLE")
            {
               if (openSource)
               {
                  declareBintray()
               }
               else
               {
                  declareArtifactory("proprietary")
               }
            }
            
            val java = convention.getPlugin(JavaPluginConvention::class.java)
            
            declarePublication(name, configurations.getByName("compile"), java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
         }
      }
   }
   
   fun setupJavaSourceSets()
   {
      val java = project.convention.getPlugin(JavaPluginConvention::class.java)
      java.sourceCompatibility = JavaVersion.VERSION_1_8
      java.targetCompatibility = JavaVersion.VERSION_1_8
      for (sourceSet in java.sourceSets)
      {
         sourceSet.java.setSrcDirs(emptySet<File>())
         sourceSet.resources.setSrcDirs(emptySet<File>())
      }
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("src/main/java")))
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("src/main/java")))
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.srcDirs(setOf(project.file("src/main/resources")))
      
      for (subproject in project.subprojects)
      {
         val java = subproject.convention.getPlugin(JavaPluginConvention::class.java)
         java.sourceCompatibility = JavaVersion.VERSION_1_8
         java.targetCompatibility = JavaVersion.VERSION_1_8
         for (sourceSet in java.sourceSets)
         {
            sourceSet.java.setSrcDirs(emptySet<File>())
            sourceSet.resources.setSrcDirs(emptySet<File>())
         }
         val sourceSetName = subproject.name.split("-").last()
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("src/$sourceSetName/java")))
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("src/$sourceSetName/java")))
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.srcDirs(setOf(project.file("src/$sourceSetName/resources")))
      }

//      if (project.hasProperty("useLegacySourceSets") && project.property("useLegacySourceSets") == "true")
//      {
//         if (project.hasProperty("extraSourceSets"))
//         {
//            val extraSourceSets = Eval.me(project.property("extraSourceSets") as String) as ArrayList<String>
//
//            for (extraSourceSet in extraSourceSets)
//            {
//               if (extraSourceSet == "test")
//               {
//                  java.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("test/src")))
//                  java.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("test/src")))
//                  java.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("test/resources")))
//               }
//               else
//               {
//                  java.sourceSets.create(extraSourceSet)
//                  java.sourceSets.getByName(extraSourceSet).java.setSrcDirs(setOf(project.file("$extraSourceSet/src")))
//                  java.sourceSets.getByName(extraSourceSet).resources.setSrcDirs(setOf(project.file("$extraSourceSet/src")))
//                  java.sourceSets.getByName(extraSourceSet).resources.setSrcDirs(setOf(project.file("$extraSourceSet/resources")))
//               }
//            }
//         }
//      }
   }
   
   fun Project.addIHMCMavenRepositories()
   {
      repositories.run {
         maven {}.url = uri("http://dl.bintray.com/ihmcrobotics/maven-vendor")
         maven {}.url = uri("http://dl.bintray.com/ihmcrobotics/maven-release")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/releases/")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/thirdparty/")
         maven {}.url = uri("https://artifactory.ihmc.us/artifactory/snapshots/")
         if (!openSource)
         {
            val proprietary = maven {}
            proprietary.url = uri("https://artifactory.ihmc.us/artifactory/proprietary/")
            proprietary.credentials.username = artifactoryUsername
            proprietary.credentials.username = artifactoryPassword
            val proprietarySnapshots = maven {}
            proprietarySnapshots.url = uri("https://artifactory.ihmc.us/artifactory/proprietary-snapshots/")
            proprietarySnapshots.credentials.username = artifactoryUsername
            proprietarySnapshots.credentials.username = artifactoryPassword
         }
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
   
   fun Project.addWorldMavenRepositories()
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
   
   private fun getPublishVersion(): String
   {
      var publishVersion = version
      
      if (publishModeProperty != "STABLE")
      {
         publishVersion += "-SNAPSHOT"
         
         if (isBranchBuild)
         {
            publishVersion += "-$branchName"
         }
         
         publishVersion += "-$buildNumber"
      }
      return publishVersion
   }
   
   fun isIncludedBuild(artifactId: String): Boolean
   {
      for (includedBuild in project.gradle.includedBuilds)
      {
         if (artifactId == includedBuild.name)
         {
            return true
         }
         else if (artifactId.startsWith(includedBuild.name))
         {
            for (extraSourceSet in IHMCBuildProperties(project.logger).load(includedBuild.projectDir.toPath()).extraSourceSets)
            {
               if (artifactId == (includedBuild.name + "-$extraSourceSet"))
               {
                  return true
               }
            }
         }
      }
      
      return false
   }
   
   internal fun getExternalDependencyVersion(groupId: String, artifactId: String, declaredVersion: String): String
   {
      // Make sure POM is correct
      if (isIncludedBuild(artifactId))
      {
         return publishVersion
      }
      
      // Use Bamboo variables to resolve the version
      if (declaredVersion.contains("BAMBOO"))
      {
         if (isChildBuild) // Match to parent build, enforcing branch
         {
            var childVersion = "SNAPSHOT"
            if (isBranchBuild)
            {
               childVersion += "-$branchName"
            }
            childVersion += "-$buildNumber"
            return matchVersionFromArtifactory(groupId, artifactId, childVersion)
         }
         else
         {
            var latestVersion = "NOT-FOUND"
            if (isBranchBuild) // Try to match the same branch
            {
               latestVersion = latestVersionFromArtifactory(groupId, artifactId, "SNAPSHOT-$branchName")
            }
            if (latestVersion.contains("NOT-FOUND")) // Give up on the branch, use any latest
            {
               latestVersion = latestVersionFromArtifactory(groupId, artifactId, "SNAPSHOT")
            }
            return latestVersion
         }
      }
      
      // For users
      if (declaredVersion.endsWith("-LATEST")) // Finds latest version
      {
         return latestVersionFromArtifactory(groupId, artifactId, declaredVersion.substringBefore("-LATEST"))
      }
      else if (declaredVersion.startsWith("SNAPSHOT")) // Not going to be exact match
      {
         return matchVersionFromArtifactory(groupId, artifactId, declaredVersion)
      }
      else // Pass directly to gradle as declared
      {
         return declaredVersion
      }
   }
   
   private fun getSnapshotRepositoryList(): List<String>
   {
      if (openSource)
      {
         return listOf("snapshots")
      }
      else
      {
         return listOf("snapshots", "proprietary-snapshots")
      }
   }
   
   private fun matchVersionFromArtifactory(groupId: String, artifactId: String, versionMatcher: String): String
   {
      for (repository in getSnapshotRepositoryList())
      {
         for (repoPath in searchArtifactoryRepository(repository, groupId, artifactId))
         {
            if (!repoPath.itemPath.endsWith("sources.jar") && !repoPath.itemPath.endsWith(".pom"))
            {
               val versionFromArtifactory: String = itemPathToVersion(repoPath.itemPath, artifactId)
         
               if (versionFromArtifactory.endsWith(versionMatcher))
               {
                  return versionFromArtifactory
               }
            }
         }
      }
      
      return "MATCH-NOT-FOUND-$versionMatcher"
   }
   
   private fun latestVersionFromArtifactory(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var matchedVersion = "LATEST-NOT-FOUND-$versionMatcher"
      var highestBuildNumber: Int = -1
   
      for (repository in getSnapshotRepositoryList())
      {
         for (repoPath in searchArtifactoryRepository(repository, groupId, artifactId))
         {
            if (!repoPath.itemPath.endsWith("sources.jar") && !repoPath.itemPath.endsWith(".pom"))
            {
               val versionFromArtifactory: String = itemPathToVersion(repoPath.itemPath, artifactId)
         
               if (versionFromArtifactory.contains(versionMatcher))
               {
                  val buildNumberFromArtifactory: Int = Integer.parseInt(versionFromArtifactory.split("-").last())
                  if (buildNumberFromArtifactory > highestBuildNumber)
                  {
                     matchedVersion = versionFromArtifactory
                     highestBuildNumber = buildNumberFromArtifactory
                  }
               }
            }
         }
      }
      
      return matchedVersion
   }
   
   private fun searchArtifactoryRepository(repository: String, groupId: String, artifactId: String): List<RepoPath>
   {
      val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
      builder.url = "https://artifactory.ihmc.us/artifactory"
      if (!openSource)
      {
         builder.username = artifactoryUsername
         builder.password = artifactoryPassword
      }
      val artifactory: Artifactory = builder.build()
      val snapshots: List<RepoPath> = artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).doSearch()
      return snapshots
   }
   
   private fun itemPathToVersion(itemPath: String, artifactId: String): String
   {
      val split: List<String> = itemPath.split("/")
      val artifact: String = split[split.size - 1]
      val withoutDotJar: String = artifact.split(".jar")[0]
      val version: String = withoutDotJar.substring(artifactId.length + 1)
      
      return version
   }
   
   private fun Project.configureJarManifest(maintainer: String, companyName: String, licenseURL: String)
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
         credentials.username = artifactoryUsername
         credentials.password = artifactoryPassword
      })
   }
   
   fun Project.declareBintray()
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "BintrayRelease"
         url = uri("https://api.bintray.com/maven/ihmcrobotics/maven-release/" + name)
         credentials.username = bintrayUser
         credentials.password = bintrayApiKey
      })
   }
   
   private fun Project.declarePublication(artifactName: String, configuration: Configuration, sourceSet: SourceSet)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = group as String
      publication.artifactId = artifactName
      publication.version = version as String
      
      publication.pom.withXml() {
         (this as XmlProvider).run {
            val dependenciesNode = asNode().appendNode("dependencies")
            
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
   
   fun printQuiet(message: Any)
   {
      project.logger.quiet("[ihmc-build] " + message)
   }
   
   fun printInfo(message: Any)
   {
      project.logger.info("[ihmc-build] " + message)
   }
}