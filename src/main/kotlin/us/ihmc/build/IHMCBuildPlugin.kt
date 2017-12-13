package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.closureOf
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import us.ihmc.continuousIntegration.IHMCContinuousIntegrationGradlePlugin
import java.io.File

class IHMCBuildPlugin : Plugin<Project>
{
   override fun apply(mainModule: Project)
   {
      if (mainModule.hasProperty("isProjectGroup") && (mainModule.property("isProjectGroup") as String).toBoolean())
      {
         for (allModule in mainModule.allprojects)
         {
            maybeApplyPlugin(allModule, BasePlugin::class.java)
            maybeApplyPlugin(allModule, EclipsePlugin::class.java)
            maybeApplyPlugin(allModule, IdeaPlugin::class.java)
            maybeApplyPlugin(allModule, TaskTreePlugin::class.java)
         }
      }
      else
      {
         for (allModule in mainModule.allprojects)
         {
            maybeApplyPlugin(allModule, JavaPlugin::class.java)
            maybeApplyPlugin(allModule, IvyPublishPlugin::class.java)
            maybeApplyPlugin(allModule, MavenPublishPlugin::class.java)
            maybeApplyPlugin(allModule, AnalyzeDependenciesPlugin::class.java)
            maybeApplyPlugin(allModule, EclipsePlugin::class.java)
            maybeApplyPlugin(allModule, IdeaPlugin::class.java)
            maybeApplyPlugin(allModule, TaskTreePlugin::class.java)
         }
         
         // Only apply to main. This plugin assumes test is a source set, not a project
         maybeApplyPlugin(mainModule, IHMCContinuousIntegrationGradlePlugin::class.java)
         
         val ihmcBuildExtension = IHMCBuildExtension(mainModule)
         mainModule.extensions.add("ihmc", ihmcBuildExtension)
         mainModule.extensions.add("mainDependencies", IHMCDependenciesExtension(mainModule, "main", ihmcBuildExtension))
         for (subproject in mainModule.subprojects)
         {
            val sourceSetKebabCasedName = toSourceSetName(subproject)
            val sourceSetCamelCasedName = toCamelCased(sourceSetKebabCasedName)
            mainModule.extensions.add(sourceSetCamelCasedName + "Dependencies", IHMCDependenciesExtension(mainModule, sourceSetKebabCasedName, ihmcBuildExtension))
         }
      }
      
      // Setup clean to not only clean "build", but out and bin too
      for (allModule in mainModule.allprojects)
      {
         allModule.tasks.getByName("clean", closureOf<Delete> {
            delete(allModule.projectDir.resolve("out"))
            delete(allModule.projectDir.resolve("bin"))
            delete(allModule.projectDir.resolve(".settings"))
         })
      }
      
      defineDeepCompositeTask("cleanAll", arrayListOf("clean", "cleanIdea", "cleanEclipse"), mainModule)
      
      defineDynamicCompositeTask(mainModule)
   }
   
   private fun defineDynamicCompositeTask(module: Project)
   {
      if (module.hasProperty("taskName") || module.hasProperty("taskNames"))
      {
         val taskNames = arrayListOf<String>()
         
         if (module.hasProperty("taskName"))
         {
            taskNames.addAll((module.property("taskName") as String).split(","))
         }
         
         if (module.hasProperty("taskNames"))
         {
            taskNames.addAll((module.property("taskNames") as String).split(","))
         }
         
         if (taskNames.size > 0)
         {
            defineDeepCompositeTask("compositeTask", taskNames, module)
         }
      }
   }
   
   /**
    * Create a task that will run a set of tasks if they exist in all subprojects, and all
    * projects in all included builds.
    */
   private fun defineDeepCompositeTask(globalTaskName: String, targetTaskNames: ArrayList<String>, mainModule: Project)
   {
      // Configure every module to have at least an empty task with the names
      for (targetTaskName in targetTaskNames)
      {
         for (allModule in mainModule.allprojects)
         {
            try
            {
               allModule.tasks.findByPath(targetTaskName)
               allModule.tasks.getByName(targetTaskName)
            }
            catch (e: NullPointerException)
            {
               configureEmptyChildModuleTask(allModule, targetTaskName, mainModule)
            }
            catch (e: UnknownTaskException)
            {
               configureEmptyChildModuleTask(allModule, targetTaskName, mainModule)
            }
         }
      }
      
      // Configure every module to have a global task that depends on all the task names
      mainModule.task(globalTaskName, closureOf<Task> {
         for (targetTaskName in targetTaskNames)
         {
            for (allModule in mainModule.allprojects)
            {
               dependsOn(allModule.tasks.getByName(targetTaskName))
            }
         }
      })
      
      // For the build root, make the global task depend on all the included build global tasks
      if (isBuildRoot(mainModule))
      {
         mainModule.tasks.getByName(globalTaskName, closureOf<Task> {
            for (includedBuild in mainModule.gradle.includedBuilds)
            {
               dependsOn(includedBuild.task(":$globalTaskName"))
            }
         })
      }
   }
   
   private fun configureEmptyChildModuleTask(childModule: Project, targetTaskName: String, mainModule: Project)
   {
      try
      {
         childModule.task(targetTaskName, closureOf<Task> {
            // Declare empty task if it doesn't exist
            logInfo(mainModule.logger, "${childModule.name}: Declaring empty task: $targetTaskName")
         })
      }
      catch (e: InvalidUserDataException)
      {
         // if the task exists or something else goes wrong
         logInfo(mainModule.logger, "${childModule.name}: InvalidUserDataException: ${e.message}: $targetTaskName")
      }
   }
   
   private fun <K : Project, T : Plugin<K>> maybeApplyPlugin(project: Project, pluginClass: Class<T>)
   {
      if (!project.plugins.hasPlugin(pluginClass))
         project.plugins.apply(pluginClass)
   }
}