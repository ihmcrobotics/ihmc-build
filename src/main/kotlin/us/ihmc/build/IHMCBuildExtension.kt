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
   val isBuildRoot = isBuildRoot(project)
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
      repository("http://dl.bintray.com/ihmcrobotics/maven-release")
      if (!openSource)
      {
         repository("https://artifactory.ihmc.us/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
         repository("https://artifactory.ihmc.us/artifactory/proprietary-snapshots/", artifactoryUsername, artifactoryPassword)
         repository("https://artifactory.ihmc.us/artifactory/proprietary-vendor/", artifactoryUsername, artifactoryPassword)
      }
      project.allprojects {
         (this as Project).run {
            repositories.jcenter()
            repositories.mavenCentral()
            repositories.mavenLocal()
         }
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
               
               if (isBuildRoot && libFolder)
               {
                  var dependencyJarLocations = " "
                  for (file in configurations.getByName("runtime"))
                  {
                     dependencyJarLocations += "lib/" + file.name + " "
                  }
                  put("Class-Path", dependencyJarLocations.trim())
               }
               if (isBuildRoot && mainClass != "NO_MAIN")
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
         url = uri("https://api.bintray.com/maven/ihmcrobotics/maven-release/" + rootProject.name)
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