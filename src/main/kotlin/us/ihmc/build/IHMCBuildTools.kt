package us.ihmc.build

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import us.ihmc.commons.nio.FileTools
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList

fun writeProjectSettingsFile(directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   println("[ihmc-build] Generating project file: " + settingsFile.absolutePath)
   
   val fileContent = IHMCBuildPlugin::class.java.getResource("/project_settings.gradle").readText()
   settingsFile.writeText(fileContent)
}

fun writeGroupSettingsFile(directory: File)
{
   val settingsFile = directory.resolve("settings.gradle")
   
   println("[ihmc-build] Generating group file: " + settingsFile.absolutePath)
   
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

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun moveSourceFolderToMavenStandard(projectDir: Path, sourceSetName: String)
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
   moveAPackage(oldSourceFolder, mavenFolder, "us")
   moveAPackage(oldSourceFolder, mavenFolder, "optiTrack")
}

private fun moveAPackage(oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   if (Files.exists(oldSourceFolder))
   {
      val oldUs = oldSourceFolder.resolve(packageName)
      val newUs = mavenFolder.resolve(packageName)
      
      if (Files.exists(oldUs))
      {
         printQuiet(mavenFolder)
         printQuiet(oldUs)
         printQuiet(newUs)
         
         FileTools.deleteQuietly(newUs)
         
         try
         {
            FileUtils.moveDirectory(oldUs.toFile(), newUs.toFile())
         }
         catch (e: Exception)
         {
            printQuiet("Failed: " + e.printStackTrace())
         }
      }
   }
}

fun moveCustomFolderToMavenStandard(projectPath: Path, customSourcePath: Path, toPath: Path)
{
   if (Files.exists(customSourcePath) && Files.isDirectory(customSourcePath))
   {
      val tempDir = projectPath.resolve("tempDir")
      
      printQuiet(customSourcePath)
      printQuiet(toPath)
      printQuiet(tempDir)
   
      FileTools.deleteQuietly(tempDir)
      
      try
      {
         FileUtils.moveDirectory(customSourcePath.toFile(), tempDir.toFile())
         FileUtils.moveDirectory(tempDir.toFile(), toPath.toFile())
      }
      catch (e: Exception)
      {
         printQuiet("Failed: " + e.printStackTrace())
      }
   
      FileTools.deleteQuietly(tempDir)
   }
}

fun revertCustomFolderFromMavenStandard(projectPath: Path, customSourcePath: Path, toPath: Path)
{
   if (Files.exists(toPath) && Files.isDirectory(toPath))
   {
      val tempDir = projectPath.resolve("tempDir")
   
      printQuiet(customSourcePath)
      printQuiet(toPath)
      printQuiet(tempDir)
   
      FileTools.deleteQuietly(tempDir)
      
      try
      {
         FileUtils.moveDirectory(toPath.toFile(), tempDir.toFile())
         FileUtils.moveDirectory(tempDir.toFile(), customSourcePath.toFile())
      }
      catch (e: Exception)
      {
         printQuiet("Failed: " + e.printStackTrace())
      }
   
      FileTools.deleteQuietly(tempDir)
   }
}

/**
 * Temporary tool for converting projects to new folder structure quickly.
 */
fun revertSourceFolderFromMavenStandard(projectDir: Path, sourceSetName: String)
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
      revertAPackage(oldSourceFolder, mavenFolder, "us")
      revertAPackage(oldSourceFolder, mavenFolder, "optiTrack")
   }
   else
   {
      println("File not exsiss: $oldSourceFolder")
   }
}

private fun revertAPackage(oldSourceFolder: Path, mavenFolder: Path, packageName: String)
{
   val oldUs = oldSourceFolder.resolve(packageName)
   val newUs = mavenFolder.resolve(packageName)
   
   if (Files.exists(newUs))
   {
      printQuiet(mavenFolder)
      printQuiet(oldUs)
      printQuiet(newUs)
      
      FileTools.deleteQuietly(oldUs)
      
      try
      {
         FileUtils.moveDirectory(newUs.toFile(), oldUs.toFile())
      }
      catch (e: Exception)
      {
         printQuiet("Failed: " + e.printStackTrace())
      }
   }
   else
   {
   
      println("File not exsiss: $oldUs")
   }
}

fun printQuiet(message: Any)
{
   println("[ihmc-build] " + message)
}