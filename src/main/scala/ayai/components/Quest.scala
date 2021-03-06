package ayai.components

import crane.Component
import ayai.quests.{KillObjective}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._

import scala.collection.mutable.ArrayBuffer

class Quest(var id: Int, var title: String, var description: String,
			var recommendLevel: Int, var objectives: List[KillObjective]) extends Component {

	def asJson(): JObject = {
		("id" -> id) ~
		("title" -> title) ~
		("description" -> description) ~
		("recommendLevel" -> recommendLevel) ~
		("objectives" -> objectives.map{obj => obj.asJson})
	}
}