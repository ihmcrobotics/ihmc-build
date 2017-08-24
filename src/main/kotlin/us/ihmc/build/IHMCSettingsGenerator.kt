package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.closureOf
import java.io.File

class IHMCSettingsGenerator(project: Project)
{
   init
   {
      val generateSettingsTask = project.task("generateSettings", closureOf<Task> {
         doLast {
            writeSettingsFileToProject(project.projectDir)
         }
      })
      
      if (project.hasProperty("disableSettingsGeneration") && project.property("disableSettingsGeneration") as String == "true")
      {
         println("[ihmc-build] [WARN] IHMCSettingsGenerator: Disabling settings.gradle auto-generation!")
      }
      else
      {
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
}