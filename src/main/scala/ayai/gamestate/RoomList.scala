package ayai.gamestate

import akka.actor.Actor

import scala.collection.mutable.ArrayBuffer

case class AddWorld(world: RoomWorld)
case class GetWorldById(id: Int)

class RoomList extends Actor {
  val roomList: ArrayBuffer[RoomWorld] = ArrayBuffer[RoomWorld]()

  def receive = {
    case AddWorld(world: RoomWorld) =>
      roomList += world
    case GetWorldById(id: Int) =>
      sender ! roomList.find(r => r.id == id)
  }

}
