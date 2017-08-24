package us.ihmc.build

import groovy.util.Eval
import org.apache.commons.lang3.StringUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.gradle.api.GradleScriptException
import org.gradle.api.logging.Logger
import java.io.FileInputStream
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
   private val buildFolderNameToPropertiesMap = HashMap<String, BuildProperties>()
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
      logger.info("[ihmc-build] Workspace dir: " + workspacePath)
      mapDirectory(workspacePath)
      for (buildFolderName in buildFolderNameToPathMap.keys)
      {
         logger.info("[ihmc-build] Found: " + buildFolderName + " : " + buildFolderNameToPathMap.get(buildFolderName))
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
         logger.quiet("[ihmc-build] Including build: " + buildToInclude)
      }
      
      return buildsToInclude
   }
   
   private fun findTransitivesRecursive(projectDir: Path)
   {
      val dependencies: SortedSet<String> = parseDependenciesFromGradleFile(projectDir.resolve("build.gradle"))
      
      for (dependency in dependencies)
      {
         val newMatchingKeys: List<String> = findMatchingBuildKey(dependency)
         
         transitiveBuildFolderNames.addAll(newMatchingKeys)
         for (newMatchingKey in newMatchingKeys)
         {
            logger.info("[ihmc-build] Adding module: " + newMatchingKey)
            findTransitivesRecursive(buildFolderNameToPathMap[newMatchingKey]!!)
         }
      }
   }
   
   private fun findMatchingBuildKey(dependencyNameAsDeclared: String): List<String>
   {
      val matched = ArrayList<String>()
      for (buildFolderNameToCheck in buildFolderNameToPathMap.keys)
      {
         if (!transitiveBuildFolderNames.contains(buildFolderNameToCheck) && matchNames(buildFolderNameToCheck, dependencyNameAsDeclared))
         {
            logger.info("Matched: " + dependencyNameAsDeclared + " to " + buildFolderNameToCheck + "  " + toPascalCased(dependencyNameAsDeclared))
            matched.add(buildFolderNameToCheck)
         }
      }
      
      return matched
   }
   
   private fun mapDirectory(directory: Path)
   {
      for (subdirectory in Files.list(directory))
      {
         if (Files.isDirectory(subdirectory) && Files.exists(subdirectory.resolve("build.gradle")))
         {
            buildFolderNameToPathMap.put(subdirectory.fileName.toString(), subdirectory)
            buildFolderNameToPropertiesMap.put(subdirectory.fileName.toString(), loadProperties(subdirectory))
            
            mapDirectory(subdirectory)
         }
      }
   }
   
   private fun loadProperties(projectPath: Path): BuildProperties
   {
      val properties = Properties()
      properties.load(FileInputStream(projectPath.resolve("gradle.properties").toFile()))
      val buildProperties: BuildProperties = BuildProperties()
      for (propertyKey in properties.keys)
      {
         if (propertyKey == "excludeFromCompositeBuild")
         {
            buildProperties.exclude = (properties.get(propertyKey)!! as String).toBoolean()
            if (buildProperties.exclude)
            {
               logger.quiet("[ihmc-build] Excluding " + projectPath.fileName.toString() + ". Property excludeFromCompositeBuild = " + properties.get(propertyKey))
            }
         }
         if (propertyKey == "pascalCasedName")
         {
            buildProperties.pascalCasedName = properties.get(propertyKey)!! as String
         }
         if (propertyKey == "hyphenatedName")
         {
            buildProperties.hyphenatedName = properties.get(propertyKey)!! as String
         }
         if (propertyKey == "extraSourceSets")
         {
            buildProperties.extraSourceSets.addAll(Eval.me(properties.get(propertyKey)!! as String) as ArrayList<String>)
         }
      }
      
      if (buildProperties.pascalCasedName == null)
      {
         buildProperties.pascalCasedName = toPascalCased(projectPath.fileName.toString())
      }
      if (buildProperties.hyphenatedName == null)
      {
         buildProperties.hyphenatedName = toHyphenated(projectPath.fileName.toString())
      }
      
      return buildProperties
   }
   
   class BuildProperties
   {
      var hyphenatedName: String = ""
      var pascalCasedName: String = ""
      var exclude = false
      val extraSourceSets = ArrayList<String>()
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
         logger.info("[ihmc-build] Parsing for dependencies: " + buildFile)
         val nodes: List<ASTNode> = builder.buildFromString(bytesInFile)
         val dependencies = ArrayList<Array<String>>()
         val visitor = ExternalGradleFileCodeVisitor(dependencies, logger)
         for (node in nodes)
         {
            node.visit(visitor)
         }
         
         for (dependency in dependencies)
         {
            logger.info("[ihmc-build] Found declared dependency: " + dependency[1])
            dependencySet.add(dependency[1])
         }
      }
      catch (e: NoSuchFileException)
      {
         logger.info("[ihmc-build] Build not found on disk: " + e.message)
      }
      catch (e: GradleScriptException)
      {
         logger.error("[ihmc-build] Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: MultipleCompilationErrorsException)
      {
         logger.error("[ihmc-build] Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: IOException)
      {
         e.printStackTrace()
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
         logger.info("[ihmc-build] Found map entry: " + expression.getText())
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
            
            dependencies.add(arrayOf(dependencyMap["group"]!!, dependencyMap["name"]!!, dependencyMap["version"]!!))
         }
         
         super.visitMapExpression(expression)
      }
   }
   
   private fun toPascalCased(hyphenated: String): String
   {
      val split = hyphenated.split("-")
      var pascalCased = ""
      for (section in split)
      {
         pascalCased += StringUtils.capitalize(section)
      }
      return pascalCased
   }
   
   private fun toHyphenated(pascalCased: String): String
   {
      var hyphenated = pascalCasedToPrehyphenated(pascalCased);
      
      hyphenated = hyphenated.substring(1, hyphenated.length - 1);
      
      return hyphenated;
   }
   
   private fun pascalCasedToPrehyphenated(pascalCased: String): String
   {
      val parts = ArrayList<String>();
      var part = "";
      
      for (i in 0..pascalCased.length)
      {
         var character = pascalCased[i].toString();
         if (StringUtils.isAllUpperCase(character) || StringUtils.isNumeric(character))
         {
            if (!part.isEmpty())
            {
               parts.add(part.toLowerCase());
            }
            part = character;
         }
         else
         {
            part += character;
         }
      }
      if (!part.isEmpty())
      {
         parts.add(part.toLowerCase());
      }
      
      var hyphenated = "";
      for (i in 0..parts.size)
      {
         hyphenated += '-';
         hyphenated += parts.get(i);
      }
      hyphenated += '-';
      
      return hyphenated;
   }
}