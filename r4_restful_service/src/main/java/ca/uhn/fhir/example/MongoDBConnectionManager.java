package ca.uhn.fhir.example;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoDBConnectionManager {
	
    static MongoClient mongoClient;
    static MongoDatabase database;
    MongoCollection<Document> encountersCollection;
    MongoCollection<Document> messageHeadersCollection;
    MongoCollection<Document> patientsCollection;
    
    public MongoDBConnectionManager() {
    	//create client to access MongoDB and get the databse
        mongoClient = MongoClients.create("mongodb://localhost:27017");
        database = mongoClient.getDatabase("uphill_challenge_db");
        
        // get access to the various collections
        encountersCollection = database.getCollection("encounter_resources");
        messageHeadersCollection = database.getCollection("message_headers");
        patientsCollection = database.getCollection("patient_resources");
    }
    
    public MongoCollection<Document> getmessageHeadersCollection() {
        return messageHeadersCollection;
    }
    public MongoCollection<Document> getencountersCollection() {
        return encountersCollection;
    }
    public MongoCollection<Document> getpatientsCollection() {
        return patientsCollection;
    }  
}
