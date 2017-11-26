package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskReference
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
         })
      }
   
      defineGlobalTask("cleanAll", arrayListOf("clean", "cleanIdea", "cleanEclipse"), project)
      
      setupCompositeTasks(project)
   }
   
   private fun setupCompositeTasks(project: Project)
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
            defineGlobalTask("compositeTask", taskNames, project)
         }
      }
   }
   
   /**
    * Create a task that will run a set of tasks if they exist in all subprojects, and all
    * projects in all included builds.
    */
   private fun defineGlobalTask(globalTaskName: String, taskNames: ArrayList<String>, project: Project)
   {
      // Configure every project to have at least an empty task with the names
      for (taskName in taskNames)
      {
         try
         {
            project.tasks.findByPath(taskName)
         }
         catch (e: NullPointerException)
         {
            try
            {
               for (allproject in project.allprojects)
               {
                  allproject.task(taskName, closureOf<Task> {
                     // Declare empty task if it doesn't exist
                     logInfo(project.logger, "${allproject.name}: Declaring empty task: $taskName")
                  })
               }
            }
            catch (e: InvalidUserDataException)
            {
               // do nothing
            }
         }
         
         // Make composite task references depend on their subproject tasks
         val subprojectTasks = hashSetOf<Task>()
         for (subproject in project.subprojects)
         {
            subprojectTasks.add(subproject.tasks.getByName(taskName))
         }
         project.tasks.getByName(taskName).dependsOn(subprojectTasks)
      }
      
      // Declare the global task in the build root (where the user is calling this from)
      if (isBuildRoot(project))
      {
         project.task(globalTaskName, closureOf<Task> {
            val taskReferences = hashSetOf<TaskReference>()
            for (includedBuild in project.gradle.includedBuilds)
            {
               for (taskName in taskNames)
               {
                  taskReferences.add(includedBuild.task(":$taskName"))
               }
            }
            dependsOn(taskReferences)
            
            // don't forget to include this projects tasks!
            for (taskName in taskNames)
            {
               dependsOn(project.tasks.getByName(taskName))
            }
         })
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