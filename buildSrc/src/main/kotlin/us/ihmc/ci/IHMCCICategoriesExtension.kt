package us.ihmc.ci

import org.gradle.api.Project

val ALLOCATION_AGENT_KEY = "allocationAgent"

class IHMCCICategory(val name: String)
{
   var forkEvery = 0 // no limit
   var maxParallelForks = 1 // careful, cost of spawning JVMs is high
   var junit5ParallelEnabled = false   // doesn't work right now with Gradle's test runner. See: https://github.com/gradle/gradle/issues/6453
   var junit5ParallelStrategy = "fixed"
   var junit5ParallelFixedParallelism = 1
   val excludeTags = hashSetOf<String>()
   val includeTags = hashSetOf<String>()
   val jvmProperties = hashMapOf<String, String>()
   val jvmArguments = hashSetOf<String>()
   var minHeapSizeGB = 1
   var maxHeapSizeGB = 4
   var enableAssertions = true
   var doFirst: () -> Unit = {}  // run user code when this category is selected
}

open class IHMCCICategoriesExtension(private val project: Project)
{
   val categories = hashMapOf<String, IHMCCICategory>()

   fun configure(name: String, configuration: IHMCCICategory.() -> Unit)
   {
      configuration.invoke(configure(name))
   }

   fun configure(name: String): IHMCCICategory
   {
      val category = categories.getOrPut(name, { IHMCCICategory(name) })
      if (name != "all" && name != "fast")  // all require no includes or excludes, fast will be configured later
      {
         category.includeTags += name   // by default, include tags of the category name
      }
      if (name == "allocation")
      {
         category.minHeapSizeGB = 2
         category.maxHeapSizeGB = 6
         category.jvmArguments += ALLOCATION_AGENT_KEY
      }
      return category
   }
}