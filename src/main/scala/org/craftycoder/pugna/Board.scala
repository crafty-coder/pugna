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

import akka.typed.scaladsl.Actor
import akka.typed.{ ActorRef, Behavior }
import org.apache.logging.log4j.scala.Logging

import scala.collection.mutable

object Board extends Logging {

  val Name = "board"

  def apply(players: Set[Player], boardSize: Int, numOfSoldiers: Int): Behavior[Command] =
    Actor.deferred { _ =>
      val occupiedCoordinates: mutable.HashMap[Coordinates, Player] =
        assignInitialPositions(players, boardSize, numOfSoldiers)

      Actor.immutable {
        case (_, GetBoardState(replyTo)) =>
          val positions = occupiedCoordinates
            .map({ case (c, p) => Position(c, p.name) })
            .toSeq
          val playerNames = players.map(_.name)
          replyTo ! BoardState(positions, boardSize, playerNames)
          Actor.same
      }
    }

  private def assignInitialPositions(players: Set[Player],
                                     boardSize: Int,
                                     numOfSoldiers: Int): mutable.HashMap[Coordinates, Player] = {

    val positions: mutable.HashMap[Coordinates, Player] = mutable.HashMap.empty
    for {
      player <- players
      i      <- 0 until numOfSoldiers
    } yield {
      var position = RandomCoordinates.next(boardSize)
      while (positions.contains(position)) {
        position = RandomCoordinates.next(boardSize)
      }

      positions += ((position, player))

    }
    positions
  }

  sealed trait Command

  case class GetBoardState(replyTo: ActorRef[GetBoardStateReply]) extends Command

  sealed trait GetBoardStateReply

  final case class BoardState(positions: Seq[Position], boardSize: Int, players: Set[String])
      extends GetBoardStateReply

  final case object BoardStateNotAvailable extends GetBoardStateReply

}
