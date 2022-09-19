/**
 * Datadog Telemetry API Generated by Openapi Generator
 * https://github.com/openapitools/openapi-generator
 *
 * <p>The version of the OpenAPI document: 1.0.0
 *
 * <p>NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 */
package datadog.telemetry.api;

import java.util.ArrayList;
import java.util.List;

public class AppDependenciesLoaded extends Payload {

  @com.squareup.moshi.Json(name = "dependencies")
  private List<Dependency> dependencies = new ArrayList<Dependency>();

  /**
   * Get dependencies
   *
   * @return dependencies
   */
  public List<Dependency> getDependencies() {
    return dependencies;
  }

  /** Set dependencies */
  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
  }

  public AppDependenciesLoaded dependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  public AppDependenciesLoaded addDependenciesItem(Dependency dependenciesItem) {
    this.dependencies.add(dependenciesItem);
    return this;
  }

  /** Create a string representation of this pojo. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AppDependenciesLoaded {\n");
    sb.append("    ").append(super.toString()).append("\n");
    sb.append("    dependencies: ").append(dependencies).append("\n");
    sb.append("}");
    return sb.toString();
  }
}