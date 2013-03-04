package gsn.vsensor;


import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.beans.VSensorConfig;
import gsn.metadata.LSM.SensorMetaData;
import gsn.metadata.LSM.SensorObservation;
import gsn.utils.PropertiesReader;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import lsm.beans.Place;
import lsm.beans.Sensor;
import lsm.beans.User;
import lsm.server.LSMTripleStore;
import lsm.beans.Observation;
import lsm.beans.ObservedProperty;

import gsn.metadata.LSM.Repository;

public class LSMExporter extends AbstractVirtualSensor {
    private static final transient Logger logger = Logger.getLogger(LSMExporter.class);
    public static final String SENSOR_ID_PARAM_NAME = "sensorID";


    private String sensorIDonLSM = null;
    LSMTripleStore lsmStore = null;

    private long latest_ts = 0;
    private double latest_value = 0.0;

    private Map observations = new HashMap<String, SensorObservation>();
    private List<String> fields = new Vector<String>();
    private SensorMetaData metadata = new SensorMetaData();

    public boolean initialize() {

        VSensorConfig vsensor = getVirtualSensorConfiguration();
        TreeMap<String, String> params = vsensor.getMainClassInitialParams();
        metadata = Repository.createSensorMetaData(vsensor);

        // for each field in output structure
        for (int i = 0; i < vsensor.getOutputStructure().length; i++) {
            fields.add(vsensor.getOutputStructure()[i].getName());
            System.out.println(fields.get(i));
            observations.put(fields.get(i), Repository.createSensorObservation(vsensor, fields.get(i)));
        }

        sensorIDonLSM = params.get(SENSOR_ID_PARAM_NAME);

        return true;
    }

    public void dataAvailable(String inputStreamName, StreamElement data) {

        Long t = data.getTimeStamp();
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            SensorObservation s = (SensorObservation) observations.get(field);
            s.setValue((Double) data.getData(field));
            s.setTime(new Date(t / 1000));
            System.out.println(s);
            Repository.getInstance().publish((SensorObservation) observations.get(field), metadata);
        }

    }

    public void dispose() {

    }

}
