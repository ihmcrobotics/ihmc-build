package us.ihmc.build

import ca.cutterslade.gradle.analyze.AnalyzeDependenciesPlugin
import com.dorongold.gradle.tasktree.TaskTreePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.invoke
import us.ihmc.continuousIntegration.IHMCContinuousIntegrationGradlePlugin

class IHMCBuild : Plugin<Project>
{
   override fun apply(project: Project)
   {
      project.run {
         maybeApplyPlugin(JavaPlugin::class.java)
         maybeApplyPlugin(AnalyzeDependenciesPlugin::class.java)
         maybeApplyPlugin(TaskTreePlugin::class.java)
         maybeApplyPlugin(IHMCContinuousIntegrationGradlePlugin::class.java)
         
         configurations.getByName("testOutput").extendsFrom(configurations.getByName("testRuntime"))
         
         tasks {
            "checkIHMCBuild"(Task::class) {
               println("IHMC Build being applied")
            }
         }
      }

//      project.configure(project, groovyClosure
//      {
//         if (!project.plugins.hasPlugin("java"))
//         {
//            project.apply(mapOf("plugin" to "java"));
//         }
//
//         project.apply(mapOf("plugin" to "ca.cutterslade.analyze"));
//         project.apply(mapOf("plugin" to "com.dorongold.task-tree"));
//
//         IHMCContinuousIntegrationGradlePlugin().apply(project)
//
//         project.configurations.getByName("testOutput").extendsFrom(project.configurations.getByName("testRuntime"))
//
////         project.task("jarTest", mapOf("type" to "Jar", "dependsOn" to project.tasks.getByName("testClasses")), taskClosure {
////         return null;
////         })
//      })
      
   }
   
   private fun <K : Project, T : Plugin<K>> Project.maybeApplyPlugin(pluginClass: Class<T>)
   {
      if (!plugins.hasPlugin(pluginClass))
         plugins.apply(pluginClass)
   }

//   private fun <T : Plugin> Project.maybeApplyPlugin(pluginClass: Class<Plugin<T>>)
//   {
//      if (!plugins.hasPlugin(pluginClass))
//         plugins.apply(pluginClass)
//   }

//   fun <T> T.taskClosure(function: () -> Task) = object : Closure<Task>(this)
//   {
//      @Suppress("unused")
//      fun doCall()
//      {
//         function()
//      }
//   }
//
//   fun <T> T.groovyClosure(function: () -> Unit) = object : Closure<Unit>(this)
//   {
//      @Suppress("unused")
//      fun doCall()
//      {
//         function()
//      }
//   }
}