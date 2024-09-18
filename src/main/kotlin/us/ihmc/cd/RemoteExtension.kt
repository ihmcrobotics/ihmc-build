package us.ihmc.cd

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.exec.OS
import org.gradle.api.Action
import us.ihmc.build.LogTools
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

open class RemoteExtension
{
   fun session(address: String, username: String, password: String, action: Action<RemoteConnection>)
   {
      session(address, {sshClient -> sshClient.authPassword(username, password)} , action)
   }

   fun session(address: String, username: String, action: Action<RemoteConnection>)
   {
      session(address, {sshClient -> authWithSSHKey(username, sshClient)} , action)
   }

   /**
    * Replicate OpenSSH functionality where users can name private keys whatever they want.
    */
   private fun authWithSSHKey(username: String, sshClient: SSHClient)
   {
      val userSSHConfigFolder = Paths.get(System.getProperty("user.home")).resolve(".ssh")

      val list = Files.list(userSSHConfigFolder)

      val privateKeyFiles = arrayListOf<String>()
      for (path in list)
      {
         if (Files.isRegularFile(path)
            && path.fileName.toString() != "config"
            && path.fileName.toString() != "known_hosts"
            && !path.fileName.toString().endsWith(".pub"))
         {
            val absoluteNormalizedString = path.toAbsolutePath().normalize().toString()
            privateKeyFiles.add(absoluteNormalizedString)
         }
      }

      LogTools.info("Passing keys to authPublicKey: $privateKeyFiles")

      sshClient.authPublickey(username, *privateKeyFiles.toTypedArray())
   }

   class RemoteConnection(val ssh: SSHClient, val sftp: SFTPClient)
   {
      fun exec(command: String, timeout: Double = 5.0)
      {
         var session: Session? = null
         try
         {
            session = ssh.startSession()

            LogTools.quiet("Executing on ${ssh.remoteHostname}: \"$command\"")
            val sshjCommand = session.exec(command)
            LogTools.quiet(IOUtils.readFully(sshjCommand.inputStream).toString())
            sshjCommand.join((timeout * 1e9).toLong(), TimeUnit.NANOSECONDS)
            LogTools.quiet("** exit status: " + sshjCommand.exitStatus)
         }
         finally
         {
            try
            {
               session?.close()
            }
            catch (e: IOException)
            {
               // do nothing
            }
         }
      }

      fun put(source: String, dest: String)
      {
         LogTools.quiet("Putting $source to ${ssh.remoteHostname}:$dest")
         sftp.put(source, dest)
      }

      fun get(source: String, dest: String)
      {
         LogTools.quiet("Getting ${ssh.remoteHostname}:$source to $dest")
         sftp.get(source, dest)
      }
   }

   fun session(address: String, authenticate: (SSHClient) -> Unit, action: Action<RemoteConnection>)
   {
      val sshClient = SSHClient()

      if (OS.isFamilyUnix())
      {
         sshClient.loadKnownHosts()
      }
      else if (OS.isFamilyWindows())
      {
         // TODO: Intelligently try to find known_hosts location on Windows
         sshClient.addHostKeyVerifier(PromiscuousVerifier())
      }

      sshClient.connect(address)

      try
      {
         authenticate(sshClient)

         val sftpClient: SFTPClient = sshClient.newSFTPClient()
         try
         {
            action.execute(RemoteConnection(sshClient, sftpClient))
         }
         finally
         {
            sftpClient.close()
         }
      }
      finally
      {
         sshClient.disconnect()
      }
   }
}