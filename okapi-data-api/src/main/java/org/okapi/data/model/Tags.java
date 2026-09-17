/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Tags {
  private List<String> tags = new ArrayList<>();

  public Tags() {}

  public Tags(List<String> tags) {
    this.tags = new ArrayList<>(tags);
  }

  public void addTag(String tag) {
    tags.add(tag);
  }

  public void removeTag(String tag) {
    tags.remove(tag);
  }

  public void setTags(List<String> tags) {
    this.tags = new ArrayList<>(tags);
  }

  public List<String> asList() {
    return tags;
  }

  public static Tags of(List<String> tags) {
    return new Tags(tags);
  }

  public static Tags of(String... tags) {
    return new Tags(Arrays.asList(tags));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Tags that && Objects.equals(tags, that.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tags);
  }
}
