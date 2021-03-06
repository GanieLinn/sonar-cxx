/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2020 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.cxx.sensors.tests.dotnet;

// origin https://github.com/SonarSource/sonar-dotnet-tests-library/
// SonarQube .NET Tests Library
// Copyright (C) 2014-2017 SonarSource SA
// mailto:info AT sonarsource DOT com
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.cxx.CxxLanguage;

public class CxxUnitTestResultsImportSensor implements ProjectSensor {

  private final WildcardPatternFileProvider wildcardPatternFileProvider
                                              = new WildcardPatternFileProvider(new File("."), File.separator);
  private final CxxUnitTestResultsAggregator unitTestResultsAggregator;
  protected final CxxLanguage language;

  public CxxUnitTestResultsImportSensor(CxxUnitTestResultsAggregator unitTestResultsAggregator,
                                        Configuration settings) {
    this.unitTestResultsAggregator = unitTestResultsAggregator;
    this.language = new CxxLanguage(settings);
  }

  public static List<PropertyDefinition> properties() {
    return Collections.unmodifiableList(Arrays.asList(
      PropertyDefinition.builder(UnitTestConfiguration.VISUAL_STUDIO_TEST_RESULTS_PROPERTY_KEY)
        .multiValues(true)
        .name("VSTest Reports Paths")
        .description(
          "Paths to VSTest reports. Multiple paths may be comma-delimited, or included via wildcards."
            + " Note that while measures such as the number of tests are displayed at project level, no drilldown is"
            + " available.\n"
            + "Example: \"report.trx\", \"report1.trx,report2.trx\" or \"C:/report.trx\"")
        .subCategory("VSTest")
        .onQualifiers(Qualifiers.PROJECT)
        .build(),
      PropertyDefinition.builder(UnitTestConfiguration.XUNIT_TEST_RESULTS_PROPERTY_KEY)
        .multiValues(true)
        .name("xUnit Test Reports Paths")
        .description(
          "Paths to xUnit execution reports. Multiple paths may be comma-delimited, or included via wildcards."
            + " Note that while measures such as the number of tests are displayed at project level, no drilldown is"
            + " available.\n"
            + "Example: \"report.xml\", \"report1.xml,report2.xml\" or \"C:/report.xml\"")
        .subCategory("xUnit Test")
        .onQualifiers(Qualifiers.PROJECT)
        .build(),
      PropertyDefinition.builder(UnitTestConfiguration.NUNIT_TEST_RESULTS_PROPERTY_KEY)
        .multiValues(true)
        .name("NUnit Test Reports Paths")
        .description(
          "Paths to NUnit execution reports. Multiple paths may be comma-delimited, or included via wildcards."
            + " Note that while measures such as the number of tests are displayed at project level, no drilldown is"
            + " available.\n"
            + "Example: \"TestResult.xml\", \"TestResult1.xml,TestResult2.xml\" or \"C:/TestResult.xml\"")
        .subCategory("NUnit Test")
        .onQualifiers(Qualifiers.PROJECT)
        .build()
    ));
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    String name = String.format("%s Unit Test Results Import", this.language.getName());
    descriptor.name(name);
    descriptor.onlyWhenConfiguration(conf -> new UnitTestConfiguration(language, conf).hasUnitTestResultsProperty());
    descriptor.onlyOnLanguage(this.language.getKey());
  }

  @Override
  public void execute(SensorContext context) {
    analyze(context, new UnitTestResults(), new UnitTestConfiguration(language, context.config()));
  }

  void analyze(SensorContext context, UnitTestResults unitTestResults, UnitTestConfiguration unitTestConf) {
    UnitTestResults aggregatedResults = unitTestResultsAggregator.aggregate(wildcardPatternFileProvider,
                                                                            unitTestResults, unitTestConf);

    context.<Integer>newMeasure()
      .forMetric(CoreMetrics.TESTS)
      .on(context.project())
      .withValue(aggregatedResults.tests())
      .save();
    context.<Integer>newMeasure()
      .forMetric(CoreMetrics.TEST_ERRORS)
      .on(context.project())
      .withValue(aggregatedResults.errors())
      .save();
    context.<Integer>newMeasure()
      .forMetric(CoreMetrics.TEST_FAILURES)
      .on(context.project())
      .withValue(aggregatedResults.failures())
      .save();
    context.<Integer>newMeasure()
      .forMetric(CoreMetrics.SKIPPED_TESTS)
      .on(context.project())
      .withValue(aggregatedResults.skipped())
      .save();

    Long executionTime = aggregatedResults.executionTime();
    if (executionTime != null) {
      context.<Long>newMeasure()
        .forMetric(CoreMetrics.TEST_EXECUTION_TIME)
        .on(context.project())
        .withValue(executionTime)
        .save();
    }
  }

}
