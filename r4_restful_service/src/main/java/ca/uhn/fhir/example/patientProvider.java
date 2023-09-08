package ca.uhn.fhir.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class patientProvider implements IResourceProvider {
	
	// method that allows the restfulserver to know which FHIR Resource this HAPI FHIR's Resource Provider manages
	public Class<? extends IBaseResource> getResourceType() {
		return Patient.class;
	}
	
	IParser parser;	
    MongoCollection<Document> patientsCollection;

    /**
     * Constructor
     */
    public patientProvider() {
    	
		// Create a context (for parsing, etc.)
		FhirContext ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
        
        // get pointer to MongoDB's collection where Patient Resources are saved with the help of the auxiliar class MongoDBConnectionManager
    	MongoDBConnectionManager mongoDBConnectionManager = new MongoDBConnectionManager();
    	patientsCollection = mongoDBConnectionManager.getpatientsCollection();
    }

    /**
	 * The "@Read" annotation indicates that this method supports the read operation. It takes one argument, the Resource type being returned.
	 * 
	 * @param theId
	 *            The read operation takes one parameter, which must be of type IdDt and must be annotated with the "@Read.IdParam" annotation.
	 * @return Returns a resource matching this identifier, or null if none exists.
	 */
    @Read(version = true)
    public Patient readPatient(@IdParam IdType theId){
    	//check if Resource has version
    	Patient returnPatient = null;
    	if (theId.hasVersionIdPart()) {
    		returnPatient = fetchPatientWithVersion(theId);
    	} else {
    		//get Resource from the database (the lastest version, if it exists several versions of the Resource with such Id)
    		returnPatient = fetchPatientNoVersion(theId.getIdPart());
    	}
    	return returnPatient;
    }
      
	private Patient fetchPatientNoVersion (String theID) {
		// query filter
		Bson filter = Filters.eq("id", theID);
		// search query in the MongoDB database - try to find json document with the same "id"
		MongoCursor<Document> cursor = patientsCollection.find(filter).iterator();
		
		// Check if the result is empty
		if (!cursor.hasNext()) {
		    // The result is empty
		    throw new ResourceNotFoundException(theID);
		} else { // result is not empty
			// check if the first Document in the cursor has versionID (if not, that means this is the only one in the DB)
			Patient latestPatientVersion = parser.parseResource(Patient.class, cursor.next().toJson());
			Integer lastVersion = null;
			if (latestPatientVersion.hasMeta()) { // versionID is in the meta field
				if (latestPatientVersion.getMeta().hasVersionId()) { // if it has versionId, it means that are other versions in the DB (continues below in order to achieve the latest version)
					lastVersion = Integer.valueOf(latestPatientVersion.getMeta().getVersionId());
				} else {
					return latestPatientVersion; // as it is the only Patient with such ID in the database (it has metadata, but no version information)
				}
			} else {
				return latestPatientVersion; // as it is the only Patient with such ID in the database
			}
			
			//if method continues, there is more than one version of the Resource in the DB - return the latest
			while (cursor.hasNext()) {
				Patient checkingPatient = parser.parseResource(Patient.class, cursor.next().toJson());
				if (checkingPatient.hasMeta()) {
					if (checkingPatient.getMeta().hasVersionId()) {
						Integer checkingVersionIdInteger = Integer.valueOf(checkingPatient.getMeta().getVersionId());
						if (checkingVersionIdInteger.intValue() > lastVersion.intValue()) {
							lastVersion = checkingVersionIdInteger;
							latestPatientVersion = checkingPatient;
						}
					}
				}
			}
			return latestPatientVersion;
		}
	}
	
	private Patient fetchPatientWithVersion (IdType theIdType) {
		//Define filter for ID
		Bson filterID = Filters.eq("id", theIdType.getIdPart());
		//define filter for versionID
		Bson filterVersionID = Filters.eq("meta.versionId", theIdType.getVersionIdPart());
		// filter resultant of joining both
		Bson theFilter = Filters.and(filterID,filterVersionID);
		
		// search query in the MongoDB database - try to find json document with the same "id" and same "versionID"
		MongoCursor<Document> cursor = patientsCollection.find(theFilter).iterator();
		// Check if the result is empty
		if (!cursor.hasNext()) {
			// The result is empty
			throw new ResourceNotFoundException(theIdType);
		} else { // found result for such ID and versionID
			return parser.parseResource(Patient.class, cursor.next().toJson());
		}
	}
}