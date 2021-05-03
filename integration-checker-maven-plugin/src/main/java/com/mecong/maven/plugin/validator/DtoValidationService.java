package com.mecong.maven.plugin.validator;

import com.google.gson.stream.JsonReader;
import com.mecong.maven.plugin.validator.dto.BoundDtoInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.mecong.maven.plugin.validator.IntegrationCheckerMojo.APPLICATION_JSON;
import static org.apache.http.HttpHeaders.ACCEPT;

@Named
@Singleton
public class DtoValidationService {

    public void verifyDto(BoundDtoInfo boundDtoInfo, String integrationCheckerUrl, Log log) throws MojoExecutionException {
        String boundUrl = integrationCheckerUrl + "/" + boundDtoInfo.getBoundId();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(boundUrl);

            httpGet.setHeader(ACCEPT, APPLICATION_JSON);

            CloseableHttpResponse response = client.execute(httpGet);
            log.debug("Response: " + response.getStatusLine().toString());
            if (response.getStatusLine().getStatusCode() >= 300) {
                log.warn("Cannot find bound resource: " + boundUrl);
                return;
            }

            parseResponse(boundDtoInfo, log, response);
        } catch (IOException e) {
            log.error("Cannot get " + boundUrl + " Error: " + e.getMessage());
        }
    }

    private void parseResponse(BoundDtoInfo boundDtoInfo, Log log, CloseableHttpResponse response)
            throws IOException, MojoExecutionException {
        try (JsonReader reader = new JsonReader(new BufferedReader(new InputStreamReader(response.getEntity().getContent())))) {
            reader.beginObject();

            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("dtoFields")) {
                    String exposedDtoFields = reader.nextString();
                    boolean isDtosEqual = exposedDtoFields.equals(boundDtoInfo.getDtoFields());

                    if (!isDtosEqual) {
                        String message = String.format("Verification failed for type: %s against: %s. Expected %s, but found %s",
                                boundDtoInfo.getType(), boundDtoInfo.getBoundId(), boundDtoInfo.getDtoFields(), exposedDtoFields);
                        throw new MojoExecutionException(message);
                    } else {
                        log.info("Verifying type: " + boundDtoInfo.getType() +
                                " against: " + boundDtoInfo.getBoundId() + " --- OK");
                    }
                    break;
                }
            }
        }
    }

}
