/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2017, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geoserver.wps.ppio;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.logging.Logger;
import org.geoserver.platform.resource.Resource;
import org.geoserver.util.IOUtils;
import org.geoserver.wps.resource.WPSResourceManager;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureStore;
import org.geotools.data.Transaction;
import org.geotools.data.csv.CSVDataStore;
import org.geotools.data.csv.CSVDataStoreFactory;
import org.geotools.data.csv.CSVFeatureStore;
import org.geotools.feature.collection.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.util.logging.Logging;
import org.geotools.xml.Text;

/** @author ian */
public class CSVPPIO extends CDataPPIO {
    WPSResourceManager resourceManager;
    private static final Logger LOGGER = Logging.getLogger("org.geoserver.wps.ppio.CSVPPIO");

    protected CSVPPIO(WPSResourceManager resourceManager) {
        super(SimpleFeatureCollection.class, SimpleFeatureCollection.class, "text/csv");
        this.resourceManager = resourceManager;
    }

    @Override
    public Object decode(String input) throws Exception {
        return decode(new ByteArrayInputStream(input.getBytes()));
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }

    @Override
    public Object decode(Object input) throws Exception {
        Class<? extends Object> type = input.getClass();
        if (type.isAssignableFrom(String.class)) {
            return decode((String) input);
        }
        if (type.isAssignableFrom(Text.class)) {
            return decode(((Text) input).getValue());
        }
        return super.decode(input);
    }

    @Override
    public Object decode(InputStream input) throws Exception {
        // this will be deleted for us when the process finishes
        Resource tmp = resourceManager.getTemporaryResource(".csv");

        IOUtils.copy(input, tmp.out());
        HashMap<String, Object> params = new HashMap<>();
        params.put(CSVDataStoreFactory.FILE_PARAM.key, tmp.file().getAbsoluteFile());
        params.put(CSVDataStoreFactory.STRATEGYP.key, CSVDataStoreFactory.GUESS_STRATEGY);
        CSVDataStore store = (CSVDataStore) DataStoreFinder.getDataStore(params);
        SimpleFeatureCollection collection = store.getFeatureSource().getFeatures();
        LOGGER.info("read in " + collection.size() + " features from CSV source");
        store.dispose();
        return collection;
    }

    @Override
    public void encode(Object value, OutputStream os) throws Exception {
        // will be deleted when the process finishes
        Resource tmp = resourceManager.getTemporaryResource(".csv");
        SimpleFeatureCollection collection = (SimpleFeatureCollection) value;
        HashMap<String, Object> params = new HashMap<>();
        params.put(CSVDataStoreFactory.FILE_PARAM.key, tmp.file().getAbsoluteFile());
        params.put(CSVDataStoreFactory.STRATEGYP.key, CSVDataStoreFactory.ATTRIBUTES_ONLY_STRATEGY);
        CSVDataStore store = (CSVDataStore) DataStoreFinder.getDataStore(params);
        store.createSchema(collection.getSchema());
        String name = store.getTypeName().getLocalPart();
        Transaction transaction = Transaction.AUTO_COMMIT;
        SimpleFeatureSource featureSource = store.getFeatureSource(name, transaction);
        if (featureSource instanceof FeatureStore) {
            CSVFeatureStore csvFeatureStore = (CSVFeatureStore) featureSource;

            csvFeatureStore.addFeatures(collection);
        }
        store.dispose();
        IOUtils.copy(tmp.in(), os);
    }
}
