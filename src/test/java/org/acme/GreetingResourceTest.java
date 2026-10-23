package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class GreetingResourceTest {

    @Test
    void unauthenticatedSpaFetchGets401() {
        // /decks requires the "user" role. A SPA fetch carries an Origin header, which
        // SpaJavaScriptRequestChecker classifies as a JavaScript request — and with
        // java-script-auto-redirect=false the mechanism must answer 401 (not a 302 to the
        // provider) so the SPA can detect the missing session and navigate to /login.
        given()
            .header("Origin", "http://localhost:3000")
          .when().get("/decks")
          .then()
             .statusCode(401);
    }
}
