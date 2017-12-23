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

object MovementMapper {

  def toMovement(text: String): Movement =
    text.toUpperCase match {
      case "UP"    => UP
      case "DOWN"  => DOWN
      case "LEFT"  => LEFT
      case "RIGHT" => RIGHT

      case "UP_LEFT"    => UP_LEFT
      case "UP_RIGHT"   => UP_RIGHT
      case "DOWN_LEFT"  => DOWN_LEFT
      case "DOWN_RIGHT" => DOWN_RIGHT

      case _ => STAY
    }

}
