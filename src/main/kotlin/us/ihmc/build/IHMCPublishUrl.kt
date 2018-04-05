package us.ihmc.build

class IHMCPublishUrl(val url: String, val username: String, val password: String)
{
   fun hasCredentials(): Boolean
   {
      return username.isEmpty() || password.isEmpty()
   }
}