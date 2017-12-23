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
import org.craftycoder.pugna.Board.BoardState
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.ahc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class PlayerGateway(val wsClient: StandaloneAhcWSClient)(implicit ec: ExecutionContext)
    extends Logging {

  def playerNextMovement(player: Player,
                         boardState: BoardState,
                         coordinateToMove: Coordinate): Future[Movement] = {

    val query = toQuery(NextMovementRequest(boardState, coordinateToMove))

    wsClient
      .url(s"${player.host}/nextMovement")
      .post(InMemoryBody(ByteString.fromString(query)))
      .map {
        case response if response.status == StatusCodes.OK.intValue =>
          MovementMapper.toMovement(response.body)
        case response =>
          logger.warn(s"Player ${player.name} failed to reply")
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

  private case class NextMovementRequest(boardState: BoardState, coordinateToMove: Coordinate)

}
