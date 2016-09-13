package org.mskcc.cbio.oncokb.util;

import org.mskcc.cbio.oncokb.model.*;

import java.util.*;
import java.util.logging.Level;

/**
 * Created by hongxinzhang on 4/5/16.
 */
public class NumberUtils {
    public static Set<GeneNumber> getGeneNumberList(Set<Gene> genes) {
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getEvidenceByGenes(genes);
        Set<GeneNumber> geneNumbers = new HashSet<>();

        Iterator it = evidences.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
            GeneNumber geneNumber = new GeneNumber();

            geneNumber.setShortGene(ShortGeneUtils.getShortGeneFromGene(pair.getKey()));

            LevelOfEvidence highestLevel = LevelUtils.getHighestLevelFromEvidence(pair.getValue());
            geneNumber.setHighestLevel(highestLevel != null ? highestLevel.name() : null);

            Set<Alteration> alterations = AlterationUtils.getAllAlterations(pair.getKey());
            Set<Alteration> excludeVUS = AlterationUtils.excludeVUS(pair.getKey(), new HashSet<Alteration>(alterations));
            geneNumber.setAlteration(excludeVUS.size());
            geneNumbers.add(geneNumber);
        }
        return geneNumbers;
    }

    public static Set<GeneNumber> getGeneNumberListWithLevels(Set<Gene> genes, Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getGeneNumberList(genes);
        }
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getEvidenceByGenes(genes);
        Set<GeneNumber> geneNumbers = new HashSet<>();

        Iterator it = evidences.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Gene, Set<Evidence>> pair = (Map.Entry) it.next();
            GeneNumber geneNumber = new GeneNumber();

            geneNumber.setShortGene(ShortGeneUtils.getShortGeneFromGene(pair.getKey()));

            LevelOfEvidence highestLevel = LevelUtils.getHighestLevelFromEvidenceByLevels(pair.getValue(), levels);
            geneNumber.setHighestLevel(highestLevel != null ? highestLevel.name() : null);

            Set<Alteration> alterations = AlterationUtils.getAllAlterations(pair.getKey());
            Set<Alteration> excludeVUS = AlterationUtils.excludeVUS(pair.getKey(), new HashSet<Alteration>(alterations));
            geneNumber.setAlteration(excludeVUS.size());
            geneNumbers.add(geneNumber);
        }
        return geneNumbers;
    }

    public static Set<LevelNumber> getLevelNumberList() {
        Set<LevelNumber> levelList = new HashSet<>();
        Map<LevelOfEvidence, Set<Gene>> genes = getLevelBasedGenesList();
        return getLevelNumbers(genes);
    }

    public static Set<LevelNumber> getLevelNumberListByLevels(Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getLevelNumberList();
        }
        Map<LevelOfEvidence, Set<Gene>> genes = getLevelBasedGenesListByLevels(levels);
        return getLevelNumbers(genes);
    }

    public static Set<LevelNumber> getLevelNumbers(Map<LevelOfEvidence, Set<Gene>> genes) {
        Set<LevelNumber> levelList = new HashSet<>();

        for (Map.Entry<LevelOfEvidence, Set<Gene>> entry : genes.entrySet()) {
            LevelNumber levelNumber = new LevelNumber();
            levelNumber.setLevel(entry.getKey());
            levelNumber.setGenes(entry.getValue());
            levelList.add(levelNumber);
        }

        return levelList;
    }

    public static Map<LevelOfEvidence, Set<Gene>> getLevelBasedGenesList() {
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getAllGeneBasedEvidences();
        Map<LevelOfEvidence, Set<Gene>> genes = new HashMap<>();
        for (Map.Entry<Gene, Set<Evidence>> entry : evidences.entrySet()) {
            Set<LevelOfEvidence> levelsOfEvidence = LevelUtils.getLevelsFromEvidence(entry.getValue());

            for(LevelOfEvidence levelOfEvidence : levelsOfEvidence) {
                if (!genes.containsKey(levelOfEvidence)) {
                    genes.put(levelOfEvidence, new HashSet<Gene>());
                }
                genes.get(levelOfEvidence).add(entry.getKey());
            }
        }
        return genes;
    }

    public static Map<LevelOfEvidence, Set<Gene>> getLevelBasedGenesListByLevels(Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getLevelBasedGenesList();
        }
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getAllGeneBasedEvidences();
        Map<LevelOfEvidence, Set<Gene>> genes = new HashMap<>();
        for (Map.Entry<Gene, Set<Evidence>> entry : evidences.entrySet()) {
            Set<LevelOfEvidence> levelsOfEvidence = LevelUtils.getLevelsFromEvidenceByLevels(entry.getValue(), levels);

            for(LevelOfEvidence levelOfEvidence : levelsOfEvidence) {
                if (!genes.containsKey(levelOfEvidence)) {
                    genes.put(levelOfEvidence, new HashSet<Gene>());
                }
                genes.get(levelOfEvidence).add(entry.getKey());
            }
        }
        return genes;
    }

    public static Set<GeneNumber> getAllGeneNumberList() {
        return getGeneNumberList(GeneUtils.getAllGenes());
    }

    public static Set<GeneNumber> getAllGeneNumberListByLevels(Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getAllGeneNumberList();
        }
        return getGeneNumberListWithLevels(GeneUtils.getAllGenes(), levels);
    }

    public static Integer getDrugsCount() {
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getAllGeneBasedEvidences();

        Map<Drug, Boolean> drugs = new HashMap<>();
        Set<LevelNumber> levelList = new HashSet<>();

        for (Map.Entry<Gene, Set<Evidence>> entry : evidences.entrySet()) {
            for (Evidence evidence : entry.getValue()) {
                for (Treatment treatment : evidence.getTreatments()) {
                    for (Drug drug : treatment.getDrugs()) {
                        if (!drugs.containsKey(drug)) {
                            drugs.put(drug, true);
                        }
                    }
                }
            }
        }

        return drugs.size();
    }

    public static Integer getDrugsCountByLevels(Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getDrugsCount();
        }
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getAllGeneBasedEvidences();
        Map<Drug, Boolean> drugs = new HashMap<>();
        Set<LevelNumber> levelList = new HashSet<>();

        for (Map.Entry<Gene, Set<Evidence>> entry : evidences.entrySet()) {
            for (Evidence evidence : entry.getValue()) {
                if (evidence.getLevelOfEvidence() != null && levels.contains(evidence.getLevelOfEvidence())) {
                    for (Treatment treatment : evidence.getTreatments()) {
                        for (Drug drug : treatment.getDrugs()) {
                            if (!drugs.containsKey(drug)) {
                                drugs.put(drug, true);
                            }
                        }
                    }
                }
            }
        }
        return drugs.size();
    }
}
