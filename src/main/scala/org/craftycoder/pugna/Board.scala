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

object Board extends Logging {

  val Name = "board"

  def apply(players: Seq[Player],
            boardSize: Int,
            numOfSoldiers: Int,
            playerGateway: PlayerGateway,
            game: ActorRef[Game.Command]): Behavior[Command] =
    Actor.deferred { _ =>
      val occupiedPositions: Seq[Position] =
        assignInitialPositions(players, boardSize, numOfSoldiers)

      val state = calculateBoardState(occupiedPositions, 0, boardSize, players)
      logger.debug("Board created and running")
      runningBoard(state, players, playerGateway, game)

    }

  private def runningBoard(state: BoardState,
                           players: Seq[Player],
                           playerGateway: PlayerGateway,
                           game: ActorRef[Game.Command]): Behavior[Command] =
    Actor.immutable {
      case (_, GetBoardState(replyTo)) =>
        replyTo ! BoardStateReply(state)
        Actor.same
      case (_, Move(position, movement, replyTo)) =>
        logger.debug(s"Applying movement: $position -> $movement")
        val playerIndex = players.indexWhere(p => p.name == position.playerName)
        applyMovement(position, movement, playerIndex, state) match {
          case Some(newState) =>
            logger.debug("Movement Applied!")
            replyTo ! MoveAplied(newState)
            runningBoard(newState, players, playerGateway, game)
          case None =>
            logger.debug("Invalid Move!")
            replyTo ! InvalidMove
            Actor.same
        }

      case (ctx, NewTurn) =>
        logger.debug("Turn Started")
        ctx.spawn(Turn(state, players, playerGateway, ctx.self), s"${Turn.Name}-${state.turn}")
        Actor.same
      case (ctx, TurnFinished) =>
        logger.debug("Turn Finished")
        game ! Game.TurnFinished
        runningBoard(state.copy(turn = state.turn + 1), players, playerGateway, game)

    }

  private def applyMovement(position: Position,
                            movement: Movement,
                            playerIndex: Int,
                            boardState: BoardState): Option[BoardState] =
    calculateTargetCoordinates(position.coordinate, movement, boardState.boardSize) match {
      case Some(targetCoordinates) =>
        val targetPosition: Option[Position] =
          boardState.positions.find(p => p.coordinate == targetCoordinates)
        targetPosition match {
          case None =>
            logger.debug(s"Moved to $targetCoordinates")
            val newPositions = boardState.positions
              .filterNot(_ == position) :+ Position(targetCoordinates, position.playerName)
            Some(boardState.copy(positions = newPositions))
          case Some(occupiedPosition) if occupiedPosition.playerName != position.playerName =>
            logger.debug(s"Kill -> $occupiedPosition Positions=${boardState.positions.size}")

            val positionsAfterKill: Seq[Position] =
              boardState.positions.filterNot(p => p.coordinate == occupiedPosition.coordinate)
            logger.debug(s"Positions after kill =${positionsAfterKill.size}")

            val respawnCoordinate =
              getFreeCoordinateForPlayer(playerIndex, boardState.boardSize, positionsAfterKill)
            logger.debug(s"Respawn in -> $respawnCoordinate")
            val newPositions: Seq[Position] = positionsAfterKill :+ Position(
              respawnCoordinate,
              position.playerName
            )
            logger.debug(s"Positions after respawn =${newPositions.size}")

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
      occupiedPositions: Seq[Position],
      turn: Int,
      boardSize: Int,
      players: Seq[Player]
  ) = {
    val playerNames = players.map(_.name)
    BoardState(occupiedPositions, boardSize, turn, playerNames)
  }

  private def assignInitialPositions(players: Seq[Player],
                                     boardSize: Int,
                                     numOfSoldiers: Int): Seq[Position] = {

    var positions = Seq.empty[Position]
    for {
      i               <- 0 until numOfSoldiers
      (player, index) <- players.zipWithIndex
    } yield {
      var coordinate = getFreeCoordinateForPlayer(index, boardSize, positions)
      positions = positions :+ Position(coordinate, player.name)

    }
    positions
  }

  private def getFreeCoordinateForPlayer(
      index: Int,
      boardSize: Int,
      occupiedPositions: Seq[Position]
  ): Coordinate = {
    var coordinate = RandomCoordinates.nextInQuadrant(index, boardSize)
    while (occupiedPositions.exists(p => p.coordinate == coordinate)) {
      coordinate = RandomCoordinates.nextInQuadrant(index, boardSize)
    }
    coordinate
  }

  sealed trait Command

  final case class GetBoardState(replyTo: ActorRef[GetBoardStateReply]) extends Command
  final case object NewTurn                                             extends Command
  final case class Move(position: Position, movement: Movement, replyTo: ActorRef[MoveReply])
      extends Command
  final case object TurnFinished extends Command

  sealed trait GetBoardStateReply
  final case class BoardStateReply(state: BoardState) extends GetBoardStateReply

  final case object BoardStateNotAvailable extends GetBoardStateReply

  sealed trait NewTurnReply

  final case object NewTurnExecuted

  sealed trait MoveReply
  final case class MoveAplied(newState: BoardState) extends MoveReply
  final case object InvalidMove                     extends MoveReply

}
