package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin
import org.gradle.kotlin.dsl.provider.KotlinScriptBasePlugin
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import us.ihmc.continuousIntegration.IHMCContinuousIntegrationGradlePlugin

class IHMCBuild : Plugin<Project>
{
   override fun apply(project: Project)
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
         
         extensions.add("ihmc", IHMCBuildExtension(project))
         extensions.add("testDependencies", IHMCTestProjectExtension(project))
         extensions.add("extraDependencies", IHMCExtraProjectExtension(project))
      }
   }
   
   private fun <K : Project, T : Plugin<K>> Project.maybeApplyPlugin(pluginClass: Class<T>)
   {
      if (!plugins.hasPlugin(pluginClass))
         plugins.apply(pluginClass)
   }
}