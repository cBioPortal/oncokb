package org.mskcc.cbio.oncokb.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.AlterationPositionBoundary;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

/**
 * Created by Hongxin on 12/23/16.
 */

@RunWith(Parameterized.class)
public class AnnotateAlterationTest {
    private String proteinChange;
    private String proteinStart;
    private String proteinEnd;
    private String expectedProteinStart;
    private String expectedProteinEnd;
    private String expectedRefResidues;
    private String expectedVariantResidues;
    private String expectedConsequence;

    public AnnotateAlterationTest(String proteinChange, String proteinStart, String proteinEnd, String expectedProteinStart, String expectedProteinEnd, String expectedRefResidues, String expectedVariantResidues, String expectedConsequence) {
        this.proteinChange = proteinChange;
        this.proteinStart = proteinStart;
        this.proteinEnd = proteinEnd;
        this.expectedProteinStart = expectedProteinStart;
        this.expectedProteinEnd = expectedProteinEnd;
        this.expectedRefResidues = expectedRefResidues;
        this.expectedVariantResidues = expectedVariantResidues;
        this.expectedConsequence = expectedConsequence;
    }

    @Parameterized.Parameters
    public static Collection<String[]> getParameters() {
        return Arrays.asList(
            new String[][]{
                // any
                {"449_514mut", "449", "514", "449", "514", null, null, "any"},

                // Missense variant
                {"V600E", "600", "600", "600", "600", "V", "E", "missense_variant"},
                {"F53_Q53delinsL", "53", "53", "53", "53", null, null, "missense_variant"},
                {"D842_I843delinsIM", "842", "843", "842", "843", null, null, "missense_variant"},
                {"IK744KI", "744", "745", "744", "745", "IK", "KI", "missense_variant"},

                // feature_truncating variant
                {"D286_L292trunc", "286", "292", "286", "292", null, null, "feature_truncation"},
                {"Truncating Mutations", Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), null, null, "feature_truncation"},

                // frameshift event
                {"N457Mfs*22", "457", "457", "457", "457", "N", null, "frameshift_variant"},
                {"*1069Ffs*5", "1069", "1069", "1069", "1069", "*", null, "frameshift_variant"},

                // inframe event
                {"T417_D419delinsI", "417", "419", "417", "419", null, null, "inframe_deletion"},
                {"E102_I103del", "102", "103", "102", "103", null, null, "inframe_deletion"},
                {"V600delinsYM", "600", "600", "600", "600", null, null, "inframe_insertion"},
                {"I744_K745delinsKIPVAI", "744", "745", "744", "745", null, null, "inframe_insertion"},
                {"762_823ins", "762", "823", "762", "823", null, null, "inframe_insertion"},
                {"V561_I562insER", "561", "562", "561", "562", null, null, "inframe_insertion"},
                {"IK744KIPVAI", "744", "745", "744", "745", "IK", "KIPVAI", "inframe_insertion"},
                {"IK744K", "744", "745", "744", "745", "IK", "K", "inframe_deletion"},
                {"IKG744KIPVAI", "744", "746", "744", "746", "IKG", "KIPVAI", "inframe_insertion"},
                {"P68_C77dup", "68", "77", "68", "77", null, null, "inframe_insertion"},

                // start_lost,
                {"M1I", "1", "1", "1", "1", "M", "I", "start_lost"},
                {"M1?", "1", "1", "1", "1", "M", "?", "start_lost"},

                // NA
                {"BCR-ABL1 Fusion", Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), null, null, "NA"},
                {"Oncogenic Mutations", Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), Integer.toString(AlterationPositionBoundary.START.getValue()), Integer.toString(AlterationPositionBoundary.END.getValue()), null, null, "NA"},
                {"V600", "600", "600", "600", "600", "V", null, "NA"},

                // Splice
                {"X405_splice", "405", "405", "405", "405", null, null, "splice_region_variant"},
                {"405_splice", "405", "405", "405", "405", null, null, "splice_region_variant"},
                {"405splice", "405", "405", "405", "405", null, null, "splice_region_variant"},
                {"X405_A500splice", "405", "500", "405", "500", null, null, "splice_region_variant"},
                {"X405_A500_splice", "405", "500", "405", "500", null, null, "splice_region_variant"},
                {"405_500_splice", "405", "500", "405", "500", null, null, "splice_region_variant"},
                {"405_500splice", "405", "500", "405", "500", null, null, "splice_region_variant"},

                // Stop gained
                {"R2109*", "2109", "2109", "2109", "2109", "R", "*", "stop_gained"},

                // Synonymous Variant
                {"G500G", "500", "500", "500", "500", "G", "G", "synonymous_variant"},

                // Overwrite protein start, protein end
                {"V105_E109delinsG", "109", "109", "105", "109", null, null, "inframe_deletion"},
                {"P191del", "191", "192", "191", "191", "P", null, "inframe_deletion"},
                // Made up case
                {"Oncogenic Mutations", "10", "50", "10", "50", null, null, "NA"},
            });
    }

    private Boolean checkNull(String expecteded, String result) {
        if (expecteded == null && result == null) {
            return true;
        } else if (expecteded != null && result != null) {
            return expecteded.equals(result);
        }
        return false;
    }

    @Test
    public void testAnnotateAlteration() throws Exception {
        // Particularly test expectedConsequence
        Alteration alt = new Alteration();
        alt.setProteinStart(Integer.parseInt(proteinStart));
        alt.setProteinEnd(Integer.parseInt(proteinEnd));
        AlterationUtils.annotateAlteration(alt, proteinChange);

        String _proteinStart = null;
        String _proteinEnd = null;
        String _consequence = null;

        if (alt.getProteinStart() != null) {
            _proteinStart = alt.getProteinStart().toString();
        }
        if (alt.getProteinStart() != null) {
            _proteinEnd = alt.getProteinEnd().toString();
        }
        if (alt.getConsequence() != null) {
            _consequence = alt.getConsequence().getTerm();
        }

        assertTrue(proteinChange + ": Protein start should be " + expectedProteinStart + ", but got: " + _proteinStart, checkNull(expectedProteinStart, _proteinStart));
        assertTrue(proteinChange + ": Protein end should be " + expectedProteinEnd + ", but got: " + _proteinEnd, checkNull(expectedProteinEnd, _proteinEnd));
        assertTrue(proteinChange + ": Ref residues should be " + expectedRefResidues + ", but got: " + alt.getRefResidues(), checkNull(expectedRefResidues, alt.getRefResidues()));
        assertTrue(proteinChange + ": Ref residues should be " + expectedVariantResidues + ", but got: " + alt.getVariantResidues(), checkNull(expectedVariantResidues, alt.getVariantResidues()));
        assertTrue(proteinChange + ": Consequence should be " + expectedConsequence + ", but got: " + _consequence, checkNull(expectedConsequence, _consequence));
    }

}
