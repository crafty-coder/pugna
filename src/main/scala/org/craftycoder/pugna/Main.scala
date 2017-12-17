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

import akka.actor.{ ActorSystem, Props }
import pureconfig.loadConfigOrThrow

object Main {

  def main(args: Array[String]): Unit = {
    sys.props += "log4j2.contextSelector" -> "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"

    val config = loadConfigOrThrow[Config]("pugna")
    val system = ActorSystem("pugna")
    system.actorOf(Props(new RootActor(config)), "root")

  }

}
