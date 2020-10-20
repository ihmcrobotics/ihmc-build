package us.ihmc.ci

class JUnitExtension(val jupiterVersion: String,
                     val platformVersion: String,
                     val vintageVersion: String)
{
   fun jupiterApi(): String = "org.junit.jupiter:junit-jupiter-api:$jupiterVersion"
   fun jupiterEngine(): String = "org.junit.jupiter:junit-jupiter-engine:$jupiterVersion"
   fun platformCommons(): String = "org.junit.platform:junit-platform-commons:$platformVersion"
   fun platformLauncher(): String = "org.junit.platform:junit-platform-launcher:$platformVersion"
   fun vintage(): String = "junit:junit:$vintageVersion"
}