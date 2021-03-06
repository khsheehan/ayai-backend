package ayai.statuseffects

import crane.Entity
/** External Imports **/
import scala.collection.mutable._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

abstract class TimeAttribute() {
    def process(): Boolean
    def asJson(): JObject
    def isReady(): Boolean
    def initialize()
    def isValid(): Boolean
}

case class OneOff() extends TimeAttribute() {
  var timesUsed = 0;
  override def initialize() {
    timesUsed = 0
  }

  def process(): Boolean = {
    timesUsed = 1
    true
  }

  def asJson(): JObject = {
    ("type" -> "oneoff")
  }

  def isReady(): Boolean = {
    if(timesUsed >= 1) {
      false
    } else {
      true
    }
  }

  def isValid(): Boolean = {
    if(timesUsed >= 1) {
        false
    } else {
        true
    }
  }
}


// maxTime will be in seconds
// Will active every interval until maxTime
// Example if you want to process every 5 seconds for 20 seconds do an interval of 5 and maxtime of 20
case class TimedInterval(val interval: Long,
                         val maxTime: Long) extends TimeAttribute() {
    var timesProcessed: Int = 0
    var startTime: Long = 0
    var currentTime: Long = 0
    var endTime: Long = 0

    override def initialize() {
        timesProcessed = 0
        var time: Long = System.currentTimeMillis
        startTime = time
        endTime = time + (maxTime * 1000)
        process()
    }

    def process(): Boolean = {
        if(isReady()) {
            timesProcessed += 1
            true
        } else {
            false
        }
    }

    def asJson(): JObject = {
        ("type" -> "interval") ~
        ("timeleft" -> getTimeLeft)
    }

    def isReady(): Boolean = {
      val nextTimeToProcess = timesProcessed * interval
      currentTime = System.currentTimeMillis
      if((currentTime - startTime) >= (startTime+((interval*1000) * timesProcessed) )) {
          true
      } else {
          false
      }
    }

    def getTimeLeft(): Long = {
        currentTime = System.currentTimeMillis
        return maxTime - currentTime
    }
    def isValid(): Boolean = {
        if(timesProcessed == (maxTime / interval)) {
            true
        } else {
            false
        }
    }
}
case class Duration(val maxTime: Long) extends TimeAttribute() {
  var timesProcessed: Int = 0
  var startTime: Long = 0
  var currentTime: Long = 0
  var endTime: Long = 0
  
  def initialize() {
    timesProcessed = 0
    var time: Long = System.currentTimeMillis
    startTime = time
    endTime = time + (maxTime * 1000)
    process()    
  }

  def process(): Boolean = {
    if(isReady()) {
        timesProcessed += 1
        true
    } else {
        false
    }
  }

  def asJson(): JObject = {
    ("type" -> "interval") ~
    ("timeleft" -> getTimeLeft)
  }

  def isReady(): Boolean = {
    false
  }

  def getTimeLeft(): Long = {
    currentTime = System.currentTimeMillis
    return endTime - currentTime
  }

  def isValid(): Boolean = {
    if(getTimeLeft() <= 0) {
        false
    } else {
        true
    }
  }
}