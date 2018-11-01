package us.ihmc.ci

import com.xebialabs.overthere.*
import com.xebialabs.overthere.ConnectionOptions.*
import com.xebialabs.overthere.OperatingSystemFamily.UNIX
import com.xebialabs.overthere.ssh.SshConnectionBuilder.CONNECTION_TYPE
import com.xebialabs.overthere.ssh.SshConnectionBuilder.SUDO_USERNAME
import com.xebialabs.overthere.ssh.SshConnectionType.INTERACTIVE_SUDO
import com.xebialabs.overthere.ssh.SshConnectionType.SFTP
import us.ihmc.encryptedProperties.EncryptedPropertyManager
import java.io.FileInputStream
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

val credentials = EncryptedPropertyManager.loadEncryptedCredentials()
val properties = GradleProperties()
val ciBackendHost = credentials.get("ciBackendHost")!!
val ciBackendUser = credentials.get("ciBackendUser")!!
val ciBackendPass = credentials.get("ciBackendPass")!!

data class ConnectionFailed(val a: Int)

fun backendConnection(sudo: Boolean): Any
{
   val connection = hostConnection(ciBackendHost, sudo)
   if (connection is ConnectionFailed)
      LogTools.error("$ciBackendHost is offline")
   return connection
}

fun hostConnection(host: String, sudo: Boolean): Any
{
   LogTools.debug("Connecting to $host...")
   val options = ConnectionOptions()
   options.set(ADDRESS, host)
   options.set(USERNAME, ciBackendUser)
   if (sudo)
   {
      options.set(SUDO_USERNAME, "root")
      options.set(CONNECTION_TYPE, INTERACTIVE_SUDO)
      options.set(PASSWORD, ciBackendPass)
   }
   else
   {
      options.set(CONNECTION_TYPE, SFTP)
      options.set(PASSWORD, ciBackendPass)
   }
   options.set(OPERATING_SYSTEM, UNIX)

   try
   {
      return Overthere.getConnection("ssh", options)
   }
   catch (e: RuntimeIOException)
   {
      LogTools.error("Could not connect to $host")
      return ConnectionFailed(0);
   }
}

private class CommandOutputHandler(val handler: (String) -> Unit): OverthereExecutionOutputHandler
{
   override fun handleLine(line: String?)
   {
      handler(line!!)
   }

   override fun handleChar(c: Char)
   {
      // do nothing
   }
}

fun executeCommand(connection: OverthereConnection, command: CmdLine, handler: (String) -> Unit): Int
{
   LogTools.info(connection.options.get<String>(ADDRESS) + " Executing command: $command")
   val returnCode = connection.execute(CommandOutputHandler(handler), CommandOutputHandler(handler), command)
   if (returnCode != 0)
   {
      LogTools.warn("Execution returned code $returnCode: $command")
   }
   return returnCode
}

fun formattedTime(): String
{
   val current = LocalDateTime.now()
   val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
   return current.format(formatter)
}

class GradleProperties()
{
   lateinit var ciBackendCommand: String

   init
   {
      val properties = Properties()
      properties.load(FileInputStream(Paths.get(System.getProperty("user.home")).resolve(".gradle").resolve("gradle.properties").toFile()))
      for (propertyKey in properties.keys)
      {
         if (propertyKey == "ciBackendCommand")
         {
            ciBackendCommand = properties.get(propertyKey)!! as String
         }
      }
   }
}
