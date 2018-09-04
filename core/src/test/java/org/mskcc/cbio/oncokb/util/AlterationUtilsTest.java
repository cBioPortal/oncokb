package org.mskcc.cbio.oncokb.util;

import junit.framework.TestCase;
import org.mskcc.cbio.oncokb.model.Alteration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Hongxin Zhang on 6/20/18.
 */
public class AlterationUtilsTest extends TestCase

{
    public void testAnnotateAlteration() throws Exception {
    }

    public void testIsFusion() throws Exception {
    }

    public void testGetRevertFusions() throws Exception {
    }

    public void testTrimAlterationName() throws Exception {
    }

    public void testGetAlteration() throws Exception {
    }

    public void testGetAlterationByHGVS() throws Exception {
    }

    public void testGetOncogenic() throws Exception {
    }

    public void testGetAllAlterations() throws Exception {
    }

    public void testGetAllAlterations1() throws Exception {
    }

    public void testGetTruncatingMutations() throws Exception {
    }

    public void testFindVUSFromEvidences() throws Exception {
    }

    public void testExcludeVUS() throws Exception {
    }

    public void testExcludeVUS1() throws Exception {
    }

    public void testExcludeInferredAlterations() throws Exception {
    }

    public void testExcludePositionedAlterations() throws Exception {
    }

    public void testIsInferredAlterations() throws Exception {
    }

    public void testIsLikelyInferredAlterations() throws Exception {
    }

    public void testGetAlterationsByKnownEffectInGene() throws Exception {
    }

    public void testGetInferredAlterationsKnownEffect() throws Exception {
    }

    public void testGetAlleleAlterations() throws Exception {
    }

    public void testGetAlleleAlterations1() throws Exception {
    }

    public void testGetPositionedAlterations() throws Exception {
        Alteration alteration = new Alteration();
        alteration.setGene(GeneUtils.getGeneByHugoSymbol("BRAF"));
        alteration.setAlteration("V600E");
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        List<Alteration> positionedAlterations = AlterationUtils.getPositionedAlterations(alteration);
        List<String> alterations = new ArrayList<>();
        for (Alteration alt : positionedAlterations) {
            alterations.add(alt.getAlteration());
        }
        assertEquals("V600", MainUtils.listToString(alterations, ","));

        // non missense should not be annotated
        alteration = new Alteration();
        alteration.setGene(GeneUtils.getGeneByHugoSymbol("BRAF"));
        alteration.setAlteration("V600del");
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        positionedAlterations = AlterationUtils.getPositionedAlterations(alteration);
        alterations = new ArrayList<>();
        for (Alteration alt : positionedAlterations) {
            alterations.add(alt.getAlteration());
        }
        assertEquals("", MainUtils.listToString(alterations, ","));
    }

    public void testGetPositionedAlterations1() throws Exception {
    }

    public void testGetUniqueAlterations() throws Exception {
    }

    public void testLookupVariant() throws Exception {
    }

    public void testGetAlleleAndRelevantAlterations() throws Exception {
    }

    public void testFindOncogenicAllele() throws Exception {
    }

    public void testGetRelevantAlterations() throws Exception {
    }

    public void testHasAlleleAlterations() throws Exception {
    }

    public void testFindAlteration() throws Exception {
    }

    public void testIsOncogenicAlteration() throws Exception {
    }

    public void testHasImportantCuratedOncogenicity() throws Exception {
    }

    public void testHasOncogenic() throws Exception {
    }

    public void testGetCuratedOncogenicity() throws Exception {
    }

    public void testGetOncogenicMutations() throws Exception {
    }

    public void testGetGeneralVariants() throws Exception {
    }

    public void testGetInferredMutations() throws Exception {
    }

    public void testGetStructuralAlterations() throws Exception {
    }

    public void testIsPositionedAlteration() throws Exception {
    }

    public void testIsGeneralAlterations() throws Exception {
    }

    public void testIsGeneralAlterations1() throws Exception {
    }

    public void testSortAlterationsByTheRange() throws Exception {
        int start = 8;
        int end = 8;
        Integer[] starts = {0, 8, 8, null, 8};
        Integer[] ends = {10, 9, 8, 8, null};
        List<Alteration> alterationList = new ArrayList<>();
        for (int i = 0; i < starts.length; i++) {
            Alteration alt = new Alteration();
            alt.setProteinStart(starts[i]);
            alt.setProteinEnd(ends[i]);
            alt.setName(Integer.toString(i));
            alterationList.add(alt);
        }
        AlterationUtils.sortAlterationsByTheRange(alterationList, start, end);
        assertEquals(5, alterationList.size());
        assertEquals(alterationList.get(0).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(0).getProteinEnd().intValue(), 8);
        assertEquals(alterationList.get(1).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(1).getProteinEnd().intValue(), 9);
        assertEquals(alterationList.get(2).getProteinStart().intValue(), 0);
        assertEquals(alterationList.get(2).getProteinEnd().intValue(), 10);
        assertEquals(alterationList.get(3).getProteinStart(), null);
        assertEquals(alterationList.get(3).getProteinEnd().intValue(), 8);
        assertEquals(alterationList.get(4).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(4).getProteinEnd(), null);
    }

    public void testIsPositionVariant() throws Exception {
        Alteration alteration = AlterationUtils.getAlteration("AKT1", "E17", null, "NA", null, null);
        assertTrue("This variant should be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17", null, null, null, null);
        assertTrue("This variant should be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17*", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17*", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "17", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17A", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17AA", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17AA", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("EGFR", "L747Rfs*13", null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

    }

}
