package com.walmartlabs.concord.plugins.ansible.inventory;

import com.walmartlabs.concord.plugins.ansible.inventory.InventoryDao.InventoryRecord;
import com.walmartlabs.concord.plugins.ansible.inventory.api.InventoryEntry;
import com.walmartlabs.concord.plugins.ansible.inventory.api.InventoryResource;
import com.walmartlabs.concord.plugins.ansible.inventory.api.Permissions;
import org.apache.shiro.subject.Subject;
import org.jooq.Field;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InventoryResourceTest extends AbstractShiroTest {

    @Test
    public void testSecurityFilter() throws Exception {
        List<InventoryRecord> records = Arrays.asList(
                new InventoryRecord("1", "a"),
                new InventoryRecord("2", "b"),
                new InventoryRecord("3", "c")
        );

        InventoryDao dao = mock(InventoryDao.class);
        when(dao.list(any(Field.class), anyBoolean())).thenReturn(records);

        InventoryResource inventoryResource = new InventoryResourceImpl(dao);

        // ---

        Subject anon = mock(Subject.class);
        when(anon.isAuthenticated()).thenReturn(false);
        setSubject(anon);

        List<InventoryEntry> result = inventoryResource.list(null, true);
        assertTrue(result.isEmpty());

        // ---

        Subject validUser = mock(Subject.class);
        when(validUser.isAuthenticated()).thenReturn(true);
        when(validUser.isPermitted(String.format(Permissions.INVENTORY_USE_INSTANCE, "a"))).thenReturn(true);
        when(validUser.isPermitted(String.format(Permissions.INVENTORY_MANAGE_INSTANCE, "c"))).thenReturn(true);
        setSubject(validUser);

        result = inventoryResource.list(null, true);
        assertEquals(2, result.size());

        assertTrue(result.get(0).isReadOnly());
        assertFalse(result.get(1).isReadOnly());
    }
}
