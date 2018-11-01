package us.ihmc.ci

val ALLOCATION_AGENT_KEY = "allocationAgent"

class IHMCCICategory(val name: String)
{
   var classesPerJVM = 0 // no limit
   var maxJVMs = 2
   var maxParallelTests = 4
   val excludeTags = hashSetOf<String>()
   val includeTags = hashSetOf<String>()
   val jvmProperties = hashMapOf<String, String>()
   val jvmArguments = arrayListOf<String>()
   var minHeapSizeGB = 1
   var maxHeapSizeGB = 4

   fun getAllocationAgentJVMArg(): String
   {
      return ALLOCATION_AGENT_KEY
   }
}