package com.csc.fi.ioapi.api.genericapi;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author malonen
 */
import com.csc.fi.ioapi.config.ApplicationProperties;
import com.csc.fi.ioapi.config.EndpointServices;
import com.csc.fi.ioapi.utils.JerseyFusekiClient;
import com.csc.fi.ioapi.utils.LDHelper;
import com.csc.fi.ioapi.utils.ServiceDescriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.hp.hpl.jena.query.DatasetAccessor;
import com.hp.hpl.jena.query.DatasetAccessorFactory;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.rdf.model.Model;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
 
/**
 * Root resource (exposed at "myresource" path)
 */
@Path("export")
@Api(value = "/export", description = "Export framed models")
public class Export {

    @Context ServletContext context;
    EndpointServices services = new EndpointServices();
    
    private static final Logger logger =Logger.getLogger(Export.class.getName());
        
  @GET
  @Produces("application/ld+json")
  @ApiOperation(value = "Get model from service", notes = "More notes about this method")
  @ApiResponses(value = {
      @ApiResponse(code = 400, message = "Invalid model supplied"),
      @ApiResponse(code = 404, message = "Service not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response json(@ApiParam(value = "Requested resource", defaultValue="default") @QueryParam("graph") String graph) {
   
      try {
          
          
        DatasetAccessor accessor = DatasetAccessorFactory.createHTTP(services.getCoreReadAddress());
        Model model = accessor.getModel(graph);
        
        /* If no id is provided create a list of classes */
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        
        pss.setNsPrefixes(model.getNsPrefixMap());
        
        String queryString = "CONSTRUCT { "
                + "?ms ?p ?o . "
                + "?rs ?rp ?ro . "
                + " } WHERE { "
                + "GRAPH ?model {"
                + "?ms ?p ?o . "
                + "?model <http://purl.org/dc/terms/hasPart> ?resource . "
                + "} GRAPH ?resource { "
                + "?rs ?rp ?ro . "
                + "}}"; 

        pss.setCommandText(queryString);
        pss.setIri("model", graph);

        ClientResponse response = JerseyFusekiClient.clientResponseFromConstruct(pss.toString(), services.getCoreSparqlAddress());

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                return Response.status(401).entity("{\"errorMessage\":\"Export Failed\"}").build();
            }
            
        ResponseBuilder rb;

        OutputStream out =  new ByteArrayOutputStream();
                
        RDFDataMgr.write(out, model, Lang.JSONLD);
        
        Map<String,Object> jsonModel = null;
          try {
              jsonModel = (Map<String,Object>)JsonUtils.fromString(out.toString());
          } catch (IOException ex) {
              Logger.getLogger(Export.class.getName()).log(Level.SEVERE, null, ex);
          }
        
      Map<String,Object> frame = new HashMap<String,Object>(); 
     //Map<String,Object> frame = (HashMap<String,Object>) LDHelper.getExportContext();
        
      Map<String,Object> context = (Map<String,Object>)jsonModel.get("@context");
        
      context.putAll(LDHelper.CONTEXT_MAP);
      
      logger.info(context.toString());
      
       frame.put("@context", context);
       frame.put("@type","owl:Ontology");
        
       logger.info(frame.toString());  
        
        Object data;
            
            try{
                    data = JsonUtils.fromInputStream(response.getEntityInputStream());
                    rb = Response.status(response.getStatus()); 
                    
                try {
                    JsonLdOptions options = new JsonLdOptions();
                    Object framed = JsonLdProcessor.frame(data, frame, options);                   
                    rb.entity(JsonUtils.toString(framed));   
                } catch (NullPointerException ex) {
                    logger.log(Level.WARNING, null, "DEFAULT GRAPH IS NULL!");
                     return rb.entity(JsonUtils.toString(data)).build();
                } catch (JsonLdError ex) {
                    logger.log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                  
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                     return Response.serverError().entity("{}").build();
                }
                
                

           
           return rb.build();
      
           
      } catch(UniformInterfaceException | ClientHandlerException ex) {
          logger.log(Level.WARNING, "Expect the unexpected!", ex);
          return Response.serverError().entity("{}").build();
      } 

  }
 
}