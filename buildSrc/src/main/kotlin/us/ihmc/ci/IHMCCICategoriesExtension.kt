package us.ihmc.ci

import org.gradle.api.Project

val ALLOCATION_AGENT_KEY = "allocationAgent"

class IHMCCICategory(val name: String)
{
   var forkEvery = 0 // no limit
   var maxParallelForks = 2 // cost of spawning too many is high, but doubling is worth it
   var maxParallelTests = 1   // doesn't work right now with Gradle's test runner. See: https://github.com/gradle/gradle/issues/6453
   val excludeTags = hashSetOf<String>()
   val includeTags = hashSetOf<String>()
   val jvmProperties = hashMapOf<String, String>()
   val jvmArguments = arrayListOf<String>()
   var minHeapSizeGB = 1
   var maxHeapSizeGB = 4
   var enableAssertions = true
}

open class IHMCCICategoriesExtension(private val project: Project)
{
   val categories = hashMapOf<String, IHMCCICategory>()

   fun create(name: String, configuration: IHMCCICategory.() -> Unit)
   {
      val category = IHMCCICategory(name)
      configuration.invoke(category)
      categories.put(name, category)
   }

   fun create(name: String): IHMCCICategory
   {
      val category = IHMCCICategory(name)
      categories.put(name, category)
      return category
   }

   fun get(name: String): IHMCCICategory
   {
      return categories.get(name)!!
   }
}