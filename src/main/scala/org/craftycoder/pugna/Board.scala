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
      case (_, Move(position, movement, replyTo)) =>
        logger.debug(s"Applying movement: $position -> $movement")
        applyMovement(position, movement, state) match {
          case Some(newState) =>
            logger.debug(s"Movement Applied!")
            replyTo ! MoveAplied(newState)
            runningBoard(newState, turnNumber, players, playerGateway, game)
          case None =>
            logger.debug(s"Invalid Move!")
            replyTo ! InvalidMove
            Actor.same
        }

      case (ctx, NewTurn) =>
        logger.debug(s"Turn Started")
        ctx.spawn(Turn(state, players, playerGateway, ctx.self), s"${Turn.Name}-$turnNumber")
        Actor.same
      case (ctx, TurnFinished) =>
        logger.debug(s"Turn Finished")
        game ! Game.TurnFinished
        runningBoard(state, turnNumber + 1, players, playerGateway, game)

    }

  private def applyMovement(position: Position,
                            movement: Movement,
                            boardState: BoardState): Option[BoardState] =
    calculateTargetCoordinates(position.coordinate, movement, boardState.boardSize) match {
      case Some(targetCoordinates) =>
        val targetPosition = boardState.positions.find(p => p.coordinate == targetCoordinates)
        targetPosition match {
          case None =>
            logger.debug(s"Moved to $targetCoordinates")
            val newPositions = boardState.positions
              .filterNot(_ == position) :+ Position(targetCoordinates, position.playerName)
            Some(boardState.copy(positions = newPositions))
          case Some(occupiedPosition) if occupiedPosition.playerName != position.playerName =>
            logger.debug(s"Kill -> $occupiedPosition")
            val newPositions = boardState.positions.filterNot(_ == occupiedPosition)
            Some(boardState.copy(positions = newPositions))
          case _ =>
            logger.debug(s"Stays")
            Some(boardState)
        }
      case None => None
    }

  private def calculateTargetCoordinates(coordinate: Coordinate,
                                         movement: Movement,
                                         boardSize: Int): Option[Coordinate] = {
    val newCoordinates = movement match {
      case UP =>
        coordinate.copy(y = coordinate.y + 1)
      case UP_LEFT =>
        coordinate.copy(x = coordinate.x - 1, y = coordinate.y + 1)
      case UP_RIGHT =>
        coordinate.copy(x = coordinate.x + 1, y = coordinate.y + 1)
      case LEFT =>
        coordinate.copy(x = coordinate.x - 1)
      case RIGHT =>
        coordinate.copy(x = coordinate.x + 1)
      case DOWN =>
        coordinate.copy(y = coordinate.y - 1)
      case DOWN_LEFT =>
        coordinate.copy(x = coordinate.x - 1, y = coordinate.y - 1)
      case DOWN_RIGHT =>
        coordinate.copy(x = coordinate.x + 1, y = coordinate.y - 1)
      case STAY =>
        coordinate

    }

    newCoordinates match {
      case Coordinate(x, y) if x < 0 || y < 0                   => None
      case Coordinate(x, y) if x >= boardSize || y >= boardSize => None
      case validCoordinates                                     => Some(validCoordinates)
    }
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
  final case class Move(position: Position, movement: Movement, replyTo: ActorRef[MoveReply])
      extends Command
  final case object TurnFinished extends Command

  sealed trait GetBoardStateReply

  final case class BoardState(positions: Seq[Position], boardSize: Int, players: Set[String])
      extends GetBoardStateReply

  final case object BoardStateNotAvailable extends GetBoardStateReply

  sealed trait NewTurnReply

  final case object NewTurnExecuted

  sealed trait MoveReply
  final case class MoveAplied(newState: BoardState) extends MoveReply
  final case object InvalidMove                     extends MoveReply

}
