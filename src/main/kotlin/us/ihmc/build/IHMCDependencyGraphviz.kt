package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention

/**
 * TODO: Support graphing sources and jars
 */
class IHMCDependencyGraphviz(val project: Project)
{
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

            for (sourceSet in project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets)
            {
               for (fileSystemLocation in sourceSet.runtimeClasspath.elements.get())
               {
                  LogTools.warn("Dependency " + fileSystemLocation.toString())
               }
            }

//            for (allDependency in project.configurations.getByName().allDependencies)
//            {
//               LogTools.warn("Dependency " + allDependency.name)
//            }
         }
      }
   }
}