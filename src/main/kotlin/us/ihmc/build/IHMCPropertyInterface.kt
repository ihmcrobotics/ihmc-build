package us.ihmc.build

import org.gradle.api.plugins.ExtraPropertiesExtension
import java.io.FileInputStream
import java.nio.file.Path
import java.util.*

class IHMCPropertyInterface
{
   var useGradleExt = false
   var gradleExt: ExtraPropertiesExtension? = null
   var javaUtil: Properties? = null
   
   fun initWithExt(gradleExt: ExtraPropertiesExtension, rootProjectFolderName: String): IHMCPropertyInterface
   {
      this.gradleExt = gradleExt
      useGradleExt = true
      set("rootProjectFolderName", rootProjectFolderName)
      return this
   }
   
   fun initWithPath(propertiesFilePath: Path, rootProjectFolderName: String): IHMCPropertyInterface
   {
      javaUtil = Properties()
      javaUtil!!.load(FileInputStream(propertiesFilePath.toFile()))
      useGradleExt = false
      set("rootProjectFolderName", rootProjectFolderName)
      return this
   }
   
   fun has(propertyName: String): Boolean
   {
      if (useGradleExt)
         return gradleExt!!.has(propertyName)
      else
         return javaUtil!!.containsKey(propertyName)
   }
   
   fun get(propertyName: String): String
   {
      if (useGradleExt)
         return gradleExt!!.get(propertyName) as String
      else
         return javaUtil!!.get(propertyName) as String
   }
   
   fun set(propertyName: String, propertyValue: String)
   {
      if (useGradleExt)
         gradleExt!!.set(propertyName, propertyValue)
      else
         javaUtil!!.set(propertyName, propertyValue)
   }
}