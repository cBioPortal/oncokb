package org.mskcc.cbio.oncokb.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.model.*;

import java.util.*;

/**
 * Created by Hongxin on 8/10/15.
 */
public class EvidenceUtils {
    private static EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

    /**
     * Remove evidences if its alteration in the alteration list
     *
     * @param evidences
     * @param alterations
     * @return
     */
    public static List<Evidence> removeByAlterations(List<Evidence> evidences, Collection<Alteration> alterations) {
        if (alterations != null) {
            Iterator<Evidence> i = evidences.iterator();
            while (i.hasNext()) {
                Boolean contain = false;
                Evidence evidence = i.next();
                for (Alteration alteration : alterations) {
                    if (alteration != null) {
                        for (Alteration eviAlt : evidence.getAlterations()) {
                            if (eviAlt != null && alteration.equals(eviAlt)) {
                                contain = true;
                                break;
                            }
                        }
                        if (contain) {
                            i.remove();
                            break;
                        }
                    }
                }
            }
        }
        return evidences;
    }

    public static Set<Evidence> getRelevantEvidences(
        Query query, String source, String geneStatus,
        Set<EvidenceType> evidenceTypes, Set<LevelOfEvidence> levelOfEvidences) {
        if (query == null) {
            return new HashSet<>();
        }
        Gene gene = query.getEntrezGeneId() == null ? GeneUtils.getGeneByHugoSymbol(query.getHugoSymbol())
            : GeneUtils.getGeneByEntrezId(query.getEntrezGeneId());
        if (gene != null) {
            String variantId = query.getQueryId() +
                (source != null ? ("&" + source) : "") +
                "&" + evidenceTypes.toString() +
                (levelOfEvidences == null ? "" : ("&" + levelOfEvidences.toString()));
            Alteration alt = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                null, query.getConsequence(), query.getProteinStart(), query.getProteinEnd());
            Set<Alteration> relevantAlterations = AlterationUtils.getRelevantAlterations(alt);

            Set<Evidence> relevantEvidences;
            List<OncoTreeType> relevantTumorTypes = new ArrayList<>();
            if (query.getTumorType() != null) {
                relevantTumorTypes = TumorTypeUtils.getMappedOncoTreeTypesBySource(query.getTumorType(), source);
            }
            EvidenceQueryRes evidenceQueryRes = new EvidenceQueryRes();
            evidenceQueryRes.setGene(gene);
            evidenceQueryRes.setQuery(query);
            evidenceQueryRes.setAlterations(new ArrayList<>(relevantAlterations));
            evidenceQueryRes.setOncoTreeTypes(relevantTumorTypes);
            evidenceQueryRes.setLevelOfEvidences(levelOfEvidences == null ? null : new ArrayList<>(levelOfEvidences));
            List<EvidenceQueryRes> evidenceQueryResList = new ArrayList<>();
            evidenceQueryResList.add(evidenceQueryRes);

            if (CacheUtils.isEnabled() && CacheUtils.containRelevantEvidences(gene.getEntrezGeneId(), variantId)) {
                relevantEvidences = CacheUtils.getRelevantEvidences(gene.getEntrezGeneId(), variantId);
            } else {
                relevantEvidences = getEvidence(evidenceQueryResList, evidenceTypes, geneStatus, levelOfEvidences);
                if (CacheUtils.isEnabled()) {
                    CacheUtils.setRelevantEvidences(gene.getEntrezGeneId(), variantId, relevantEvidences);
                }
            }

            return filterEvidence(relevantEvidences, evidenceQueryRes);
        } else {
            return new HashSet<>();
        }
    }

    public static Set<Evidence> getEvidenceByEvidenceTypesAndLevels(Set<EvidenceType> types, Set<LevelOfEvidence> levels) {
        if (CacheUtils.isEnabled()) {
            String levelStr = levels.toString();
            String typeStr = types.toString();
            StringBuilder sb = new StringBuilder();

            if (types != null) {
                sb.append(typeStr);
            }
            if (levelStr != null) {
                sb.append(levelStr);
            }
            String variantId = sb.toString();

            if (!CacheUtils.containRelevantEvidences(-1, variantId)) {
                Set<Alteration> alterations = AlterationUtils.getAllAlterations();
                Set<Evidence> evidences = EvidenceUtils.getEvidence(alterations, types, levels);
                CacheUtils.setRelevantEvidences(-1, variantId, evidences);
            }
            return CacheUtils.getRelevantEvidences(-1, variantId);
        } else {
            Set<Alteration> alterations = AlterationUtils.getAllAlterations();
            Set<Evidence> evidences = EvidenceUtils.getEvidence(alterations, types, levels);
            return evidences;
        }
    }

    private static Set<Evidence> getEvidence(Set<Alteration> alterations) {
        if (alterations == null || alterations.size() == 0) {
            return new HashSet<>();
        }
        if (CacheUtils.isEnabled()) {
            return getAlterationEvidences(alterations);
        } else {
            return new HashSet<>(evidenceBo.findEvidencesByAlteration(alterations));
        }
    }

    public static Set<Evidence> getEvidence(Set<Alteration> alterations, Set<EvidenceType> evidenceTypes, Set<LevelOfEvidence> levelOfEvidences) {
        if (alterations == null) {
            alterations = new HashSet<>();
        }
        if (evidenceTypes == null) {
            evidenceTypes = new HashSet<>();
        }
        if (levelOfEvidences == null) {
            levelOfEvidences = new HashSet<>();
        }
        if (alterations.size() == 0) {
            return new HashSet<>();
        }
        if (evidenceTypes.size() == 0 && levelOfEvidences.size() == 0) {
            return getEvidence(alterations);
        }
        if (CacheUtils.isEnabled()) {
            Set<Evidence> alterationEvidences = getAlterationEvidences(new HashSet<>(alterations));
            Set<Evidence> result = new HashSet<>();

            for (Evidence evidence : alterationEvidences) {
                if (evidenceTypes.size() > 0 && !evidenceTypes.contains(evidence.getEvidenceType())) {
                    continue;
                }
                if (levelOfEvidences.size() > 0 && !levelOfEvidences.contains(evidence.getLevelOfEvidence())) {
                    continue;
                }
                result.add(evidence);
            }
            return result;
        } else {
            if (levelOfEvidences.size() == 0) {
                return new HashSet<>(evidenceBo.findEvidencesByAlteration(alterations, evidenceTypes));
            } else {
                return new HashSet<>(evidenceBo.findEvidencesByAlterationWithLevels(alterations, evidenceTypes, levelOfEvidences));
            }
        }
    }

    public static Set<Evidence> getEvidence(Set<Alteration> alterations, Set<EvidenceType> evidenceTypes, Set<OncoTreeType> tumorTypes, Set<LevelOfEvidence> levelOfEvidences) {
        if (alterations == null || alterations.size() == 0) {
            return new HashSet<>();
        }
        if (evidenceTypes == null || evidenceTypes.size() == 0) {
            return getEvidence(alterations);
        }
        if (tumorTypes == null || tumorTypes.size() == 0) {
            return getEvidence(alterations, evidenceTypes, levelOfEvidences);
        }
        if (levelOfEvidences == null || levelOfEvidences.size() == 0) {
            return new HashSet<>(evidenceBo.findEvidencesByAlteration(alterations, evidenceTypes, tumorTypes));
        } else {
            return new HashSet<>(evidenceBo.findEvidencesByAlteration(alterations, evidenceTypes, tumorTypes, levelOfEvidences));
        }
    }

    public static Set<Evidence> getEvidence(List<EvidenceQueryRes> queries, Set<EvidenceType> evidenceTypes, String geneStatus, Set<LevelOfEvidence> levelOfEvidences) {
        Set<Evidence> evidences = new HashSet<>();
        List<EvidenceType> filteredETs = new ArrayList<>();

        Map<Integer, Gene> genes = new HashMap<>(); //Get gene evidences
        Map<Integer, Alteration> alterations = new HashMap<>();
        Map<Integer, Alteration> alterationsME = new HashMap<>(); //Mutation effect only
        Set<OncoTreeType> tumorTypes = new HashSet<>();

        for (EvidenceQueryRes query : queries) {
            if (query.getGene() != null) {
                int entrezGeneId = query.getGene().getEntrezGeneId();
                if (!genes.containsKey(entrezGeneId)) {
                    genes.put(entrezGeneId, query.getGene());
                }

                if (query.getAlterations() != null) {
                    for (Alteration alt : query.getAlterations()) {
                        int altId = alt.getId();
                        if (!alterations.containsKey(altId)) {
                            alterations.put(altId, alt);
                        }
                    }
                }

                if (query.getOncoTreeTypes() != null) {
                    for (OncoTreeType tumorType : query.getOncoTreeTypes()) {
                        if (!tumorTypes.contains(tumorType)) {
                            tumorTypes.add(tumorType);
                        }
                    }
                }
            }
        }

        if (evidenceTypes.contains(EvidenceType.GENE_SUMMARY)) {
            filteredETs.add(EvidenceType.GENE_SUMMARY);
        }
        if (evidenceTypes.contains(EvidenceType.GENE_BACKGROUND)) {
            filteredETs.add(EvidenceType.GENE_BACKGROUND);
        }
        if (filteredETs.size() > 0) {
            Map<Gene, Set<Evidence>> mappedEvidences =
                EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(new HashSet<>(genes.values()), new HashSet<>(filteredETs));

            for (Map.Entry<Gene, Set<Evidence>> cursor : mappedEvidences.entrySet()) {
                evidences.addAll(cursor.getValue());
            }
        }

        Set<Alteration> alts = new HashSet<>();
        alts.addAll(alterations.values());
        alts.addAll(alterationsME.values());

        if (evidenceTypes.contains(EvidenceType.MUTATION_EFFECT)) {
            filteredETs.add(EvidenceType.MUTATION_EFFECT);
            evidences.addAll(getEvidence(alts, Collections.singleton(EvidenceType.MUTATION_EFFECT), null));
        }
        if (evidenceTypes.contains(EvidenceType.ONCOGENIC)) {
            filteredETs.add(EvidenceType.ONCOGENIC);
            evidences.addAll(getEvidence(alts, Collections.singleton(EvidenceType.ONCOGENIC), null));
        }
        if (evidenceTypes.contains(EvidenceType.VUS)) {
            filteredETs.add(EvidenceType.VUS);
            evidences.addAll(getEvidence(alts, Collections.singleton(EvidenceType.VUS), null));
        }
        if (evidenceTypes.size() != filteredETs.size()) {
            //Include all level 1 evidences
            Set<EvidenceType> tmpTypes = new HashSet<>();
            tmpTypes.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
            tmpTypes.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
            evidences.addAll(getEvidence(new HashSet<>(alterations.values()), tmpTypes, levelOfEvidences));

            evidenceTypes.remove(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
            evidenceTypes.remove(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);

            Set<Evidence> tumorTypesEvidences = getEvidence(new HashSet<>(alterations.values()), evidenceTypes, tumorTypes.isEmpty() ? null : tumorTypes, levelOfEvidences);

            evidences.addAll(tumorTypesEvidences);
        }
        return evidences;
    }

    public static Set<Evidence> getAlterationEvidences(Set<Alteration> alterations) {
        Set<Evidence> evidences = new HashSet<>();

        if (CacheUtils.isEnabled()) {
            Set<Evidence> geneEvidences = new HashSet<>();
            for (Alteration alteration : alterations) {
                geneEvidences.addAll(CacheUtils.getEvidences(alteration.getGene()));
            }
            for (Evidence evidence : geneEvidences) {
                if (!Collections.disjoint(evidence.getAlterations(), alterations)) {
                    evidences.add(evidence);
                }
            }
        } else {
            evidences = new HashSet<>(evidenceBo.findEvidencesByAlteration(alterations));
        }
        return evidences;
    }

    public static Map<Gene, Set<Evidence>> getEvidenceByGenes(Set<Gene> genes) {
        Map<Gene, Set<Evidence>> evidences = new HashMap<>();
        if (CacheUtils.isEnabled()) {
            for (Gene gene : genes) {
                if (gene != null) {
                    evidences.put(gene, CacheUtils.getEvidences(gene));
                }
            }
        } else {
            evidences = EvidenceUtils.separateEvidencesByGene(genes, new HashSet<Evidence>(ApplicationContextSingleton.getEvidenceBo().findAll()));
        }
        return evidences;
    }

    public static Map<Gene, Set<Evidence>> getEvidenceByGenesAndEvidenceTypes(Set<Gene> genes, Set<EvidenceType> evidenceTypes) {
        Map<Gene, Set<Evidence>> result = new HashMap<>();
        if (CacheUtils.isEnabled()) {
            for (Gene gene : genes) {
                if (gene != null) {
                    Set<Evidence> evidences = CacheUtils.getEvidences(gene);
                    Set<Evidence> filtered = new HashSet<>();
                    for (Evidence evidence : evidences) {
                        if (evidenceTypes.contains(evidence.getEvidenceType())) {
                            filtered.add(evidence);
                        }
                    }
                    result.put(gene, filtered);
                }
            }
        } else {
            result = EvidenceUtils.separateEvidencesByGene(genes, new HashSet<Evidence>(ApplicationContextSingleton.getEvidenceBo().findAll()));
            for (Gene gene : genes) {
                Set<Evidence> evidences = result.get(gene);

                for (Evidence evidence : evidences) {
                    if (!evidenceTypes.contains(evidence.getEvidenceType())) {
                        evidences.remove(evidence);
                    }
                }
            }
        }
        return result;
    }

    public static Set<Evidence> getEvidenceByGeneAndEvidenceTypes(Gene gene, Set<EvidenceType> evidenceTypes) {
        Set<Evidence> result = new HashSet<>();
        if (gene != null) {
            if (CacheUtils.isEnabled()) {
                Set<Evidence> evidences = CacheUtils.getEvidences(gene);
                for (Evidence evidence : evidences) {
                    if (evidenceTypes.contains(evidence.getEvidenceType())) {
                        result.add(evidence);
                    }
                }
            } else {
                List<Evidence> evidences = evidenceBo.findEvidencesByGene(Collections.singleton(gene), evidenceTypes);
                if (evidences != null) {
                    result = new HashSet<>(evidences);
                }
            }
        }
        return result;
    }

    public static Set<Evidence> convertEvidenceLevel(Set<Evidence> evidences, Set<OncoTreeType> tumorTypes) {
        Set<Evidence> tmpEvidences = new HashSet<>();

        for (Evidence evidence : evidences) {
            Evidence tmpEvidence = new Evidence(evidence);
            if (CollectionUtils.intersection(Collections.singleton(tmpEvidence.getOncoTreeType()), tumorTypes).isEmpty()) {
                if (tmpEvidence.getLevelOfEvidence() != null) {
                    if (tmpEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_1) ||
                        tmpEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_2A)) {
                        tmpEvidence.setLevelOfEvidence(LevelOfEvidence.LEVEL_2B);
                    } else if (tmpEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_3A)) {
                        tmpEvidence.setLevelOfEvidence(LevelOfEvidence.LEVEL_3B);
                    }
                }
            }
            tmpEvidences.add(tmpEvidence);
        }
        return tmpEvidences;
    }

    public static Set<Evidence> filterEvidence(Set<Evidence> evidences, EvidenceQueryRes evidenceQuery) {
        Set<Evidence> filtered = new HashSet<>();

        if (evidenceQuery.getGene() != null) {
            for (Evidence evidence : evidences) {
                Evidence tempEvidence = new Evidence(evidence);
                if (tempEvidence.getGene().equals(evidenceQuery.getGene())) {
                    //Add all gene specific evidences
                    if (tempEvidence.getAlterations().isEmpty()) {
                        filtered.add(tempEvidence);
                    } else {
                        if (!CollectionUtils.intersection(tempEvidence.getAlterations(), evidenceQuery.getAlterations()).isEmpty()) {
                            if (tempEvidence.getOncoTreeType() == null) {
                                if (tempEvidence.getEvidenceType().equals(EvidenceType.ONCOGENIC)) {
                                    if (tempEvidence.getDescription() == null) {
                                        List<Alteration> alterations = new ArrayList<>();
                                        alterations.addAll(tempEvidence.getAlterations());
//                                        tempEvidence.setDescription(SummaryUtils.variantSummary(Collections.singleton(tempEvidence.getGene()), alterations, evidenceQuery.getQueryAlteration(), Collections.singleton(tempEvidence.getTumorType()), evidenceQuery.getQueryTumorType()));
                                    }
                                }
                                filtered.add(tempEvidence);
                            } else {
                                List<OncoTreeType> tumorType = new ArrayList<>();

                                if (tempEvidence.getOncoTreeType() != null) {
                                    tumorType.add(tempEvidence.getOncoTreeType());
                                }

                                if (!Collections.disjoint(evidenceQuery.getOncoTreeTypes(), tumorType)) {
                                    filtered.add(tempEvidence);
                                } else {
                                    if (tempEvidence.getLevelOfEvidence() != null) {
                                        if (tempEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_1) ||
                                            tempEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_2A)) {
                                            if (evidenceQuery.getLevelOfEvidences() == null
                                                || evidenceQuery.getLevelOfEvidences().size() == 0
                                                || evidenceQuery.getLevelOfEvidences().contains(LevelOfEvidence.LEVEL_2B)) {
                                                tempEvidence.setLevelOfEvidence(LevelOfEvidence.LEVEL_2B);
                                                filtered.add(tempEvidence);
                                            }
                                        } else if (tempEvidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_3A)) {
                                            if (evidenceQuery.getLevelOfEvidences() == null
                                                || evidenceQuery.getLevelOfEvidences().size() == 0
                                                || evidenceQuery.getLevelOfEvidences().contains(LevelOfEvidence.LEVEL_3B)) {
                                                tempEvidence.setLevelOfEvidence(LevelOfEvidence.LEVEL_3B);
                                                filtered.add(tempEvidence);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return filtered;
    }

    public static List<Evidence> filterAlteration(List<Evidence> evidences, List<Alteration> alterations) {
        for (Evidence evidence : evidences) {
            Set<Alteration> filterEvidences = new HashSet<>();
            for (Alteration alt : evidence.getAlterations()) {
                if (alterations.contains(alt)) {
                    filterEvidences.add(alt);
                }
            }
            evidence.getAlterations().clear();
            evidence.setAlterations(filterEvidences);
        }

        return evidences;
    }

    public static Map<Gene, Set<Evidence>> separateEvidencesByGene(Set<Gene> genes, Set<Evidence> evidences) {
        Map<Gene, Set<Evidence>> result = new HashMap<>();

        for (Gene gene : genes) {
            result.put(gene, new HashSet<Evidence>());
        }

        for (Evidence evidence : evidences) {
            result.get(evidence.getGene()).add(evidence);
        }
        return result;
    }

    public static MutationEffect getMutationEffectFromEvidence(Set<Evidence> evidences) {
        Set<MutationEffect> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect() != null) {
                result.add(MutationEffect.getByName(evidence.getKnownEffect()));
            }
        }

        if (result.size() > 1) {
            return MainUtils.findHighestMutationEffect(result);
        } else {
            return result.iterator().next();
        }
    }

    public static Oncogenicity getOncogenicityFromEvidence(Set<Evidence> evidences) {
        Set<Oncogenicity> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect() != null) {
                result.add(Oncogenicity.getByLevel(evidence.getKnownEffect()));
            }
        }

        if (result.size() > 1) {
            return MainUtils.findHighestOncogenic(result);
        } else {
            return result.iterator().next();
        }
    }

    public static Set<String> getPmids(Set<Evidence> evidences) {
        Set<String> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            for (Article article : evidence.getArticles()) {
                result.add(article.getPmid());
            }
        }
        return result;
    }

    public static Set<String> getDrugs(Set<Evidence> evidences) {
        Set<String> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            for (Treatment treatment : evidence.getTreatments()) {
                Set<String> drugsInTreatment = new HashSet<>();
                for (Drug drug : treatment.getDrugs()) {
                    drugsInTreatment.add(drug.getDrugName());
                }
                result.add(StringUtils.join(drugsInTreatment, " + "));
            }
        }
        return result;
    }

    public static Map<Gene, Set<Evidence>> getAllGeneBasedEvidences() {
        Set<Gene> genes = GeneUtils.getAllGenes();
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getEvidenceByGenes(genes);
        return evidences;
    }

    public static Set<Evidence> getEvidenceBasedOnHighestOncogenicity(Set<Evidence> evidences) {
        Set<Evidence> filtered = new HashSet<>();
        Map<Oncogenicity, Set<Evidence>> map = new HashMap<>();

        if (evidences == null || evidences.size() == 0)
            return filtered;

        for (Evidence evidence : evidences) {
            if (evidence.getEvidenceType() != null && evidence.getEvidenceType().equals(EvidenceType.ONCOGENIC)) {
                Oncogenicity oncogenicity = Oncogenicity.getByLevel(evidence.getKnownEffect());

                if (oncogenicity != null) {
                    if (!map.containsKey(oncogenicity))
                        map.put(oncogenicity, new HashSet<Evidence>());

                    map.get(oncogenicity).add(evidence);
                }
            }
        }

        Oncogenicity highestOncogenicity = MainUtils.findHighestOncogenicByEvidences(evidences);
        if (map.get(highestOncogenicity) != null)
            filtered = map.get(highestOncogenicity);
        return filtered;
    }

    public static Set<Evidence> getOnlyHighestLevelEvidences(Set<Evidence> evidences) {
        Map<LevelOfEvidence, Set<Evidence>> levels = new HashMap<>();

        for (Evidence evidence : evidences) {
            if (evidence.getLevelOfEvidence() != null) {
                if (!levels.containsKey(evidence.getLevelOfEvidence())) {
                    levels.put(evidence.getLevelOfEvidence(), new HashSet<Evidence>());
                }
                levels.get(evidence.getLevelOfEvidence()).add(evidence);
            }
        }

        Set<LevelOfEvidence> keys = levels.keySet();

        LevelOfEvidence highestLevel = LevelUtils.getHighestLevel(keys);
        if (highestLevel != null) {
            return levels.get(highestLevel);
        } else {
            return new HashSet<>();
        }
    }

    public static Set<Evidence> keepHighestLevelForSameTreatments(Set<Evidence> evidences) {
        Map<String, Set<Evidence>> maps = new HashedMap();
        Set<Evidence> filtered = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getTreatments() != null && evidence.getTreatments().size() > 0) {
                String treatmentsName = TreatmentUtils.getTreatmentName(evidence.getTreatments(), true);
                if (!maps.containsKey(treatmentsName)) {
                    maps.put(treatmentsName, new HashSet<Evidence>());
                }
                maps.get(treatmentsName).add(evidence);
            } else {
                // Keep all un-treatment evidences
                filtered.add(evidence);
            }
        }

        for (Map.Entry<String, Set<Evidence>> entry : maps.entrySet()) {
            Set<Evidence> highestEvis = EvidenceUtils.getOnlyHighestLevelEvidences(entry.getValue());

            // If highestEvis has more than 1 items, find highest original level if the level is 2B, 3B
            if (highestEvis.size() > 1) {
                Set<LevelOfEvidence> checkLevels = new HashSet<>();
                checkLevels.add(LevelOfEvidence.LEVEL_2B);
                checkLevels.add(LevelOfEvidence.LEVEL_3B);
                if (checkLevels.contains(highestEvis.iterator().next().getLevelOfEvidence())) {
                    Set<Integer> evidenceIds = new HashSet<>();
                    for (Evidence evidence : highestEvis) {
                        evidenceIds.add(evidence.getId());
                    }
                    Set<Evidence> originalEvis = EvidenceUtils.getEvidenceByEvidenceIds(evidenceIds);
                    Set<Evidence> highestOriginalEvis = EvidenceUtils.getOnlyHighestLevelEvidences(originalEvis);
                    Set<Integer> filteredIds = new HashSet<>();
                    for (Evidence evidence : highestOriginalEvis) {
                        filteredIds.add(evidence.getId());
                    }
                    for (Evidence evidence : highestEvis) {
                        if (filteredIds.contains(evidence.getId())) {
                            filtered.add(evidence);
                            // Only add one
                            break;
                        }
                    }
                } else {
                    filtered.add(highestEvis.iterator().next());
                }
            } else {
                filtered.addAll(highestEvis);
            }
        }
        return filtered;
    }

    public static Evidence getEvidenceByEvidenceId(Integer id) {
        if (id == null) {
            return null;
        }
        Set<Evidence> evidences = new HashSet<>();
        if (CacheUtils.isEnabled()) {
            evidences = CacheUtils.getEvidencesByIds(Collections.singleton(id));
        } else {
            List<Evidence> evidenceList = evidenceBo.findEvidencesByIds(Collections.singletonList(id));
            if (evidenceList == null) {
                evidences = null;
            } else {
                evidences = new HashSet<>(evidenceList);
            }
        }
        if (evidences == null || evidences.size() > 1) {
            return null;
        }
        return evidences.iterator().next();
    }

    public static Set<Evidence> getEvidenceByEvidenceIds(Set<Integer> ids) {
        if (ids == null) {
            return new HashSet<>();
        }
        if (CacheUtils.isEnabled()) {
            return CacheUtils.getEvidencesByIds(ids);
        } else {
            return new HashSet<>(evidenceBo.findEvidencesByIds(new ArrayList<Integer>(ids)));
        }
    }

    public static Set<Evidence> filterEvidenceByKnownEffect(Set<Evidence> evidences, String knownEffect) {
        if (knownEffect == null) {
            return null;
        }
        Set<Evidence> result = new HashSet<>();
        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect().equalsIgnoreCase(knownEffect)) {
                result.add(evidence);
            }
        }
        return result;
    }

    public static Set<Evidence> getSensitiveEvidences(Set<Evidence> evidences) {
        return filterEvidenceByKnownEffect(evidences, "sensitive");
    }

    public static Set<Evidence> getResistanceEvidences(Set<Evidence> evidences) {
        return filterEvidenceByKnownEffect(evidences, "resistant");
    }

    // Temporary move evidence process methods here in order to share the code between new APIs and legacies
    public static List<EvidenceQueryRes> processRequest(List<Query> requestQueries, Set<EvidenceType> evidenceTypes,
                                                        String geneStatus, String source,
                                                        Set<LevelOfEvidence> levelOfEvidences, Boolean highestLevelOnly) {
        List<EvidenceQueryRes> evidenceQueries = new ArrayList<>();

        if (source == null) {
            source = "quest";
        }

        if (evidenceTypes == null) {
            evidenceTypes = new HashSet<>(MainUtils.getAllEvidenceTypes());
        }

        if (levelOfEvidences == null) {
            levelOfEvidences = LevelUtils.getPublicLevels();
        }

        if (requestQueries == null || requestQueries.size() == 0) {
            Set<Evidence> evidences = new HashSet<>();
            if ((evidenceTypes != null && evidenceTypes.size() > 0) ||
                (levelOfEvidences != null && levelOfEvidences.size() > 0)) {
                evidences = EvidenceUtils.getEvidenceByEvidenceTypesAndLevels(evidenceTypes, levelOfEvidences);
            }
            EvidenceQueryRes query = new EvidenceQueryRes();
            query.setEvidences(new ArrayList<>(evidences));
            return Collections.singletonList(query);
        } else {
            for (Query requestQuery : requestQueries) {
                EvidenceQueryRes query = new EvidenceQueryRes();

                query.setQuery(requestQuery);
                query.setGene(getGene(requestQuery.getEntrezGeneId(), requestQuery.getHugoSymbol()));

                if (query.getGene() != null) {
                    query.setOncoTreeTypes(TumorTypeUtils.getMappedOncoTreeTypesBySource(requestQuery.getTumorType(), source));

                    if (requestQuery.getAlteration() != null) {
                        Alteration alt = AlterationUtils.getAlteration(query.getGene().getHugoSymbol(),
                            requestQuery.getAlteration(), null, requestQuery.getConsequence(),
                            requestQuery.getProteinStart(), requestQuery.getProteinEnd());
                        Set<Alteration> relevantAlts = AlterationUtils.getRelevantAlterations(alt);
                        query.setAlterations(relevantAlts == null ? null : new ArrayList<>(relevantAlts));

                        Alteration alteration = AlterationUtils.getAlteration(requestQuery.getHugoSymbol(), requestQuery.getAlteration(), AlterationType.MUTATION.name(), requestQuery.getConsequence(), requestQuery.getProteinStart(), requestQuery.getProteinEnd());
                        Set<Alteration> allelesAlts = AlterationUtils.getAlleleAlterations(alteration);
                        query.setAlleles(new ArrayList<>(allelesAlts));
                    } else if (query.getOncoTreeTypes() != null && query.getOncoTreeTypes().size() > 0) {
                        // if no alteration assigned, but has tumor type
                        query.setAlterations(new ArrayList<Alteration>(AlterationUtils.getAllAlterations(query.getGene())));
                    }
                }
                if (levelOfEvidences != null) {
                    query.setLevelOfEvidences(new ArrayList<LevelOfEvidence>(levelOfEvidences));
                }
                evidenceQueries.add(query);
            }
        }

        return assignEvidence(EvidenceUtils.getEvidence(evidenceQueries, evidenceTypes, geneStatus, levelOfEvidences),
            evidenceQueries, highestLevelOnly);
    }

    private static Gene getGene(Integer entrezGeneId, String hugoSymbol) {
        Gene gene = null;
        if (entrezGeneId != null && hugoSymbol != null && !GeneUtils.isSameGene(entrezGeneId, hugoSymbol)) {
            return gene;
        } else {
            if (entrezGeneId != null) {
                gene = GeneUtils.getGeneByEntrezId(entrezGeneId);
            } else if (hugoSymbol != null) {
                gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
            }
        }
        return gene;
    }


    private static List<EvidenceQueryRes> assignEvidence(Set<Evidence> evidences, List<EvidenceQueryRes> evidenceQueries,
                                                         Boolean highestLevelOnly) {
        highestLevelOnly = highestLevelOnly == null ? false : highestLevelOnly;

        for (EvidenceQueryRes query : evidenceQueries) {
            query.setEvidences(
                new ArrayList<>(
                    EvidenceUtils.keepHighestLevelForSameTreatments(EvidenceUtils.filterEvidence(evidences, query))));

            // Attach evidence if query doesn't contain any alteration and has alleles.
            if ((query.getAlterations() == null || query.getAlterations().isEmpty() || AlterationUtils.excludeVUS(query.getGene(), new HashSet<>(query.getAlterations())).size() == 0) && (query.getAlleles() != null && !query.getAlleles().isEmpty())) {
                // Get oncogenic and mutation effect evidences
                Set<Alteration> alleles = new HashSet<>(query.getAlleles());
                Set<Evidence> oncogenics = EvidenceUtils.getEvidence(alleles, Collections.singleton(EvidenceType.ONCOGENIC), null);
                Oncogenicity highestOncogenic = MainUtils.findHighestOncogenicByEvidences(oncogenics);
                if (highestOncogenic != null) {
                    Evidence recordMatchHighestOncogenicity = null;

                    for (Evidence evidence : oncogenics) {
                        if (evidence.getKnownEffect() != null) {
                            Oncogenicity oncogenicity = Oncogenicity.getByLevel(evidence.getKnownEffect());
                            if (oncogenicity != null && oncogenicity.equals(highestOncogenic)) {
                                recordMatchHighestOncogenicity = evidence;
                                break;
                            }
                        }
                    }

                    if (recordMatchHighestOncogenicity != null) {
                        Oncogenicity alleleOncogenicity = MainUtils.setToAlleleOncogenicity(highestOncogenic);
                        Evidence evidence = new Evidence();
                        evidence.setId(recordMatchHighestOncogenicity.getId());
                        evidence.setGene(recordMatchHighestOncogenicity.getGene());
                        evidence.setEvidenceType(EvidenceType.ONCOGENIC);
                        evidence.setKnownEffect(alleleOncogenicity == null ? "" : alleleOncogenicity.getOncogenic());
                        query.getEvidences().add(evidence);
                    }
                }

                Set<Alteration> altsWithHighestOncogenicity = new HashSet<>();

                for (Evidence evidence : EvidenceUtils.getEvidenceBasedOnHighestOncogenicity(new HashSet<Evidence>(oncogenics))) {
                    for (Alteration alt : evidence.getAlterations()) {
                        if (alleles.contains(alt)) {
                            altsWithHighestOncogenicity.add(alt);
                        }
                    }
                }

                Set<Evidence> mutationEffectsEvis = EvidenceUtils.getEvidence(altsWithHighestOncogenicity, Collections.singleton(EvidenceType.MUTATION_EFFECT), null);
                if (mutationEffectsEvis != null && mutationEffectsEvis.size() > 0) {
                    Set<String> effects = new HashSet<>();

                    for (Evidence mutationEffectEvi : mutationEffectsEvis) {
                        effects.add(mutationEffectEvi.getKnownEffect());
                    }

                    Evidence mutationEffect = new Evidence();
                    Evidence example = mutationEffectsEvis.iterator().next();
                    mutationEffect.setId(example.getId());
                    mutationEffect.setGene(example.getGene());
                    mutationEffect.setEvidenceType(EvidenceType.MUTATION_EFFECT);
                    mutationEffect.setKnownEffect(MainUtils.getAlleleConflictsMutationEffect(effects));
                    query.getEvidences().add(mutationEffect);
                }

                // Get treatment evidences
                Set<Evidence> alleleEvidences = EvidenceUtils.getEvidence(alleles, MainUtils.getSensitiveTreatmentEvidenceTypes(), LevelUtils.getPublicLevels());
                Set<Evidence> alleleEvidencesCopy = new HashSet<>();
                if (alleleEvidences != null) {
                    LevelOfEvidence highestLevelFromEvidence = LevelUtils.getHighestLevelFromEvidence(new HashSet<>(alleleEvidences));
                    if (highestLevelFromEvidence != null && LevelUtils.getPublicLevels().contains(highestLevelFromEvidence)) {
                        alleleEvidences = EvidenceUtils.getEvidence(alleles, MainUtils.getSensitiveTreatmentEvidenceTypes(), Collections.singleton(highestLevelFromEvidence));
                        for (Evidence evidence : alleleEvidences) {
                            Evidence tmpEvidence = new Evidence(evidence);
                            tmpEvidence.setLevelOfEvidence(LevelUtils.setToAlleleLevel(evidence.getLevelOfEvidence(), CollectionUtils.intersection(Collections.singleton(evidence.getOncoTreeType()), query.getOncoTreeTypes()).size() > 0));
                            alleleEvidencesCopy.add(tmpEvidence);
                        }
                        query.getEvidences().addAll(alleleEvidencesCopy);
                    }
                }
            }

            if (highestLevelOnly) {
                Set<Evidence> allEvidences = new HashSet<>(query.getEvidences());
                List<Evidence> filteredEvidences = new ArrayList<>();

                // Get highest sensitive evidences
                Set<Evidence> sensitiveEvidences = EvidenceUtils.getSensitiveEvidences(allEvidences);
                filteredEvidences.addAll(EvidenceUtils.getOnlyHighestLevelEvidences(sensitiveEvidences));

                // Get highest resistance evidences
                Set<Evidence> resistanceEvidences = EvidenceUtils.getResistanceEvidences(allEvidences);
                filteredEvidences.addAll(EvidenceUtils.getOnlyHighestLevelEvidences(resistanceEvidences));

                query.setEvidences(filteredEvidences);
            }
        }
        return evidenceQueries;
    }
}
