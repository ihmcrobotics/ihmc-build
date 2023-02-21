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
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

class IHMCBuildPlugin : Plugin<Project>
{
   override fun apply(project: Project)
   {
      LogTools = IHMCBuildLogTools(project.logger)

      if (project.hasProperty("isProjectGroup") &&
          IHMCBuildTools.isProjectGroupCompatibility(project.property("isProjectGroup") as String))
      {
         project.allprojects {
            pluginManager.apply(BasePlugin::class.java)
            pluginManager.apply(EclipsePlugin::class.java)
            pluginManager.apply(IdeaPlugin::class.java)
            pluginManager.apply(TaskTreePlugin::class.java)
            pluginManager.apply(HelpTasksPlugin::class.java)
         }
      }
      else
      {
         project.allprojects {
            pluginManager.apply(JavaLibraryPlugin::class.java)
            pluginManager.apply(IvyPublishPlugin::class.java)
            pluginManager.apply(MavenPublishPlugin::class.java)
            pluginManager.apply(AnalyzeDependenciesPlugin::class.java)
            pluginManager.apply(EclipsePlugin::class.java)
            pluginManager.apply(IdeaPlugin::class.java)
            pluginManager.apply(TaskTreePlugin::class.java)
            pluginManager.apply(HelpTasksPlugin::class.java)
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

      // There are 4 main things to do here:
      // - Enhance certain tasks (such as extending clean to clean IDE outputs)
      // - Add new tasks (like adding a task that cleans up buildship data)
      // - Combine tasks for convenience (like cleanIDE combines cleanIdea, cleanEclipse, and cleanBuildship)
      // - Ensure composite builds have the proper dependency structure. That the desired task is run though all
      //   the included build projects

      // setup task to clean buildship files
      for (allproject in project.allprojects)
      {
         allproject.tasks.tasks.create("cleanIdeaBuild") {
            delete(allproject.projectDir.resolve("out")) // IntelliJ default output dir
         }

         allproject.tasks.tasks.create("cleanEclipseBuild") {
            delete(allproject.projectDir.resolve("bin")) // Eclipse default output dir
         }

         // create a task to clean all IDE build files
         allproject.tasks.create("cleanIDEBuilds")
         {
            dependsOn(allproject.tasks.getByPath("cleanIdeaBuild"))
            dependsOn(allproject.tasks.getByPath("cleanEclipseBuild"))
         }

         // create a task to clean all build files
         allproject.tasks.create("cleanAllBuilds")
         {
            dependsOn(allproject.tasks.getByPath("clean"))
            dependsOn(allproject.tasks.getByPath("cleanIDEBuilds"))
         }

         // create a task to clean buildship files
         allproject.tasks.create("cleanBuildship", Delete::class.java)
         {
            delete(allproject.projectDir.resolve(".settings"))
         }

         // create a task to clean all IDE files and configuration (dangerous!)
         allproject.tasks.create("cleanIDEConfigurations")
         {
            dependsOn(allproject.tasks.getByPath("cleanIdea"))
            dependsOn(allproject.tasks.getByPath("cleanEclipse"))
            dependsOn(allproject.tasks.getByPath("cleanBuildship"))
         }
      }

      // setup graph dependencies task
      IHMCDependencyGraphviz(project)

      // composite tasks name composite* instead of *All because, while they would work for single multi project builds too,
      // the normal tasks also call the subproject ones
      IHMCBuildTools.defineDeepCompositeTask("compositeClean", "clean", project)
      IHMCBuildTools.defineDeepCompositeTask("compositeCleanIDEBuilds", "cleanIDEBuilds", project)
      IHMCBuildTools.defineDeepCompositeTask("compositeCleanIDEConfigurations", "cleanIDEConfigurations", project)
      IHMCBuildTools.defineDeepCompositeTask("compositeCompileJava", "compileJava", project)
      IHMCBuildTools.defineDeepCompositeTask("compositeJar", "jar", project)
      IHMCBuildTools.defineDeepCompositeTask("compositePublish", "publish", project)

      // IHMCBuildTools.defineExtraSourceSetCompositeTask("publishExtraSourceSets", arrayListOf("publishHandle"), project)
      // IHMCBuildTools.defineExtraSourceSetCompositeTask("publishExtraSourceSets", arrayListOf("publish"), project)

      IHMCBuildTools.defineDynamicCompositeTask(project)
   }
}