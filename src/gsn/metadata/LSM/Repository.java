package gsn.metadata.LSM;

import gsn.beans.VSensorConfig;
import gsn.utils.PropertiesReader;
import lsm.beans.*;
import lsm.server.LSMTripleStore;
import org.apache.log4j.Logger;

import java.util.Date;

public class Repository {

    private static final transient Logger logger = Logger.getLogger(Repository.class);
    public static final String LSM_CONFIG_PROPERTIES_FILE = "conf/lsm_config.properties";
    public static final String METADATA_FILE_SUFFIX = ".metadata";
    LSMTripleStore lsmStore = null;
    User user;

    private static Repository singleton;

    private Repository() {
        /*
        * Set sensor's author
        * If you don't have LSM account, please visit LSM Home page (http://lsm.deri.ie) to sign up
        */
        User user = new User();
        user.setUsername(PropertiesReader.readProperty(LSM_CONFIG_PROPERTIES_FILE, "username"));
        user.setPass(PropertiesReader.readProperty(LSM_CONFIG_PROPERTIES_FILE, "password"));
    }

    public static synchronized Repository getInstance() {
        if (singleton == null)
            try {
                singleton = new Repository();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        return singleton;
    }

    public static String generateMetaDataFileName(VSensorConfig vs) {
        return vs.getFileName() + METADATA_FILE_SUFFIX;
    }

    public static SensorMetaData createSensorMetaData(VSensorConfig vs) {
        String metadataFile = generateMetaDataFileName(vs);
        SensorMetaData s = new SensorMetaData();
        s.initFromFile(metadataFile);
        return s;
    }

    public static Sensor createLSMSensor(VSensorConfig vs) {
        SensorMetaData s = createSensorMetaData(vs);

        System.out.println("*************  NOW publishing *****" + "\n" + vs);
        System.out.println(s);

        Sensor sensor = new Sensor();
        sensor.setName(s.getSensorName());
        sensor.setAuthor(s.getAuthor());
        sensor.setSensorType(s.getSensorType());
        sensor.setSourceType(s.getSourceType());
        sensor.setInfor(s.getInformation());
        sensor.setSource(s.getSource());
        sensor.setTimes(new Date());

        Place place = new Place();
        place.setLat(vs.getLatitude());
        place.setLng(vs.getLongitude());
        sensor.setPlace(place);

        return sensor;
    }

    public void announceSensor(VSensorConfig vs) {

        Sensor sensor = createLSMSensor(vs);

        /*
        // create LSMTripleStore instance
        LSMTripleStore lsmStore = new LSMTripleStore();

        //set user information for authentication
        lsmStore.setUser(user);

        //call sensorAdd method
        lsmStore.sensorAdd(sensor);
        */
    }

    public static SensorObservation createSensorObservation(VSensorConfig vs, String field) {
        String fileName = generateMetaDataFileName(vs);
        SensorObservation o = new SensorObservation();
        o.setPropertyName(PropertiesReader.readProperty(fileName, "observation." + field + "." + "propertyName"));
        o.setUnit(PropertiesReader.readProperty(fileName, "observation." + field + "." + "unit"));
        System.out.println(o.toString());
        return o;
    }

    public void publish(SensorObservation observation, SensorMetaData sensorMetaData) {
        //create an Observation object
        Observation obs = new Observation();

        // set SensorURL of observation
        Sensor sensor2 = lsmStore.getSensorById(sensorMetaData.getSensorID());
        obs.setSensor(sensor2.getId());
        //set time when the observation was observed. In this example, the time is current local time.
        obs.setTimes(observation.getTime());

        ObservedProperty obvTem = new ObservedProperty();
        obvTem.setObservationId(obs.getId());
        obvTem.setPropertyName(observation.getPropertyName());
        obvTem.setValue(observation.getValue());
        obvTem.setUnit(observation.getUnit());
        obs.addReading(obvTem);
        lsmStore.sensorDataUpdate(obs);
    }


}
