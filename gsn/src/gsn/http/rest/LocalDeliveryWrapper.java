package gsn.http.rest;

import gsn.Main;
import gsn.DataDistributer;
import gsn.Mappings;
import gsn.VirtualSensorInitializationFailedException;
import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.InputInfo;
import gsn.beans.StreamElement;
import gsn.beans.VSensorConfig;
import gsn.storage.SQLUtils;
import gsn.storage.SQLValidator;
import gsn.utils.Helpers;
import gsn.vsensor.AbstractVirtualSensor;
import gsn.wrappers.AbstractWrapper;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Date;

import org.apache.log4j.Logger;
import org.joda.time.format.ISODateTimeFormat;

public class LocalDeliveryWrapper extends AbstractWrapper implements DeliverySystem{

	private  final String CURRENT_TIME = ISODateTimeFormat.dateTime().print(System.currentTimeMillis());
	
	private static transient Logger                  logger           = Logger.getLogger( LocalDeliveryWrapper.class );
	
	private VSensorConfig vSensorConfig;
	
	private String vsName;
	
	public VSensorConfig getVSensorConfig() {
		return vSensorConfig;
	}
	
	private DataField[] structure;
	
	private DefaultDistributionRequest distributionRequest;

	public String getWrapperName() {
		return "Local-wrapper";
	}

	public boolean initialize() {
		AddressBean params = getActiveAddressBean( );
		String query = params.getPredicateValue("query");
		
		vsName = params.getPredicateValue( "name" );
		String startTime = params.getPredicateValueWithDefault("start-time",CURRENT_TIME );

		if (query==null && vsName == null) {
			logger.error("For using local-wrapper, either >query< or >name< parameters should be specified"); 
			return false;
		}

		if (query == null) 
			query = "select * from "+vsName;

		
		long lastVisited = -1;
		boolean continuous = false;
		Connection conn = null;
		ResultSet rs = null;
		if (startTime.equals("continue")) {
			continuous = true;
			try {
				conn = Main.getStorage(params.getVirtualSensorName()).getConnection();
				
				rs = conn.getMetaData().getTables(null, null, params.getVirtualSensorName(), new String[] {"TABLE"});
				if (rs.next()) {
					StringBuilder dbquery = new StringBuilder();
					dbquery.append("select max(timed) from ").append(params.getVirtualSensorName());
					Main.getStorage(params.getVirtualSensorName()).close(rs);

					rs = Main.getStorage(params.getVirtualSensorName()).executeQueryWithResultSet(dbquery, conn);
					if (rs.next()) {
						lastVisited = rs.getLong(1);
					}
				}
			} catch (SQLException e) {
				logger.error(e.getMessage(), e);
			} finally {
				Main.getStorage(params.getVirtualSensorName()).close(rs);
				Main.getStorage(params.getVirtualSensorName()).close(conn);
			}
		} else if (startTime.startsWith("-")) {
			try {
				lastVisited = System.currentTimeMillis() - Long.parseLong(startTime.substring(1));
			} catch (NumberFormatException e) {
				logger.error("Problem in parsing the start-time parameter, the provided value is: " + startTime);
				logger.error(e.getMessage(), e);
				return false;				
			}
		} else {
			try {
				lastVisited = Helpers.convertTimeFromIsoToLong(startTime);
			} catch (Exception e) {
				logger.error("Problem in parsing the start-time parameter, the provided value is:"+startTime+" while a valid input is:"+CURRENT_TIME);
				logger.error(e.getMessage(),e);
				return false;
			}
		}

		try {
			vsName = SQLValidator.getInstance().validateQuery(query);
			if(vsName==null) //while the other instance is not loaded.
				return false;
			
			if (startTime.equals("continue")){
				try {
					conn = Main.getStorage(vsName).getConnection();
					
					rs = conn.getMetaData().getTables(null, null, vsName, new String[] {"TABLE"});
					if (rs.next()) {
						StringBuilder dbquery = new StringBuilder();
						dbquery.append("select max(timed) from ").append(vsName);
						Main.getStorage(vsName).close(rs);

						rs = Main.getStorage(vsName).executeQueryWithResultSet(dbquery, conn);
						if (rs.next()) {
							long t = rs.getLong(1);
							if (lastVisited > t) {
								lastVisited = t;
								logger.info("newest timed from " + vsName + " is older than requested start time -> using timed as start time");
							}
						}
					}
				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				} finally {
					Main.getStorage(vsName).close(rs);
					Main.getStorage(vsName).close(conn);
				}
			}
			if (logger.isDebugEnabled())
				logger.debug("lastVisited=" + String.valueOf(lastVisited));
			
			query = SQLUtils.newRewrite(query, vsName, vsName.toLowerCase()).toString();

			if (logger.isDebugEnabled())
				logger.debug("Local wrapper request received for: "+vsName);
			
			vSensorConfig = Mappings.getConfig(vsName);
			distributionRequest = DefaultDistributionRequest.create(this, vSensorConfig, query, lastVisited, continuous);
			// This call MUST be executed before adding this listener to the data-distributer because distributer checks the isClose method before flushing.
		}catch (Exception e) {
			logger.error("Problem in the query parameter of the local-wrapper.");
			logger.error(e.getMessage(),e);
			return false;
		}
		return true;
	}

	public InputInfo sendToWrapper ( String action,String[] paramNames, Serializable[] paramValues ) {
		AbstractVirtualSensor vs;
		try {
			vs = Mappings.getVSensorInstanceByVSName( vSensorConfig.getName( ) ).borrowVS( );
		} catch ( VirtualSensorInitializationFailedException e ) {
			logger.warn("Sending data back to the source virtual sensor failed !: "+e.getMessage( ),e);
			return new InputInfo("LocalDeliveryWrapper", "Sending data back to the source virtual sensor failed !: "+e.getMessage(), false);
		}
		InputInfo toReturn = vs.dataFromWeb( action , paramNames , paramValues );
		Mappings.getVSensorInstanceByVSName( vSensorConfig.getName( ) ).returnVS( vs );
		return toReturn;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LocalDistributionReq => [" ).append(distributionRequest.getQuery()).append(", Start-Time: ").append(new Date(distributionRequest.getStartTime())).append("]");
		return sb.toString();
	}
	
	public void run() {
		DataDistributer localDistributer = DataDistributer.getInstance(LocalDeliveryWrapper.class);
		localDistributer.addListener(this.distributionRequest);
	}

	public void writeStructure(DataField[] fields) throws IOException {
		this.structure=fields;
		
	}
	
	public DataField[] getOutputFormat() {
		return structure;
	}

	public void close() {
		logger.warn("Closing a local delivery.");
		try {
			releaseResources();
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		
	}

	public boolean isClosed() {
		return !isActive();
	}

	public boolean writeStreamElement(StreamElement se) {
		if (getActiveAddressBean().getVirtualSensorConfig().isProducingStatistics())
			inputEvent(vsName, se.getVolume());
		boolean isSucced = postStreamElement(se);
		if (logger.isDebugEnabled())
			logger.debug("wants to deliver stream element:"+ se.toString()+ "["+isSucced+"]");
		return true;
	}

    public boolean writeKeepAliveStreamElement() {
        return true;
    }

    public void dispose() {
		
	}

	public void setTimeout(long timeoutMs) {
	}

	@Override
	public String getUser() {
		return "local wrapper";
	}
}

 	  	 
