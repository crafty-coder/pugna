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
import akka.pattern.pipe
import akka.typed.{ ActorRef, Behavior }
import org.apache.logging.log4j.scala.Logging
import org.craftycoder.pugna.Board.{ BoardState, InvalidMove, MoveAplied, MoveReply }

import scala.util.{ Failure, Success }

object Turn extends Logging {

  val Name = "turn"

  def apply(state: BoardState,
            players: Seq[Player],
            playerGateway: PlayerGateway,
            board: ActorRef[Board.Command]): Behavior[Turn.Command] =
    Actor.deferred { ctx =>
      val boardAdapter: ActorRef[MoveReply] = ctx.spawnAdapter {
        case MoveAplied(s) => FinishSuccessfulMove(s)
        case InvalidMove   => FinishUnsuccessfulMove
      }
      logger.debug("Turn Created")
      ctx.self ! NextMove
      val shufflePositions = scala.util.Random.shuffle(state.positions)
      turnInProgress(state, players, shufflePositions, playerGateway, boardAdapter, board)
    }

  private def turnInProgress(state: BoardState,
                             players: Seq[Player],
                             remainingPossitionsToMove: Seq[Position],
                             playerGateway: PlayerGateway,
                             boardAdapter: ActorRef[MoveReply],
                             board: ActorRef[Board.Command]): Behavior[Turn.Command] =
    Actor.immutable {

      case (ctx, NextMove) =>
        implicit val ec    = ctx.executionContext
        val positionToMove = remainingPossitionsToMove.head
        getPlayerFromPosition(players, positionToMove).foreach { player =>
          playerGateway.playerNextMovement(player, state, positionToMove.coordinate).andThen {
            case Success(movement) ⇒
              board ! Board.Move(positionToMove, movement, boardAdapter)
            case Failure(f) ⇒
              board ! Board.Move(positionToMove, STAY, boardAdapter)
          }
        }
        turnInProgress(state,
                       players,
                       remainingPossitionsToMove.tail,
                       playerGateway,
                       boardAdapter,
                       board)
      case (ctx, FinishSuccessfulMove(newState: BoardState)) =>
        if (remainingPossitionsToMove.isEmpty) {
          board ! Board.TurnFinished
          Actor.stopped
        } else {
          ctx.self ! NextMove
          turnInProgress(newState,
                         players,
                         remainingPossitionsToMove,
                         playerGateway,
                         boardAdapter,
                         board)
        }
      case (ctx, FinishUnsuccessfulMove) =>
        if (remainingPossitionsToMove.isEmpty) {
          board ! Board.TurnFinished
          Actor.stopped
        } else {
          ctx.self ! NextMove
          Actor.same
        }
    }

  def getPlayerFromPosition(players: Seq[Player], position: Position): Option[Player] =
    players.find(p => p.name == position.playerName)

  sealed trait Command
  private final case object NextMove                                  extends Command
  private final case class FinishSuccessfulMove(newState: BoardState) extends Command
  private final case object FinishUnsuccessfulMove                    extends Command

}
