package us.ihmc.ci

import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

object TagParser
{
   /**
    * Return map of fully qualified test names to tag names sourced
    * from the JUnit 5 discovery engine itself.
    */
   fun parseForTags(testProject: Project, testsToTagsMap: HashMap<String, HashSet<String>>)
   {
      LogTools.info("[ihmc-ci] Discovering tests in $testProject")

      val contextClasspathUrls = arrayListOf<URL>()   // all of the tests and dependencies
      val selectorPaths = hashSetOf<Path>()           // just the test classes in this project
      assembleTestClasspath(testProject, contextClasspathUrls, selectorPaths)
      LogTools.debug("[ihmc-ci] Classpath entries: " + contextClasspathUrls.toString())

      val originalClassLoader = Thread.currentThread().contextClassLoader
      val customClassLoader = URLClassLoader.newInstance(contextClasspathUrls.toTypedArray(), originalClassLoader)
      lateinit var testPlan: TestPlan
      try
      {
         Thread.currentThread().contextClassLoader = customClassLoader
         debugContextClassLoader(customClassLoader)

         val launcher = LauncherFactory.create()
         val builder = LauncherDiscoveryRequestBuilder.request()
         builder.selectors(DiscoverySelectors.selectClasspathRoots(selectorPaths))
         builder.configurationParameters(emptyMap())
         val discoveryRequest = builder.build()
         debugClasspathSelectors(discoveryRequest)
         testPlan = launcher.discover(discoveryRequest)
         recursiveBuildMap(testPlan.roots, testPlan, testsToTagsMap)
         LogTools.debug("[ihmc-ci] Contains tests: ${testPlan.containsTests()}")
      }
      finally
      {
         Thread.currentThread().contextClassLoader = originalClassLoader
      }
   }

   private fun recursiveBuildMap(set: Set<TestIdentifier>, testPlan: TestPlan, testsToTagsMap: HashMap<String, HashSet<String>>)
   {
      set.forEach { testIdentifier ->
         if (testIdentifier.type == TestDescriptor.Type.TEST && testIdentifier.source.isPresent && testIdentifier.source.get() is MethodSource)
         {
            val methodSource = testIdentifier.source.get() as MethodSource
            LogTools.debug("[ihmc-ci] Test id: ${testIdentifier.displayName} tags: ${testIdentifier.tags} path: $methodSource")
            val fullyQualifiedTestName = methodSource.className + "." + methodSource.methodName
            if (!testsToTagsMap.containsKey(fullyQualifiedTestName))
            {
               testsToTagsMap.put(fullyQualifiedTestName, hashSetOf())
            }

            testIdentifier.tags.forEach { testTag ->
               testsToTagsMap[fullyQualifiedTestName]!!.add(testTag.name)
            }
         }
         else
         {
            LogTools.debug("[ihmc-ci] Test id: ${testIdentifier.displayName} tags: ${testIdentifier.tags} type: ${testIdentifier.type}")
         }

         recursiveBuildMap(testPlan.getChildren(testIdentifier), testPlan, testsToTagsMap)
      }
   }

   /**
    * This function gathers all the paths and JARs comprising the classpath of the test source set
    * of the project this plugin is applied to. It is used to simulate conditions as if Gradle or
    * JUnit was running those tests.
    */
   private fun assembleTestClasspath(testProject: Project, contextClasspathUrls: ArrayList<URL>, selectorPaths: HashSet<Path>)
   {
      val java = testProject.convention.getPlugin(JavaPluginConvention::class.java)
//      val java = testProject.convention.getPlugin(JavaLibraryPlugin::class.java)
//      testProject.plugins.
//      testProject.configurations.getByName("default").forEach { file ->
      java.sourceSets.getByName("main").compileClasspath.forEach { file ->
         addStuffToClasspath(file, contextClasspathUrls, selectorPaths)
      }
      java.sourceSets.getByName("main").runtimeClasspath.forEach { file ->
         addStuffToClasspath(file, contextClasspathUrls, selectorPaths)
      }
   }

   private fun addStuffToClasspath(file: File, contextClasspathUrls: ArrayList<URL>, selectorPaths: HashSet<Path>)
   {
      var entryString = file.toString()
      val uri = file.toURI()
      val path = file.toPath()
      if (entryString.endsWith(".jar"))
      {
         contextClasspathUrls.add(uri.toURL())
      }
      else if (!entryString.endsWith("/"))
      {
         val fileWithSlash = File("$entryString/") // TODO: Is this necessary?
         contextClasspathUrls.add(fileWithSlash.toURI().toURL())
         selectorPaths.add(fileWithSlash.toPath())
      }
      else
      {
         contextClasspathUrls.add(uri.toURL())
         selectorPaths.add(path)
      }
   }

   private fun debugClasspathSelectors(discoveryRequest: LauncherDiscoveryRequest)
   {
      discoveryRequest.getSelectorsByType(ClasspathRootSelector::class.java).forEach {
         LogTools.debug("[ihmc-ci] Selector: $it")
      }
   }

   private fun debugContextClassLoader(customClassLoader: URLClassLoader)
   {
      // make sure context class loader is working
      customClassLoader.urLs.forEach {
         LogTools.debug("[ihmc-ci] " + it.toString())
      }
   }
}
