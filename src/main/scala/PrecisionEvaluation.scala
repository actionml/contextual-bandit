/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.template.classification

import org.apache.predictionio.controller.OptionAverageMetric
import org.apache.predictionio.controller.EmptyEvaluationInfo
import org.apache.predictionio.controller.Evaluation

case class Precision(variant:Double)
  extends OptionAverageMetric[EmptyEvaluationInfo, Query, PredictedResult, ActualResult] {
  override def header: String = s"Precision(variant = $variant)"

  def calculate(query: Query, predicted: PredictedResult, actual: ActualResult)
  : Option[Double] = {
    if (predicted.variant == variant) {
      if (predicted.variant == actual.variant) {
        Some(1.0)  // True positive
      } else {
        Some(0.0)  // False positive
      }
    } else {
      None  // Unrelated case for calcuating precision
    }
  }
}

object PrecisionEvaluation extends Evaluation {
  engineMetric = (ClassificationEngine(), new Precision(variant = 1.0))
}
