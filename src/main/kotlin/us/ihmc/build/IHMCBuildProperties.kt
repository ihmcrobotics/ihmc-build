package us.ihmc.build

import groovy.util.Eval
import org.gradle.api.logging.Logger
import java.io.FileInputStream
import java.nio.file.Path
import java.util.*

class IHMCBuildProperties(val logger: Logger)
{
   var hyphenatedName: String = ""
   var pascalCasedName: String = ""
   var exclude = false
   var isProjectGroup: Boolean = false
   val extraSourceSets = ArrayList<String>()
   
   fun load(projectPath: Path): IHMCBuildProperties
   {
      val properties = Properties()
      properties.load(FileInputStream(projectPath.resolve("gradle.properties").toFile()))
      for (propertyKey in properties.keys)
      {
         if (propertyKey == "excludeFromCompositeBuild")
         {
            exclude = (properties.get(propertyKey)!! as String).toBoolean()
            if (exclude)
            {
               logInfo(logger, "Excluding " + projectPath.fileName.toString() + ". Property excludeFromCompositeBuild = " + properties.get(propertyKey))
            }
         }
         if (propertyKey == "isProjectGroup")
         {
            isProjectGroup = (properties.get(propertyKey)!! as String).toBoolean()
            if (isProjectGroup)
            {
               logInfo(logger, "Including project group: " + projectPath.fileName.toString() + ". Property isProjectGroup = " + properties.get(propertyKey))
            }
         }
         if (propertyKey == "pascalCasedName")
         {
            pascalCasedName = properties.get(propertyKey)!! as String
         }
         if (propertyKey == "hyphenatedName")
         {
            hyphenatedName = properties.get(propertyKey)!! as String
         }
         if (propertyKey == "extraSourceSets")
         {
            extraSourceSets.addAll(Eval.me(properties.get(propertyKey)!! as String) as ArrayList<String>)
         }
      }
      
      if (pascalCasedName == null)
      {
         pascalCasedName = toPascalCased(projectPath.fileName.toString())
      }
      if (hyphenatedName == null)
      {
         hyphenatedName = toHyphenated(projectPath.fileName.toString())
      }
      
      return this
   }
}