package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.extra

open class IHMCDependenciesExtension(private val mainProject: Project,
                                     private val sourceSetKebabCasedName: String,
                                     private val ihmcBuildExtension: IHMCBuildExtension,
                                     private val dependencies: DependencyHandler)
   : DependencyHandler by dependencies  // Allows us to override only the methods we need to
{
   private val kebabCasedName: String = IHMCBuildTools.kebabCasedNameCompatibility(mainProject.name, mainProject.extra)
   private val projectToConfigure by lazy {
      if (sourceSetKebabCasedName == "main")
      {
         mainProject
      }
      else
      {
         mainProject.project(":$kebabCasedName-$sourceSetKebabCasedName")
      }
   }

   override fun create(dependencyNotation: Any): Dependency
   {
      return dependencies.create(modifyDependency(dependencyNotation))
   }

   override fun add(configurationName: String, dependencyNotation: Any): Dependency?  // trick for Kotlin
   {
      return filterAndAddDependency(configurationName, dependencyNotation)
   }

   private fun filterAndAddDependency(configurationName: String, dependencyNotation: Any): Dependency?
   {
      val modifiedDependencyNotation = processDependencyDeclaration(configurationName, dependencyNotation)
      return dependencies.add(configurationName, modifiedDependencyNotation)
   }

   private fun processDependencyDeclaration(configurationName: String, dependencyNotation: Any): Any
   {
      val modifiedDependencyNotation = modifyDependency(dependencyNotation)

      LogTools.debug("Adding dependency to " + projectToConfigure.name + ": $modifiedDependencyNotation")
      
      if (configurationName != "api")
      {
         LogTools.debug(" Unusual dependency on configuration: $configurationName: $dependencyNotation")
      }
      
      return modifiedDependencyNotation
   }
   
   private fun modifyDependency(dependencyNotation: Any): Any
   {
      if (dependencyNotation is String)
      {
         val split = dependencyNotation.split(":")

         val modifiedVersion = ihmcBuildExtension.getExternalDependencyVersion(split[0], split[1], split[2])

         var modifiedString = ""
         for (i in split.indices)
         {
            modifiedString += if (i == 2)
            {
               modifiedVersion
            } else
            {
               split[i]
            }

            if (i < split.size - 1)
            {
               modifiedString += ":"
            }
         }

         return modifiedString
      }
      else if (dependencyNotation is Map<*, *>)
      {
         val groupId: String
         val artifactName: String
         val dependencyMode: String

         if (dependencyNotation.contains("group") && dependencyNotation["group"] is String)
         {
            groupId = dependencyNotation["group"] as String
         }
         else
         {
            return dependencyNotation
         }
         if (dependencyNotation.contains("name") && dependencyNotation["name"] is String)
         {
            artifactName = dependencyNotation["name"] as String
         }
         else
         {
            return dependencyNotation
         }
         if (dependencyNotation.contains("version") && dependencyNotation["version"] is String)
         {
            dependencyMode = dependencyNotation["version"] as String
         }
         else
         {
            return dependencyNotation
         }

         val modifiedVersion = ihmcBuildExtension.getExternalDependencyVersion(groupId, artifactName, dependencyMode)

         val modifiedMap = hashMapOf<String, Any?>()

         for (entry in dependencyNotation)
         {
            modifiedMap[entry.key as String] = entry.value
         }
         modifiedMap["version"] = modifiedVersion

         return modifiedMap
      }
      else
      {
         return dependencyNotation
      }
   }
}