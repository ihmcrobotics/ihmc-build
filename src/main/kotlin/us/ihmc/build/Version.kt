package us.ihmc.build

class Version(private val rawVersion: String) : Comparable<Version>
{
   private val version = rawVersion.trim().split("-").get(0)

   init {
      if (version.isBlank() || !version.matches("[0-9]+(\\.[0-9]+)*".toRegex()))
         throw IllegalArgumentException("Invalid version format: $version")
   }

   fun get(): String
   {
      return this.version
   }

   override operator fun compareTo(that: Version): Int
   {
      if (that == null)
         return 1
      val thisParts = this.get().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val thatParts = that.get().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val length = Math.max(thisParts.size, thatParts.size)
      for (i in 0 until length)
      {
         val thisPart = if (i < thisParts.size)
            Integer.parseInt(thisParts[i])
         else
            0
         val thatPart = if (i < thatParts.size)
            Integer.parseInt(thatParts[i])
         else
            0
         if (thisPart < thatPart)
            return -1
         if (thisPart > thatPart)
            return 1
      }
      return 0
   }

   override fun equals(that: Any?): Boolean
   {
      if (this === that)
         return true
      if (that == null)
         return false
      return if (this.javaClass != that.javaClass) false else this.compareTo(that as Version) == 0
   }
}