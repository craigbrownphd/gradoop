/*
 * This file is part of Gradoop.
 *
 * Gradoop is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gradoop is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Gradoop. If not, see <http://www.gnu.org/licenses/>.
 */

package org.gradoop.flink.datagen.transactions.foodbroker.generators;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.gradoop.common.model.impl.pojo.VertexFactory;
import org.gradoop.flink.datagen.transactions.foodbroker.config.FoodBrokerConfig;
import org.gradoop.flink.datagen.transactions.foodbroker.tuples.MasterDataSeed;
import org.gradoop.flink.util.GradoopFlinkConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract generator for master data object generators.
 */
public abstract class AbstractMasterDataGenerator
  implements MasterDataGenerator {
  /**
   * FoodBroker configuration.
   */
  protected final FoodBrokerConfig foodBrokerConfig;
  /**
   * Flink execution environment.
   */
  protected final ExecutionEnvironment env;
  /**
   * EPGM vertex factory.
   */
  protected final VertexFactory vertexFactory;

  /**
   * Valued constructor.
   *
   * @param gradoopFlinkConfig Gradoop Flink configuration
   * @param foodBrokerConfig FoodBroker configuration
   */
  AbstractMasterDataGenerator(
    GradoopFlinkConfig gradoopFlinkConfig, FoodBrokerConfig foodBrokerConfig) {
    this.foodBrokerConfig = foodBrokerConfig;
    this.env = gradoopFlinkConfig.getExecutionEnvironment();
    this.vertexFactory = gradoopFlinkConfig.getVertexFactory();
  }

  /**
   * Generates seeds for master data objects.
   *
   * @param className class name of the master data object
   * @return list of master data seeds
   */
  List<MasterDataSeed> getMasterDataSeeds(String className) {
    Double goodRatio = foodBrokerConfig.getMasterDataGoodRatio(className);

    Double badRatio = foodBrokerConfig.getMasterDataBadRatio(className);

    Integer count = foodBrokerConfig.getMasterDataCount(className);

    Integer goodCount = (int) Math.round(count * goodRatio);
    Integer badCount = (int) Math.round(count * badRatio);
    Integer normalCount = count - goodCount - badCount;

    List<MasterDataSeed> seedList = new ArrayList<>();

    Map<Float, Integer> qualityCounts = new HashMap<>();

    qualityCounts.put(foodBrokerConfig.getQualityGood(), goodCount);
    qualityCounts.put(foodBrokerConfig.getQualityNormal(), normalCount);
    qualityCounts.put(foodBrokerConfig.getQualityBad(), badCount);
    //create for each quality the amount of seeds, amount defined by config file
    int j = 0;
    for (Map.Entry<Float, Integer> qualityCount : qualityCounts.entrySet()) {
      Float quality = qualityCount.getKey();
      for (int i = 1; i <= qualityCount.getValue(); i++) {
        seedList.add(new MasterDataSeed(i + j , quality));
      }
      j += qualityCount.getValue();
    }
    Collections.shuffle(seedList);

    return seedList;
  }
}
