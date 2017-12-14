package us.ihmc.build

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.http.options.Options
import groovy.lang.MissingPropertyException
import groovy.util.Eval
import org.apache.commons.lang3.StringUtils
import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import us.ihmc.commons.thread.ThreadTools
import java.io.IOException
import java.util.*

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

fun logError(logger: Logger, message: Any)
{
   logger.error(ihmcBuildMessage(message))
}

fun logDebug(logger: Logger, message: Any)
{
   logger.debug(ihmcBuildMessage(message))
}

fun logTrace(logger: Logger, trace: Any)
{
   logger.trace(trace.toString())
}

fun hardCrash(logger: Logger,  message: Any)
{
   logError(logger, message)
   throw GradleException("[ihmc-build] " + message as String)
}

val userHasBeenWarnedMap = hashSetOf<String>()

fun deprecationMessage(logger: Logger, thing: String, removalVersion: String, suggestedAction: String)
{
   if (!userHasBeenWarnedMap.contains("$thing$removalVersion$suggestedAction"))
   {
      logInfo(logger, "$thing has been deprecated and will be removed in $removalVersion. $suggestedAction")
      
      userHasBeenWarnedMap.add("$thing$removalVersion$suggestedAction")
   }
}

fun ihmcBuildMessage(message: Any): String
{
   return "[ihmc-build] " + message
}

fun kebabCasedNameCompatibility(logger: Logger, properties: IHMCPropertyInterface): String
{
   if (properties.has("kebabCasedName") && !properties.get("kebabCasedName").startsWith("$"))
   {
      return properties.get("kebabCasedName")
   }
   else if (properties.has("hyphenatedName") && !properties.get("hyphenatedName").startsWith("$"))
   {
      deprecationMessage(logger, "In ${rootProjectFolderName(properties)} gradle.properties, \"hyphenatedName\"", "0.15", "Please use \"kebabCasedName\" instead.")
      return properties.get("hyphenatedName")
   }
   else
   {
      val defaultValue = toKebabCased(rootProjectFolderName(properties))
      logInfo(logger, "No value found for kebabCasedName. Using default value: $defaultValue")
      properties.set("kebabCasedName", defaultValue)
      return defaultValue
   }
}

fun pascalCasedNameCompatibility(logger: Logger, properties: IHMCPropertyInterface): String
{
   if (properties.has("pascalCasedName") && !properties.get("pascalCasedName").startsWith("$"))
   {
      return properties.get("pascalCasedName")
   }
   else
   {
      val defaultValue = toPascalCased(rootProjectFolderName(properties))
      logInfo(logger, "No value found for pascalCasedName. Using default value: $defaultValue")
      properties.set("pascalCasedName", defaultValue)
      return defaultValue
   }
}

fun isProjectGroupCompatibility(logger: Logger, properties: IHMCPropertyInterface): Boolean
{
   if (properties.has("isProjectGroup") && !properties.get("isProjectGroup").startsWith("$"))
   {
      return properties.get("isProjectGroup")!!.toBoolean()
   }
   else
   {
      val defaultValue = false
      logInfo(logger, "No value found for isProjectGroup. Using default value: $defaultValue")
      properties.set("pascalCasedName", defaultValue.toString())
      return defaultValue
   }
}

fun depthFromWorkspaceDirectoryCompatibility(properties: IHMCPropertyInterface): Int
{
   if (properties.has("depthFromWorkspaceDirectory") && !properties.get("depthFromWorkspaceDirectory").startsWith("$"))
   {
      return properties.get("depthFromWorkspaceDirectory")!!.toInt()
   }
   else
   {
      throw MissingPropertyException("Please set depthFromWorkspaceDirectory = 1 (default) in gradle.properties.")
   }
}

fun includeBuildsFromWorkspaceCompatibility(properties: IHMCPropertyInterface): Boolean
{
   if (properties.has("includeBuildsFromWorkspace") && !properties.get("includeBuildsFromWorkspace").startsWith("$"))
   {
      return properties.get("includeBuildsFromWorkspace")!!.toBoolean()
   }
   else
   {
      throw MissingPropertyException("Please set includeBuildsFromWorkspace = true (default) in gradle.properties.")
   }
}

fun excludeFromCompositeBuildCompatibility(properties: IHMCPropertyInterface): Boolean
{
   if (properties.has("excludeFromCompositeBuild") && !properties.get("excludeFromCompositeBuild").startsWith("$"))
   {
      return properties.get("excludeFromCompositeBuild")!!.toBoolean()
   }
   else
   {
      throw MissingPropertyException("Please set excludeFromCompositeBuild = false (default) in gradle.properties.")
   }
}

fun modulesCompatibility(logger: Logger, properties: IHMCPropertyInterface): Collection<IHMCModule>
{
   if (properties.has("modules") && !properties.get("modules").startsWith("$"))
   {
      return modulesFromRawProperty(logger, properties.get("modules"), properties)
   }
   else if (properties.has("extraSourceSets") && !properties.get("extraSourceSets").startsWith("$"))
   {
      deprecationMessage(logger, "In ${rootProjectFolderName(properties)} gradle.properties, \"extraSourceSets\"", "0.15", "Please use \"modules\" instead.")
      return modulesFromRawProperty(logger, properties.get("extraSourceSets"), properties)
   }
   else
   {
      throw MissingPropertyException("Please set modules = [] (ex. [\"test\", \"visualizers\"] in gradle.properties.")
   }
}

fun modulesFromRawProperty(logger: Logger, propertyValue: String, properties: IHMCPropertyInterface) : Collection<IHMCModule>
{
   val userModuleIdentifiers = linkedSetOf("main")
   userModuleIdentifiers.addAll(Eval.me(propertyValue) as ArrayList<String>)
   
   val modules = arrayListOf<IHMCModule>()
   for (userModuleIdentifier in userModuleIdentifiers)
   {
      modules.add(IHMCModule(logger, userModuleIdentifier, properties))
   }
   return modules
}

fun toModuleIdentifier(subproject: Project): String
{
   return toKebabCased(subproject.name.substringAfter(subproject.parent!!.name + "-"))
}

fun rootProjectFolderName(properties: IHMCPropertyInterface): String
{
   return properties.get("rootProjectFolderName")
}

fun toPascalCased(anyCased: String): String
{
   val split = anyCased.split("-")
   var pascalCased = ""
   for (section in split)
   {
      pascalCased += StringUtils.capitalize(section)
   }
   return pascalCased
}

fun toCamelCased(anyCased: String): String
{
   val split = anyCased.split("-")
   var camelCased = ""
   if (split.size > 0)
   {
      camelCased += split[0].decapitalize()
   }
   for (i in 1 until split.size)
   {
      camelCased += StringUtils.capitalize(split[i])
   }
   return camelCased
}

fun toKebabCased(anyCased: String): String
{
   var kebabCased = toPreKababWithBookendHandles(anyCased);
   
   kebabCased = kebabCased.substring(1, kebabCased.length - 1);
   
   return kebabCased;
}

fun toPreKababWithBookendHandles(anyCased: String): String
{
   val parts = ArrayList<String>();
   var part = "";
   
   for (i in 0 until anyCased.length)
   {
      var character = anyCased[i].toString();
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
   
   var kebab = "";
   for (i in 0 until parts.size)
   {
      kebab += '-';
      kebab += parts.get(i);
   }
   kebab += '-';
   
   return kebab;
}

fun isBuildRoot(project: Project): Boolean
{
   return isBuildRoot(project.gradle.startParameter)
}

fun isBuildRoot(startParameter: StartParameter): Boolean
{
   return startParameter.isSearchUpwards
}

fun requestGlobalBuildNumberFromCIDatabase(logger: Logger, buildKey: String): String
{
   var tryCount = 0
   var globalBuildNumber = "ERROR"
   while (tryCount < 5 && globalBuildNumber == "ERROR")
   {
      globalBuildNumber = tryGlobalBuildNumberRequest(logger, buildKey)
      tryCount++
      logInfo(logger, "Global build number for $buildKey: $globalBuildNumber")
   }
   
   return globalBuildNumber.toString()
}

private fun tryGlobalBuildNumberRequest(logger: Logger, buildKey: String): String
{
   try
   {
      return Unirest.get("http://alcaniz.ihmc.us:8087").queryString("globalBuildNumber", buildKey).asString().getBody()
   }
   catch (e: UnirestException)
   {
      logInfo(logger, "Failed to retrieve global build number. Trying again... " + e.message)
      ThreadTools.sleep(100)
      try
      {
         Unirest.shutdown();
         Options.refresh();
      }
      catch (ioException: IOException)
      {
         ioException.printStackTrace();
      }
      return "ERROR"
   }
}