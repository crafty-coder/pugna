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
import org.craftycoder.pugna.Universe.{ GameNotFound, GameRef, getGameRef }

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

object Api extends Logging {

  final val Name = "api"

  def apply(address: String,
            port: Int,
            universe: ActorRef[Universe.Command],
            askTimeout: FiniteDuration)(
      implicit mat: Materializer
  ): Behavior[Command] =
    Actor.deferred { context =>
      import akka.typed.scaladsl.adapter._
      import context.executionContext
      implicit val s: ActorSystem = context.system.toUntyped

      val self = context.self
      Http()
        .bindAndHandle(
          route(universe)(askTimeout, context.system.executionContext, context.system.scheduler),
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
      universe: ActorRef[Universe.Command]
  )(implicit askTimeout: Timeout, ec: ExecutionContextExecutor, scheduler: Scheduler): Route = {
    import Directives._
    import ErrorAccumulatingCirceSupport._
    import io.circe.generic.auto._

    path("games") {
      get {
        complete {
          import Universe._
          (universe ? getGames()).mapTo[Games]
        }
      } ~ post {
        import Universe._
        onSuccess(universe ? createGame()) {
          case GameCreated(x) => complete(Created)
        }
      }
    } ~ path("games" / Segment / "players") { gameId =>
      pathEndOrSingleSlash {
        get {
          import Universe._
          onSuccess(universe ? getGameRef(gameId)) {
            case GameNotFound =>
              complete(NotFound)
            case GameRef(game) =>
              import Game._
              complete {
                (game ? getPlayers()).mapTo[Players]
              }
          }
        } ~ post {
          entity(as[Player]) { player =>
            onSuccess(universe ? getGameRef(gameId)) {
              case GameNotFound =>
                complete(NotFound)
              case GameRef(game) =>
                import Game._
                onSuccess(game ? addPlayer(player)) {
                  case UnReachablePlayer  => complete(BadGateway)
                  case DuplicatePlayer    => complete(Conflict)
                  case TooManyPlayers     => complete(Conflict)
                  case GameAlreadyStarted => complete(Conflict)
                  case PlayerAdded(p) =>
                    logger.debug(s"Player $p added")
                    complete(Created)
                }
            }
          }
        }
      }
    } ~ path("games" / Segment / "start") { gameId =>
      put {
        logger.debug("Starting Game!")
        onSuccess(universe ? getGameRef(gameId)) {
          case GameNotFound =>
            complete(NotFound)
          case GameRef(game) =>
            import Game._
            onSuccess(game ? startGame()) {
              case GameAlreadyStarted => complete(Conflict)
              case TooFewPlayers      => complete(Conflict)
              case GameStarted        => complete(Created)
            }
        }
      }
    } ~ path("games" / Segment / "restart") { gameId =>
      put {
        logger.debug("Restart Game!")
        onSuccess(universe ? getGameRef(gameId)) {
          case GameNotFound =>
            complete(NotFound)
          case GameRef(game) =>
            import Game._
            onSuccess(game ? restartGame()) {
              case GameRestarted => complete(OK)
            }
        }
      }
    } ~ path("games" / Segment / "state") { gameId =>
      pathEndOrSingleSlash {
        get {
          onSuccess(universe ? getGameRef(gameId)) {
            case GameNotFound =>
              complete(NotFound)
            case GameRef(game) =>
              import Game._
              onSuccess(game ? getGameState()) {
                case b: GameState => complete(b)
              }
          }
        }
      }
    } ~
    pathEndOrSingleSlash {
      getFromResource("view/index.html")
    } ~ pathPrefix("") {
      getFromResourceDirectory("view")
    }

  }

  sealed trait Command

  private final case class HandleBound(address: InetSocketAddress) extends Command

  private final case object HandleBindFailure extends Command

}
