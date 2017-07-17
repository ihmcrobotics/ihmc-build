package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import us.ihmc.continuousIntegration.IHMCContinuousIntegrationGradlePlugin

class IHMCBuild : Plugin<Project>
{
   override fun apply(project: Project)
   {
      project.run {
         maybeApplyPlugin(JavaPlugin::class.java)
         maybeApplyPlugin(MavenPublishPlugin::class.java)
         maybeApplyPlugin(AnalyzeDependenciesPlugin::class.java)
         maybeApplyPlugin(TaskTreePlugin::class.java)
         maybeApplyPlugin(IHMCContinuousIntegrationGradlePlugin::class.java)
         
         extensions.create("ihmc", IHMCBuildExtension::class.java, project)
      }
   }
   
   private fun <K : Project, T : Plugin<K>> Project.maybeApplyPlugin(pluginClass: Class<T>)
   {
      if (!plugins.hasPlugin(pluginClass))
         plugins.apply(pluginClass)
   }
}