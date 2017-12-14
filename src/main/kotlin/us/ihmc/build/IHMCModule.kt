package us.ihmc.build

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.SourceSet
import java.io.File

class IHMCModule(logger: Logger, userGivenIdentifier: String, properties: IHMCPropertyInterface)
{
   // User
   val kebabCasedIdentifier: String
   val isMain: Boolean
   val kebabCasedPostfix: String
   val pascalCasedIdentifier: String
   val pascalCasedPostfix: String
   
   // Settings Evaluation Phase
   var isProjectGroup = false
   lateinit var kebabCasedName: String
   lateinit var pascalCasedName: String
   
   // Build Configuration Phase
   lateinit var project: Project
   lateinit var sourceSet: SourceSet
   
   init
   {
      kebabCasedIdentifier = toKebabCased(userGivenIdentifier)
      isMain = kebabCasedIdentifier == "main"
      kebabCasedPostfix = if (isMain) "" else "-$kebabCasedIdentifier"
      pascalCasedIdentifier = toPascalCased(kebabCasedIdentifier)
      pascalCasedPostfix = if (isMain) "" else "$pascalCasedIdentifier"
      
      isProjectGroup = isProjectGroupCompatibility(logger, properties)
      kebabCasedName = kebabCasedNameCompatibility(logger, properties) + kebabCasedPostfix
      pascalCasedName = pascalCasedNameCompatibility(logger, properties) + pascalCasedPostfix
   }
   
   fun evaluate(logger: Logger, settings: Settings, properties: IHMCPropertyInterface)
   {
      if (!isProjectGroup)
      {
         val moduleDirectory = File(settings.rootProject.projectDir, "src/$kebabCasedIdentifier")
         moduleDirectory.mkdir()
         File(moduleDirectory, "java").mkdir()
      }
      
      if (isMain)
      {
         settings.rootProject.name = kebabCasedName
      }
      else
      {
         settings.include(arrayOf("src/$kebabCasedIdentifier"))
         settings.project(":src/$kebabCasedIdentifier").name = kebabCasedName
      }
   }
   
   fun configure()
   {
   
   }
}