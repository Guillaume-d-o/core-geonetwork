package org.fao.geonet.kernel.harvest.harvester.simpleurl;

import org.fao.geonet.utils.Log;
import org.junit.Test;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Test for STAC harvesting with SimpleUrlHarvester
 */
public class SimpleUrlStacTest {

    @Test
    public void testIsStacLike() throws Exception {
        // Load a sample STAC item from test resources
        String stacJson = getResourceAsString("/stac/stac-item-sample.json");
        assertNotNull("Sample STAC JSON not loaded", stacJson);
        
        // Create test instance of Harvester
        SimpleUrlParams params = new SimpleUrlParams(null);
        Harvester harvester = new Harvester(null, Log.createLogger("TEST"), null, params, new ArrayList<>());
        
        // Use reflection to access private method for testing
        java.lang.reflect.Method isStacLikeMethod = Harvester.class.getDeclaredMethod("isSTACLike", String.class);
        isStacLikeMethod.setAccessible(true);
        boolean isStac = (boolean) isStacLikeMethod.invoke(harvester, stacJson);
        
        assertTrue("Failed to identify STAC item content", isStac);
    }
    
    @Test
    public void testIsStacCollectionLike() throws Exception {
        // Load a sample STAC collection from test resources
        String stacJson = getResourceAsString("/stac/stac-collection-sample.json");
        assertNotNull("Sample STAC Collection JSON not loaded", stacJson);
        
        // Create test instance of Harvester
        SimpleUrlParams params = new SimpleUrlParams(null);
        Harvester harvester = new Harvester(null, Log.createLogger("TEST"), null, params, new ArrayList<>());
        
        // Use reflection to access private method for testing
        java.lang.reflect.Method isStacLikeMethod = Harvester.class.getDeclaredMethod("isSTACLike", String.class);
        isStacLikeMethod.setAccessible(true);
        boolean isStac = (boolean) isStacLikeMethod.invoke(harvester, stacJson);
        
        assertTrue("Failed to identify STAC collection content", isStac);
    }
    
    private String getResourceAsString(String resourcePath) {
        try {
            return IOUtils.toString(
                this.getClass().getResourceAsStream(resourcePath), 
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            return null;
        }
    }
}
