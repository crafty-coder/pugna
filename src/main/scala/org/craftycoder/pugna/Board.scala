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

  def getBoardState(replyTo: ActorRef[GetBoardStateReply]): GetBoardState =
    GetBoardState(replyTo)

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
          case MovementResult(Some(newState), None) =>
            logger.debug("Movement Applied!")
            replyTo ! MoveApplied(newState, position)
            runningBoard(newState, players, playerGateway, game)
          case MovementResult(Some(newState), Some((killer, killed))) =>
            logger.debug(s"Movement Applied: $killer has killed $killed")
            val metricsUpdated = addKillingToMetrics(killer, killed, newState.metrics)
            replyTo ! MoveApplied(newState, position)
            runningBoard(newState.copy(metrics = metricsUpdated), players, playerGateway, game)
          case _ =>
            logger.debug(s"Invalid Move($position, $movement)! state: $state")
            val metricsUpdated = addInvalidMoveToMetrics(position.playerName, state.metrics)
            replyTo ! InvalidMove(position)
            runningBoard(state.copy(metrics = metricsUpdated), players, playerGateway, game)
        }
      case (ctx, NewRound) =>
        logger.debug("Round Started")
        ctx.spawn(Round(state, players, playerGateway, ctx.self), s"${Round.Name}-${state.round}")
        Actor.same
      case (_, RoundFinished) =>
        logger.debug("Round Finished")
        game ! Game.RoundFinished(state.round + 1, positions = state.positions)
        runningBoard(state.copy(round = state.round + 1), players, playerGateway, game)

    }

  private def addInvalidMoveToMetrics(
      playerName: String,
      players: Seq[PlayerMetrics]
  ): Seq[PlayerMetrics] =
    players.map { metric =>
      if (metric.name == playerName) metric.copy(invalidMoves = metric.invalidMoves + 1)
      else metric
    }

  private def addKillingToMetrics(
      killerName: String,
      killedName: String,
      players: Seq[PlayerMetrics]
  ): Seq[PlayerMetrics] =
    players.map { metric =>
      if (metric.name == killerName)
        metric.copy(killingBlows = metric.killingBlows + 1)
      else if (metric.name == killedName) metric.copy(deaths = metric.deaths + 1)
      else metric
    }

  private def applyMovement(
      position: Position,
      movement: Movement,
      playerIndex: Int,
      boardState: BoardState
  ): MovementResult =
    calculateTargetCoordinates(position.coordinate, movement, boardState.boardSize) match {
      case Some(targetCoordinates) =>
        val targetPosition: Option[Position] =
          boardState.positions.find(p => p.coordinate == targetCoordinates)
        targetPosition match {
          case None =>
            logger.debug(s"Moved to $targetCoordinates")
            val newPositions = boardState.positions
              .filterNot(_ == position) :+ Position(targetCoordinates, position.playerName)
            MovementResult(Some(boardState.copy(positions = newPositions)), None)
          case Some(occupiedPosition) if occupiedPosition.playerName != position.playerName =>
            val killed = occupiedPosition.playerName
            val killer = position.playerName

            logger.debug(s"$killer has killed $killed on ${occupiedPosition.coordinate}")

            val positionsAfterKill: Seq[Position] =
              boardState.positions.filterNot(p => p.coordinate == occupiedPosition.coordinate)

            val respawnCoordinate =
              getFreeCoordinateForPlayer(playerIndex, boardState.boardSize, positionsAfterKill)
            logger.debug(s"$killer got new position: $respawnCoordinate for his killing")
            val newPositions: Seq[Position] = positionsAfterKill :+ Position(
              respawnCoordinate,
              position.playerName
            )

            MovementResult(Some(boardState.copy(positions = newPositions)), Some(killer, killed))
          case _ =>
            logger.debug(s"Stays")
            MovementResult(Some(boardState), None)
        }
      case None =>
        MovementResult(None, None)
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
      round: Int,
      boardSize: Int,
      players: Seq[Player]
  ) =
    BoardState(occupiedPositions, boardSize, round, players.map(p => PlayerMetrics(name = p.name)))

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

  case class MovementResult(state: Option[BoardState], killing: Option[(String, String)])

  sealed trait Command

  final case class GetBoardState(replyTo: ActorRef[GetBoardStateReply]) extends Command

  final case object NewRound extends Command

  final case class Move(position: Position, movement: Movement, replyTo: ActorRef[MoveReply])
      extends Command

  final case object RoundFinished extends Command

  sealed trait GetBoardStateReply

  final case class BoardStateReply(state: BoardState) extends GetBoardStateReply

  sealed trait NewRoundReply

  final case object NewRoundExecuted

  sealed trait MoveReply

  final case class MoveApplied(newState: BoardState, positionMoved: Position) extends MoveReply

  final case class InvalidMove(positionNotMoved: Position) extends MoveReply

}
