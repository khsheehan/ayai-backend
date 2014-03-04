package ayai.apps

/** Ayai Imports **/
import ayai.networking._
import ayai.components._
import ayai.persistence._
import ayai.gamestate._
import ayai.factories._

/** Akka Imports **/
import akka.actor.{Actor, ActorRef, ActorSystem, Status, Props}
import akka.actor.Status.{Success, Failure}
import akka.pattern.ask
import akka.util.Timeout

/** External Imports **/
import scala.concurrent.{Await, ExecutionContext, Promise, Future}
import scala.concurrent.duration._
import scala.collection.concurrent.{Map => ConcurrentMap, TrieMap}
import scala.collection.JavaConversions._
import scala.collection.mutable.{ArrayBuffer, HashMap}


import org.slf4j.{Logger, LoggerFactory}


object GameLoop {
  private val log = LoggerFactory.getLogger(getClass)

  var running: Boolean = true

  def main(args: Array[String]) {
    implicit val timeout = Timeout(Constants.NETWORK_TIMEOUT seconds)
    import ExecutionContext.Implicits.global

    DBCreation.ensureDbExists()

    var worlds = HashMap[String, RoomWorld]()
    var socketMap: ConcurrentMap[String, String] = TrieMap[String, String]()

    val networkSystem = ActorSystem("NetworkSystem")
    val mQueue = networkSystem.actorOf(Props[MessageQueue], name="MQueue")
    val nmInterpreter = networkSystem.actorOf(Props[NetworkMessageInterpreterSupervisor], name="NMInterpreter")
    val aProcessor = networkSystem.actorOf(Props[AuthorizationProcessor], name="AProcessor")
    val socketUserMap = networkSystem.actorOf(Props[SocketUserMap], name="SocketUserMap")
    val userRoomMap = networkSystem.actorOf(Props[UserRoomMap], name="UserRoomMap")
    val roomList = networkSystem.actorOf(Props[RoomList], name="RoomList")
    val itemMap = networkSystem.actorOf(Props[ItemMap], name="ItemMap")
    val questMap = networkSystem.actorOf(Props[QuestMap], name="QuestMap")

    val rooms = List("map3", "map2")
    val worldFactory = WorldFactory(networkSystem)

    val itemFactory = ItemFactory.bootup(networkSystem)    
    val questFactory = QuestFactory.bootup(networkSystem)

    for((file, index) <- rooms.zipWithIndex)
      worlds(s"room$index") = worldFactory.createWorld(s"room$index", s"$file")

    for((name, world) <- worlds)
      roomList ! AddWorld(world)

    val receptionist = SockoServer(networkSystem)
    receptionist.run(Constants.SERVER_PORT)

    //GAME LOOP RUNS AS LONG AS SERVER IS UP
    while(running) {
      val start = System.currentTimeMillis

      val processedMessages = new ArrayBuffer[Future[Any]]
      for((name, world) <- worlds) {
        val future = mQueue ? FlushMessages(name)
        val result = Await.result(future, timeout.duration).asInstanceOf[QueuedMessages]
        val mProcessor = networkSystem.actorSelection(s"user/MProcessor$name")
//        println("MESSAGES")
 //       println(result.messages)

        result.messages.foreach { message =>
          processedMessages += mProcessor ? new ProcessMessage(message)
        }
      }

      Await.result(Future.sequence(processedMessages), 5 seconds)

      for((name, world) <- worlds) {
        world.process()
        //println(world.entities)
      }

      val end = System.currentTimeMillis
      if((end - start) < (1000 / Constants.FRAMES_PER_SECOND)) {
        Thread.sleep((1000 / Constants.FRAMES_PER_SECOND) - (end - start))
      }
    }
  }
}


