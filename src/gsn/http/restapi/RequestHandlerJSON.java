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
 * File: src/gsn/http/restapi/RequestHandlerJSON.java
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
import gsn.utils.geo.GridTools;
import org.apache.commons.collections.KeyValue;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Vector;

public class RequestHandlerJSON extends RequestHandler{

    private static transient Logger logger = Logger.getLogger(RequestHandlerJSON.class);

    @Override
    public RestResponse getAllSensors(User user) {

        if (Main.getContainerConfig().isAcEnabled() && (user == null)) {
            return errorResponse(ErrorType.NO_SUCH_USER, user, null);
        }

        RestResponse restResponse = new RestResponse();

        JSONArray sensorsInfo = new JSONArray();

        Iterator<VSensorConfig> vsIterator = Mappings.getAllVSensorConfigs();

        while (vsIterator.hasNext()) {

            JSONObject aSensor = new JSONObject();

            VSensorConfig sensorConfig = vsIterator.next();

            String vs_name = sensorConfig.getName();
            if (Main.getContainerConfig().isAcEnabled() && !user.hasReadAccessRight(vs_name) && !user.isAdmin() && DataSource.isVSManaged(vs_name)) {   // if you dont have access to this sensor
                continue;
            }

            aSensor.put("name", vs_name);

            JSONArray listOfFields = new JSONArray();

            for (DataField df : sensorConfig.getOutputStructure()) {

                String field_name = df.getName().toLowerCase();
                String field_type = df.getType().toLowerCase();
                String field_unit = df.getUnit();
                if (field_unit == null || field_unit.trim().length() == 0)
                    field_unit = "";

                if (field_type.indexOf("double") >= 0) {
                    //listOfFields.add(field_name);
                    JSONObject field = new JSONObject();
                    field.put("name", field_name);
                    field.put("unit", field_unit);
                    listOfFields.add(field);
                }
            }

            aSensor.put("fields", listOfFields);

            Double alt = 0.0;
            Double lat = 0.0;
            Double lon = 0.0;

            for (KeyValue df : sensorConfig.getAddressing()) {

                String adressing_key = df.getKey().toString().toLowerCase().trim();
                String adressing_value = df.getValue().toString().toLowerCase().trim();

                if (adressing_key.indexOf("altitude") >= 0)
                    alt = Double.parseDouble(adressing_value);

                if (adressing_key.indexOf("longitude") >= 0)
                    lon = Double.parseDouble(adressing_value);

                if (adressing_key.indexOf("latitude") >= 0)
                    lat = Double.parseDouble(adressing_value);
            }

            aSensor.put("lat", lat);
            aSensor.put("lon", lon);
            aSensor.put("alt", alt);

            sensorsInfo.add(aSensor);

        }

        restResponse.setResponse(sensorsInfo.toJSONString());
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);

        return restResponse;
    }

    //TODO
    @Override
    public RestResponse getMeasurementsForSensor(User user, String sensor, String from, String to, String size) {
        RestResponse restResponse = new RestResponse();
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);
        restResponse.setResponse("");
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
            return errorResponse(ErrorType.MALFORMED_DATE_FROM_TO, user, sensor);
        }

        errorFlag = !getData(sensor, field, fromAsLong, toAsLong, stream, timestamps);

        if (errorFlag) {
            return errorResponse(ErrorType.ERROR_IN_REQUEST, user, sensor);
        }

        //find unit for field
        Iterator< VSensorConfig > vsIterator = Mappings.getAllVSensorConfigs();
        String unit = "";
        boolean found = false;
        while ( vsIterator.hasNext( ) ) {
            VSensorConfig sensorConfig = vsIterator.next( );
            if (sensorConfig.getName().equalsIgnoreCase(sensor)){
                DataField[] dataFieldArray = sensorConfig.getOutputStructure();
                for (DataField df: dataFieldArray){
                    if (field.equalsIgnoreCase(df.getName())){
                        unit = df.getUnit();
                        if (unit == null || unit.trim().length() == 0){
                            unit = "";
                        }
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        }

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("sensor", sensor);
        jsonResponse.put("field", field);
        jsonResponse.put("unit", unit);
        jsonResponse.put("from", from);
        jsonResponse.put("to", to);
        JSONArray streamArray = new JSONArray();
        JSONArray timestampsArray = new JSONArray();
        JSONArray epochsArray = new JSONArray();
        for (int i = 0; i < stream.size(); i++) {
            streamArray.add(stream.get(i));
            epochsArray.add(timestamps.get(i));
            timestampsArray.add(new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamps.get(i))));
        }
        jsonResponse.put("timestamps", timestampsArray);
        jsonResponse.put("epochs", epochsArray);
        jsonResponse.put("values", streamArray);
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);
        restResponse.setResponse(jsonResponse.toJSONString());

        return restResponse;
    }

    //TODO Access Control
    @Override
    public RestResponse getPreviewMeasurementsForSensorField(User user, String sensor, String field, String from, String to, String size) {
        RestResponse restResponse = new RestResponse();

        Vector<Double> stream = new Vector<Double>();
        Vector<Long> timestamps = new Vector<Long>();

        boolean errorFlag = false;

        long n = -1;
        long fromAsLong = -1;
        long toAsLong = -1;

        if (size == null)
            n = DEFAULT_PREVIEW_SIZE;
        else
            try {
                n = Long.parseLong(size);
            } catch (NumberFormatException e) {
                logger.error(e.getMessage(), e);
            }

        if (n < 1) n = DEFAULT_PREVIEW_SIZE; // size should be strictly larger than 0

        if (from == null) { // no lower bound provided
            fromAsLong = getMinTimestampForSensorField(sensor, field);
        } else try {
            fromAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(from).getTime();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            errorFlag = true;
        }

        if (to == null) { // no lower bound provided
            toAsLong = getMaxTimestampForSensorField(sensor, field);
        } else try {
            toAsLong = new java.text.SimpleDateFormat(ISO_FORMAT).parse(to).getTime();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            errorFlag = true;
        }

        if (errorFlag) {
            return errorResponse(ErrorType.MALFORMED_DATE_FROM_TO, user, sensor);
        }

        errorFlag = !getDataPreview(sensor, field, fromAsLong, toAsLong, stream, timestamps, n);

        if (errorFlag) {
            return errorResponse(ErrorType.ERROR_IN_REQUEST, user, sensor);
        }

        //find unit for field
        Iterator< VSensorConfig > vsIterator = Mappings.getAllVSensorConfigs();
        String unit = "";
        boolean found = false;
        while ( vsIterator.hasNext( ) ) {
            VSensorConfig sensorConfig = vsIterator.next( );
            if (sensorConfig.getName().equalsIgnoreCase(sensor)){
                DataField[] dataFieldArray = sensorConfig.getOutputStructure();
                for (DataField df: dataFieldArray){
                    if (field.equalsIgnoreCase(df.getName())){
                        unit = df.getUnit();
                        if (unit == null || unit.trim().length() == 0){
                            unit = "";
                        }
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        }

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("sensor", sensor);
        jsonResponse.put("field", field);
        jsonResponse.put("unit", unit);
        jsonResponse.put("from", from);
        jsonResponse.put("to", to);
        JSONArray streamArray = new JSONArray();
        JSONArray timestampsArray = new JSONArray();
        JSONArray epochsArray = new JSONArray();
        for (int i = 0; i < stream.size(); i++) {
            streamArray.add(stream.get(i));
            timestampsArray.add(new java.text.SimpleDateFormat(ISO_FORMAT).format(new java.util.Date(timestamps.get(i))));
            epochsArray.add(timestamps.get(i));
        }
        jsonResponse.put("timestamps", timestampsArray);
        jsonResponse.put("values", streamArray);
        jsonResponse.put("epochs", epochsArray);
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);
        restResponse.setResponse(jsonResponse.toJSONString());

        return restResponse;
    }

    @Override
    public RestResponse getGridData(User user, String sensor, String date) {
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

        long timestamp = -1;
        try {
            timestamp = new java.text.SimpleDateFormat(ISO_FORMAT).parse(date).getTime();
        } catch (ParseException e) {
            logger.warn("Timestamp is badly formatted: " + date);
        }
        if (timestamp == -1) {
            return errorResponse(ErrorType.MALFORMED_DATE_DATE_FIELD, user, sensor);
        }

        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);
        restResponse.setResponse(GridTools.executeQueryForGridAsJSON(sensor, timestamp));

        logger.warn(restResponse.toString());
        return restResponse;
    }

    @Override
    public RestResponse errorResponse(ErrorType errorType, User user, String sensor) {

        RestResponse restResponse = new RestResponse();

        JSONObject jsonObject = new JSONObject();

        String errorMessage = "";

        switch (errorType){
            case NO_SUCH_SENSOR:
                errorMessage = String.format(ERROR_NO_SUCH_SENSOR_MSG, sensor);
                break;
            case NO_SUCH_USER:
                errorMessage = ERROR_NO_SUCH_USER_MSG;
                break;
            case NO_SENSOR_ACCESS:
                errorMessage = String.format(ERROR_NO_SENSOR_ACCESS_MSG, user.getUserName(), sensor);
                break;
            case UNKNOWN_REQUEST:
                errorMessage = ERROR_UNKNOWN_REQUEST_MSG;
                break;
            case MALFORMED_DATE_FROM_TO:
                errorMessage = ERROR_MALFORMED_DATE_FROM_TO_MSG;
                break;
            case MALFORMED_DATE_DATE_FIELD:
                errorMessage = ERROR_MALFORMED_DATE_DATE_FIELD_MSG;
                break;
            case ERROR_IN_REQUEST:
                errorMessage = ERROR_ERROR_IN_REQUEST_FILENAME;
                break;
        }

        jsonObject.put("error", errorMessage);

        restResponse.setResponse(jsonObject.toJSONString());
        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_ERROR);
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);

        return restResponse;
    }

    //without checking access rights
    public RestResponse getAllSensors() {

        RestResponse restResponse = new RestResponse();

        restResponse.setHttpStatus(RestResponse.HTTP_STATUS_OK);
        restResponse.setType(RestResponse.JSON_CONTENT_TYPE);

        JSONArray sensorsInfo = new JSONArray();

        Iterator<VSensorConfig> vsIterator = Mappings.getAllVSensorConfigs();

        while (vsIterator.hasNext()) {

            JSONObject aSensor = new JSONObject();

            VSensorConfig sensorConfig = vsIterator.next();

            String vs_name = sensorConfig.getName();

            aSensor.put("name", vs_name);

            JSONArray listOfFields = new JSONArray();

            for (DataField df : sensorConfig.getOutputStructure()) {

                String field_name = df.getName().toLowerCase();
                String field_type = df.getType().toLowerCase();
                String field_unit = df.getUnit();
                if (field_unit == null || field_unit.trim().length() == 0)
                    field_unit = "";

                if (field_type.indexOf("double") >= 0) {
                    //listOfFields.add(field_name);
                    JSONObject field = new JSONObject();
                    field.put("name", field_name);
                    field.put("unit", field_unit);
                    listOfFields.add(field);
                }
            }

            aSensor.put("fields", listOfFields);

            Double alt = 0.0;
            Double lat = 0.0;
            Double lon = 0.0;

            for (KeyValue df : sensorConfig.getAddressing()) {

                String adressing_key = df.getKey().toString().toLowerCase().trim();
                String adressing_value = df.getValue().toString().toLowerCase().trim();

                if (adressing_key.indexOf("altitude") >= 0)
                    alt = Double.parseDouble(adressing_value);

                if (adressing_key.indexOf("longitude") >= 0)
                    lon = Double.parseDouble(adressing_value);

                if (adressing_key.indexOf("latitude") >= 0)
                    lat = Double.parseDouble(adressing_value);
            }

            aSensor.put("lat", lat);
            aSensor.put("lon", lon);
            aSensor.put("alt", alt);

            sensorsInfo.add(aSensor);

        }
        restResponse.setResponse(sensorsInfo.toJSONString());

        return restResponse;
    }
}

