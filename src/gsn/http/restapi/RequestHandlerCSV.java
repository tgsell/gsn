/**
 * Global Sensor Networks (GSN) Source Code
 * Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
 *
 * This file is part of GSN.
 *
 * GSN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GSN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GSN.  If not, see <http://www.gnu.org/licenses/>.
 *
 * File: src/gsn/http/restapi/RequestHandlerCSV.java
 *
 * @author Sofiane Sarni
 * @author Ivo Dimitrov
 * @author Milos Stojanovic
 * @author Jean-Paul Calbimonte
 *
 */

package gsn.http.restapi;

import gsn.Main;
import gsn.Mappings;
import gsn.beans.DataField;
import gsn.beans.VSensorConfig;
import gsn.http.ac.DataSource;
import gsn.http.ac.User;
import org.apache.commons.collections.KeyValue;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

public class RequestHandlerCSV extends RequestHandler{

    private static transient Logger logger = Logger.getLogger(RequestHandlerCSV.class);

    private static final String EXTENSION = ".csv";

    @Override
    public RestResponse getAllSensors(User user) {
        if ( Main.getContainerConfig().isAcEnabled() && (user == null) )  {
            return errorResponse(ErrorType.NO_SUCH_USER, user, null);
        }

        RestResponse restResponse = new RestResponse();

        String filename = String.format(FILENAME_MULTIPLE_SENSORS, datetime);

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        StringBuilder output = new StringBuilder("");
        int is_public;
        Iterator<VSensorConfig> vsIterator = Mappings.getAllVSensorConfigs();
        output.append("# is_public == 1 if the VS is publicly accessed and 0, otherwise\n");

        while (vsIterator.hasNext()) {

            VSensorConfig sensorConfig = vsIterator.next();

            String vs_name = sensorConfig.getName();
            if (Main.getContainerConfig().isAcEnabled() && !user.hasReadAccessRight(vs_name) && !user.isAdmin() && DataSource.isVSManaged(vs_name)) {   // if you dont have access to this sensor
                continue;
            }

            output.append("# vsname:"+vs_name+"\n");
            is_public = (DataSource.isVSManaged(vs_name)) ? 0 : 1;
            output.append("# is_public:" + is_public + "\n");
            for ( KeyValue df : sensorConfig.getAddressing()){
                output.append("# " + df.getKey().toString().toLowerCase().trim() + ":" + df.getValue().toString().trim() + "\n");
            }

            String fieldNames = "# fields:time,timestamp,";
            String fieldUnits = "# units:,,";
            String fieldTypes = "# types:string,long,";
            boolean  first = true;
            for (DataField df : sensorConfig.getOutputStructure()) {
                String field_name = df.getName().toLowerCase();
                String field_type = df.getType().toLowerCase();
                String field_unit = df.getUnit();
                if (field_unit == null || field_unit.trim().length() == 0)
                    field_unit = "";
                if (first) {
                    fieldNames += field_name;
                    fieldTypes += field_type;
                    fieldUnits += field_unit;

                    first = false;
                } else {
                    fieldNames += ","+field_name;
                    fieldTypes += ","+field_type;
                    fieldUnits += ","+field_unit;
                }

            }
            output.append(fieldNames + "\n");
            output.append(fieldUnits + "\n");
            output.append(fieldTypes + "\n");
        }

        restResponse.setResponse(output.toString());

        return restResponse;
    }

    @Override
    public RestResponse getMeasurementsForSensor(User user, String sensor, String from, String to, String size) {
        if (Mappings.getConfig(sensor) == null ) {
            return errorResponse(ErrorType.NO_SUCH_SENSOR, user, sensor);
        }
        if (Main.getContainerConfig().isAcEnabled() && (user == null)) {
            return errorResponse(ErrorType.NO_SUCH_USER, user, sensor);
        }
        if (Main.getContainerConfig().isAcEnabled() && !user.hasReadAccessRight(sensor) && !user.isAdmin() && DataSource.isVSManaged(sensor)) {
            return errorResponse(ErrorType.NO_SENSOR_ACCESS, user, sensor);
        }

        RestResponse restResponse = new RestResponse();

        String filename = String.format(FILENAME_SENSOR_FIELDS, sensor, datetime);

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        StringBuilder output = new StringBuilder("");

        boolean errorFlag = false;

        long fromAsLong = 0;
        long toAsLong = 0;
        try {
            fromAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(from).getTime();
            toAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(to).getTime();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            errorFlag = true;
        }

        if (errorFlag) {
            restResponse = errorResponse(ErrorType.MALFORMED_DATE_FROM_TO, user, sensor);
            restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
            restResponse.setHttpStatus(RestResponse.HTTP_STATUS_BAD_REQUEST);
            return restResponse;
        }

     /*   output.append("## sensor: " + sensor+"\n");
        output.append("## field, value, timestamp, epoch\n");
        output.append(+"\n");              */
        Vector<Double> stream = new Vector<Double>();
        Vector<Long> timestamps = new Vector<Long>();
        ArrayList<Vector<Double>> elements  = new ArrayList<Vector<Double>>();;
        VSensorConfig sensorConfig = Mappings.getConfig(sensor);    // get the configuration for this vs
        ArrayList<String> fields = new ArrayList<String>();
        ArrayList<String> units = new ArrayList<String>();


        for (DataField df : sensorConfig.getOutputStructure()) {
            fields.add(df.getName().toLowerCase());   // get the field name that is going to be processed
            String unit = df.getUnit();
            if (unit == null || unit.trim().length() == 0)
                unit = "";
            units.add(unit);
        }

        output.append("# vsname:" + sensor + "\n");
        for ( KeyValue df : sensorConfig.getAddressing()){
            output.append("# " + df.getKey().toString().toLowerCase().trim() + ":" + df.getValue().toString().trim() + "\n");
        }

        output.append("# fields:time,timestamp");
        int j;
        for (j=0; j < (fields.size()-1); j++) {
            output.append("," + fields.get(j));
        }
        output.append("," + fields.get(j) + "\n");

        //units (second line)
        output.append("# units:,");
        for (j=0; j < (fields.size()-1); j++) {
            output.append("," + units.get(j));
        }
        output.append("," + units.get(j) + "\n");
        ///////////////////////   Connection to the DB to get the data

        Connection conn = null;
        ResultSet resultSet = null;
        boolean restrict = false;

        if (size != null)  {
            restrict = true;
        }

        try {
            conn = Main.getStorage(sensor).getConnection();

            StringBuilder query;
            if (restrict) {
                Integer window = new Integer(size);
                query = new StringBuilder("select * from ")
                        .append(sensor)
                        .append(" where timed >= ")
                        .append(fromAsLong)
                        .append(" and timed <=")
                        .append(toAsLong)
                        .append(" order by timed desc")
                        .append(" limit 0,"+(window+1));
            } else {
                query = new StringBuilder("select * from ")
                        .append(sensor)
                        .append(" where timed >= ")
                        .append(fromAsLong)
                        .append(" and timed <=")
                        .append(toAsLong);
            }
            resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);
            while (resultSet.next()) {
                if (restrict) {
                    Vector<Double> stream2 = new Vector<Double>();
                    timestamps.add(resultSet.getLong("timed"));
                    for (String fieldname : fields) {
                        stream2.add(getDouble(resultSet,fieldname));
                    }
                    elements.add(stream2);
                } else {
                    long timestamp = resultSet.getLong("timed");

                    output.append((new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamp))).toString().replace('T', ' ') + "," + timestamp);
                    for (String fieldname : fields) {
                        stream.add(getDouble(resultSet,fieldname));
                    }
                    for (int i = 0; i < stream.size(); i++) {
                        output.append("," + stream.get(i));
                    }
                    output.append("\n");
                    stream.clear();
                }
            }
            if (restrict) {
                for (int k = elements.size()-1; k > 0; k--) {       // for each one of the results
                    Vector<Double> streamTemp = elements.get(k);
                    output.append((new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamps.get(k)))).toString().replace('T', ' ') + "," + timestamps.get(k));
                    for (int i = 0; i < streamTemp.size(); i++)  {
                        output.append("," + streamTemp.get(i));
                    }
                    output.append("\n");
                }

            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } finally {
            Main.getStorage(sensor).close(resultSet);
            Main.getStorage(sensor).close(conn);
        }

        restResponse.setResponse(output.toString());

        return restResponse;
    }

    @Override
    public RestResponse getMeasurementsForSensorField(User user, String sensor, String field, String from, String to, String size) {
        if ( Mappings.getConfig(sensor) == null ) {
            return errorResponse(ErrorType.NO_SUCH_SENSOR, user, sensor);
        }
        if (Main.getContainerConfig().isAcEnabled() && (user == null)) {
            return errorResponse(ErrorType.NO_SUCH_USER, user, sensor);
        }
        if (Main.getContainerConfig().isAcEnabled() && !user.hasReadAccessRight(sensor) && !user.isAdmin() && DataSource.isVSManaged(sensor)) {
            return errorResponse(ErrorType.NO_SENSOR_ACCESS, user, sensor);
        }

        RestResponse restResponse = new RestResponse();
        String filename = String.format(FILENAME_SENSOR_FIELD, sensor, field, datetime);

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        StringBuilder output = new StringBuilder("");

        Vector<Double> stream = new Vector<Double>();
        Vector<Long> timestamps = new Vector<Long>();

        boolean errorFlag = false;

        long fromAsLong = 0;
        long toAsLong = 0;
        try {
            fromAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(from).getTime();
            toAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(to).getTime();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            errorFlag = true;
        }

        if (errorFlag) {
            restResponse = errorResponse(ErrorType.MALFORMED_DATE_FROM_TO, user, sensor);
            restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
            restResponse.setHttpStatus(RestResponse.HTTP_STATUS_BAD_REQUEST);
            return restResponse;
        }

        VSensorConfig sensorConfig = Mappings.getConfig(sensor);
        output.append("# vsname:" + sensor + "\n");
        for ( KeyValue df : sensorConfig.getAddressing()){
            output.append("# " + df.getKey().toString().toLowerCase().trim() + ":" + df.getValue().toString().trim() + "\n");
        }
        output.append("# fields:time,timestamp,"+field+"\n");
        DataField[] dataFieldArray = sensorConfig.getOutputStructure();
        for (DataField df: dataFieldArray){
            if (field.equalsIgnoreCase(df.getName())){
                String unit = df.getUnit();
                if (unit == null || unit.trim().length() == 0){
                    unit = "";
                }
                output.append("# units:,," + unit + "\n");
                break;
            }
        }


        if (size != null)  {
            Integer window = new Integer(size);
            ///////////
            Connection conn = null;
            ResultSet resultSet = null;

            try {
                conn = Main.getStorage(sensor).getConnection();
                StringBuilder query = new StringBuilder("select timed, ")
                        .append(field)
                        .append(" from ")
                        .append(sensor)
                        .append(" where timed >= ")
                        .append(fromAsLong)
                        .append(" and timed <=")
                        .append(toAsLong)
                        .append(" order by timed desc")
                        .append(" limit 0,"+(window+1));

                resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);

                while (resultSet.next()) {
                    timestamps.add(resultSet.getLong(1));
                    stream.add(getDouble(resultSet,field));
                    /*long timestamp = resultSet.getLong(1);
                    output.append(resultSet.getDouble(2)+","+(new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamp))).toString().replace('T', ' ')+","+timestamp);
*/
                }
                for (int i=stream.size()-1; i > 0; i--) {
                    long timestamp = timestamps.get(i);
                    output.append((new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamp))).toString().replace('T', ' ') + "," + timestamp + "," + stream.get(i) + "\n");
                }
                timestamps.clear();stream.clear();
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            } finally {
                Main.getStorage(sensor).close(resultSet);
                Main.getStorage(sensor).close(conn);
            }
            ///////////
        } else {
            errorFlag = !getData(sensor, field, fromAsLong, toAsLong, stream, timestamps);
                 /*   output.append("##vsname: "+sensor);
        output.append("##field: "+field);
        output.append("##value,timestamp,epoch");   */
            for (int i = 0; i < stream.size(); i++) {
                output.append((new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamps.get(i)))).toString().replace('T', ' ') + "," + timestamps.get(i) + "," + stream.get(i) + "\n");
            }
        }

        if (errorFlag) {
            restResponse = errorResponse(ErrorType.ERROR_IN_REQUEST, user, sensor);
            restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
            restResponse.setHttpStatus(RestResponse.HTTP_STATUS_BAD_REQUEST);
            return restResponse;
        }

        restResponse.setResponse(output.toString());

        return restResponse;
    }

    //TODO
    @Override
    public RestResponse getPreviewMeasurementsForSensorField(User user, String sensor, String field, String from, String to, String size) {
        RestResponse restResponse = new RestResponse();
        String filename = String.format(FILENAME_PREVIEW_SENSOR_FIELD, sensor, field, datetime);

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        StringBuilder output = new StringBuilder("");

        restResponse.setResponse(output.toString());

        return restResponse;
    }

    //TODO
    @Override
    public RestResponse getGridData(User user, String sensor, String date) {
        RestResponse restResponse = new RestResponse();
        String filename = String.format(FILENAME_GRID_DATA, sensor, datetime);

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + EXTENSION));
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        StringBuilder output = new StringBuilder("");

        restResponse.setResponse(output.toString());

        return restResponse;
    }

    @Override
    public RestResponse errorResponse(ErrorType errorType, User user, String sensor) {

        RestResponse restResponse = new RestResponse();

        String errorMessage = "";
        String filename = "";
        String extension = ".csv";

        switch (errorType){
            case NO_SUCH_SENSOR:
                errorMessage = String.format(ERROR_NO_SUCH_SENSOR_MSG, sensor);
                filename = ERROR_NO_SUCH_SENSOR_FILENAME;
                break;
            case NO_SUCH_USER:
                errorMessage = ERROR_NO_SUCH_USER_MSG;
                filename = ERROR_NO_SUCH_USER_FILENAME;
                break;
            case NO_SENSOR_ACCESS:
                errorMessage = String.format(ERROR_NO_SENSOR_ACCESS_MSG, user.getUserName(), sensor);
                filename = ERROR_NO_SENSOR_ACCESS_FILENAME;
                break;
            case UNKNOWN_REQUEST:
                errorMessage = ERROR_UNKNOWN_REQUEST_MSG;
                filename = ERROR_UNKNOWN_REQUEST_FILENAME;
                break;
            case MALFORMED_DATE_FROM_TO:
                errorMessage = ERROR_MALFORMED_DATE_FROM_TO_MSG;
                break;
            case MALFORMED_DATE_DATE_FIELD:
                errorMessage = ERROR_MALFORMED_DATE_DATE_FIELD_MSG;
                break;
            case ERROR_IN_REQUEST:
                errorMessage = ERROR_ERROR_IN_REQUEST_MSG;
                break;
        }

        errorMessage = "# " + errorMessage;

        restResponse.setType(RestResponse.CSV_CONTENT_TYPE);
        restResponse.addHeader(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_NAME, String.format(RestResponse.RESPONSE_HEADER_CONTENT_DISPOSITION_VALUE, filename + extension));
        restResponse.setResponse(errorMessage);
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_ERROR);

        return restResponse;
    }
}
