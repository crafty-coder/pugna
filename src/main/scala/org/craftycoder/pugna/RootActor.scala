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

import akka.actor.{ Actor, ActorLogging, Terminated }
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.ahc._
import scala.concurrent.duration._
import scala.languageFeature.postfixOps

final class RootActor(config: Config) extends Actor with ActorLogging {

  import akka.typed.scaladsl.adapter._

  private implicit val ec                = context.system.dispatcher
  private implicit val mat: Materializer = ActorMaterializer()

  private val playerGateway = new PlayerGateway(createWsClient())

  private val game = context.spawn(Game(playerGateway), Game.Name)

  private val api = {
    import config.api._
    context.spawn(Api(address, port, game, askTimeout), Api.Name)
  }

  context.watch(api)
  context.watch(game)
  log.info(s"${context.system.name} up and running")

  override def receive: Receive = {
    case Terminated(actor) =>
      log.error(s"Shutting down, because actor ${actor.path} terminated!")
      context.system.terminate()
  }

  private def createWsClient() = {
    val config = AhcWSClientConfig(
      wsClientConfig = WSClientConfig(
        connectionTimeout = 2 seconds,
        requestTimeout = 2 seconds
      )
    )
    StandaloneAhcWSClient(config)
  }

}
