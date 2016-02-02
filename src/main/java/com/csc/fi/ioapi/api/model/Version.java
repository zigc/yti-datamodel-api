/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1 
 */
package com.csc.fi.ioapi.api.model;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIException;
import org.apache.jena.iri.IRIFactory;

import com.csc.fi.ioapi.config.EndpointServices;
import com.csc.fi.ioapi.config.LoginSession;
import com.csc.fi.ioapi.utils.ErrorMessage;
import com.csc.fi.ioapi.utils.GraphManager;
import com.csc.fi.ioapi.utils.JerseyFusekiClient;
import com.csc.fi.ioapi.utils.LDHelper;
import com.csc.fi.ioapi.utils.NamespaceManager;
import com.csc.fi.ioapi.utils.ProvenanceManager;
import com.csc.fi.ioapi.utils.QueryLibrary;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.uri.UriComponent;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.DELETE;

 
/**
 * Root resource (exposed at "myresource" path)
 */
@Path("version")
@Api(value = "/version", description = "Operations about version history")
public class Version {

    private static final Logger logger = Logger.getLogger(Version.class.getName());

    @Context ServletContext context;
    EndpointServices services = new EndpointServices();
 
  @GET
  @Produces("application/ld+json")
  @ApiOperation(value = "Get activity history for the resource", notes = "More notes about this method")
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid model supplied"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response json(
      @ApiParam(value = "resource id")
      @QueryParam("id") String id) {
      
      if(id==null || id.equals("undefined") || id.equals("default")) {

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        
        Map<String, String> namespacemap = NamespaceManager.getCoreNamespaceMap();
        namespacemap.putAll(LDHelper.PREFIX_MAP);
        
        pss.setNsPrefixes(namespacemap);
        
        String queryString = "CONSTRUCT { "
                + "?activity a prov:Activity . "
                + "?activity rdfs:label ?label . "
                + "?activity prov:wasAttributedTo ?user . "
                + "?activity prov:atTime ?created . "
                + "?activity rdfs:isDefinedBy ?model . "
                + " } "
                + "WHERE {"
                + "?activity a prov:Activity . "
                + "?activity prov:used ?entity . "
                + "?entity a prov:Entity . "
                + "?entity prov:wasAttributedTo ?user . "
                + "?entity prov:generatedAtTime ?created . "
                + "?entity prov:value ?provGraph . "
                + "GRAPH ?provGraph {"
                + "?resource rdfs:isDefinedBy ?model . "
                + "?resource rdfs:label ?label . "
                + "}"
                + "} ORDER BY DESC(?created)"; 

        pss.setCommandText(queryString);

        return JerseyFusekiClient.constructGraphFromService(pss.toString(), services.getProvReadSparqlAddress());
      
      } else {
        return JerseyFusekiClient.getGraphResponseFromService(id, services.getProvReadWriteAddress());
      }
      
    }
  

    @PUT
    @ApiOperation(value = "Create new model version", notes = "Create new model version")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "New property is created"),
                    @ApiResponse(code = 400, message = "Invalid ID supplied"),
                    @ApiResponse(code = 403, message = "Invalid IRI in parameter"),
                    @ApiResponse(code = 401, message = "User is not logged in"),
                    @ApiResponse(code = 404, message = "Service not found") })
    public Response newModelVersion(
            @ApiParam(value = "model ID", required = true) @QueryParam("modelID") String modelID,
            @Context HttpServletRequest request) {

        HttpSession session = request.getSession();
        
        if(session==null) {
            return Response.status(401).entity(ErrorMessage.UNAUTHORIZED).build();
        }
       
        LoginSession login = new LoginSession(session);
        
        if(!login.isLoggedIn() || !login.hasRightToEditModel(modelID)) {
            return Response.status(401).entity(ErrorMessage.UNAUTHORIZED).build();
        }

            IRI modelIRI;

            try {
                    IRIFactory iri = IRIFactory.semanticWebImplementation();
                    modelIRI = iri.construct(modelID); 
            } catch (IRIException e) {
                    logger.log(Level.WARNING, "CLASS OR PROPERTY ID is invalid IRI!");
                    return Response.status(403).build();
            }
          
            /* TODO: Check that model is dcap:MetadataVocabulary or dcap:DCAP */
            
        UUID provModelUUID = ProvenanceManager.createNewVersionModel(modelID, login.getEmail());
           
        return Response.status(200).entity("{\"@id\":\"urn:uuid:"+provModelUUID+"\"}").build();
         
    }

  
  }