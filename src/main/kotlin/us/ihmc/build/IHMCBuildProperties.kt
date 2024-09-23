package us.ihmc.build

import groovy.util.Eval
import java.io.FileInputStream
import java.nio.file.Path
import java.util.*

class IHMCBuildProperties(val projectPath: Path) : Comparable<IHMCBuildProperties>
{
   var folderName = projectPath.fileName.toString()
   var kebabCasedName: String = ""
   var excludeFromCompositeBuild = false
   var isProjectGroup: Boolean = false
   val extraSourceSets = ArrayList<String>()
   val allArtifacts = ArrayList<String>()
   
   init
   {
      val properties = Properties()
      properties.load(FileInputStream(projectPath.resolve("gradle.properties").toFile()))
      for (propertyKey in properties.keys)
      {
         if (propertyKey == "excludeFromCompositeBuild")
         {
            excludeFromCompositeBuild = (properties[propertyKey]!! as String).toBoolean()
            if (excludeFromCompositeBuild)
            {
               LogTools.info("Excluding " + folderName + ". Property excludeFromCompositeBuild = " + properties[propertyKey])
            }
         }
         if (propertyKey == "isProjectGroup")
         {
            isProjectGroup = IHMCBuildTools.isProjectGroupCompatibility(properties[propertyKey]!! as String)
            if (isProjectGroup)
            {
               LogTools.info("Found group: $folderName (isProjectGroup = $isProjectGroup) $projectPath")
            }
         }
         if (propertyKey == "extraSourceSets")
         {
            // TODO: cleanup
            extraSourceSets.addAll(Eval.me(properties[propertyKey]!! as String) as ArrayList<String>)
         }
      }
   
      kebabCasedName = kebabCasedNameCompatibilityDuplicate(folderName, properties)

      for (i in 0 until extraSourceSets.size)
      {
         extraSourceSets[i] = IHMCBuildTools.toKebabCased(extraSourceSets[i])
      }
      
      allArtifacts.add(kebabCasedName)
      for (extraSourceSet in extraSourceSets)
      {
         allArtifacts.add("$kebabCasedName-$extraSourceSet")
      }
   }
   
   private fun kebabCasedNameCompatibilityDuplicate(projectName: String, properties: Properties): String
   {
      return if (properties.containsKey("kebabCasedName") && !(properties["kebabCasedName"] as String).startsWith("$"))
      {
         properties["kebabCasedName"] as String
      } else if (properties.containsKey("title") && !(properties["title"] as String).startsWith("$"))
      {
         IHMCBuildTools.titleToKebabCase(properties["title"] as String)
      } else
      {
         val defaultValue = IHMCBuildTools.toKebabCased(projectName)
         LogTools.info("No value found for kebabCasedName. Using default value: $defaultValue")
         properties["kebabCasedName"] = defaultValue
         defaultValue
      }
   }
   
   override fun compareTo(other: IHMCBuildProperties): Int
   {
      return projectPath.toString().compareTo(other.projectPath.toString())
   }
}