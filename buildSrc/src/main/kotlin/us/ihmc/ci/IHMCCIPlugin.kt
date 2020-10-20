package us.ihmc.ci;

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType
import java.io.File

lateinit var LogTools: Logger

class IHMCCIPlugin : Plugin<Project>
{
   val JUNIT_VERSION = "5.5.1"
   val PLATFORM_VERSION = "1.5.1"
   val ALLOCATION_INSTRUMENTER_VERSION = "3.2.0"
   val VINTAGE_VERSION = "4.12"

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
      LogTools = project.logger

      loadProperties()
      categoriesExtension = project.extensions.create("categories", IHMCCICategoriesExtension::class.java, project)
      project.extensions.add("junit", junit)
      project.extensions.add("allocation", allocation)

//      project.tasks.create("addTestDependencies") {
//
//         val java = project.convention.getPlugin(JavaPluginConvention::class.java)
//      }

      project.tasks.whenTaskAdded {
         LogTools.quiet("Task was added!")
         println("Task added: #configurations: " + project.configurations.size)

         for (testProject in testProjects.value)
         {
            addDependencies(testProject, apiConfigurationName, runtimeConfigurationName)
         }
         if (!containsIHMCTestMultiProject(project))
         {
            addDependencies(project, "testImplementation", "testRuntimeOnly")
         }
      }

      return;

//      addTestDependencies()
      configureTestTask()

      // register ciServerSync task
      CIServerSyncTask.configureTask(testsToTagsMap,
                                     testProjects,
                                     ciBackendHost).invoke(project.getOrCreate("ciServerSync"))
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
            LogTools.info("[ihmc-ci] Adding JUnit 4 dependency to $runtimeConfigurationName in ${project.name}")
            project.dependencies.add(runtimeConfigurationName, junit.vintage())
         }
         else
         {
            LogTools.info("[ihmc-ci] Adding JUnit 5 dependencies to $runtimeConfigurationName in ${project.name}")
            project.dependencies.add(runtimeConfigurationName, junit.jupiterEngine())
         }
      }

      // add api dependencies
      if (!addedDependenciesMap["${project.name}:$apiConfigurationName"]!! && configurationExists(project, apiConfigurationName))
      {
         addedDependenciesMap["${project.name}:$apiConfigurationName"] = true
         if (!vintageMode) // add junit 5 dependencies
         {
            LogTools.info("[ihmc-ci] Adding JUnit 5 dependencies to $apiConfigurationName in ${project.name}")
            project.dependencies.add(apiConfigurationName, junit.jupiterApi())
            project.dependencies.add(apiConfigurationName, junit.platformCommons())
            project.dependencies.add(apiConfigurationName, junit.platformLauncher())

         }

         if (category == "allocation") // help out users trying to run allocation tests
         {
            LogTools.info("[ihmc-ci] Adding allocation intrumenter dependency to $apiConfigurationName in ${project.name}")
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

   private fun Project.getOrCreate(taskName: String): Task
   {
      return tasks.findByName(taskName) ?: tasks.create(taskName)
   }

   fun configureTestTask()
   {
      for (testProject in testProjects.value)
      {
         configureTestTask(testProject)
      }
      // special case when a project does not use ihmc-build or doesn't declare a multi-project ending with "-test"
      // yes, some projects don't have any tests, but why would they use this plugin? so not checking for test code
      if (!containsIHMCTestMultiProject(project))
      {
         configureTestTask(project)
      }
   }

   fun configureTestTask(project: Project)
   {
      project.tasks.withType<Test>()
      {
         doFirst {
            // create a default category if not found
            val categoryConfig = postProcessCategoryConfig()
            applyCategoryConfigToGradleTest(this as Test, categoryConfig, project)
         }
         finalizedBy(addPhonyTestXmlTask(project))
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
            this.project.logger.info("[ihmc-ci] Including JUnit 4 classes: $includeString")
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
      this.project.logger.info("[ihmc-ci] Passing to JVM: -Dresource.dir=" + resourcesDir)
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
         this.project.logger.info("[ihmc-ci] Assertions enabled. Adding JVM arg: -ea")
         test.enableAssertions = true
      }
      else
      {
         this.project.logger.info("[ihmc-ci] Assertions disabled")
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

      this.project.logger.info("[ihmc-ci] test.forkEvery = ${test.forkEvery}")
      this.project.logger.info("[ihmc-ci] test.maxParallelForks = ${test.maxParallelForks}")
      this.project.logger.info("[ihmc-ci] test.systemProperties = ${test.systemProperties}")
      this.project.logger.info("[ihmc-ci] test.allJvmArgs = ${test.allJvmArgs}")
      this.project.logger.info("[ihmc-ci] test.minHeapSize = ${test.minHeapSize}")
      this.project.logger.info("[ihmc-ci] test.maxHeapSize = ${test.maxHeapSize}")
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

      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.forkEvery = ${categoryConfig.forkEvery}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.maxParallelForks = ${categoryConfig.maxParallelForks}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.excludeTags = ${categoryConfig.excludeTags}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.includeTags = ${categoryConfig.includeTags}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.jvmProperties = ${categoryConfig.jvmProperties}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.jvmArguments = ${categoryConfig.jvmArguments}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.minHeapSizeGB = ${categoryConfig.minHeapSizeGB}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.maxHeapSizeGB = ${categoryConfig.maxHeapSizeGB}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.enableAssertions = ${categoryConfig.enableAssertions}")
      this.project.logger.info("[ihmc-ci] ${categoryConfig.name}.allocationRecording = ${categoryConfig.jvmArguments}")

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
         this.doLast {
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
      project.properties["vintageSuite"].run { if (this != null) vintageSuite = (this as String).trim() }
      project.properties["ciBackendHost"].run { if (this != null) ciBackendHost = (this as String).trim() }
      project.properties["minHeapSizeGB"].run { if (this != null) minHeapSizeGBOverride = (this as String).toInt() }
      project.properties["maxHeapSizeGB"].run { if (this != null) maxHeapSizeGBOverride = (this as String).toInt() }
      project.properties["forkEvery"].run { if (this != null) forkEveryOverride = (this as String).toInt() }
      project.properties["maxParallelForks"].run { if (this != null) maxParallelForksOverride = (this as String).toInt() }
      project.properties["enableAssertions"].run { if (this != null) enableAssertionsOverride = (this as String).toBoolean() }
      project.properties["allocationRecording"].run { if (this != null) allocationRecordingOverride = (this as String).toBoolean() }
      project.logger.info("[ihmc-ci] cpuThreads = $cpuThreads")
      project.logger.info("[ihmc-ci] category = $category")
      project.logger.info("[ihmc-ci] vintageMode = $vintageMode")
      project.logger.info("[ihmc-ci] vintageSuite = $vintageSuite")
      project.logger.info("[ihmc-ci] minHeapSizeGB = ${unsetPrintFilter(minHeapSizeGBOverride)}")
      project.logger.info("[ihmc-ci] maxHeapSizeGB = ${unsetPrintFilter(maxHeapSizeGBOverride)}")
      project.logger.info("[ihmc-ci] forkEvery = ${unsetPrintFilter(forkEveryOverride)}")
      project.logger.info("[ihmc-ci] maxParallelForks = ${unsetPrintFilter(maxParallelForksOverride)}")
      project.logger.info("[ihmc-ci] enableAssertions = ${unsetPrintFilter(enableAssertionsOverride)}")
      project.logger.info("[ihmc-ci] allocationRecording = ${unsetPrintFilter(allocationRecordingOverride)}")
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
