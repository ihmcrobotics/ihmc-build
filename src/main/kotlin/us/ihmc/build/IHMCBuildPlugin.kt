package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
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
      }
      else
      {
         project.run {
            allprojects {
               maybeApplyPlugin(JavaPlugin::class.java)
               maybeApplyPlugin(EclipsePlugin::class.java)
               maybeApplyPlugin(IdeaPlugin::class.java)
               maybeApplyPlugin(IvyPublishPlugin::class.java)
               maybeApplyPlugin(MavenPublishPlugin::class.java)
               maybeApplyPlugin(AnalyzeDependenciesPlugin::class.java)
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
   
      if (project.hasProperty("taskName"))
      {
         project.task("compositeTask", closureOf<Task> {
            val taskReferences = hashSetOf<TaskReference>()
            for (includedBuild in project.gradle.includedBuilds)
            {
               taskReferences.add(includedBuild.task(":" + project.property("taskName") as String))
            }
            dependsOn(taskReferences)
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