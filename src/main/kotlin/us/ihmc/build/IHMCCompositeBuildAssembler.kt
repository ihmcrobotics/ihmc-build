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
   val depthFromRepositoryGroup = configurator.depthFromWorkspaceDirectory
   val includeBuildsFromWorkspace = configurator.includeBuildsFromWorkspace
   val rootProjectPath: Path = configurator.settings.rootProject.projectDir.toPath()
   var repositoryGroupPath: Path
   private val includedBuildPropertiesMap = HashMap<String, IHMCBuildProperties>()
   val transitiveBuildFolderNames = TreeSet<String>()
   
   init
   {
      repositoryGroupPath = rootProjectPath
      for (i in 1..depthFromRepositoryGroup)
      {
         repositoryGroupPath = repositoryGroupPath.resolve("..")
      }
      repositoryGroupPath = repositoryGroupPath.toRealPath()
      logInfo(logger, "Repository group path: " + repositoryGroupPath)
   }
   
   /**
    * Returns a list of all the paths to include as strings.
    */
   fun findCompositeBuilds(): List<String>
   {
      mapAllCompatiblePaths(repositoryGroupPath)
      
      val buildsToInclude = ArrayList<String>()
      findTransitivesRecursive(rootProjectPath)
      
      // Should probably sort this with repository name included
      for (transitiveKey in transitiveBuildFolderNames)
      {
            val relativizedPathName: String = rootProjectPath.relativize(includedBuildPropertiesMap[transitiveKey]!!.projectPath).toString()
            if (!relativizedPathName.isEmpty()) // Including itself
            {
               buildsToInclude.add(relativizedPathName)
            }
      }
      
      for (buildToInclude in buildsToInclude)
      {
         logInfo(logger, "Including build: " + buildToInclude)
      }
      
      return buildsToInclude
   }
   
   private fun findTransitivesRecursive(projectDir: Path)
   {
      // If not initially mapped, for compatibility
      if (!includedBuildPropertiesMap.containsKey(projectDir.fileName.toString()))
         return
      
      if (includedBuildPropertiesMap[projectDir.fileName.toString()]!!.isProjectGroup)
      {
         val projectFile = projectDir.toFile()
         for (childDir in projectFile.list())
         {
            if (File(projectFile, childDir + "/build.gradle").exists())
            {
               addModule(childDir)
            }
         }
      }
      else
      {
         val dependencies: SortedSet<String> = parseDependenciesFromGradleFile(projectDir.resolve("build.gradle"))
         
         for (dependency in dependencies)
         {
            addModule(dependency)
         }
      }
   }
   
   private fun addModule(dependency: String)
   {
      val newMatchingKeys: List<String> = findMatchingBuildKey(dependency)
      
      transitiveBuildFolderNames.addAll(newMatchingKeys)
      for (newMatchingKey in newMatchingKeys)
      {
         logInfo(logger, "Adding module: " + newMatchingKey)
         findTransitivesRecursive(includedBuildPropertiesMap[newMatchingKey]!!.projectPath)
      }
   }
   
   private fun findMatchingBuildKey(dependencyNameAsDeclared: String): List<String>
   {
      val matched = ArrayList<String>()
      for (includedBuildProperties in includedBuildPropertiesMap.values)
      {
         // This is probably where we need to use kebabCasedName
         val buildFolderNameToCheck = includedBuildProperties.projectPath.fileName.toString()
         // Since this method is gathering more build folder names, make sure this folder isn't already in the set.
         // If it is, you save some computation on name matching.
         // Make sure the names match up. See {@link #matchNames}
         if (!transitiveBuildFolderNames.contains(buildFolderNameToCheck) && matchNames(buildFolderNameToCheck, dependencyNameAsDeclared))
         {
            logInfo(logger, "Matched: " + dependencyNameAsDeclared + " to " + buildFolderNameToCheck + "  " + toPascalCased(dependencyNameAsDeclared))
            matched.add(buildFolderNameToCheck)
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
         
         if (includedBuildProperties.kebabCasedName == kebabCasedName || !includedBuildProperties.excludeFromCompositeBuild)
         {
            includedBuildPropertiesMap.put(directory.fileName.toString(), includedBuildProperties)
   
            logInfo(logger, "Found: " + directory.fileName.toString() + ": " + directory)
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
            && Files.exists(subdirectory.resolve("build.gradle"))
            && Files.exists(subdirectory.resolve("gradle.properties"))
            && Files.exists(subdirectory.resolve("settings.gradle")))
   }
   
   private fun matchNames(buildFolderNameToCheck: String, dependencyNameAsDeclared: String): Boolean
   {
      if (dependencyNameAsDeclared == buildFolderNameToCheck) return true
      
      val buildToCheckProperties = includedBuildPropertiesMap[buildFolderNameToCheck]!!
      
      if (dependencyNameAsDeclared == buildToCheckProperties.pascalCasedName) return true
      if (dependencyNameAsDeclared == buildToCheckProperties.kebabCasedName) return true
      
      for (extraSourceSet in buildToCheckProperties.extraSourceSets)
      {
         if (dependencyNameAsDeclared == buildToCheckProperties.pascalCasedName + extraSourceSet.capitalize()) return true
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
            logInfo(logger, "Found declared dependency: " + dependency[1])
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
         logInfo(logger, "Found map entry: " + expression.getText())
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
}