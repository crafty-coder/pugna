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

  def apply(players: Set[Player],
            boardSize: Int,
            numOfSoldiers: Int,
            playerGateway: PlayerGateway,
            game: ActorRef[Game.Command]): Behavior[Command] =
    Actor.deferred { _ =>
      val occupiedCoordinates: Map[Coordinate, String] =
        assignInitialPositions(players, boardSize, numOfSoldiers)

      val state = calculateBoardState(occupiedCoordinates, players, boardSize)
      logger.debug("Board created and running")
      runningBoard(state, 0, players, playerGateway, game)

    }

  private def runningBoard(state: BoardState,
                           turnNumber: Int,
                           players: Set[Player],
                           playerGateway: PlayerGateway,
                           game: ActorRef[Game.Command]): Behavior[Command] =
    Actor.immutable {
      case (_, GetBoardState(replyTo)) =>
        replyTo ! state
        Actor.same
      case (_, Move(coordinate, movement, replyTo)) =>
        //TODO Apply movement
        logger.debug(s"Applying movement: $coordinate -> $movement")
        replyTo ! InvalidMove
        Actor.same
      case (ctx, NewTurn) =>
        logger.debug(s"Turn Started")
        ctx.spawn(Turn(state, players, playerGateway, ctx.self), s"${Turn.Name}-$turnNumber")
        Actor.same
      case (ctx, TurnFinished) =>
        logger.debug(s"Turn Finished")
        game ! Game.TurnFinished
        runningBoard(state, turnNumber + 1, players, playerGateway, game)

    }

  private def calculateBoardState(
      occupiedCoordinates: Map[Coordinate, String],
      players: Set[Player],
      boardSize: Int,
  ) = {
    val positions = occupiedCoordinates
      .map({ case (c, p) => Position(c, p) })
      .toSeq
    val playerNames = players.map(_.name)
    BoardState(positions, boardSize, playerNames)
  }

  private def assignInitialPositions(players: Set[Player],
                                     boardSize: Int,
                                     numOfSoldiers: Int): Map[Coordinate, String] = {

    val positions: mutable.Map[Coordinate, String] = mutable.HashMap.empty
    for {
      i      <- 0 until numOfSoldiers
      player <- players
    } yield {
      var position = RandomCoordinates.next(boardSize)
      while (positions.contains(position)) {
        position = RandomCoordinates.next(boardSize)
      }

      positions += ((position, player.name))

    }
    positions.toMap
  }

  sealed trait Command

  final case class GetBoardState(replyTo: ActorRef[GetBoardStateReply]) extends Command
  final case object NewTurn                                             extends Command
  final case class Move(coordinate: Coordinate, movement: Movement, replyTo: ActorRef[MoveReply])
      extends Command
  final case object TurnFinished extends Command

  sealed trait GetBoardStateReply

  final case class BoardState(positions: Seq[Position], boardSize: Int, players: Set[String])
      extends GetBoardStateReply

  final case object BoardStateNotAvailable extends GetBoardStateReply

  sealed trait NewTurnReply

  final case object NewTurnExecuted

  sealed trait MoveReply
  final case class MoveExecuted(newState: BoardState) extends MoveReply
  final case object InvalidMove                       extends MoveReply

}
