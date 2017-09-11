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
   val depthFromWorkspaceDirectory = configurator.depthFromWorkspaceDirectory
   val rootProjectPath: Path = configurator.settings.rootProject.projectDir.toPath()
   var workspacePath: Path
   val buildFolderNameToPathMap = HashMap<String, Path>()
   private val buildFolderNameToPropertiesMap = HashMap<String, IHMCBuildProperties>()
   val transitiveBuildFolderNames = TreeSet<String>()
   
   init
   {
      workspacePath = rootProjectPath
      for (i in 1..depthFromWorkspaceDirectory)
      {
         workspacePath = workspacePath.resolve("..")
      }
      workspacePath = workspacePath.toRealPath()
   }
   
   fun findCompositeBuilds(): List<String>
   {
      logInfo(logger, "Workspace dir: " + workspacePath)
      
      if (Files.isDirectory(workspacePath) && Files.exists(workspacePath.resolve("build.gradle"))
            && Files.exists(workspacePath.resolve("gradle.properties")) && Files.exists(workspacePath.resolve("settings.gradle")))
      {
         buildFolderNameToPathMap.put(workspacePath.fileName.toString(), workspacePath)
         buildFolderNameToPropertiesMap.put(workspacePath.fileName.toString(), IHMCBuildProperties(logger).load(workspacePath))
      }
      mapDirectory(workspacePath)
      for (buildFolderName in buildFolderNameToPathMap.keys)
      {
         logInfo(logger, "Found: " + buildFolderName + " : " + buildFolderNameToPathMap.get(buildFolderName))
      }
      
      val buildsToInclude = ArrayList<String>()
      findTransitivesRecursive(rootProjectPath)
      for (transitiveKey in transitiveBuildFolderNames)
      {
         if (!buildFolderNameToPropertiesMap[transitiveKey]!!.exclude)
         {
            val relativizedPathName: String = rootProjectPath.relativize(buildFolderNameToPathMap.get(transitiveKey)).toString()
            if (!relativizedPathName.isEmpty()) // Including itself
            {
               buildsToInclude.add(relativizedPathName)
            }
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
      if (!buildFolderNameToPropertiesMap.containsKey(projectDir.fileName.toString()))
         return
      
      if (buildFolderNameToPropertiesMap[projectDir.fileName.toString()]!!.isProjectGroup)
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
         findTransitivesRecursive(buildFolderNameToPathMap[newMatchingKey]!!)
      }
   }
   
   private fun findMatchingBuildKey(dependencyNameAsDeclared: String): List<String>
   {
      val matched = ArrayList<String>()
      for (buildFolderNameToCheck in buildFolderNameToPathMap.keys)
      {
         if (!transitiveBuildFolderNames.contains(buildFolderNameToCheck) && matchNames(buildFolderNameToCheck, dependencyNameAsDeclared))
         {
            logInfo(logger, "Matched: " + dependencyNameAsDeclared + " to " + buildFolderNameToCheck + "  " + toPascalCased(dependencyNameAsDeclared))
            matched.add(buildFolderNameToCheck)
         }
      }
      
      return matched
   }
   
   private fun mapDirectory(directory: Path)
   {
      for (subdirectory in Files.list(directory))
      {
         if (Files.isDirectory(subdirectory) && Files.exists(subdirectory.resolve("build.gradle"))
               && Files.exists(subdirectory.resolve("gradle.properties")) && Files.exists(subdirectory.resolve("settings.gradle")))
         {
            buildFolderNameToPathMap.put(subdirectory.fileName.toString(), subdirectory)
            buildFolderNameToPropertiesMap.put(subdirectory.fileName.toString(), IHMCBuildProperties(logger).load(subdirectory))
            
            mapDirectory(subdirectory)
         }
      }
   }
   
   private fun matchNames(buildFolderNameToCheck: String, dependencyNameAsDeclared: String): Boolean
   {
      if (dependencyNameAsDeclared == buildFolderNameToCheck) return true
      
      val buildToCheckProperties = buildFolderNameToPropertiesMap[buildFolderNameToCheck]!!
      
      if (dependencyNameAsDeclared == buildToCheckProperties.pascalCasedName) return true
      if (dependencyNameAsDeclared == buildToCheckProperties.hyphenatedName) return true
      
      for (extraSourceSet in buildToCheckProperties.extraSourceSets)
      {
         if (dependencyNameAsDeclared == buildToCheckProperties.pascalCasedName + extraSourceSet.capitalize()) return true
         if (dependencyNameAsDeclared == buildToCheckProperties.hyphenatedName + "-" + extraSourceSet) return true
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