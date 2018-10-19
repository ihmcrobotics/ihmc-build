package us.ihmc.ci;

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.testing.Test
import java.io.File

class IHMCCIPlugin : Plugin<Project>
{
   val JUNIT_VERSION = "5.3.1"

   lateinit var project: Project
   var cpuThreads = 8
   var category: String = "fast"
   lateinit var categoriesExtension: IHMCCICategoriesExtension
   var allocationJVMArg: String? = null
   var noTestsFoundFileCreated = false

   override fun apply(project: Project)
   {
      this.project = project

      loadProperties()
      categoriesExtension = project.extensions.create("categories", IHMCCICategoriesExtension::class.java, project)
      configureDefaultCategories()

      for (testProject in testProjects(project))
      {
         // add junit 5 dependencies
         testProject.dependencies.add("compile", "org.junit.jupiter:junit-jupiter-api:$JUNIT_VERSION")
         testProject.dependencies.add("runtimeOnly", "org.junit.jupiter:junit-jupiter-engine:$JUNIT_VERSION")
         testProject.dependencies.add("runtimeOnly", "org.junit.vintage:junit-vintage-engine:$JUNIT_VERSION")
         if (category == "allocation") // help out users trying to run allocation tests
            testProject.dependencies.add("compile", "com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.1.0")

         testProject.tasks.withType(Test::class.java) { test ->
            test.doFirst {
               val categoryConfig = categoriesExtension.categories[category] // setup category
               if (categoryConfig != null)
               {
                  configureTestTask(testProject, test, categoryConfig)
               }
               else
               {
                  throw GradleException("[ihmc-ci] Category $category is not defined! Define it in a categories.create(..) { } block.")
               }
            }
            test.doLast {
               // check for build/test-rseults/*.xml, if none, make empty test result
               val testDir = testProject.buildDir.resolve("test-results/test")
               if (!testDir.exists() || !containsXml(testProject))
               {
                  // there are no test results, make one
                  createNoTestsFoundXml(testProject, testDir)
               }
            }
         }
      }

      // special case when a project does not have a test source set
      if (!containsIHMCTestMultiProject(project))
      {
         // add junit 5 dependencies
         project.dependencies.add("testCompile", "org.junit.jupiter:junit-jupiter-api:$JUNIT_VERSION")
         project.dependencies.add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:$JUNIT_VERSION")
         project.dependencies.add("testRuntimeOnly", "org.junit.vintage:junit-vintage-engine:$JUNIT_VERSION")
         if (category == "allocation") // help out users trying to run allocation tests
            project.dependencies.add("testCompile", "com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.1.0")

         project.tasks.withType(Test::class.java) { test ->
            test.doFirst {
               val categoryConfig = categoriesExtension.categories[category] // setup category
               if (categoryConfig != null)
               {
                  configureTestTask(project, test, categoryConfig)
               }
               else
               {
                  throw GradleException("[ihmc-ci] Category $category is not defined! Define it in a categories.create(..) { } block.")
               }
            }
            test.doLast {
               // check for build/test-rseults/*.xml, if none, make empty test result
               val testDir = project.buildDir.resolve("test-results/test")
               if (!testDir.exists() || !containsXml(project))
               {
                  // there are no test results, make one
                  createNoTestsFoundXml(project, testDir)
               }
            }
         }
      }
   }

   fun createNoTestsFoundXml(testProject: Project, testDir: File)
   {
      if (!noTestsFoundFileCreated)
      {
         noTestsFoundFileCreated = true
         testProject.mkdir(testDir)
         val noTestsFoundFile = testDir.resolve("TEST-us.ihmc.NoTestsFoundTest.xml")
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
   }

   fun containsXml(testProject: Project): Boolean
   {
      testProject.buildDir.resolve("test-results/test").listFiles().forEach { entry ->
         if (entry.isFile && entry.name.endsWith(".xml"))
         {
            return true
         }
      }
      return false
   }

   fun configureTestTask(testProject: Project, test: Test, categoryConfig: IHMCCICategory)
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

      project.properties["runningOnCIServer"].run {
         if (this != null)
            test.systemProperties["runningOnCIServer"] = this.toString()
      }
      for (jvmProp in categoryConfig.jvmProperties)
      {
         test.systemProperties[jvmProp.key] = jvmProp.value
      }

      test.systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
      test.systemProperties["junit.jupiter.execution.parallel.config.strategy"] = "fixed"
      test.systemProperties["junit.jupiter.execution.parallel.config.fixed.parallelism"] = categoryConfig.maxParallelTests.toString()

      // add resources dir JVM property
      val java = testProject.convention.getPlugin(JavaPluginConvention::class.java)
      val resourcesDir = java.sourceSets.getByName("main").output.resourcesDir
      project.logger.info("[ihmc-ci] Passing to JVM: -Dresource.dir=" + resourcesDir)
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

      test.minHeapSize = "${categoryConfig.minHeapSizeGB}g"
      test.maxHeapSize = "${categoryConfig.maxHeapSizeGB}g"
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
      project.logger.info("[ihmc-ci] cpuThreads = $cpuThreads")
      project.logger.info("[ihmc-ci] category = $category")
   }

   fun configureDefaultCategories()
   {
      categoriesExtension.create("fast") {
         // defaults
      }
      categoriesExtension.create("allocation") {
         maxParallelTests = 1
         includeTags += "allocation"
         jvmArguments += getAllocationAgentJVMArg()
      }
      categoriesExtension.create("scs") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags += "scs"
         jvmProperties.putAll(getScsDefaultJVMProps())
         minHeapSizeGB = 6
         maxHeapSizeGB = 8
      }
      categoriesExtension.create("video") {
         classesPerJVM = 1
         maxJVMs = 2
         maxParallelTests = 1
         includeTags += "video"
         jvmProperties["create.scs.gui"] = "true"
         jvmProperties["show.scs.windows"] = "true"
         jvmProperties["create.videos.dir"] = "/home/shadylady/bamboo-videos/"
         jvmProperties["show.scs.yographics"] = "true"
         jvmProperties["java.awt.headless"] = "false"
         jvmProperties["create.videos"] = "true"
         jvmProperties["openh264.license"] = "accept"
         jvmProperties["disable.joint.subsystem.publisher"] = "true"
         jvmProperties["scs.dataBuffer.size"] = "8142"
         minHeapSizeGB = 6
         maxHeapSizeGB = 8
      }
   }
}
