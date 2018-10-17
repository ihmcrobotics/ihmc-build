package us.ihmc.ci

class Category (val name: String)
{
   var classesPerJVM = 1
   var maxJVMs = 2
   var maxParallelTests = 4
   var excludeTags = arrayListOf("all")
   var includeTags = arrayListOf<String>()
   val jvmArgs = arrayListOf<String>()
   var minHeapSizeGB = 1
   var maxHeapSizeGB = 4
}