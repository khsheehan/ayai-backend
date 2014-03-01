package ayai.gamestate

/** Ayai Imports **/
import ayai.components._

/** Crane Imports **/
import crane.{Entity, World}

/** Akka Imports **/
import akka.actor.{Actor, ActorSystem, ActorRef}


/** External Imports **/
import scala.collection.mutable.ArrayBuffer
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import org.slf4j.{Logger, LoggerFactory}

sealed trait QueryType
sealed trait QueryResponse

case class CharacterRadius(characterId: String) extends QueryType
case class CharacterResponse(json: String)  extends QueryResponse
case class MapRequest(room: Entity)
case object GetRoomJson
case object Refresh
case object SomeData

object GameStateSerializer {
  def apply(world: World) = new GameStateSerializer(world)
}

class GameStateSerializer(world: World) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  private var roomJSON: String = ""
  private var valid: Boolean = false

  //Returns a list of entities contained within a room.
  def getRoomEntities(roomId: Long): ArrayBuffer[Entity] = {
    world.groups("ROOM" + roomId)
  }

  //Returns a character's belongings and surroundings.
  def getRoom = {
    if(!valid) {
      var entities = world.getEntitiesByComponents(classOf[Character], classOf[Position],
                                                      classOf[Health], classOf[Mana],
                                                      classOf[Actionable], classOf[QuestBag],
                                                      classOf[Inventory], classOf[Equipment])
         val jsonLift: JObject =
           ("type" -> "update") ~
            ("players" -> entities.map{ e =>
             (e.getComponent(classOf[Character]),
               e.getComponent(classOf[Position]),
               e.getComponent(classOf[Health]),
               e.getComponent(classOf[Mana]),
               e.getComponent(classOf[Actionable]),
               e.getComponent(classOf[Inventory]),
               e.getComponent(classOf[Equipment]),
               e.getComponent(classOf[QuestBag])) match {
                 case (Some(character: Character), Some(position: Position), Some(health: Health),
                       Some(mana: Mana), Some(actionable: Actionable), Some(inventory: Inventory),
                       Some(quest: Equipment), Some(equipment: QuestBag)) =>
                   ((character.asJson) ~
                   (position.asJson) ~
                   (health.asJson) ~
                   (mana.asJson) ~
                   (actionable.action.asJson) ~
                   (quest.asJson) ~
                   (inventory.asJson) ~
                   (equipment.asJson))
                 case _ =>
                   log.warn("f3d3275: getComponent failed to return anything BLARG2")
                   JNothing
             }})

         try {
           roomJSON = compact(render(jsonLift))
           valid = true
           sender ! roomJSON
         } catch {
           case e: Exception =>
             e.printStackTrace()
             sender ! ""
         }

    } else {
      sender ! roomJSON
    }
  }

  //Once characters actually have belonging we'll want to implement this and use it in getCharacterRadius
  // def getCharacterBelongings(characterId: String) = {

  // }

  // def getSurroundings(pos: Position) = {

  // }

  def sendMapInfo(room: Entity ) = {
    val tileMap = world.asInstanceOf[RoomWorld].tileMap
    val json =  ("type" -> "map") ~
                ("tilemap" -> tileMap.file) ~
                (tileMap.tilesets.asJson)

    try {
      println(compact(render(json)))
      sender ! compact(render(json))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        sender ! ""
    }
  }

  def receive = {
    case GetRoomJson => getRoom
    case MapRequest(room) => sendMapInfo(room)
    case Refresh => valid = false
    case _ => println("Error: from serializer.")
  }
}
