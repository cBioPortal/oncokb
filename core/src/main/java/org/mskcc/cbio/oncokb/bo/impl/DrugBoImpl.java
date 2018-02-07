/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.bo.impl;

import org.mskcc.cbio.oncokb.bo.DrugBo;
import org.mskcc.cbio.oncokb.dao.DrugDao;
import org.mskcc.cbio.oncokb.model.Drug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author jgao
 */
public class DrugBoImpl extends GenericBoImpl<Drug, DrugDao> implements DrugBo {
    private String correctDrugName(String drugName) {
        // Always uppercase first letter
        if (drugName == null || drugName.isEmpty()) {
            return drugName;
        } else if (drugName.length() == 1) {
            return drugName.toUpperCase();
        }
        drugName = drugName.replaceAll("(\\([^\\)]*\\))|(\\[[^\\]]*\\])", "");
        return drugName.substring(0, 1).toUpperCase() + drugName.substring(1);
    }

    @Override
    public Drug findDrugByName(String drugName) {
        return getDao().findDrugByName(drugName);
    }

    @Override
    public List<Drug> findDrugsByNames(Collection<String> drugNames) {
        List<Drug> drugs = new ArrayList<Drug>();
        for (String drugName : drugNames) {
            Drug drug = getDao().findDrugByName(drugName);
            if (drug != null) {
                drugs.add(drug);
            }
        }
        return drugs;
    }

    @Override
    public List<Drug> findDrugsBySynonym(String synonym) {
        return getDao().findDrugBySynonym(synonym);
    }

    @Override
    public List<Drug> guessDrugs(String drugNameOrSynonym) {
        Drug drug = findDrugByName(drugNameOrSynonym);
        if (drug != null) {
            return Collections.singletonList(drug);
        }

        return findDrugsBySynonym(drugNameOrSynonym);
    }

    @Override
    public Drug guessUnambiguousDrug(String drugNameOrSynonym) {
        List<Drug> drugs = guessDrugs(drugNameOrSynonym);
        if (drugs.size() == 1) {
            return drugs.get(0);
        }

        return null;
    }

    @Override
    public List<Drug> findDrugsByAtcCode(String atcCode) {
        return getDao().findDrugByAtcCode(atcCode);
    }

    @Override
    public void update(Drug drug) {
        if (drug != null) {
            drug.setDrugName(correctDrugName(drug.getDrugName()));
        }
        super.update(drug);
    }

    @Override
    public void save(Drug drug) {
        if (drug != null) {
            drug.setDrugName(correctDrugName(drug.getDrugName()));
        }
        super.save(drug);
    }

    @Override
    public void saveOrUpdate(Drug drug) {
        if (drug != null) {
            drug.setDrugName(correctDrugName(drug.getDrugName()));
        }
        super.saveOrUpdate(drug);
    }
}
