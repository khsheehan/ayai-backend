//
// GameConfiguration.scala
//
// Copyright (c) 2015 by Curalate, Inc.
//

package ayai.gameconfig

import ayai.systems.ai.RootAIComponent
import java.io.File
import net.liftweb.json._
import scala.io.Source

/*
 * This class is used to expose read, write, and processing
 * functionality for the game configuration file. This file
 * is used to configure the AI portions of the codebase.
 */
object GameConfiguration {
  private val DEFAULT_MAP_GENERATION = "WorldGenerator"
}

case class GameConfigurationFile(mapGeneration: String)
case class GameConfigurationException(msg: String) extends Throwable(msg)

class GameConfiguration {
  private val CONFIGURATION_FILE_NAME = "gameconfig.ayai"
  private val gameConfigurationFile = fetchGameConfigurationFile()

  def getClassForAIComponent(aiComponentName: String): Class[_ <: RootAIComponent] = {
    aiComponentName match {
      case "MapGeneration" => Class.forName(aiComponentName).asInstanceOf[Class[_ <: RootAIComponent]]
      case _ => throw new GameConfigurationException("The AI component " + aiComponentName + " does not exist.")
    }
  }
  
  private def fetchGameConfigurationFile(): GameConfigurationFile = {
    val file = Source.fromFile(new File("src/main/resources/" + CONFIGURATION_FILE_NAME))
    val aiConfiguration = parse(file.mkString).
      extract[JObject].
      values.asInstanceOf[Map[String, Map[String, String]]].
      getOrElse("ai",
        throw new GameConfigurationException(
          "The game configuration file appears to be broken. Please check documentation for further details."
        ))
    file.close()
    
    GameConfigurationFile(
      mapGeneration = aiConfiguration.get("mapGeneration").getOrElse(GameConfiguration.DEFAULT_MAP_GENERATION)
    )
  }
}
