package us.ihmc.build

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.http.options.Options
import groovy.util.Eval
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import us.ihmc.commons.thread.ThreadTools
import us.ihmc.continuousIntegration.AgileTestingTools
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Paths
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

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
   private val publishUsername: String
   private val publishPassword: String
   
   private val kebabCasedNameProperty: String
   private val snapshotModeProperty: Boolean
   private val publishUrlProperty: String
   private val customPublishUrls by lazy { hashMapOf<String, IHMCPublishUrl>() }
   
   // Bamboo variables
   private val isChildBuild: Boolean
   private val isBambooBuild: Boolean
   private val integrationNumber: String
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
   private val pomDependencies: HashMap<String, ArrayList<ArrayList<String>>> = hashMapOf()
   private val documentBuilderFactory by lazy {
      DocumentBuilderFactory.newInstance()
   }
   
   init
   {
      bintrayUser = setupPropertyWithDefault("bintray_user", "unset_user")
      bintrayApiKey = setupPropertyWithDefault("bintray_key", "unset_api_key")
      artifactoryUsername = setupPropertyWithDefault("artifactoryUsername", "unset_username")
      artifactoryPassword = setupPropertyWithDefault("artifactoryPassword", "unset_password")
      publishUsername = setupPropertyWithDefault("publishUsername", "")
      publishPassword = setupPropertyWithDefault("publishPassword", "")
   
      snapshotModeProperty = snapshotModeCompatibility(logger, project.extra)
      publishUrlProperty = publishUrlCompatibility(logger, project.extra)
      
      kebabCasedNameProperty = kebabCasedNameCompatibility(project.name, logger, project.extra)
      
      val bambooBuildNumberProperty = setupPropertyWithDefault("bambooBuildNumber", "0")
      val bambooPlanKeyProperty = setupPropertyWithDefault("bambooPlanKey", "UNKNOWN-KEY")
      val bambooBranchNameProperty = setupPropertyWithDefault("bambooBranchName", "")
      val bambooParentBuildKeyProperty = setupPropertyWithDefault("bambooParentBuildKey", "")
      
      isChildBuild = !bambooParentBuildKeyProperty.isEmpty()
      isBambooBuild = bambooPlanKeyProperty != "UNKNOWN-KEY"
      if (offline || !isBambooBuild)
      {
         integrationNumber = bambooBuildNumberProperty
      }
      else if (isChildBuild)
      {
         integrationNumber = requestIntegrationNumberFromCIDatabase(bambooParentBuildKeyProperty)
      }
      else
      {
         integrationNumber = requestIntegrationNumberFromCIDatabase("$bambooPlanKeyProperty-$bambooBuildNumberProperty")
      }
      isBranchBuild = !bambooBranchNameProperty.isEmpty() && bambooBranchNameProperty != "develop" && bambooBranchNameProperty != "master"
      branchName = bambooBranchNameProperty.replace("/", "-")
   }
   
   private fun requestIntegrationNumberFromCIDatabase(buildKey: String): String
   {
      var tryCount = 0
      var integrationNumber = "ERROR"
      while (tryCount < 5 && integrationNumber == "ERROR")
      {
         integrationNumber = tryIntegrationNumberRequest(buildKey)
         tryCount++
         logInfo(logger, "Integration number for $buildKey: $integrationNumber")
      }
      
      return integrationNumber
   }
   
   private fun tryIntegrationNumberRequest(buildKey: String): String
   {
      try
      {
         return Unirest.get("http://alcaniz.ihmc.us:8087").queryString("globalBuildNumber", buildKey).asString().getBody()
      }
      catch (e: UnirestException)
      {
         logInfo(logger, "Failed to retrieve integration number. Trying again... " + e.message)
         ThreadTools.sleep(100)
         try
         {
            Unirest.shutdown();
            Options.refresh();
         }
         catch (ioException: IOException)
         {
            ioException.printStackTrace();
         }
         return "ERROR"
      }
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
            if (!openSource && isBambooBuild)
            {
               logWarn(logger, "Please set artifactoryUsername and artifactoryPassword in /path/to/user/.gradle/gradle.properties.")
            }
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
      if (isBambooBuild)
      {
         declareMavenCentral()
         repository("http://clojars.org/repo/")
         declareJCenter()
         repository("http://10.6.6.221:8081/artifactory/snapshots/")
         repository("http://dl.bintray.com/ihmcrobotics/maven-release")
         if (!openSource)
         {
            repository("http://10.6.6.221:8081/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
            repository("http://10.6.6.221:8081/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
            repository("http://10.6.6.221:8081/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
         }
         repository("http://dl.bintray.com/ihmcrobotics/maven-vendor")
         repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
         repository("https://artifactory.ihmc.us/artifactory/snapshots/")
         if (!openSource)
         {
            repository("https://artifactory.ihmc.us/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
            repository("https://artifactory.ihmc.us/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
            repository("https://artifactory.ihmc.us/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
         }
      }
      else
      {
         declareMavenCentral()
         declareJCenter()
         repository("http://clojars.org/repo/")
         repository("http://dl.bintray.com/ihmcrobotics/maven-release")
         repository("http://dl.bintray.com/ihmcrobotics/maven-vendor")
         repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
         declareMavenLocal()
      }
      
      setupJavaSourceSets()
      
      try
      {
         val testProject = project.project(":" + kebabCasedNameProperty + "-test")
         testProject.dependencies {
            add("compile", project)
            add("compile", "us.ihmc:ihmc-ci-core-api:0.17.14")
         }
      }
      catch (e: UnknownProjectException)
      {
      }
   }
   
   fun declareJCenter()
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.jcenter()
      }
   }
   
   fun declareMavenCentral()
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.mavenCentral()
      }
   }
   
   fun declareMavenLocal()
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.mavenLocal()
      }
   }
   
   fun repository(url: String)
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.maven {}.url = allproject.uri(url)
      }
   }
   
   fun repository(url: String, username: String, password: String)
   {
      for (allproject in project.allprojects)
      {
         val maven = allproject.repositories.maven {}
         maven.url = allproject.uri(url)
         maven.credentials.username = username
         maven.credentials.password = password
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
            
            if (publishUrlProperty.toLowerCase() == "local")
            {
               declareMavenLocal()
            }
            else if (publishUrlProperty == "ihmcSnapshots" || publishUrlProperty == "ihmcSnapshot")
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
            else if (publishUrlProperty == "ihmcRelease")
            {
               if (openSource)
               {
                  declareBintray("maven-release")
               }
               else
               {
                  declareArtifactory("proprietary-releases")
               }
            }
            else if (publishUrlProperty == "ihmcVendor")
            {
               if (openSource)
               {
                  declareBintray("maven-vendor")
               }
               else
               {
                  declareArtifactory("proprietary-releases")
               }
            }
            else if (customPublishUrls.contains(publishUrlProperty)) // addPublishUrl was called
            {
               declareCustomPublishUrl(publishUrlProperty, customPublishUrls[publishUrlProperty]!!)
            }
            else // User passes new url in manually
            {
               logInfo(logger, "Declaring user publish repository: $publishUrlProperty")
               val userPublishUrl = IHMCPublishUrl(publishUrlProperty, publishUsername, publishPassword)
               declareCustomPublishUrl("User", userPublishUrl)
            }
            
            val java = convention.getPlugin(JavaPluginConvention::class.java)
            
            declarePublication(name, configurations.getByName("compile"), java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
         }
      }
   
      val jarAllTask = project.task("jarAll")
      for (allproject in project.allprojects)
      {
         jarAllTask.dependsOn(allproject.tasks.getByName("mainClassesJar"))
         jarAllTask.dependsOn(allproject.tasks.getByName("mainSourcesJar"))
      }
   
//      for (subproject in project.subprojects)
//      {
//         subproject.tasks.getByName("publishHandle", closureOf<Task> {
//            dependsOn(subproject.tasks.getByName("publish"))
//         })
//      }
   }
   
   fun addPublishUrl(keyword: String, url: String)
   {
      customPublishUrls[keyword] = IHMCPublishUrl(url, "",
                                                  setupPropertyWithDefault("publishPassword", ""))
   }
   fun addPublishUrl(keyword: String, url: String, username: String, password: String)
   {
      customPublishUrls[keyword] = IHMCPublishUrl(url, publishUsername,
                                                  setupPropertyWithDefault("publishPassword", ""))
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
         val sourceSetName = toSourceSetName(subproject)
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("src/$sourceSetName/java")))
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("src/$sourceSetName/java")))
         java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.srcDirs(setOf(project.file("src/$sourceSetName/resources")))
         
         if (subproject.name.endsWith("test"))
         {
            val test = subproject.tasks.findByPath("test") as Test
            test.testClassesDirs = java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output.classesDirs
         }
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
   
   fun javaDirectory(sourceSetName: String, directory: String)
   {
      var modifiedDirectory = directory
      if (sourceSetName == "main")
         modifiedDirectory = "src/main/" + directory
      
      sourceSet(sourceSetName).java.srcDir(modifiedDirectory)
   }
   
   fun resourceDirectory(sourceSetName: String, directory: String)
   {
      var modifiedDirectory = directory
      if (sourceSetName == "main")
         modifiedDirectory = "src/main/" + directory
      
      sourceSet(sourceSetName).resources.srcDir(modifiedDirectory)
   }
   
   fun sourceSet(sourceSetName: String): SourceSet
   {
      return sourceSetProject(sourceSetName).convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
   }
   
   fun sourceSetProject(sourceSetName: String): Project
   {
      if (sourceSetName == "main")
         return project
      else
         return project.project(project.name + "-" + sourceSetName)
   }
   
   private fun getPublishVersion(): String
   {
      if (snapshotModeProperty)
      {
         var publishVersion = "SNAPSHOT"
         if (isBranchBuild)
         {
            publishVersion += "-$branchName"
         }
         publishVersion += "-$integrationNumber"
         return publishVersion
      }
      else
      {
         return version
      }
   }
   
   /** Public API. **/
   fun isBuildRoot(): Boolean
   {
      return isBuildRoot(project)
   }
   
   fun thisProjectIsIncludedBuild(): Boolean
   {
      return !project.gradle.startParameter.isSearchUpwards
   }
   
   fun getIncludedBuilds(): Collection<IncludedBuild>
   {
      if (isBuildRoot(project))
      {
         return project.gradle.includedBuilds
      }
      else
      {
         return project.gradle.parent!!.includedBuilds
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
               for (extraSourceSet in IHMCBuildProperties(project.logger, includedBuild.projectDir.toPath()).extraSourceSets)
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
      var externalDependencyVersion: String
      
      // For high-level projects depending on develop,
      // use version: "source" to make sure you've got everything, and fail fast
      if (declaredVersion.toLowerCase().contains("source"))
      {
         if (artifactIsIncludedBuild(artifactId))
         {
            // When deploying to the robot, or when publishing high-level snapshots,
            // set dependency versions to your version.
            // All high-level project version numbers are the same
            externalDependencyVersion = publishVersion
         }
         else
         {
            var message = "$groupId:$artifactId's version is set to \"$declaredVersion\" and is not included in the build. Please put" +
                  " $artifactId in your composite build or use a release."
            logError(logger, message)
            throw GradleException("[ihmc-build] " + message)
         }
      }
      // Try to resolve a snapshot, check for snapshotMode first
      // Only gonna happen on Bamboo, not supporting this for users
      else if (snapshotModeProperty && declaredVersion.startsWith("SNAPSHOT"))
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
               childVersion += "-$integrationNumber"
               closestVersion = matchVersionFromRepositories(groupId, artifactId, childVersion)
            }
            if (closestVersion.contains("NOT-FOUND") && isBranchBuild) // Try latest from branch
            {
               closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT-$branchName")
            }
            if (closestVersion.contains("NOT-FOUND")) // Try latest without branch
            {
               closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT")
            }
            externalDependencyVersion = closestVersion
         }
         else
         {
            // For users, probably get rid of this soon
            if (sanitizedDeclaredVersion.endsWith("-LATEST")) // Finds latest version
            {
               externalDependencyVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, declaredVersion.substringBefore("-LATEST"))
            }
            else // Get exact match on end of string
            {
               externalDependencyVersion = matchVersionFromRepositories(groupId, artifactId, declaredVersion)
            }
         }
   
         if (externalDependencyVersion.contains("NOT-FOUND"))
         {
            throw GradleException("External dependency version not found: $groupId:$artifactId:$externalDependencyVersion")
         }
      }
      else // Pass directly to gradle as declared
      {
         externalDependencyVersion = declaredVersion
      }
      
      logInfo(logger, "Passing version to Gradle: $groupId:$artifactId:$externalDependencyVersion")
      return externalDependencyVersion
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
               for (repoPath in searchArtifactory(repository, groupId, artifactId))
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
   
   private fun anyVersionExists(groupId: String, artifactId: String): Boolean
   {
      return !searchRepositories(groupId, artifactId).isEmpty()
   }
   
   private fun versionExists(groupId: String, artifactId: String, version: String): Boolean
   {
      if (repositoryVersions.containsKey("$groupId:$artifactId") && repositoryVersions["$groupId:$artifactId"]!!.contains(version))
      {
         return true
      }
      
      if (!offline)
      {
         for (repository in getSnapshotRepositoryList())
         {
            if (searchArtifactory(repository, groupId, artifactId, version).size > 0)
            {
               if (repositoryVersions.containsKey("$groupId:$artifactId"))
               {
                  repositoryVersions["$groupId:$artifactId"]!!.add(version)
               }
               logInfo(logger, "Found version circumventing Artifactory bug: $groupId:$artifactId:$version")
               return true
            }
         }
      }
      
      return false
   }
   
   private fun loadPOMDependencies(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (offline)
      {
         return loadPOMDependenciesMavenLocal(groupId, artifactId, versionToCheck)
      }
      else
      {
         return loadPOMDependenciesArtifactory(groupId, artifactId, versionToCheck)
      }
   }
   
   private fun loadPOMDependenciesArtifactory(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf()
         
         var pomPath: RepoPath
         for (repository in getSnapshotRepositoryList())
         {
            for (repoPath in searchArtifactory(repository, groupId, artifactId, versionToCheck))
            {
               if (repoPath.itemPath.matches(Regex(".*\\d\\.pom$")))
               {
                  logInfo(logger, "Hitting Artifactory for POM: " + repoPath.itemPath)
                  val inputStream = downloadItemFromArtifactory(repository, repoPath)
                  
                  parsePOMInputStream(inputStream, groupId, artifactId, versionToCheck)
               }
            }
         }
      }
      
      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }
   
   private fun searchArtifactory(repository: String, groupId: String, artifactId: String): List<RepoPath>
   {
      try
      {
         return artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).doSearch()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$groupId/$artifactId/$version")
      }
   }
   
   private fun searchArtifactory(repository: String, groupId: String, artifactId: String, version: String): List<RepoPath>
   {
      try
      {
         return artifactory.searches().artifactsByGavc().repositories(repository).groupId(groupId).artifactId(artifactId).version(version).doSearch()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$groupId/$artifactId/$version")
      }
   }
   
   private fun downloadItemFromArtifactory(repository: String, repoPath: RepoPath): InputStream
   {
      try
      {
         return artifactory.repository(repository).download(repoPath.itemPath).doDownload()
      }
      catch (e: IllegalArgumentException)
      {
         throw artifactoryException("$repository/$repoPath")
      }
   }
   
   private fun artifactoryException(path: String): GradleException
   {
      return GradleException("Problem authenticating or retrieving item from Artifactory: $path. Try logging into artifactory.ihmc.us with the credentials used (artifactoryUsername and artifactoryPassword properties) and see if the item is there.")
   }
   
   private fun parsePOMInputStream(inputStream: InputStream?, groupId: String, artifactId: String, versionToCheck: String)
   {
      try
      {
         val documentBuilder = documentBuilderFactory.newDocumentBuilder()
         val document = documentBuilder.parse(inputStream);
         
         val dependencyTags = document.getElementsByTagName("dependency")
         for (i in 0 until dependencyTags.length)
         {
            val dependencyGroupId = dependencyTags.item(i).childNodes.item(1).textContent
            val dependencyArtifactId = dependencyTags.item(i).childNodes.item(3).textContent
            val dependencyVersion = dependencyTags.item(i).childNodes.item(5).textContent
            
            if (dependencyVersion.contains("SNAPSHOT") && anyVersionExists(dependencyGroupId, dependencyArtifactId))
            {
               val arrayDependency: ArrayList<String> = arrayListOf()
               arrayDependency.add(dependencyGroupId)
               arrayDependency.add(dependencyArtifactId)
               arrayDependency.add(dependencyVersion)
               
               pomDependencies["$groupId:$artifactId:$versionToCheck"]!!.add(arrayDependency)
            }
         }
      }
      catch (e: Exception)
      {
         e.printStackTrace()
      }
   }
   
   private fun loadPOMDependenciesMavenLocal(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf()
         
         logInfo(logger, "Hitting Maven Local for POM: user.home/.gradle/caches/modules-2/files-2.1/$groupId/$artifactId/$versionToCheck")
         val gradleCache = Paths.get(System.getProperty("user.home")).resolve(".gradle/caches/modules-2/files-2.1")
         val versionPath = gradleCache.resolve(groupId).resolve(artifactId).resolve(versionToCheck)
         
         var pomFile: File? = null
         for (hashEntry in versionPath.toFile().list())
         {
            for (fileEntry in versionPath.resolve(hashEntry).toFile().list())
            {
               if (fileEntry.endsWith(".pom"))
               {
                  pomFile = versionPath.resolve(hashEntry).resolve(fileEntry).toFile()
               }
            }
         }
         
         parsePOMInputStream(FileInputStream(pomFile), groupId, artifactId, versionToCheck)
      }
      
      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }
   
   private fun performPOMCheck(groupId: String, artifactId: String, versionToCheck: String): Boolean
   {
      if (!versionExists(groupId, artifactId, versionToCheck))
      {
         logInfo(logger, "Version doesn't exist: $groupId:$artifactId:$versionToCheck")
         return false
      }
      else
      {
         for (dependency in loadPOMDependencies(groupId, artifactId, versionToCheck))
         {
            if (!performPOMCheck(dependency[0], dependency[1], dependency[2]))
            {
               return false
            }
         }
         
         return true
      }
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
   
   private fun latestPOMCheckedVersionFromRepositories(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
      
      if (highestVersion.contains("NOT-FOUND"))
         return highestVersion
      
      while (!performPOMCheck(groupId, artifactId, highestVersion))
      {
         logInfo(logger, "Failed POM check: $groupId:$artifactId:$highestVersion")
         repositoryVersions["$groupId:$artifactId"]!!.remove(highestVersion)
         highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
         
         if (highestVersion.contains("NOT-FOUND"))
         {
            logError(logger, "Rollback failed, no more versions found: $groupId:$artifactId:$highestVersion")
            break;
         }
         
         logInfo(logger, "Rolling back to: $groupId:$artifactId:$highestVersion")
      }
      
      return highestVersion
   }
   
   private fun highestBuildNumberVersion(groupId: String, artifactId: String, versionMatcher: String): String
   {
      var matchedVersion = "LATEST-NOT-FOUND-$versionMatcher"
      var highestBuildNumber: Int = -1
      
      for (repositoryVersion in searchRepositories(groupId, artifactId))
      {
         if (repositoryVersion.matches(Regex("$versionMatcher-\\d+")))
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
   
   fun Project.declareCustomPublishUrl(keyword: String, publishUrl: IHMCPublishUrl)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = toPascalCased(keyword)
         url = uri(publishUrl.url)
         if (publishUrl.hasCredentials())
         {
            credentials.username = publishUrl.username
            credentials.password = publishUrl.password
         }
      })
   }
   
   fun Project.declareArtifactory(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "Artifactory" + toPascalCased(repoName)
         url = uri("https://artifactory.ihmc.us/artifactory/" + repoName)
         credentials.username = artifactoryUsername
         credentials.password = artifactoryPassword
      })
   }
   
   fun Project.declareBintray(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "Bintray" + toPascalCased(repoName)
         url = uri("https://api.bintray.com/maven/ihmcrobotics/$repoName/" + rootProject.name)
         credentials.username = bintrayUser
         credentials.password = bintrayApiKey
      })
   }
   
   fun Project.declareMavenLocal()
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.mavenLocal()
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
    * @deprecated Use convertJobNameToKebabCasedName instead.
    */
   fun convertJobNameToHyphenatedName(jobName: String): String
   {
      return convertJobNameToKebabCasedName(jobName)
   }
   
   /**
    * Used for artifact-test-runner to keep easy Bamboo configuration.
    * Job names are pascal cased on Bamboo and use this method to
    * resolve their kebab cased artifact counterparts.
    */
   fun convertJobNameToKebabCasedName(jobName: String): String
   {
      return AgileTestingTools.pascalCasedToHyphenatedWithoutJob(jobName)
   }
}