// ----------------------------------------------------------------------------
// Copyright 2007-2014, GeoTelematic Solutions, Inc.
// All rights reserved
// ----------------------------------------------------------------------------
//
// This source module is PROPRIETARY and CONFIDENTIAL.
// NOT INTENDED FOR PUBLIC RELEASE.
//
// Use of this software is subject to the terms and conditions outlined in
// the 'Commercial' license provided with this software.  If you did not obtain
// a copy of the license with this software please request a copy from the
// Software Provider.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
// Change History:
//  2009/08/07  Martin D. Flynn
//     -Initial release
//  2013/08/27  Martin D. Flynn
//     -Increase FLD_commandArgs size to 256 bytes
// ----------------------------------------------------------------------------
package org.opengts.extra.tables;

import java.lang.*;
import java.util.*;
import java.math.*;
import java.io.*;
import java.sql.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.dbtypes.*;

import org.opengts.db.*;
import org.opengts.db.tables.*;


import static java.lang.System.out;

public class PendingCommands
    extends DeviceRecord<PendingCommands>
{

    // ------------------------------------------------------------------------

    /* these values are store in the table (do not change) */
    public static final int SENDSTATE_PENDING       =  0;
    public static final int SENDSTATE_SUCCESS       =  1;
    public static final int SENDSTATE_FAILED        = 99;

    /* these values are store in the table (do not change) */
    public static final int DELAFTER_NEVER          = 0x00; // do not delete
    public static final int DELAFTER_SUCCESS        = 0x01; // delete after send success
    public static final int DELAFTER_FAILURE        = 0x02; // delete after send success
    public static final int DELAFTER_ACK            = 0x04; // delete after ack

    /* these values are store in the table (do not change) */
    public static final int ACKRESP_WAITING         =  0; // waiting for ack
    public static final int ACKRESP_SUCCESS         = 10; // ack success
    public static final int ACKRESP_FAILED          = 20; // ack failed


    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* keyed FLD_sendState attributes? */
    private static String _sendState_attr()
    {
        // FLD_sendState
        if (!RTConfig.getBoolean(DBConfig.PROP_PendingCommands_keyedSendState,true)) {
            // not keyed
            return "edit=2";
        } else
        if (!DBField.AllowUpdateKeyFields()) {
            // keyed and key-update NOT allowed (default)
            return "key=true";
        } else {
            // keyed and key-update allowed
            return "key=true update";
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // SQL table definition below

    /* table name */
    public static final String _TABLE_NAME              = "PendingCommands";
    public static String TABLE_NAME() { return DBProvider.translateTableName(_TABLE_NAME); }

    /* field definition */
    public static final String FLD_queueTime            = "queueTime";
    public static final String FLD_sendState            = "sendState";      // pending, success, failed
    public static final String FLD_commandType          = "commandType";
    public static final String FLD_commandID            = "commandID";
    public static final String FLD_commandArgs          = "commandArgs";
    public static final String FLD_sendTime             = "sendTime";
    public static final String FLD_deleteAfter          = "deleteAfter";
    public static final String FLD_ackTime              = "ackTime";
    public static final String FLD_ackResponse          = "ackResponse";
    private static DBField FieldInfo[] = {
        // PendingCommands fields
        newField_accountID(true),
        newField_deviceID(true),
        new DBField(FLD_queueTime   , Long.TYPE     , DBField.TYPE_UINT32     , "Queue Time"            , "key=true"),
        new DBField(FLD_commandType , String.class  , DBField.TYPE_STRING(32) , "Command Type"          , "key=true"),
        new DBField(FLD_commandID   , String.class  , DBField.TYPE_STRING(32) , "Command ID"            , "key=true"),
        new DBField(FLD_sendState   , Integer.TYPE  , DBField.TYPE_UINT16     , "Send State"            , PendingCommands._sendState_attr()),
        new DBField(FLD_commandArgs , String.class  , DBField.TYPE_STRING(256), "Command Args"          , "edit=2"),
        new DBField(FLD_sendTime    , Long.TYPE     , DBField.TYPE_UINT32     , "Send Time"             , "edit=2"),
        new DBField(FLD_deleteAfter , Integer.TYPE  , DBField.TYPE_UINT16     , "Delete after condition", "edit=2"),
        new DBField(FLD_ackTime     , Long.TYPE     , DBField.TYPE_UINT32     , "ACK Time"              , "edit=2"),
        new DBField(FLD_ackResponse , Integer.TYPE  , DBField.TYPE_UINT16     , "ACK Response"          , "edit=2"),
        // Common fields
        newField_lastUpdateTime(),
        newField_creationTime(),
    };

    /* key class */
    public static class Key
        extends DeviceKey<PendingCommands>
    {
        public Key() {
            super();
        }
        public Key(String acctId, String devId,
            long queueTime, int sendState,
            String cmdType, String cmdID) {
            super.setKeyValue(FLD_accountID  , ((acctId != null)? acctId.toLowerCase() : ""));
            super.setKeyValue(FLD_deviceID   , ((devId  != null)? devId.toLowerCase()  : ""));
            super.setKeyValue(FLD_queueTime  , queueTime);
            super.setKeyValue(FLD_sendState  , sendState); // show not be a key
            super.setKeyValue(FLD_commandType, cmdType);
            super.setKeyValue(FLD_commandID  , cmdID);
        }
        public DBFactory<PendingCommands> getFactory() {
            return PendingCommands.getFactory();
        }
    }

    /* factory constructor */
    private static DBFactory<PendingCommands> factory = null;
    public static DBFactory<PendingCommands> getFactory()
    {
        if (factory == null) {
            factory = DBFactory.createDBFactory(
                PendingCommands.TABLE_NAME(),
                PendingCommands.FieldInfo,
                DBFactory.KeyType.PRIMARY,
                PendingCommands.class,
                PendingCommands.Key.class,
                true/*editable*/, true/*viewable*/);
            factory.addParentTable(Account.TABLE_NAME());
            factory.addParentTable(Device.TABLE_NAME());
        }
        return factory;
    }

    /* Bean instance */
    public PendingCommands()
    {
        super();
    }

    /* database record */
    public PendingCommands(PendingCommands.Key key)
    {
        super(key);
    }

    // ------------------------------------------------------------------------

    /* table description */
    public static String getTableDescription(Locale loc)
    {
        I18N i18n = I18N.getI18N(PendingCommands.class, loc);
        return i18n.getString("PendingCommands.description",
            "This table contains " +
            "commands which are to be sent to the " +
            "client device the next time it 'checks-in' with the server."
            );
    }

    // SQL table definition above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Bean access fields below

    // ------------------------------------------------------------------------

    public long getQueueTime()
    {
        Long v = (Long)this.getFieldValue(FLD_queueTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setQueueTime(long v)
    {
        this.setFieldValue(FLD_queueTime, v);
    }

    // ------------------------------------------------------------------------

    public int getSendState()
    {
        Integer v = (Integer)this.getFieldValue(FLD_sendState);
        return (v != null)? v.intValue() : SENDSTATE_PENDING;
    }

    public void setSendState(int v)
    {
        this.setFieldValue(FLD_sendState, v);
    }

    public boolean isSendStatePending()
    {
        return (this.getSendState() == SENDSTATE_PENDING);
    }

    // ------------------------------------------------------------------------

    public String getCommandType()
    {
        String v = (String)this.getFieldValue(FLD_commandType);
        return StringTools.trim(v);
    }

    public void setCommandType(String v)
    {
        this.setFieldValue(FLD_commandType, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getCommandID()
    {
        String v = (String)this.getFieldValue(FLD_commandID);
        return StringTools.trim(v);
    }

    public void setCommandID(String v)
    {
        this.setFieldValue(FLD_commandID, StringTools.trim(v));
    }

    // ------------------------------------------------------------------------

    public String getCommandArgs()
    {
        String v = (String)this.getFieldValue(FLD_commandArgs);
        return StringTools.trim(v);
    }

    public void setCommandArgs(String v)
    {
        this.setFieldValue(FLD_commandArgs, StringTools.trim(v));
    }

    public String[] getCommandArgs_array()
    {
        String argStr = this.getCommandArgs();
        return StringTools.parseArray(argStr,'|');
    }

    public void setCommandArgs_array(String v[])
    {
        if (ListTools.size(v) > 0) {
            this.setCommandArgs(StringTools.encodeArray(v,'|',false));
        } else {
            this.setCommandArgs("");
        }
    }

    // ------------------------------------------------------------------------

    public long getSendTime()
    {
        Long v = (Long)this.getFieldValue(FLD_sendTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setSendTime(long v)
    {
        this.setFieldValue(FLD_sendTime, v);
    }

    // ------------------------------------------------------------------------

    public int getDeleteAfter()
    {
        Integer v = (Integer)this.getFieldValue(FLD_deleteAfter);
        return (v != null)? v.intValue() : DELAFTER_NEVER;
    }

    public void setDeleteAfter(int v)
    {
        this.setFieldValue(FLD_deleteAfter, v);
    }

    public boolean isNeverDelete()
    {
        return (this.getDeleteAfter() == DELAFTER_NEVER);
    }

    public boolean isDeleteAfterSendSuccess()
    {
        return ((this.getDeleteAfter() & DELAFTER_SUCCESS) != 0);
    }

    public boolean isDeleteAfterSendFailure()
    {
        return ((this.getDeleteAfter() & DELAFTER_FAILURE) != 0);
    }

    public boolean isDeleteAfterAck()
    {
        return ((this.getDeleteAfter() & DELAFTER_ACK) != 0);
    }

    public boolean deletePendingCommand()
    {
        try {
            //PendingCommands.Key pcKey = this.getRecordKey();
            DBRecordKey<PendingCommands> pcKey = this.getRecordKey();
            pcKey.delete(true); // also delete dependencies
            return true;
        } catch (DBException dbe) {
            Print.logException("Unable to delete PendingCommand", dbe);
            return false;
        }
    }

    // ------------------------------------------------------------------------

    public long getAckTime()
    {
        Long v = (Long)this.getFieldValue(FLD_ackTime);
        return (v != null)? v.longValue() : 0L;
    }

    public void setAckTime(long v)
    {
        this.setFieldValue(FLD_ackTime, v);
    }

    // ------------------------------------------------------------------------

    /* return the ack response */
    public int getAckResponse()
    {
        Integer v = (Integer)this.getFieldValue(FLD_ackResponse);
        return (v != null)? v.intValue() : ACKRESP_WAITING;
    }

    /* set the account type */
    public void setAckResponse(int v)
    {
        this.setFieldValue(FLD_ackResponse, v);
    }

    // Bean access fields above
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /**
    *** Sets this PendingCommands state to sent 'success'/'failed', or deletes
    *** the command if indicated by the record.
    *** @param success  True to indicate success, false to indicate failure
    *** @param sendTime The time the command was sent (or 0 to indicate current time)
    *** @return True if this PendingCommands record was deleted, false otherwise
    **/
    public boolean setCommandSent(boolean success, long sendTime)
    {
        this.setSendState(success? SENDSTATE_SUCCESS : SENDSTATE_FAILED);
        this.setSendTime((sendTime > 0L)? sendTime : DateTime.getCurrentTimeSec());
        if (success && this.isDeleteAfterSendSuccess()) {
            return this.deletePendingCommand();
        } else
        if (!success && this.isDeleteAfterSendFailure()) {
            return this.deletePendingCommand();
        } else {
            try {
                this.update(FLD_sendState, FLD_sendTime);
            } catch (DBException dbe) {
                Print.logException("Unable to save 'sendTime'", dbe);
            }
            return false;
        }
    }

    // ------------------------------------------------------------------------

    /**
    *** This method sends commands through the normal DCS command dispatcher
    *** @return True if the command was successful, false otherwise
    **/
    public String sendCommand()
    {
        String response ="enter function ";
        /* get device */
        Device device = this.getDevice();
        if (device == null) {
           response +="device not found ";
        }

        /* send command */
        String cmdType    = this.getCommandType();
        String cmdID      = this.getCommandID();
        String cmdArgs[]  = this.getCommandArgs_array();
        RTProperties resp = DCServerFactory.sendServerCommand(device, cmdType, cmdID, cmdArgs); // proxy
        boolean success   = DCServerFactory.isCommandResultOK(resp);
        response +="cmdType: "+cmdType + "cmdID: "+cmdID + "cmdArgs: "+cmdArgs + "resp:-- "+ resp ;
        /* mark record as sent, or delete if specified */
        boolean deleted = false;
        if (success) {
            Print.logError("Command succeeded: " + resp);
            deleted = this.setCommandSent(true, -1L);
        } else {
            Print.logError("Command failed: " + resp);
            deleted = this.setCommandSent(false, -1L);
        }

        /* return result */
        return response;

    }

    // ------------------------------------------------------------------------

    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getAccountID());
        sb.append("/");
        sb.append(this.getDeviceID());
        sb.append("/");
        sb.append(this.getQueueTime());
        sb.append("/");
        sb.append(this.getSendState());
        sb.append("/");
        sb.append(this.getCommandType());
        sb.append("/");
        sb.append(this.getCommandID());
        return sb.toString();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* delete pending-command records for specified account/device */
    public static boolean deletePendingCommands(String acctId, String devId,
        int sendState)
        throws DBException
    {
        return PendingCommands.deletePendingCommands(acctId, devId, sendState, -1L);
    }

    /* delete pending-packet records for specified account/device */
    public static boolean deletePendingCommands(String acctId, String devId,
        int sendState, long queueTime)
        throws DBException
    {
        // 'queueTime' may be '-1L' to delete ALL pending packets

        /* invalid account/device? */
        if (StringTools.isBlank(acctId)) {
            return false;
        } else
        if (StringTools.isBlank(devId)) {
            return false;
        }

        /* delete statement */
        try {
            // DBDelete: DELETE FROM PendingCommands WHERE ( accountID='acct' AND deviceID='dev' [AND queueTime<=123456789] )
            DBDelete ddel = new DBDelete(PendingCommands.getFactory());
            DBWhere dwh = ddel.createDBWhere();
            ddel.setWhere(dwh.WHERE_(
                dwh.AND(
                    //dwh.EQ(FLD_accountID,acctId),
                    dwh.EQ(FLD_deviceID ,devId)
                    //((queueTime > 0L)? dwh.LE(FLD_queueTime,queueTime) : null),
                    //((sendState >= 0)? dwh.EQ(FLD_sendState,sendState) : dwh.NE(FLD_sendState,SENDSTATE_PENDING))
                )
            ));
            DBConnection dbc = null;
            try {
                dbc = DBConnection.getDefaultConnection();
                dbc.executeUpdate(ddel.toString());
            } finally {
                DBConnection.release(dbc);
            }
            return true;
        } catch (SQLException sqe) {
            throw new DBException("PendingCommands deletion", sqe);
        }

    }

    // ------------------------------------------------------------------------

    /* get unsent pending-packet records for specified account/device */
    public static PendingCommands[] getPendingCommands(Device dev)
        throws DBException
    {

        /* invalid device */
        if (dev == null) {
            return null;
        }

        /* return commands */
        return PendingCommands.getPendingCommands(dev.getAccountID(), dev.getDeviceID());

    }

    /* get unsent pending-packet records for specified account/device */
    public static PendingCommands[] getPendingCommands(String acctId, String devId)
        throws DBException
    {

        /* invalid account/device? */
        if (StringTools.isBlank(acctId)) {
            return null;
        } else
        if (StringTools.isBlank(devId)) {
            return null;
        }

        /* where clause */
        // DBSelect: WHERE ((accountID=='acct')AND(deviceID='dev')AND(sendState=0)) ORDER BY queueTime;
        DBSelect<PendingCommands> dsel = new DBSelect<PendingCommands>(PendingCommands.getFactory());
        DBWhere dwh = dsel.createDBWhere();
        dsel.setWhere(dwh.WHERE_(
            dwh.AND(
                dwh.EQ(FLD_accountID, acctId),
                dwh.EQ(FLD_deviceID , devId),
                dwh.EQ(FLD_sendState, SENDSTATE_PENDING)
            )
        ));
        dsel.setOrderByFields(FLD_queueTime);

        /* get PendingCommands */
        PendingCommands pc[] = null;
        try {
            DBProvider.lockTables(new String[] { TABLE_NAME() }, null);
            pc = DBRecord.select(dsel); // select:DBSelect
        } finally {
            DBProvider.unlockTables();
        }

        /* no packets? */
        if (pc == null) {
            // no records
            return null;
        }

        /* return array of pending commands */
        return pc;

    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    /* insert packets */
    public static boolean insertCommand(Device dev,
        String cmdType, String cmdID, String cmdArgs[])
        throws DBException
    {

        /* no device? */
        if (dev == null) {
            return false;
        }

        /* insert command */
        return PendingCommands.insertCommand(
            dev.getAccountID(), dev.getDeviceID(),
            cmdType, cmdID, cmdArgs);

    }

    /* insert packets */
    protected static boolean insertCommand(String acctId, String devId,
        String cmdType, String cmdID, String cmdArgs[])
        throws DBException
    {

        /* has account/device */
        if (StringTools.isBlank(acctId) || StringTools.isBlank(devId)) {
            return false;
        }

        /* no command */
        if (StringTools.isBlank(cmdType) || StringTools.isBlank(cmdID)) {
            return false;
        }

        /* create key */
        long nowTime = DateTime.getCurrentTimeSec();
        PendingCommands.Key key = new PendingCommands.Key(acctId, devId,
            nowTime, SENDSTATE_PENDING,
            cmdType, cmdID);
        if (key.exists()) { // may throw DBException
            // already exists
            return false;
        }

        /* insert pending command */
        PendingCommands pc = key.getDBRecord();
        pc.setCommandArgs_array(cmdArgs);
        pc.save();
        return true;

    }

    public static String sendPendingCommands(String acctID, String devID)
    {
        String response ="";
        try { // sendCommand
                PendingCommands pc[] = PendingCommands.getPendingCommands(acctID, devID);
                if (!ListTools.isEmpty(pc)) {
                     response ="no esta vacio";
                    for (int i = 0; i < pc.length; i++) {
                        Print.sysPrintln(i + ") PC: " + pc[i]);
                        String answer = pc[i].sendCommand();
                        response = response + "  SendCommand "+ answer;
                        //Print.sysPrintln("   SendCommand " + (ok +"success":"failed"));
                    }
                } else {
                    response ="No pending commands for this account/device";
                    Print.sysPrintln("No pending commands for this account/device");
                }
            } catch (Throwable th) {
                 response ="Error getting commands for account/device "+ th;
                Print.logException("Error getting commands for account/device", th);
                //System.out.println("error: error pending", th);
                System.exit(1);
            }
        return response;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // Main admin entry point below

    private static final String ARG_ACCOUNT[]   = new String[] { "account", "acct"  };
    private static final String ARG_DEVICE[]    = new String[] { "device" , "dev"   };
    private static final String ARG_LIST[]      = new String[] { "list"             };
    private static final String ARG_ADD[]       = new String[] { "add"              };
    private static final String ARG_DELETE[]    = new String[] { "delete"           };
    private static final String ARG_SEND[]      = new String[] { "send"             };

    private static void usage()
    {
        Print.logInfo("Usage:");
        Print.logInfo("  java ... " + PendingCommands.class.getName() + " {options}");
        Print.logInfo("Common Options:");
        Print.logInfo("  -account=<id>           Acount ID which owns the specified Device");
        Print.logInfo("  -device=<id>            Device ID to apply pending commands");
        Print.logInfo("  -list                   List all pending commands for Device");
        Print.logInfo("  -delete=<time>          Delete pending commands for Device");
        Print.logInfo("  -add=<cmd>              Add pending command for Device");
        System.exit(1);
    }

    public static void main(String args[])
    {
        Print.sysPrintln(args[0]);
        DBConfig.cmdLineInit(args,true);  // main
        String acctID  = RTConfig.getString(ARG_ACCOUNT, "");
        String devID   = RTConfig.getString(ARG_DEVICE , "");
        Print.setIncludeStackFrame(1);

        /* account-id specified? */
        if (StringTools.isBlank(acctID)) {
            Print.sysPrintln("ERROR: Account-ID not specified.");
            usage();
        }

        /* get account */
        Account account = null;
        try {
            account = Account.getAccount(acctID); // may return DBException
            if (account == null) {
                Print.sysPrintln("ERROR: Account-ID does not exist: " + acctID);
                usage();
            }
        } catch (DBException dbe) {
            Print.logException("Error loading Account: " + acctID, dbe);
            //dbe.printException();
            System.exit(99);
        }

        /* device-id specified? */
        if (StringTools.isBlank(devID)) {
            Print.sysPrintln("ERROR: Device-ID not specified.");
            usage();
        }

        /* get device */
        Device device = null;
        try {
            device = Device.getDevice(account, devID, false);
            if (device == null) {
                Print.sysPrintln("ERROR: Device-ID does not exist: " + devID);
                usage();
            }
        } catch (DBException dbe) {
            Print.logException("Error loading Device: " + devID, dbe);
            //dbe.printException();
            System.exit(99);
        }

        /* option count */
        int opts = 0;

        /* add commands */
        // ie. "-add=cmdType/cmdID/cmdArgs"
        if (RTConfig.hasProperty(ARG_ADD)) {
            opts++;
            Print.sysPrintln("");
            String cmd = RTConfig.getString(ARG_ADD,null);
            if (!StringTools.isBlank(cmd)) {
                String C[]       = StringTools.split(cmd,'/');
                String cmdType   = (C.length > 0)? C[0] : "";
                String cmdID     = (C.length > 1)? C[1] : "";
                String cmdArgs[] = (C.length > 2)? StringTools.split(C[2],',') : null;
                try {
                    if (PendingCommands.insertCommand(device,cmdType,cmdID,cmdArgs)) {
                        Print.sysPrintln("Command inserted: " + cmdType+"/"+cmdID);
                    } else {
                        Print.sysPrintln("Error inserting command: " + cmdType+"/"+cmdID);
                    }
                } catch (DBException dbe) {
                    Print.logException("Error inserting command: " + cmdType+"/"+cmdID, dbe);
                    System.exit(99);
                }
            } else {
                Print.sysPrintln("ERROR: Missing 'command' ...");
                usage();
            }
            System.exit(0);
        }

        /* set "send" */
        // ie. "-send"
        if (RTConfig.hasProperty(ARG_SEND)) {
            opts++;
            Print.sysPrintln("");
            try { // sendCommand
                PendingCommands pc[] = PendingCommands.getPendingCommands(acctID, devID);
                if (!ListTools.isEmpty(pc)) {
                    for (int i = 0; i < pc.length; i++) {
                        Print.sysPrintln(i + ") PC: " + pc[i]);
                        //boolean ok = pc[i].sendCommand();
                        //Print.sysPrintln("   SendCommand " + (ok?"success":"failed"));
                    }
                } else {
                    Print.sysPrintln("No pending commands for this account/device");
                }
            } catch (Throwable th) {
                Print.logException("Error getting commands for account/device", th);
                System.exit(1);
            }
            System.exit(0);
        }

        /* list current commands */
        // ie. "-list"
        if (RTConfig.hasProperty(ARG_LIST)) {
            opts++;
            Print.sysPrintln("");
            try {
                PendingCommands pc[] = PendingCommands.getPendingCommands(acctID, devID);
                if (!ListTools.isEmpty(pc)) {
                    for (int i = 0; i < pc.length; i++) {
                        Print.sysPrintln(i + ") PC: " + pc[i]);
                    }
                } else {
                    Print.sysPrintln("No pending commands for this account/device");
                }
            } catch (Throwable th) {
                Print.logException("Error getting commands for account/device", th);
                System.exit(1);
            }
            System.exit(0);
        }

        /* delete existing pending commands */
        // ie. "-delete=<time>"    (delete records with a queue time older than <time>)
        if (RTConfig.hasProperty(ARG_DELETE)) {
            opts++;
            Print.sysPrintln("");
            long queTime = RTConfig.getLong(ARG_DELETE, -2L);
            if ((queTime == -1L) || (queTime > 0L)) {
                try {
                    PendingCommands.deletePendingCommands(acctID, devID, -1, queTime);
                    if (queTime == -1L) {
                        Print.sysPrintln("Deleted all sent commands");
                    } else {
                        Print.sysPrintln("Deleted sent commands up to, and including " + queTime);
                    }
                } catch (Throwable th) {
                    Print.logException("Error while deleting commands for account/device", th);
                    System.exit(1);
                }
                System.exit(0);
            } else {
                Print.sysPrintln("ERROR: Invalid time specified: " + RTConfig.getString(ARG_DELETE,""));
                usage();
            }
        }

        /* no options specified */
        if (opts == 0) {
            Print.logWarn("Missing options ...");
            usage();
        }

    }

}
