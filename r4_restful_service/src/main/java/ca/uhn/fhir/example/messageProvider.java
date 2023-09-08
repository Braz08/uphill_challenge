package ca.uhn.fhir.example;


import org.bson.Document;
import org.bson.conversions.Bson;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;


import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.MessageHeader.MessageHeaderResponseComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import org.hl7.fhir.r4.model.MessageHeader.ResponseType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.UrlType;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class messageProvider {
	
	// it is an HAPI FHIR's Plain provider, so it does not "implements IResourceProvider" (it's not FHIR Resource-specific - used for server level operations mostly)

	IParser parser;
    MongoCollection<Document> encountersCollection;
    MongoCollection<Document> messageHeadersCollection;
    MongoCollection<Document> patientsCollection;
    Bundle generalRequestBundle;
	
	// constructor
	public messageProvider() {
		
		// Create a context (for parsing, etc.)
		FhirContext ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
        
        // get inteded MongoDB's collections
		MongoDBConnectionManager mongoDBConnectionManager = new MongoDBConnectionManager();
        encountersCollection = mongoDBConnectionManager.getencountersCollection();
        messageHeadersCollection = mongoDBConnectionManager.getmessageHeadersCollection();
        patientsCollection = mongoDBConnectionManager.getpatientsCollection();
	}
	
	// https://hapifhir.io/hapi-fhir/docs/server_plain/rest_operations_operations.html#server-level-operations
	// 5.6.3Server-Level Operations - Server-level operations do not operate on a specific resource type or instance, 
	// but rather operate globally on the server itself.
	// URL: [base]/$process-message
	@Operation(name="$process-message")
	public Bundle messageProcessing(@ResourceParam Bundle requestBundle) {

        generalRequestBundle = requestBundle;
		
		// initial request message validation steps (Resource's mandatory fields validation already checked by HAPI FHIR functionalities)
		
		// error if Bundle type is not "message"
		if (! requestBundle.getType().equals(Bundle.BundleType.MESSAGE)) {
			throw new InvalidRequestException("Bundle type must be 'message'");
		}
		// error if the first entry is not a MessageHeader
		Resource firstEntry = requestBundle.getEntryFirstRep().getResource();
		if ( ! (firstEntry instanceof MessageHeader) ) { 
			throw new InvalidRequestException("First Bundle entry must contain a MessageHeader Resource.");
		}
		//error if MessageHeader has an equal ID from other requests - a simplist way to prevent repeated requests
		String id_MsgHeaderString = firstEntry.getIdElement().getIdPart();
		if ( ! checkUniqueIdBoolean(id_MsgHeaderString,"MessageHeader") ) { 
			throw new InvalidRequestException("MessageHeader ID suggests that message was already received.");
		}
		// error if MessageHeader's focus is not an Encounter Resource
		MessageHeader theMessageHeader = (MessageHeader) firstEntry;
		if (theMessageHeader.hasFocus() &&  ! (theMessageHeader.getFocus().get(0).getReference().contains("Encounter")) ){
			throw new InvalidRequestException("MessageHeader's focus must be an Encounter resource.");
		}
		
		// handle each one of the entries of the Bundle
		for (BundleEntryComponent entry : requestBundle.getEntry() ) {
			if (entry.getResource() instanceof MessageHeader ) {
				MessageHeaderHandling( (MessageHeader) entry.getResource());
			} else if (entry.getResource() instanceof Encounter ) {
				EncounterHandling( (Encounter) entry.getResource());
			} else if (entry.getResource() instanceof Patient ) {
				PatientHandling( (Patient) entry.getResource());
			} else {
				throw new InvalidRequestException("Bundle entries must be either MessageHeader, Encounter or Patient.");
			}			
		}
		
		//create blank response Bundle w/ type "message"
		Bundle responseBundle = new Bundle().setType(Bundle.BundleType.MESSAGE);
		//response's MessageHeader - use infos from the message's MessageHeader
		MessageHeader responseMessageHeader = new MessageHeader().setEvent(theMessageHeader.getEventCoding())
				.setSource(new MessageSourceComponent(new UrlType("http://localhost:8080")))
				.setResponse(new MessageHeaderResponseComponent().setIdentifier(theMessageHeader.getIdElement().getIdPart())
				.setCode(ResponseType.OK)
				);
		//add MessageHeader Resource into a Bundle entry and then into the response Bundle
		BundleEntryComponent responseMessageHeaderEntry = new BundleEntryComponent();
		responseMessageHeaderEntry.setResource(responseMessageHeader);
		responseBundle.addEntry(responseMessageHeaderEntry);
		
		//add the resources present in the request (as stated in the FHIR specification)
		for (BundleEntryComponent entry : requestBundle.getEntry() ) {
			if ( ! (entry.getResource() instanceof MessageHeader)) {
				responseBundle.addEntry(entry);
			}
		}		
		
		return responseBundle;
	}
	
	private void MessageHeaderHandling (MessageHeader theMessageHeader) {
		//uniqueness of the id was already evaluated above
		//save MessageHeader Resource in MongoDB
		Document documentToAdd = Document.parse(parser.encodeResourceToString(theMessageHeader));
		messageHeadersCollection.insertOne(documentToAdd);
	}
	
	private void EncounterHandling (Encounter theEncounter) {
		//validate Encounter resource first before saving in the DB
		Boolean wrongIdentifierBoolean = true;
		//check if the identifier's system is "urn:uh-encounter-id"
		String idvalueString = null;
		for (org.hl7.fhir.r4.model.Identifier theIdentifier : theEncounter.getIdentifier() ) {
			if (theIdentifier.hasSystem()) {
				if (theIdentifier.getSystem().equals("urn:uh-encounter-id")) {
					wrongIdentifierBoolean = false;
					idvalueString = theEncounter.getIdElement().getIdPart();
				}
			}
		}
		if (wrongIdentifierBoolean) { // if there is no identifier whose system is urn:uh-encounter-id, then it is invalid
			throw new InvalidRequestException("Encounter identifier(s) are not recognized and should belong to the appropriate system.");
		}
		// Encounter Resource must have a status
		if (! theEncounter.hasStatus()) {
			throw new InvalidRequestException("Encounter must have a status.");
		}
		// Encounter Resource must have a service type and belong to the defined value set
		if ( ! theEncounter.hasServiceType()) {
			throw new InvalidRequestException("Encounter must have a service type.");
		} else if (! theEncounter.getServiceType().getCodingFirstRep().getSystem().equals("http://hl7.org/fhir/ValueSet/service-type")) {
			throw new InvalidRequestException("Encounter's serrvice type must be bounded to the FHIR terminology value set for 'service type'.");
		}
		
		//validate if Patient is referenced in the Encounter Resource
		if (! theEncounter.hasSubject()) {
			throw new InvalidRequestException("Encounter must have a subject.");
		}
		//validate if the mentioned Patient Resource is present as an entry in the request Bundle
		Boolean hasPatientBoolean = false; 
		for (BundleEntryComponent theEntryComponent : generalRequestBundle.getEntry()) {
			if (theEntryComponent.getResource().getIdElement().getIdPart().equals(
					theEncounter.getSubject().getReference().split("/")[1])) { // reference = Patient/patient_id
				hasPatientBoolean = true;
			}
		}
		if (! hasPatientBoolean) {
			throw new InvalidRequestException("Subject mentioned in the Encounter Resource must be stated in the request as an entry.");
		}
		
		//save in MongoDB - if id is unique (i.e, a Resource different from the others)
		if ( checkUniqueIdBoolean(idvalueString,"Encounter") ) {
			// create metadata indicating that it is version 1
			Meta addMeta = new Meta().setVersionId("1");
			theEncounter.setMeta(addMeta);
			Document documentToAdd = Document.parse(parser.encodeResourceToString(theEncounter));
			encountersCollection.insertOne(documentToAdd);
		} else { // Resource with the same Id already exists in the database - add with the latest versionID
			addResourceWithLatestVersionID(theEncounter, "Encounter");
		}
	}
	
	private void PatientHandling (Patient thePatient) {
        
		//validate Patient resource first
		Boolean wrongIdentifierBoolean = true;
		String idvalueString = null;
		for (org.hl7.fhir.r4.model.Identifier theIdentifier : thePatient.getIdentifier() ) {
			if (theIdentifier.hasSystem()) {
				if (theIdentifier.getSystem().equals("urn:uh-patient-id")) {
					wrongIdentifierBoolean = false;
					idvalueString = thePatient.getIdElement().getIdPart();
				}
			}
		}
		if (wrongIdentifierBoolean) { // if there is no identifier whose system is urn:uh-patient-id, then it is invalid
			throw new InvalidRequestException("Patient identifier(s) are not recognized and should belong to the appropriate system.");
		}
		
		if (! thePatient.hasName()) {
			throw new InvalidRequestException("Patient must have a name.");
		}
		if (! thePatient.hasContact()) {
			throw new InvalidRequestException("Patient must have a contact.");
		}
		if (! thePatient.hasGender()) {
			throw new InvalidRequestException("Patient must have a gender.");
		}
		if (! thePatient.hasBirthDate()) {
			throw new InvalidRequestException("Patient must have a birthdate.");
		}
		
		//save in MongoDB - if id is unique (i.e, a Resource different from the others)
		if ( checkUniqueIdBoolean(idvalueString,"Patient") ) {
			// create metadata indicating that it is version 1
			Meta addMeta = new Meta().setVersionId("1");
			thePatient.setMeta(addMeta);
			Document documentToAdd = Document.parse(parser.encodeResourceToString(thePatient));
			patientsCollection.insertOne(documentToAdd);
		} else { // Resource with the same Id already exists in the database - add with the latest versionID
			addResourceWithLatestVersionID(thePatient, "Patient");
		}
	}
	
	private Boolean checkUniqueIdBoolean (String theID, String theResource) {
		MongoCollection<Document> theCollection;
		if (theResource.equals("Patient")) {
			theCollection = patientsCollection;
		} else if (theResource.equals("Encounter")) {
			theCollection = encountersCollection;
		} else {
			theCollection = messageHeadersCollection;
		}
		
		// query filter
		Bson filter = Filters.eq("id", theID);
		// search query in the MongoDB database - try to find json document in the right collection with the same "id"
		MongoCursor<Document> cursor = theCollection.find(filter).iterator();
		// Check if the result is empty
		if (!cursor.hasNext()) {
		    // The result is empty
		    return true;
		} else {
			return false;
		}
	}
	
	private void addResourceWithLatestVersionID (Resource theResource, String whichResourceType) {
		//define collection to add the Resource
	    MongoCollection<Document> theCollection = null;
	    if (whichResourceType.equals("Patient")) {
	    	theCollection = patientsCollection;
	    } else {
	    	theCollection = encountersCollection;
	    }
	    
		// query filter
		Bson filter = Filters.eq("id", theResource.getIdElement().getIdPart());
		// search query in the MongoDB database - try to find json document with the same "id"
		MongoCursor<Document> cursor = theCollection.find(filter).iterator();
		
		// Check if the result is empty
		if (!cursor.hasNext()) {
		    // The result is empty
		    throw new ResourceNotFoundException(theResource.getIdElement().getIdPart());
		} else { // result is not empty
			// detect the last versionID of the Resource in the DB
			Integer lastVersion = 1;
			Resource checkingResource = null;
			while (cursor.hasNext()) {
				if (whichResourceType.equals("Patient")) {
					checkingResource = (Patient) parser.parseResource(Patient.class, cursor.next().toJson());
				} else {
					checkingResource = (Resource) parser.parseResource(Encounter.class, cursor.next().toJson());
				}
				//check versionId of the Resource
				if (checkingResource.hasMeta()) {
					if (checkingResource.getMeta().hasVersionId()) {
						Integer checkingVersionIdInteger = Integer.valueOf(checkingResource.getMeta().getVersionId());
						if (checkingVersionIdInteger.intValue() > lastVersion.intValue()) {
							lastVersion = checkingVersionIdInteger;
						}
					}
				}
			}
			// save Resource in the DB with the versionID = last versionID + 1
			Document theDocument = null;
			if (whichResourceType.equals("Patient")) {
				Patient addedPatient = (Patient) theResource;
				addedPatient.setMeta(new Meta().setVersionId(String.valueOf(lastVersion.intValue()+1)));
				theDocument = Document.parse(parser.encodeResourceToString(addedPatient));
			} else { // Encounter
				Encounter addedEncounter = (Encounter) theResource;
				addedEncounter.setMeta(new Meta().setVersionId(String.valueOf(lastVersion.intValue()+1)));
				theDocument = Document.parse(parser.encodeResourceToString(addedEncounter));
			}
			theCollection.insertOne(theDocument);
		}
	}
	
	
}
