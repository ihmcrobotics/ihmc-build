package us.ihmc.build

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.gradle.api.GradleScriptException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class IHMCCompositeBuildAssembler(val configurator: IHMCSettingsConfigurator)
{
   val logger = configurator.logger
   val kebabCasedName = configurator.settings.rootProject.name
   val compositeSearchHeight = configurator.depthFromWorkspaceDirectory
   val buildRootPath: Path = configurator.settings.rootProject.projectDir.toPath()
   var compositeSearchPath: Path
   private val kebabCasedNameToPropertiesMap = HashMap<String, IHMCBuildProperties>()
   private val pathToPropertiesMap = HashMap<Path, IHMCBuildProperties>()
   val transitiveIncludedBuilds = TreeSet<IHMCBuildProperties>()
   
   init
   {
      compositeSearchPath = buildRootPath
      for (i in 1..compositeSearchHeight)
      {
         compositeSearchPath = compositeSearchPath.resolve("..")
      }
      compositeSearchPath = compositeSearchPath.toRealPath()
      logInfo(logger, "Repository group path: " + compositeSearchPath)
   }
   
   /**
    * Returns a list of all the paths to include as strings.
    */
   fun findCompositeBuilds(): List<String>
   {
      mapAllCompatiblePaths(compositeSearchPath)
      
      findTransitivesRecursive(kebabCasedName)
      
      val buildsToInclude = ArrayList<String>()
      // Sorted with repository name included
      for (transitiveBuild in transitiveIncludedBuilds)
      {
         val relativizedPathName: String = buildRootPath.relativize(transitiveBuild.projectPath).toString()
         if (!relativizedPathName.isEmpty()) // Including itself
         {
            buildsToInclude.add(relativizedPathName)
         }
      }
      
      for (buildToInclude in buildsToInclude)
      {
         logQuiet(logger, "Including build: " + buildToInclude)
      }
      
      return buildsToInclude
   }
   
   private fun findTransitivesRecursive(kebabCasedName: String)
   {
      for (kebabCasedDependency in findDirectKebabCasedDependencies(kebabCasedName))
      {
         for (newMatchingBuild in findNewMatchingBuilds(kebabCasedDependency))
         {
            logInfo(logger, "Adding dependency: " + newMatchingBuild.projectPath)
            findTransitivesRecursive(newMatchingBuild.kebabCasedName)
         }
      }
   }
   
   private fun findDirectKebabCasedDependencies(kebabCasedName: String): List<String>
   {
      val dependencies = arrayListOf<String>()
      val properties = propertiesFromKebabCasedName(kebabCasedName)
      if (properties.isProjectGroup)
      {
         val projectFile = properties.projectPath.toFile()
         for (childDir in projectFile.listFiles(File::isDirectory))
         {
            val childPath = childDir.toPath()
            if (pathToPropertiesMap.containsKey(childPath))
            {
               dependencies.add(propertiesFromPath(childPath).kebabCasedName)
            }
         }
      }
      else
      {
         for (declaredDependency in parseDependenciesFromGradleFile(properties.projectPath.resolve("build.gradle")))
         {
            if (kebabCasedNameToPropertiesMap.containsKey(declaredDependency))
            {
               dependencies.add(declaredDependency)
            }
         }
      }
      
      return dependencies
   }
   
   private fun findNewMatchingBuilds(kebabCasedDependency: String): List<IHMCBuildProperties>
   {
      val matched = ArrayList<IHMCBuildProperties>()
      for (includedBuildProperties in kebabCasedNameToPropertiesMap.values)
      {
         // Since this method is gathering more build folder names, make sure this folder isn't already in the set.
         // If it is, you save some computation on name matching.
         // Make sure the names match up. See {@link #matchNames}
         if (!transitiveIncludedBuilds.contains(includedBuildProperties) && matchNames(includedBuildProperties.kebabCasedName, kebabCasedDependency))
         {
            logInfo(logger, "Matched: " + kebabCasedDependency + " to " + includedBuildProperties.kebabCasedName)
            transitiveIncludedBuilds.add(includedBuildProperties)
            matched.add(includedBuildProperties)
         }
      }
      
      return matched
   }
   
   private fun mapAllCompatiblePaths(directory: Path)
   {
      if (isPathCompatibleWithBuildConfiguration(directory))
      {
         // Load the properties, even for the root
         val includedBuildProperties = IHMCBuildProperties(logger, directory)
         
         // Always include the build root, because observe external exclude preferences
         if (includedBuildProperties.kebabCasedName == kebabCasedName || !includedBuildProperties.excludeFromCompositeBuild)
         {
            kebabCasedNameToPropertiesMap.put(includedBuildProperties.kebabCasedName, includedBuildProperties)
            pathToPropertiesMap.put(directory, includedBuildProperties)
            
            logInfo(logger, "Found: " + includedBuildProperties.kebabCasedName + ": " + directory)
         }
      }
      
      for (subdirectory in directory.toFile().listFiles(File::isDirectory))
      {
         mapAllCompatiblePaths(subdirectory.toPath())
      }
   }
   
   /** Here, we could make the project more friendly by not having such harsh requirements. */
   private fun isPathCompatibleWithBuildConfiguration(subdirectory: Path): Boolean
   {
      return (Files.isDirectory(subdirectory)
            && subdirectory.fileName.toString() != "bin" // Address Eclipse bug where it copies build files to bin directory
            && subdirectory.fileName.toString() != "out" // Address the same hypothetical situation in IntelliJ
            && (Files.exists(subdirectory.resolve("build.gradle")) || Files.exists(subdirectory.resolve("build.gradle.kts")))
            && Files.exists(subdirectory.resolve("gradle.properties"))
            && Files.exists(subdirectory.resolve("settings.gradle")))
   }
   
   private fun matchNames(buildFolderNameToCheck: String, dependencyNameAsDeclared: String): Boolean
   {
      if (dependencyNameAsDeclared == buildFolderNameToCheck) return true
      
      val buildToCheckProperties = propertiesFromKebabCasedName(buildFolderNameToCheck)

      if (dependencyNameAsDeclared == buildToCheckProperties.kebabCasedName) return true
      
      for (extraSourceSet in buildToCheckProperties.extraSourceSets)
      {
         if (dependencyNameAsDeclared == buildToCheckProperties.kebabCasedName + "-" + extraSourceSet) return true
      }
      
      return false
   }
   
   private fun parseDependenciesFromGradleFile(buildFile: Path): SortedSet<String>
   {
      val dependencySet = TreeSet<String>()
      try
      {
         val builder = AstBuilder()
         val bytesInFile = String(Files.readAllBytes(buildFile))
         logInfo(logger, "Parsing for dependencies: " + buildFile)
         val nodes: List<ASTNode> = builder.buildFromString(bytesInFile)
         val dependencies = ArrayList<Array<String>>()
         val visitor = ExternalGradleFileCodeVisitor(dependencies, logger)
         for (node in nodes)
         {
            node.visit(visitor)
         }
         
         for (dependency in dependencies)
         {
            logDebug(logger, "Found declared dependency: " + dependency[1])
            dependencySet.add(dependency[1])
         }
      }
      catch (e: NoSuchFileException)
      {
         logInfo(logger, "Build not found on disk: " + e.message)
      }
      catch (e: GradleScriptException)
      {
         logWarn(logger, "Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: MultipleCompilationErrorsException)
      {
         logWarn(logger, "Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: IOException)
      {
         logTrace(logger, e.stackTrace)
      }
      return dependencySet
   }
   
   class ExternalGradleFileCodeVisitor(val dependencies: ArrayList<Array<String>>, val logger: Logger) : CodeVisitorSupport()
   {
      override fun visitArgumentlistExpression(ale: ArgumentListExpression)
      {
         val expressions: List<Expression> = ale.getExpressions()
         
         if (expressions.size == 1 && expressions.get(0) is ConstantExpression)
         {
            val dependencyString = expressions.get(0).getText()
            if (dependencyString.contains(":"))
            {
               val split = dependencyString.split(":")
               
               if (split.size >= 3)
               {
                  dependencies.add(arrayOf(split[0], split[1], split[2]))
               }
            }
         }
         
         super.visitArgumentlistExpression(ale)
      }
      
      override fun visitMapExpression(expression: MapExpression)
      {
         logDebug(logger, "Found map entry: " + expression.getText())
         val mapEntryExpressions: List<MapEntryExpression> = expression.getMapEntryExpressions()
         if (mapEntryExpressions.size >= 3)
         {
            val dependencyMap = HashMap<String, String>()
            
            for (mapEntryExpression in mapEntryExpressions)
            {
               val key = mapEntryExpression.getKeyExpression().getText()
               val value = mapEntryExpression.getValueExpression().getText()
               dependencyMap.put(key, value)
            }
            
            if (dependencyMap.containsKey("group") && dependencyMap.containsKey("name") && dependencyMap.containsKey("version"))
            {
               dependencies.add(arrayOf(dependencyMap["group"]!!, dependencyMap["name"]!!, dependencyMap["version"]!!))
            }
         }
         
         super.visitMapExpression(expression)
      }
   }
   
   private fun propertiesFromKebabCasedName(kebabCasedName: String): IHMCBuildProperties
   {
      return kebabCasedNameToPropertiesMap.get(kebabCasedName)!!
   }
   
   private fun propertiesFromPath(path: Path): IHMCBuildProperties
   {
      return pathToPropertiesMap.get(path)!!
   }
}