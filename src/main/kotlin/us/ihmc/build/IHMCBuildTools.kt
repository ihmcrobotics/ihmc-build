package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.logging.Logger
import us.ihmc.commons.nio.FileTools
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList

fun logQuiet(logger: Logger, message: Any)
{
   logger.quiet(ihmcBuildMessage(message))
}

fun logInfo(logger: Logger, message: Any)
{
   logger.info(ihmcBuildMessage(message))
}

fun logWarn(logger: Logger, message: Any)
{
   logger.warn(ihmcBuildMessage(message))
}

fun logDebug(logger: Logger, message: Any)
{
   logger.debug(ihmcBuildMessage(message))
}

fun logTrace(logger: Logger, trace: Any)
{
   logger.trace(trace.toString())
}

fun ihmcBuildMessage(message: Any): String
{
   return "[ihmc-build] " + message
}

fun writeProjectSettingsFile(logger: Logger, directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   logQuiet(logger, "Generating project file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/project_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun writeGroupSettingsFile(logger: Logger, directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   logQuiet(logger, "Generating group file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/group_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun toPascalCased(hyphenated: String): String
{
   val split = hyphenated.split("-")
   var pascalCased = ""
   for (section in split)
   {
      pascalCased += StringUtils.capitalize(section)
   }
   return pascalCased
}

fun toHyphenated(pascalCased: String): String
{
   var hyphenated = pascalCasedToPrehyphenated(pascalCased);
   
   hyphenated = hyphenated.substring(1, hyphenated.length - 1);
   
   return hyphenated;
}

fun pascalCasedToPrehyphenated(pascalCased: String): String
{
   val parts = ArrayList<String>();
   var part = "";
   
   for (i in 0 until pascalCased.length)
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
   for (i in 0 until parts.size)
   {
      hyphenated += '-';
      hyphenated += parts.get(i);
   }
   hyphenated += '-';
   
   return hyphenated;
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun moveSourceFolderToMavenStandard(logger: Logger, projectDir: Path, sourceSetName: String)
{
   val oldSourceFolder: Path
   if (sourceSetName == "main")
   {
      oldSourceFolder = projectDir.resolve("src")
   }
   else
   {
      oldSourceFolder = projectDir.resolve(sourceSetName)
   }
   val mavenFolder = projectDir.resolve("src").resolve(sourceSetName).resolve("java")
   moveAPackage(logger, oldSourceFolder, mavenFolder, "us")
   moveAPackage(logger, oldSourceFolder, mavenFolder, "optiTrack")
}

private fun moveAPackage(logger: Logger, oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   if (Files.exists(oldSourceFolder))
   {
      val oldUs = oldSourceFolder.resolve(packageName)
      val newUs = mavenFolder.resolve(packageName)
      
      if (Files.exists(oldUs))
      {
         logQuiet(logger, mavenFolder)
         logQuiet(logger, oldUs)
         logQuiet(logger, newUs)
         
         FileTools.deleteQuietly(newUs)
         
         try
         {
            FileUtils.moveDirectory(oldUs.toFile(), newUs.toFile())
         }
         catch (e: Exception)
         {
            logTrace(logger, e.stackTrace)
         }
      }
   }
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun revertSourceFolderFromMavenStandard(logger: Logger, projectDir: Path, sourceSetName: String)
{
   val oldSourceFolder: Path
   if (sourceSetName == "main")
   {
      oldSourceFolder = projectDir.resolve("src")
   }
   else
   {
      oldSourceFolder = projectDir.resolve(sourceSetName)
   }
   val mavenFolder = projectDir.resolve("src").resolve(sourceSetName).resolve("java")
   if (Files.exists(oldSourceFolder))
   {
      revertAPackage(logger, oldSourceFolder, mavenFolder, "us")
      revertAPackage(logger, oldSourceFolder, mavenFolder, "optiTrack")
   }
   else
   {
      logWarn(logger, "File not exist: $oldSourceFolder")
   }
}

private fun revertAPackage(logger: Logger, oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   val oldUs = oldSourceFolder.resolve(packageName)
   val newUs = mavenFolder.resolve(packageName)
   
   if (Files.exists(newUs))
   {
      logQuiet(logger, mavenFolder)
      logQuiet(logger, oldUs)
      logQuiet(logger, newUs)
      
      FileTools.deleteQuietly(oldUs)
      
      try
      {
         FileUtils.moveDirectory(newUs.toFile(), oldUs.toFile())
      }
      catch (e: Exception)
      {
         logTrace(logger, e.stackTrace)
      }
   }
   else
   {
      logWarn(logger,"File not exist: $oldUs")
   }
}