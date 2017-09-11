package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
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
               val sourceSetName = subproject.name.split("-").last()
               extensions.add(sourceSetName + "Dependencies", IHMCDependenciesExtension(project, sourceSetName, ihmcBuildExtension))
            }
            project.task("generateSettings", closureOf<Task> {
               doLast {
                  us.ihmc.build.writeProjectSettingsFile(logger, project.projectDir)
               }
            })
         }
      }
   }
   
   private fun <K : Project, T : Plugin<K>> Project.maybeApplyPlugin(pluginClass: Class<T>)
   {
      if (!plugins.hasPlugin(pluginClass))
         plugins.apply(pluginClass)
   }
   
   private fun configureProjectGroup(project: Project)
   {
      project.task("generateSettings", closureOf<Task> {
         doLast {
            writeGroupSettingsFile(logger, project.projectDir)
            
            for (childDir in project.projectDir.list())
            {
               if (File(project.projectDir, childDir + "/build.gradle").exists())
               {
                  writeProjectSettingsFile(logger, File(project.projectDir, childDir))
               }
            }
         }
      })
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