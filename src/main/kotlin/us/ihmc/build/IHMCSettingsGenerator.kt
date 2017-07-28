package us.ihmc.build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.delegateClosureOf
import us.ihmc.continuousIntegration.AgileTestingTools
import java.io.File
import java.nio.charset.Charset

class IHMCSettingsGenerator(project: Project)
{
   init
   {
      project.task("generateSettings").doLast {
         val settingsFile = project.rootDir.resolve("settings.gradle")
         
         println("Generating file: " + settingsFile.absolutePath)
         
         val buildsToInclude = sortedSetOf<String>()
         
         project.allprojects(closureOf<Project> {
            configurations.getByName("compile").allDependencies.forEach {
               if (it.group.startsWith("us.ihmc"))
               {
                  if (file("../" + it.name).exists())
                  {
                     buildsToInclude.add(it.name as String)
                  }
                  
                  val pascalCasedName = AgileTestingTools.hyphenatedToPascalCased(it.name)
                  if (file("../" + pascalCasedName).exists())
                  {
                     buildsToInclude.add(pascalCasedName)
                  }
               }
            }
         })
         
         println(buildsToInclude)
         
         var text = "rootProject.name = hyphenatedName" + "\n"
         text += "\n"
         text += "println \"Evaluating \" + pascalCasedName + \" settings.gradle\"" + "\n"
         text += "\n"
         text += "Eval.me(extraSourceSets).each {" + "\n"
         text += "   include it" + "\n"
         text += "   project(\":\" + it).name = hyphenatedName + \"-\" + it" + "\n"
         text += "}" + "\n"
         
         if (!buildsToInclude.isEmpty())
         {
            text += "\n"
            buildsToInclude.forEach {
               text += "includeBuild \"../$it\"\n"
            }
         }
         
         settingsFile.writeText(text)
      }
   }
}