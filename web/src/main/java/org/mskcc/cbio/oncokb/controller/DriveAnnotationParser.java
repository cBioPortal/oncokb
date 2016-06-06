/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.controller;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mortbay.util.ajax.JSON;
import org.mskcc.cbio.oncokb.bo.*;
import org.mskcc.cbio.oncokb.importer.ClinicalTrialsImporter;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * @author jgao
 */
@Controller
public class DriveAnnotationParser {
    private static final String DO_NOT_IMPORT = "DO NOT IMPORT";

    @RequestMapping(value = "/api/driveAnnotation", method = POST)
    public
    @ResponseBody
    void getEvidence(
            @RequestParam(value = "gene", required = true) String gene,
            @RequestParam(value = "vus", required = false) String vus
    ) throws IOException {

        if (gene == null) {
            System.out.println("#No gene info available.");
        } else {
            JSONObject jsonObj = new JSONObject(gene);
            JSONArray jsonArray = null;
            if (vus != null) {
                jsonArray = new JSONArray(vus);
            }
            parseGene(jsonObj, jsonArray);
        }
    }

    private static void parseVUS(Gene gene, JSONArray vus) {
        System.out.println("##    Variants of unknown significance");
        if (gene != null && vus != null) {
            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            AlterationType type = AlterationType.MUTATION; //TODO: cna and fusion

            for (int i = 0; i < vus.length(); i++) {
                JSONObject variant = vus.getJSONObject(i);
                String mutationStr = variant.getString("name");
                String lastEdit = variant.getString("lastEdit");

                Map<String, String> mutations = parseMutationString(mutationStr);
                Set<Alteration> alterations = new HashSet<Alteration>();

                for (Map.Entry<String, String> mutation : mutations.entrySet()) {
                    String proteinChange = mutation.getKey();
                    String displayName = mutation.getValue();
                    Alteration alteration = alterationBo.findAlteration(gene, type, proteinChange);
                    if (alteration == null) {
                        alteration = new Alteration();
                        alteration.setGene(gene);
                        alteration.setAlterationType(type);
                        alteration.setAlteration(proteinChange);
                        alteration.setName(displayName);
                        AlterationUtils.annotateAlteration(alteration, proteinChange);
                        alterationBo.save(alteration);
                    }
                    alterations.add(alteration);
                }

                Evidence evidence = new Evidence();
                evidence.setEvidenceType(EvidenceType.VUS);
                evidence.setGene(gene);
                evidence.setAlterations(alterations);
                if (lastEdit != null) {
                    Date date = new Date(Long.valueOf(lastEdit).longValue());
                    evidence.setLastEdit(date);
                }
                evidenceBo.save(evidence);
            }
        } else {
            if (gene == null) {
                System.out.println("###      No gene available.");
            }
            if (vus == null) {
                System.out.println("###      No VUS available.");
            }
        }
    }

    private static void parseGene(JSONObject geneInfo, JSONArray vus) throws IOException {
        GeneBo geneBo = ApplicationContextSingleton.getGeneBo();
        if (geneInfo.has("name") && !geneInfo.getString("name").trim().isEmpty()) {
            String hugo = geneInfo.getString("name").trim();
            String status = geneInfo.has("status") ? geneInfo.getString("status").trim() : null;
            Gene gene = geneBo.findGeneByHugoSymbol(hugo);


            if (gene == null) {
                System.out.println("Could not find gene " + hugo + ". Loading from MyGene.Info...");
                gene = GeneAnnotatorMyGeneInfo2.readByHugoSymbol(hugo);
                if (gene == null) {
//                    throw new RuntimeException("Could not find gene "+hugo+" either.");
                    System.out.println("!!!!!!!!!Could not find gene " + hugo + " either.");
                } else {
                    if (status != null) {
                        gene.setStatus(status);
                    }
                    geneBo.save(gene);
                }
            } else {
                if (status != null) {
                    gene.setStatus(status);
                    geneBo.saveOrUpdate(gene);
                }
            }

            if (gene != null) {
                EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
                List<Evidence> evidences = evidenceBo.findEvidencesByGene(Collections.singleton(gene));
                List<Alteration> alterations = alterationBo.findAlterationsByGene(Collections.singleton(gene));

                for (Evidence evidence : evidences) {
                    evidenceBo.delete(evidence);
                }

                for (Alteration alteration : alterations) {
                    alterationBo.delete(alteration);
                }

                // summary
                parseSummary(gene, geneInfo.has("summary") ? geneInfo.getString("summary").trim() : null);

                // background
                parseGeneBackground(gene, geneInfo.has("background") ? geneInfo.getString("background").trim() : null);

                // mutations
                parseMutations(gene, geneInfo.has("mutations") ? geneInfo.getJSONArray("mutations") : null);

                // Variants of unknown significance
                parseVUS(gene, vus);

                CacheUtils.updateGene(Integer.toString(gene.getEntrezGeneId()));
            } else {
                System.out.print("No gene name available");
            }
        }
    }

    private static void parseSummary(Gene gene, String geneSummary) {
        System.out.println("##  Summary");

        // gene summary
        if (geneSummary != null && !geneSummary.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_SUMMARY);
            evidence.setGene(gene);
            evidence.setDescription(geneSummary);
            setDocuments(geneSummary, evidence);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
        } else {
            System.out.println("    No info...");
        }
    }

    private static void parseGeneBackground(Gene gene, String bg) {
        System.out.println("##  Background");

        if (bg != null && !bg.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_BACKGROUND);
            evidence.setGene(gene);
            evidence.setDescription(bg);
            setDocuments(bg, evidence);

            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
        } else {
            System.out.println("    No info...");
        }
    }

    private static void parseMutations(Gene gene, JSONArray mutations) {
        System.out.println("##  Mutations");
        if (mutations != null) {
            for (int i = 0; i < mutations.length(); i++) {
                parseMutation(gene, mutations.getJSONObject(i));
            }
        } else {
            System.out.println("    no mutation available.");
        }
    }

    private static void parseMutation(Gene gene, JSONObject mutationObj) {
        String mutationStr = mutationObj.has("name") ? mutationObj.getString("name").trim() : null;

        if (mutationStr != null && !mutationStr.isEmpty() && !mutationStr.contains("?")) {
            System.out.println("##  Mutation: " + mutationStr);

            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            AlterationType type = AlterationType.MUTATION; //TODO: cna and fusion

            Set<Alteration> alterations = new HashSet<Alteration>();

            String oncogenic = null;
            String oncogenicSummaryStr = null;

            if (mutationObj.has("oncogenic") && !mutationObj.getString("oncogenic").isEmpty()) {
                String oncogenicStr = mutationObj.getString("oncogenic").toLowerCase();

                switch (oncogenicStr) {
                    case "yes":
                        oncogenic = "1";
                        break;
                    case "likely":
                        oncogenic = "2";
                        break;
                    case "no":
                        oncogenic = "0";
                        break;
                    case "unknown":
                        oncogenic = "-1";
                        break;
                    default:
                        break;
                }
            }
            if (mutationObj.has("shortSummary") && !mutationObj.getString("shortSummary").isEmpty()) {
                oncogenicSummaryStr = mutationObj.getString("shortSummary");
            }

            Map<String, String> mutations = parseMutationString(mutationStr);
            for (Map.Entry<String, String> mutation : mutations.entrySet()) {
                String proteinChange = mutation.getKey();
                String displayName = mutation.getValue();
                Alteration alteration = alterationBo.findAlteration(gene, type, proteinChange);
                if (alteration == null) {
                    alteration = new Alteration();
                    alteration.setGene(gene);
                    alteration.setAlterationType(type);
                    alteration.setAlteration(proteinChange);
                    alteration.setName(displayName);
                    AlterationUtils.annotateAlteration(alteration, proteinChange);
                    alterationBo.save(alteration);
                } else {
                    alterationBo.update(alteration);
                }
                alterations.add(alteration);
                setOncogenic(gene, alteration, oncogenic, oncogenicSummaryStr);
            }

            // mutation summary
            System.out.println("##    Summary");

            if (mutationObj.has("summary") && !mutationObj.getString("summary").isEmpty()) {
                Evidence evidence = new Evidence();
                evidence.setEvidenceType(EvidenceType.MUTATION_SUMMARY);
                evidence.setAlterations(alterations);
                evidence.setGene(gene);
                evidence.setDescription(mutationObj.getString("summary"));
                setDocuments(mutationObj.getString("summary"), evidence);
                EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                evidenceBo.save(evidence);
            } else {
                System.out.println("    No info...");
            }

            // mutation effect
            JSONObject effectObject = mutationObj.has("effect") ? mutationObj.getJSONObject("effect") : null;
            if (effectObject != null) {
                String effect = effectObject.has("value") ? effectObject.getString("value") : null;

                if (effect != null && !effect.isEmpty()) {
                    String effectAddon = effectObject.has("addOn") ? effectObject.getString("addOn") : null;
                    if (effect.equalsIgnoreCase("other")) {
                        if (effectAddon != null && !effectAddon.isEmpty()) {
                            effect = effectAddon;
                        } else {
                            effect = "Other";
                        }
                    } else {
                        if (effectAddon != null && !effectAddon.isEmpty()) {
                            if (StringUtils.containsIgnoreCase(effectAddon, effect)) {
                                effect = effectAddon;
                            } else {
                                effect = effect + " " + effectAddon;
                            }
                        }
                    }
                } else {
                    effect = null;
                }

                // save
                if (effect != null) {
                    Evidence evidence = new Evidence();
                    evidence.setEvidenceType(EvidenceType.MUTATION_EFFECT);
                    evidence.setAlterations(alterations);
                    evidence.setGene(gene);

                    if ((mutationObj.has("description") && !mutationObj.getString("description").trim().isEmpty())) {
                        String desc = mutationObj.getString("description").trim();
                        evidence.setDescription(desc);
                        setDocuments(desc, evidence);
                    }

                    if ((mutationObj.has("short") && !mutationObj.getString("short").trim().isEmpty())) {
                        String desc = mutationObj.getString("short").trim();
                        evidence.setShortDescription(desc);
                        setDocuments(desc, evidence);
                    }

                    evidence.setKnownEffect(effect);

                    EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                    evidenceBo.save(evidence);
                }
            }

            // cancers
            if (mutationObj.has("tumors")) {
                JSONArray cancers = mutationObj.getJSONArray("tumors");
                for (int i = 0; i < cancers.length(); i++) {
                    JSONArray subTumorTypes = cancers.getJSONObject(i).getJSONArray("cancerTypes");
                    for (int j = 0; j < subTumorTypes.length(); j++) {
                        parseCancer(gene, alterations, cancers.getJSONObject(i), subTumorTypes.getJSONObject(j).getString("cancerType"));
                    }
                }
            } else {
                System.out.println("    No tumor available.");
            }
        } else {
            System.out.println("##  Mutation does not have name skip...");
        }
    }

    private static void setOncogenic(Gene gene, Alteration alteration, String oncogenic, String oncogenicSummary) {
        if (alteration != null && gene != null && (oncogenic != null || oncogenicSummary != null)) {
            List<Evidence> evidences = new ArrayList<>();
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidences = evidenceBo.findEvidencesByAlteration(Collections.singleton(alteration), Collections.singleton(EvidenceType.ONCOGENIC));
            if (evidences.size() == 0) {
                Evidence evidence = new Evidence();
                evidence.setGene(gene);
                evidence.setAlterations(Collections.singleton(alteration));
                evidence.setEvidenceType(EvidenceType.ONCOGENIC);
                evidence.setKnownEffect(oncogenic);
                evidence.setDescription(oncogenicSummary);
                evidenceBo.save(evidence);
                setDocuments(oncogenicSummary, evidence);
            } else if (oncogenic.equals("1") || oncogenic.equals("2")) {
                evidences.get(0).setKnownEffect(oncogenic);
                evidences.get(0).setDescription(oncogenicSummary);
                evidenceBo.update(evidences.get(0));
                setDocuments(oncogenicSummary, evidences.get(0));
            }
        }
    }

    private static Map<String, String> parseMutationString(String mutationStr) {
        Map<String, String> ret = new HashMap<String, String>();

        mutationStr = mutationStr.replaceAll("\\([^\\)]+\\)", ""); // remove comments first

        String[] parts = mutationStr.split(", *");

        Pattern p = Pattern.compile("([A-Z][0-9]+)([^0-9/]+/.+)", Pattern.CASE_INSENSITIVE);
        for (String part : parts) {
            String proteinChange, displayName;
            part = part.trim();
            if (part.contains("[")) {
                int l = part.indexOf("[");
                int r = part.indexOf("]");
                proteinChange = part.substring(0, l).trim();
                displayName = part.substring(l + 1, r).trim();
            } else {
                proteinChange = part;
                displayName = part;
            }

            Matcher m = p.matcher(proteinChange);
            if (m.find()) {
                String ref = m.group(1);
                for (String var : m.group(2).split("/")) {
                    ret.put(ref + var, ref + var);
                }
            } else {
                ret.put(proteinChange, displayName);
            }
        }
        return ret;
    }

    private static void parseCancer(Gene gene, Set<Alteration> alterations, JSONObject cancerObj, String cancerName) {
        String cancer = "";

        if(cancerName == null) {
            return;
        }else {
            cancer = cancerName;
        }
//        if (cancerObj.has("name")) {
////            Use cancerTypes array instead of cancer name for now.
////            cancer = cancerObj.getString("name").trim();
//        } else {
//            return;
//        }

        if (cancer.isEmpty() || cancer.endsWith(DO_NOT_IMPORT)) {
            System.out.println("##    Cancer type: " + cancer + " -- skip");
            return;
        }

        System.out.println("##    Cancer type: " + cancer);

        TumorTypeBo tumorTypeBo = ApplicationContextSingleton.getTumorTypeBo();
        TumorType tumorType = tumorTypeBo.findTumorTypeByName(cancer);

        //Check tumor type ID
        if (tumorType == null) {
            tumorType = tumorTypeBo.findTumorTypeById(cancer);
        }

        if (tumorType == null) {
            tumorType = new TumorType();
            tumorType.setTumorTypeId(cancer);
            tumorType.setName(cancer);
            tumorType.setClinicalTrialKeywords(Collections.singleton(cancer));
            tumorTypeBo.save(tumorType);
        }

        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

        // cancer type summary
        System.out.println("##      Summary");

        if (cancerObj.has("summary") && !cancerObj.getString("summary").isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.TUMOR_TYPE_SUMMARY);
            evidence.setGene(gene);
            evidence.setDescription(cancerObj.getString("summary"));
            evidence.setAlterations(alterations);
            evidence.setTumorType(tumorType);
            setDocuments(cancerObj.getString("summary"), evidence);
            evidenceBo.save(evidence);
        } else {
            System.out.println("    No info...");
        }

        // Prevalence
        if ((cancerObj.has("prevalence") && !cancerObj.getString("prevalence").trim().isEmpty()) || (cancerObj.has("shortPrevalence") && !cancerObj.getString("shortPrevalence").trim().isEmpty())) {
            System.out.println("##      Prevalence: " + alterations.toString());

            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.PREVALENCE);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setTumorType(tumorType);

            if (cancerObj.has("shortPrevalence") && !cancerObj.getString("shortPrevalence").trim().isEmpty()) {
                System.out.println("###         Short prevalence: " + alterations.toString());
                String desc = cancerObj.getString("shortPrevalence").trim();
                evidence.setShortDescription(desc);
                setDocuments(desc, evidence);
            }

            if (cancerObj.has("prevalence") && !cancerObj.getString("prevalence").trim().isEmpty()) {
                System.out.println("###         Full prevalence: " + alterations.toString());
                String desc = cancerObj.getString("prevalence").trim();
                evidence.setDescription(desc);
                setDocuments(desc, evidence);
            }
            evidenceBo.save(evidence);
        } else {
            System.out.println("##      No Prevalance for " + alterations.toString());
        }

        // Prognostic implications
        if ((cancerObj.has("progImp") && !cancerObj.getString("progImp").trim().isEmpty()) || (cancerObj.has("shortProgImp") && !cancerObj.getString("shortProgImp").trim().isEmpty())) {
            System.out.println("##      Prognostic implications:" + alterations.toString());

            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.PROGNOSTIC_IMPLICATION);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setTumorType(tumorType);

            if (cancerObj.has("shortProgImp") && !cancerObj.getString("shortProgImp").trim().isEmpty()) {
                System.out.println("###         Short prognostic implications: " + alterations.toString());
                String desc = cancerObj.getString("shortProgImp").trim();
                evidence.setShortDescription(desc);
                setDocuments(desc, evidence);
            }

            if (cancerObj.has("progImp") && !cancerObj.getString("progImp").trim().isEmpty()) {
                System.out.println("###         Full prognostic implications: " + alterations.toString());
                String desc = cancerObj.getString("progImp").trim();
                evidence.setDescription(desc);
                setDocuments(desc, evidence);
            }
            evidenceBo.save(evidence);
        } else {
            System.out.println("##      No Proganostic implications " + alterations.toString());
        }

        JSONArray implications = cancerObj.getJSONArray("TI");

        for (int i = 0; i < implications.length(); i++) {
            JSONObject implication = implications.getJSONObject(i);
            if ((implication.has("description") && !implication.getString("description").trim().isEmpty()) || (implication.has("treatments") && implication.getJSONArray("treatments").length() > 0)) {
                EvidenceType evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
                String type = "";
                if (implication.has("status") && implication.has("type")) {
                    if (implication.getString("status").equals("1")) {
                        if (implication.getString("type").equals("1")) {
                            evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
                            type = "Sensitive";
                        } else if (implication.getString("type").equals("0")) {
                            evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE;
                            type = "Resistant";
                        }
                    } else if (implication.getString("status").equals("0")) {
                        if (implication.getString("type").equals("1")) {
                            evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY;
                            type = "Sensitive";
                        } else if (implication.getString("type").equals("0")) {
                            evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE;
                            type = "Resistant";
                        }
                    }
                    parseTherapeuticImplcations(gene, alterations, tumorType, implication, evidenceType, type);
                }
            }
        }

        // NCCN
        if (cancerObj.has("nccn") && cancerObj.getJSONObject("nccn").has("disease") && !cancerObj.getJSONObject("nccn").getString("disease").isEmpty()) {
            System.out.println("##      NCCN for " + alterations.toString());
            parseNCCN(gene, alterations, tumorType, cancerObj.getJSONObject("nccn"));
        } else {
            System.out.println("##      No NCCN for " + alterations.toString());
        }

        if (cancerObj.has("trials") && cancerObj.getJSONArray("trials").length() > 0) {
            System.out.println("##      Clincial trials for " + alterations.toString());
            parseClinicalTrials(gene, alterations, tumorType, cancerObj.getJSONArray("trials"));
        } else {
            System.out.println("##      No Clincial trials for " + alterations.toString());
        }
    }

    private static void parseClinicalTrials(Gene gene, Set<Alteration> alterations, TumorType tumorType, JSONArray trialsArray) {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

        Set<String> nctIds = new HashSet<String>();
        for (int i = 0; i < trialsArray.length(); i++) {
            String nctId = trialsArray.getString(i).trim();
            if (!nctId.isEmpty()) {
                nctIds.add(nctId);
            }
        }

        List<Evidence> evidences = evidenceBo.findEvidencesByAlteration(alterations, Collections.singleton(EvidenceType.CLINICAL_TRIAL), Collections.singleton(tumorType));

        for (Evidence eve : evidences) {
            evidenceBo.delete(eve);
        }

        try {
            List<ClinicalTrial> trials = ClinicalTrialsImporter.importTrials(nctIds);
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.CLINICAL_TRIAL);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setTumorType(tumorType);
            evidence.setClinicalTrials(new HashSet<ClinicalTrial>(trials));
            evidenceBo.save(evidence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseTherapeuticImplcations(Gene gene, Set<Alteration> alterations, TumorType tumorType, JSONObject implicationObj,
                                                    EvidenceType evidenceType, String knownEffectOfEvidence) {
        System.out.println("##      " + evidenceType + " for " + alterations.toString() + " " + tumorType.getName());

        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

        if (implicationObj.has("description") && !implicationObj.getString("description").trim().isEmpty()) {
            // general description
            String desc = implicationObj.getString("description");
            if (!desc.isEmpty()) {
                Evidence evidence = new Evidence();
                evidence.setEvidenceType(evidenceType);
                evidence.setAlterations(alterations);
                evidence.setGene(gene);
                evidence.setTumorType(tumorType);
                evidence.setKnownEffect(knownEffectOfEvidence);
                if (implicationObj.has("shortProgImp") && !implicationObj.getString("shortProgImp").trim().isEmpty()) {
                    String shortDesc = implicationObj.getString("shortProgImp").trim();
                    evidence.setShortDescription(shortDesc);
                    setDocuments(shortDesc, evidence);
                }
                evidence.setDescription(desc);
                setDocuments(desc, evidence);
                evidenceBo.save(evidence);
            }
        }

        // specific evidence
        DrugBo drugBo = ApplicationContextSingleton.getDrugBo();
        TreatmentBo treatmentBo = ApplicationContextSingleton.getTreatmentBo();
        JSONArray drugsArray = implicationObj.getJSONArray("treatments");

        for (int i = 0; i < drugsArray.length(); i++) {
            JSONObject drugObj = drugsArray.getJSONObject(i);
            if (!drugObj.has("name") || drugObj.getString("name").trim().isEmpty()) {
                System.out.println("##        drug dpes not have name, skip... ");
                continue;
            }

            String drugNameStr = drugObj.getString("name").trim();
            System.out.println("##        drugs: " + drugNameStr);

            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setTumorType(tumorType);
            evidence.setKnownEffect(knownEffectOfEvidence);

            // approved indications
            Set<String> appovedIndications = new HashSet<String>();
            if (drugObj.has("indication") && !drugObj.getString("indication").trim().isEmpty()) {
                appovedIndications = new HashSet<String>(Arrays.asList(drugObj.getString("indication").split(";")));
            }

            String[] drugTxts = drugNameStr.replaceAll("(\\([^\\)]*\\))|(\\[[^\\]]*\\])", "").split(",");

            Set<Treatment> treatments = new HashSet<Treatment>();
            for (String drugTxt : drugTxts) {
                String[] drugNames = drugTxt.split(" ?\\+ ?");

                Set<Drug> drugs = new HashSet<Drug>();
                for (String drugName : drugNames) {
                    drugName = drugName.trim();
                    Drug drug = drugBo.guessUnambiguousDrug(drugName);
                    if (drug == null) {
                        drug = new Drug(drugName);
                        drugBo.save(drug);
                    }
                    drugs.add(drug);
                }

                Treatment treatment = new Treatment();
                treatment.setDrugs(drugs);
                treatment.setApprovedIndications(appovedIndications);

                treatmentBo.save(treatment);

                treatments.add(treatment);
            }
            evidence.setTreatments(treatments);

            // highest level of evidence
            if (!drugObj.has("level") || drugObj.getString("level").trim().isEmpty()) {
                System.err.println("Error: no level of evidence");
                // TODO:
                //throw new RuntimeException("no level of evidence");
            } else {
                String level = drugObj.getString("level").trim().toLowerCase();
                if (level.equals("2")) {

                    if (evidenceType == EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE
                            || evidenceType == EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY) {
                        level = "2a";
                    } else {
                        level = "2b";
                    }
                }

                LevelOfEvidence levelOfEvidence = LevelOfEvidence.getByLevel(level);
                if (levelOfEvidence == null) {
                    System.err.println("Errow: wrong level of evidence: " + level);
                    // TODO:
                    //throw new RuntimeException("wrong level of evidence: "+level);
                }
                evidence.setLevelOfEvidence(levelOfEvidence);
            }

            // description
            if (drugObj.has("description") && !drugObj.getString("description").trim().isEmpty()) {
                String desc = drugObj.getString("description").trim();
                if (drugObj.has("short") && !drugObj.getString("short").trim().isEmpty()) {
                    String shortDesc = drugObj.getString("short").trim();
                    evidence.setShortDescription(shortDesc);
                    setDocuments(shortDesc, evidence);
                }
                evidence.setDescription(desc);
                setDocuments(desc, evidence);
            }

            evidenceBo.save(evidence);
        }
    }

    private static void parseNCCN(Gene gene, Set<Alteration> alterations, TumorType tumorType, JSONObject nccnObj) {
        // disease
        String disease = null;
        if (nccnObj.has("disease") && !nccnObj.getString("disease").trim().isEmpty()) {
            disease = nccnObj.getString("disease").trim();
        }

        // version
        String version = null;
        if (nccnObj.has("version") && !nccnObj.getString("version").trim().isEmpty()) {
            version = nccnObj.getString("version").trim();
        }

        // pages
        String pages = null;
        if (nccnObj.has("pages") && !nccnObj.getString("pages").trim().isEmpty()) {
            pages = nccnObj.getString("pages").trim();
        }

        // Recommendation category
        String category = null;
        if (nccnObj.has("category") && !nccnObj.getString("category").trim().isEmpty()) {
            category = nccnObj.getString("category").trim();
        }

        // description
        String nccnDescription = null;
        if (nccnObj.has("description") && !nccnObj.getString("description").trim().isEmpty()) {
            nccnDescription = nccnObj.getString("description").trim();
        }

        Evidence evidence = new Evidence();
        evidence.setEvidenceType(EvidenceType.NCCN_GUIDELINES);
        evidence.setAlterations(alterations);
        evidence.setGene(gene);
        evidence.setTumorType(tumorType);
        evidence.setDescription(nccnDescription);

        NccnGuidelineBo nccnGuideLineBo = ApplicationContextSingleton.getNccnGuidelineBo();

        NccnGuideline nccnGuideline = new NccnGuideline();
        nccnGuideline.setDisease(disease);
        nccnGuideline.setVersion(version);
        nccnGuideline.setPages(pages);
        nccnGuideline.setCategory(category);
        nccnGuideline.setDescription(nccnDescription);

        if (nccnObj.has("short") && !nccnObj.getString("short").trim().isEmpty()) {
            String shortDesc = nccnObj.getString("short").trim();
            evidence.setShortDescription(shortDesc);
            nccnGuideline.setShortDescription(shortDesc);
        }

        nccnGuideLineBo.save(nccnGuideline);

        evidence.setNccnGuidelines(Collections.singleton(nccnGuideline));

        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        evidenceBo.save(evidence);
    }

    private static void setDocuments(String str, Evidence evidence) {
        if (str == null) return;
        Set<Article> docs = new HashSet<Article>();
        Set<ClinicalTrial> clinicalTrials = new HashSet<ClinicalTrial>();
        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
        ClinicalTrialBo clinicalTrialBo = ApplicationContextSingleton.getClinicalTrialBo();
        Pattern pmidPattern = Pattern.compile("\\(PMIDs?:([^\\);]+).*\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = pmidPattern.matcher(str);
        int start = 0;
        while (m.find(start)) {
            String pmids = m.group(1).trim();
            for (String pmid : pmids.split(", *(PMID:)? *")) {
                if (pmid.startsWith("NCT")) {
                    // support NCT numbers
                    Set<String> nctIds = new HashSet<String>(Arrays.asList(pmid.split(", *")));
                    try {
                        List<ClinicalTrial> trials = ClinicalTrialsImporter.importTrials(nctIds);
                        for (ClinicalTrial trial : trials) {
                            clinicalTrialBo.saveOrUpdate(trial);
                            clinicalTrials.add(trial);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                Article doc = articleBo.findArticleByPmid(pmid);
                if (doc == null) {
                    doc = NcbiEUtils.readPubmedArticle(pmid);
                    articleBo.save(doc);
                }
                docs.add(doc);
            }
            start = m.end();
        }

        evidence.addArticles(docs);
        evidence.addClinicalTrials(clinicalTrials);
    }
}
