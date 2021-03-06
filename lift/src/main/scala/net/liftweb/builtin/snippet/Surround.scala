/*
 * Copyright 2007-2009 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.liftweb.builtin.snippet

import scala.xml._
import net.liftweb.http._
import net.liftweb.util._
import Helpers._

object Surround extends DispatchSnippet {

  def dispatch : DispatchIt = {
    case _ => render _
  }

  def render(kids: NodeSeq) : NodeSeq =
  (for {ctx <- S.session ?~ ("FIX"+"ME: Invalid session")
        req <- S.request ?~ ("FIX"+"ME: Invalid request")
    } yield {
      val paramElements: Seq[Node] = findElems(kids)(e => {
       val oldSyntax = e.label == "with-param" && e.prefix == "lift"
       val newSyntax = e.label == "with-param" && e.prefix == "lift-tag"

       if (oldSyntax) {
         Log.warn("DEPRECATE WARNING: <lift:with-param> is deprecated. Please use <lift-tag:with-param> instead !")
       }

       oldSyntax || newSyntax
      })

      val params: Seq[(String, NodeSeq)] =
      for {e <- paramElements
           name <- e.attributes.get("name")
      } yield (name.text, ctx.processSurroundAndInclude(PageName.get, e.child))

      val mainParam = (S.attr.~("at").map(_.text).
                       getOrElse("main"), ctx.processSurroundAndInclude(PageName.get, kids))
      val paramsMap = collection.immutable.Map(params: _*) + mainParam
      ctx.findAndMerge(S.attr.~("with"), paramsMap)

    }) match {
    case Full(x) => x
    case Empty => Comment("FIX"+ "ME: session or request are invalid")
    case Failure(msg, _, _) => Comment(msg)
  }
}
