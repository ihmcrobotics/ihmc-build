package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.HelpTasksPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.closureOf
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import java.io.File

class IHMCBuildPlugin : Plugin<Project>
{
   override fun apply(project: Project)
   {
      LogTools = IHMCBuildLogTools(project.logger)

      if (project.hasProperty("isProjectGroup") &&
          IHMCBuildTools.isProjectGroupCompatibility(project.property("isProjectGroup") as String))
      {
         configureProjectGroup(project)

         project.allprojects {
            this.pluginManager.apply(BasePlugin::class.java)
            this.pluginManager.apply(EclipsePlugin::class.java)
            this.pluginManager.apply(IdeaPlugin::class.java)
            this.pluginManager.apply(TaskTreePlugin::class.java)
            this.pluginManager.apply(HelpTasksPlugin::class.java)
         }
      }
      else
      {
         project.allprojects {
            this.pluginManager.apply(JavaLibraryPlugin::class.java)
            this.pluginManager.apply(IvyPublishPlugin::class.java)
            this.pluginManager.apply(MavenPublishPlugin::class.java)
            this.pluginManager.apply(AnalyzeDependenciesPlugin::class.java)
            this.pluginManager.apply(EclipsePlugin::class.java)
            this.pluginManager.apply(IdeaPlugin::class.java)
            this.pluginManager.apply(TaskTreePlugin::class.java)
            this.pluginManager.apply(HelpTasksPlugin::class.java)
         }

         val ihmcBuildExtension = IHMCBuildExtension(project)
         project.extensions.add("ihmc", ihmcBuildExtension)
         project.extensions.add("mainDependencies", IHMCDependenciesExtension(project, "main", ihmcBuildExtension))
         for (subproject in project.subprojects)
         {
            val sourceSetKebabCasedName = IHMCBuildTools.toSourceSetName(subproject)
            val sourceSetCamelCasedName = IHMCBuildTools.kebabToCamelCase(sourceSetKebabCasedName)
            project.extensions.add(sourceSetCamelCasedName + "Dependencies", IHMCDependenciesExtension(project, sourceSetKebabCasedName, ihmcBuildExtension))
         }
      }

      // setup clean to not only clean "build", but out and bin too
      for (allproject in project.allprojects)
      {
         val clean = allproject.tasks.findByName("clean")
         clean?.configure(closureOf<Delete> {
            delete(allproject.projectDir.resolve("out"))
            delete(allproject.projectDir.resolve("bin"))
         })
      }
   
      // setup task to clean buildship files
      for (allproject in project.allprojects)
      {
         allproject.task(mapOf("type" to Delete::class.java), "cleanBuildship", closureOf<Delete> {
            delete(allproject.projectDir.resolve(".settings"))
         })
      }
      
      defineDeepCompositeTask("cleanBuild", arrayListOf("clean"), project)
      defineDeepCompositeTask("cleanIDE", arrayListOf("cleanIdea", "cleanEclipse", "cleanBuildship"), project)
      defineDeepCompositeTask("publishAll", arrayListOf("publish"), project)
//      defineExtraSourceSetCompositeTask("publishExtraSourceSets", arrayListOf("publishHandle"), project)
//      defineExtraSourceSetCompositeTask("publishExtraSourceSets", arrayListOf("publish"), project)
      
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
               configureEmptySubprojectTask(allproject, targetTaskName)
            }
            catch (e: UnknownTaskException)
            {
               configureEmptySubprojectTask(allproject, targetTaskName)
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
      if (IHMCBuildTools.isBuildRoot(project))
      {
         project.tasks.getByName(globalTaskName, closureOf<Task> {
            for (includedBuild in project.gradle.includedBuilds)
            {
               dependsOn(includedBuild.task(":$globalTaskName"))
            }
         })
      }
   }
   
   /**
    * Create a task that will run a set of tasks if they exist in all subprojects, and all
    * projects in all included builds.
    */
   private fun defineExtraSourceSetCompositeTask(globalTaskName: String, targetTaskNames: ArrayList<String>, project: Project)
   {
      // Configure every project to have at least an empty task with the names
      for (targetTaskName in targetTaskNames)
      {
         for (subproject in project.subprojects)
         {
            try
            {
               subproject.tasks.findByPath(targetTaskName)
               subproject.tasks.getByName(targetTaskName)
            }
            catch (e: NullPointerException)
            {
               configureEmptySubprojectTask(subproject, targetTaskName)
            }
            catch (e: UnknownTaskException)
            {
               configureEmptySubprojectTask(subproject, targetTaskName)
            }
         }
      }
      
      // Configure every project to have a global task that depends on all the task names
      project.task(globalTaskName, closureOf<Task> {
         for (targetTaskName in targetTaskNames)
         {
            for (subproject in project.subprojects)
            {
               dependsOn(subproject.tasks.getByName(targetTaskName))
            }
         }
      })
      
      // For the build root, make the global task depend on all the included build global tasks
      if (IHMCBuildTools.isBuildRoot(project))
      {
         project.tasks.getByName(globalTaskName, closureOf<Task> {
            for (includedBuild in project.gradle.includedBuilds)
            {
               dependsOn(includedBuild.task(":$globalTaskName"))
            }
         })
      }
   }
   
   private fun configureEmptySubprojectTask(subproject: Project, targetTaskName: String)
   {
      try
      {
         subproject.task(targetTaskName, closureOf<Task> {
            // Declare empty task if it doesn't exist
            LogTools.info("${subproject.name}: Declaring empty task: $targetTaskName")
         })
      }
      catch (e: InvalidUserDataException)
      {
         // if the task exists or something else goes wrong
         LogTools.info("${subproject.name}: InvalidUserDataException: ${e.message}: $targetTaskName")
      }
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
                  val childPath = childFile.toPath()
                  IHMCBuildTools.moveSourceFolderToMavenStandard(childPath, "main")
                  IHMCBuildTools.moveSourceFolderToMavenStandard(childPath, "test")
                  IHMCBuildTools.moveSourceFolderToMavenStandard(childPath, "visualizers")
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
                  val childPath = childFile.toPath()
                  IHMCBuildTools.revertSourceFolderFromMavenStandard(childPath, "main")
                  IHMCBuildTools.revertSourceFolderFromMavenStandard(childPath, "test")
                  IHMCBuildTools.revertSourceFolderFromMavenStandard(childPath, "visualizers")
               }
            }
         }
      })
   }
}