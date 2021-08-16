package us.ihmc.build

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.gradle.api.GradleException
import org.gradle.api.GradleScriptException
import org.gradle.api.logging.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class IHMCCompositeBuildAssembler(val configurator: IHMCSettingsConfigurator)
{
   val logger = configurator.logger
   val buildRootKebabCasedName = configurator.settings.rootProject.name
   var compositeRootKebabCasedName = "NotYetEvaluated"
   val compositeSearchHeight = configurator.compositeSearchHeight
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
      LogTools.info("Repository group path: " + compositeSearchPath)
   }
   
   /**
    * Returns a list of all the paths to include as strings.
    */
   fun findCompositeBuilds(): List<String>
   {
      mapAllCompatiblePaths(compositeSearchPath)
      
      findTransitivesRecursive(buildRootKebabCasedName)
      
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
         LogTools.quiet("Including build: " + buildToInclude)
      }
      
      return buildsToInclude
   }
   
   private fun findTransitivesRecursive(kebabCasedName: String)
   {
      for (kebabCasedDependency in findDirectKebabCasedDependencies(kebabCasedName))
      {
         for (newMatchingBuild in findNewMatchingBuilds(kebabCasedDependency))
         {
            LogTools.info("Adding dependency: " + newMatchingBuild.projectPath)
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
         for (childDir in projectFile.listFiles { f -> f.isDirectory })
         {
            val childPath = childDir.toPath()
            if (pathToPropertiesMap.containsKey(childPath))
            {
               dependencies.addAll(propertiesFromPath(childPath).allArtifacts)
            }
         }
      }
      else
      {
         var declaredDependencies: SortedSet<String>? = null
         if (Files.isRegularFile(properties.projectPath.resolve("build.gradle")))
         {
            declaredDependencies = parseDependenciesFromGradleFile(properties.projectPath.resolve("build.gradle"))
         }
         else if (Files.isRegularFile(properties.projectPath.resolve("build.gradle.kts")))
         {
            declaredDependencies = IHMCBuildTools.parseDependenciesFromGradleKtsFile(properties.projectPath.resolve("build.gradle.kts"))
         }
         else
         {
            throw GradleException("Did not find a build.gradle or build.gradle.kts file")
         }

         for (declaredDependency in declaredDependencies)
         {
            LogTools.debug("Found dependency: $declaredDependency")
         }

         for (declaredDependency in declaredDependencies!!)
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
      for (artifactName in kebabCasedNameToPropertiesMap.keys)
      {
         // Since this method is gathering more build folder names, make sure this folder isn't already in the set.
         // If it is, you save some computation on name matching.
         // Make sure the names match up. See {@link #matchNames}
         if (!transitiveIncludedBuilds.contains(propertiesFromKebabCasedName(artifactName)) && matchNames(artifactName, kebabCasedDependency))
         {
            LogTools.info("Matched: " + kebabCasedDependency + " to " + artifactName)
            transitiveIncludedBuilds.add(propertiesFromKebabCasedName(artifactName))
            matched.add(propertiesFromKebabCasedName(artifactName))
         }
      }
      
      return matched
   }
   
   private fun mapAllCompatiblePaths(directory: Path)
   {
      var includedBuildProperties: IHMCBuildProperties? = null
      if (isPathCompatibleWithBuildConfiguration(directory))
      {
         // Load the properties, even for the root
         includedBuildProperties = IHMCBuildProperties(logger, directory)
         
         // Always include the build root, but observe external exclude preferences
         if (forceInclude(includedBuildProperties.kebabCasedName) || !includedBuildProperties.excludeFromCompositeBuild)
         {
            for (artifactName in includedBuildProperties.allArtifacts) // map test, etc. source set projects
            {
               kebabCasedNameToPropertiesMap.put(artifactName, includedBuildProperties)
               LogTools.info("Found: " + artifactName + ": " + directory)
            }
            pathToPropertiesMap.put(directory, includedBuildProperties)
         }
      }
   
      // The first project to be evaluated is always the composite search root
      // Set it now so we can force include it later
      if (compositeRootKebabCasedName == "NotYetEvaluated" && includedBuildProperties != null)
         compositeRootKebabCasedName = includedBuildProperties.kebabCasedName
      else
         compositeRootKebabCasedName = "NotAProject"
      
      // Recursively map all other projects
      if (includedBuildProperties == null // Keep searching through non-project directories
            || forceInclude(includedBuildProperties.kebabCasedName) // Always include the build root, composite root
            || !includedBuildProperties.excludeFromCompositeBuild) // Exclude subprojects of excluded groups
      {
         val listFiles = directory.toFile().listFiles { f -> f.isDirectory }
         if (listFiles != null) // Handle archive directories on Windows (i.e. C:/$RECYCLE.BIN)
         {
            for (subdirectory in listFiles)
            {
               mapAllCompatiblePaths(subdirectory.toPath())
            }
         }
      }
   }
   
   private fun forceInclude(kebabCasedName: String): Boolean
   {
      // Always include the build root
      if (kebabCasedName == buildRootKebabCasedName)
         return true
      
      // Always include the composite search root
      if (compositeRootKebabCasedName != "NotYetEvaluated" && kebabCasedName == compositeRootKebabCasedName)
         return true
      
      return false
   }
   
   /** Here, we could make the project more friendly by not having such harsh requirements. */
   private fun isPathCompatibleWithBuildConfiguration(subdirectory: Path): Boolean
   {
      return (Files.isDirectory(subdirectory)
            // Handle system root directory where fileName is null
            // Address Eclipse bug where it copies build files to bin directory
            && (subdirectory.fileName != null && subdirectory.fileName.toString() != "bin")
            // Address the same hypothetical situation in IntelliJ
            && (subdirectory.fileName != null && subdirectory.fileName.toString() != "out")
            && (Files.exists(subdirectory.resolve("build.gradle")) || Files.exists(subdirectory.resolve("build.gradle.kts")))
            && Files.exists(subdirectory.resolve("gradle.properties"))
            && (Files.exists(subdirectory.resolve("settings.gradle")) || Files.exists(subdirectory.resolve("settings.gradle.kts")))
            )
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
         LogTools.info("Parsing for dependencies: " + buildFile)
         
         // Handle empty build.gradle
         if (bytesInFile.trim().isEmpty())
         {
            LogTools.warn("Build file is empty: $buildFile")
            return dependencySet
         }
         
         val nodes: List<ASTNode> = builder.buildFromString(bytesInFile)
         val dependencies = ArrayList<Array<String>>()
         val visitor = ExternalGradleFileCodeVisitor(dependencies, logger)
         for (node in nodes)
         {
            node.visit(visitor)
         }
         
         for (dependency in dependencies)
         {
            LogTools.debug("Found declared dependency: " + dependency[1])
            dependencySet.add(dependency[1])
         }
      }
      catch (e: NoSuchFileException)
      {
         LogTools.info("Build not found on disk: " + e.message)
      }
      catch (e: GradleScriptException)
      {
         LogTools.warn("Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: MultipleCompilationErrorsException)
      {
         LogTools.warn("Cannot evaluate " + buildFile + ": " + e.message)
      }
      catch (e: IOException)
      {
         LogTools.trace(e.stackTrace)
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
         LogTools.debug("Found map entry: " + expression.getText())
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
      if (!kebabCasedNameToPropertiesMap.containsKey(kebabCasedName))
      {
         throw GradleException("Something went wrong. $kebabCasedName has not been mapped.")
      }
      return kebabCasedNameToPropertiesMap.get(kebabCasedName)!!
   }
   
   private fun propertiesFromPath(path: Path): IHMCBuildProperties
   {
      return pathToPropertiesMap.get(path)!!
   }
}
