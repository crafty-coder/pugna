/*
 * Copyright 2017 Carlos Peña
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.craftycoder.pugna

import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.logging.log4j.scala.Logging
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.ahc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class PlayerGateway(private val wsClient: StandaloneAhcWSClient)(implicit ec: ExecutionContext)
    extends Logging {

  def playerNextMovement(player: Player,
                         boardState: BoardState,
                         coordinateToMove: Coordinate): Future[Movement] = {

    val query = toQuery(NextMovementRequest(boardState, coordinateToMove))

    val url = s"${player.host}/nextmove"
    logger.debug(s"--> $url")
    wsClient
      .url(url)
      .addHttpHeaders("Content-Type" -> "application/json")
      .post(InMemoryBody(ByteString.fromString(query)))
      .map {
        case response if response.status == StatusCodes.OK.intValue =>
          val body = response.body.replaceAll("\"", "")
          logger.debug(s"Response Body: $body")
          MovementMapper.toMovement(body)
        case response =>
          logger.warn(s"Player ${player.name} failed to reply -> ${response.status}")
          STAY
      }
      .recover {
        case t: Throwable =>
          logger.warn(s"Player ${player.name} failed to reply. ${t.getMessage}")
          STAY
      }
  }

  private def toQuery(request: NextMovementRequest): String =
    request.asJson.spaces2

  def ping(player: Player): Future[Boolean] = {
    val url = s"${player.host}/ping"
    logger.debug(s"--> $url")
    wsClient
      .url(url)
      .get()
      .map {
        case response if response.status == StatusCodes.OK.intValue =>
          response.body.replaceAll("\"", "").toLowerCase() == "pong"
        case response =>
          logger.warn(
            s"Player ${player.name} failed to reply -> ${response.status} ${response.body}"
          )
          false
      }
      .recover {
        case t: Throwable => false
      }

  }

  private case class NextMovementRequest(boardState: BoardState, coordinateToMove: Coordinate)

}
