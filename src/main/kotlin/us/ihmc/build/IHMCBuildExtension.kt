package us.ihmc.build

import com.mashape.unirest.http.Unirest
import groovy.util.Eval
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.XmlProvider
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
import org.gradle.kotlin.dsl.extra
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import us.ihmc.continuousIntegration.AgileTestingTools
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*

open class IHMCBuildExtension(val project: Project)
{
   private val logger = project.logger
   private val offline: Boolean = project.gradle.startParameter.isOffline
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
   private lateinit var artifactoryUsername: String
   private lateinit var artifactoryPassword: String
   
   private val publishModeProperty: String
   private val hyphenatedNameProperty: String
   private val groupDependencyVersionProperty: String
   
   // Bamboo variables
   private val isChildBuild: Boolean
   private val isBambooBuild: Boolean
   private val buildNumber: String
   private lateinit var publishVersion: String
   private val isBranchBuild: Boolean
   private val branchName: String
   
   private val includedBuildMap: HashMap<String, Boolean> = hashMapOf()
   
   private val artifactory: Artifactory by lazy {
      val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
      builder.url = "https://artifactory.ihmc.us/artifactory"
      if (!openSource)
      {
         builder.username = artifactoryUsername
         builder.password = artifactoryPassword
      }
      builder.build()
   }
   private val repositoryVersions: HashMap<String, TreeSet<String>> = hashMapOf()
   
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
      val bambooPlanKeyProperty = setupPropertyWithDefault("bambooPlanKey", "UNKNOWN-KEY")
      val bambooBranchNameProperty = setupPropertyWithDefault("bambooBranchName", "")
      val bambooParentBuildKeyProperty = setupPropertyWithDefault("bambooParentBuildKey", "")
      
      isChildBuild = !bambooParentBuildKeyProperty.isEmpty()
      isBambooBuild = bambooPlanKeyProperty != "UNKNOWN-KEY"
      if (offline || !isBambooBuild)
      {
         buildNumber = bambooBuildNumberProperty
      }
      else if (isChildBuild)
      {
         buildNumber = requestGlobalBuildNumberFromCIDatabase(bambooParentBuildKeyProperty)
      }
      else
      {
         buildNumber = requestGlobalBuildNumberFromCIDatabase("$bambooPlanKeyProperty-$bambooBuildNumberProperty")
      }
      isBranchBuild = !bambooBranchNameProperty.isEmpty() && bambooBranchNameProperty != "develop" && bambooBranchNameProperty != "master"
      branchName = bambooBranchNameProperty.replace("/", "-")
   }
   
   private fun requestGlobalBuildNumberFromCIDatabase(buildKey: String): String
   {
      val ciDatabaseServer = Unirest.get("http://alcaniz.ihmc.us:8087")
      val request = ciDatabaseServer.queryString("globalBuildNumber", buildKey)
      val globalBuildNumber = request.asString().getBody()
      logInfo(logger, "Global build number for $buildKey: $globalBuildNumber")
      return globalBuildNumber
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
         {
            if (openSource)
               logInfo(logger, "Please set artifactoryUsername and artifactoryPassword in /path/to/user/.gradle/gradle.properties.")
            else
               logWarn(logger, "Please set artifactoryUsername and artifactoryPassword in /path/to/user/.gradle/gradle.properties.")
         }
         if (propertyName == "bintray_user" || propertyName == "bintray_key")
         {
            logInfo(logger, "Please set bintray_user and bintray_key in /path/to/user/.gradle/gradle.properties.")
         }
         
         logInfo(logger, "No value found for $propertyName. Using default value: $defaultValue")
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
            logInfo(logger, "Loaded group: " + group)
         }
         if (property.key as String == "version")
         {
            version = property.value as String
            logInfo(logger, "Loaded version: " + version)
         }
         if (property.key as String == "vcsUrl")
         {
            vcsUrl = property.value as String
            logInfo(logger, "Loaded vcsUrl: " + vcsUrl)
         }
         if (property.key as String == "openSource")
         {
            openSource = Eval.me(property.value as String) as Boolean
            logInfo(logger, "Loaded openSource: " + openSource)
         }
      }
   }
   
   fun configureDependencyResolution()
   {
      repository("https://artifactory.ihmc.us/artifactory/snapshots/")
      repository("https://artifactory.ihmc.us/artifactory/releases/")
      repository("http://dl.bintray.com/ihmcrobotics/maven-release")
      if (!openSource)
      {
         repository("https://artifactory.ihmc.us/artifactory/proprietary/", artifactoryUsername, artifactoryPassword)
         repository("https://artifactory.ihmc.us/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
      }
      project.allprojects {
         (this as Project).run {
            repositories.jcenter()
            repositories.mavenCentral()
            repositories.mavenLocal()
         }
      }
      repository("https://artifactory.ihmc.us/artifactory/thirdparty/")
      repository("http://dl.bintray.com/ihmcrobotics/maven-vendor")
      repository("http://clojars.org/repo/")
      repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
      repository("https://oss.sonatype.org/content/repositories/snapshots")
      
      setupJavaSourceSets()
      
      try
      {
         val testProject = project.project(":" + hyphenatedNameProperty + "-test")
         testProject.dependencies {
            add("compile", project)
            add("compile", "us.ihmc:ihmc-ci-core-api:0.16.8")
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
   
   fun mainClassJarWithLibFolder(mainClass: String)
   {
      project.allprojects {
         (this as Project).run {
            configureJarManifest(maintainer, companyName, licenseURL, mainClass, true)
         }
      }
   }
   
   fun jarWithLibFolder()
   {
      project.allprojects {
         (this as Project).run {
            configureJarManifest(maintainer, companyName, licenseURL, "NO_MAIN", true)
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
            
            configureJarManifest(maintainer, companyName, licenseURL, "NO_MAIN", false)
            
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
   
   private fun getPublishVersion(): String
   {
      var publishVersion = version
      
      if (publishModeProperty != "STABLE")
      {
         publishVersion = "SNAPSHOT"
         
         if (isBranchBuild)
         {
            publishVersion += "-$branchName"
         }
         
         publishVersion += "-$buildNumber"
      }
      return publishVersion
   }
   
   fun isBuildRoot(): Boolean
   {
      return project.gradle.startParameter.isSearchUpwards
   }
   
   fun thisProjectIsIncludedBuild(): Boolean
   {
      return !project.gradle.startParameter.isSearchUpwards
   }
   
   fun getIncludedBuilds(): Collection<IncludedBuild>
   {
      if (isBuildRoot())
      {
         return project.gradle.includedBuilds
      }
      else
      {
         return project.gradle.parent.includedBuilds
      }
   }
   
   fun artifactIsIncludedBuild(artifactId: String): Boolean
   {
      if (!includedBuildMap.containsKey(artifactId))
      {
         for (includedBuild in getIncludedBuilds())
         {
            if (artifactId == includedBuild.name)
            {
               includedBuildMap[artifactId] = true
               return true
            }
            else if (artifactId.startsWith(includedBuild.name))
            {
               for (extraSourceSet in IHMCBuildProperties(project.logger).load(includedBuild.projectDir.toPath()).extraSourceSets)
               {
                  if (artifactId == (includedBuild.name + "-$extraSourceSet"))
                  {
                     includedBuildMap[artifactId] = true
                     return true
                  }
               }
            }
         }
   
         includedBuildMap[artifactId] = false
         return false
      }
      
      return includedBuildMap[artifactId]!!
   }
   
   internal fun getExternalDependencyVersion(groupId: String, artifactId: String, declaredVersion: String): String
   {
      // Make sure POM is correct
      if (artifactIsIncludedBuild(artifactId))
      {
         return publishVersion
      }
      
      if (declaredVersion.startsWith("SNAPSHOT"))
      {
         var sanitizedDeclaredVersion = declaredVersion.replace("-BAMBOO", "")
         
         // Use Bamboo variables to resolve the version
         if (isBambooBuild)
         {
            var closestVersion = "NOT-FOUND"
            if (isChildBuild) // Match to parent build, exact branch and version
            {
               var childVersion = "SNAPSHOT"
               if (isBranchBuild)
               {
                  childVersion += "-$branchName"
               }
               childVersion += "-$buildNumber"
               closestVersion = matchVersionFromRepositories(groupId, artifactId, childVersion)
            }
            if (closestVersion.contains("NOT-FOUND") && isBranchBuild) // Try latest from branch
            {
               closestVersion = latestVersionFromRepositories(groupId, artifactId, "SNAPSHOT-$branchName")
            }
            if (closestVersion.contains("NOT-FOUND")) // Try latest without branch
            {
               closestVersion = latestVersionFromRepositories(groupId, artifactId, "SNAPSHOT")
            }
            return closestVersion
         }
         
         // For users
         if (sanitizedDeclaredVersion.endsWith("-LATEST")) // Finds latest version
         {
            return latestVersionFromRepositories(groupId, artifactId, declaredVersion.substringBefore("-LATEST"))
         }
         else // Get exact match on end of string
         {
            return matchVersionFromRepositories(groupId, artifactId, declaredVersion)
         }
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
   
   private fun searchRepositories(groupId: String, artifactId: String): Set<String>
   {
      if (!repositoryVersions.containsKey("$groupId:$artifactId"))
      {
         repositoryVersions["$groupId:$artifactId"] = sortedSetOf<String>()
         
         if (offline)
         {
            val gradleCache = Paths.get(System.getProperty("user.home")).resolve(".gradle/caches/modules-2/files-2.1")
            val artifactPath = gradleCache.resolve(groupId).resolve(artifactId)
            
            for (entry in artifactPath.toFile().list())
            {
               repositoryVersions["$groupId:$artifactId"]!!.add(entry)
            }
         }
         else
         {
            for (repository in getSnapshotRepositoryList())
            {
               for (repoPath in artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).doSearch())
               {
                  if (repoPath.itemPath.matches(Regex(".*\\d\\.jar$")))
                  {
                     repositoryVersions["$groupId:$artifactId"]!!.add(itemPathToVersion(repoPath.itemPath, artifactId))
                  }
               }
            }
         }
      }
      
      return repositoryVersions["$groupId:$artifactId"]!!
   }
   
   private fun itemPathToVersion(itemPath: String, artifactId: String): String
   {
      val split: List<String> = itemPath.split("/")
      val artifact: String = split[split.size - 1]
      val withoutDotJar: String = artifact.split(".jar")[0]
      val version: String = withoutDotJar.substring(artifactId.length + 1)
      
      return version
   }
   
   private fun matchVersionFromRepositories(groupId: String, artifactId: String, versionMatcher: String): String
   {
      for (repositoryVersion in searchRepositories(groupId, artifactId))
      {
         if (repositoryVersion.endsWith(versionMatcher))
         {
            return repositoryVersion
         }
      }
      
      return "MATCH-NOT-FOUND-$versionMatcher"
   }
   
   private fun latestVersionFromRepositories(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var matchedVersion = "LATEST-NOT-FOUND-$versionMatcher"
      var highestBuildNumber: Int = -1
      
      for (repositoryVersion in searchRepositories(groupId, artifactId))
      {
         if (repositoryVersion.contains(versionMatcher))
         {
            val buildNumberFromArtifactory: Int = Integer.parseInt(repositoryVersion.split("-").last())
            if (buildNumberFromArtifactory > highestBuildNumber)
            {
               matchedVersion = repositoryVersion
               highestBuildNumber = buildNumberFromArtifactory
            }
         }
      }
      
      return matchedVersion
   }
   
   private fun Project.configureJarManifest(maintainer: String, companyName: String, licenseURL: String, mainClass: String, libFolder: Boolean)
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
               
               if (!thisProjectIsIncludedBuild() && libFolder)
               {
                  var dependencyJarLocations = " "
                  for (file in configurations.getByName("runtime"))
                  {
                     dependencyJarLocations += "lib/" + file.name + " "
                  }
                  put("Class-Path", dependencyJarLocations.trim())
               }
               if (!thisProjectIsIncludedBuild() && mainClass != "NO_MAIN")
               {
                  put("Main-Class", mainClass)
               }
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
}