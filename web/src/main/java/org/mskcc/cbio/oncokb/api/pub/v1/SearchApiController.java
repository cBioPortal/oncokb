package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.ApiParam;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.service.JsonResultFactory;
import org.mskcc.cbio.oncokb.util.GeneUtils;
import org.mskcc.cbio.oncokb.util.IndicatorUtils;
import org.mskcc.cbio.oncokb.util.LevelUtils;
import org.mskcc.cbio.oncokb.util.MainUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mskcc.cbio.oncokb.util.MainUtils.stringToEvidenceTypes;


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-10-19T19:28:21.941Z")

@Controller
public class SearchApiController implements SearchApi {

    public ResponseEntity<IndicatorQueryResp> searchGet(
        @ApiParam(value = "The query ID") @RequestParam(value = "id", required = false) String id
        , @ApiParam(value = "The gene symbol used in Human Genome Organisation.") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "The entrez gene ID.") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "Variant name.") @RequestParam(value = "variant", required = false) String variant
        , @ApiParam(value = "Structural Variant Type.") @RequestParam(value = "svType", required = false) StructuralVariantType svType
        , @ApiParam(value = "Consequence") @RequestParam(value = "consequence", required = false) String consequence
        , @ApiParam(value = "Protein Start") @RequestParam(value = "proteinStart", required = false) Integer proteinStart
        , @ApiParam(value = "Protein End") @RequestParam(value = "proteinEnd", required = false) Integer proteinEnd
        , @ApiParam(value = "Tumor type name. OncoTree code is supported.") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = "Tumor type source. OncoTree tumor types are the default setting. We may have customized version, like Quest.", defaultValue = "oncotree") @RequestParam(value = "source", required = false, defaultValue = "oncotree") String source
        , @ApiParam(value = "Level of evidences.") @RequestParam(value = "levels", required = false) String levels
        , @ApiParam(value = "Only show treatments of highest level") @RequestParam(value = "highestLevelOnly", required = false, defaultValue = "FALSE") Boolean highestLevelOnly
        , @ApiParam(value = "Query type. There maybe slight differences between different query types. Currently support web or regular.") @RequestParam(value = "queryType", required = false, defaultValue = "regular") String queryType
        , @ApiParam(value = "Evidence type.") @RequestParam(value = "evidenceType", required = false) String evidenceType
        , @ApiParam(value = "HGVS varaint. Its priority is higher than entrezGeneId/hugoSymbol + variant combination") @RequestParam(value = "hgvs", required = false) String hgvs
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;
        IndicatorQueryResp indicatorQueryResp = null;

        if (entrezGeneId != null && hugoSymbol != null && !GeneUtils.isSameGene(entrezGeneId, hugoSymbol)) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            Query query = new Query(id, queryType, entrezGeneId, hugoSymbol, variant, null, svType, tumorType, consequence, proteinStart, proteinEnd, hgvs);
            source = source == null ? "oncokb" : source;

            Set<LevelOfEvidence> levelOfEvidences = levels == null ? LevelUtils.getPublicAndOtherIndicationLevels() : LevelUtils.parseStringLevelOfEvidences(levels);
            indicatorQueryResp = IndicatorUtils.processQuery(query, null, levelOfEvidences, source, highestLevelOnly, new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceType, ",")));
        }
        return ResponseEntity.status(status.value()).body(JsonResultFactory.getIndicatorQueryResp(indicatorQueryResp, fields));
    }

    public ResponseEntity<List<IndicatorQueryResp>> searchPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody(required = true) EvidenceQueries body
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;

        List<IndicatorQueryResp> result = new ArrayList<>();

        if (body == null || body.getQueries() == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {

            String source = body.getSource() == null ? "oncokb" : body.getSource();

            for (Query query : body.getQueries()) {
                result.add(IndicatorUtils.processQuery(query, null,
                    body.getLevels() == null ? LevelUtils.getPublicAndOtherIndicationLevels() : body.getLevels(),
                    source, body.getHighestLevelOnly(), new HashSet<>(stringToEvidenceTypes(body.getEvidenceTypes(), ","))));
            }
        }
        return ResponseEntity.status(status.value()).body(JsonResultFactory.getIndicatorQueryResp(result, fields));
    }
}
