package us.ihmc.ci;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

class IHMCCIPlugin : Plugin<Project>
{
   lateinit var project: Project
   var cpuThreads = 8
   lateinit var category: String
   lateinit var categoriesExtension: CategoriesExtension

   override fun apply(project: Project)
   {
      this.project = project

      loadProperties()
      project.logger.info("[ihmc-ci] cpuThreads = $cpuThreads")

      categoriesExtension = CategoriesExtension(project)
      project.extensions.create("categories", CategoriesExtension::class.java, categoriesExtension)
      configureDefaultCategories()

//      configureJUnitPlatform(project)
   }

   fun loadProperties()
   {
      project.properties["cpuThreads"].run { if (this != null) cpuThreads = (this as String).toInt()}
      project.properties["category"].run { if (this != null) category = (this as String).trim().toLowerCase()}
   }

   fun configureDefaultCategories()
   {
      categoriesExtension.create("fast") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 4
         excludeTags += "all"
      }
      categoriesExtension.create("allocation") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags += "allocation"
         jvmArgs += getAllocationAgentJVMArg()
      }
      categoriesExtension.create("scs") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags += "scs"
         jvmArgs += getScsDefaultJVMArgs()
      }
      categoriesExtension.create("video") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags += "video"
         jvmArgs += "-Dcreate.scs.gui=true"
         jvmArgs += "-Dshow.scs.windows=true"
         jvmArgs += "-Dcreate.videos.dir=/home/shadylady/bamboo-videos/"
         jvmArgs += "-Dshow.scs.yographics=true"
         jvmArgs += "-Djava.awt.headless=false"
         jvmArgs += "-Dcreate.videos=true"
         jvmArgs += "-Dopenh264.license=accept"
         jvmArgs += "-Ddisable.joint.subsystem.publisher=true"
         jvmArgs += "-Dscs.dataBuffer.size=8142"
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
