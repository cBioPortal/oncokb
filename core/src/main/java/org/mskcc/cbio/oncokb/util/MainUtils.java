package org.mskcc.cbio.oncokb.util;

import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.oncotree.model.TumorType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by hongxinzhang on 4/5/16.
 */
public class MainUtils {
    static String DataVersion = null;
    static Date DataVersionDate = null;
    static Boolean ReadDataVersionDateProperty = false;

    public static Map<String, Object> GetRequestQueries(
        String entrezGeneId, String hugoSymbol, String alteration, String tumorType,
        String evidenceType, String consequence, String proteinStart, String proteinEnd,
        String geneStatus, String source, String levels) {

        Map<String, Object> requestQueries = new HashMap<>();

        List<Query> queries = new ArrayList<>();
        List<EvidenceType> evidenceTypes = new ArrayList<>();
        List<LevelOfEvidence> levelOfEvidences = new ArrayList<>();
        String[] genes = {};

        if (entrezGeneId != null) {
            for (String id : entrezGeneId.trim().split("\\s*,\\s*")) {
                Query requestQuery = new Query();
                requestQuery.setEntrezGeneId(Integer.parseInt(id));
                queries.add(requestQuery);
            }
        } else if (hugoSymbol != null) {
            for (String symbol : hugoSymbol.trim().split("\\s*,\\s*")) {
                Query requestQuery = new Query();
                requestQuery.setHugoSymbol(symbol.toUpperCase());
                queries.add(requestQuery);
            }
        }

        if (evidenceType != null) {
            for (String type : evidenceType.trim().split("\\s*,\\s*")) {
                EvidenceType et = EvidenceType.valueOf(type);
                evidenceTypes.add(et);
            }
        } else {
            evidenceTypes = getAllEvidenceTypes();
        }

        if (alteration != null) {
            String[] alts = alteration.trim().split("\\s*,\\s*");
            if (queries.size() == alts.length) {
                String[] consequences = consequence == null ? new String[0] : consequence.trim().split("\\s*,\\s*");
                String[] proteinStarts = proteinStart == null ? new String[0] : proteinStart.trim().split("\\s*,\\s*");
                String[] proteinEnds = proteinEnd == null ? new String[0] : proteinEnd.trim().split("\\s*,\\s*");

                for (int i = 0; i < queries.size(); i++) {
                    queries.get(i).setAlteration(alts[i]);
                    queries.get(i).setConsequence(consequences.length == alts.length ? consequences[i] : null);
                    queries.get(i).setProteinStart(proteinStarts.length == alts.length ? Integer.valueOf(proteinStarts[i]) : null);
                    queries.get(i).setProteinEnd(proteinEnds.length == alts.length ? Integer.valueOf(proteinEnds[i]) : null);
                }
            } else {
                return null;
            }
        }

        String[] tumorTypes = tumorType == null ? new String[0] : tumorType.trim().split("\\s*,\\s*");
        if (tumorTypes.length > 0) {
            if (tumorTypes.length == 1) {
                for (int i = 0; i < queries.size(); i++) {
                    queries.get(i).setTumorType(tumorTypes[0]);
                }
            } else if (queries.size() == tumorTypes.length) {
                for (int i = 0; i < queries.size(); i++) {
                    queries.get(i).setTumorType(tumorTypes[i]);
                }
            }
        }

        if (levels != null) {
            String[] levelStrs = levels.trim().split("\\s*,\\s*");
            for (int i = 0; i < levelStrs.length; i++) {
                LevelOfEvidence level = LevelOfEvidence.getByName(levelStrs[i]);
                if (level != null) {
                    levelOfEvidences.add(level);
                }
            }
        }

        requestQueries.put("queries", queries);
        requestQueries.put("evidenceTypes", evidenceTypes);
        requestQueries.put("source", source == null ? "quest" : source);
        requestQueries.put("geneStatus", geneStatus == null ? "complete" : geneStatus);
        requestQueries.put("levels", levelOfEvidences);
        return requestQueries;
    }

    public static Long printTimeDiff(Long oldDate, Long newDate, String message) {
        System.out.println(message + ": " + (newDate - oldDate));
        return newDate;
    }

    public static MutationEffect findHighestMutationEffect(Set<MutationEffect> mutationEffect) {
        MutationEffect[] effects = {
            MutationEffect.GAIN_OF_FUNCTION,
            MutationEffect.LIKELY_GAIN_OF_FUNCTION,
            MutationEffect.INCONCLUSIVE,
            MutationEffect.LIKELY_NEUTRAL,
            MutationEffect.NEUTRAL,
            MutationEffect.LIKELY_SWITCH_OF_FUNCTION,
            MutationEffect.SWITCH_OF_FUNCTION,
            MutationEffect.LIKELY_LOSS_OF_FUNCTION,
            MutationEffect.LOSS_OF_FUNCTION
        };
        List<MutationEffect> list = Arrays.asList(effects);
        Integer index = 100;
        for (MutationEffect effect : mutationEffect) {
            if (list.indexOf(effect) < index) {
                index = list.indexOf(effect);
            }
        }
        return index == 100 ? null : list.get(index);
    }

    public static Oncogenicity findHighestOncogenicity(Set<Oncogenicity> oncogenicitySet) {
        Oncogenicity[] effects = {
            Oncogenicity.INCONCLUSIVE,
            Oncogenicity.LIKELY_NEUTRAL,
            Oncogenicity.LIKELY,
            Oncogenicity.YES
        };
        List<Oncogenicity> list = Arrays.asList(effects);
        Integer index = -1;

        for (Oncogenicity datum : oncogenicitySet) {
            if (datum != null) {
                Integer oncogenicIndex = list.indexOf(datum);
                if (index < oncogenicIndex) {
                    index = oncogenicIndex;
                }
            }
        }

        return index == -1 ? null : list.get(index);
    }

    public static Oncogenicity idealOncogenicityByMutationEffect(MutationEffect mutationEffect) {
        if (mutationEffect == null) {
            return null;
        }

        Oncogenicity oncogenic;

        switch (mutationEffect) {
            case GAIN_OF_FUNCTION:
                oncogenic = Oncogenicity.YES;
                break;
            case LIKELY_GAIN_OF_FUNCTION:
                oncogenic = Oncogenicity.LIKELY;
                break;
            case LOSS_OF_FUNCTION:
                oncogenic = Oncogenicity.YES;
                break;
            case LIKELY_LOSS_OF_FUNCTION:
                oncogenic = Oncogenicity.LIKELY;
                break;
            case SWITCH_OF_FUNCTION:
                oncogenic = Oncogenicity.YES;
                break;
            case LIKELY_SWITCH_OF_FUNCTION:
                oncogenic = Oncogenicity.LIKELY;
                break;
            case NEUTRAL:
                oncogenic = Oncogenicity.LIKELY_NEUTRAL;
                break;
            case LIKELY_NEUTRAL:
                oncogenic = Oncogenicity.LIKELY_NEUTRAL;
                break;
            case INCONCLUSIVE:
                oncogenic = Oncogenicity.INCONCLUSIVE;
                break;
            default:
                oncogenic = null;
        }
        return oncogenic;
    }

    public static Set<EvidenceType> getTreatmentEvidenceTypes() {
        Set<EvidenceType> types = new HashSet<>();
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);

        return types;
    }

    public static List<EvidenceType> getAllEvidenceTypes() {
        return Arrays.asList(EvidenceType.values());
    }

    public static Set<EvidenceType> getSensitiveTreatmentEvidenceTypes() {
        Set<EvidenceType> types = new HashSet<>();
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
        return types;
    }

    public static Oncogenicity findHighestOncogenicByEvidences(Set<Evidence> evidences) {
        Set<Oncogenicity> oncogenicitySet = new HashSet<>();

        if (evidences != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getKnownEffect() != null) {
                    oncogenicitySet.add(Oncogenicity.getByEffect(evidence.getKnownEffect()));
                }
            }
        }

        return findHighestOncogenicity(oncogenicitySet);
    }

    public static Oncogenicity setToAlleleOncogenicity(Oncogenicity oncogenicity) {
        if (oncogenicity == null) {
            return null;
        }
        if (oncogenicity.equals(Oncogenicity.YES) || oncogenicity.equals(Oncogenicity.LIKELY)) {
            return Oncogenicity.LIKELY;
        } else {
            return null;
        }
    }

    public static String getAlleleConflictsMutationEffect(Set<String> mutationEffects) {
        Set<String> clean = new HashSet<>();

        for (String mutationEffect : mutationEffects) {
            if (mutationEffect != null) {
                mutationEffect = mutationEffect.replaceAll("(?i)likely", "");
                mutationEffect = mutationEffect.replaceAll("\\s", "");
                clean.add(mutationEffect);
            }
        }

        if (clean.size() > 1) {
            return "Unknown";
        } else if (clean.size() == 1) {
            return "Likely " + clean.iterator().next();
        } else {
            return "";
        }
    }


    public static Long getCurrentTimestamp() {
        return new Date().getTime();
    }

    public static Long getTimestampDiff(Long old) {
        return new Date().getTime() - old;
    }

    public static String getDataVersion() {
        if (DataVersion == null) {
            DataVersion = getProperty("data.version");
        }
        return DataVersion;
    }

    public static Date getLatestDate(Set<Date> dates) {
        if (dates != null) {
            dates.removeAll(Collections.singleton(null));
            if (dates.size() > 0)
                return Collections.max(dates);
        }
        return null;
    }

    public static Date getLatestDateFromEvidences(Set<Evidence> evidences) {
        if (evidences != null) {
            Set<Date> dates = new HashSet<>();
            for (Evidence evidence : evidences) {
                if (evidence.getLastEdit() != null)
                    dates.add(evidence.getLastEdit());
            }
            if (dates.size() > 0)
                return Collections.max(dates);
        }
        return null;
    }

    public static Date getDataVersionDate() {
        if (ReadDataVersionDateProperty) {
            return DataVersionDate;
        } else {
            try {
                String dateStr = getProperty("data.version_date");
                DateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
                DataVersionDate = format.parse(dateStr);
            } finally {
                ReadDataVersionDateProperty = true;
                return DataVersionDate;
            }
        }
    }

    private static String getProperty(String propertyName) {
        String version = "";
        if (propertyName != null) {
            try {
                String tmpData = PropertiesUtils.getProperties(propertyName);
                if (tmpData != null) {
                    version = tmpData;
                }
            } catch (Exception e) {
            }
        }
        return version;
    }

    public static String listToString(List<String> list, String separator) {
        if (list.isEmpty()) {
            return "";
        }

        int n = list.size();
        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0));
        if (n == 1) {
            return sb.toString();
        }

        for (int i = 1; i < n; i++) {
            sb.append(separator).append(list.get(i));
        }

        return sb.toString();
    }

    public static List<Integer> stringToIntegers(String ids) {
        if (ids == null) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (String id : ids.trim().split("\\s*,\\s*")) {
            Integer match = Integer.parseInt(id);

            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    public static List<EvidenceType> stringToEvidenceTypes(String string, String separator) {
        List<EvidenceType> evidenceTypes = new ArrayList<>();
        if (string != null) {
            if (separator == null) {
                separator = ",";
            }
            for (String type : string.trim().split("\\s*" + separator + "\\s*")) {
                EvidenceType et = EvidenceType.valueOf(type);
                evidenceTypes.add(et);
            }
        } else {
            return null;
        }
        return evidenceTypes;
    }

    public static Set<BiologicalVariant> getBiologicalVariants(Gene gene) {
        Set<BiologicalVariant> variants = new HashSet<>();
        if (gene != null) {
            Long oldTime = new Date().getTime();
            List<Alteration> alterations;

            alterations = AlterationUtils.excludeVUS(gene, new ArrayList<>(AlterationUtils.getAllAlterations(gene)));
            alterations = AlterationUtils.excludeInferredAlterations(alterations);

//                oldTime = MainUtils.printTimeDiff(oldTime, new Date().getTime(), "Get all alterations for " + hugoSymbol);

            Set<EvidenceType> evidenceTypes = new HashSet<EvidenceType>() {{
                add(EvidenceType.MUTATION_EFFECT);
                add(EvidenceType.ONCOGENIC);
            }};
            Map<Alteration, Map<EvidenceType, Set<Evidence>>> evidences = new HashMap<>();

            for (Alteration alteration : alterations) {
                Map<EvidenceType, Set<Evidence>> map = new HashMap<>();
                map.put(EvidenceType.ONCOGENIC, new HashSet<Evidence>());
                map.put(EvidenceType.MUTATION_EFFECT, new HashSet<Evidence>());
                evidences.put(alteration, map);
            }
//                oldTime = MainUtils.printTimeDiff(oldTime, new Date().getTime(), "Initialize evidences.");

            Map<Gene, Set<Evidence>> geneEvidences =
                EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(Collections.singleton(gene), evidenceTypes);
//                oldTime = MainUtils.printTimeDiff(oldTime, new Date().getTime(), "Get all gene evidences.");

            for (Evidence evidence : geneEvidences.get(gene)) {
                for (Alteration alteration : evidence.getAlterations()) {
                    if (evidences.containsKey(alteration)) {
                        evidences.get(alteration).get(evidence.getEvidenceType()).add(evidence);
                    }
                }
            }
//                oldTime = MainUtils.printTimeDiff(oldTime, new Date().getTime(), "Seperate evidences.");

            for (Map.Entry<Alteration, Map<EvidenceType, Set<Evidence>>> entry : evidences.entrySet()) {
                Alteration alteration = entry.getKey();
                Map<EvidenceType, Set<Evidence>> map = entry.getValue();

                BiologicalVariant variant = new BiologicalVariant();
                variant.setVariant(alteration);
                Oncogenicity oncogenicity = EvidenceUtils.getOncogenicityFromEvidence(map.get(EvidenceType.ONCOGENIC));
                MutationEffect mutationEffect = EvidenceUtils.getMutationEffectFromEvidence(map.get(EvidenceType.MUTATION_EFFECT));
                if ((oncogenicity == null || oncogenicity.equals(Oncogenicity.INCONCLUSIVE)) && mutationEffect != null) {
                    oncogenicity = idealOncogenicityByMutationEffect(mutationEffect);
                }
                variant.setOncogenic(oncogenicity == null ? null : oncogenicity.getOncogenic());
                variant.setMutationEffect(mutationEffect == null ? null : mutationEffect.getMutationEffect());
                variant.setOncogenicPmids(EvidenceUtils.getPmids(map.get(EvidenceType.ONCOGENIC)));
                variant.setMutationEffectPmids(EvidenceUtils.getPmids(map.get(EvidenceType.MUTATION_EFFECT)));
                variant.setOncogenicAbstracts(EvidenceUtils.getAbstracts(map.get(EvidenceType.ONCOGENIC)));
                variant.setMutationEffectAbstracts(EvidenceUtils.getAbstracts(map.get(EvidenceType.MUTATION_EFFECT)));
                variants.add(variant);
            }
//                oldTime = MainUtils.printTimeDiff(oldTime, new Date().getTime(), "Created biological annotations.");
        }
        return variants;
    }

    public static Set<ClinicalVariant> getClinicalVariants(Gene gene) {
        Set<ClinicalVariant> variants = new HashSet<>();
        if (gene != null) {
            List<Alteration> alterations;
            alterations = AlterationUtils.excludeVUS(gene, new ArrayList<>(AlterationUtils.getAllAlterations(gene)));
            Set<EvidenceType> evidenceTypes = new HashSet<EvidenceType>() {{
                add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
                add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE);
                add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
            }};
            Map<Alteration, Map<TumorType, Map<LevelOfEvidence, Set<Evidence>>>> evidences = new HashMap<>();
            Set<LevelOfEvidence> publicLevels = LevelUtils.getPublicLevels();

            for (Alteration alteration : alterations) {
                evidences.put(alteration, new HashMap<TumorType, Map<LevelOfEvidence, Set<Evidence>>>());
            }

            Map<Gene, Set<Evidence>> geneEvidences =
                EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(Collections.singleton(gene), evidenceTypes);

            for (Evidence evidence : geneEvidences.get(gene)) {
                TumorType oncoTreeType = evidence.getOncoTreeType();

                if (oncoTreeType != null) {
                    for (Alteration alteration : evidence.getAlterations()) {
                        if (evidences.containsKey(alteration)) {
                            if (!evidences.get(alteration).containsKey(oncoTreeType)) {
                                evidences.get(alteration).put(oncoTreeType, new HashMap<LevelOfEvidence, Set<Evidence>>());
                            }
                            if (publicLevels.contains(evidence.getLevelOfEvidence())) {
                                LevelOfEvidence levelOfEvidence = evidence.getLevelOfEvidence();
                                if (!evidences.get(alteration).get(oncoTreeType).containsKey(levelOfEvidence)) {
                                    evidences.get(alteration).get(oncoTreeType).put(levelOfEvidence, new HashSet<Evidence>());
                                }
                                evidences.get(alteration).get(oncoTreeType).get(levelOfEvidence).add(evidence);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Alteration, Map<TumorType, Map<LevelOfEvidence, Set<Evidence>>>> entry : evidences.entrySet()) {
                Alteration alteration = entry.getKey();
                Map<TumorType, Map<LevelOfEvidence, Set<Evidence>>> map = entry.getValue();

                for (Map.Entry<TumorType, Map<LevelOfEvidence, Set<Evidence>>> _entry : map.entrySet()) {
                    TumorType oncoTreeType = _entry.getKey();

                    for (Map.Entry<LevelOfEvidence, Set<Evidence>> __entry : _entry.getValue().entrySet()) {
                        ClinicalVariant variant = new ClinicalVariant();
                        variant.setOncoTreeType(oncoTreeType);
                        variant.setVariant(alteration);
                        variant.setLevel(__entry.getKey().getLevel());
                        variant.setDrug(EvidenceUtils.getDrugs(__entry.getValue()));
                        variant.setDrugPmids(EvidenceUtils.getPmids(__entry.getValue()));
                        variant.setDrugAbstracts(EvidenceUtils.getAbstracts(__entry.getValue()));
                        variants.add(variant);
                    }
                }
            }
        }
        return variants;
    }

    public static boolean containsCaseInsensitive(String s, List<String> l) {
        for (String string : l) {
            if (string.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
