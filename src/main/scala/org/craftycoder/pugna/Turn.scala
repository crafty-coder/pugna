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
import org.craftycoder.pugna.Board.{ InvalidMove, MoveApplied, MoveReply }

import scala.util.{ Failure, Success }

object Turn extends Logging {

  val Name                      = "turn"
  val MAX_MOVEMENTS_IN_PARALLEL = 10
  val SAFE_DISTANCE_TO_PARALLEL = 5

  def apply(state: BoardState,
            players: Seq[Player],
            playerGateway: PlayerGateway,
            board: ActorRef[Board.Command]): Behavior[Turn.Command] =
    Actor.deferred { ctx =>
      val boardAdapter: ActorRef[MoveReply] = ctx.spawnAdapter {
        case MoveApplied(s, positionMoved) => FinishSuccessfulMove(s, positionMoved)
        case InvalidMove(positionNotMoved) => FinishUnsuccessfulMove(positionNotMoved)
      }
      logger.debug(s"Turn ${state.turn} Created")
      ctx.self ! NextMove
      val shufflePositions = scala.util.Random.shuffle(state.positions)
      turnInProgress(state,
                     players,
                     shufflePositions,
                     Set.empty,
                     playerGateway,
                     boardAdapter,
                     board)
    }

  private def getPlayerFromPosition(players: Seq[Player], position: Position): Option[Player] =
    players.find(p => p.name == position.playerName)

  private def getPositionsToMoveInParallel(positions: Seq[Position]): Set[Position] = {

    def inner(remainingPositions: Seq[Position], selectedPositions: Set[Position]): Set[Position] =
      remainingPositions match {
        case x :: ys
            if minDistanceToSet(x, selectedPositions).forall(_ > SAFE_DISTANCE_TO_PARALLEL) && selectedPositions.size < MAX_MOVEMENTS_IN_PARALLEL =>
          selectedPositions ++ Set(x) ++ inner(ys, selectedPositions ++ Set(x))
        case _ :: _ => selectedPositions
        case Nil    => Set.empty
      }

    inner(remainingPositions = positions, selectedPositions = Set.empty)

  }

  private def minDistanceToSet(p: Position, selectedPositions: Set[Position]): Option[Int] =
    if (selectedPositions.isEmpty) None
    else Some(selectedPositions.map(distance(p, _)).min)

  private def distance(a: Position, b: Position): Int = {
    val xDistance = Math.abs(a.coordinate.x - b.coordinate.x)
    val yDistance = Math.abs(a.coordinate.y - b.coordinate.y)
    Math.max(xDistance, yDistance)
  }

  private def turnInProgress(state: BoardState,
                             players: Seq[Player],
                             remainingPositionsToMove: Seq[Position],
                             positionsInMovement: Set[Position],
                             playerGateway: PlayerGateway,
                             boardAdapter: ActorRef[MoveReply],
                             board: ActorRef[Board.Command]): Behavior[Turn.Command] =
    Actor.immutable {

      case (ctx, NextMove) =>
        implicit val ec               = ctx.executionContext
        val positionsToMoveInParallel = getPositionsToMoveInParallel(remainingPositionsToMove)
        logger.debug(
          s"Moving in parallel ${positionsToMoveInParallel.size} positions $positionsToMoveInParallel"
        )
        positionsToMoveInParallel.foreach { positionToMove =>
          getPlayerFromPosition(players, positionToMove).foreach { player =>
            playerGateway.playerNextMovement(player, state, positionToMove).andThen {
              case Success(movement) ⇒
                board ! Board.Move(positionToMove, movement, boardAdapter)
              case Failure(f) ⇒
                board ! Board.Move(positionToMove, STAY, boardAdapter)
            }
          }
        }

        turnInProgress(state,
                       players,
                       remainingPositionsToMove.tail,
                       positionsToMoveInParallel,
                       playerGateway,
                       boardAdapter,
                       board)
      case (ctx, FinishSuccessfulMove(newState, positionMoved)) =>
        val currentPositionsInMovement    = positionsInMovement - positionMoved
        val remainingPositionsToMoveAlive = remainingPositionsToMove.intersect(newState.positions)

        if (currentPositionsInMovement.isEmpty) {

          if (remainingPositionsToMoveAlive.isEmpty) {
            board ! Board.TurnFinished
            Actor.stopped
          } else {
            ctx.self ! NextMove
            turnInProgress(newState,
                           players,
                           remainingPositionsToMoveAlive,
                           currentPositionsInMovement,
                           playerGateway,
                           boardAdapter,
                           board)
          }

        } else {
          turnInProgress(newState,
                         players,
                         remainingPositionsToMoveAlive,
                         currentPositionsInMovement,
                         playerGateway,
                         boardAdapter,
                         board)
        }

      case (ctx, FinishUnsuccessfulMove(positionNotMoved)) =>
        val currentPositionsInMovement = positionsInMovement - positionNotMoved

        if (currentPositionsInMovement.isEmpty) {
          if (remainingPositionsToMove.isEmpty) {
            board ! Board.TurnFinished
            Actor.stopped
          } else {
            ctx.self ! NextMove
            turnInProgress(state,
                           players,
                           remainingPositionsToMove,
                           currentPositionsInMovement,
                           playerGateway,
                           boardAdapter,
                           board)
          }
        } else {
          turnInProgress(state,
                         players,
                         remainingPositionsToMove,
                         currentPositionsInMovement,
                         playerGateway,
                         boardAdapter,
                         board)
        }

    }

  sealed trait Command

  private final case class FinishSuccessfulMove(newState: BoardState, positionMoved: Position)
      extends Command

  private final case object NextMove extends Command

  private final case class FinishUnsuccessfulMove(positionNotMoved: Position) extends Command

}
