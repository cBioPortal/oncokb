/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mskcc.cbio.oncokb.util;

import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jgao
 */
public final class AlterationUtils {
    private static List<String> oncogenicList = Arrays.asList(new String[]{"", "-1", "0", "2", "1"});
    private static AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
    private final static String[] generalAlts = {"activating mutations", "activating mutation", "inactivating mutations", "inactivating mutation", "all mutations", "all mutation", "wildtype", "wildtypes"};
    private final static Set<String> generalAlterations = new HashSet<>(Arrays.asList(generalAlts));

    private AlterationUtils() {
        throw new AssertionError();
    }

    public static void annotateAlteration(Alteration alteration, String proteinChange) {
        String consequence = "N/A";
        String ref = null;
        String var = null;
        Integer start = -1;
        Integer end = 100000;

        if (proteinChange.startsWith("p.")) {
            proteinChange = proteinChange.substring(2);
        }

        if (proteinChange.indexOf("[") != -1) {
            proteinChange = proteinChange.substring(0, proteinChange.indexOf("["));
        }

        proteinChange = proteinChange.trim();

        Pattern p = Pattern.compile("([A-Z\\*])([0-9]+)([A-Z\\*]?)");
        Matcher m = p.matcher(proteinChange);
        if (m.matches()) {
            ref = m.group(1);
            start = Integer.valueOf(m.group(2));
            end = start;
            var = m.group(3);

            if (ref.equals(var)) {
                consequence = "synonymous_variant";
            } else if (ref.equals("*")) {
                consequence = "stop_lost";
            } else if (var.equals("*")) {
                consequence = "stop_gained";
            } else if (start == 1) {
                consequence = "initiator_codon_variant";
            } else {
                consequence = "missense_variant";
            }
        } else {
            p = Pattern.compile("[A-Z]?([0-9]+)_[A-Z]?([0-9]+)(.+)");
            m = p.matcher(proteinChange);
            if (m.matches()) {
                start = Integer.valueOf(m.group(1));
                end = Integer.valueOf(m.group(2));
                String v = m.group(3);
                switch (v) {
                    case "mis":
                        consequence = "missense_variant";
                        break;
                    case "ins":
                        consequence = "inframe_insertion";
                        break;
                    case "del":
                        consequence = "inframe_deletion";
                        break;
                    case "fs":
                        consequence = "frameshift_variant";
                        break;
                    case "trunc":
                        consequence = "feature_truncation";
                        break;
                    case "mut":
                        consequence = "any";
                }
            } else {
                p = Pattern.compile("([A-Z\\*])([0-9]+)[A-Z]?fs.*");
                m = p.matcher(proteinChange);
                if (m.matches()) {
                    ref = m.group(1);
                    start = Integer.valueOf(m.group(2));
                    end = start;

                    consequence = "frameshift_variant";
                } else {
                    p = Pattern.compile("([A-Z]+)?([0-9]+)((ins)|(del))");
                    m = p.matcher(proteinChange);
                    if (m.matches()) {
                        ref = m.group(1);
                        start = Integer.valueOf(m.group(2));
                        end = start;
                        String v = m.group(3);
                        switch (v) {
                            case "ins":
                                consequence = "inframe_insertion";
                                break;
                            case "del":
                                consequence = "inframe_deletion";
                                break;
                        }
                    } else {
                        p = Pattern.compile("_splice");
                        m = p.matcher(proteinChange);
                        if (m.find()) {
                            consequence = "splice_region_variant";
                        }
                    }
                }
            }
        }

        // truncating
        if (proteinChange.toLowerCase().matches("truncating mutations?")) {
            consequence = "feature_truncation";
        }

        VariantConsequence variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(consequence);

        if (alteration.getRefResidues() == null && ref != null && !ref.isEmpty()) {
            alteration.setRefResidues(ref);
        }

        if (alteration.getVariantResidues() == null && var != null && !var.isEmpty()) {
            alteration.setVariantResidues(var);
        }

        if (alteration.getProteinStart() == null && start != null) {
            alteration.setProteinStart(start);
        }

        if (alteration.getProteinEnd() == null && end != null) {
            alteration.setProteinEnd(end);
        }

        if (alteration.getConsequence() == null && variantConsequence != null) {
            alteration.setConsequence(variantConsequence);
        }
    }

    public static String getVariantName(String gene, String alteration) {
        //Gene + mutation name
        String variantName = "";

        if (gene != null && alteration != null && alteration.toLowerCase().contains(gene.toLowerCase())) {
            variantName = alteration;
        } else {
            variantName = (gene != null ? (gene + " ") : "") + (alteration != null ? alteration : "");
        }

        if (alteration != null) {
            if (alteration.toLowerCase().contains("fusion")) {
//            variantName = variantName.concat(" event");
            } else if (alteration.toLowerCase().contains("deletion") || alteration.toLowerCase().contains("amplification")) {
                //Keep the variant name
            } else {
                variantName = variantName.concat(" mutation");
            }
        }
        return variantName;
    }

    public static String trimAlterationName(String alteration) {
        if (alteration != null) {
            if (alteration.startsWith("p.")) {
                alteration = alteration.substring(2);
            }
        }
        return alteration;
    }

    public static Alteration getAlteration(String hugoSymbol, String alteration, String alterationType,
                                           String consequence, Integer proteinStart, Integer proteinEnd) {
        Alteration alt = new Alteration();

        if (alteration != null) {
            alteration = AlterationUtils.trimAlterationName(alteration);
            alt.setAlteration(alteration);
        }

        Gene gene = null;
        if (hugoSymbol != null) {
            gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        }
        alt.setGene(gene);

        AlterationType type = AlterationType.MUTATION;
        if (alterationType != null) {
            AlterationType t = AlterationType.valueOf(alterationType.toUpperCase());
            if (t != null) {
                type = t;
            }
        }
        alt.setAlterationType(type);

        VariantConsequence variantConsequence = null;
        if (consequence != null) {
            variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(consequence);
        }
        alt.setConsequence(variantConsequence);

        if (proteinEnd == null) {
            proteinEnd = proteinStart;
        }
        alt.setProteinStart(proteinStart);
        alt.setProteinEnd(proteinEnd);

        AlterationUtils.annotateAlteration(alt, alt.getAlteration());
        return alt;
    }

    public static String getOncogenic(List<Alteration> alterations) {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        List<Evidence> evidences = evidenceBo.findEvidencesByAlteration(alterations, Collections.singleton(EvidenceType.ONCOGENIC));
        return findHighestOncogenic(evidences);
    }

    private static String findHighestOncogenic(List<Evidence> evidences) {
        String oncogenic = "";
        for (Evidence evidence : evidences) {
            oncogenic = oncogenicList.indexOf(evidence.getKnownEffect()) < oncogenicList.indexOf(oncogenic) ? oncogenic : evidence.getKnownEffect();
        }
        return oncogenic;
    }

    public static List<Alteration> getAllAlterations(Gene gene) {
        if (CacheUtils.isEnabled()) {
            if (!CacheUtils.containAlterations(gene.getEntrezGeneId())) {
                CacheUtils.setAlterations(gene.getEntrezGeneId(),
                    new HashSet<>(alterationBo.findAlterationsByGene(Collections.singleton(gene))));
            }
            return new ArrayList<>(CacheUtils.getAlterations(gene.getEntrezGeneId()));
        } else {
            return alterationBo.findAlterationsByGene(Collections.singleton(gene));
        }
    }

    public static Set<Alteration> findVUSFromEvidences(Set<Evidence> evidences) {
        Set<Alteration> alterations = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getEvidenceType().equals(EvidenceType.VUS)) {
                alterations.addAll(evidence.getAlterations());
            }
        }

        return alterations;
    }

    public static Set<Alteration> excludeVUS(Set<Alteration> alterations) {
        Set<Alteration> result = new HashSet<>();
        Set<Alteration> VUS = new HashSet<>();
        Set<Gene> allGenes = CacheUtils.getAllGenes();
        for (Gene gene : allGenes) {
            VUS.addAll(CacheUtils.getVUS(gene.getEntrezGeneId()));
        }

        for (Alteration alteration : alterations) {
            if (!VUS.contains(alteration)) {
                result.add(alteration);
            }
        }

        return result;
    }

    public static Set<Alteration> excludeVUS(Gene gene, Set<Alteration> alterations) {
        Set<Alteration> result = new HashSet<>();
        Set<Alteration> VUS = CacheUtils.getVUS(gene.getEntrezGeneId());

        for (Alteration alteration : alterations) {
            if (!VUS.contains(alteration)) {
                result.add(alteration);
            }
        }

        return result;
    }

    public static Set<Alteration> excludeGeneralAlterations(Set<Alteration> alterations) {
        Set<Alteration> result = new HashSet<>();
        for (Alteration alteration : alterations) {
            String name = alteration.getAlteration().toLowerCase();
            if (name != null && !generalAlterations.contains(name)) {
                result.add(alteration);
            }
        }
        return result;
    }

    public static List<Alteration> getAlterations(Gene gene, String alteration, String consequence, Integer proteinStart, Integer proteinEnd, List<Alteration> fullAlterations) {
        List<Alteration> alterations = new ArrayList<>();
        VariantConsequence variantConsequence = null;

        if (gene != null && alteration != null) {
            if (consequence != null) {
                //Consequence format  a, b+c, d ... each variant pair (gene + alteration) could have one or multiple consequences. Multiple consequences are separated by '+'
                for (String con : consequence.split("\\+")) {
                    Alteration alt = new Alteration();
                    alt.setAlteration(alteration);
                    variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(con);
                    alt.setConsequence(variantConsequence);
                    alt.setAlterationType(AlterationType.MUTATION);
                    alt.setGene(gene);
                    alt.setProteinStart(proteinStart);
                    alt.setProteinEnd(proteinEnd);

                    AlterationUtils.annotateAlteration(alt, alt.getAlteration());

                    List<Alteration> alts = alterationBo.findRelevantAlterations(alt, fullAlterations);
                    if (!alts.isEmpty()) {
                        alterations.addAll(alts);
                    }
                }
            } else {
                Alteration alt = new Alteration();
                alt.setAlteration(alteration);
                alt.setAlterationType(AlterationType.MUTATION);
                alt.setGene(gene);
                alt.setProteinStart(proteinStart);
                alt.setProteinEnd(proteinEnd);

                AlterationUtils.annotateAlteration(alt, alt.getAlteration());

                List<Alteration> alts = alterationBo.findRelevantAlterations(alt, fullAlterations);
                if (!alts.isEmpty()) {
                    alterations.addAll(alts);
                }
            }
        }

        return alterations;
    }

    public static List<Alteration> getRelevantAlterations(Alteration alteration) {
        return alterationBo.findRelevantAlterations(alteration, null);
    }

    public static Set<Alteration> getAlleleAlterations(Alteration alteration) {
        List<Alteration> alterations =
            alterationBo.findMutationsByConsequenceAndPosition(
                alteration.getGene(), alteration.getConsequence(), alteration.getProteinStart(),
                alteration.getProteinEnd(),
                new ArrayList<>(CacheUtils.getAlterations(alteration.getGene().getEntrezGeneId())));
        // Remove alteration itself
        alterations.remove(alteration);
        return filterAllelesBasedOnLocation(new HashSet<>(alterations), alteration.getProteinStart());
    }

    public static List<Alteration> getRelevantAlterations(
        Gene gene, String alteration, String consequence,
        Integer proteinStart, Integer proteinEnd) {
        if (gene == null) {
            return new ArrayList<>();
        }
        String id = gene.getHugoSymbol() + alteration + (consequence == null ? "" : consequence);

        if (CacheUtils.isEnabled()) {
            if (!CacheUtils.containRelevantAlterations(gene.getEntrezGeneId(), id)) {
                CacheUtils.setRelevantAlterations(
                    gene.getEntrezGeneId(), id,
                    getAlterations(
                        gene, alteration, consequence,
                        proteinStart, proteinEnd,
                        getAllAlterations(gene)));
            }

            return CacheUtils.getRelevantAlterations(gene.getEntrezGeneId(), id);
        } else {
            return getAlterations(
                gene, alteration, consequence,
                proteinStart, proteinEnd,
                getAllAlterations(gene));
        }
    }

    public static Boolean hasAlleleAlterations(Alteration alteration) {
        Set<Alteration> alleles = new HashSet<>(AlterationUtils.getAlleleAlterations(alteration));
        if (alleles == null || alleles.size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    public static Set<Alteration> filterAllelesBasedOnLocation(Set<Alteration> alterations, Integer location) {
        Set<Alteration> result = new HashSet<>();

        for (Alteration alteration : alterations) {
            if (alteration.getProteinStart() != null
                && alteration.getProteinEnd() != null
                && alteration.getProteinStart().equals(alteration.getProteinEnd())
                && alteration.getProteinStart().equals(location)) {

                result.add(alteration);
            }
        }

        return result;
    }
}
