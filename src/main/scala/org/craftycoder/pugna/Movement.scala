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

sealed trait Movement
case object UP    extends Movement
case object DOWN  extends Movement
case object LEFT  extends Movement
case object RIGHT extends Movement

case object UP_LEFT    extends Movement
case object UP_RIGHT   extends Movement
case object DOWN_LEFT  extends Movement
case object DOWN_RIGHT extends Movement
case object STAY       extends Movement
