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
 * File: src/gsn/http/restapi/RequestHandler.java
 *
 * @author Sofiane Sarni
 * @author Ivo Dimitrov
 * @author Milos Stojanovic
 * @author Jean-Paul Calbimonte
 *
 */

package gsn.http.restapi;

import gsn.Main;
import gsn.http.ac.User;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

public abstract class RequestHandler {

    private static transient Logger logger = Logger.getLogger(RequestHandlerCSV.class);

    protected static final String ERROR_NO_SUCH_SENSOR_FILENAME = "error_no_such_sensor";
    protected static final String ERROR_NO_SUCH_SENSOR_MSG = "The virtual sensor %s doesn't exist in GSN!";
    protected static final String ERROR_NO_SUCH_USER_FILENAME = "error_no_user";
    protected static final String ERROR_NO_SUCH_USER_MSG = "There is no user with the provided username and password!";
    protected static final String ERROR_NO_SENSOR_ACCESS_FILENAME = "error_no_sensor_access";
    protected static final String ERROR_NO_SENSOR_ACCESS_MSG = "The user %s doesn't have access to the sensor %s!";
    protected static final String ERROR_UNKNOWN_REQUEST_FILENAME = "error_unknown_request";
    protected static final String ERROR_UNKNOWN_REQUEST_MSG = "Cannot interpret request!";
    protected static final String ERROR_ERROR_IN_REQUEST_FILENAME = "error_in_request";
    protected static final String ERROR_ERROR_IN_REQUEST_MSG = "Error in request!";
    protected static final String ERROR_MALFORMED_DATE_FROM_TO_FILENAME = "error_malformed_date";
    protected static final String ERROR_MALFORMED_DATE_FROM_TO_MSG = "Malformed date for 'from' or 'to' field!";
    protected static final String ERROR_MALFORMED_DATE_DATE_FIELD_FILENAME = "error_malformed_date";
    protected static final String ERROR_MALFORMED_DATE_DATE_FIELD_MSG = "Malformed date for 'date' field!";


    protected static final String FILENAME_MULTIPLE_SENSORS = "multiple_sensors_%s";
    protected static final String FILENAME_SENSOR_FIELDS = "sensor_%s_fields_%s";
    protected static final String FILENAME_SENSOR_FIELD = "sensor_%s_field_%s_%s";
    protected static final String FILENAME_PREVIEW_SENSOR_FIELD = "preview_sensor_%s_fields_%s_%s";
    protected static final String FILENAME_GRID_DATA = "sensor_%s_grid_data_%s";

    public static enum ErrorType {NO_SUCH_SENSOR, NO_SUCH_USER, NO_SENSOR_ACCESS, UNKNOWN_REQUEST, MALFORMED_DATE_FROM_TO, MALFORMED_DATE_DATE_FIELD, ERROR_IN_REQUEST}

    public abstract RestResponse getAllSensors(User user);
    public abstract RestResponse getMeasurementsForSensor(User user, String sensor, String from, String to, String size);
    public abstract RestResponse getMeasurementsForSensorField(User user, String sensor, String field, String from, String to, String size);
    public abstract RestResponse getPreviewMeasurementsForSensorField(User user, String sensor, String field, String from, String to, String size);
    public abstract RestResponse getGridData(User user, String sensor, String date);

    public abstract RestResponse errorResponse(ErrorType errorType, User user, String sensor);

    protected String datetime;

    private static final String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";
    protected static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    protected static final long DEFAULT_PREVIEW_SIZE = 1000;

    public RequestHandler () {
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        Date currentDate = Calendar.getInstance().getTime();
        datetime = dateFormat.format(currentDate);
    }

    protected boolean getData(String sensor, String field, long from, long to, Vector<Double> stream, Vector<Long> timestamps) {
        Connection conn = null;
        ResultSet resultSet = null;

        boolean result = true;

        try {
            conn = Main.getStorage(sensor).getConnection();
            StringBuilder query = new StringBuilder("select timed, ")
                    .append(field)
                    .append(" from ")
                    .append(sensor)
                    .append(" where timed >= ")
                    .append(from)
                    .append(" and timed<=")
                    .append(to);

            resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);

            while (resultSet.next()) {
                //int ncols = resultSet.getMetaData().getColumnCount();
                long timestamp = resultSet.getLong(1);
                Double value = getDouble(resultSet,field);
                stream.add(value);
                timestamps.add(timestamp);
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            result = false;
        } finally {
            Main.getStorage(sensor).close(resultSet);
            Main.getStorage(sensor).close(conn);
        }

        return result;
    }

    protected boolean getDataPreview(String sensor, String field, long from, long to, Vector<Double> stream, Vector<Long> timestamps, long size) {
        Connection conn = null;
        ResultSet resultSet = null;

        boolean result = true;

        long skip = getTableSize(sensor) / size;

        /*
        logger.warn("skip = " + skip);
        logger.warn("size = " + size);
        logger.warn("getTableSize(sensor) = " + getTableSize(sensor));
        */

        try {
            conn = Main.getStorage(sensor).getConnection();
            StringBuilder query = new StringBuilder("select timed, ")
                    .append(field)
                    .append(" from ")
                    .append(sensor);
            if (skip > 1)
                query.append(" where mod(pk,")
                        .append(skip)
                        .append(")=1");

            resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);

            while (resultSet.next()) {
                //int ncols = resultSet.getMetaData().getColumnCount();
                long timestamp = resultSet.getLong(1);
                double value = resultSet.getDouble(2);
                //logger.warn(ncols + " cols, value: " + value + " ts: " + timestamp);
                stream.add(value);
                timestamps.add(timestamp);
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            result = false;
        } finally {
            Main.getStorage(sensor).close(resultSet);
            Main.getStorage(sensor).close(conn);
        }

        return result;
    }

    protected long getTableSize(String sensor) {
        Connection conn = null;
        ResultSet resultSet = null;

        boolean result = true;
        long timestamp = -1;

        try {
            conn = Main.getDefaultStorage().getConnection();
            StringBuilder query = new StringBuilder("select count(*) from ").append(sensor);

            resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);

            if (resultSet.next()) {

                timestamp = resultSet.getLong(1);
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            result = false;
        } finally {
            Main.getStorage(sensor).close(resultSet);
            Main.getStorage(sensor).close(conn);
        }

        return timestamp;
    }

    protected Double getDouble(ResultSet rs,String fieldName) throws SQLException{
        Double d=rs.getDouble(fieldName);
        if (rs.wasNull()) return null;
            //if (o!=null) return rs.getDouble(fieldName);
        else return d;
    }

    protected long getMinTimestampForSensorField(String sensor, String field) {
        return getTimestampBoundForSensorField(sensor, field, "min");
    }

    protected long getMaxTimestampForSensorField(String sensor, String field) {
        return getTimestampBoundForSensorField(sensor, field, "max");
    }

    protected long getTimestampBoundForSensorField(String sensor, String field, String boundType) {
        Connection conn = null;
        ResultSet resultSet = null;

        boolean result = true;
        long timestamp = -1;

        try {
            conn = Main.getDefaultStorage().getConnection();
            StringBuilder query = new StringBuilder("select ").append(boundType).append("(timed) from ").append(sensor);

            resultSet = Main.getStorage(sensor).executeQueryWithResultSet(query, conn);

            if (resultSet.next()) {

                timestamp = resultSet.getLong(1);
            }

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            result = false;
        } finally {
            Main.getStorage(sensor).close(resultSet);
            Main.getStorage(sensor).close(conn);
        }

        return timestamp;
    }
}
