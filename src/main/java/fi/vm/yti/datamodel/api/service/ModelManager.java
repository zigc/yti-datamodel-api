/*
 * Licensed under the European Union Public Licence (EUPL) V.1.1 
 */
package fi.vm.yti.datamodel.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service
public class ModelManager {

    private static final Logger logger = Logger.getLogger(ModelManager.class.getName());

    /**
     * Writes jena model to string
     * @param model model to be written as json-ld string
     * @return string
     */
    public String writeModelToJSONLDString(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, RDFFormat.JSONLD);
        return writer.toString();
    }

    public String writeModelToString(Model model, RDFFormat format) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, format);
        return writer.toString();
    }

    public JsonNode toJsonNode(Model m) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DatasetGraph g = DatasetFactory.create(m).asDatasetGraph();
        WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(RDFFormat.JSONLD_FLATTEN_FLAT);
        w.write(baos, g, RiotLib.prefixMap(g), null, g.getContext());
        ObjectMapper objectMapper = new ObjectMapper();
        try {
           JsonNode jsonNode = objectMapper.readTree(baos.toByteArray());
           return jsonNode;
        } catch(IOException ex) {
           ex.printStackTrace();
           return null;
        }
    }

    public String writeModelToJSONLDString(Model m, Context jenaContext) {
        try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(RDFFormat.JSONLD_FRAME_PRETTY);
            DatasetGraph g = DatasetFactory.create(m).asDatasetGraph();
            PrefixMap pm = RiotLib.prefixMap(g);
            //PrefixMap pm = PrefixMapFactory.create(m.getNsPrefixMap());
            String base = null;
            w.write(out, g, pm, base, jenaContext) ;
            out.flush();
            return out.toString("UTF-8");
        } catch (IOException e) { throw new RuntimeException(e); }
    }


    public Model removeListStatements(Model resource, Model model) {

        StmtIterator listIterator = resource.listStatements();

        // OMG: Iterate trough all RDFLists and remove old lists from exportModel
        while(listIterator.hasNext()) {
            Statement listStatement = listIterator.next();
            if(!listStatement.getSubject().isAnon() && listStatement.getObject().isAnon()) {
                Statement removeStatement = model.getRequiredProperty(listStatement.getSubject(), listStatement.getPredicate());
                RDFList languageList = removeStatement.getObject().as(RDFList.class);
                languageList.removeList();
                removeStatement.remove();

            }
        }

        return model;

    }

    public Model removeResourceStatements(Model resource, Model model) {

        StmtIterator listIterator = resource.listStatements();

        while(listIterator.hasNext()) {
            Statement listStatement = listIterator.next();
            Resource subject = listStatement.getSubject();
            RDFNode object = listStatement.getObject();
            if(subject.isURIResource() && object.isAnon()) {
                try {
                    Statement removeStatement = model.getRequiredProperty(subject, listStatement.getPredicate());
                    if (!removeStatement.getObject().isAnon()) {
                        logger.warning("This should'nt happen!");
                        logger.warning("Bad data " + subject.toString() + "->" + listStatement.getPredicate());
                    } else {
                        RDFList languageList = removeStatement.getObject().as(RDFList.class);
                        languageList.removeList();
                        removeStatement.remove();
                    }
                } catch(PropertyNotFoundException ex) {
                    logger.warning("This should'nt happen!");
                    ex.printStackTrace();
                }
            } else if(subject.isURIResource() && !subject.hasProperty(RDF.type, OWL.Ontology)){
                model.remove(listStatement);
            }
        }

        return model;

    }


    /**
     * Create jena model from json-ld string
     * @param modelString RDF as JSON-LD string
     * @return Model
     */
    public Model createJenaModelFromJSONLDString(String modelString) throws IllegalArgumentException {
        Model model = ModelFactory.createDefaultModel();
        
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(modelString.getBytes("UTF-8")), Lang.JSONLD) ;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(ModelManager.class.getName()).log(Level.SEVERE, null, ex);
            throw new IllegalArgumentException("Could not parse the model");
        }

        return model;
        
    }
}