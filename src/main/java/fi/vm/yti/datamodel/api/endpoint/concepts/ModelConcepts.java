/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1
 */
package fi.vm.yti.datamodel.api.endpoint.concepts;

import fi.vm.yti.datamodel.api.service.EndpointServices;
import fi.vm.yti.datamodel.api.service.IDManager;
import fi.vm.yti.datamodel.api.service.JerseyClient;
import fi.vm.yti.datamodel.api.service.JerseyResponseManager;
import fi.vm.yti.datamodel.api.utils.LDHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Component
@Path("v1/modelConcepts")
@Tag(name = "Concept")
public class ModelConcepts {

    private final EndpointServices endpointServices;
    private final JerseyResponseManager jerseyResponseManager;
    private final JerseyClient jerseyClient;
    private final IDManager idManager;

    @Autowired
    ModelConcepts(EndpointServices endpointServices,
                  JerseyResponseManager jerseyResponseManager,
                  JerseyClient jerseyClient,
                  IDManager idManager) {

        this.endpointServices = endpointServices;
        this.jerseyResponseManager = jerseyResponseManager;
        this.jerseyClient = jerseyClient;
        this.idManager = idManager;
    }

    @GET
    @Produces("application/ld+json")
    @Operation(description = "Get used concepts from model")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "404", description = "No such resource"),
        @ApiResponse(responseCode = "400", description = "Invalid model supplied"),
        @ApiResponse(responseCode = "404", description = "Service not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Response getModelConcepts(
        @Parameter(description = "Concept id")
        @QueryParam("id") String id,
        @Parameter(description = "Model id", required = true)
        @QueryParam("model") String model) {

        if (id != null && !id.equals("undefined") && idManager.isInvalid(id)) {
            return jerseyResponseManager.invalidIRI();
        }

        if (idManager.isInvalid(model)) {
            return jerseyResponseManager.invalidIRI();
        }

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefixes(LDHelper.PREFIX_MAP);

        String queryString = "CONSTRUCT { "
            + "?concept a skos:Concept . "
            + "?concept skos:prefLabel ?label . "
            + "?concept skos:definition ?definition . "
            + "} WHERE {"
            + "GRAPH ?modelParts { "
            + "?model dcterms:hasPart ?resource . "
            + "}"
            + "GRAPH ?resource { "
            + "?resource dcterms:subject ?concept ."
            + "?concept skos:prefLabel ?label . "
            + "?concept skos:definition ?definition . "
            + "}"
            + "}";

        pss.setIri("model", model);
        pss.setIri("modelParts", model + "#HasPartGraph");

        if (id != null && !id.equals("undefined")) {
            pss.setIri("concept", id);
        }

        pss.setCommandText(queryString);

        return jerseyClient.constructGraphFromService(pss.toString(), endpointServices.getCoreSparqlAddress());
    }
}
