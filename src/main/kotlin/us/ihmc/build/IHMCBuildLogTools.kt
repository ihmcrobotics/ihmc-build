package us.ihmc.build

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

class IHMCBuildLogTools(val logger: Logger)
{
   fun quiet(message: Any)
   {
      logger.quiet(ihmcBuildMessage(message))
   }

   fun info(message: Any)
   {
      logger.info(ihmcBuildMessage(message))
   }

   fun warn(message: Any)
   {
      logger.warn(ihmcBuildMessage(message))
   }

   fun error(message: Any)
   {
      logger.error(ihmcBuildMessage(message))
   }

   fun debug(message: Any)
   {
      logger.debug(ihmcBuildMessage(message))
   }

   fun trace(trace: Any)
   {
      logger.trace(trace.toString())
   }

   fun crash(message: Any)
   {
      error(message)
      throw GradleException("[ihmc-build] " + message as String)
   }

   private fun ihmcBuildMessage(message: Any): String
   {
      return "[ihmc-build] " + message
   }
}