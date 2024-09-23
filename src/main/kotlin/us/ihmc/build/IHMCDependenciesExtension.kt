package us.ihmc.build

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.ExternalModuleDependencyVariantSpec
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.kotlin.dsl.extra

open class IHMCDependenciesExtension(private val mainProject: Project,
                                     private val sourceSetKebabCasedName: String,
                                     private val ihmcBuildExtension: IHMCBuildExtension)
   : MethodMixIn, // Groovy trick
     IHMCKotlinDependencyHandlerDelegate()  // Kotlin trick
{
   private val logger = mainProject.logger
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
   val dependencies: DependencyHandler = projectToConfigure.dependencies

   override val delegate: DependencyHandler get() = dependencies  // trick for Kotlin

   private val dynamicMethods = DynamicMethods()  // trick for Groovy

   override fun create(dependencyNotation: Any): Dependency
   {
      return delegate.create(modifyDependency(dependencyNotation))
   }

   override fun enforcedPlatform(dependencyProvider: Provider<MinimalExternalModuleDependency>): Provider<MinimalExternalModuleDependency>
   {
      TODO("Not yet implemented")
   }

   override fun <T : Any?, U : ExternalModuleDependency?> addProvider(configurationName: String,
                                                                      dependencyNotation: Provider<T>,
                                                                      configuration: Action<in U>)
   {
      TODO("Not yet implemented")
   }

   override fun <T : Any?> addProvider(configurationName: String, dependencyNotation: Provider<T>)
   {
      TODO("Not yet implemented")
   }

   override fun <T : Any?, U : ExternalModuleDependency?> addProviderConvertible(configurationName: String,
                                                                                 dependencyNotation: ProviderConvertible<T>,
                                                                                 configuration: Action<in U>)
   {
      TODO("Not yet implemented")
   }

   override fun <T : Any?> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T>)
   {
      TODO("Not yet implemented")
   }

   override fun variantOf(dependencyProvider: Provider<MinimalExternalModuleDependency>,
                          variantSpec: Action<in ExternalModuleDependencyVariantSpec>)
   : Provider<MinimalExternalModuleDependency>
   {
      TODO("Not yet implemented")
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

   private fun filterAndAddDependency(configurationName: String, dependencyNotation: Any, configureClosure: Closure<Any>): Dependency?
   {
      val modifiedDependencyNotation = processDependencyDeclaration(configurationName, dependencyNotation)
      return dependencies.add(configurationName, modifiedDependencyNotation, configureClosure)
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

   /// GROOVY DYNAMIC METHODS ///

   override fun getAdditionalMethods(): MethodAccess
   {
      return dynamicMethods
   }
   
   private inner class DynamicMethods : MethodAccess
   {
      override fun hasMethod(name: String, vararg arguments: Any): Boolean
      {
         return arguments.isNotEmpty() && projectToConfigure.configurations.findByName(name) != null
      }
      
      override fun tryInvokeMethod(name: String, vararg arguments: Any): DynamicInvokeResult
      {
         if (arguments.isEmpty())
         {
            return DynamicInvokeResult.notFound()
         }
         val configuration = projectToConfigure.configurations.findByName(name) ?: return DynamicInvokeResult.notFound()
         val normalizedArgs = listOf(*arguments)
         if (normalizedArgs.size == 2 && normalizedArgs[1] is Closure<*>)
         {
            return DynamicInvokeResult.found(filterAndAddDependency(configuration.name, normalizedArgs[0], normalizedArgs[1] as Closure<Any>))
         }
         else if (normalizedArgs.size == 1)
         {
            return DynamicInvokeResult.found(filterAndAddDependency(configuration.name, normalizedArgs[0]))
         }
         else
         {
            for (arg in normalizedArgs)
            {
               dependencies.add(configuration.name, arg) // we don't know how to filter, let Gradle handle
            }
            return DynamicInvokeResult.found()
         }
      }
   }
}