
# Interoperability Engineer @ UpHill
## Introduction

The work presented in this directory states the development of an HL7 FHIR R4 RESTful service to fufill the Interoperability Engineer position's challenge proposed to Nuno Pimenta.

A Docker Compose file couldn't be made, but the following sections provide information on how to interpret and experiment the developed service, as well as relevant considerations/assumptions made along the development process.
## Directories and documents

- /r4_restful_service : all files related to the Hl7 FHIR service/server
- /spring_auth_server : all files related to the Spring Auhtorization server
- /message_working_example.txt : example of a working message (Budndle request message)
- /oauth_server_steps.txt : information about the sequential steps needed for obtaining a valid access token
- /openAPI_service_description :  OpenAPI service description



## General considerations/assumptions needed

Considerations about the requirements presented in the PDF document:

- service does not perform actions that may be asked in the request message. It "only" receives and responds according to the FHIR Messaging specification (ideally the service would be upgraded to fufill such requests or redirect actions to another service that does so);
- it is assumed that the fields of each Resource Type (Encounter and Patient) that are refered in the PDF document are mandatory fields that must be populated in the Resource upon receives. Otherwise, the service responds with an error;
- only synchronous request management is dealt (http://hl7.org/fhir/R4/messaging.html#sync);
- the option for a HL7 FHIR server with the HAPI FHIR library is based on the fact that we are considering a future and possible upgrade of the service's capabilities, for example, in the ability to deal with other FHIR interactions such as update, delete, create, among others;
- Resources are kept in a database in order to preserve information between restarts and to make it acessible to other services and users in such database;



Other notable considerations:
- a detected bug in the HAPI FHIR library prevented the use of the FHIR incoming request valiation module/interceptor (https://hapifhir.io/hapi-fhir/docs/introduction/changelog_2020.html);
- a MongoDB database (with name "uphill_challenge_db) and with collections "message_headers", "encounter_resources" and "patient_resources" should be available at port 27017 (default MongoDB port). I have used MongoDBCompass and collections may be initally empty that it does not cause error in the service;
- a CapabilityStatment is available in /metadata;
- the server's OpenAPi is description can also be observed in /api-docs;
- /swagger-ui makes available a testing interface, but it can not be currently used because when using a browser for accessing such path, the request made in the browser does not possess an Authorization header (and so UI gives errors);
- 


## Initializing

- Authorization server: run as a Spring Boot Aplication (port: 9000)
- FHIR service: go inside /r4_restful_service and run
```bash
  mvn jetty:run
```
- Get valid authorization token: see "Demonstration" (video) plus "oauth_server_steps.txt"

## Service demonstration

Despite not corresponding exactly to the HL7 FHIR R4 restful service in hands, the following video displays the pipeline from starting the servers until the request, passing through the steps for adquiring an access token.

https://1drv.ms/v/s!Ah6q-RGgvduYgv1_pr6yZMbOCRhqFw?e=yHTLIf

## Versions

|                | Version                                                |
| ----------------- | ---------------------------------------------------------------- |
| Java       | jdk 11 ("11.0.19" 2023-04-18 LTS) |
| Maven       | 3.8.6 |
| Spring Authorization Server| 1.1.2 |
| OAuth       | 2.1 |
| HAPI FHIR       | 6.0.0 |
| MongoDBCompass       | 1.39.3  |
| Postman       | v10.17.7  |
| cmd       | Microsoft Windows [Version 10.0.22621.2134] |
| Others       | pom.xml file  |


## Support

In case of any doubt, feel free to contact me: nunopimenta2000@live.com.pt


## Author

- [@Braz08](https://github.com/Braz08)


## Melhorias

Que melhorias você fez no seu código? Ex: refatorações, melhorias de performance, acessibilidade, etc

