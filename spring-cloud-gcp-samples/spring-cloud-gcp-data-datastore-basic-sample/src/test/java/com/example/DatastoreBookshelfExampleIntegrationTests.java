/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.shell.Shell;
import org.springframework.shell.jline.InteractiveShellApplicationRunner;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Tests for the Book Shelf sample app. */
// Please use "-Dit.datastore=true" to enable the tests.
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = DatastoreBookshelfExample.class,
    properties = {InteractiveShellApplicationRunner.SPRING_SHELL_INTERACTIVE_ENABLED + "=" + false},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "it.datastore", matches = "true")
class DatastoreBookshelfExampleIntegrationTests {

  @Autowired private Shell shell;

  @Autowired private DatastoreTemplate datastoreTemplate;

  @Autowired private BookRepository bookRepository;

  @Autowired private TestRestTemplate restTemplate;

  @AfterEach
  void cleanUp() {
    this.datastoreTemplate.deleteAll(Book.class);
  }

  @Test
  void testSerializedPage() {
    Book book = new Book("Book1", "Author1", 2019);
    book.id = 12345678L;
    this.bookRepository.save(book);
    Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> this.bookRepository.count() == 1);
    String responseBody = sendRequest("/allbooksserialized", null, HttpMethod.GET);
    assertThat(responseBody).contains("content\":[{\"id\":12345678}],\"pageable\":");
    assertThat(responseBody).containsPattern("\"urlSafeCursor\":\".+\"");
  }

  @Test
  void testSaveBook() {
    String book1 = (String) this.shell.evaluate(() -> "save-book book1 author1 1984");
    String book2 = (String) this.shell.evaluate(() -> "save-book book2 author2 2000");

    String allBooks = (String) this.shell.evaluate(() -> "find-all-books");
    assertThat(allBooks).containsSequence(book1);
    assertThat(allBooks).containsSequence(book2);

    assertThat(this.shell.evaluate(() -> "find-by-author author1")).isEqualTo("[" + book1 + "]");

    assertThat(this.shell.evaluate(() -> "find-by-author-year author2 2000"))
        .isEqualTo("[" + book2 + "]");

    assertThat(this.shell.evaluate(() -> "find-by-year-greater-than 1985"))
        .isEqualTo("[" + book2 + "]");

    this.shell.evaluate(() -> "remove-all-books");

    assertThat(this.shell.evaluate(() -> "find-all-books")).isEqualTo("[]");
  }

  private String sendRequest(String url, String json, HttpMethod method) {
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("Content-Type", "application/json");

    HttpEntity<String> entity = new HttpEntity<>(json, map);
    ResponseEntity<String> response = this.restTemplate.exchange(url, method, entity, String.class);
    return response.getBody();
  }
}
