// ----------------------------------------------------------------------------
// Copyright 2007-2015, GeoTelematic Solutions, Inc.
// All rights reserved
// ----------------------------------------------------------------------------
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
//  Device Communication Server:
//  SHENZHEN COBAN ELECTRONICS CO., LIMITED
//  Website: http://www.coban.net
// ----------------------------------------------------------------------------
//  Support Camera (Mode UDP is better)
// ----------------------------------------------------------------------------
//  Author:
//  Carlos Gonzalez
// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------
package org.opengts.servers.coban;

import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.servers.*;
import org.opengts.servers.coban.*;

import org.opengts.cellid.CellTower;

public class TrackClientPacketHandler
    extends AbstractClientPacketHandler
{

    public static       boolean DEBUG_MODE                  = false;

    // ------------------------------------------------------------------------

    public static       String  UNIQUEID_PREFIX[]           = null;
    public static       double  MINIMUM_SPEED_KPH           = Constants.MINIMUM_SPEED_KPH;
    public static       boolean ESTIMATE_ODOMETER           = true;
    public static       boolean SIMEVENT_GEOZONES           = true;
    public static       boolean SIMEVENT_ENGINEHOURS        = true; // Simulate "engine-hours"
    public static       long    SIMEVENT_DIGITAL_INPUTS     = 0xFFL;
    public static       boolean XLATE_LOCATON_INMOTION      = true;
    public static       boolean USE_LAST_VALID_GPS          = true;
    public static       double  MINIMUM_MOVED_METERS        = 0.0;
    public static       boolean PACKET_LEN_END_OF_STREAM    = false;

    // ------------------------------------------------------------------------

    /* convenience for converting knots to kilometers */
    public static final double  KILOMETERS_PER_KNOT         = 1.85200000;

    // ------------------------------------------------------------------------

    /* Camera support for the Coban/Xexun and others */
    // --- Camera Conector (Infrared Camera) = 4 pins
    // Photo size: 320 x 240 pixels Thumbnail
    private static String IMEI_Camera                      = "";
    private static String SEGMENT_INITIAL_DATA             = "FFD8FFE000104A46494600010101000000000000FFDB";
    private static long   DataPacketAmount                 = 0L;
    private static long   DataPacketCount                  = 0L;
    private static String Data_Camera                      = "";
    private static ArrayList<String> DATA_FLD = new ArrayList<String>();
    private static long   RETRANSMIT_TIME                  = 0L; // Data re-transmit photo each 2 time (When data is not received completely)
    /* Parsing: JFIF Segment Initial Format */
    public static final String START_OF_IMAGE              = "FFD8";        // SOI (Start Of Image)
    public static final String APP0_HEADER                 = "FFE0";        // APP0 marker (Application-Specific)
    public static final String LENGHT_SEGMENT              = "0010";        // Length (Length of segment excluding APP0 marker)
    public static final String ID_JFIF                     = "4A46494600";  // Identifier (ASCII: "JFIF\000")
    public static final String JFIF_VERSION_1              = "0101";        // JFIF v1.01 (Major Version:01, Minor Version:01)
    public static final String JFIF_VERSION_2              = "0102";        // JFIF v1.02 (Major Version:01, Minor Version:02) *Ignored
    /* (0 -> No Units, Aspect Ratio Only Specified) (1 -> Pixels Per Inch) (2 -> Pixels Per Centimetre)	*/
    public static final String UNITS_PIXEL                 = "01";          // Units For Pixel Density Fields
    public static final String HORIZONTAL_PIXEL            = "0000";        // Horizontal Pixel Density
    public static final String VERTICAL_PIXEL              = "0000";        // Vertical Pixel Density
    public static final String HORIZONTAL_SIZE_THUMBNAIL   = "00";          // Horizontal Size Of Embedded JFIF Thumbnail In Pixels)
    public static final String VERTICAL_SIZE_THUMBNAIL     = "00";          // Vertical Size Of Embedded JFIF Thumbnail In Pixels
    public static final String DEFINE_QUANTIZATION_TABLE   = "FFDB";        // DQT (Define Quantization Table)
    /* Device with Camera */
    public static final String STATUS_PHOTO_TAKE_WT        = "wt";
    public static final String STATUS_PHOTO_TAKE_VT        = "vt";
    public static final String STATUS_PHOTO_RECORDING      = "vr";
    public static final String QUANTITY_PHOTO_TAKEN        = "0000";
    public static final String DATA_SEPARATOR              = "FA01"; // (Standalone Marker, Keep Going) - Separator
    public static final String JPEG_IMAGE_SIGNATURE = START_OF_IMAGE + DEFINE_QUANTIZATION_TABLE; // GPS TK106/A-B (320x240 Thumbnail)
    /* JFIF (JPEG) */
    public static final String END_OF_IMAGE                = "FFD9"; // EOI
    /* MIME Type JPEG Format */
    public static final String CONTENT_TYPE_JPEG           = "image/jpeg"; // 0xFF,0xD8,0xFF,0xE0

    // ------------------------------------------------------------------------

    /* GTS status codes for Input-On events */
    private static final int InputStatusCodes_ON[] = new int[] {
        StatusCodes.STATUS_INPUT_ON_00,
        StatusCodes.STATUS_INPUT_ON_01,
        StatusCodes.STATUS_INPUT_ON_02,
        StatusCodes.STATUS_INPUT_ON_03,
        StatusCodes.STATUS_INPUT_ON_04,
        StatusCodes.STATUS_INPUT_ON_05,
        StatusCodes.STATUS_INPUT_ON_06,
        StatusCodes.STATUS_INPUT_ON_07,
        StatusCodes.STATUS_INPUT_ON_08,
        StatusCodes.STATUS_INPUT_ON_09,
        StatusCodes.STATUS_INPUT_ON_10,
        StatusCodes.STATUS_INPUT_ON_11,
        StatusCodes.STATUS_INPUT_ON_12,
        StatusCodes.STATUS_INPUT_ON_13,
        StatusCodes.STATUS_INPUT_ON_14,
        StatusCodes.STATUS_INPUT_ON_15
    };

    /* GTS status codes for Input-Off events */
    private static final int InputStatusCodes_OFF[] = new int[] {
        StatusCodes.STATUS_INPUT_OFF_00,
        StatusCodes.STATUS_INPUT_OFF_01,
        StatusCodes.STATUS_INPUT_OFF_02,
        StatusCodes.STATUS_INPUT_OFF_03,
        StatusCodes.STATUS_INPUT_OFF_04,
        StatusCodes.STATUS_INPUT_OFF_05,
        StatusCodes.STATUS_INPUT_OFF_06,
        StatusCodes.STATUS_INPUT_OFF_07,
        StatusCodes.STATUS_INPUT_OFF_08,
        StatusCodes.STATUS_INPUT_OFF_09,
        StatusCodes.STATUS_INPUT_OFF_10,
        StatusCodes.STATUS_INPUT_OFF_11,
        StatusCodes.STATUS_INPUT_OFF_12,
        StatusCodes.STATUS_INPUT_OFF_13,
        StatusCodes.STATUS_INPUT_OFF_14,
        StatusCodes.STATUS_INPUT_OFF_15
    };

    // ------------------------------------------------------------------------

    /* GMT/UTC timezone */
    private static final TimeZone gmtTimezone = DateTime.getGMTTimeZone();

    // ------------------------------------------------------------------------

    /**
    *** Enumerated type: GPS device type
    **/
    public enum GPSDeviceType {
        UNKNOWN  (0,"Unknown" ),
        GPSCoban (1,"Coban"   );
        // ---
        private int    vv = 0;
        private String dd = null;
        GPSDeviceType(int v, String d) { vv = v; dd = d; }
        public String  toString()     { return dd; }
        public boolean isUnknown()    { return this.equals(UNKNOWN); }
    };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private GPSDeviceType gpsDeviceType = GPSDeviceType.UNKNOWN;
    private String       tkModemID      = null;
    private Device       tkDevice       = null;

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* TCP session ID */
    private String          sessionID                   = null;

    /* common GPSEvent instance */
    private GPSEvent        gpsEvent                    = null;

    /* Device record */
    private Device          gpsDevice                   = null;
    private String          lastModemID                 = null;

    /* Session 'terminate' indicator */
    // This value should be set to 'true' when this server has determined that the
    // session should be terminated.  For instance, if this server finishes communication
    // with the Device or if parser finds a fatal error in the incoming data stream
    // (ie. invalid Account/Device, or unrecognizable data).
    private boolean         terminate                   = false;

    /* session IP address */
    // These values will be set for you by the incoming session to indicate the
    // originating IP address.
    private String          ipAddress                   = null;
    private int             clientPort                  = 0;

    /**
    *** Packet handler constructor
    **/
    public TrackClientPacketHandler()
    {
        //super(Constants.DEVICE_CODE);
		super();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Callback when session is starting
    **/
    public void sessionStarted(InetAddress inetAddr, boolean isTCP, boolean isText)
    {
        super.sessionStarted(inetAddr, isTCP, isText);
        super.clearTerminateSession();
        this.clearSavedEventCount();
        this.gpsDeviceType = GPSDeviceType.UNKNOWN;
        this.tkModemID    = null;
        this.tkDevice     = null;
        /* init */
        this.ipAddress        = (inetAddr != null)? inetAddr.getHostAddress() : null;
        this.clientPort       = this.getSessionInfo().getRemotePort();
    }

    /**
    *** Callback when session is terminating
    **/
    public void sessionTerminated(Throwable err, long readCount, long writeCount)
    {
	  super.sessionTerminated(err, readCount, writeCount);
    }

    // ------------------------------------------------------------------------

    /**
    *** Returns the size of the packet in the queue.
    **/
    public int getActualPacketLength(byte packet[], int packetLen)
    {

        /* minimum number of bytes */
        int minBytes = 1; // set to expected minimum number of bytes
        if (packetLen < minBytes) {
            return minBytes | ServerSocketThread.PACKET_LEN_INCREMENTAL_;
        }

        /* Coban: check for login packet */
        if (packet[0] == '#') {
            // PacketTerminator: ';'
            // IE: "##,imei:123451042191239,A;"
            this.gpsDeviceType = GPSDeviceType.GPSCoban;
            if (PACKET_LEN_END_OF_STREAM) {
                return ServerSocketThread.PACKET_LEN_END_OF_STREAM;
            } else {
                // -- should instead explicitly look for the ';' terminator?
                return ServerSocketThread.PACKET_LEN_LINE_TERMINATOR;
            }
        } else
        if ((packet[0] == 'i') || (packet[0] == 'I')) {
            // PacketTerminator: ';'
            // IE: "imei:123451042191239,tracker,1107090553,9735551234,F,215314.000,A,4103.7641,N,14244.9450,W,0.08,;"
            this.gpsDeviceType = GPSDeviceType.GPSCoban;
            if (PACKET_LEN_END_OF_STREAM) {
                return ServerSocketThread.PACKET_LEN_END_OF_STREAM;
            } else {
                // -- should instead explicitly look for the ';' terminator?
                return ServerSocketThread.PACKET_LEN_LINE_TERMINATOR;
            }
        }

        /* consume unrecognized single bytes */
        if (packetLen == 1) {
            byte b = packet[0];
            if (b <= ' ') {
                // -- discard single space/control char
                return 1; // consume/ignore
            }
        }

        if (PACKET_LEN_END_OF_STREAM) {
            return ServerSocketThread.PACKET_LEN_END_OF_STREAM;
        } else {
            return ServerSocketThread.PACKET_LEN_LINE_TERMINATOR;
        }

    }

    // ------------------------------------------------------------------------

    /* set session terminate after next packet handling */
    private void setTerminate()
    {
        this.terminate = true;
    }

    /* indicate that the session should terminate */
    // This method is called after each return from "getHandlePacket" to check to see
    // the current session should be closed.
    public boolean getTerminateSession()
    {
        return this.terminate;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* return the initial packet sent to the Device after session is open */
    public byte[] getInitialPacket()
        throws Exception
    {
        // At this point a connection from the client to the server has just been
        // initiated, and we have not yet received any data from the client.
        // If the client is expecting to receive an initial packet from the server at
        // the time that the client connects, then this is where the server can return
        // a byte array that will be transmitted to the client Device.
        return null;
        // Note: any returned response for "getInitialPacket()" is ignored for simplex/udp connections.
        // Returned UDP packets may be sent from "getHandlePacket" or "getFinalPacket".
    }

    // ------------------------------------------------------------------------

    /* final packet sent to Device before session is closed */
    public byte[] getFinalPacket(boolean hasError)
        throws Exception
    {
        // If the server wishes to send a final packet to the client just before the connection
        // is closed, then this is where the server should compose the final packet, and return
        // this packet in the form of a byte array.  This byte array will then be transmitted
        // to the client Device before the session is closed.
	  return null;
    }

    // ------------------------------------------------------------------------

    /**
    *** Workhorse of the packet handler.  Parse/insert event data.
    **/
    public static String imei_Tracker = "";
    public byte[] getHandlePacket(byte pktBytes[])
    {

        String s = StringTools.toStringValue(pktBytes).trim(); // debug message

        /* empty packet */
        if (ListTools.isEmpty(pktBytes)) {
            Print.logWarn("Ignoring empty/null packet \n");
            return null;
        }

        /* invalid length */
        if (pktBytes.length < 11) {
            Print.logError("Unexpected packet length: " + pktBytes.length + "\n");
            return null;
        }

        /* reset event count (necessary when receiving multiple records via TCP) */
        this.clearSavedEventCount();

        /* debug/header */
        Print.logInfo("Recv: " + s + "\n");
        if (!StringTools.isPrintableASCII(pktBytes)) {
        Print.logInfo("Hex : 0x" + StringTools.toHexString(pktBytes) + "\n");
        }

        /* Coban: keep-alive packet? */
        if (s.startsWith("##")) {
            // Coban: keep-alive packet?
            //   ##,imei:123451042191239,A;
            Print.logInfo("Login Packet: " + s + "\n"); // debug message
            // Success log on
            return (new byte[] { (byte)'L', (byte)'O', (byte)'A', (byte)'D' }); // ACK "LOAD"
        } else
        if (s.startsWith("imei:")) {
            // TK103-2: data packet
            //   imei:123451042191239,tracker,1107090553,9735551234,F,215314.000,A,4103.7641,N,14244.9450,W,0.08,;
            return this.parseInsertRecord_COBAN(s);
        }

        /* TK103-1 "ON" response */
        if ((s.length() == 15) && StringTools.isNumeric(s)) {
            Print.logInfo("Heartbeat packets: " + s + "\n");
            // on line
            return "ON".getBytes(); // ACK "ON"
        }

        if(s.length() == 16 && s.contains(";")) {
			Print.logInfo("Heartbeat packets: " + s + "\n");
			// ##,imei:123451042191239,B;
			// Single position
			String plot  = StringTools.toStringValue(pktBytes).trim();
			imei_Tracker = plot;
			String temp  = "";
			char imei[];
			imei = new char[15];
			for (int x=0;x < 15;x++) {
			temp = imei_Tracker.substring(x , x + 1);
			imei[x] = temp.charAt(0);
			}
			return (new byte[] { (byte)'*', (byte)'*', (byte)',', (byte)'i', (byte)'m', (byte)'e', (byte)'i', (byte)':', (byte) imei[0], (byte) imei[1], (byte) imei[2], (byte) imei[3], (byte) imei[4], (byte) imei[5], (byte) imei[6], (byte) imei[7], (byte) imei[8], (byte) imei[9], (byte) imei[10], (byte) imei[11], (byte) imei[12], (byte) imei[13], (byte) imei[14], (byte)',', (byte)'B' });
		}
        return null;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Coban:
    /**
    *** Coban: parse and insert data record
    **/

    private byte[] parseInsertRecord_COBAN(String s) // handleCommon
    {
	  boolean CAMERA_ACTIVE = false;

        Print.logInfo("Parsing: (Coban) \n");

        /* pre-validate */
        if (s == null) {
            Print.logError("String is null");
            return null;
        }

        /* parse to fields */
		 String fld[] = StringTools.parseStringArray(s, ',');
        //String fld[] = StringTools.parseString(s, ',');
        if (fld == null) {
            // -- will not occur
            Print.logWarn("Fields are null");
            return null;
        }

        /* get "imei:" */
        if (fld[0].startsWith("imei:")) {
            this.tkModemID = fld[0].substring("imei:".length()).trim();
        }
        if (StringTools.isBlank(this.tkModemID)) {
            Print.logError("'imei:' value is missing");
            return null;
        }

        String EventCamera = fld[1].trim().toLowerCase(); // Events: vt/wt/vr (Only for GPS with camera)
        /* Parsing: Event vt / wt */
        if (EventCamera.contains("vt")) {
		// Reset value camera
            IMEI_Camera      = "";
            Data_Camera      = "";
            DataPacketAmount = 0L;
            DataPacketCount  = 0L;
		DATA_FLD.clear();
		// Parsing
	   	DataPacketAmount = StringTools.parseLong(fld[1].substring("vt".length()).trim(),0L);
	      DATA_FLD.add(0, fld[0].substring("imei:".length()).trim());
	      DATA_FLD.add(1, fld[1]);
	      DATA_FLD.add(2, fld[2]);
	      DATA_FLD.add(3, fld[3]);
	      DATA_FLD.add(4, fld[4]);
	      DATA_FLD.add(5, fld[5]);
	      DATA_FLD.add(6, fld[6]);
	      DATA_FLD.add(7, fld[7]);
	      DATA_FLD.add(8, fld[8]);
	      DATA_FLD.add(9, fld[9]);
	      DATA_FLD.add(10, fld[10]);
	      DATA_FLD.add(11, fld[11]);
	      DATA_FLD.add(12, fld[12]);
        }
        if (EventCamera.contains("wt")) {
	      // Reset value camera
            IMEI_Camera      = "";
            Data_Camera      = "";
            DataPacketAmount = 0L;
            DataPacketCount  = 0L;
		DATA_FLD.clear();
		// Parsing
		DataPacketAmount = StringTools.parseLong(fld[1].substring("wt".length()).trim(),0L);
		DATA_FLD.add(0, fld[0].substring("imei:".length()).trim());
	      DATA_FLD.add(1, fld[1]);
	      DATA_FLD.add(2, fld[2]);
	      DATA_FLD.add(3, fld[3]);
	      DATA_FLD.add(4, fld[4]);
	      DATA_FLD.add(5, fld[5]);
	      DATA_FLD.add(6, fld[6]);
	      DATA_FLD.add(7, fld[7]);
	      DATA_FLD.add(8, fld[8]);
	      DATA_FLD.add(9, fld[9]);
	      DATA_FLD.add(10, fld[10]);
	      DATA_FLD.add(11, fld[11]);
	      DATA_FLD.add(12, fld[12]);
        }
        /* Parsing: Event vr */
        if (EventCamera.contains("vr")) {
	      DataPacketCount++;
	      IMEI_Camera    = fld[0].substring("imei:".length()).trim();
	      this.tkModemID = IMEI_Camera;
	    if (DataPacketCount == 1) {
	      Data_Camera =  SEGMENT_INITIAL_DATA + Data_Camera +  fld[2].substring(16, fld[2].length()-4);
	    } else if (DataPacketCount != DataPacketAmount) {
	      Data_Camera =  Data_Camera +  fld[2].substring(8, fld[2].length()-4);
	    } else if (DataPacketCount == DataPacketAmount) {
	      Data_Camera =  Data_Camera +  fld[2].substring(4, fld[2].length()-8) + END_OF_IMAGE;
	      CAMERA_ACTIVE = true;
	    }
        }

        if (EventCamera.contains("vr") && !CAMERA_ACTIVE) {
		Print.logInfo("Receiving Packets of the camera. ");
		//String ACK_ = "**,imei:"+IMEI_Camera+",161"; // Data re-transmission
		//return ACK_.getBytes();
		return null;
	  }

		String mimeType = CONTENT_TYPE_JPEG;
		byte[] imageCamera = null;
	  if (CAMERA_ACTIVE) {
            // Save Image
            Data_Camera = Data_Camera.toUpperCase();
            imageCamera = Data_Camera.getBytes();
		Print.logInfo("Image Complete. \n ");
	      // Reset value camera
            IMEI_Camera      = "";
            Data_Camera      = "";
            DataPacketAmount = 0L;
            DataPacketCount  = 0L;
	   }

        /* event code */
        String eventCode = StringTools.trim(fld[1]);
        if (eventCode.endsWith("!")) {
            // -- remove trailing "!", if present
            eventCode = eventCode.substring(0,eventCode.length()-1);
            // -- IE: "help me!" ==> "help me", etc.
        }

        /* minimum field length */
        if (eventCode.equalsIgnoreCase("OBD")) {
            if (fld.length < 13) { // DTC codes are optional
                Print.logWarn("Invalid number of fields: " + fld.length);
                return null;
            }
        } else {
            if (fld.length < 12 && fld.length > 4) {
                Print.logWarn("Invalid number of fields: " + fld.length);
                return null;
            }
        }

        /* GPS values */
        long fixtime = 0L;
        boolean   validGPS    = false;
        double    latitude    = 0.0;
        double    longitude   = 0.0;
        double    headingDeg  = -1.0;
        double    speedKPH    = -1.0;
        double    altitudeM   = 0.0;
        double    odomKM      = 0.0;
        double    batteryV    = 0.0;
        double    engTempC    = 0.0;
        String    engDTC[]    = null;
        long      gpsAge      = 0L;
        double    HDOP        = 0.0;
        int       numSats     = 0;
        long      gpioInput   = -1L;
        CellTower servingCell = null;
        GeoPoint  geoPoint    = null;
        int       statusCode  = StatusCodes.STATUS_LOCATION;
        /* Extra fields */
        double temp_alarm = 0.0;
        double fuelLevel1 = 0.0;
        double fuelLevel2 = 0.0;
        String FaultCode  = "";
        String RFIDTag    = "";

        if (!CAMERA_ACTIVE) { // start
        /* get time */
        {
            // -- local scope
            long locYMDhms = 0L;
            if (fld[2].length() >= 12) {
                locYMDhms = StringTools.parseLong(fld[2].substring(0,12),0L);
            } else
            if (fld[2].length() >= 10) {
                locYMDhms = StringTools.parseLong(fld[2].substring(0,10),0L);
                locYMDhms *= 100L;
            } else {
                locYMDhms = 0L;
            }
            if (eventCode.equalsIgnoreCase("OBD")) {
                fixtime = this._parseDate_YYMMDDhhmmss(locYMDhms);
            } else {
                long gmtHMS = StringTools.parseLong(fld[5],-1L);
                if (gmtHMS >= 0L) {
                    // -- GPS time appears to be available
                    fixtime = this._parseDate_YMDhms_HMS(locYMDhms, gmtHMS);
                } else {
                    // -- GPS time is not available
                    fixtime = this._parseDate_YYMMDDhhmmss(locYMDhms);
                }
            }
        }
        if (fixtime <= 0L) {
            Print.logWarn("Invalid date: " + fld[2] + "/" + fld[5] + " (using current time)");
            fixtime = DateTime.getCurrentTimeSec(); // default to now
        }

        /* parse event */
        if (eventCode.equalsIgnoreCase("OBD")) {
            // -- parse custom OBD data (TODO)
        } else
        if (fld[4].equals("L")) {
            // -- parse Mobile location information
            // -  Many thanks to Franjieh El Khoury for this information.
            int MCC = 0; // -- this may need to be manually filled in
            int MNC = 0; // -- this may need to be manually filled in
            int LAC = (fld.length > 7)? StringTools.parseInt(fld[7],0) : -1;
            int CID = (fld.length > 9)? StringTools.parseInt(fld[9],0) : 0;
            if ((MCC >= 0) && (MNC >= 0) && (LAC >= 0) && (CID > 0)) {
                servingCell = new CellTower();
                servingCell.setMobileCountryCode(MCC);
                servingCell.setMobileNetworkCode(MNC);
                servingCell.setLocationAreaCode(LAC);
                servingCell.setCellTowerID(CID);
            }
        } else { // (fld[4].equals("F"))
            // -- parse GPS information
            validGPS    = fld[6].equalsIgnoreCase("A");
            latitude    = validGPS? this._parseLatitude( fld[7], fld[ 8])  : 0.0;
            longitude   = validGPS? this._parseLongitude(fld[9], fld[10])  : 0.0;
            double knts = (validGPS && (fld.length > 11))? StringTools.parseDouble(fld[11], -1.0) : -1.0;
            headingDeg  = (validGPS && (fld.length > 12))? StringTools.parseDouble(fld[12], -1.0) : -1.0;
            speedKPH    = (knts >= 0.0)? (knts * KILOMETERS_PER_KNOT)   : -1.0;
        }

        // Temperature Alarm (Range -050 Celsius to +125 Celsius)
        if (eventCode.contains("T:")) {
		   temp_alarm = StringTools.parseDouble(fld[1].substring("T:".length()).trim(), 0.0);
               statusCode = StatusCodes.STATUS_TEMPERATURE;
		   Print.logInfo("Temperature Alarm: " + temp_alarm);
	  }
	  // Fuel Level Oil 1 and Oil 2
	  if (eventCode.contains("oil") && !eventCode.contains("oil1") && !eventCode.contains("oil2")) {
	  fuelLevel1 = StringTools.parseDouble(fld[1].substring("oil".length()).trim(), 0.0);
	  //statusCode = StatusCodes.STATUS_FUEL_SENSOR;
	  Print.logInfo("Fuel Level 1: " + fuelLevel1);
        }
	  if (eventCode.contains("oil1")) {
	  fuelLevel1 = StringTools.parseDouble(fld[fld.length - 3], 0.0);
	  // statusCode = StatusCodes.STATUS_FUEL_SENSOR;
	  Print.logInfo("Fuel Level 1: " + fuelLevel1);
        }
        if (eventCode.contains("oil2")) {
	  fuelLevel2 = StringTools.parseDouble(fld[fld.length - 2], 0.0);
	  //statusCode = StatusCodes.STATUS_FUEL_SENSOR;
	  Print.logInfo("Fuel Level 2: " + fuelLevel2);
        }
        // DTC - Diagnostic Trouble Code
        if (eventCode.contains("DTC")) {
		  FaultCode  = fld[fld.length - 2];
		  statusCode = StatusCodes.STATUS_OBD_FAULT;
		  Print.logInfo("DTC (OBD-II): " + FaultCode);
	  }
	  // RFID - Radio Frequency Identification
	  if (eventCode.contains("rfid") || eventCode.contains("RFID")) {
		  RFIDTag = fld[3];
		  // statusCode = StatusCodes.STATUS_RFID_CONNECT;
		  Print.logInfo("RFID Tag: " + RFIDTag);
	  }
	  // TPMS - Tyres Pressure Monitoring
	  if (eventCode.contains("tpms") || eventCode.contains("TPMS")) {
		  Print.logInfo("TPMS (Tyres Pressure Monitoring) ");
	  }

        /* valid lat/lon? */
        if (validGPS && !GeoPoint.isValid(latitude,longitude)) {
            Print.logWarn("Invalid GPRMC lat/lon: " + latitude + "/" + longitude);
            latitude  = 0.0;
            longitude = 0.0;
            validGPS  = false;
        }
        geoPoint = new GeoPoint(latitude, longitude);

        /* adjust speed, calculate approximate heading if not available in packet */
        if (speedKPH < MINIMUM_SPEED_KPH) {
            //Print.logInfo("Actual Speed: " + speedKPH);
            speedKPH   = 0.0;
            headingDeg = 0.0;
        } else
        if (headingDeg < 0.0) {
            //headingDeg = 0.0;   <== leave as '-1'
        }

        /* timestamp adjustments based on the event code */
        // -- may be necessary if the "acc on"/"acc off" occur at the same GPS time
        if (statusCode == StatusCodes.STATUS_IGNITION_OFF) {
            // -- subtract one second to make sure it comes before any following "acc on"
            fixtime--;
        }
	    } //end

	    if (CAMERA_ACTIVE) { // start
		 /* get time */
        {
            // -- local scope
            long locYMDhms = 0L;
            if (DATA_FLD.get(2).length() >= 12) {
                locYMDhms = StringTools.parseLong(DATA_FLD.get(2).substring(0,12),0L);
            } else
            if (DATA_FLD.get(2).length() >= 10) {
                locYMDhms = StringTools.parseLong(DATA_FLD.get(2).substring(0,10),0L);
                locYMDhms *= 100L;
            } else {
                locYMDhms = 0L;
            }
            if (eventCode.equalsIgnoreCase("OBD")) {
                fixtime = this._parseDate_YYMMDDhhmmss(locYMDhms);
            } else {
                long gmtHMS = StringTools.parseLong(DATA_FLD.get(5),-1L);
                if (gmtHMS >= 0L) {
                    // -- GPS time appears to be available
                    fixtime = this._parseDate_YMDhms_HMS(locYMDhms, gmtHMS);
                } else {
                    // -- GPS time is not available
                    fixtime = this._parseDate_YYMMDDhhmmss(locYMDhms);
                }
            }
        }
        if (fixtime <= 0L) {
            Print.logWarn("Invalid date: " + DATA_FLD.get(2) + "/" + DATA_FLD.get(5) + " (using current time)");
            fixtime = DateTime.getCurrentTimeSec(); // default to now
        }

        /* parse event */
        if (eventCode.equalsIgnoreCase("OBD")) {
            // -- parse custom OBD data (TODO)
        } else
        if (DATA_FLD.get(4).equals("L")) {
            // -- parse Mobile location information
            // -  Many thanks to Franjieh El Khoury for this information.
            int MCC = 0; // -- this may need to be manually filled in
            int MNC = 0; // -- this may need to be manually filled in
            int LAC = (DATA_FLD.size() > 7)? StringTools.parseInt(DATA_FLD.get(7),0) : -1;
            int CID = (DATA_FLD.size() > 9)? StringTools.parseInt(DATA_FLD.get(9),0) : 0;
            if ((MCC >= 0) && (MNC >= 0) && (LAC >= 0) && (CID > 0)) {
                servingCell = new CellTower();
                servingCell.setMobileCountryCode(MCC);
                servingCell.setMobileNetworkCode(MNC);
                servingCell.setLocationAreaCode(LAC);
                servingCell.setCellTowerID(CID);
            }
        } else { // (DATA_FLD.get(4).equals("F"))
            // -- parse GPS information
            validGPS    = DATA_FLD.get(6).equalsIgnoreCase("A");
            latitude    = validGPS? this._parseLatitude( DATA_FLD.get(7), DATA_FLD.get(8))   : 0.0;
            longitude   = validGPS? this._parseLongitude(DATA_FLD.get(9), DATA_FLD.get(10))  : 0.0;
            double knts = (validGPS && (DATA_FLD.size() > 11))? StringTools.parseDouble(DATA_FLD.get(11), -1.0) : -1.0;
            headingDeg  = (validGPS && (DATA_FLD.size() > 12))? StringTools.parseDouble(DATA_FLD.get(12), -1.0) : -1.0;
            speedKPH    = (knts >= 0.0)? (knts * KILOMETERS_PER_KNOT)   : -1.0;
        }

        /* valid lat/lon? */
        if (validGPS && !GeoPoint.isValid(latitude,longitude)) {
            Print.logWarn("Invalid GPRMC lat/lon: " + latitude + "/" + longitude);
            latitude  = 0.0;
            longitude = 0.0;
            validGPS  = false;
        }
        geoPoint = new GeoPoint(latitude, longitude);

        /* adjust speed, calculate approximate heading if not available in packet */
        if (speedKPH < MINIMUM_SPEED_KPH) {
            //Print.logInfo("Actual Speed: " + speedKPH);
            speedKPH   = 0.0;
            headingDeg = 0.0;
        } else
        if (headingDeg < 0.0) {
            //headingDeg = 0.0;   <== leave as '-1'
        }

        /* timestamp adjustments based on the event code */
        // -- may be necessary if the "acc on"/"acc off" occur at the same GPS time
        if (statusCode == StatusCodes.STATUS_IGNITION_OFF) {
            // -- subtract one second to make sure it comes before any following "acc on"
            fixtime--;
        }
        statusCode = StatusCodes.STATUS_IMAGE_0; // Received Photo
		} // end

        // ------------------------------------------------
        // TK103-2: Common data handling below
        this.handleCommon(this.tkModemID,
            fixtime, statusCode, null,
            geoPoint, gpsAge, HDOP, numSats,
            speedKPH, headingDeg, altitudeM, odomKM,
            gpioInput, batteryV, 0.0/*battLvl*/,
            engTempC, engDTC,
            servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag, eventCode);
      /* return ACK */
      return null;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Computes seconds in UTC time given values from GPS device.
    *** @param locYMDhms Date received from packet in YYMMDDhhmmss format, where DD is day, MM is month,
    ***                  YY is year, hh is the hour, mm is the minutes, and ss is the seconds.
    ***                  Unfortunately, the device is allowed to be configured to specify this time
    ***                  relative to the local timezone, rather than GMT.  This makes determining the
    ***                  actual time of the event more difficult.
    *** @param gmtHMS    Time received from GPS in HHMMSS format, where HH is hour, MM is minute,
    ***                  and SS is second.  This time is assumed to be relative to GMT.
    *** @return Time in UTC seconds.
    **/
    private long _parseDate_YMDhms_HMS(long locYMDhms, long gmtHMS)
    {

        /* GMT time of day */
        int    gmtHH  = (int)((gmtHMS / 10000L) % 100L);
        int    gmtMM  = (int)((gmtHMS /   100L) % 100L);
        int    gmtSS  = (int)((gmtHMS         ) % 100L);
        long   gmtTOD = (gmtHH * 3600L) + (gmtMM * 60L) + gmtSS; // seconds of day
        //Print.logInfo("GMT HHMMSS: " + gmtHMS);
        //Print.logInfo("GMT HH="+gmtHH + " MM="+gmtMM + " SS="+gmtSS +" TOD="+gmtTOD);

        /* local time of day */
        int    locHH  = (int)((locYMDhms / 10000L) % 100L);
        int    locMM  = (int)((locYMDhms /   100L) % 100L);
        int    locSS  = (int)((locYMDhms         ) % 100L);
        long   locTOD = (locHH * 3600L) + (locMM * 60L) + locSS; // seconds of day
        //Print.logInfo("Loc HHMMSS: " + locYMDhms);
        //Print.logInfo("Loc HH="+locHH + " MM="+locMM + " SS="+locSS +" TOD="+locTOD);

        /* current day */
        long ymd = locYMDhms / 1000000L; // remove hhmmss
        long DAY;
        if (ymd > 0L) {
            int    dd = (int)( ymd           % 100L);
            int    mm = (int)((ymd /   100L) % 100L);
            int    yy = (int)((ymd / 10000L) % 100L) + 2000;
            long   yr = ((long)yy * 1000L) + (long)(((mm - 3) * 1000) / 12);
            DAY       = ((367L * yr + 625L) / 1000L) - (2L * (yr / 1000L))
                         + (yr / 4000L) - (yr / 100000L) + (yr / 400000L)
                         + (long)dd - 719469L;
            long  dif = (locTOD >= gmtTOD)? (locTOD - gmtTOD) : (gmtTOD - locTOD); // difference should be small (ie. < 1 hour)
            if (dif > DateTime.HourSeconds(12)) { // 12 to 18 hours
                // > 12 hour difference, assume we've crossed a day boundary
                if (locTOD > gmtTOD) {
                    // locTOD > gmtTOD likely represents the next day
                    // ie 2011/07/10 23:00 ==> 01:00 (2011/07/11)
                    DAY++;
                } else {
                    // locTOD < gmtTOD likely represents the previous day
                    // ie 2011/07/10 01:00 ==> 23:00 (2011/07/09)
                    DAY--;
                }
            }
        } else {
            // we don't have the day, so we need to figure out as close as we can what it should be.
            long   utc = DateTime.getCurrentTimeSec();
            long   tod = utc % DateTime.DaySeconds(1);
            DAY        = utc / DateTime.DaySeconds(1);
            long   dif = (tod >= gmtTOD)? (tod - gmtTOD) : (gmtTOD - tod); // difference should be small (ie. < 1 hour)
            if (dif > DateTime.HourSeconds(12)) { // 12 to 18 hours
                // > 12 hour difference, assume we've crossed a day boundary
                if (tod > gmtTOD) {
                    // tod > TOD likely represents the next day
                    DAY++;
                } else {
                    // tod < TOD likely represents the previous day
                    DAY--;
                }
            }
        }

        /* return UTC seconds */
        long sec = DateTime.DaySeconds(DAY) + gmtTOD;
        return sec;

    }

    /**
    *** Parses the specified date into unix 'epoch' time
    **/
    private long _parseDate_YYMMDDhhmmss(long YYMMDDhhmmss)
    {
        if (YYMMDDhhmmss <= 0L) {
            return 0L;
        } else {
            //                            YYMMDDhhmmss
            int YY = (int)((YYMMDDhhmmss / 10000000000L) % 100L); // 14 year
            int MM = (int)((YYMMDDhhmmss /   100000000L) % 100L); // 04 month
            int DD = (int)((YYMMDDhhmmss /     1000000L) % 100L); // 11 day
            int hh = (int)((YYMMDDhhmmss /       10000L) % 100L); // 01 hour
            int mm = (int)((YYMMDDhhmmss /         100L) % 100L); // 48 minute
            int ss = (int)((YYMMDDhhmmss /           1L) % 100L); // 04 second
            DateTime dt = new DateTime(gmtTimezone,YY+2000,MM,DD,hh,mm,ss);
            return dt.getTimeSec();
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Parses latitude given values from GPS device.
    *** @param  s  Latitude String from GPS device in DDmm.mmmm format.
    *** @param  d  Latitude hemisphere, "N" for northern, "S" for southern.
    *** @return Latitude parsed from GPS data, with appropriate sign based on hemisphere or
    ***         90.0 if invalid latitude provided.
    **/
    private double _parseLatitude(String s, String d)
    {
        double _lat = StringTools.parseDouble(s, 99999.0);
        if (_lat < 99999.0) {
            double lat = (double)((long)_lat / 100L); // _lat is always positive here
            lat += (_lat - (lat * 100.0)) / 60.0;
            return d.equals("S")? -lat : lat;
        } else {
            return 90.0; // invalid latitude
        }
    }

    /**
    *** Parses longitude given values from GPS device.
    *** @param s Longitude String from GPS device in DDDmm.mmmm format.
    *** @param d Longitude hemisphere, "E" for eastern, "W" for western.
    *** @return Longitude parsed from GPS data, with appropriate sign based on hemisphere or
    *** 180.0 if invalid longitude provided.
    **/
    private double _parseLongitude(String s, String d)
    {
        double _lon = StringTools.parseDouble(s, 99999.0);
        if (_lon < 99999.0) {
            double lon = (double)((long)_lon / 100L); // _lon is always positive here
            lon += (_lon - (lon * 100.0)) / 60.0;
            return d.equals("W")? -lon : lon;
        } else {
            return 180.0; // invalid longitude
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Common event data handler for all TKDeviceType's
    **/
    private boolean handleCommon(String modemID, // this.tkModemID
        long      fixtime, int statusCode, HashSet<Integer> statCodeSet,
        GeoPoint  geoPoint, long gpsAge, double HDOP, int numSats,
        double    speedKPH, double headingDeg, double altitudeM, double odomKM,
        long      gpioInput, double batteryV, double battLvl,
        double    engTempC, String engDTC[],
        CellTower servingCell, String mimeType, byte[] imageCamera, double temp_alarm, double fuelLevel1, double fuelLevel2, String FaultCode, String RFIDTag, String eventCode)
    {

        /* parsed data */
        Print.logInfo("IMEI     : " + this.tkModemID);
        Print.logInfo("Timestamp: " + fixtime + " [" + new DateTime(fixtime) + "]");

        /* find Device */
        //Device device = DCServerFactory.loadDeviceByPrefixedModemID(UNIQUEID_PREFIX, this.tkModemID);
         Device device = DCServerConfig.loadDeviceUniqueID(Main.getServerConfig(), this.tkModemID);

        if (device == null) {
            return false; // errors already displayed
        }
        String accountID = device.getAccountID();
        String deviceID  = device.getDeviceID();
        String uniqueID  = device.getUniqueID();
        Print.logInfo("UniqueID : " + uniqueID);
        Print.logInfo("DeviceID : " + accountID + "/" + deviceID);

        /* status code */
        DCServerConfig dcs = Main.getServerConfig();
        if ((dcs != null) && !StringTools.isBlank(eventCode)) {
            int code = dcs.translateStatusCode(eventCode, -9999);
            if (eventCode.contains("acc on")) {
		    gpioInput  = 1;
		    statusCode = StatusCodes.STATUS_IGNITION_ON;
		    device.setLastInputState(gpioInput & 0xFFFFL);
		    device.setLastIgnitionOnTime(fixtime);
		   // device.setLastEngineOnTime(fixtime);
		}
		if (eventCode.contains("acc off")) {
		    gpioInput  = 0;
		    statusCode = StatusCodes.STATUS_IGNITION_OFF;
		    device.setLastInputState(gpioInput & 0xFFFFL);
		    device.setLastIgnitionOffTime(fixtime);
		  //  device.setLastEngineOffTime(fixtime);
		}
            if (code == -9999) {
                // -- default 'statusCode' is StatusCodes.STATUS_LOCATION
            } else {
                statusCode = code;
            }
        }

        /* check IP address */
        DataTransport dataXPort = device.getDataTransport();
        if (this.hasIPAddress() && !dataXPort.isValidIPAddress(this.getIPAddress())) {
            DTIPAddrList validIPAddr = dataXPort.getIpAddressValid(); // may be null
            Print.logError("Invalid IP Address from device: " + this.getIPAddress() +
                " [expecting " + validIPAddr + "]");
            return false;
        }
        dataXPort.setIpAddressCurrent(this.getIPAddress());    // FLD_ipAddressCurrent
        dataXPort.setRemotePortCurrent(this.getRemotePort());  // FLD_remotePortCurrent
        dataXPort.setLastTotalConnectTime(DateTime.getCurrentTimeSec()); // FLD_lastTotalConnectTime
        if (!dataXPort.getDeviceCode().equalsIgnoreCase(Main.getServerName())) {
            dataXPort.setDeviceCode(Main.getServerName()); // FLD_deviceCode
        }

        /* valid GeoPoint? */
        boolean validGPS;
        if (geoPoint == null) {
            geoPoint = GeoPoint.INVALID_GEOPOINT;
            validGPS = false;
        } else {
            validGPS = geoPoint.isValid();
        }

        /* calculate heading from last location */
        if (headingDeg < 0.0) {
            // -- try to calculate heading based on last valid GPS location
            headingDeg = 0.0;
            if (validGPS && (speedKPH > 0.0)) {
                GeoPoint lastGP = device.getLastValidLocation();
                if (GeoPoint.isValid(lastGP)) {
                    // -- calculate heading from last point to this point
                    headingDeg = lastGP.headingToPoint(geoPoint);
                }
            }
        }

        /* estimate GPS-based odometer */
        if (odomKM <= 0.0) {
            // -- calculate odometer
            odomKM = (ESTIMATE_ODOMETER && validGPS)?
                device.getNextOdometerKM(geoPoint) :
                device.getLastOdometerKM();
        } else {
            // -- bounds-check odometer
            odomKM = device.adjustOdometerKM(odomKM);
        }

        /* log parsed data */
        Print.logInfo("GPS      : " + geoPoint);
        if (altitudeM != 0.0) {
        Print.logInfo("Altitude : " + StringTools.format(altitudeM,"#0.0") + " meters");
        }
        Print.logInfo("Speed    : " + StringTools.format(speedKPH ,"#0.0") + " kph " + headingDeg);
        if (batteryV > 0.0) {
        Print.logInfo("Battery V: " + StringTools.format(batteryV ,"#0.0") + " Volts");
        }
        if (battLvl > 0.0) {
        Print.logInfo("Battery %: " + StringTools.format(battLvl*100.0,"#0.0") + " %");
        }
        if (odomKM > 0.0) {
        Print.logInfo("Odometer : " + odomKM + " km");
        }
        if (engTempC > 0.0) {
        Print.logInfo("EngTemp C: " + StringTools.format(engTempC ,"#0.0") + " C");
        }
        if ((engDTC != null) && (engDTC.length > 0)) {
        Print.logInfo("OBD DTC  : " + StringTools.join(engDTC,","));
        }
        if (servingCell != null) {
        Print.logInfo("CellTower: " + servingCell);
        }

        /* digital input change events */
        if (gpioInput >= 0L) {
            if (SIMEVENT_DIGITAL_INPUTS > 0L) {
                // The current input state is compared to the last value stored in the Device record.
                // Changes in the input state will generate a synthesized event.
                long chgMask = (device.getLastInputState() ^ gpioInput) & SIMEVENT_DIGITAL_INPUTS;
                if (chgMask != 0L) {
                    // an input state has changed
                    for (int b = 0; b <= 7; b++) {
                        long m = 1L << b;
                        if ((chgMask & m) != 0L) {
                            // this bit changed
                            long inpTime = fixtime;
                            int  inpCode = ((gpioInput & m) != 0L)? InputStatusCodes_ON[b] : InputStatusCodes_OFF[b];
                            Print.logInfo("GPIO input : " + StatusCodes.GetDescription(inpCode,null));
                            this.insertEventRecord(device,
                                inpTime, inpCode, null/*geozone*/,
                                geoPoint, gpsAge, HDOP, numSats,
                                speedKPH, headingDeg, altitudeM, odomKM,
                                gpioInput, batteryV, battLvl,
                                engTempC, engDTC,
                                servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
                        }
                    }
                }
            }
            device.setLastInputState(gpioInput & 0xFFFFL); // FLD_lastInputState
        }

        /* simulate Geozone arrival/departure */
        if (SIMEVENT_GEOZONES && validGPS) {
            java.util.List<Device.GeozoneTransition> zone = device.checkGeozoneTransitions(fixtime, geoPoint);
            if (zone != null) {
                for (Device.GeozoneTransition z : zone) {
                    int zsc = z.getStatusCode(); // STATUS_GEOFENCE_ARRIVE / STATUS_GEOFENCE_DEPART
                    this.insertEventRecord(device,
                        z.getTimestamp(), z.getStatusCode(), z.getGeozone(),
                        geoPoint, gpsAge, HDOP, numSats,
                        speedKPH, headingDeg, altitudeM, odomKM,
                        gpioInput, batteryV, battLvl,
                        engTempC, engDTC,
                        servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
                    Print.logInfo("Geozone    : " + z);
                }
            }
        }

        if (SIMEVENT_ENGINEHOURS && validGPS) {	// start: SIMEVENT_ENGINEHOURS
            long   EngineON               = device.getLastIgnitionOnTime();
            long   EngineOFF              = device.getLastIgnitionOffTime();
            long   lastInputState         = device.getLastInputState(); // 0=OFF and 1= ON
            double runHrs                 = 0.0;
            double AccumulatedEngineHours = device.getLastEngineHours();
            if ( lastInputState == 0 && EngineON != 0L && EngineOFF != 0L && AccumulatedEngineHours == 0.0D ) {
		     runHrs = (double)(EngineOFF - EngineON) / 3600.0; // elapsed hours
		     device.setLastInputState(-1L);
	      }
	      if ( lastInputState == 0 && EngineON != 0L && EngineOFF != 0L && AccumulatedEngineHours > 0.0D ) {
		     runHrs = (double)(EngineOFF - EngineON) / 3600.0; // elapsed hours
		     AccumulatedEngineHours = (AccumulatedEngineHours + runHrs);
		     runHrs = AccumulatedEngineHours;
		     device.setLastInputState(-1L);
	      }
		if ( lastInputState == 0 || lastInputState == 1 && runHrs > 0.0D ) { // save value
		     device.setLastEngineHours(runHrs);
                 EventData evdb = createEventRecord(device,
                 fixtime, statusCode, null,
                 geoPoint, gpsAge, HDOP, numSats,
                 speedKPH, headingDeg, altitudeM, odomKM,
                 gpioInput, batteryV, battLvl,
                 engTempC, engDTC,
                 servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag, runHrs);
                 Print.logInfo("Engine Hours --> " +  runHrs );
		}
        } // end: SIMEVENT_ENGINEHOURS

        /* insert all status code in 'statCodeSet' */
        if (!ListTools.isEmpty(statCodeSet)) {
            // "statCodeSet" should not contain a STATUS_LOCATION code
            for (Integer sci : statCodeSet) {
                int sc = sci.intValue();
                this.insertEventRecord(device,
                    fixtime, sc, null/*geozone*/,
                    geoPoint, gpsAge, HDOP, numSats,
                    speedKPH, headingDeg, altitudeM, odomKM,
                    gpioInput, batteryV, battLvl,
                    engTempC, engDTC,
                    servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
                if (statusCode == sc) {
                    // unlikely, but check anyway
                    statusCode = StatusCodes.STATUS_IGNORE;
                }
            }
            if (statusCode == StatusCodes.STATUS_LOCATION) {
                // we already have other events, skip default STATUS_LOCATION
                statusCode = StatusCodes.STATUS_IGNORE;
            }
        }

        /* Event insertion: status code checks */
        if (statusCode < 0) { // StatusCodes.STATUS_IGNORE
            // -- skip (event ignored)
            Print.logDebug("Ignoring Event (per EventCodeMap)");
        } else
        if (statusCode == StatusCodes.STATUS_IGNORE) {
            // -- skip (event ignored)
            Print.logDebug("Ignoring Event (per EventCodeMap)");
        } else
        if (this.hasSavedEvents()                           &&
            ((statusCode == StatusCodes.STATUS_LOCATION) ||
             (statusCode == StatusCodes.STATUS_NONE)       )  ) {
            // -- skip (already inserted an event above)
        } else
        if (statusCode == StatusCodes.STATUS_NONE) {
            // -- STATUS_NONE and no inserted events ==> convert to "InMotion" or "Location"
            int sc = (speedKPH > 0.0)? StatusCodes.STATUS_MOTION_IN_MOTION : StatusCodes.STATUS_LOCATION;
            this.insertEventRecord(device,
                fixtime, sc, null/*geozone*/,
                geoPoint, gpsAge, HDOP, numSats,
                speedKPH, headingDeg, altitudeM, odomKM,
                gpioInput, batteryV, battLvl,
                engTempC, engDTC,
                servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
        } else
        if (statusCode != StatusCodes.STATUS_LOCATION) {
            // -- Not a "Location" event
            this.insertEventRecord(device,
                fixtime, statusCode, null/*geozone*/,
                geoPoint, gpsAge, HDOP, numSats,
                speedKPH, headingDeg, altitudeM, odomKM,
                gpioInput, batteryV, battLvl,
                engTempC, engDTC,
                servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
        } else
        if (XLATE_LOCATON_INMOTION && (speedKPH > 0.0)) {
            // -- Traslate "Location" to "InMotion"
            int sc = StatusCodes.STATUS_MOTION_IN_MOTION;
            this.insertEventRecord(device,
                fixtime, sc, null/*geozone*/,
                geoPoint, gpsAge, HDOP, numSats,
                speedKPH, headingDeg, altitudeM, odomKM,
                gpioInput, batteryV, battLvl,
                engTempC, engDTC,
                servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
        } else // <-- fixed v2.5.7-B10
        if (validGPS && !device.isNearLastValidLocation(geoPoint,MINIMUM_MOVED_METERS)) {
            // Only include "Location" if not nearby previous event
            this.insertEventRecord(device,
                fixtime, statusCode, null/*geozone*/,
                geoPoint, gpsAge, HDOP, numSats,
                speedKPH, headingDeg, altitudeM, odomKM,
                gpioInput, batteryV, battLvl,
                engTempC, engDTC,
                servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag);
        }

        /* save device changes */
        if (!DEBUG_MODE) {
            try {
                //DBConnection.pushShowExecutedSQL();
                device.updateChangedEventFields();
            } catch (DBException dbe) {
                Print.logException("Unable to update Device: " + accountID + "/" + deviceID, dbe);
            } finally {
                //DBConnection.popShowExecutedSQL();
            }
        }

        /* return ok */
        return true;

    }

    /**
    *** Create EventData record
    **/
    private EventData createEventRecord(Device device,
        long      gpsTime, int statusCode, Geozone geozone,
        GeoPoint  geoPoint, long gpsAge, double HDOP, int numSats,
        double    speedKPH, double heading, double altitudeM, double odomKM,
        long      gpioInput, double batteryV, double battLvl,
        double    engTempC, String engDTC[],
        CellTower servingCell, String mimeType, byte[] imageCamera, double temp_alarm, double fuelLevel1, double fuelLevel2, String FaultCode, String RFIDTag, double runHrs)
    {
        String accountID    = device.getAccountID();
        String deviceID     = device.getDeviceID();
        EventData.Key evKey = new EventData.Key(accountID, deviceID, gpsTime, statusCode);
        EventData evdb      = evKey.getDBRecord();
        evdb.setGeozone(geozone);
        evdb.setGeoPoint(geoPoint);
        evdb.setGpsAge(gpsAge);
        evdb.setHDOP(HDOP);                     // <-- requires "GPSFieldInfo" optional fields
        evdb.setSatelliteCount(numSats);        // <-- requires "GPSFieldInfo" optional fields
        evdb.setSpeedKPH(speedKPH);
        evdb.setHeading(heading);
        evdb.setAltitude(altitudeM);
        evdb.setOdometerKM(odomKM);
        evdb.setInputMask(gpioInput);
        evdb.setBatteryVolts(batteryV);         // <-- requires "GPSFieldInfo" optional fields
        evdb.setBatteryLevel(battLvl);          // <-- requires "GPSFieldInfo" optional fields
        evdb.setServingCellTower(servingCell);  // <-- requires "ServingCellTowerData" optional fields
        evdb.setCoolantTemp(engTempC);          // <-- requires "CANBUSFieldInfo" optional fields
        evdb.setFaultCode_OBDII(engDTC);        // <-- requires "CANBUSFieldInfo" optional fields
        evdb.setAttachType(mimeType);
        evdb.setAttachData(imageCamera);
        // temp_alarm
        evdb.setFuelLevel(fuelLevel1);
        evdb.setFuelLevel2(fuelLevel2);
        evdb.setFaultCode(FaultCode);
        evdb.setRfidTag(RFIDTag);
        evdb.setEngineHours(runHrs); // Sets the engine hours
        return evdb;
    }

    /**
    *** Create/Insert a EventData record
    **/
    private void insertEventRecord(Device device,
        long      gpsTime, int statusCode, Geozone geozone,
        GeoPoint  geoPoint, long gpsAge, double HDOP, int numSats,
        double    speedKPH, double heading, double altitudeM, double odomKM,
        long      gpioInput, double batteryV, double battLvl,
        double    engTempC, String engDTC[],
        CellTower servingCell, String mimeType, byte[] imageCamera, double temp_alarm, double fuelLevel1, double fuelLevel2, String FaultCode, String RFIDTag)
    {

        /* create event */
        EventData evdb = createEventRecord(device,
            gpsTime, statusCode, geozone,
            geoPoint, gpsAge, HDOP, numSats,
            speedKPH, heading, altitudeM, odomKM,
            gpioInput, batteryV, battLvl,
            engTempC, engDTC,
            servingCell, mimeType, imageCamera, temp_alarm, fuelLevel1, fuelLevel2, FaultCode, RFIDTag, 0.0);

        /* insert event */
        // this will display an error if it was unable to store the event
        Print.logInfo("Event: [0x" + StringTools.toHexString(statusCode,16) + "] " +
            StatusCodes.GetDescription(statusCode,null));
        if (!DEBUG_MODE) {
            device.insertEventData(evdb);
            this.incrementSavedEventCount();
        }

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Startup configuration initialization
    **/
    public static void configInit()
    {
        DCServerConfig dcsc = Main.getServerConfig();
        if (dcsc != null) {

            /* common */
            UNIQUEID_PREFIX          = dcsc.getUniquePrefix();
            MINIMUM_SPEED_KPH        = dcsc.getMinimumSpeedKPH(MINIMUM_SPEED_KPH);
            ESTIMATE_ODOMETER        = dcsc.getEstimateOdometer(ESTIMATE_ODOMETER);
            SIMEVENT_GEOZONES        = dcsc.getSimulateGeozones(SIMEVENT_GEOZONES);
            SIMEVENT_DIGITAL_INPUTS  = dcsc.getSimulateDigitalInputs(SIMEVENT_DIGITAL_INPUTS) & 0xFFL;
            XLATE_LOCATON_INMOTION   = dcsc.getStatusLocationInMotion(XLATE_LOCATON_INMOTION);
            USE_LAST_VALID_GPS       = dcsc.getUseLastValidGPSLocation(USE_LAST_VALID_GPS);
            MINIMUM_MOVED_METERS     = dcsc.getMinimumMovedMeters(MINIMUM_MOVED_METERS);

            /* custom */
            PACKET_LEN_END_OF_STREAM = dcsc.getBooleanProperty(Constants.CFG_packetLenEndOfStream, PACKET_LEN_END_OF_STREAM);

        }

    }

}
