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

import scala.util.Random

object RandomCoordinates {

  val DISPERSION = 15

  def nextInQuadrant(quadrant: Int, size: Int): Coordinate = {
    val dispersionX = Random.nextInt(DISPERSION * 2) - DISPERSION
    val dispersionY = Random.nextInt(DISPERSION * 2) - DISPERSION
    val center      = calculateQuadrantCenter(quadrant, size)
    center.copy(
      x = Math.max(Math.min(center.x + dispersionX, size - 1), 0),
      y = Math.max(Math.min(center.y + dispersionY, size - 1), 0)
    )
  }

  private def calculateQuadrantCenter(quadrant: Int, boardSize: Int): Coordinate = {

    val boardCenter = boardSize / 2
    val radius      = boardCenter
    val x           = (boardCenter + radius * Math.cos(Math.PI / 4 + Math.PI / 2 * quadrant)).toInt
    val y           = (boardCenter + radius * Math.sin(Math.PI / 4 + Math.PI / 2 * quadrant)).toInt
    Coordinate(x, y)
  }

}
