package org.mskcc.cbio.oncokb.util;

import junit.framework.TestCase;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.Evidence;

import java.util.*;

/**
 * Created by Hongxin on 3/8/17.
 */
public class EvidenceUtilsTest extends TestCase {
    public void testSortTumorTypeEvidenceBasedNumOfAlts() throws Exception {
        Evidence e1 = new Evidence();
        e1.setId(1);
        Evidence e2 = new Evidence();
        e2.setId(2);
        Alteration a1 = new Alteration();
        a1.setAlteration("a");
        Alteration a2 = new Alteration();
        a1.setAlteration("b");

        Set<Alteration> alts = new HashSet<>();
        alts.add(a1);
        alts.add(a2);
        e1.setAlterations(Collections.singleton(a1));
        e2.setAlterations(alts);

        List<Evidence> evidenceList = new ArrayList<>();
        evidenceList.add(e1);
        evidenceList.add(e2);
        evidenceList = EvidenceUtils.sortTumorTypeEvidenceBasedNumOfAlts(evidenceList, false);

        assertEquals(1, evidenceList.get(0).getAlterations().size());
        assertEquals(2, evidenceList.get(1).getAlterations().size());


        evidenceList = EvidenceUtils.sortTumorTypeEvidenceBasedNumOfAlts(evidenceList, true);

        assertEquals(2, evidenceList.get(0).getAlterations().size());
        assertEquals(1, evidenceList.get(1).getAlterations().size());

        // if evidences has same number of alterations, the original order should be kept.
        Evidence e3 = new Evidence();
        e3.setId(3);
        e3.setAlterations(alts);
        evidenceList = new ArrayList<>();
        evidenceList.add(e3);
        evidenceList.add(e1);
        evidenceList.add(e2);

        evidenceList = EvidenceUtils.sortTumorTypeEvidenceBasedNumOfAlts(evidenceList, true);

        assertEquals(3, evidenceList.get(0).getId().intValue());
        assertEquals(2, evidenceList.get(1).getId().intValue());
        assertEquals(1, evidenceList.get(2).getId().intValue());

        evidenceList = new ArrayList<>();
        evidenceList.add(e1);
        evidenceList.add(e2);
        evidenceList.add(e3);

        evidenceList = EvidenceUtils.sortTumorTypeEvidenceBasedNumOfAlts(evidenceList, true);

        assertEquals(2, evidenceList.get(0).getId().intValue());
        assertEquals(3, evidenceList.get(1).getId().intValue());
        assertEquals(1, evidenceList.get(2).getId().intValue());

    }

}
