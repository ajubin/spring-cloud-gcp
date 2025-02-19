/*
 * Copyright 2017-2019 the original author or authors.
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

package com.google.cloud.spring.vision;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesRequest;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageContext;
import com.google.protobuf.ByteString;
import com.google.rpc.Code;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Spring Template offering convenience methods for interacting with the Cloud Vision APIs.
 *
 * @since 1.1
 */
public class CloudVisionTemplate {

  private final ImageAnnotatorClient imageAnnotatorClient;

  public CloudVisionTemplate(ImageAnnotatorClient imageAnnotatorClient) {
    Assert.notNull(imageAnnotatorClient, "imageAnnotatorClient must not be null.");
    this.imageAnnotatorClient = imageAnnotatorClient;
  }

  /**
   * Extract the text out of an image and return the result as a String.
   *
   * @param imageResource the image one wishes to analyze
   * @return the text extracted from the image aggregated to a String
   * @throws CloudVisionException if the image could not be read or if text extraction failed
   */
  public String extractTextFromImage(Resource imageResource) {
    return extractTextFromImage(imageResource, ImageContext.getDefaultInstance());
  }

  /**
   * Extract the text out of an image and return the result as a String.
   *
   * @param imageResource the image one wishes to analyze
   * @param imageContext the image context to customize the text extraction request
   * @return the text extracted from the image aggregated to a String
   * @throws CloudVisionException if the image could not be read or if text extraction failed
   */
  public String extractTextFromImage(Resource imageResource, ImageContext imageContext) {
    AnnotateImageResponse response = analyzeImage(imageResource, imageContext, Type.TEXT_DETECTION);

    String result = response.getFullTextAnnotation().getText();
    if (result.isEmpty() && response.getError().getCode() != Code.OK.getNumber()) {
      throw new CloudVisionException(response.getError().getMessage());
    }

    return result;
  }

  /**
   * Analyze an image and extract the features of the image specified by {@code featureTypes}.
   *
   * <p>A feature describes the kind of Cloud Vision analysis one wishes to perform on an image,
   * such as text detection, image labelling, facial detection, etc. A full list of feature types
   * can be found in {@link Feature.Type}.
   *
   * @param imageResource the image one wishes to analyze. The Cloud Vision APIs support image
   *     formats described here: https://cloud.google.com/vision/docs/supported-files
   * @param featureTypes the types of image analysis to perform on the image
   * @return the results of image analyses
   * @throws CloudVisionException if the image could not be read or if a malformed response is
   *     received from the Cloud Vision APIs
   */
  public AnnotateImageResponse analyzeImage(Resource imageResource, Feature.Type... featureTypes) {
    return analyzeImage(imageResource, ImageContext.getDefaultInstance(), featureTypes);
  }

  /**
   * Analyze an image and extract the features of the image specified by {@code featureTypes}.
   *
   * <p>A feature describes the kind of Cloud Vision analysis one wishes to perform on an image,
   * such as text detection, image labelling, facial detection, etc. A full list of feature types
   * can be found in {@link Feature.Type}.
   *
   * @param imageResource the image one wishes to analyze. The Cloud Vision APIs support image
   *     formats described here: https://cloud.google.com/vision/docs/supported-files
   * @param imageContext the image context used to customize the Vision API request
   * @param featureTypes the types of image analysis to perform on the image
   * @return the results of image analyses
   * @throws CloudVisionException if the image could not be read or if a malformed response is
   *     received from the Cloud Vision APIs
   */
  public AnnotateImageResponse analyzeImage(
      Resource imageResource, ImageContext imageContext, Feature.Type... featureTypes) {
    ByteString imgBytes;
    try {
      imgBytes = ByteString.readFrom(imageResource.getInputStream());
    } catch (IOException ex) {
      throw new CloudVisionException("Failed to read image bytes from provided resource.", ex);
    }

    Image image = Image.newBuilder().setContent(imgBytes).build();

    List<Feature> featureList =
        Arrays.stream(featureTypes)
            .map(featureType -> Feature.newBuilder().setType(featureType).build())
            .collect(Collectors.toList());

    BatchAnnotateImagesRequest request =
        BatchAnnotateImagesRequest.newBuilder()
            .addRequests(
                AnnotateImageRequest.newBuilder()
                    .addAllFeatures(featureList)
                    .setImageContext(imageContext)
                    .setImage(image))
            .build();

    BatchAnnotateImagesResponse batchResponse =
        this.imageAnnotatorClient.batchAnnotateImages(request);
    List<AnnotateImageResponse> annotateImageResponses = batchResponse.getResponsesList();

    if (!annotateImageResponses.isEmpty()) {
      return annotateImageResponses.get(0);
    } else {
      throw new CloudVisionException(
          "Failed to receive valid response Vision APIs; empty response received.");
    }
  }
}
