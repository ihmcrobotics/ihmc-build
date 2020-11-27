package us.ihmc.ci;

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File

lateinit var LogTools: IHMCCILogTools

class IHMCCIPlugin : Plugin<Project>
{
   val JUNIT_VERSION = "5.7.0"
   val PLATFORM_VERSION = "1.7.0"
   val ALLOCATION_INSTRUMENTER_VERSION = "3.3.0"
   val VINTAGE_VERSION = "4.13.1"

   lateinit var project: Project
   var cpuThreads = 8
   var category: String = "fast"
   object Unset
   var minHeapSizeGBOverride: Any = Unset
   var maxHeapSizeGBOverride: Any = Unset
   var forkEveryOverride: Any = Unset
   var maxParallelForksOverride: Any = Unset
   var enableAssertionsOverride: Any = Unset
   var allocationRecordingOverride: Any = Unset
   var vintageMode: Boolean = false
   var vintageSuite: String? = null
   var ciBackendHost: String = "unset"
   lateinit var categoriesExtension: IHMCCICategoriesExtension
   var allocationJVMArg: String? = null
   val apiConfigurationName = "api"
   val runtimeConfigurationName = "runtimeOnly"
   val addedDependenciesMap = HashMap<String, Boolean>()
   var registeredCIServerSyncTask = false
   var configuredTestTasks = HashMap<String, Boolean>()
   val testProjects = lazy {
      val testProjects = arrayListOf<Project>()
      for (allproject in project.allprojects)
      {
         if (allproject.name.endsWith("-test"))
         {
            testProjects += allproject
         }
      }
      testProjects
   }
   val testsToTagsMap = lazy {
      val map = hashMapOf<String, HashSet<String>>()
      testProjects.value.forEach {
         TagParser.parseForTags(it, map)
      }
      map
   }
   private val junit = JUnitExtension(JUNIT_VERSION, PLATFORM_VERSION, VINTAGE_VERSION)
   private val allocation = AllocationInstrumenter(ALLOCATION_INSTRUMENTER_VERSION)

   override fun apply(project: Project)
   {
      this.project = project
      LogTools = IHMCCILogTools(project.logger)

      loadProperties()
      categoriesExtension = project.extensions.create("categories", IHMCCICategoriesExtension::class.java, project)
      project.extensions.add("junit", junit)
      project.extensions.add("allocation", allocation)

      // These things must be configured later in the build lifecycle.
      // Here, we are notified when any task is added to the build.
      // This happens a lot, so must check if requirements are met
      // and gate it with a boolean.
      project.tasks.whenTaskAdded {
         for (testProject in testProjects.value)
         {
            addDependencies(testProject, apiConfigurationName, runtimeConfigurationName)
            configureTestTask(testProject)
         }
         if (!containsIHMCTestMultiProject(project))
         {
            addDependencies(project, "testImplementation", "testRuntimeOnly")
            configureTestTask(project)
         }

         var allHaveCompileJava = true
         testProjects.value.forEach { testProject ->
            allHaveCompileJava = allHaveCompileJava && testProject.tasks.findByPath("compileJava") != null
         }
         if (!registeredCIServerSyncTask && allHaveCompileJava)
         {
            registeredCIServerSyncTask = true
            project.tasks.register("ciServerSync") {
               LogTools.info("Configuring ciServerSync task")
               configureCIServerSyncTask(testsToTagsMap, testProjects, ciBackendHost)
            }
         }
      }
   }

   private fun addDependencies(project: Project, apiConfigurationName: String, runtimeConfigurationName: String)
   {
      addedDependenciesMap.computeIfAbsent("${project.name}:$apiConfigurationName") { false }
      addedDependenciesMap.computeIfAbsent("${project.name}:$runtimeConfigurationName") { false }

      // add runtime dependencies
      if (!addedDependenciesMap["${project.name}:$runtimeConfigurationName"]!! && configurationExists(project, runtimeConfigurationName))
      {
         addedDependenciesMap["${project.name}:$runtimeConfigurationName"] = true
         if (vintageMode)
         {
            LogTools.info("Adding JUnit 4 dependency to $runtimeConfigurationName in ${project.name}")
            project.dependencies.add(runtimeConfigurationName, junit.vintage())
         }
         else
         {
            LogTools.info("Adding JUnit 5 dependencies to $runtimeConfigurationName in ${project.name}")
            project.dependencies.add(runtimeConfigurationName, junit.jupiterEngine())
         }
      }

      // add api dependencies
      if (!addedDependenciesMap["${project.name}:$apiConfigurationName"]!! && configurationExists(project, apiConfigurationName))
      {
         addedDependenciesMap["${project.name}:$apiConfigurationName"] = true
         if (!vintageMode) // add junit 5 dependencies
         {
            LogTools.info("Adding JUnit 5 dependencies to $apiConfigurationName in ${project.name}")
            project.dependencies.add(apiConfigurationName, junit.jupiterApi())
            project.dependencies.add(apiConfigurationName, junit.platformCommons())
            project.dependencies.add(apiConfigurationName, junit.platformLauncher())

         }

         if (category == "allocation") // help out users trying to run allocation tests
         {
            LogTools.info("Adding allocation intrumenter dependency to $apiConfigurationName in ${project.name}")
            project.dependencies.add(apiConfigurationName, allocation.instrumenter())
         }
      }
   }

   private fun configurationExists(project: Project, name: String): Boolean
   {
      for (configuration in project.configurations)
      {
         if (configuration.name == name)
         {
            return true
         }
      }
      return false
   }

   fun configureTestTask(project: Project)
   {
      configuredTestTasks.computeIfAbsent(project.name) { false }

      if (!configuredTestTasks[project.name]!! && project.tasks.findByName("test") != null)
      {
         configuredTestTasks[project.name] = true
         val addPhonyTestXmlTask = addPhonyTestXmlTask(project)
         project.tasks.named("test", Test::class.java) {
            doFirst {
               // create a default category if not found
               val categoryConfig = postProcessCategoryConfig()
               applyCategoryConfigToGradleTest(this as Test, categoryConfig, project)
            }
            finalizedBy(addPhonyTestXmlTask)
         }
      }
   }

   fun applyCategoryConfigToGradleTest(test: Test, categoryConfig: IHMCCICategory, project: Project)
   {
      categoryConfig.doFirst.invoke()

      if (vintageMode)
      {
         test.useJUnit()

         if (vintageSuite != null)
         {
            val includeString = "**/${vintageSuite}TestSuite.class"
            LogTools.info("Including JUnit 4 classes: $includeString")
            test.include(includeString)
         }
      }
      else
      {
         test.useJUnitPlatform {
            for (tag in categoryConfig.includeTags)
            {
               this.includeTags(tag)
            }
            for (tag in categoryConfig.excludeTags)
            {
               this.excludeTags(tag)
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
                     if (tag != "fast") // this allows @Tag("fast") to be used
                     {
                        this.excludeTags(tag)
                     }
                  }
               }
            }
         }
      }
      test.setForkEvery(categoryConfig.forkEvery.toLong())
      test.maxParallelForks = categoryConfig.maxParallelForks
      this.project.properties["runningOnCIServer"].run {
         if (this != null)
            test.systemProperties["runningOnCIServer"] = toString()
      }
      for (jvmProp in categoryConfig.jvmProperties)
      {
         test.systemProperties[jvmProp.key] = jvmProp.value
      }

      if (categoryConfig.junit5ParallelEnabled)
      {
         test.systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
         test.systemProperties["junit.jupiter.execution.parallel.config.strategy"] = categoryConfig.junit5ParallelStrategy
         test.systemProperties["junit.jupiter.execution.parallel.config.fixed.parallelism"] = categoryConfig.junit5ParallelFixedParallelism
      }

      val java = project.convention.getPlugin(JavaPluginConvention::class.java)
      val resourcesDir = java.sourceSets.getByName("main").output.resourcesDir
      LogTools.info("Passing to JVM: -Dresource.dir=$resourcesDir")
      test.systemProperties["resource.dir"] = resourcesDir

      for (jvmArg in categoryConfig.jvmArguments)
      {
         if (jvmArg == ALLOCATION_AGENT_KEY)
         {
            test.jvmArgs(findAllocationJVMArg())
         }
         else
         {
            test.jvmArgs(jvmArg)
         }
      }
      if (categoryConfig.enableAssertions)
      {
         LogTools.info("Assertions enabled. Adding JVM arg: -ea")
         test.enableAssertions = true
      }
      else
      {
         LogTools.info("Assertions disabled")
         test.enableAssertions = false
      }

      test.minHeapSize = "${categoryConfig.minHeapSizeGB}g"
      test.maxHeapSize = "${categoryConfig.maxHeapSizeGB}g"

      test.testLogging.info.events = setOf(TestLogEvent.STARTED,
                                           TestLogEvent.FAILED,
                                           TestLogEvent.PASSED,
                                           TestLogEvent.SKIPPED,
                                           TestLogEvent.STANDARD_ERROR,
                                           TestLogEvent.STANDARD_OUT)

      LogTools.info("test.forkEvery = ${test.forkEvery}")
      LogTools.info("test.maxParallelForks = ${test.maxParallelForks}")
      LogTools.info("test.systemProperties = ${test.systemProperties}")
      LogTools.info("test.allJvmArgs = ${test.allJvmArgs}")
      LogTools.info("test.minHeapSize = ${test.minHeapSize}")
      LogTools.info("test.maxHeapSize = ${test.maxHeapSize}")
   }

   fun postProcessCategoryConfig(): IHMCCICategory
   {
      val categoryConfig = categoriesExtension.configure(category)

      if (categoryConfig.name == "fast")  // fast runs all "untagged" tests, so exclude all found tags
      {
         testsToTagsMap.value.forEach {
            it.value.forEach {
               if (it != "fast")
               {
                  categoryConfig.excludeTags.add(it)
               }
            }
         }
         categoryConfig.includeTags.clear()  // include is a whitelist, so must clear it
      }
      minHeapSizeGBOverride.run { if (this is Int) categoryConfig.minHeapSizeGB = this }
      maxHeapSizeGBOverride.run { if (this is Int) categoryConfig.maxHeapSizeGB = this }
      forkEveryOverride.run { if (this is Int) categoryConfig.forkEvery = this }
      maxParallelForksOverride.run { if (this is Int) categoryConfig.maxParallelForks = this }
      enableAssertionsOverride.run { if (this is Boolean) categoryConfig.enableAssertions = this }
      allocationRecordingOverride.run { if (this is Boolean && this) categoryConfig.jvmArguments += ALLOCATION_AGENT_KEY }

      LogTools.info("${categoryConfig.name}.forkEvery = ${categoryConfig.forkEvery}")
      LogTools.info("${categoryConfig.name}.maxParallelForks = ${categoryConfig.maxParallelForks}")
      LogTools.info("${categoryConfig.name}.excludeTags = ${categoryConfig.excludeTags}")
      LogTools.info("${categoryConfig.name}.includeTags = ${categoryConfig.includeTags}")
      LogTools.info("${categoryConfig.name}.jvmProperties = ${categoryConfig.jvmProperties}")
      LogTools.info("${categoryConfig.name}.jvmArguments = ${categoryConfig.jvmArguments}")
      LogTools.info("${categoryConfig.name}.minHeapSizeGB = ${categoryConfig.minHeapSizeGB}")
      LogTools.info("${categoryConfig.name}.maxHeapSizeGB = ${categoryConfig.maxHeapSizeGB}")
      LogTools.info("${categoryConfig.name}.enableAssertions = ${categoryConfig.enableAssertions}")
      LogTools.info("${categoryConfig.name}.allocationRecording = ${categoryConfig.jvmArguments}")

      // List tests to be run
      LogTools.quiet("Tests to be run:")
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
         this.doLast {
            var testsFound = false
            for (path in anyproject.rootDir.walkBottomUp())
            {
               if (path.toPath().toAbsolutePath().toString().matches(Regex(".*/test-results/test/.*\\.xml")))
               {
                  LogTools.info("Found test file: $path")
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
      LogTools.info("No tests found. Writing $noTestsFoundFile")
      noTestsFoundFile.writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                  "<testsuite name=\"us.ihmc.NoTestsFoundTest\" tests=\"1\" skipped=\"0\" failures=\"0\" " +
                  "errors=\"0\" timestamp=\"2018-10-19T15:10:58\" hostname=\"duncan-ihmc\" time=\"0.01\">" +
                  "<properties/>" +
                  "<testcase name=\"noTestsFoundTest\" classname=\"us.ihmc.NoTestsFoundTest\" time=\"0.01\"/>" +
                  "<system-out>This is a phony test to make CI builds pass when a project does not contain any tests.</system-out>" +
                  "<system-err><![CDATA[]]></system-err>" +
                  "</testsuite>")
   }

   fun findAllocationJVMArg(): String
   {
      if (allocationJVMArg == null) // search only once
      {
         for (testProject in testProjects.value)
         {
            testProject.configurations.getByName("runtimeClasspath").files.forEach {
               if (it.name.contains("java-allocation-instrumenter"))
               {
                  allocationJVMArg = "-javaagent:" + it.getAbsolutePath()
                  LogTools.info("Found allocation JVM arg: $allocationJVMArg")
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
      project.properties["vintageSuite"].run { if (this != null) vintageSuite = (this as String).trim() }
      project.properties["ciBackendHost"].run { if (this != null) ciBackendHost = (this as String).trim() }
      project.properties["minHeapSizeGB"].run { if (this != null) minHeapSizeGBOverride = (this as String).toInt() }
      project.properties["maxHeapSizeGB"].run { if (this != null) maxHeapSizeGBOverride = (this as String).toInt() }
      project.properties["forkEvery"].run { if (this != null) forkEveryOverride = (this as String).toInt() }
      project.properties["maxParallelForks"].run { if (this != null) maxParallelForksOverride = (this as String).toInt() }
      project.properties["enableAssertions"].run { if (this != null) enableAssertionsOverride = (this as String).toBoolean() }
      project.properties["allocationRecording"].run { if (this != null) allocationRecordingOverride = (this as String).toBoolean() }
      LogTools.info("cpuThreads = $cpuThreads")
      LogTools.info("category = $category")
      LogTools.info("vintageMode = $vintageMode")
      LogTools.info("vintageSuite = $vintageSuite")
      LogTools.info("minHeapSizeGB = ${unsetPrintFilter(minHeapSizeGBOverride)}")
      LogTools.info("maxHeapSizeGB = ${unsetPrintFilter(maxHeapSizeGBOverride)}")
      LogTools.info("forkEvery = ${unsetPrintFilter(forkEveryOverride)}")
      LogTools.info("maxParallelForks = ${unsetPrintFilter(maxParallelForksOverride)}")
      LogTools.info("enableAssertions = ${unsetPrintFilter(enableAssertionsOverride)}")
      LogTools.info("allocationRecording = ${unsetPrintFilter(allocationRecordingOverride)}")
   }

   private fun unsetPrintFilter(any: Any) = if (any is Unset) "Not set" else any

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
