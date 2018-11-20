package us.ihmc.ci.sourceCodeParser

import org.gradle.api.Project
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
import us.ihmc.ci.LogTools
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Return map of fully qualified test names to tag names sourced
 * from the JUnit 5 discovery engine itself.
 */
fun parseForTags(testProject: Project, testsToTagsMap: HashMap<String, HashSet<String>>)
{
   LogTools.info("Discovering tests in $testProject")

   val contextClasspathUrls = arrayListOf<URL>()   // all of the tests and dependencies
   val selectorPaths = hashSetOf<Path>()           // just the test classes in this project
   assembleTestClasspath(testProject, contextClasspathUrls, selectorPaths)

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
      recursiveBuildMap(testPlan!!.roots, testPlan, testsToTagsMap)
      LogTools.debug("Contains tests: ${testPlan.containsTests()}")
   }
   finally
   {
      Thread.currentThread().contextClassLoader = originalClassLoader
   }
}

fun recursiveBuildMap(set: Set<TestIdentifier>, testPlan: TestPlan, testsToTagsMap: HashMap<String, HashSet<String>>)
{
   set.forEach {
      if (it.type == TestDescriptor.Type.TEST && it.source.isPresent && it.source.get() is MethodSource)
      {
         val methodSource = it.source.get() as MethodSource
         LogTools.debug("Test id: ${it.displayName} tags: ${it.tags} path: $methodSource")
         val fullyQualifiedTestName = methodSource.className + "." + methodSource.methodName
         if (!testsToTagsMap.containsKey(fullyQualifiedTestName))
         {
            testsToTagsMap.put(fullyQualifiedTestName, hashSetOf())
         }

         it.tags.forEach {
            testsToTagsMap[fullyQualifiedTestName]!!.add(it.name)
         }
      }
      else
      {
         LogTools.debug("Test id: ${it.displayName} tags: ${it.tags} type: ${it.type}")
      }

      recursiveBuildMap(testPlan.getChildren(it), testPlan, testsToTagsMap)
   }
}

/**
 * This function gathers all the paths and JARs comprising the classpath of the test source set
 * of the project this plugin is applied to. It is used to simulate conditions as if Gradle or
 * JUnit was running those tests.
 */
fun assembleTestClasspath(testProject: Project, contextClasspathUrls: ArrayList<URL>, selectorPaths: HashSet<Path>)
{
   val java = testProject.convention.getPlugin(JavaPluginConvention::class.java)
   java.sourceSets.getByName("main").runtimeClasspath.forEach {

      var entryString = it.toString()
      val uri = it.toURI()
      val path = it.toPath()
      if (entryString.endsWith(".jar"))
      {
         contextClasspathUrls.add(uri.toURL())
      }
      else if (!entryString.endsWith("/"))
      {
         val file = File("$entryString/")
         contextClasspathUrls.add(file.toURI().toURL())
         selectorPaths.add(file.toPath())
      }
      else
      {
         contextClasspathUrls.add(uri.toURL())
         selectorPaths.add(path)
      }
   }
}

fun debugClasspathSelectors(discoveryRequest: LauncherDiscoveryRequest)
{
   discoveryRequest.getSelectorsByType(ClasspathRootSelector::class.java).forEach {
      LogTools.debug("Selector: $it")
   }
}

fun debugContextClassLoader(customClassLoader: URLClassLoader)
{
   // make sure context class loader is working
   customClassLoader.urLs.forEach {
      LogTools.debug(it.toString())
   }
}
