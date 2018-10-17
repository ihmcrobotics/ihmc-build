package us.ihmc.ci;

import org.codehaus.groovy.runtime.MethodClosure;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;

import java.nio.file.Path;

class IHMCCIPlugin : Plugin<Project>
{
   val categoriesExtension = CategoriesExtension()

   override fun apply(project: Project)
   {
      project.extensions.create("categories", CategoriesExtension::class.java, categoriesExtension)

      configureDefaultCategories()

      configureJUnitPlatform(project)
   }

   fun configureDefaultCategories()
   {
      categoriesExtension.create("fast") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 4
         excludeTags.add("all")
      }
      categoriesExtension.create("allocation") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags.add("allocation")
         jvmArgs += allocationAgentJVMArg
      }
   }

   private fun configureJUnitPlatform(project: Project)
   {
      try
      {
         val testProject = project.project(project.getName() + "-test") // assumes ihmc-build plugin
         val testExtension = testProject.getTasks().getByName("test") as Test

         testExtension.useJUnitPlatform { jUnitPlatformOptions ->
            project.getLogger().info("[ihmc-ci] Using JUnit platform")

            includeTags(project, jUnitPlatformOptions, "includeTags")
            includeTags(project, jUnitPlatformOptions, "includeTag")
            excludeTags(project, jUnitPlatformOptions, "excludeTags")
            excludeTags(project, jUnitPlatformOptions, "excludeTag")
         }
      }
      catch (e: UnknownDomainObjectException)
      {
         // do nothing
      }
   }

   private fun includeTags(project: Project, jUnitPlatformOptions: JUnitPlatformOptions, propertyName: String)
   {
      if (project.hasProperty(propertyName) && !project.property(propertyName)!!.equals("all"))
      {
         for (includeTag in (project.property(propertyName) as String).trim().toLowerCase().split(","))
         {
            project.getLogger().info("[ihmc-ci] Including tag: " + includeTag)
            jUnitPlatformOptions.includeTags(includeTag)
         }
      }
   }

   private fun excludeTags(project: Project, jUnitPlatformOptions: JUnitPlatformOptions, propertyName: String)
   {
      if (project.hasProperty(propertyName) && !project.property(propertyName)!!.equals("none"))
      {
         for (excludeTag in (project.property(propertyName) as String).trim().toLowerCase().split(","))
         {
            project.getLogger().info("[ihmc-ci] Excluding tag: " + excludeTag)
            jUnitPlatformOptions.excludeTags(excludeTag)
         }
      }
   }
}
