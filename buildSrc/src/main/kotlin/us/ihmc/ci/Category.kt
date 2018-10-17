package us.ihmc.ci

import org.gradle.api.GradleException
import org.gradle.api.Project

class Category (val name: String, private val project: Project)
{
   var classesPerJVM = 1
   var maxJVMs = 2
   var maxParallelTests = 4
   val excludeTags = arrayListOf("all")
   val includeTags = arrayListOf<String>()
   val jvmArgs = arrayListOf<String>()
   var minHeapSizeGB = 1
   var maxHeapSizeGB = 4

   fun getScsDefaultJVMArgs(): Array<String>
   {
      return arrayOf("-Dcreate.scs.gui=false",
                     "-Dshow.scs.windows=false",
                     "-Dshow.scs.yographics=false",
                     "-Djava.awt.headless=true",
                     "-Dcreate.videos=false",
                     "-Dopenh264.license=accept",
                     "-Ddisable.joint.subsystem.publisher=true",
                     "-Dscs.dataBuffer.size=8142"
      )
   }

   fun getAllocationAgentJVMArg(): String
   {
      project.project(project.name + "-test").configurations.getByName("compile").files.forEach {
         if (it.name.contains("java-allocation-instrumenter"))
         {
            val allocationJVMArg = "-javaagent:" + it.getAbsolutePath()
            println("[ihmc-ci] Found allocation JVM arg: " + allocationJVMArg)
            return allocationJVMArg
         }
      }

      throw GradleException("[ihmc-ci] Cannot find `java-allocation-instrumenter` on test classpath. Please add it to your test dependencies!")
   }
}