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
         project.task("generateSettings", closureOf<Task> {
            doLast {
               us.ihmc.build.writeGroupSettingsFile(project.projectDir)
   
               for (childDir in project.projectDir.list())
               {
                  if (File(project.projectDir, childDir + "/build.gradle").exists())
                  {
                     us.ihmc.build.writeProjectSettingsFile(File(project.projectDir, childDir))
                  }
               }
            }
         })
         project.task("deleteTests", closureOf<Task> {
            doLast {
               us.ihmc.build.writeGroupSettingsFile(project.projectDir)
         
               for (childDir in project.projectDir.list())
               {
                  if (File(project.projectDir, childDir + "/build.gradle").exists())
                  {
                     val childPath = File(project.projectDir, childDir).toPath()
                     us.ihmc.build.revertSourceFolderFromMavenStandard(childPath, "main")
                     us.ihmc.build.revertSourceFolderFromMavenStandard(childPath, "test")
                     us.ihmc.build.revertSourceFolderFromMavenStandard(childPath, "visualizers")
                  }
               }
            }
         })
         project.task("copyTests", closureOf<Task> {
            doLast {
               us.ihmc.build.writeGroupSettingsFile(project.projectDir)
         
               for (childDir in project.projectDir.list())
               {
                  if (File(project.projectDir, childDir + "/build.gradle").exists())
                  {
                     val childPath = File(project.projectDir, childDir).toPath()
                     us.ihmc.build.moveSourceFolderToMavenStandard(childPath, "main")
                     us.ihmc.build.moveSourceFolderToMavenStandard(childPath, "test")
                     us.ihmc.build.moveSourceFolderToMavenStandard(childPath, "visualizers")
                  }
               }
            }
         })
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
               maybeApplyPlugin(IHMCContinuousIntegrationGradlePlugin::class.java)
            }
            
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
                  us.ihmc.build.writeProjectSettingsFile(project.projectDir)
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
}