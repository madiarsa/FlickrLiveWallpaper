package novoda.net;

import junit.framework.TestCase;;

public class TestGeoNames  extends TestCase {
	
	public void testGeoNames() throws Exception{
		GeoNamesAPI geoNames = new GeoNamesAPI();
		String lat= "51.332704";
		String lon = "-0.755131";
		assertTrue(geoNames.getNearestPlaceName(lat, lon).equalsIgnoreCase("camberley"));
	}
}
