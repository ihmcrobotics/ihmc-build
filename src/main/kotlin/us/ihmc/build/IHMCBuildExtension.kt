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
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import us.ihmc.continuousIntegration.AgileTestingTools
import java.io.File
import java.io.FileInputStream
import java.util.*

open class IHMCBuildExtension(val project: Project)
{
   internal val logger = project.logger
   internal val offline: Boolean = project.gradle.startParameter.isOffline
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
   internal lateinit var artifactoryUsername: String
   internal lateinit var artifactoryPassword: String
   
   private val publishModeProperty: String
   private val kebabCasedNameProperty: String
   private val groupDependencyVersionProperty: String
   
   // Bamboo variables
   internal val isChildBuild: Boolean
   internal val isBambooBuild: Boolean
   internal val buildNumber: String
   internal lateinit var publishVersion: String
   internal val isBranchBuild: Boolean
   internal val branchName: String
   
   internal val versionFilter: IHMCVersionFilter
   
   init
   {
      bintrayUser = setupPropertyWithDefault("bintray_user", "unset_user")
      bintrayApiKey = setupPropertyWithDefault("bintray_key", "unset_api_key")
      artifactoryUsername = setupPropertyWithDefault("artifactoryUsername", "unset_username")
      artifactoryPassword = setupPropertyWithDefault("artifactoryPassword", "unset_password")
      
      groupDependencyVersionProperty = setupPropertyWithDefault("groupDependencyVersion", "SNAPSHOT-LATEST")
      publishModeProperty = setupPropertyWithDefault("publishMode", "SNAPSHOT")
      kebabCasedNameProperty = kebabCasedNameCompatibility(project.name, logger, project.extra)
      
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
         buildNumber = requestGlobalBuildNumberFromCIDatabase(logger, bambooParentBuildKeyProperty)
      }
      else
      {
         buildNumber = requestGlobalBuildNumberFromCIDatabase(logger, "$bambooPlanKeyProperty-$bambooBuildNumberProperty")
      }
      isBranchBuild = !bambooBranchNameProperty.isEmpty() && bambooBranchNameProperty != "develop" && bambooBranchNameProperty != "master"
      branchName = bambooBranchNameProperty.replace("/", "-")
      
      versionFilter = IHMCVersionFilter(this, project)
   }
   
   /** Public API. */
   fun configureDependencyResolution()
   {
      repository("https://artifactory.ihmc.us/artifactory/snapshots/")
      repository("http://dl.bintray.com/ihmcrobotics/maven-release")
      if (!openSource)
      {
         repository("https://artifactory.ihmc.us/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
         repository("https://artifactory.ihmc.us/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
         repository("https://artifactory.ihmc.us/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
      }
      for (allproject in project.allprojects)
      {
         allproject.repositories.jcenter()
         allproject.repositories.mavenCentral()
         allproject.repositories.mavenLocal()
      }
      repository("http://dl.bintray.com/ihmcrobotics/maven-vendor")
      repository("http://clojars.org/repo/")
      repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master")
      repository("https://oss.sonatype.org/content/repositories/snapshots")
      
      setupJavaSourceSets()
      
      try
      {
         val testProject = project.project(":" + kebabCasedNameProperty + "-test")
         testProject.dependencies {
            add("compile", project)
            add("compile", "us.ihmc:ihmc-ci-core-api:0.17.0")
         }
      }
      catch (e: UnknownProjectException)
      {
      }
   }
   
   /** Public API. */
   fun configurePublications()
   {
      if (openSource)
      {
         licenseURL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
         licenseName = "Apache License, Version 2.0"
      }
      
      val productGroup = group
      for (allproject in project.allprojects)
      {
         allproject.group = productGroup
         publishVersion = getPublishVersion()
         allproject.version = publishVersion
         
         configureJarManifest(allproject, maintainer, companyName, licenseURL, "NO_MAIN", false)
         
         if (publishModeProperty == "SNAPSHOT")
         {
            if (openSource)
            {
               declareArtifactory(allproject, "snapshots")
            }
            else
            {
               declareArtifactory(allproject, "proprietary-snapshots")
            }
         }
         else if (publishModeProperty == "STABLE")
         {
            if (openSource)
            {
               declareBintray(allproject)
            }
            else
            {
               declareArtifactory(allproject, "proprietary")
            }
         }
         
         declarePublication(allproject)
      }
   }
   
   private fun getPublishVersion(): String
   {
      if (publishModeProperty == "STABLE")
      {
         return version
      }
      else if (publishModeProperty == "SNAPSHOT")
      {
         var publishVersion = "SNAPSHOT"
         if (isBranchBuild)
         {
            publishVersion += "-$branchName"
         }
         publishVersion += "-$buildNumber"
         return publishVersion
      }
      else
      {
         return publishModeProperty
      }
   }
   
   private fun declarePublication(project: Project)
   {
      val sourceSet = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = group
      publication.artifactId = project.name
      publication.version = version
      
      publication.pom.withXml() {
         (this as XmlProvider).run {
            val dependenciesNode = asNode().appendNode("dependencies")
            
            project.configurations.getByName("compile").allDependencies.forEach {
               if (it.name != "unspecified")
               {
                  val dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", it.group)
                  dependencyNode.appendNode("artifactId", it.name)
                  dependencyNode.appendNode("version", it.version)
               }
            }
            
            asNode().appendNode("name", project.name)
            asNode().appendNode("url", vcsUrl)
            val licensesNode = asNode().appendNode("licenses")
            
            val licenseNode = licensesNode.appendNode("license")
            licenseNode.appendNode("name", licenseName)
            licenseNode.appendNode("url", licenseURL)
            licenseNode.appendNode("distribution", "repo")
         }
      }
      
      publication.artifact(project.task(mapOf("type" to Jar::class.java), sourceSet.name + "ClassesJar", closureOf<Jar> {
         from(sourceSet.output)
      }))
      
      publication.artifact(project.task(mapOf("type" to Jar::class.java), sourceSet.name + "SourcesJar", closureOf<Jar> {
         from(sourceSet.allJava)
         classifier = "sources"
      }))
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
   }
   
   /** Public API. */
   fun repository(url: String)
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.run {
            maven {}.url = allproject.uri(url)
         }
      }
   }
   
   /** Public API. */
   fun repository(url: String, username: String, password: String)
   {
      for (allproject in project.allprojects)
      {
         allproject.repositories.run {
            val maven = maven {}
            maven.url = allproject.uri(url)
            maven.credentials.username = username
            maven.credentials.password = password
         }
      }
   }
   
   /** Public API. */
   fun mainClassJarWithLibFolder(mainClass: String)
   {
      for (allproject in project.allprojects)
      {
         configureJarManifest(allproject, maintainer, companyName, licenseURL, mainClass, true)
      }
   }
   
   /** Public API. */
   fun jarWithLibFolder()
   {
      for (allproject in project.allprojects)
      {
         configureJarManifest(allproject, maintainer, companyName, licenseURL, "NO_MAIN", true)
      }
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
      return module(sourceSetName).convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
   }
   
   @Deprecated("Use module() instead.")
         /** Public API. */
   fun sourceSetProject(sourceSetName: String): Project
   {
      return module(sourceSetName)
   }
   
   /**
    * Public API. Gets a module by name.
    */
   fun module(moduleName: String): Project
   {
      if (moduleName == "main")
         return project
      else
         return project.project(project.name + "-" + moduleName)
   }
   
   private fun configureJarManifest(project: Project, maintainer: String, companyName: String, licenseURL: String, mainClass: String, libFolder: Boolean)
   {
      project.tasks.getByName("jar") {
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
               
               if (isBuildRoot() && libFolder)
               {
                  var dependencyJarLocations = " "
                  for (file in project.configurations.getByName("runtime"))
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
   }
   
   fun declareArtifactory(project: Project, repoName: String)
   {
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "Artifactory"
         url = project.uri("https://artifactory.ihmc.us/artifactory/" + repoName)
         credentials.username = artifactoryUsername
         credentials.password = artifactoryPassword
      })
   }
   
   fun declareBintray(project: Project)
   {
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "BintrayRelease"
         url = project.uri("https://api.bintray.com/maven/ihmcrobotics/maven-release/" + project.rootProject.name)
         credentials.username = bintrayUser
         credentials.password = bintrayApiKey
      })
   }
   
   /**
    * @deprecated Use convertJobNameToKebabCasedName instead.
    */
   fun convertJobNameToHyphenatedName(jobName: String): String
   {
      return convertJobNameToKebabCasedName(jobName)
   }
   
   /**
    * Public API. Used for artifact-test-runner to keep easy Bamboo configuration.
    * Job names are pascal cased on Bamboo and use this method to
    * resolve their kebab cased artifact counterparts.
    */
   fun convertJobNameToKebabCasedName(jobName: String): String
   {
      return AgileTestingTools.pascalCasedToHyphenatedWithoutJob(jobName)
   }
   
   /** Public API. */
   fun isBuildRoot(): Boolean
   {
      return isBuildRoot(project)
   }
   
   /** Public API. */
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
   
   private fun setupPropertyWithDefault(propertyName: String, defaultValue: String): String
   {
      if (project.hasProperty(propertyName) && !(project.property(propertyName) as String).startsWith("$"))
      {
         return project.property(propertyName) as String
      }
      else
      {
         if (propertyName == "artifactoryUsername" || propertyName == "artifactoryPassword")
         {
            if (!openSource)
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
}