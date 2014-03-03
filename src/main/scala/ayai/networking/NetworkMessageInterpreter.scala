package ayai.networking

/** Ayai Imports **/
import ayai.gamestate._
import ayai.actions._
import ayai.apps.Constants

/** Akka Imports **/
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

/** Socko Imports **/
import org.mashupbots.socko.routes._
import org.mashupbots.socko.events.WebSocketFrameEvent

/** External Imports **/
import scala.util.Random
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import java.rmi.server.UID

class NetworkMessageInterpreter extends Actor {
  implicit val timeout = Timeout(2 seconds)
  val queue = context.system.actorSelection("user/MQueue")
  val socketUserMap = context.system.actorSelection("user/SocketUserMap")
  val userRoomMap = context.system.actorSelection("user/UserRoomMap")
  // TODO: We need a room lookup that is better than this
  val roomList = context.system.actorSelection("user/RoomList")

  implicit val formats = Serialization.formats(NoTypeHints)

  def lookUpUserBySocketId(socketId: String): String = {
    val future = socketUserMap ? GetUserId(socketId)
    Await.result(future, timeout.duration).asInstanceOf[String]
  }

  def lookUpWorldByUserId(userId: String): String = {
    val future = userRoomMap ? GetWorld(userId)
    Await.result(future, timeout.duration).asInstanceOf[RoomWorld].name
  }

  def lookUpWorldByName(name: String): RoomWorld = {
    val future = roomList ? GetWorldByName(name)
    Await.result(future, timeout.duration).asInstanceOf[Option[RoomWorld]] match {
      case Some(room: RoomWorld) => room
    }
  }

  def interpretMessage(wsFrame: WebSocketFrameEvent) = {
    val rootJSON = parse(wsFrame.readText)
    val tempType: String = compact(render(rootJSON \ "type"))
    val msgType: String = tempType.substring(1, tempType.length - 1)

    val userId = msgType match {
      case "init" => ""
      case _ =>
        lookUpUserBySocketId(wsFrame.webSocketId)
    }
    val world = msgType match {
      case "init" => "room0"
      case _ =>
        lookUpWorldByUserId(userId)
    }

    msgType match {
      case "init" =>
        val id = (new UID()).toString
        context.system.actorOf(Props(new SockoSender(wsFrame)), "SockoSender" + id)
        context.system.actorSelection("user/SocketUserMap") ! AddSocketUser(wsFrame.webSocketId, id)
        context.system.actorSelection("user/UserRoomMap") ! AddAssociation(id, lookUpWorldByName("room0"))

        val tempName: String = compact(render(rootJSON \ "name"))
        val characterName:String = compact(render(rootJSON \ "name")).substring(1, tempName.length - 1)
        queue ! new AddInterpretedMessage(world, new AddNewCharacter(id, characterName, Constants.STARTING_X, Constants.STARTING_Y))

      case "move" =>
        //TODO: Add exceptions and maybe parse shit a bit more intelligently
        val start: Boolean = compact(render(rootJSON \ "start")).toBoolean
        var direction: MoveDirection = new MoveDirection(0,0)
        if(start) {
          val dir: Int = compact(render(rootJSON \ "dir")).toInt
          direction  = dir match {
            case 0 => UpDirection
            case 1 => UpRightDirection
            case 2 => RightDirection
            case 3 => DownRightDirection
            case 4 => DownDirection
            case 5 => DownLeftDirection
            case 6 => LeftDirection
            case 7 => UpLeftDirection
            case _ => {
              println("Direction not found, in Interpreter")
              new MoveDirection(0,0)
            }
          }
        }
        queue ! new AddInterpretedMessage(world, new MoveMessage(userId, start, direction))

      case "attack" =>
        println("Attack Received")
        queue ! new AddInterpretedMessage(world, new AttackMessage(userId))

      case "chat" =>
        val message = compact(render(rootJSON \ "message"))
        val tempSender: String = compact(render(rootJSON \ "sender"))
        val sender = tempSender.substring(1, tempSender.length - 1)
        queue ! new AddInterpretedMessage(world, new PublicChatMessage(message, sender))

      case "open" =>
        val containerId: String = (rootJSON \ "containerId").extract[String]
        queue ! new AddInterpretedMessage(world, new OpenMessage(userId, containerId))

      case "chars" =>
        val accountName: String = (rootJSON \ "accountName").extract[String]
        queue ! new CharacterList(userId, accountName)
      case "equip" => 
        val slot: Int = (rootJSON \ "slot").extract[Int]
        val equipmentType: String = (rootJSON \ "equipmentType").extract[String]
        queue ! new EquipMessage(userId, slot, equipmentType)    
      case _ =>
        println("Unknown message in NetworkMessageInterpreter: " + msgType)

    }
  }

  def receive = {
    case InterpretMessage(message) => interpretMessage(message)
    case _ => println("Error: from interpreter.")
  }
}
