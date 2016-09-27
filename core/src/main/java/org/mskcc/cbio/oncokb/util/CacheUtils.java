package org.mskcc.cbio.oncokb.util;

import org.apache.commons.collections.map.HashedMap;
import org.mskcc.cbio.oncokb.model.*;

import java.util.*;


/**
 * Created by Hongxin on 4/1/16.
 * <p/>
 * CacheUtils is used to manage cached variant summaries, relevant alterations, alterations which all gene based.
 * It also includes mapped tumor types which is based on query tumor type name + source.
 * <p/>
 * The GeneObservable manages all gene based caches. Any updates happen on gene will automatically trigger
 * GeneObservable to notify all observers to update relative cache.
 * <p/>
 * TODO:
 * Ideally, we should place cache functions in the cache BAO with a factory which controls the source of data.
 * In this way, user can easily to choose to get data from cache or database directly.
 */


public class CacheUtils {
    private static Map<Integer, Map<String, String>> variantSummary = new HashMap<>();
    private static Map<Integer, Map<String, Map<String, String>>> variantTumorTypeSummary = new HashMap<>();
    private static Map<Integer, Map<String, Set<Integer>>> relevantAlterations = new HashMap<>();
    private static Map<Integer, Gene> genesByEntrezId = new HashMap<>();
    // The key would be entrezGeneId, variant name and evidence ID. -1 will be used to store gene irrelevant evidences.
    private static Map<Integer, Map<String, Set<Integer>>> relevantEvidences = new HashMap<>();
    private static Map<Integer, Set<Alteration>> VUS = new HashMap<>();

    private static Map<String, List<OncoTreeType>> mappedTumorTypes = new HashMap<>();
    private static Map<String, Integer> hugoSymbolToEntrez = new HashMap<>();
    private static Map<String, List<OncoTreeType>> allOncoTreeTypes = new HashMap<>(); //Tag by different categories. main or subtype
    private static Map<String, Object> numbers = new HashMap<>();

    private static String status = "enabled"; //Current cacheUtils status. Applicable value: disabled enabled

    // Cache metadata from database
    private static Set<Gene> genes = new HashSet<>();
    private static Map<Integer, Set<Evidence>> evidences = new HashMap<>(); //Gene based evidences 
    private static Map<Integer, Set<Alteration>> alterations = new HashMap<>(); //Gene based evidences 

    private static Observer variantSummaryObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                variantSummary.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                variantSummary.clear();
            }
        }
    };

    private static Observer variantTumorTypeSummaryObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                variantTumorTypeSummary.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                variantTumorTypeSummary.clear();
            }
        }
    };

    private static Observer relevantAlterationsObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                relevantAlterations.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                relevantAlterations.clear();
            }
        }
    };

    private static Observer numbersObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            numbers.clear();
        }
    };

    private static Observer VUSObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                Gene gene = GeneUtils.getGeneByEntrezId(entrezGeneId);
                VUS.remove(entrezGeneId);
                VUS.put(entrezGeneId, AlterationUtils.findVUSFromEvidences(EvidenceUtils.getEvidenceByGenes(Collections.singleton(gene)).get(gene)));
            } else if (operation.get("cmd") == "reset") {
                VUS.clear();
            }
        }
    };

    private static Observer alterationsObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                alterations.remove(entrezGeneId);
            } else if (operation.get("cmd") == "reset") {
                alterations.clear();
            }
        }
    };

    private static Observer geneObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                genesByEntrezId.remove(entrezGeneId);
                Iterator it = hugoSymbolToEntrez.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    if ((pair.getValue()).equals(entrezGeneId)) {
                        it.remove();
                    }
                }
            } else if (operation.get("cmd") == "reset") {
                genesByEntrezId.clear();
                hugoSymbolToEntrez.clear();
            }
        }
    };

    private static Observer genesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            genes = new HashSet<>(ApplicationContextSingleton.getGeneBo().findAll());
        }
    };

    private static Observer relevantEvidencesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                relevantEvidences.remove(entrezGeneId);
                //Remove gene irrelevant evidences 
                relevantEvidences.remove(-1);
            } else if (operation.get("cmd") == "reset") {
                relevantEvidences.clear();
            }
        }
    };

    private static Observer evidencesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            Map<String, String> operation = (Map<String, String>) arg;
            if (operation.get("cmd") == "update") {
                Integer entrezGeneId = Integer.parseInt(operation.get("val"));
                Gene gene = GeneUtils.getGeneByEntrezId(entrezGeneId);
                evidences.remove(entrezGeneId);
                evidences.put(entrezGeneId, new HashSet<>(
                    ApplicationContextSingleton.getEvidenceBo().findEvidencesByGene(Collections.singleton(gene))));

            } else if (operation.get("cmd") == "reset") {
                evidences.clear();
                Map<Gene, Set<Evidence>> mappedEvidence =
                    EvidenceUtils.separateEvidencesByGene(genes, new HashSet<>(
                        ApplicationContextSingleton.getEvidenceBo().findAll()));
                Iterator it = mappedEvidence.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
                    evidences.put(pair.getKey().getEntrezGeneId(), pair.getValue());
                }
            }
        }
    };

    private static Observer allCancerTypesObserver = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
            allOncoTreeTypes.put("main", TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes()));
            allOncoTreeTypes.put("subtype", TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes()));
        }
    };

    static {
        try {
            GeneObservable.getInstance().addObserver(variantSummaryObserver);
            GeneObservable.getInstance().addObserver(variantTumorTypeSummaryObserver);
            GeneObservable.getInstance().addObserver(relevantAlterationsObserver);
            GeneObservable.getInstance().addObserver(alterationsObserver);
            GeneObservable.getInstance().addObserver(geneObserver);
            GeneObservable.getInstance().addObserver(relevantEvidencesObserver);
            GeneObservable.getInstance().addObserver(allCancerTypesObserver);
            GeneObservable.getInstance().addObserver(genesObserver);
            GeneObservable.getInstance().addObserver(evidencesObserver);
            GeneObservable.getInstance().addObserver(VUSObserver);
            GeneObservable.getInstance().addObserver(numbersObserver);

            genes = new HashSet<>(ApplicationContextSingleton.getGeneBo().findAll());

            Map<Gene, Set<Evidence>> mappedEvidence = EvidenceUtils.separateEvidencesByGene(genes, new HashSet<>(ApplicationContextSingleton.getEvidenceBo().findAll()));
            Iterator it = mappedEvidence.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
                evidences.put(pair.getKey().getEntrezGeneId(), pair.getValue());
            }
            for (Map.Entry<Integer, Set<Evidence>> entry : evidences.entrySet()) {
                setVUS(entry.getKey(), entry.getValue());
            }
            allOncoTreeTypes.put("main", TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes()));
            allOncoTreeTypes.put("subtype", TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes()));

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static Gene getGeneByEntrezId(Integer entrezId) {
        if (genesByEntrezId.containsKey(entrezId)) {
            return genesByEntrezId.get(entrezId);
        } else {
            return null;
        }
    }

    public static Boolean containGeneByEntrezId(Integer entrezId) {
        return genesByEntrezId.containsKey(entrezId) ? true : false;
    }

    public static void setGeneByEntrezId(Gene gene) {
        if (gene != null) {
            genesByEntrezId.put(gene.getEntrezGeneId(), gene);
            hugoSymbolToEntrez.put(gene.getHugoSymbol(), gene.getEntrezGeneId());
        }
    }

    public static Gene getGeneByHugoSymbol(String hugoSymbol) {
        Integer entrezGeneId = hugoSymbolToEntrez.get(hugoSymbol);
        if (entrezGeneId == null)
            return null;

        return genesByEntrezId.get(entrezGeneId);
    }

    public static Boolean containGeneByHugoSymbol(String hugoSymbol) {
        Integer entrezGeneId = hugoSymbolToEntrez.get(hugoSymbol);
        if (entrezGeneId == null)
            return false;

        if (genesByEntrezId.get(entrezGeneId) == null) {
            return false;
        } else {
            return true;
        }
    }

    public static void setGeneByHugoSymbol(Gene gene) {
        if (gene != null) {
            hugoSymbolToEntrez.put(gene.getHugoSymbol(), gene.getEntrezGeneId());
            genesByEntrezId.put(gene.getEntrezGeneId(), gene);
        }
    }

    public static Set<Evidence> getRelevantEvidences(Integer entrezGeneId, String variant) {
        if (relevantEvidences.containsKey(entrezGeneId) && relevantEvidences.get(entrezGeneId).containsKey(variant)) {
            Set<Integer> mappedEntrez = relevantEvidences.get(entrezGeneId).get(variant);
            Set<Evidence> geneEvidences = new HashSet<>();
            Set<Evidence> mappedEvidences = new HashSet<>();

            if (entrezGeneId == -1) {
                for (Map.Entry<Integer, Set<Evidence>> map : evidences.entrySet()) {
                    geneEvidences.addAll(map.getValue());
                }
            } else {
                geneEvidences = evidences.get(entrezGeneId);
            }
            for (Evidence evidence : geneEvidences) {
                if (mappedEntrez.contains(evidence.getEvidenceId())) {
                    mappedEvidences.add(evidence);
                }
            }
            return mappedEvidences;
        } else {
            return new HashSet<>();
        }
    }

    public static Boolean containRelevantEvidences(Integer entrezGeneId, String variant) {
        return (relevantEvidences.containsKey(entrezGeneId) &&
            relevantEvidences.get(entrezGeneId).containsKey(variant)) ? true : false;
    }

    public static void setVUS(Integer entrezGeneId, Set<Evidence> evidences) {
        if (!VUS.containsKey(entrezGeneId)) {
            VUS.put(entrezGeneId, new HashSet<Alteration>());
        }
        VUS.put(entrezGeneId, AlterationUtils.findVUSFromEvidences(evidences));
    }

    public static Set<Alteration> getVUS(Integer entrezGeneId) {
        if (VUS.containsKey(entrezGeneId)) {
            return VUS.get(entrezGeneId);
        } else {
            return new HashSet<>();
        }
    }

    public static void setNumbers(String type, Object number) {
        numbers.put(type, number);
    }

    public static Object getNumbers(String type) {
        return numbers.get(type);
    }

    public static void setRelevantEvidences(Integer entrezGeneId, String variant, Set<Evidence> evidences) {
        if (!relevantEvidences.containsKey(entrezGeneId)) {
            relevantEvidences.put(entrezGeneId, new HashMap<String, Set<Integer>>());
        }
        Set<Integer> mappedEvidenceIds = new HashSet<>();
        for (Evidence evidence : evidences) {
            mappedEvidenceIds.add(evidence.getEvidenceId());
        }
        relevantEvidences.get(entrezGeneId).put(variant, mappedEvidenceIds);
    }

    public static String getVariantSummary(Integer entrezGeneId, String variant) {
        if (variantSummary.containsKey(entrezGeneId) && variantSummary.get(entrezGeneId).containsKey(variant)) {
            return variantSummary.get(entrezGeneId).get(variant);
        } else {
            return null;
        }
    }

    public static Boolean containVariantSummary(Integer entrezGeneId, String variant) {
        return (variantSummary.containsKey(entrezGeneId) &&
            variantSummary.get(entrezGeneId).containsKey(variant)) ? true : false;
    }

    public static void setVariantSummary(Integer entrezGeneId, String variant, String summary) {
        if (!variantSummary.containsKey(entrezGeneId)) {
            variantSummary.put(entrezGeneId, new HashMap<String, String>());
        }
        variantSummary.get(entrezGeneId).put(variant, summary);
    }

    public static String getVariantTumorTypeSummary(Integer entrezGeneId, String variant, String tumorType) {
        if (variantTumorTypeSummary.containsKey(entrezGeneId)
            && variantTumorTypeSummary.get(entrezGeneId).containsKey(variant)
            && variantTumorTypeSummary.get(entrezGeneId).get(variant).containsKey(tumorType)) {
            return variantTumorTypeSummary.get(entrezGeneId).get(variant).get(tumorType);
        } else {
            return null;
        }
    }

    public static Boolean containVariantTumorTypeSummary(Integer entrezGeneId, String variant, String tumorType) {
        return (variantTumorTypeSummary.containsKey(entrezGeneId)
            && variantTumorTypeSummary.get(entrezGeneId).containsKey(variant)
        && variantTumorTypeSummary.get(entrezGeneId).get(variant).containsKey(tumorType)) ? true : false;
    }

    public static void setVariantTumorTypeSummary(Integer entrezGeneId, String variant, String tumorType, String summary) {
        if (!variantTumorTypeSummary.containsKey(entrezGeneId)) {
            variantTumorTypeSummary.put(entrezGeneId, new HashMap<String, Map<String, String>>());
        }
        if (!variantTumorTypeSummary.get(entrezGeneId).containsKey(variant)) {
            variantTumorTypeSummary.get(entrezGeneId).put(variant, new HashMap<String, String>());
        }
        variantTumorTypeSummary.get(entrezGeneId).get(variant).put(tumorType, summary);
    }

    public static Set<Alteration> getRelevantAlterations(Integer entrezGeneId, String variant) {
        if (relevantAlterations.containsKey(entrezGeneId) && relevantAlterations.get(entrezGeneId).containsKey(variant)) {
            Set<Integer> mappedAltsIds = relevantAlterations.get(entrezGeneId).get(variant);
            Set<Alteration> mappedAlts = new HashSet<>();
            Set<Alteration> geneAlts = alterations.get(entrezGeneId);
            for (Alteration alteration : geneAlts) {
                if (mappedAltsIds.contains(alteration.getAlterationId())) {
                    mappedAlts.add(alteration);
                }
            }
            return mappedAlts;
        } else {
            return new HashSet<>();
        }
    }

    public static Boolean containRelevantAlterations(Integer entrezGeneId, String variant) {
        return (relevantAlterations.containsKey(entrezGeneId) &&
            relevantAlterations.get(entrezGeneId).containsKey(variant)) ? true : false;
    }

    public static void setRelevantAlterations(Integer entrezGeneId, String variant, Set<Alteration> alts) {
        if (!relevantAlterations.containsKey(entrezGeneId)) {
            relevantAlterations.put(entrezGeneId, new HashMap<String, Set<Integer>>());
        }
        Set<Integer> mappedAltsIds = new HashSet<>();
        for (Alteration alteration : alts) {
            mappedAltsIds.add(alteration.getAlterationId());
        }
        relevantAlterations.get(entrezGeneId).put(variant, mappedAltsIds);
    }

    public static Set<Alteration> getAlterations(Integer entrezGeneId) {
        return alterations.get(entrezGeneId);
    }

    public static Boolean containAlterations(Integer entrezGeneId) {
        return alterations.containsKey(entrezGeneId) ? true : false;
    }

    public static void setAlterations(Integer entrezGeneId, Set<Alteration> alts) {
        alterations.put(entrezGeneId, alts);
    }

    public static List<OncoTreeType> getMappedTumorTypes(String queryTumorType, String source) {
        return mappedTumorTypes.get(queryTumorType + "&" + source);
    }

    public static Boolean containMappedTumorTypes(String queryTumorType, String source) {
        return mappedTumorTypes.containsKey(queryTumorType + "&" + source) ? true : false;
    }

    public static void setMappedTumorTypes(String queryTumorType, String source, List<OncoTreeType> tumorTypes) {
        mappedTumorTypes.put(queryTumorType + "&" + source, tumorTypes);
    }

    public static List<OncoTreeType> getAllCancerTypes() {
        if (isEnabled()) {
            return allOncoTreeTypes.get("main");
        } else {
            return TumorTypeUtils.getOncoTreeCancerTypes(ApplicationContextSingleton.getEvidenceBo().findAllCancerTypes());
        }
    }

    public static List<OncoTreeType> getAllSubtypes() {
        if (isEnabled()) {
            return allOncoTreeTypes.get("subtype");
        } else {
            return TumorTypeUtils.getOncoTreeSubtypesByCode(ApplicationContextSingleton.getEvidenceBo().findAllSubtypes());
        }
    }

    public static Set<Gene> getAllGenes() {
        if (genes.size() == 0) {
            genes = new HashSet<>(ApplicationContextSingleton.getGeneBo().findAll());
        }
        return genes;
    }

    public static Set<Evidence> getEvidences(Gene gene) {
        if (evidences == null || evidences.size() == 0) {
            Map<Gene, Set<Evidence>> mappedEvidence =
                EvidenceUtils.separateEvidencesByGene(genes, new HashSet<>(
                    ApplicationContextSingleton.getEvidenceBo().findAll()));
            Iterator it = mappedEvidence.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
                evidences.put(pair.getKey().getEntrezGeneId(), pair.getValue());
            }
        }

        if (evidences.containsKey(gene.getEntrezGeneId())) {
            Set<Evidence> result = evidences.get(gene.getEntrezGeneId());
            return result;
        } else {
            return new HashSet<>();
        }
    }

    public static Boolean containEvidences(Gene gene) {
        return (evidences.containsKey(gene)) ? true : false;
    }

    public static void setEvidences(Gene gene, Set<Evidence> newEvidences) {
        evidences.put(gene.getEntrezGeneId(), newEvidences);
    }

    public static Set<ShortGene> getAllShortGenes() {
        return ShortGeneUtils.getShortGenesFromGenes(genes);
    }

    public static void updateGene(Integer entrezGeneId) {
        GeneObservable.getInstance().update("update", entrezGeneId.toString());
    }

    public static void resetAll() {
        GeneObservable.getInstance().update("reset", null);
    }

    public static void enableCacheUtils() {
        status = "enabled";
    }

    public static void disableCacheUtils() {
        status = "disabled";
    }

    public static String getCacheUtilsStatus() {
        return status;
    }

    public static Boolean isEnabled() {
        if (status == "enabled") {
            return true;
        } else {
            return false;
        }
    }

    public static Boolean isDisabled() {
        if (status == "disabled") {
            return true;
        } else {
            return false;
        }
    }
}