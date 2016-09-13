/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mskcc.cbio.oncokb.util;

import org.mskcc.cbio.oncokb.model.*;

import java.util.*;

/**
 * @author zhangh2
 */
public final class TreatmentUtils {
    private TreatmentUtils() {
        throw new AssertionError();
    }

    public static Set<Treatment> getTreatmentsByGene(Gene gene) {
        if (gene == null) {
            return new HashSet<>();
        }
        List<Alteration> alterations = AlterationUtils.getAllAlterations(gene);
        List<Evidence> evidences = EvidenceUtils.getEvidence(alterations,
            new ArrayList<>(MainUtils.getTreatmentEvidenceTypes()), null);
        Set<Treatment> treatments = new HashSet<>();

        for (Evidence evidence : evidences) {
            treatments.addAll(evidence.getTreatments());
        }
        return treatments;
    }

    public static Set<Treatment> getTreatmentsByAlteration(Alteration alteration) {
        if (alteration == null) {
            return new HashSet<>();
        }
        List<Evidence> evidences = EvidenceUtils.getEvidence(Collections.singletonList(alteration),
            new ArrayList<>(MainUtils.getTreatmentEvidenceTypes()), null);
        Set<Treatment> treatments = new HashSet<>();

        for (Evidence evidence : evidences) {
            treatments.addAll(evidence.getTreatments());
        }
        return treatments;
    }

    public static Set<Treatment> getTreatmentsByLevels(Set<LevelOfEvidence> levels) {
        if (levels == null || levels.size() == 0) {
            return new HashSet<>();
        }
        return getTreatmentsByGenesAndLevels(GeneUtils.getAllGenes(), levels);
    }

    public static Set<Treatment> getTreatmentsByGeneAndLevels(Gene gene, Set<LevelOfEvidence> levels) {
        if (levels == null) {
            return getTreatmentsByGene(gene);
        }
        if (gene == null) {
            getTreatmentsByLevels(levels);
        }
        return getTreatmentsByGenesAndLevels(Collections.singleton(gene), levels);
    }

    private static Set<Treatment> getTreatmentsByGenesAndLevels(Set<Gene> genes, Set<LevelOfEvidence> levels) {
        Map<Gene, Set<Evidence>> allEvidence = EvidenceUtils.getEvidenceByGenes(genes);
        Set<Treatment> treatments = new HashSet<>();

        Iterator iterator = allEvidence.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Gene, Set<Evidence>> map = (Map.Entry) iterator.next();
            for (Evidence evidence : map.getValue()) {
                if (evidence.getLevelOfEvidence() != null
                    && levels.contains(evidence.getLevelOfEvidence())) {
                    treatments.addAll(evidence.getTreatments());
                }
            }
        }

        return treatments;
    }
}
