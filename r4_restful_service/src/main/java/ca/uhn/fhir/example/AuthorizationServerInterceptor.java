package ca.uhn.fhir.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;

public class AuthorizationServerInterceptor {

	// interceptor defined in the following Pointcut (see pointcuts: https://hapifhir.io/hapi-fhir/docs/interceptors/server_pointcuts.html)
   @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
   public void authorizationCheck(RequestDetails theRequestDetails, HttpServletRequest theRequest) throws IOException {
	   
	   // extract sent authorization token
	   String authHeader = theRequest.getHeader("Authorization");
	   //the URL for the request that is going to be placed at the Spring Authorization Server
	   String theURL = String.format("http://localhost:9000/oauth2/introspect?token=%s",authHeader); // it should not be hardcoded in the future
		
		// https://www.digitalocean.com/community/tutorials/java-httpurlconnection-example-java-http-request-get-post
		// https://www.baeldung.com/java-http-request
		URL obj = new URL(theURL);
		// establish/open a connection to the uppermentioned URL
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		
		String auth = "client" + ":" + "secret";
		// enconde authorization credentials
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
		// set Basic authorization credentials needed for the request
		String authHeaderValue = "Basic " + new String(encodedAuth);
		con.setRequestProperty("Authorization", authHeaderValue);
		
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		int responseCode = con.getResponseCode();
		
		if (responseCode == HttpURLConnection.HTTP_OK) { // verify if request was successful
			//read the body of the response
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
		 	}
			in.close();
			// evaluate if the response states that the authorization token is active or not (valid or not)
			Boolean bool = Boolean.valueOf(response.toString().split("\"active\":")[1].split(",")[0]);
			if (bool == false) { //token not active
				throw new ForbiddenOperationException("Access token provided is invalid or expired");
			} // else, token is valid and action must not be taken

		} else {
			// to inform as a log in the terminal
			System.out.println("GET request did not work.");
		}
   }
}
