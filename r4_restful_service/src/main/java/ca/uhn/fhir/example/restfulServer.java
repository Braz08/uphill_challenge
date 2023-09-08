package ca.uhn.fhir.example;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.RequestValidatingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseValidatingInterceptor;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.example.encounterProvider;
import ca.uhn.fhir.example.messageProvider;
import ca.uhn.fhir.example.AuthorizationServerInterceptor;


@WebServlet("/*")
public class restfulServer extends RestfulServer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1826824711660710811L;

	@Override
	protected void initialize() throws ServletException {
		// Create a context for the appropriate version
		FhirContext ctx = FhirContext.forR4();
		setFhirContext(ctx); // This is an R4 server
		
		// Register resource providers in the server - so that server may know where to direct requests
		registerProvider(new patientProvider());
		registerProvider(new encounterProvider());
		registerProvider(new messageProvider());
		
		// Register interceptors in the server
		registerInterceptor(new AuthorizationServerInterceptor()); // deals with validating authorization tokens
		registerInterceptor(new ResponseHighlighterInterceptor()); // Format the responses in nice HTML
		
		// Create a context for the appropriate version (for parsing, etc.)
		//FhirContext ctx = FhirContext.forR4();
		
		// Create an interceptor to validate incoming requests
		RequestValidatingInterceptor requestInterceptor = new RequestValidatingInterceptor();
		  
		// Register a validator module (you could also use SchemaBaseValidator and/or SchematronBaseValidator)
		//requestInterceptor.addValidatorModule(new FhirInstanceValidator(ctx));
		// ---> this code line is commented because a bug was found related that gives an error (even when fullUrl and id match):
		// ---> "Except for transactions and batches, each entry in a Bundle must have a fullUrl which is the identity of the resource in the entry" <location value="Bundle.entry[0]"></location>
		  
		requestInterceptor.setFailOnSeverity(ResultSeverityEnum.ERROR); 
		requestInterceptor.setAddResponseHeaderOnSeverity(ResultSeverityEnum.INFORMATION);
		requestInterceptor.setResponseHeaderValue("Validation on ${line}: ${message} ${severity}");
		requestInterceptor.setResponseHeaderValueNoIssues("No issues detected");
		  
		// Now register in the server the request validation interceptor
		registerInterceptor(requestInterceptor);
		
		// Create an interceptor to validate responses
		// This is configured in the same way as above
		ResponseValidatingInterceptor responseInterceptor = new ResponseValidatingInterceptor();
		//responseInterceptor.addValidatorModule(new FhirInstanceValidator(ctx));
		responseInterceptor.setFailOnSeverity(ResultSeverityEnum.ERROR);
		responseInterceptor.setAddResponseHeaderOnSeverity(ResultSeverityEnum.INFORMATION);
		responseInterceptor.setResponseHeaderValue("Validation on ${line}: ${message} ${severity}");
		responseInterceptor.setResponseHeaderValueNoIssues("No issues detected");
		registerInterceptor(responseInterceptor);
		
		// OpenAPI interceptor
	    OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
	    registerInterceptor(openApiInterceptor);
		
	}
}
