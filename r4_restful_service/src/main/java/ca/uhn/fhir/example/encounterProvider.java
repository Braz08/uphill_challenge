package ca.uhn.fhir.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.hl7.fhir.instance.model.api.IBaseResource;
public class encounterProvider implements IResourceProvider {
	
	// method that allows the restfulserver to know which FHIR Resource this HAPI FHIR's Resource Provider manages
	public Class<? extends IBaseResource> getResourceType() {
		return Encounter.class;
	}
	   
	IParser parser;
	MongoCollection<Document> encountersCollection;
	
    /**
     * Constructor
     */
    public encounterProvider() {
    	
		// Create a context (for parsing, etc.)
		FhirContext ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
    	
        // get pointer to MongoDB's collection where Encounter Resources are saved with the help of the auxiliar class MongoDBConnectionManager
    	MongoDBConnectionManager mongoDBConnectionManager = new MongoDBConnectionManager();
    	encountersCollection = mongoDBConnectionManager.getencountersCollection();
    }

    /**
	 * The "@Read" annotation indicates that this method supports the read operation. It takes one argument, the Resource type being returned.
	 * 
	 * @param theId
	 *            The read operation takes one parameter, which must be of type IdDt and must be annotated with the "@Read.IdParam" annotation.
	 * @return Returns a resource matching this identifier, or null if none exists.
	 */
    @Read(version = true)
    public Encounter readEncounter(@IdParam IdType theId){
    	//check if Resource has version
    	Encounter returnEncounter = null;
		//get Resource from the database
    	if (theId.hasVersionIdPart()) {
    		returnEncounter = fetchEncounterWithVersion(theId);
    	} else {
        	returnEncounter = fetchEncounterNoVersion(theId.getIdPart());
    	}
    	return returnEncounter;
    }
    
	private Encounter fetchEncounterNoVersion (String theID) {
		// query filter
		Bson filter = Filters.eq("id", theID);
		// search query in the MongoDB database - try to find json document with the same "id"
		MongoCursor<Document> cursor = encountersCollection.find(filter).iterator();
		
		// Check if the result is empty
		if (!cursor.hasNext()) {
		    // The result is empty
		    throw new ResourceNotFoundException(theID);
		} else { // result is not empty
			// check if the first Document in the cursor has versionID (if not, that means this is the only one in the DB)
			Encounter latestEncounterVersion = parser.parseResource(Encounter.class, cursor.next().toJson());
			Integer lastVersion = null;
			if (latestEncounterVersion.hasMeta()) { // versionID is in the meta field
				if (latestEncounterVersion.getMeta().hasVersionId()) { // if it has versionId, it means that are other versions in the DB (continues below in order to achieve the latest version)
					lastVersion = Integer.valueOf(latestEncounterVersion.getMeta().getVersionId());
				} else {
					return latestEncounterVersion; // as it is the only Encounter with such ID in the database (it has metadata, but no version information)
				}
			} else {
				return latestEncounterVersion; // as it is the only Encounter with such ID in the database
		}
			
			//if method continues, there is more than one version of the Resource in the DB - return the latest
			while (cursor.hasNext()) {
				Encounter checkingEncounter = parser.parseResource(Encounter.class, cursor.next().toJson());
				if (checkingEncounter.hasMeta()) {
					if (checkingEncounter.getMeta().hasVersionId()) {
						Integer checkingVersionIdInteger = Integer.valueOf(checkingEncounter.getMeta().getVersionId());
						if (checkingVersionIdInteger.intValue() > lastVersion.intValue()) {
							lastVersion = checkingVersionIdInteger;
							latestEncounterVersion = checkingEncounter;
						}
					}
				}
			}
			return latestEncounterVersion;
		}
	}
	
	private Encounter fetchEncounterWithVersion (IdType theIdType) {
		//Define filter for ID
		Bson filterID = Filters.eq("id", theIdType.getIdPart());
		//define filter for versionID
		Bson filterVersionID = Filters.eq("meta.versionId", theIdType.getVersionIdPart());
		// filter resultant of joining both
		Bson theFilter = Filters.and(filterID,filterVersionID);
		
		// search query in the MongoDB database - try to find json document with the same "id" and same "versionID"
		MongoCursor<Document> cursor = encountersCollection.find(theFilter).iterator();
		// Check if the result is empty
		if (!cursor.hasNext()) {
			// The result is empty
			throw new ResourceNotFoundException(theIdType);
		} else { // found result for such ID and versionID
			return parser.parseResource(Encounter.class, cursor.next().toJson());
		}
	}
}
 