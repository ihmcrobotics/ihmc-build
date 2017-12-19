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
   override fun apply(project: Project)
   {
      if (project.hasProperty("isProjectGroup") && (project.property("isProjectGroup") as String).toBoolean())
      {
         configureProjectGroup(project)
         
         project.run {
            allprojects {
               maybeApplyPlugin(BasePlugin::class.java)
               maybeApplyPlugin(EclipsePlugin::class.java)
               maybeApplyPlugin(IdeaPlugin::class.java)
               maybeApplyPlugin(TaskTreePlugin::class.java)
            }
         }
      }
      else
      {
         project.run {
            allprojects {
               maybeApplyPlugin(JavaPlugin::class.java)
               maybeApplyPlugin(IvyPublishPlugin::class.java)
               maybeApplyPlugin(MavenPublishPlugin::class.java)
               maybeApplyPlugin(AnalyzeDependenciesPlugin::class.java)
               maybeApplyPlugin(EclipsePlugin::class.java)
               maybeApplyPlugin(IdeaPlugin::class.java)
               maybeApplyPlugin(TaskTreePlugin::class.java)
            }
            
            // Only apply to main
            maybeApplyPlugin(IHMCContinuousIntegrationGradlePlugin::class.java)
            
            val ihmcBuildExtension = IHMCBuildExtension(project)
            extensions.add("ihmc", ihmcBuildExtension)
            extensions.add("mainDependencies", IHMCDependenciesExtension(project, "main", ihmcBuildExtension))
            for (subproject in project.subprojects)
            {
               val sourceSetKebabCasedName = toSourceSetName(subproject)
               val sourceSetCamelCasedName = toCamelCased(sourceSetKebabCasedName)
               extensions.add(sourceSetCamelCasedName + "Dependencies", IHMCDependenciesExtension(project, sourceSetKebabCasedName, ihmcBuildExtension))
            }
         }
      }
      
      // setup clean to not only clean "build", but out and bin too
      for (allproject in project.allprojects)
      {
         allproject.tasks.getByName("clean", closureOf<Delete> {
            delete(allproject.projectDir.resolve("out"))
            delete(allproject.projectDir.resolve("bin"))
            delete(allproject.projectDir.resolve(".settings"))
         })
      }
      
      defineDeepCompositeTask("cleanAll", arrayListOf("clean", "cleanIdea", "cleanEclipse"), project)
      
      defineDynamicCompositeTask(project)
   }
   
   private fun defineDynamicCompositeTask(project: Project)
   {
      if (project.hasProperty("taskName") || project.hasProperty("taskNames"))
      {
         val taskNames = arrayListOf<String>()
         
         if (project.hasProperty("taskName"))
         {
            taskNames.addAll((project.property("taskName") as String).split(","))
         }
         
         if (project.hasProperty("taskNames"))
         {
            taskNames.addAll((project.property("taskNames") as String).split(","))
         }
         
         if (taskNames.size > 0)
         {
            defineDeepCompositeTask("compositeTask", taskNames, project)
         }
      }
   }
   
   /**
    * Create a task that will run a set of tasks if they exist in all subprojects, and all
    * projects in all included builds.
    */
   private fun defineDeepCompositeTask(globalTaskName: String, targetTaskNames: ArrayList<String>, project: Project)
   {
      // Configure every project to have at least an empty task with the names
      for (targetTaskName in targetTaskNames)
      {
         for (allproject in project.allprojects)
         {
            try
            {
               allproject.tasks.findByPath(targetTaskName)
               allproject.tasks.getByName(targetTaskName)
            }
            catch (e: NullPointerException)
            {
               configureEmptySubprojectTask(allproject, targetTaskName, project)
            }
            catch (e: UnknownTaskException)
            {
               configureEmptySubprojectTask(allproject, targetTaskName, project)
            }
         }
      }
      
      // Configure every project to have a global task that depends on all the task names
      project.task(globalTaskName, closureOf<Task> {
         for (targetTaskName in targetTaskNames)
         {
            for (allproject in project.allprojects)
            {
               dependsOn(allproject.tasks.getByName(targetTaskName))
            }
         }
      })
      
      // For the build root, make the global task depend on all the included build global tasks
      if (isBuildRoot(project))
      {
         project.tasks.getByName(globalTaskName, closureOf<Task> {
            for (includedBuild in project.gradle.includedBuilds)
            {
               dependsOn(includedBuild.task(":$globalTaskName"))
            }
         })
      }
   }
   
   private fun configureEmptySubprojectTask(subproject: Project, targetTaskName: String, project: Project)
   {
      try
      {
         subproject.task(targetTaskName, closureOf<Task> {
            // Declare empty task if it doesn't exist
            logInfo(project.logger, "${subproject.name}: Declaring empty task: $targetTaskName")
         })
      }
      catch (e: InvalidUserDataException)
      {
         // if the task exists or something else goes wrong
         logInfo(project.logger, "${subproject.name}: InvalidUserDataException: ${e.message}: $targetTaskName")
      }
   }
   
   private fun <K : Project, T : Plugin<K>> Project.maybeApplyPlugin(pluginClass: Class<T>)
   {
      if (!plugins.hasPlugin(pluginClass))
         plugins.apply(pluginClass)
   }
   
   private fun configureProjectGroup(project: Project)
   {
      project.task("convertStructure", closureOf<Task> {
         doLast {
            for (childDir in project.projectDir.list())
            {
               if (File(project.projectDir, childDir + "/build.gradle").exists())
               {
                  val childFile = File(project.projectDir, childDir)
                  val tempFile = File(project.projectDir, "TMP" + childDir)
                  val childPath = childFile.toPath()
                  moveSourceFolderToMavenStandard(project.logger, childPath, "main")
                  moveSourceFolderToMavenStandard(project.logger, childPath, "test")
                  moveSourceFolderToMavenStandard(project.logger, childPath, "visualizers")
               }
            }
         }
      })
      project.task("revertStructure", closureOf<Task> {
         doLast {
            for (childDir in project.projectDir.list())
            {
               if (File(project.projectDir, childDir + "/build.gradle").exists())
               {
                  val childFile = File(project.projectDir, childDir)
                  val tempFile = File(project.projectDir, "TMP" + childDir)
                  val childPath = childFile.toPath()
                  revertSourceFolderFromMavenStandard(project.logger, childPath, "main")
                  revertSourceFolderFromMavenStandard(project.logger, childPath, "test")
                  revertSourceFolderFromMavenStandard(project.logger, childPath, "visualizers")
               }
            }
         }
      })
   }
}