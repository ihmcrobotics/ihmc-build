package us.ihmc.build

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.util.CollectionUtils

open class IHMCDependenciesExtension(private val rootProject: Project, private val name: String, private val ihmcBuildExtension: IHMCBuildExtension) : MethodMixIn
{
   private val logger = rootProject.logger
   private val hyphenatedName: String = rootProject.property("hyphenatedName") as String
   private val projectToConfigure by lazy {
      if (name == "main")
      {
         rootProject
      }
      else
      {
         rootProject.project(":$hyphenatedName-$name")
      }
   }
   private val dynamicMethods = DynamicMethods()
   
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
         
         val modifiedVersion = ihmcBuildExtension.getExternalDependencyVersion(split[0], split[1], split[2])
         
         var modifiedString = ""
         for (i in split.indices)
         {
            if (i == 2)
            {
               modifiedString += modifiedVersion
            }
            else
            {
               modifiedString += split[i]
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
         
         val modifiedVersion = ihmcBuildExtension.getExternalDependencyVersion(groupId, artifactName, dependencyMode)
         
         var modifiedMap = hashMapOf<String, Any?>()
         
         for (entry in dependencyNotation)
         {
            modifiedMap.put(entry.key as String, entry.value)
         }
         modifiedMap.put("version", modifiedVersion)
         
         return modifiedMap
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