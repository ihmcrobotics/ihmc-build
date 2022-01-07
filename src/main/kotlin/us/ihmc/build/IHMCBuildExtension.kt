package us.ihmc.build

import groovy.util.Eval
import groovy.util.Node
import kong.unirest.Unirest
import kong.unirest.json.JSONObject
import org.apache.commons.lang3.SystemUtils
import org.gradle.api.*
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.collections.HashSet

open class IHMCBuildExtension(val project: Project)
{
   private val offline: Boolean = project.gradle.startParameter.isOffline
   var group = "unset.group"
   var version = "UNSET-VERSION"
   var vcsUrl: String = "unset_vcs_url"
   var openSource: Boolean = false
   var licenseURL: String = "proprietary"
   var licenseName: String = "Proprietary"
   var companyName: String = "IHMC"
   var maintainer: String = "Rosie (dragon_ryderz@ihmc.us)"
   
   private lateinit var artifactoryUsername: String
   private lateinit var artifactoryPassword: String
   private lateinit var nexusUsername: String
   private lateinit var nexusPassword: String
   private val publishUsername: String
   private val publishPassword: String

   private val titleCasedNameProperty: String
   private val kebabCasedNameProperty: String
   private val snapshotModeProperty: Boolean
   private val publishUrlProperty: String
   private val ciDatabaseUrlProperty: String
   private val artifactoryUrlProperty = IHMCBuildTools.artifactoryUrlCompatibility(project.extra)
   private val nexusUrlProperty = IHMCBuildTools.nexusUrlCompatibility(project.extra)
   private var compatibilityVersionProperty: String
   private val customPublishUrls by lazy { hashMapOf<String, IHMCPublishUrl>() }
   
   // Bamboo variables
   private val isChildBuild: Boolean
   private val isBambooBuild: Boolean
   private val integrationNumber: String
   private lateinit var publishVersion: String
   private val isBranchBuild: Boolean
   private val branchName: String
   
   private val includedBuildMap = hashMapOf<String, Boolean>()
   
   private val artifactory: Artifactory by lazy {
      val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
      builder.url = "$artifactoryUrlProperty/artifactory"
      if (!openSource)
      {
         builder.username = artifactoryUsername
         builder.password = artifactoryPassword
      }
      builder.build()
   }
   private val repositoryVersions = hashMapOf<String, TreeSet<String>>()
   private val pomDependencies = hashMapOf<String, ArrayList<ArrayList<String>>>()
   private val documentBuilderFactory by lazy {
      DocumentBuilderFactory.newInstance()
   }
   
   init
   {
      artifactoryUsername = setupPropertyWithDefault("artifactoryUsername", "unset_username")
      artifactoryPassword = setupPropertyWithDefault("artifactoryPassword", "unset_password")
      nexusUsername = setupPropertyWithDefault("nexusUsername", "unset_username")
      nexusPassword = setupPropertyWithDefault("nexusPassword", "unset_password")
      publishUsername = setupPropertyWithDefault("publishUsername", "")
      publishPassword = setupPropertyWithDefault("publishPassword", "")
   
      snapshotModeProperty = IHMCBuildTools.snapshotModeCompatibility(project.extra)
      publishUrlProperty = IHMCBuildTools.publishUrlCompatibility(project.extra)
      ciDatabaseUrlProperty = IHMCBuildTools.ciDatabaseUrlCompatibility(project.extra)
      compatibilityVersionProperty = IHMCBuildTools.compatibilityVersionCompatibility(project.extra)

      titleCasedNameProperty = IHMCBuildTools.titleCasedNameCompatibility(project.name, project.extra)
      kebabCasedNameProperty = IHMCBuildTools.kebabCasedNameCompatibility(project.name, project.extra)
      
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
      Unirest.config().connectTimeout(30000)
   }
   
   private fun requestIntegrationNumberFromCIDatabase(buildKey: String): String
   {
      var tryCount = 0
      var integrationNumber = "ERROR"
      while (tryCount < 5 && integrationNumber == "ERROR")
      {
         integrationNumber = tryIntegrationNumberRequest(buildKey)
         tryCount++
         LogTools.info("Integration number for $buildKey: $integrationNumber")
      }
      
      return integrationNumber
   }
   
   private fun tryIntegrationNumberRequest(buildKey: String): String
   {
      var result = "ERROR"
      Unirest.get(ciDatabaseUrlProperty).queryString("integrationNumber", buildKey).asString()
            .ifSuccess { response ->
               result = response.body
            }
            .ifFailure { response ->
               LogTools.error("Response status: ${response.status}")
               response.parsingError.ifPresent { exception ->
                  LogTools.error("Exception: $exception")
                  LogTools.error("Exception: ${exception.originalBody}")
               }
               LogTools.info("Failed to retrieve integration number. Trying again...")
            }
      return result
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
               LogTools.warn("Please set artifactoryUsername and artifactoryPassword in /path/to/user/.gradle/gradle.properties.")
            }
         }
         if (propertyName == "nexusUsername" || propertyName == "nexusPassword")
         {
            if (!openSource && isBambooBuild)
            {
               LogTools.warn("Please set nexusUsername and nexusPassword in /path/to/user/.gradle/gradle.properties.")
            }
         }

         LogTools.info("No value found for $propertyName. Using default value: $defaultValue")
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
            LogTools.info("Loaded group: " + group)
         }
         if (property.key as String == "version")
         {
            version = property.value as String
            LogTools.info("Loaded version: " + version)
         }
         if (property.key as String == "vcsUrl")
         {
            vcsUrl = property.value as String
            LogTools.info("Loaded vcsUrl: " + vcsUrl)
         }
         if (property.key as String == "openSource")
         {
            openSource = Eval.me(property.value as String) as Boolean
            LogTools.info("Loaded openSource: " + openSource)
         }
      }
   }
   
   fun configureDependencyResolution()
   {
      if (snapshotModeProperty)
      {
         declareMavenCentral()
         repository("https://clojars.org/repo/")
         declareJCenter()
         repository("$nexusUrlProperty/repository/open-snapshots/")
         repository("$artifactoryUrlProperty/artifactory/snapshots/")
         if (!openSource)
         {
            repository("$nexusUrlProperty/repository/proprietary-releases/", nexusUsername, nexusPassword)
            repository("$nexusUrlProperty/repository/proprietary-snapshots/", nexusUsername, nexusPassword)
            repository("$nexusUrlProperty/repository/proprietary-vendor/", nexusUsername, nexusPassword)
            repository("$artifactoryUrlProperty/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
            repository("$artifactoryUrlProperty/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
            repository("$artifactoryUrlProperty/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
         }
         repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
         repository("https://jitpack.io")
      }
      else
      {
         declareMavenCentral()
         declareJCenter()
         repository("https://clojars.org/repo/")
         repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
         repository("https://jitpack.io")
         if (!openSource && (nexusUsername != "unset_username")) // support third parties not needing to declare Nexus
         {
            repository("$nexusUrlProperty/repository/proprietary-releases/", nexusUsername, nexusPassword)
            repository("$nexusUrlProperty/repository/proprietary-vendor/", nexusUsername, nexusPassword)
         }
         if (!openSource && (artifactoryUsername != "unset_username")) // support third parties not needing to declare Artifactory
         {
            repository("$artifactoryUrlProperty/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
            repository("$artifactoryUrlProperty/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
         }
         declareMavenLocal()
      }
      
      setupJavaSourceSets()
      
      try // always declare dependency on "main" from "test"
      {
         val testProject = project.project(":$kebabCasedNameProperty-test")
         testProject.dependencies.add("api", project)
      }
      catch (e: UnknownProjectException)
      {

      }
   }

   @Deprecated("JFrog announced JCenter's <a href=\"https://blog.gradle.org/jcenter-shutdown\">sunset</a> in February 2021. Use {@link #mavenCentral()} instead.")
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
         this.run {
            configureJarManifest(maintainer, companyName, licenseURL, mainClass, true)
         }
      }
   }
   
   fun jarWithLibFolder()
   {
      project.allprojects {
         this.run {
            configureJarManifest(maintainer, companyName, licenseURL, "NO_MAIN", true)
         }
      }
   }
   
   fun configurePublications()
   {
      if (openSource)
      {
         licenseURL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
         licenseName = "Apache License, Version 2.0"
      }
      
      val productGroup = group
      project.allprojects {
         this.run {
            group = productGroup
            publishVersion = getPublishVersion()
            version = publishVersion
            
            configureJarManifest(maintainer, companyName, licenseURL, "NO_MAIN", false)
            
            if (IHMCBuildTools.publishUrlIsKeyword(publishUrlProperty, "local"))
            {
               declareMavenLocal()
            }
            else if (IHMCBuildTools.publishUrlIsKeyword(publishUrlProperty, "ihmcsnapshots")
                  || IHMCBuildTools.publishUrlIsKeyword(publishUrlProperty, "ihmcsnapshot"))
            {
               if (openSource)
               {
                  declareNexus("open-snapshots")
               }
               else
               {
                  declareNexus("proprietary-snapshots")
               }
            }
            else if (IHMCBuildTools.publishUrlIsKeyword(publishUrlProperty, "ihmcrelease"))
            {
               if (openSource)
               {
                  declareMavenCentral("releases")
               }
               else
               {
                  declareNexus("proprietary-releases")
               }
            }
            else if (IHMCBuildTools.publishUrlIsKeyword(publishUrlProperty, "ihmcvendor"))
            {
               if (openSource)
               {
                  declareMavenCentral("releases")
               }
               else
               {
                  declareNexus("proprietary-releases")
               }
            }
            else if (customPublishUrls.contains(publishUrlProperty)) // addPublishUrl was called
            {
               declareCustomPublishUrl(publishUrlProperty, customPublishUrls[publishUrlProperty]!!)
            }
            else // User passes new url in manually
            {
               LogTools.info("Declaring user publish repository: $publishUrlProperty")
               val userPublishUrl = IHMCPublishUrl(publishUrlProperty, publishUsername, publishPassword)
               declareCustomPublishUrl("User", userPublishUrl)
            }
            
            val java = extensions.getByType(JavaPluginExtension::class.java)
            
            declarePublication(name, java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
         }
      }
   }
   
   fun addPublishUrl(keyword: String, url: String)
   {
      customPublishUrls[keyword] = IHMCPublishUrl(url, "", setupPropertyWithDefault("publishPassword", ""))
   }

   fun addPublishUrl(keyword: String, url: String, username: String, password: String)
   {
      customPublishUrls[keyword] = IHMCPublishUrl(url, username, password)
   }
   
   fun setupJavaSourceSets()
   {
      val java = project.extensions.getByType(JavaPluginExtension::class.java)
      if (compatibilityVersionProperty != "CURRENT")
      {
         java.sourceCompatibility = JavaVersion.valueOf(compatibilityVersionProperty)
         java.targetCompatibility = JavaVersion.valueOf(compatibilityVersionProperty);
      }
      for (sourceSet in java.sourceSets)
      {
         sourceSet.java.setSrcDirs(emptySet<File>())
         sourceSet.resources.setSrcDirs(emptySet<File>())
      }
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("src/main/java")))
      java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("src/main/resources")))

      for (subproject in project.subprojects)
      {
         val javaSubproject = subproject.extensions.getByType(JavaPluginExtension::class.java)
         if (compatibilityVersionProperty != "CURRENT")
         {
            javaSubproject.sourceCompatibility = JavaVersion.valueOf(compatibilityVersionProperty)
            javaSubproject.targetCompatibility = JavaVersion.valueOf(compatibilityVersionProperty);
         }
         for (sourceSet in javaSubproject.sourceSets)
         {
            sourceSet.java.setSrcDirs(emptySet<File>())
            sourceSet.resources.setSrcDirs(emptySet<File>())
         }
         val sourceSetName = IHMCBuildTools.toSourceSetName(subproject)
         javaSubproject.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.setSrcDirs(setOf(project.file("src/$sourceSetName/java")))
         javaSubproject.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("src/$sourceSetName/resources")))

         if (subproject.name.endsWith("test"))
         {
            subproject.tasks.withType<Test>()
            {
               testClassesDirs = javaSubproject.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output.classesDirs
            }
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
//                  java.sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME).resources.setSrcDirs(setOf(project.file("test/resources")))
//               }
//               else
//               {
//                  java.sourceSets.create(extraSourceSet)
//                  java.sourceSets.getByName(extraSourceSet).java.setSrcDirs(setOf(project.file("$extraSourceSet/src")))
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
      return sourceSetProject(sourceSetName).extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
   }
   
   fun sourceSetProject(sourceSetName: String): Project
   {
      if (sourceSetName == "main")
         return project
      else
         return project.project(project.name + "-" + sourceSetName)
   }

   fun javaFXModule(moduleName: String, version: String): String
   {
      return "org.openjfx:javafx-$moduleName:$version:${javaFXOSIdentifier()}"
   }

   fun javaFXOSIdentifier(): String
   {
      return when
      {
         SystemUtils.IS_OS_WINDOWS -> "win"
         SystemUtils.IS_OS_MAC     -> "mac"
         else                      -> "linux"
      }
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
         if (isBambooBuild)
         {
            publishVersion += "-$integrationNumber"
         }
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
      return IHMCBuildTools.isBuildRoot(project)
   }

   fun getIncludedBuilds(): Collection<IncludedBuild>
   {
      if (IHMCBuildTools.isBuildRoot(project))
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
         else if (snapshotModeProperty) // allows to retire `groupDependencyVersion` by allowing "source" to be
         {                              // satisfied by snapshots, but only if snapshotMode = true
            externalDependencyVersion = resolveSnapshotVersion(declaredVersion, groupId, artifactId)
         }
         else
         {
            var message = "$groupId:$artifactId's version is set to \"$declaredVersion\" and is not included in the build. Please put" +
                  " $artifactId in your composite build or use a release."
            LogTools.error(message)
            throw GradleException("[ihmc-build] " + message)
         }
      }
      // Try to resolve a snapshot, check for snapshotMode first
      // Only gonna happen on Bamboo, not supporting this for users
      // NOTE: This code path will probably hardly be used soon
      else if (snapshotModeProperty && declaredVersion.startsWith("SNAPSHOT"))
      {
         externalDependencyVersion = resolveSnapshotVersion(declaredVersion, groupId, artifactId)
      }
      else // Pass directly to gradle as declared
      {
         externalDependencyVersion = declaredVersion
      }

      LogTools.info("Passing version to Gradle: $groupId:$artifactId:$externalDependencyVersion")
      return externalDependencyVersion
   }

   fun resolveSnapshotVersion(declaredVersion: String, groupId: String, artifactId: String): String
   {
      val externalDependencyVersion: String
      var sanitizedDeclaredVersion = declaredVersion.replace("-BAMBOO", "") // not sure where this came from

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
         else // this was a bug for a long time where child builds could use an older snapshot with no warning
         {
            if (closestVersion.contains("NOT-FOUND") && isBranchBuild) // Try latest from branch
            {
               closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT-$branchName")
            }
            if (closestVersion.contains("NOT-FOUND")) // Try latest without branch
            {
               closestVersion = latestPOMCheckedVersionFromRepositories(groupId, artifactId, "SNAPSHOT")
            }
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
      return externalDependencyVersion
   }

   private fun getSnapshotRepositoryList(): List<String>
   {
      if (openSource)
      {
         return listOf("open-snapshots")
      }
      else
      {
         return listOf("open-snapshots", "proprietary-snapshots")
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
               for (asset in searchNexus(repository, groupId, artifactId, ".*\\d\\.jar$"))
               {
                  repositoryVersions["$groupId:$artifactId"]!!.add(itemPathToVersion(asset.getString("path"), artifactId))
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
            if (searchNexus(repository, groupId, artifactId, version, ".*").isNotEmpty())
            {
               if (repositoryVersions.containsKey("$groupId:$artifactId"))
               {
                  repositoryVersions["$groupId:$artifactId"]!!.add(version)
               }
               LogTools.info("Found version circumventing Artifactory bug: $groupId:$artifactId:$version")
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
         return loadPOMDependenciesNexus(groupId, artifactId, versionToCheck)
      }
   }

   private fun loadPOMDependenciesNexus(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf<ArrayList<String>>()

         for (repository in getSnapshotRepositoryList())
         {
            for (asset in searchNexus(repository, groupId, artifactId, versionToCheck, ".*\\d\\.pom$"))
            {
               LogTools.info("Hitting Nexus for POM: " + asset.getString("path"))
               val bytes = downloadItemFromNexus(asset.getString("downloadUrl"))

               parsePOMInputStream(ByteArrayInputStream(bytes), groupId, artifactId, versionToCheck)
            }
         }
      }

      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }

   private fun loadPOMDependenciesArtifactory(groupId: String, artifactId: String, versionToCheck: String): ArrayList<ArrayList<String>>
   {
      if (!pomDependencies.containsKey("$groupId:$artifactId:$versionToCheck"))
      {
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf<ArrayList<String>>()
         
         for (repository in getSnapshotRepositoryList())
         {
            for (repoPath in searchArtifactory(repository, groupId, artifactId, versionToCheck))
            {
               if (repoPath.itemPath.matches(Regex(".*\\d\\.pom$")))
               {
                  LogTools.info("Hitting Artifactory for POM: " + repoPath.itemPath)
                  val inputStream = downloadItemFromArtifactory(repository, repoPath)
                  
                  parsePOMInputStream(inputStream, groupId, artifactId, versionToCheck)
               }
            }
         }
      }
      
      return pomDependencies["$groupId:$artifactId:$versionToCheck"]!!
   }

   private fun searchNexus(repository: String, groupId: String, artifactId: String, version: String, matchPattern: String): List<JSONObject>
   {
      return searchNexus("repository=$repository&maven.groupId=$groupId&maven.artifactId=$artifactId&maven.baseVersion=$version", matchPattern);
   }

   private fun searchNexus(repository: String, groupId: String, artifactId: String, matchPattern: String): List<JSONObject>
   {
      return searchNexus("repository=$repository&maven.groupId=$groupId&maven.artifactId=$artifactId", matchPattern);
   }

   private fun searchNexus(parameters: String, matchPattern: String): List<JSONObject>
   {
      var continuationToken = "first_page"
      val requestUrl = "$nexusUrlProperty/service/rest/v1/search?$parameters"
      val matches = arrayListOf<JSONObject>()
      while (continuationToken != "no_more_pages")
      {
          Unirest.get(if (continuationToken == "first_page") requestUrl else "$requestUrl?continuationToken=$continuationToken")
                 .basicAuth(nexusUsername, nexusPassword)
                 .asJson()
                 .ifSuccess { response ->
                     val bodyObject: JSONObject = response.body.`object`
                     if (bodyObject.isNull("continuationToken"))
                         continuationToken = "no_more_pages"
                     else
                         continuationToken = bodyObject.getString("continuationToken")
                     val items = bodyObject.getJSONArray("items")
                     for (item in items)
                     {
                        val assets = (item as JSONObject).getJSONArray("assets")
                        for (asset in assets)
                        {
                           val assetObject = asset as JSONObject
                           val path = assetObject.getString("path")
                           if (path.matches(Regex(matchPattern)))
                           {
                              matches.add(assetObject)
                           }
                        }
                     }
                 }
                 .ifFailure { response ->
                    throw nexusException(requestUrl)
                 }
      }
      return matches
   }

   private fun downloadItemFromNexus(downloadUrl: String): ByteArray?
   {
      var bytes: ByteArray? = null
      Unirest.get(downloadUrl)
             .basicAuth(nexusUsername, nexusPassword)
             .asBytes()
             .ifSuccess { response ->
                bytes = response.body
             }
             .ifFailure { response ->
                throw nexusException(downloadUrl)
             }
      return bytes
   }

   private fun nexusException(path: String): GradleException
   {
      return GradleException("Problem authenticating or retrieving item from Nexus: $path. " +
              "Try logging into $nexusUrlProperty with the credentials used " +
              "(nexusUsername and nexusPassword properties) and see if the item is there.")
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
      return GradleException("Problem authenticating or retrieving item from Artifactory: $path. " +
                             "Try logging into $artifactoryUrlProperty with the credentials used " +
                             "(artifactoryUsername and artifactoryPassword properties) and see if the item is there.")
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
         pomDependencies["$groupId:$artifactId:$versionToCheck"] = arrayListOf<ArrayList<String>>()

         LogTools.info("Hitting Maven Local for POM: user.home/.gradle/caches/modules-2/files-2.1/$groupId/$artifactId/$versionToCheck")
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
         LogTools.info("Version doesn't exist: $groupId:$artifactId:$versionToCheck")
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
      LogTools.info("Looking for latest version: $groupId:$artifactId:$versionMatcher")

      var highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
      
      if (highestVersion.contains("NOT-FOUND"))
         return highestVersion
      
      while (!performPOMCheck(groupId, artifactId, highestVersion))
      {
         LogTools.info("Failed POM check: $groupId:$artifactId:$highestVersion")
         repositoryVersions["$groupId:$artifactId"]!!.remove(highestVersion)
         highestVersion = highestBuildNumberVersion(groupId, artifactId, versionMatcher)
         
         if (highestVersion.contains("NOT-FOUND"))
         {
            LogTools.error("Rollback failed, no more versions found: $groupId:$artifactId:$highestVersion")
            break;
         }

         LogTools.info("Rolling back to: $groupId:$artifactId:$highestVersion")
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
            val buildNumberFromRepositoryManager: Int = Integer.parseInt(repositoryVersion.split("-").last())
            if (buildNumberFromRepositoryManager > highestBuildNumber)
            {
               matchedVersion = repositoryVersion
               highestBuildNumber = buildNumberFromRepositoryManager
            }
         }
      }
      
      return matchedVersion
   }
   
   private fun Project.configureJarManifest(maintainer: String, companyName: String, licenseURL: String, mainClass: String, libFolder: Boolean)
   {
      tasks.withType(Jar::class.java) {
         manifest.attributes.apply {
            put("Created-By", maintainer)
            put("Implementation-Title", name)
            put("Implementation-Version", archiveVersion.get())
            put("Implementation-Vendor", companyName)

            put("Bundle-Name", name)
            put("Bundle-Version", archiveVersion.get())
            put("Bundle-License", licenseURL)
            put("Bundle-Vendor", companyName)

            if (isBuildRoot() && libFolder)
            {
               var dependencyJarLocations = " "
               for (file in configurations.getByName("default"))
               {
                  dependencyJarLocations += "lib/" + file.name + " "
               }
               put("Class-Path", dependencyJarLocations.trim())
            }
            if (isBuildRoot() && mainClass != "NO_MAIN")
            {
               put("Main-Class", mainClass)
            }
         }
      }
   }
   
   fun Project.declareCustomPublishUrl(keyword: String, publishUrl: IHMCPublishUrl)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven {
         name = IHMCBuildTools.kebabToPascalCase(keyword)
         url = uri(publishUrl.url)
         if (publishUrl.hasCredentials())
         {
            credentials.username = publishUrl.username
            credentials.password = publishUrl.password
         }
      }
   }

   fun Project.declareNexus(repoName: String) {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven {
         name = "Nexus" + IHMCBuildTools.kebabToPascalCase(repoName)
         url = uri("https://nexus.ihmc.us/repository/$repoName")
         credentials.username = nexusUsername
         credentials.password = nexusPassword
      }
   }
   
   fun Project.declareArtifactory(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven {
         name = "Artifactory" + IHMCBuildTools.kebabToPascalCase(repoName)
         url = uri("$artifactoryUrlProperty/artifactory/$repoName")
         credentials.username = artifactoryUsername
         credentials.password = artifactoryPassword
      }
   }

   fun Project.declareMavenCentral(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven {
         name = "MavenCentral" + IHMCBuildTools.kebabToPascalCase(repoName)
         url = uri("https://s01.oss.sonatype.org/content/repositories/$repoName/")
         credentials.username = publishUsername
         credentials.password = publishPassword
      }
   }
   
   fun Project.declareMavenLocal()
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.mavenLocal()
   }
   
   private fun Project.declarePublication(artifactName: String, sourceSet: SourceSet)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = group as String
      publication.artifactId = artifactName
      publication.version = version as String

      LogTools.info("Assembing publication for $name")
      
      publication.pom.withXml {
         val dependenciesNode = asNode().appendNode("dependencies")

         val addedAlready = hashSetOf<String>()
         val exclusions = hashMapOf<String, HashSet<ExcludeRule>>()
         val implementationDependencies = hashSetOf<String>()
         configurations.getByName("implementation").dependencies.forEach { dependency ->
            implementationDependencies.add("${dependency.group}:${dependency.name}:${dependency.version}")
         }
         findExclusions(exclusions, "api")
         findExclusions(exclusions, "implementation")
         findExclusions(exclusions, "runtimeOnly")
         addPOMDependenciesForConfiguration(dependenciesNode, addedAlready, exclusions, implementationDependencies, "compileClasspath")
         addPOMDependenciesForConfiguration(dependenciesNode, addedAlready, exclusions, implementationDependencies, "runtimeClasspath")

         asNode().appendNode("description", titleCasedNameProperty)
         asNode().appendNode("name", name)
         asNode().appendNode("url", vcsUrl)
         val licensesNode = asNode().appendNode("licenses")

         val licenseNode = licensesNode.appendNode("license")
         licenseNode.appendNode("name", licenseName)
         licenseNode.appendNode("url", licenseURL)
         licenseNode.appendNode("distribution", "repo")
      }

      publication.artifact(tasks.withType<Jar>().getByName("jar"))

      publication.artifact(tasks.create("sourceJar", Jar::class.java) {
         from(sourceSet.allJava)
         archiveClassifier.set("sources")
      })
   }

   private fun Project.addPOMDependenciesForConfiguration(dependenciesNode: Node,
                                                          addedAlready: HashSet<String>,
                                                          exclusions: HashMap<String, HashSet<ExcludeRule>>,
                                                          implementationDependencies: HashSet<String>,
                                                          configurationName: String)
   {
      configurations.getByName(configurationName).resolvedConfiguration.run {
         firstLevelModuleDependencies.forEach { firstLevelModuleDependency ->
            LogTools.info("First level dependency: configuration: $configurationName dependency: $firstLevelModuleDependency")

            val classifiers = hashSetOf<String>()
            resolvedArtifacts.forEach { resolvedArtifact ->
               if (resolvedArtifact.id.componentIdentifier.displayName.equals(firstLevelModuleDependency.name))
               {
                  val classifier = resolvedArtifact.classifier
                  if (classifier != null && classifier.isNotEmpty())
                  {
                     LogTools.info("Classifier: $classifier")
                     classifiers.add(classifier)
                  }
                  else
                  {
                     classifiers.add("")
                  }
               }
            }

            if (classifiers.isEmpty())
            {
               classifiers.add("")
            }

            classifiers.forEach { classifier ->
               val dependencyGAVKey = firstLevelModuleDependency.moduleGroup +
                                      ":${firstLevelModuleDependency.moduleName}" +
                                      ":${firstLevelModuleDependency.moduleVersion}"
               val dependencyGAVWithClassifierKey = "$dependencyGAVKey:$classifier"

               if (!addedAlready.contains(dependencyGAVWithClassifierKey))
               {
                  addedAlready.add(dependencyGAVWithClassifierKey)

                  val dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", firstLevelModuleDependency.moduleGroup)
                  dependencyNode.appendNode("artifactId", firstLevelModuleDependency.moduleName)
                  dependencyNode.appendNode("version", firstLevelModuleDependency.moduleVersion)
                  exclusions.computeIfPresent(dependencyGAVKey) { _, excludeRules ->
                     val exclusionsNode = dependencyNode.appendNode("exclusions")
                     excludeRules.forEach { excludeRule ->
                        val exclusionNode = exclusionsNode.appendNode("exclusion")
                        exclusionNode.appendNode("groupId", excludeRule.group)
                        var excludeString = excludeRule.group
                        if (excludeRule.module != null) // warning that it's always false but it's not
                        {
                           exclusionNode.appendNode("artifactId", excludeRule.module)
                           excludeString += ":" + excludeRule.module
                        }
                        LogTools.quiet("Excluding transitive(s) in POM: $excludeString")
                     }
                     excludeRules
                  }
                  if (classifier.isNotEmpty())
                     dependencyNode.appendNode("classifier", classifier)
                  var scope = configurationName.removeSuffix("Classpath")
                  var implementationReportString = ""
                  if (implementationDependencies.contains(dependencyGAVKey))
                  {
                     implementationReportString += " (implementation)"
                     scope = "runtime"
                  }
                  dependencyNode.appendNode("scope", scope)

                  LogTools.quiet("Adding dependency to POM: $dependencyGAVWithClassifierKey:$scope$implementationReportString")
               }
            }
         }
      }
   }

   private fun Project.findExclusions(exclusions: HashMap<String, HashSet<ExcludeRule>>, configurationName: String)
   {
      configurations.getByName(configurationName).dependencies.forEach { dependency ->
         if (dependency is DefaultExternalModuleDependency)
         {
            dependency.excludeRules.forEach { excludeRule ->
               val key = "${dependency.group}:${dependency.name}:${dependency.version}"
               exclusions.computeIfAbsent(key) { hashSetOf() }
               exclusions[key]!!.add(excludeRule)
            }
         }
      }
   }
}