package us.ihmc.ci

import org.gradle.api.GradleException
import org.gradle.api.Project

val ALLOCATION_AGENT_KEY = "allocationAgent"

class IHMCCICategory(val name: String, private val project: Project)
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

   fun getScsDefaultJVMProps(): Map<String, String>
   {
      return mapOf("create.scs.gui" to "false",
                   "show.scs.windows" to "false",
                   "show.scs.yographics" to "false",
                   "java.awt.headless" to "true",
                   "create.videos" to "false",
                   "openh264.license" to "accept",
                   "disable.joint.subsystem.publisher" to "true",
                   "scs.dataBuffer.size" to "8142")
   }

   fun getAllocationAgentJVMArg(): String
   {
      return ALLOCATION_AGENT_KEY
   }
}