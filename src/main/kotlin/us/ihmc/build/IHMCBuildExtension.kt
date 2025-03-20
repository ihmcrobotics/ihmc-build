package us.ihmc.build

import groovy.util.Eval
import groovy.util.Node
import org.apache.commons.exec.OS
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
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
   var maintainer: String = "ihmc-rosie (rosie@ihmc.org)"

   private var ihmcNexusUrl = "https://nexus.ihmc.us"
   private var ihmcNexusUsername: String
   private var ihmcNexusPassword: String

   private val publishUsername: String
   private val publishPassword: String

   private val titleCasedNameProperty: String
   private val kebabCasedNameProperty: String
   private val publishUrlProperty: String
   private var compatibilityVersionProperty: String
   private val customPublishUrls by lazy { hashMapOf<String, IHMCPublishUrl>() }

   private lateinit var publishVersion: String

   private val includedBuildMap = hashMapOf<String, Boolean>()

   init
   {
      ihmcNexusUsername = setupPropertyWithDefault("nexusUsername", "unset_username")
      ihmcNexusPassword = setupPropertyWithDefault("nexusPassword", "unset_password")
      publishUsername = setupPropertyWithDefault("publishUsername", "")
      publishPassword = setupPropertyWithDefault("publishPassword", "")

      publishUrlProperty = IHMCBuildTools.publishUrlCompatibility(project.extra)
      compatibilityVersionProperty = IHMCBuildTools.compatibilityVersionCompatibility(project.extra)

      titleCasedNameProperty = IHMCBuildTools.titleCasedNameCompatibility(project.name, project.extra)
      kebabCasedNameProperty = IHMCBuildTools.kebabCasedNameCompatibility(project.name, project.extra)
   }

   fun setupPropertyWithDefault(propertyName: String, defaultValue: String): String
   {
      if (project.hasProperty(propertyName) && !(project.property(propertyName) as String).startsWith("$"))
      {
         return project.property(propertyName) as String
      }
      else
      {
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
            LogTools.info("Loaded group: $group")
         }
         if (property.key as String == "version")
         {
            version = property.value as String
            LogTools.info("Loaded version: $version")
         }
         if (property.key as String == "vcsUrl")
         {
            vcsUrl = property.value as String
            LogTools.info("Loaded vcsUrl: $vcsUrl")
         }
         if (property.key as String == "openSource")
         {
            openSource = Eval.me(property.value as String) as Boolean
            LogTools.info("Loaded openSource: $openSource")
         }
      }
   }

   fun configureDependencyResolution()
   {
      declareMavenLocal()

      declareMavenCentral()

      // If we use these endpoints directly instead of declareMavenCentral() (https://repo.maven.apache.org/maven2), we don't have to wait
      // for the artifacts to propagate within their CDN setup. They're available much more quickly after publishing.
      // 03/20/25 - We tried this for some time and found that s01 sonatype sometimes has reliability issues (going down, returning 503 on artifacts, etc)

      // Sonatype releases
      // repository("https://oss.sonatype.org/content/repositories/releases")
      // repository("https://s01.oss.sonatype.org/content/repositories/releases")
      // Sonatype snapshots
      // repository("https://oss.sonatype.org/content/repositories/snapshots")
      // repository("https://s01.oss.sonatype.org/content/repositories/snapshots")

      repository("https://github.com/rosjava/rosjava_mvn_repo/raw/master") // TODO: remove
      repository("https://raw.githubusercontent.com/ihmcrobotics/maven-artifacts-archive/main/") // TODO: remove

      repository("https://jitpack.io") // Used for kryonet and gdx-gltf

      if (!openSource && (ihmcNexusUsername != "unset_username")) // support third parties not needing to declare Nexus
      {
         repository("$ihmcNexusUrl/repository/proprietary-releases/", ihmcNexusUsername, ihmcNexusPassword)
         repository("$ihmcNexusUrl/repository/proprietary-vendor/", ihmcNexusUsername, ihmcNexusPassword)
      }

      setupJavaSourceSets()

      try // always declare dependency on "main" from "test"
      {
         val testProject = project.project(":$kebabCasedNameProperty-test")
         testProject.dependencies.add("api", project)
      }
      catch (_: UnknownProjectException)
      {

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
         java.targetCompatibility = JavaVersion.valueOf(compatibilityVersionProperty)
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
            javaSubproject.targetCompatibility = JavaVersion.valueOf(compatibilityVersionProperty)
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
   }

   fun javaDirectory(sourceSetName: String, directory: String)
   {
      var modifiedDirectory = directory
      if (sourceSetName == "main")
         modifiedDirectory = "src/main/$directory"

      sourceSet(sourceSetName).java.srcDir(modifiedDirectory)
   }

   fun resourceDirectory(sourceSetName: String, directory: String)
   {
      var modifiedDirectory = directory
      if (sourceSetName == "main")
         modifiedDirectory = "src/main/$directory"

      sourceSet(sourceSetName).resources.srcDir(modifiedDirectory)
   }

   fun sourceSet(sourceSetName: String): SourceSet
   {
      return sourceSetProject(sourceSetName).extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
   }

   fun sourceSetProject(sourceSetName: String): Project
   {
      return if (sourceSetName == "main")
         project
      else
         project.project(project.name + "-" + sourceSetName)
   }

   fun javaFXModule(moduleName: String, version: String): String
   {
      return "org.openjfx:javafx-$moduleName:$version:${javaFXOSIdentifier()}"
   }

   fun javaFXOSIdentifier(): String
   {
      var archSuffix = ""
      val isARM64 = System.getProperty("os.arch").equals("aarch64")
            || System.getProperty("os.arch").equals("arm64")
            || System.getProperty("ihmc.build.javafxarm64", "false").equals("true")
      if (isARM64)
         archSuffix = "-aarch64"

      return when
      {

         OS.isFamilyWindows() -> "win" // No additional platforms for win
         OS.isFamilyMac()     -> "mac$archSuffix"
         OS.isFamilyUnix()    -> "linux$archSuffix"
         else                 ->
         {
            throw RuntimeException("Unsupported javafx platform")
         }
      }
   }

   private fun getPublishVersion(): String
   {
      return version
   }

   /** Public API. **/
   fun isBuildRoot(): Boolean
   {
      return IHMCBuildTools.isBuildRoot(project)
   }

   fun getIncludedBuilds(): Collection<IHMCIncludedBuild>
   {
      val includedBuilds = ArrayList<IHMCIncludedBuild>()
      if (IHMCBuildTools.isBuildRoot(project))
      {
         for (includedBuild in project.gradle.includedBuilds)
         {
            includedBuilds.add(IHMCIncludedBuild(includedBuild.name, includedBuild.projectDir))
         }
      }
      else
      {
         val parent = project.gradle.parent!!
         for (includedBuild in parent.includedBuilds)
         {
            includedBuilds.add(IHMCIncludedBuild(includedBuild.name, includedBuild.projectDir))
         }
         // This was a bug for a while where we didn't include the parent as an included build, which it is.
         includedBuilds.add(IHMCIncludedBuild(parent.rootProject.name, parent.rootProject.projectDir))
      }
      return includedBuilds
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
               for (extraSourceSet in IHMCBuildProperties(includedBuild.projectDir.toPath()).extraSourceSets)
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
      val externalDependencyVersion: String

      // For high-level projects depending on develop,
      // use version: "source" to make sure you've got everything, and fail fast
      if (declaredVersion.lowercase().contains("source"))
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
            val message = "$groupId:$artifactId's version is set to \"$declaredVersion\" and is not included in the build. Please put" +
                  " $artifactId in your composite build or use a release."
            LogTools.error(message)
            throw GradleException("[ihmc-build] $message")
         }
      }
      else // Pass directly to gradle as declared
      {
         externalDependencyVersion = declaredVersion
      }

      LogTools.info("Passing version to Gradle: $groupId:$artifactId:$externalDependencyVersion")
      return externalDependencyVersion
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
               for (file in configurations.getByName("runtimeClasspath"))
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

   fun Project.declareNexus(repoName: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven {
         name = "Nexus" + IHMCBuildTools.kebabToPascalCase(repoName)
         url = uri("$ihmcNexusUrl/repository/$repoName")
         credentials.username = ihmcNexusUsername
         credentials.password = ihmcNexusPassword
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
      val publication = publishing.publications.create(sourceSet.name.replaceFirstChar {
         if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
      }, MavenPublication::class.java)
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
      // TODO: cleanup
      configurations.getByName(configurationName).resolvedConfiguration.run {
         // Get each of the resolved artifacts for the configuration
         // firstLevelModuleDependencies may not return all dependencies if they aren't resolved locally
         resolvedArtifacts.forEach { artifact ->
            val dependencyGAVKey = "${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}:${artifact.moduleVersion.id.version}"
            var classifier = artifact.classifier
            if (classifier == null)
               classifier = ""

            val dependencyGAVWithClassifierKey = "$dependencyGAVKey:$classifier"

            if (!addedAlready.contains(dependencyGAVWithClassifierKey))
            {
               addedAlready.add(dependencyGAVWithClassifierKey)

               val dependencyNode = dependenciesNode.appendNode("dependency")
               dependencyNode.appendNode("groupId", artifact.moduleVersion.id.group)
               dependencyNode.appendNode("artifactId", artifact.moduleVersion.id.name)
               dependencyNode.appendNode("version", artifact.moduleVersion.id.version)
               exclusions.computeIfPresent(dependencyGAVKey) { _, excludeRules ->
                  val exclusionsNode = dependencyNode.appendNode("exclusions")
                  excludeRules.forEach { excludeRule ->
                     val exclusionNode = exclusionsNode.appendNode("exclusion")
                     exclusionNode.appendNode("groupId", excludeRule.group)
                     var excludeString = excludeRule.group
                     exclusionNode.appendNode("artifactId", excludeRule.module)
                     excludeString += ":" + excludeRule.module
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

   private fun Project.findExclusions(exclusions: HashMap<String, HashSet<ExcludeRule>>, configurationName: String)
   {
      // TODO: cleanup
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
