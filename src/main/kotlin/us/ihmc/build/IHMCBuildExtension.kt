package us.ihmc.build

import groovy.lang.Closure
import groovy.util.Node
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.impldep.aQute.bnd.maven.MavenRepository
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.extra
import org.jfrog.artifactory.client.Artifactory
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import org.jfrog.artifactory.client.model.RepoPath
import us.ihmc.continuousIntegration.AgileTestingTools

class IHMCBuildExtension(project: Project)
{
   fun configureProjectForOpenRobotics(project: Project)
   {
      project.run {
         val java = project.convention.getPlugin(JavaPluginConvention::class.java)
         val mainSourceSet = java.sourceSets.getByName("main")
         tasks.getByName("jar") {
            (this as Jar).run {
               from(mainSourceSet.allSource)
               manifest.attributes.apply {
                  put("Implementation-Title", "Gradle Kotlin DSL (${project.name})")
                  put("Implementation-Version", version)
               }
            }
         }
      }
   }
   
   fun Project.getBuildVersion(groupId: String, artifactId: String, dependencyMode: String): String
   {
      val username = property("artifactoryUsername") as String
      val password = property("artifactoryPassword") as String
      
      var buildVersion: String? = "error";
      if (dependencyMode.startsWith("STABLE"))
      {
         val firstDash: Int = dependencyMode.indexOf("-");
         if (firstDash > 0)
         {
            buildVersion = dependencyMode.substring(firstDash + 1);
         }
      }
      else
      {
         try
         {
            val builder: ArtifactoryClientBuilder = ArtifactoryClientBuilder.create()
            builder.url = "https://artifactory.ihmc.us/artifactory"
            builder.username = username
            builder.password = password
            val artifactory: Artifactory = builder.build()
            val snapshots: List<RepoPath> = artifactory.searches().artifactsByGavc().repositories("snapshots").groupId("us.ihmc").artifactId(artifactId).doSearch()
            
            var latestVersion: String? = null
            var latestBuildNumber: Int = -1
            for (repoPath in snapshots)
            {
               if (repoPath.getItemPath().endsWith(".pom") || repoPath.getItemPath().endsWith("sources.jar"))
               {
                  continue;
               }
               
               if (!repoPath.getItemPath().contains(dependencyMode))
               {
                  continue;
               }
               
               val version: String = itemPathToVersion(repoPath.getItemPath(), artifactId);
               val buildNumber: Int = buildNumber (version);
               
               // Found exact nightly
               if (version.endsWith(dependencyMode))
               {
                  buildVersion = itemPathToVersion(repoPath.getItemPath(), artifactId);
               }
               
               if (latestVersion == null)
               {
                  latestVersion = version;
                  latestBuildNumber = buildNumber;
               }
               else if (buildNumber > latestBuildNumber)
               {
                  latestVersion = version;
                  latestBuildNumber = buildNumber;
               }
            }
            
            buildVersion = latestVersion;
         }
         catch (exception: Exception)
         {
            System.out.println("Artifactory could not be reached, reverting to latest.");
            buildVersion = "error";
         }
      }
      
      if (buildVersion == null) buildVersion = "error"
      
      return groupId + ":" + artifactId + ":" + buildVersion;
   }
   
   fun buildNumber(version: String): Int
   {
      return Integer.parseInt(version.split("-")[2]);
   }
   
   fun itemPathToVersion(itemPath: String, artifactId: String): String
   {
      val split: List<String> = itemPath.split("/")
      val artifact: String = split[split.size - 1]
      val withoutDotJar: String = artifact.split("\\.jar")[0]
      val version: String = withoutDotJar.substring(artifactId.length + 1)
      
      return version
   }
   
   fun Project.setupCommonJARConfiguration()
   {
      setupCommonJARConfiguration(project.extra["maintainer"] as String,
                                  project.extra["companyName"] as String, project.extra["licenseURL"] as String)
   }
   
   fun Project.setupCommonJARConfiguration(maintainer: String, companyName: String, licenseURL: String)
   {
      tasks.getByName("jar") {
         (this as Jar).run {
            manifest.attributes.apply {
               put("Created-By", maintainer)
               put("Implementation-Title", project.name)
               put("Implementation-Version", project.version)
               put("Implementation-Vendor", companyName)
               
               put("Bundle-Name", project.name)
               put("Bundle-Version", project.version)
               put("Bundle-License", licenseURL)
               put("Bundle-Vendor", companyName)
            }
         }
      }
   }
   
   fun Project.declareArtifactory()
   {
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "Artifactory"
         url = uri("https://artifactory.ihmc.us/artifactory/" + project.name)
         credentials.username = property("artifactoryUsername") as String
         credentials.password = property("artifactoryPassword") as String
      })
   }
   
   fun Project.declareBintray()
   {
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      publishing.repositories.maven(closureOf<MavenArtifactRepository> {
         name = "BintrayRelease"
         url = uri("https://api.bintray.com/maven/ihmcrobotics/maven-release/" + project.name)
         credentials.username = property("bintray_user") as String
         credentials.password = property("bintray_key") as String
      })
   }
   
   fun Project.declarePublication(artifactName: String, configuration: Configuration,
                                  sourceSet: SourceSet, vararg internalDependencies: String)
   {
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = project.group as String
      publication.artifactId = artifactName
      publication.version = project.version as String
      
      publication.pom.withXml() {
         (this as XmlProvider).run {
            val dependenciesNode = asNode().appendNode("dependencies")
            
            internalDependencies.forEach {
               val internalDependency = dependenciesNode.appendNode("dependency")
               internalDependency.appendNode("groupId", project.group)
               internalDependency.appendNode("artifactId", it)
               internalDependency.appendNode("version", project.version)
            }
            
            configuration.allDependencies.forEach {
               if (it.name != "unspecified")
               {
                  val dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", project.group)
                  dependencyNode.appendNode("artifactId", it)
                  dependencyNode.appendNode("version", project.version)
               }
            }
            
            asNode().appendNode("name", project.name)
            asNode().appendNode("url", project.extra["vcsUrl"])
            val licensesNode = asNode().appendNode("licenses")
            
            val licenseNode = licensesNode.appendNode("license")
            licenseNode.appendNode("name", project.project.extra["licenseName"])
            licenseNode.appendNode("url", project.project.extra["licenseURL"])
            licenseNode.appendNode("distribution", "repo")
         }
         
         publication.artifact(project.task(mapOf("type" to Jar::class.java), sourceSet.name + "ClassesJar", closureOf<Jar> {
            from(sourceSet.output)
         }))
         
         publication.artifact(project.task(mapOf("type" to Jar::class.java), sourceSet.name + "SourcesJar", closureOf<Jar> {
            from(sourceSet.allJava)
            classifier = "sources"
         }))
      }
   }
   
   fun Project.setupAggressiveResolutionStrategy()
   {
      configurations.forEach {
         it.resolutionStrategy.run {
            preferProjectModules()
            cacheDynamicVersionsFor(0, "seconds")
            cacheChangingModulesFor(0, "seconds")
         }
      }
   }
   
   fun convertJobNameToHyphenatedName(jobName: String): String
   {
      return AgileTestingTools.pascalCasedToHyphenatedWithoutJob(jobName)
   }
}