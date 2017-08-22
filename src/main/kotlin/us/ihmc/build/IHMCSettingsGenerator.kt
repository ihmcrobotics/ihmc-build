package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.closureOf
import java.io.File

class IHMCSettingsGenerator(project: Project)
{
   init
   {
      if (project.hasProperty("disableSettingsGeneration") && project.property("disableSettingsGeneration") as String == "true")
      {
         println("[ihmc-build] [WARN] IHMCSettingsGenerator: Disabling settings.gradle generation!")
      }
      else
      {
         val generateSettingsTask = project.task("generateSettings", closureOf<Task> {
            doLast {
               writeSettingsFileToProject(project.projectDir)
            }
         })
         
         project.getTasksByName("compileJava", false).forEach {
            it.dependsOn(generateSettingsTask)
         }
      }
   }
   
   fun writeSettingsFileToProject(projectDir: File)
   {
      val settingsFile = projectDir.resolve("settings.gradle")
   
      println("[ihmc-build] Generating file: " + settingsFile.absolutePath)
   
      val fileContent = IHMCSettingsGenerator::class.java.getResource("/settings.gradle").readText()
      settingsFile.writeText(fileContent)
   }
   
   companion object {
      @JvmStatic
      fun testSettingsGenerationFromClasspath()
      {
         println("What a wonderful world")
      }
   }
}