import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import static org.apache.http.HttpStatus.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

import static org.hamcrest.Matchers.*;

import java.util.function.Consumer;

public class APIVacancyTextTest {
    // Other solutions for token keeping were terribly inconvenient
    // Just try to remember to delete token before git add :)
    // Btw shouldn't tests and token be available for same people?
    // i.e. if tests are public, then there should be public token,
    // else there is no need to hide that token in private hub imo.
    // Enter your OAuth2 access token here
    private static final String authKey = "";

    private static ResponseSpecification foundAnyRespSpec;

    @BeforeAll
    private static void configureSpecs() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                                                .setBaseUri("https://api.hh.ru")
                                                .setBasePath("/vacancies")
                                                .setAuth(RestAssured.oauth2(authKey))
                                                .addHeader("User-Agent", "")
                                                .setContentType(ContentType.JSON)
                                                .build();

        foundAnyRespSpec = new ResponseSpecBuilder()
                                .expectStatusCode(SC_OK)
                                .expectBody("found", greaterThan(0))
                                .build();
    }

    @Test
    public void emptyRequest() {
        when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void emptyText() {
        given()
            .param("text", "")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void regularText() {
        given()
            .param("text", "тестировщик")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void regularTextWithQuotation() {
        given()
            .param("text", "\"тестировщик junior\"")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void emptyTextWithQuotation() {
        given()
            .param("text", "\"\"")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    private String getGroovyScriptCheckingEveryItemContains(String text) {
        return "items*.any{ it.getValue().toString().toLowerCase().contains('" + text.toLowerCase() + "') }";
    }

    // Case insensitive
    // Looks just for text value entry, which is not always standalone, may be part of bigger word
    public void testThatEveryResponseItemContains(String textToRequest, String textToSearch) {
        Consumer<ValidatableResponse> checkIfAnyItemWithoutText =
            (responseToValidate) -> {
                responseToValidate.log().ifValidationFails();
                responseToValidate.body(
                    getGroovyScriptCheckingEveryItemContains(textToSearch),
                    not(hasItem(false))
                );
            };

        lookThroughPages(textToRequest, checkIfAnyItemWithoutText);
    }

    // According to https://hh.ru/article/1175#uchet-slovoform there should be only
    // items with any field containing requested text value exactly as it is (case insensitive tho).
    @Test
    public void wordWithExclamation() {
        String text = "Тестировщик";
        testThatEveryResponseItemContains("!" + text, text);
    }

    @Test
    public void sentenceWithExclamation() {
        String text = "Тестировщик Junior";
        testThatEveryResponseItemContains("!\"" + text + "\"", text);
    }

    @Test
    public void onlyExclamation() {
        given()
            .param("text", "!")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void exclamationWithEmptyQuote() {
        given()
            .param("text", "!\"\"")
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec);
    }

    @Test
    public void wordExclusion() {
        String includedWord = "Тестировщик";
        String excludedWord = "Junior";
        String requestText = includedWord + " not !" + excludedWord;

        Consumer<ValidatableResponse> checkIfExclusionWorks =
            (responseToValidate) -> {
                responseToValidate.log().ifValidationFails();
                responseToValidate.body(
                    getGroovyScriptCheckingEveryItemContains(excludedWord),
                    not(hasItem(true))
                );
            };

        lookThroughPages(requestText, checkIfExclusionWorks);
    }

    @Test
    public void wordWithAsterisk() {
        String text = "Гео";
        testThatEveryResponseItemContains(text + "*", text);
    }

    // Be aware! This won't check all items, just some.
    // You can change it, if you need thourough check, or to see more presice log.
    private void lookThroughPages(
            String textToRequest,
            Consumer<ValidatableResponse> responseValidator
        ) {
        final int maxPagesToLook = 50;
        final int perPage = 100;

        RequestSpecification requestSpecification = new RequestSpecBuilder()
                                                        .addParam("text", textToRequest)
                                                        .addParam("per_page", String.valueOf(perPage))
                                                        .build();

        int pagesToLookCount =
        given()
            .spec(requestSpecification)
        .when()
            .get()
        .then()
            .spec(foundAnyRespSpec)
            .extract().path("pages");

        if (maxPagesToLook > 0)
            pagesToLookCount = Math.min(pagesToLookCount, maxPagesToLook);

        for (int curPageIdx = 0; curPageIdx < pagesToLookCount; ++curPageIdx) {
            ValidatableResponse respToValidate =
            given()
                .spec(requestSpecification)
                .param("page", String.valueOf(curPageIdx))
            .when()
                .get()
            .then()
                .statusCode(SC_OK);
            responseValidator.accept(respToValidate);
        }
    }
}
