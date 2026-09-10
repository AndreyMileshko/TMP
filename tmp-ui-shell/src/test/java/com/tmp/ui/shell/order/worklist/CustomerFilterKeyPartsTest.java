package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerFilterKeyPartsTest {

    @Test
    void inputMutationDoesNotChangeParts() {
        Set<String> refs = new LinkedHashSet<>();
        refs.add("CUST-1");
        Set<String> names = new LinkedHashSet<>();
        names.add("Alpha");
        CustomerFilterKey.Parts parts = new CustomerFilterKey.Parts(refs, names, true);

        refs.add("CUST-2");
        names.add("Beta");

        assertEquals(Set.of("CUST-1"), parts.refs());
        assertEquals(Set.of("Alpha"), parts.names());
        assertTrue(parts.unassigned());
    }

    @Test
    void accessorMutationIsRejectedAndDoesNotChangeParts() {
        CustomerFilterKey.Parts parts =
                new CustomerFilterKey.Parts(Set.of("CUST-1"), Set.of("Alpha"), false);

        assertThrows(UnsupportedOperationException.class, () -> parts.refs().add("CUST-2"));
        assertThrows(UnsupportedOperationException.class, () -> parts.names().add("Beta"));

        assertEquals(Set.of("CUST-1"), parts.refs());
        assertEquals(Set.of("Alpha"), parts.names());
    }

    @Test
    void partsCollectsRefsNamesAndUnassigned() {
        Set<CustomerFilterKey> keys = new LinkedHashSet<>();
        keys.add(CustomerFilterKey.ref("R2"));
        keys.add(CustomerFilterKey.ref("R1"));
        keys.add(CustomerFilterKey.name("N2"));
        keys.add(CustomerFilterKey.name("N1"));
        keys.add(CustomerFilterKey.unassigned());

        CustomerFilterKey.Parts parts = CustomerFilterKey.parts(keys);

        assertEquals(Set.of("R1", "R2"), parts.refs());
        assertEquals(Set.of("N1", "N2"), parts.names());
        assertTrue(parts.unassigned());
    }
}
