package us.ihmc.ci;

import com.github.kittinunf.fuel.Fuel
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.testing.Test
import us.ihmc.ci.sourceCodeParser.parseForTags
import java.io.File

lateinit var LogTools: Logger

class IHMCCIPlugin : Plugin<Project>
{
   val JUNIT_VERSION = "5.3.1"

   lateinit var project: Project
   var cpuThreads = 8
   var category: String = "fast"
   var vintageMode: Boolean = false
   var ciBackendHost: String = "unset"
   lateinit var categoriesExtension: IHMCCICategoriesExtension
   var allocationJVMArg: String? = null
   val testsToTagsMap = lazy {
      val map = hashMapOf<String, HashSet<String>>()
      testProjects(project).forEach {
         parseForTags(it, map)
      }
      map
   }

   override fun apply(project: Project)
   {
      this.project = project
      LogTools = project.logger

      loadProperties()
      categoriesExtension = project.extensions.create("categories", IHMCCICategoriesExtension::class.java, project)
      configureDefaultCategories()

      for (testProject in testProjects(project))
      {
         LogTools.info("[ihmc-ci] Configuring ${testProject.name}")
         addTestDependencies(testProject, "compile", "runtimeOnly")
         configureTestTask(testProject)
      }

      // special case when a project does not use ihmc-build or doesn't declare a multi-project ending with "-test"
      // yes, some projects don't have any tests, but why would they use this plugin? so not checking for test code
      if (!containsIHMCTestMultiProject(project))
      {
         LogTools.info("[ihmc-ci] No test multi-project found, using test source set")
         addTestDependencies(project, "testCompile", "testRuntimeOnly")
         configureTestTask(project)
      }

      // register bambooSync task
      val bambooSync: (Task) -> Unit = { task ->
         task.doFirst {

            // send test/tag map to <backend program to be named>
            // and maybe some things happen there
            // or it sends back a signal to fail the build
            // send and receive JSON

            Fuel.testMode { timeout = 5000 }
            val url = "http://$ciBackendHost/sync"
            Fuel.post(url, listOf(Pair("text", "hello")))
                  .response { req, res, result ->
                     result.fold({ byteArray ->
                                    LogTools.error("success: $url\nresponse: ${res.responseMessage}")
                                 },
                                 { error ->
                                    LogTools.error("failed: $url\n$error")
                                 })
                  }
         }
      }
      project.tasks.register("bambooSync", bambooSync)
   }

   fun addTestDependencies(project: Project, compileConfigName: String, runtimeConfigName: String)
   {
      // add junit 5 dependencies
      project.dependencies.add(compileConfigName, "org.junit.jupiter:junit-jupiter-api:$JUNIT_VERSION")
      project.dependencies.add(runtimeConfigName, "org.junit.jupiter:junit-jupiter-engine:$JUNIT_VERSION")
      if (vintageMode)
         project.dependencies.add(runtimeConfigName, "org.junit.vintage:junit-vintage-engine:$JUNIT_VERSION")
      if (category == "allocation") // help out users trying to run allocation tests
         project.dependencies.add(compileConfigName, "com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.1.0")
   }

   fun configureTestTask(project: Project)
   {
      project.tasks.withType(Test::class.java) { test ->
         test.doFirst {
            // create a default category if not found
            val categoryConfig = postProcessCategoryConfig()
            applyCategoryConfigToGradleTest(test, categoryConfig, project)
         }
         test.finalizedBy(addPhonyTestXmlTask(project))
      }
   }

   fun applyCategoryConfigToGradleTest(test: Test, categoryConfig: IHMCCICategory, project: Project)
   {
      test.useJUnitPlatform {
         for (tag in categoryConfig.includeTags)
         {
            it.includeTags(tag)
         }
         for (tag in categoryConfig.excludeTags)
         {
            it.excludeTags(tag)
         }
         // If the "fast" category includes nothing, this excludes all tags included by other
         // categories, which makes it run only untagged tests and tests that would not be run
         // if the user were to run all defined catagories. This is both a safety feature,
         // and the expected functionality of the "fast" category, historically at IHMC.
         if (categoryConfig.name == "fast" && categoryConfig.includeTags.isEmpty())
         {
            for (definedCategory in categoriesExtension.categories)
            {
               for (tag in definedCategory.value.includeTags)
               {
                  it.excludeTags(tag)
               }
            }
         }
      }
      test.setForkEvery(categoryConfig.classesPerJVM.toLong())
      test.maxParallelForks = categoryConfig.maxJVMs
      this.project.properties["runningOnCIServer"].run {
         if (this != null)
            test.systemProperties["runningOnCIServer"] = toString()
      }
      for (jvmProp in categoryConfig.jvmProperties)
      {
         test.systemProperties[jvmProp.key] = jvmProp.value
      }
      val java = project.convention.getPlugin(JavaPluginConvention::class.java)
      val resourcesDir = java.sourceSets.getByName("main").output.resourcesDir
      this.project.logger.info("[ihmc-ci] Passing to JVM: -Dresource.dir=" + resourcesDir)
      test.systemProperties["resource.dir"] = resourcesDir
      val tmpArgs = test.allJvmArgs
      for (jvmArg in categoryConfig.jvmArguments)
      {
         if (jvmArg == ALLOCATION_AGENT_KEY)
         {
            tmpArgs.add(findAllocationJVMArg())
         }
         else
         {
            tmpArgs.add(jvmArg)
         }
      }
      tmpArgs.add("-ea")
      test.allJvmArgs = tmpArgs
      test.minHeapSize = "${categoryConfig.initialHeapSizeGB}g"
      test.maxHeapSize = "${categoryConfig.maxHeapSizeGB}g"
   }

   fun postProcessCategoryConfig(): IHMCCICategory
   {
      val categoryConfig = categoriesExtension.categories[category].run {
         if (this != null)
            this
         else
            IHMCCICategory(category)
      }

      this.project.properties["classesPerJVM"].run { if (this != null) categoryConfig.classesPerJVM = (this as String).toInt() }
      this.project.properties["maxJVMs"].run { if (this != null) categoryConfig.maxJVMs = (this as String).toInt() }
      this.project.properties["initialHeapSizeGB"].run { if (this != null) categoryConfig.initialHeapSizeGB = (this as String).toInt() }
      this.project.properties["maxHeapSizeGB"].run { if (this != null) categoryConfig.maxHeapSizeGB = (this as String).toInt() }
      if (categoryConfig.name == "fast")
      {
         testsToTagsMap.value.forEach {
            it.value.forEach {
               categoryConfig.excludeTags.add(it)
            }
         }
         categoryConfig.includeTags.clear()
      }
      else
      {
         // handle dynamically created categories
         // or default if no include specified
         if (categoryConfig.includeTags.isEmpty())
         {
            categoryConfig.includeTags.add(categoryConfig.name)
         }
      }

      this.project.logger.info("[ihmc-ci] classesPerJVM = ${categoryConfig.classesPerJVM}")
      this.project.logger.info("[ihmc-ci] maxJVMs = ${categoryConfig.maxJVMs}")
      this.project.logger.info("[ihmc-ci] includeTags = ${categoryConfig.includeTags}")
      this.project.logger.info("[ihmc-ci] excludeTags = ${categoryConfig.excludeTags}")
      this.project.logger.info("[ihmc-ci] initialHeapSizeGB = ${categoryConfig.initialHeapSizeGB}")
      this.project.logger.info("[ihmc-ci] maxHeapSizeGB = ${categoryConfig.maxHeapSizeGB}")

      // List tests to be run
      LogTools.quiet("[ihmc-ci] Tests to be run:")
      testsToTagsMap.value.forEach { entry ->
         if ((category == "fast" && entry.value.isEmpty()) || entry.value.contains(category))
         {
            LogTools.quiet(entry.key + " " + entry.value)
         }
      }

      return categoryConfig
   }

   fun addPhonyTestXmlTask(anyproject: Project): Task?
   {
      return anyproject.tasks.create("addPhonyTestXml") {
         it.doLast {
            var testsFound = false
            for (path in anyproject.rootDir.walkBottomUp())
            {
               if (path.toPath().toAbsolutePath().toString().matches(Regex(".*/test-results/test/.*\\.xml")))
               {
                  anyproject.logger.info("[ihmc-ci] Found test file: $path")
                  testsFound = true
                  break
               }
            }
            if (!testsFound)
               createNoTestsFoundXml(anyproject, anyproject.buildDir.resolve("test-results/test"))
         }
      }
   }

   fun createNoTestsFoundXml(testProject: Project, testDir: File)
   {
      testProject.mkdir(testDir)
      val noTestsFoundFile = testDir.resolve("TEST-us.ihmc.NoTestsFoundTest.xml")
      project.logger.info("[ihmc-ci] No tests found. Writing $noTestsFoundFile")
      noTestsFoundFile.writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                  "<testsuite name=\"us.ihmc.NoTestsFoundTest\" tests=\"1\" skipped=\"0\" failures=\"0\" " +
                  "errors=\"0\" timestamp=\"2018-10-19T15:10:58\" hostname=\"duncan-ihmc\" time=\"0.01\">" +
                  "<properties/>" +
                  "<testcase name=\"noTestsFoundTest\" classname=\"us.ihmc.NoTestsFoundTest\" time=\"0.01\"/>" +
                  "<system-out>This is a phony test to make Bamboo pass when a project does not contain any tests.</system-out>" +
                  "<system-err><![CDATA[]]></system-err>" +
                  "</testsuite>")
   }

   fun findAllocationJVMArg(): String
   {
      if (allocationJVMArg == null) // search only once
      {
         for (testProject in testProjects(project))
         {
            testProject.configurations.getByName("compile").files.forEach {
               if (it.name.contains("java-allocation-instrumenter"))
               {
                  allocationJVMArg = "-javaagent:" + it.getAbsolutePath()
                  println("[ihmc-ci] Found allocation JVM arg: " + allocationJVMArg)
               }
            }
         }
         if (allocationJVMArg == null) // error out, because user needs to add it
         {
            throw GradleException("[ihmc-ci] Cannot find `java-allocation-instrumenter` on test classpath. Please add it to your test dependencies!")
         }
      }

      return allocationJVMArg!!
   }

   fun loadProperties()
   {
      project.properties["cpuThreads"].run { if (this != null) cpuThreads = (this as String).toInt() }
      project.properties["category"].run { if (this != null) category = (this as String).trim().toLowerCase() }
      project.properties["vintageMode"].run { if (this != null) vintageMode = (this as String).trim().toLowerCase().toBoolean() }
      project.properties["ciBackendHost"].run { if (this != null) ciBackendHost = (this as String).trim() }
      project.logger.info("[ihmc-ci] cpuThreads = $cpuThreads")
      project.logger.info("[ihmc-ci] category = $category")
      project.logger.info("[ihmc-ci] vintageMode = $vintageMode")
   }

   fun configureDefaultCategories()
   {
      categoriesExtension.create("fast") {
         // defaults
      }
      categoriesExtension.create("allocation") {
         maxParallelTests = 1
         includeTags += "allocation"
         jvmArguments += ALLOCATION_AGENT_KEY
      }
   }

   fun testProjects(project: Project): List<Project>
   {
      val testProjects = arrayListOf<Project>()
      for (allproject in project.allprojects)
      {
         if (allproject.name.endsWith("-test"))
         {
            testProjects += allproject
         }
      }
      return testProjects
   }

   fun containsIHMCTestMultiProject(project: Project): Boolean
   {
      for (allproject in project.allprojects)
      {
         if (allproject.name.endsWith("-test"))
         {
            return true
         }
      }
      return false
   }
}
