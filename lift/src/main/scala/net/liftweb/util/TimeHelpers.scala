package net.liftweb.util
import _root_.java.text.SimpleDateFormat
import _root_.java.util.{TimeZone, Calendar, Date, Locale}

/**
 * The TimeHelpers object extends the TimeHelpers. It can be imported to access all of the trait functions.
 */
object TimeHelpers extends TimeHelpers with ControlHelpers with ClassHelpers
/**
 * The TimeHelpers trait provide functions to create TimeSpans (an object representing an amount of time), to manage date formats
 * or general utility functions (get the date for today, get year/month/day number,...)
 */
trait TimeHelpers { self: ControlHelpers =>

  /** private variable allowing the access to all TimeHelpers functions from inside the TimeSpan class */
  private val outer = this

  /** transforms a long to a TimeSpanBuilder object. Usage: 3L.seconds returns a TimeSpan of 3000L millis  */
  implicit def longToTimeSpanBuilder(in: long): TimeSpanBuilder = TimeSpanBuilder(in)

  /** transforms an int to a TimeSpanBuilder object. Usage: 3.seconds returns a TimeSpan of 3000L millis  */
  implicit def intToTimeSpanBuilder(in: int): TimeSpanBuilder = TimeSpanBuilder(in)

  /** transforms a long to a TimeSpan object. Usage: 3000L returns a TimeSpan of 3000L millis  */
  implicit def longToTimeSpan(in: long): TimeSpan = TimeSpan(in)

  /** transforms an int to a TimeSpan object. Usage: 3000 returns a TimeSpan of 3000L millis  */
  implicit def intToTimeSpan(in: int): TimeSpan = TimeSpan(in)

  /** class building TimeSpans given an amount (len) and a method specify the time unit  */
  case class TimeSpanBuilder(val len: Long) {
    def seconds = TimeSpan(outer.seconds(len))
    def second = seconds
    def minutes = TimeSpan(outer.minutes(len))
    def minute = minutes
    def hours = TimeSpan(outer.hours(len))
    def hour = hours
    def days = TimeSpan(outer.days(len))
    def day = days
    def weeks = TimeSpan(outer.weeks(len))
    def week = weeks
  }

  /**
   * transforms a TimeSpan to a date by converting the TimeSpan expressed as millis and creating
   * a Date lasting that number of millis from the Epoch time (see the documentation for java.util.Date)
   */
  implicit def timeSpanToDate(in: TimeSpan): Date = in.date

  /** transforms a TimeSpan to its long value as millis */
  implicit def timeSpanToLong(in: TimeSpan): long = in.millis

  /**
   * The TimeSpan class represents an amount of time.
   * It can be translated to a date with the date method. In that case, the number of millis seconds will be used to create a Date
   * object starting from the Epoch time (see the documentation for java.util.Date)
   */
  class TimeSpan(val millis: Long) {
    /** @return a Date as the amount of time represented by the TimeSpan after the Epoch date */
    def date = new Date(millis)

    /** @return a Date as the amount of time represented by the TimeSpan after now */
    def later = TimeSpan(millis + outer.millis).date

    /** @return a Date as the amount of time represented by the TimeSpan before now */
    def ago = TimeSpan(outer.millis - millis).date

    /** @return a TimeSpan representing the addition of 2 TimeSpans */
    def +(in: TimeSpan) = TimeSpan(this.millis + in.millis)

    /** @return a TimeSpan representing the substraction of 2 TimeSpans */
    def -(in: TimeSpan) = TimeSpan(this.millis - in.millis)

    /** override the equals method so that TimeSpans can be compared to long, int and TimeSpan */
    override def equals(cmp: Any) = {
      cmp match {
        case lo: long => lo == this.millis
        case i: int => i == this.millis
        case ti: TimeSpan => ti.millis == this.millis
        case _ => false
      }
    }

    /** override the toString method to display a readable amount of time */
    override def toString = TimeSpan.format(millis)
  }

  /**
   * The TimeSpan object provides class represents an amount of time.
   * It can be translated to a date with the date method. In that case, the number of millis seconds will be used to create a Date
   * object starting from the Epoch time (see the documentation for java.util.Date)
   */
  object TimeSpan {
    /** time units and values used when converting a total number of millis to those units (see the format function)  */
    val scales = List((1000L, "milli"), (60L, "second"), (60L, "minute"), (24L, "hour"), (7L, "day"), (10000L, "week"))

    /** explicit constructor for a TimeSpan  */
    def apply(in: long) = new TimeSpan(in)

    /**
     * Formats a number of millis to a string representing the number of weeks, days, hours, minutes, seconds, millis
     */
    def format(millis: Long): String = {
      def divideInUnits(millis: Long) = scales.foldLeft[(Long, List[(Long, String)])]((millis, Nil)){ (total, div) =>
          (total._1 / div._1, (total._1 % div._1, div._2) :: total._2)
        }._2
      def formatAmount(amountUnit: (Long, String)) = amountUnit match {
        case (amount, unit) if (amount == 1) => amount + " " + unit
        case (amount, unit) => amount + " " + unit + "s"
      }
      divideInUnits(millis).filter(_._1 > 0).map(formatAmount(_)).mkString(", ")
    }
  }

  /** @return the current number of millis: System.currentTimeMillis  */
  def millis = System.currentTimeMillis

  /** @return the number of millis corresponding to 'in' seconds */
  def seconds(in: long): long = in * 1000L

  /** @return the number of millis corresponding to 'in' minutes */
  def minutes(in: long): long = seconds(in) * 60L

  /** @return the number of millis corresponding to 'in' hours */
  def hours(in: long): long = minutes(in) * 60L

  /** @return the number of millis corresponding to 'in' days */
  def days(in: long): long = hours(in) * 24L

  /** @return the number of millis corresponding to 'in' weeks */
  def weeks(in: long): long = days(in) * 7L

  /** implicit def used to add the noTime method to the Date class */
  implicit def toDateExtension(d: Date) = new DateExtension(d)

  /** This class adds a noTime method the Date class, in order to get at Date object starting at 00:00 */
  class DateExtension(date: Date) {
    /** @returns a Date object starting at 00:00 from date */
    def noTime = {
      val calendar = Calendar.getInstance
      calendar.set(Calendar.HOUR_OF_DAY, 0)
      calendar.set(Calendar.MINUTE, 0)
      calendar.set(Calendar.SECOND, 0)
      calendar.set(Calendar.MILLISECOND, 0)
      calendar.getTime
    }
  }

  /** implicit def used to add the setXXX methods to the Calendar class */
  implicit def toCalendarExtension(c: Calendar) = new CalendarExtension(c)

  /** This class adds the setXXX methods to the Calendar class. Each setter returns the updated Calendar */
  class CalendarExtension(c: Calendar) {
    /** set the day of the month (1 based) and return the calendar */
    def setDay(d: Int) = { c.set(Calendar.DAY_OF_MONTH, d); c }

    /** set the month (0 based) and return the calendar */
    def setMonth(m: Int) = { c.set(Calendar.MONTH, m); c }

    /** set the year and return the calendar */
    def setYear(y: Int) = { c.set(Calendar.YEAR, y); c }

    /** set the TimeZone and return the calendar */
    def setTimezone(tz: TimeZone) = { c.setTimeZone(tz); c }

    /** set the time to 00:00:00.000 and return the calendar */
    def noTime = { c.setTime(c.getTime.noTime); c }
  }

  /** @return the date object for now */
  def now  = new Date

  /** @return the Calendar object for today (the TimeZone is the local TimeZone). Its time is 00:00:00.000 */
  def today  = Calendar.getInstance.noTime

  /** @return the current year */
  def currentYear: Int = Calendar.getInstance.get(Calendar.YEAR)

  /**
   * @deprecated use now instead
   * @return the current time as a Date object
   */
  def timeNow = new Date

  /**
   * @deprecated use today instead
   * @return the current Day as a Date object
   */
  def dayNow: Date = 0.seconds.later.noTime

  /** alias for new Date(millis) */
  def time(when: long) = new Date(when)

  /** @return the month corresponding to today (0 based, relative to UTC) */
  def month(in: Date): Int = {
    val cal = Calendar.getInstance(utc)
    cal.setTimeInMillis(in.getTime)
    cal.get(Calendar.MONTH)
  }

  /** @return the year corresponding to today (relative to UTC) */
  def year(in: Date): Int =  {
    val cal = Calendar.getInstance(utc)
    cal.setTimeInMillis(in.getTime)
    cal.get(Calendar.YEAR)
  }

  /** @return the day of month corresponding to the input date (1 based) */
  def day(in: Date): Int =  {
    val cal = Calendar.getInstance(utc)
    cal.setTimeInMillis(in.getTime)
    cal.get(Calendar.DAY_OF_MONTH)
  }

  /** The UTC TimeZone */
  val utc = TimeZone.getTimeZone("UTC")

  /** @return the number of days since epoch converted from millis */
  def millisToDays(millis: Long): Long = millis / (1000L * 60L * 60L * 24L)

  /** @return the number of days since epoch */
  def daysSinceEpoch: Long = millisToDays(millis)

  /** @return the time taken to evaluate f in millis and the result */
  def calcTime[T](f: => T): (Long, T) = {
    val start = millis
    val result = f
    (millis - start, result)
  }

  /**
   * Log a message with the time taken in millis to do something and retrun the result
   * @return the result
   */
  def logTime[T](msg: String)(f: => T): T = {
    val (time, ret) = calcTime(f)
    Log.info(msg + " took " + time + " Milliseconds")
    ret
  }

  /**
   * @return a standard format HH:mm:ss
   */
  val hourFormat = new SimpleDateFormat("HH:mm:ss")

  /**
   * @return the formatted time for a given Date
   */
  def hourFormat(in: Date): String = hourFormat.format(in)

  /** @return a standard format for the date yyyy/MM/dd */
  def dateFormatter = new SimpleDateFormat("yyyy/MM/dd")

  /** @return a format for the time which includes the TimeZone: HH:mm zzz*/
  def timeFormatter = new SimpleDateFormat("HH:mm zzz")

  /** @return today's date formatted as yyyy/MM/dd */
  def formattedDateNow = dateFormatter.format(now)

  /** @return now's time formatted as HH:mm zzz */
  def formattedTimeNow = timeFormatter.format(now)

  /** @return a formatter for internet dates including: the day of week, the month, day of month, time and time zone */
  def internetDateFormatter = {
    val ret = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US)
    ret.setTimeZone(utc)
    ret
  }

  /** @return a date from a string using the internet format. Return the Epoch date if the parse is unsuccesfull */
  def parseInternetDate(dateString: String): Date = tryo {
    internetDateFormatter.parse(dateString)
  } openOr new Date(0L)

  /** @return a date formatted with the internet format */
  def toInternetDate(in: Date): String = internetDateFormatter.format(in)

  /** @return a date formatted with the internet format (from a number of millis) */
  def toInternetDate(in: Long): String = internetDateFormatter.format(new Date(in))

  /** @return a Full(date) or a failure if the input couldn't be translated to date (or Empty if the input is null)*/
  def toDate(in: Any): Can[Date] = {
    try {
      in match {
        case null => Empty
        case d: Date => Full(d)
        case lng: Long => Full(new Date(lng))
        case lng: Number => Full(new Date(lng.longValue))
        case Nil | Empty | None | Failure(_, _, _) => Empty
        case Full(v) => toDate(v)
        case Some(v) => toDate(v)
        case v :: vs => toDate(v)
        case s : String => tryo(internetDateFormatter.parse(s)) or tryo(dateFormatter.parse(s))
        case o => toDate(o.toString)
      }
    } catch {
      case e => Log.debug("Error parsing date "+in, e); Failure("Bad date: "+in, Full(e), Nil)
    }
  }
}
