/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.openai;

import org.okapi.oscar.secrets.ApiKeyProvider;
import org.okapi.oscar.secrets.SecretsPathReader;
import org.okapi.oscar.spring.cfg.OpenAiCfg;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "okapi.oscar.model.core-model-provider", havingValue = "openai")
public class OpenAiConfig {
  @Bean
  public ApiKeyProvider openAiApiKeyProvider(
      OpenAiCfg config, SecretsPathReader secretsPathReader) {
    return secretsPathReader.resolve(config.getApiKeyPath());
  }

  @Bean
  public OpenAiApi openAiApi(OpenAiCfg cfg, ApiKeyProvider openAiApiKeyProvider) {
    String baseUrl = cfg.getBaseUrl();
    return OpenAiApi.builder().baseUrl(baseUrl).apiKey(openAiApiKeyProvider.getKey()).build();
  }

  @Bean
  public OpenAiChatModel openAiChatModel(OpenAiCfg cfg, OpenAiApi openAiApi) {
    var options = OpenAiChatOptions.builder().model(cfg.getModel()).build();
    return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
  }

  @Bean
  public ChatClient chatClient(OpenAiChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }
}
