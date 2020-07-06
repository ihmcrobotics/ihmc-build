package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType

/**
 * TODO: Support graphing sources and jars
 */
class IHMCDependencyGraphviz(val project: Project)
{
   data class IHMCDependency(val group: String, val name: String, val version: String)
   val dependencies = sortedSetOf<String>()

   init
   {
      project.tasks.create("graphDependencies")
      {
         doLast {
            for (includedBuild in project.gradle.includedBuilds)
            {
               LogTools.warn("Included build: " + includedBuild.name)
            }

            for (configurationName in project.configurations.names)
            {
               LogTools.warn("configurationName: " + configurationName)
            }

//            for (configuration in project.configurations)
//            {
//                  LogTools.warn("Configuration " + configuration.name)
//
//            }

            for (firstLevelModuleDependency in project.configurations["runtimeClasspath"].resolvedConfiguration.firstLevelModuleDependencies)
            {
               recursiveHandleDependency(firstLevelModuleDependency)

            }

//            LogTools.warn("Class: " + project.configurations["runtimeClasspath"]::class.supertypes)
//            for (dependency in project.configurations["runtimeClasspath"].allDependencies)
//            {
//               LogTools.warn("Dependency " + dependency)
//            }
//            for (file in project.configurations["runtimeClasspath"].resolve())
//            {
//               LogTools.warn("Resolved " + file)
//
//            }
//            for (artifact in project.configurations["runtimeClasspath"].outgoing.artifacts)
//            {
//               LogTools.warn("Outgoing artifact " + artifact)
//            }

//            for (sourceSet in project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets)
//            {
//               for (fileSystemLocation in sourceSet.runtimeClasspath.elements.get())
//               {
//                  LogTools.warn("Dependency " + fileSystemLocation.toString())
//               }
//            }

//            for (allDependency in project.configurations.getByName().allDependencies)
//            {
//               LogTools.warn("Dependency " + allDependency.name)
//            }
         }
      }
   }

   private fun recursiveHandleDependency(firstLevelModuleDependency: ResolvedDependency)
   {
      LogTools.warn("First level module dependency: " + firstLevelModuleDependency)
      firstLevelModuleDependency.run {
//         dependencies.add(IHMCDependency(moduleGroup, moduleName, moduleVersion))
         dependencies.add("$moduleGroup:$moduleName:$moduleVersion")
      }

      for (child in firstLevelModuleDependency.children)
      {
//         if (!dependencies.contains(IHMCDependency(child.moduleGroup, child.moduleName, child.moduleVersion)))
         if (!dependencies.contains("${child.moduleGroup}:${child.moduleName}:${child.moduleVersion}"))
         {
            LogTools.warn("Child: " + child)
            recursiveHandleDependency(child)
         }
      }
   }
}