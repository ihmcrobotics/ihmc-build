package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.dependencies

open class IHMCExtraDependenciesExtension(val project: Project, val name: String, val ihmcBuildExtension: IHMCBuildExtension)
{
   val hyphenatedName: String = project.property("hyphenatedName") as String
   val subproject by lazy {
      project.project(":$hyphenatedName-$name")
   }
   
   fun compile(project: Project)
   {
      subproject.dependencies {
         add("compile", project)
      }
   }
   
   fun compile(dependencyNotation: String)
   {
      val split = dependencyNotation.split(":")
      val groupId = split[0]
      val artifactName = split[1]
      val dependencyMode = split[2]
      
      val modifiedDependency = ihmcBuildExtension.getBuildVersion(groupId, artifactName, dependencyMode)
      
      compile(modifiedDependency[0], modifiedDependency[1], modifiedDependency[2])
   }
   
   fun compile(dependencyNotation: Map<String?, Any?>)
   {
      val groupId = dependencyNotation.get("group") as String
      val artifactName = dependencyNotation.get("name") as String
      val dependencyMode = dependencyNotation.get("version") as String
      
      val modifiedDependency = ihmcBuildExtension.getBuildVersion(groupId, artifactName, dependencyMode)
      
      compile(modifiedDependency[0], modifiedDependency[1], modifiedDependency[2])
   }
   
   private fun compile(group: String, name: String, version: String)
   {
      subproject.dependencies {
         add("compile", "$group:$name:$version")
      }
   }
}