package us.ihmc.build

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.extra
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
   
   fun Project.declarePublication(artifactName: String, configuration: Configuration,
                                  sourceSet: SourceSet, vararg internalDependencies: String)
   {
      val publishing = extensions.getByType(PublishingExtension::class.java)
      val publication = publishing.publications.create(sourceSet.name.capitalize(), MavenPublication::class.java)
      publication.groupId = project.group as String
      publication.artifactId = artifactName
      publication.version = project.version as String

      publication.pom.withXml() {
         (this as XmlProvider).run {
            var dependenciesNode = asNode().appendNode("dependencies")
            
            internalDependencies.forEach {
               var internalDependency = dependenciesNode.appendNode("dependency")
               internalDependency.appendNode("groupId", project.group)
               internalDependency.appendNode("artifactId", it)
               internalDependency.appendNode("version", project.version)
            }
            
            configuration.allDependencies.forEach {
               if (it.name != "unspecified")
               {
                  var dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", project.group)
                  dependencyNode.appendNode("artifactId", it)
                  dependencyNode.appendNode("version", project.version)
               }
            }
            
            asNode().children().last()
//
//                  . + {
//               resolveStrategy = DELEGATE_FIRST
//               name project.name
//                     url project.extras["vcsUrl"]
//                     licenses {
//                        license {
//                           name project.extras["licenseName"]
//                                 url project.extras["licenseURL"]
//                                 distribution "repo"
//                        }
//                     }
//            }
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