/**
 * Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy

import scala.util.{Failure, Success, Try}

package object ingest {
  implicit class TryExtensions[T](val t: Try[T]) extends AnyVal {
    def doOnError(f: Throwable => Unit): Try[T] = {
      t match {
        case Failure(e) => Try { f(e); throw e }
        case x => x
      }
    }

    def doOnSuccess(f: T => Unit): Try[T] = {
      t match {
        case Success(x) => Try { f(x); x }
        case e => e
      }
    }

    def eventually[Ignore](effect: () => Ignore): Try[T] = {
      t.doOnSuccess(_ => effect()).doOnError(_ => effect())
    }
  }

}
