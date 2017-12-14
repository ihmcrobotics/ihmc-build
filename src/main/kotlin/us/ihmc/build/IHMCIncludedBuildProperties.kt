package us.ihmc.build

import org.gradle.api.logging.Logger
import java.nio.file.Path

class IHMCIncludedBuildProperties(val logger: Logger)
{
   var excludeFromCompositeBuild = false
   lateinit var modules: Collection<IHMCModule>
   
   fun load(projectPath: Path): IHMCIncludedBuildProperties
   {
      val properties = IHMCPropertyInterface().initWithPath(projectPath.resolve("gradle.properties"), projectPath.fileName.toString())
   
      excludeFromCompositeBuild = excludeFromCompositeBuildCompatibility(properties)
      modules = modulesCompatibility(logger, properties)
   
      if (excludeFromCompositeBuild)
      {
         logInfo(logger, "Excluding " + projectPath.fileName.toString() + ". Property excludeFromCompositeBuild = $excludeFromCompositeBuild")
      }
      
      return this
   }
}