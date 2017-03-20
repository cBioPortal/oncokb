package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.*;
import org.mskcc.cbio.oncokb.model.Evidence;
import org.mskcc.cbio.oncokb.model.EvidenceQueries;
import org.mskcc.cbio.oncokb.model.EvidenceQueryRes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-10-19T19:28:21.941Z")

@Api(value = "evidences", description = "The evidences API")
public interface EvidencesApi {

    @ApiOperation(value = "", notes = "Get specific evidence.", response = Evidence.class, tags = {"Evidences",})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Evidence.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ResponseEntity.class)})
    @RequestMapping(value = "/evidences/{uuid}",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<ApiObjectResp> evidencesUUIDGet(
        @ApiParam(value = "Universally Unique identifier list.", required = true) @PathVariable("uuid") String uuid
    );


    @ApiOperation(value = "", notes = "Search evidences. Multi-queries are supported.", response = Evidence.class, responseContainer = "List", tags = {"Evidences", "Search",})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Evidence.class, responseContainer = "List")})
    @RequestMapping(value = "/evidences/lookup",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<Evidence>> evidencesLookupGet(
        @ApiParam(value = "The entrez gene ID.") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "The gene symbol used in Human Genome Organisation.") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "Variant name.") @RequestParam(value = "variant", required = false) String variant
        , @ApiParam(value = "Tumor type name. OncoTree code is supported.") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = "Consequence. Possible value: feature_truncation, frameshift_variant, inframe_deletion, inframe_insertion, initiator_codon_variant, missense_variant, splice_region_variant, stop_gained, synonymous_variant") @RequestParam(value = "consequence", required = false) String consequence
        , @ApiParam(value = "Protein Start.") @RequestParam(value = "proteinStart", required = false) String proteinStart
        , @ApiParam(value = "Protein End.") @RequestParam(value = "proteinEnd", required = false) String proteinEnd
        , @ApiParam(value = "Tumor type source. OncoTree tumor types are the default setting. We may have customized version, like Quest.", defaultValue = "oncotree") @RequestParam(value = "source", required = false, defaultValue = "oncotree") String source
        , @ApiParam(value = "Only show highest level evidences") @RequestParam(value = "highestLevelOnly", required = false, defaultValue = "FALSE") Boolean highestLevelOnly
        , @ApiParam(value = "Separate by comma. LEVEL_1, LEVEL_2A, LEVEL_2B, LEVEL_3A, LEVEL_3B, LEVEL_4, LEVEL_R1, LEVEL_R2, LEVEL_R3") @RequestParam(value = "levelOfEvidence", required = false) String levels
        , @ApiParam(value = "Separate by comma. Evidence type includes GENE_SUMMARY, GENE_BACKGROUND, MUTATION_SUMMARY, ONCOGENIC, MUTATION_EFFECT, VUS, PREVALENCE, PROGNOSTIC_IMPLICATION, TUMOR_TYPE_SUMMARY, NCCN_GUIDELINES, STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY, STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE, CLINICAL_TRIAL") @RequestParam(value = "evidenceTypes", required = false) String evidenceTypes
    );

    @ApiOperation(value = "", notes = "Search evidences.", response = EvidenceQueryRes.class, responseContainer = "List", tags = {"Evidences", "Search",})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = EvidenceQueryRes.class, responseContainer = "List")})
    @RequestMapping(value = "/evidences/lookup",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<EvidenceQueryRes>> evidencesLookupPost(@ApiParam(value = "List of queries. Please see swagger.json for request body format. Please use JSON string.", required = true) @RequestBody(required = true) EvidenceQueries body
    );

//    @ApiOperation(value = "", notes = "Get specific evidences.", response = Evidence.class, responseContainer = "List", tags = {"Evidences",})
//    @ApiResponses(value = {
//        @ApiResponse(code = 200, message = "OK", response = Evidence.class, responseContainer = "List"),
//        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiErrorResp.class)})
//    @RequestMapping(value = "/evidences",
//        consumes = {"application/json"},
//        produces = {"application/json"},
//        method = RequestMethod.POST)
//    ResponseEntity<ApiListResp> evidencesPost(@ApiParam(value = "List of unique identifier for each model. Separated by comma.", required = true) @RequestParam(value = "ids", required = true) String ids
//    );
}
