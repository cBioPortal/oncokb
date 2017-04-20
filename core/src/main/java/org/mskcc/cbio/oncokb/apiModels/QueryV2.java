package org.mskcc.cbio.oncokb.apiModels;
// Generated Dec 19, 2013 1:33:26 AM by Hibernate Tools 3.2.1.GA

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.LevelUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * TumorType generated by hbm2java
 */
public class QueryV2 implements java.io.Serializable {
    private String hugoSymbol;
    private Integer entrezGeneId;
    private String variant;
    private String tumorType;
    private String consequence;
    private Integer proteinStart;
    private Integer proteinEnd;
    private Set<LevelOfEvidence> levels;
    private Boolean highestLevelOnly = false;
    private Set<EvidenceType> evidenceTypes;
    @JsonIgnore
    private String alterationType;
    private String id; //Optional, This id is passed from request. The identifier used to distinguish the query
    private QueryType type = QueryType.regular; // Query type. Different type may return different result.
    private String source = "oncotree";
    private Projection projection = Projection.DETAILED;

    public QueryV2() {
    }

    public QueryV2(String hugoSymbol, Integer entrezGeneId, String variant, String tumorType, String consequence, Integer proteinStart, Integer proteinEnd, Set<LevelOfEvidence> levels, Boolean highestLevelOnly, Set<EvidenceType> evidenceTypes, String alterationType, String id, QueryType type, String source, Projection projection) {
        this.hugoSymbol = hugoSymbol;
        this.entrezGeneId = entrezGeneId;
        this.setVariant(variant);
        this.tumorType = tumorType;
        this.consequence = consequence;
        this.proteinStart = proteinStart;
        this.proteinEnd = proteinEnd;
        this.setSource(source);
        this.setLevels(levels);
        this.setHighestLevelOnly(highestLevelOnly);
        this.evidenceTypes = evidenceTypes;
        this.alterationType = alterationType;
        this.id = id;
        this.type = type;
        this.projection = projection;
    }

    public QueryV2(String hugoSymbol, Integer entrezGeneId, String variant, String tumorType, String consequence, Integer proteinStart, Integer proteinEnd) {
        this.hugoSymbol = hugoSymbol;
        this.entrezGeneId = entrezGeneId;
        this.variant = variant;
        this.tumorType = tumorType;
        this.consequence = consequence;
        this.proteinStart = proteinStart;
        this.proteinEnd = proteinEnd;
    }

    public QueryV2(Alteration alt) {
        if (alt != null) {
            if (alt.getGene() != null) {
                this.hugoSymbol = alt.getGene().getHugoSymbol();
                this.entrezGeneId = alt.getGene().getEntrezGeneId();
            }
            this.variant = alt.getAlteration();
            this.alterationType = alt.getAlterationType() == null ? "MUTATION" : alt.getAlterationType().name();
            this.consequence = alt.getConsequence() == null ? null : alt.getConsequence().getTerm();
            this.proteinStart = alt.getProteinStart();
            this.proteinEnd = alt.getProteinEnd();
        }
    }

    public QueryV2(VariantQuery variantQuery) {
        if (variantQuery != null) {
            if (variantQuery.getGene() != null) {
                this.hugoSymbol = variantQuery.getGene().getHugoSymbol();
                this.entrezGeneId = variantQuery.getGene().getEntrezGeneId();
            }
            this.variant = variantQuery.getQueryAlteration();
            this.consequence = variantQuery.getConsequence();
            this.proteinStart = variantQuery.getProteinStart();
            this.proteinEnd = variantQuery.getProteinEnd();
        }
    }

    public QueryV2(String hugoSymbol, String variant, String tumorType) {
        this.hugoSymbol = hugoSymbol;
        this.variant = variant;
        this.tumorType = tumorType;
    }

    public QueryV2(String hugoSymbol, Integer entrezGeneId, String variant, String alterationType, String tumorType, String consequence, Integer proteinStart, Integer proteinEnd) {
        this.hugoSymbol = hugoSymbol;
        this.entrezGeneId = entrezGeneId;
        this.variant = variant;
        this.alterationType = alterationType;
        this.tumorType = tumorType;
        this.consequence = consequence;
        this.proteinStart = proteinStart;
        this.proteinEnd = proteinEnd;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public QueryType getType() {
        return type;
    }

    public void setType(QueryType type) {
        this.type = type;
    }

    public String getHugoSymbol() {
        return hugoSymbol;
    }

    public void setHugoSymbol(String hugoSymbol) {
        this.hugoSymbol = hugoSymbol;
    }

    public Integer getEntrezGeneId() {
        return entrezGeneId;
    }

    public void setEntrezGeneId(Integer entrezGeneId) {
        this.entrezGeneId = entrezGeneId;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        if (variant == null)
            variant = "";
        this.variant = variant;
    }

    public String getAlterationType() {
        return alterationType;
    }

    public void setAlterationType(String alterationType) {
        this.alterationType = alterationType;
    }

    public String getTumorType() {
        return tumorType;
    }

    public void setTumorType(String tumorType) {
        this.tumorType = tumorType;
    }

    public String getConsequence() {
        return consequence;
    }

    public void setConsequence(String consequence) {
        this.consequence = consequence;
    }

    public Integer getProteinStart() {
        return proteinStart;
    }

    public void setProteinStart(Integer proteinStart) {
        this.proteinStart = proteinStart;
    }

    public Integer getProteinEnd() {
        return proteinEnd;
    }

    public void setProteinEnd(Integer proteinEnd) {
        this.proteinEnd = proteinEnd;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        if (source != null && !source.isEmpty()) {
            this.source = source;
        } else {
            this.source = "oncotree";
        }
    }

    public Set<LevelOfEvidence> getLevels() {
        return levels;
    }

    public void setLevels(Set<LevelOfEvidence> levels) {
        if (levels == null) {
            this.levels = LevelUtils.getPublicAndOtherIndicationLevels();
        } else {
            this.levels = levels;
        }
    }

    public Boolean getHighestLevelOnly() {
        return highestLevelOnly;
    }

    public void setHighestLevelOnly(Boolean highestLevelOnly) {
        if (highestLevelOnly == null) {
            this.highestLevelOnly = false;
        } else {
            this.highestLevelOnly = highestLevelOnly;
        }
    }

    public Set<EvidenceType> getEvidenceTypes() {
        return evidenceTypes;
    }

    public void setEvidenceTypes(Set<EvidenceType> evidenceTypes) {
        this.evidenceTypes = evidenceTypes;
    }

    public Projection getProjection() {
        return projection;
    }

    public void setProjection(Projection projection) {
        this.projection = projection;
    }

    @JsonIgnore
    public String getQueryId() {

        List<String> content = new ArrayList<>();
        if (this.hugoSymbol != null) {
            content.add(this.hugoSymbol);
        } else {
            if (this.entrezGeneId != null) {
                content.add(Integer.toString(this.entrezGeneId));
            } else {
                content.add("");
            }
        }
        if (this.variant != null) {
            content.add(this.variant);
        } else {
            content.add("");
        }
        if (this.alterationType != null) {
            content.add(this.alterationType);
        } else {
            content.add("");
        }
        if (this.type != null) {
            content.add(this.type.name());
        } else {
            content.add("");
        }
        if (this.tumorType != null) {
            content.add(this.tumorType);
        } else {
            content.add("");
        }
        if (this.source != null) {
            content.add(this.source);
        } else {
            content.add("");
        }
        if (consequence != null) {
            content.add(this.consequence);
        } else {
            content.add("");
        }
        if (this.proteinStart != null) {
            content.add(Integer.toString(this.proteinStart));
        } else {
            content.add("");
        }
        if (this.proteinEnd != null) {
            content.add(Integer.toString(this.proteinEnd));
        } else {
            content.add("");
        }

        if (this.projection != null) {
            content.add(this.projection.toString());
        } else {
            content.add("");
        }


        return StringUtils.join(content.toArray(), "&");
    }

    @Override
    public String toString() {
        return getQueryId();
    }
}


