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

import java.net.InetSocketAddress

import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.Materializer
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern.Askable
import akka.typed.{ ActorRef, Behavior }
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.apache.logging.log4j.scala.Logging
import org.craftycoder.pugna.Board.{ BoardState, BoardStateNotAvailable }

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

object Api extends Logging {

  final val Name = "api"

  def apply(address: String, port: Int, game: ActorRef[Game.Command], askTimeout: FiniteDuration)(
      implicit mat: Materializer
  ): Behavior[Command] =
    Actor.deferred { context =>
      import akka.typed.scaladsl.adapter._
      import context.executionContext
      implicit val s: ActorSystem = context.system.toUntyped

      val self = context.self
      Http()
        .bindAndHandle(
          route(game)(askTimeout, context.system.executionContext, context.system.scheduler),
          address,
          port
        )
        .onComplete {
          case Failure(_)                   => self ! HandleBindFailure
          case Success(ServerBinding(host)) => self ! HandleBound(host)
        }

      Actor.immutable {
        case (_, HandleBindFailure) =>
          logger.error(s"Stopping, because cannot bind to $address:$port!")
          Actor.stopped

        case (_, HandleBound(host)) =>
          logger.info(s"Bound to $host")
          Actor.ignore
      }
    }

  def route(
      game: ActorRef[Game.Command]
  )(implicit askTimeout: Timeout, ec: ExecutionContextExecutor, scheduler: Scheduler): Route = {
    import Directives._
    import ErrorAccumulatingCirceSupport._
    import io.circe.generic.auto._

    path("players") {
      pathEndOrSingleSlash {
        get {
          complete {
            import Game._
            (game ? getPlayers()).mapTo[Players].map(_.players)
          }
        } ~
        post {
          entity(as[Player]) { player =>
            import Game._
            onSuccess(game ? addPlayer(player)) {
              case DuplicatePlayer    => complete(Conflict)
              case GameAlreadyStarted => complete(Conflict)
              case PlayerAdded(_)     => complete(Created)
            }
          }
        }
      }
    } ~ pathPrefix("game") {
      path("start") {
        get {
          import Game._
          onSuccess(game ? startGame()) {
            case GameAlreadyStarted => complete(Conflict)
            case GameStarted        => complete(Created)
          }
        }
      }
    } ~ pathPrefix("board") {
      pathEndOrSingleSlash {
        get {
          import Game._
          onSuccess(game ? getPositions()) {
            case b @ BoardState(_, _, _) => complete(b)
            case BoardStateNotAvailable  => complete(NotFound)
          }
        }
      }
    }

  }

  sealed trait Command

  private final case class HandleBound(address: InetSocketAddress) extends Command

  private final case object HandleBindFailure extends Command

}
