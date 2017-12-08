package us.ihmc.build

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.kotlin.dsl.extra
import org.gradle.util.CollectionUtils

open class IHMCDependenciesExtension(private val rootProject: Project, private val name: String, private val ihmcBuildExtension: IHMCBuildExtension) : MethodMixIn
{
   private val logger = rootProject.logger
   private val kebabCasedName: String = kebabCasedNameCompatibility(rootProject.name, logger, rootProject.extra)
   private val projectToConfigure by lazy {
      if (name == "main")
      {
         rootProject
      }
      else
      {
         rootProject.project(":$kebabCasedName-$name")
      }
   }
   private val dynamicMethods = DynamicMethods()
   
   /**
    * Public API supporting:
    *
    * compile module("main")
    * compile module("test")
    * compile module("visualizers")
    */
   fun module(moduleName: String): Project
   {
      return ihmcBuildExtension.sourceSetProject(moduleName)
   }
   
   private fun add(configurationName: String, dependencyNotation: Any)
   {
      val modifiedDependencyNotation = processDependencyDeclaration(configurationName, dependencyNotation)
      projectToConfigure.dependencies.add(configurationName, modifiedDependencyNotation)
   }
   
   private fun add(configurationName: String, dependencyNotation: Any, configureClosure: Closure<Any>)
   {
      val modifiedDependencyNotation = processDependencyDeclaration(configurationName, dependencyNotation)
      projectToConfigure.dependencies.add(configurationName, modifiedDependencyNotation, configureClosure)
   }
   
   private fun processDependencyDeclaration(configurationName: String, dependencyNotation: Any): Any
   {
      val modifiedDependencyNotation = modifyDependency(dependencyNotation)
   
      logDebug(logger, "Adding dependency to " + projectToConfigure.name + ": $modifiedDependencyNotation")
      
      if (configurationName != "compile")
      {
         logDebug(logger, " Unusual dependency on configuration: " + configurationName + ": " + dependencyNotation)
      }
      
      return modifiedDependencyNotation
   }
   
   private fun modifyDependency(dependencyNotation: Any): Any
   {
      if (dependencyNotation is String)
      {
         val split = dependencyNotation.split(":")
         
         val filteredVersion = ihmcBuildExtension.versionFilter.filterVersion(split[0], split[1], split[2])
         
         var filteredString = ""
         for (i in split.indices)
         {
            if (i == 2)
            {
               filteredString += filteredVersion
            }
            else
            {
               filteredString += split[i]
            }
            
            if (i < split.size - 1)
            {
               filteredString += ":"
            }
         }
         
         return filteredString
      }
      else if (dependencyNotation is Map<*, *>)
      {
         val groupId: String
         val artifactName: String
         val dependencyMode: String
         
         if (dependencyNotation.contains("group") && dependencyNotation.get("group") is String)
         {
            groupId = dependencyNotation.get("group") as String
         }
         else
         {
            return dependencyNotation
         }
         if (dependencyNotation.contains("name") && dependencyNotation.get("name") is String)
         {
            artifactName = dependencyNotation.get("name") as String
         }
         else
         {
            return dependencyNotation
         }
         if (dependencyNotation.contains("version") && dependencyNotation.get("version") is String)
         {
            dependencyMode = dependencyNotation.get("version") as String
         }
         else
         {
            return dependencyNotation
         }
         
         val filteredVersion = ihmcBuildExtension.versionFilter.filterVersion(groupId, artifactName, dependencyMode)
         
         var filteredMap = hashMapOf<String, Any?>()
         
         for (entry in dependencyNotation)
         {
            filteredMap.put(entry.key as String, entry.value)
         }
         filteredMap.put("version", filteredVersion)
         
         return filteredMap
      }
      else
      {
         return dependencyNotation
      }
   }
   
   override fun getAdditionalMethods(): MethodAccess
   {
      return dynamicMethods
   }
   
   private inner class DynamicMethods : MethodAccess
   {
      override fun hasMethod(name: String, vararg arguments: Any): Boolean
      {
         return arguments.size != 0 && projectToConfigure.configurations.findByName(name) != null
      }
      
      override fun tryInvokeMethod(name: String, vararg arguments: Any): DynamicInvokeResult
      {
         if (arguments.size == 0)
         {
            return DynamicInvokeResult.notFound()
         }
         val configuration = projectToConfigure.configurations.findByName(name) ?: return DynamicInvokeResult.notFound()
         val normalizedArgs = CollectionUtils.flattenCollections(*arguments)
         if (normalizedArgs.size == 2 && normalizedArgs[1] is Closure<*>)
         {
            return DynamicInvokeResult.found(add(configuration.name, normalizedArgs[0] as Any, normalizedArgs[1] as Closure<Any>))
         }
         else if (normalizedArgs.size == 1)
         {
            return DynamicInvokeResult.found(add(configuration.name, normalizedArgs[0] as Any))
         }
         else
         {
            for (arg in normalizedArgs)
            {
               add(configuration.name, arg as Any)
            }
            return DynamicInvokeResult.found()
         }
      }
   }
}