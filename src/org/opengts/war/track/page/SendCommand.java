package org.opengts.war.track.page;

import java.util.*;
import java.io.*;
import java.net.*;
import java.math.*;
import java.security.*;

import java.io.PrintWriter;
 
import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.geocoder.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.extra.tables.*;

import org.opengts.war.tools.*;
import org.opengts.war.track.*;
import org.opengts.war.track.page.*;

import static java.lang.System.out;

public class SendCommand extends HttpServlet {	
    
	// ------------------------------------------------------------------------
	public void deleteCommand(String accountID, String deviceID) {
		try { 
				PendingCommands pc[] = PendingCommands.getPendingCommands(accountID, deviceID);
				if (!ListTools.isEmpty(pc)) {                   
					if (pc.length > 0) {
						int sendState = pc[0].getSendState();
						PendingCommands.deletePendingCommands(accountID, deviceID, sendState, -1L);
					}
				} else { /*Print.logWarn("No pending commands for this account/device");*/ }
		} catch (Throwable th) { Print.logException("Error getting commands for: " + accountID+"/"+deviceID, th); }
	}

	// ------------------------------------------------------------------------
	public String checkCommand(String accountID, String deviceID) {
		String command = "no_commands_pending";
		try { 
				
				PendingCommands pc[] = PendingCommands.getPendingCommands(accountID, deviceID);
				if (!ListTools.isEmpty(pc)) {                   
					if (pc.length > 0) {
						command = pc[0].getCommandArgs();
						return command;
					}
				} else { /*Print.logWarn("No pending commands for this account/device");*/ }
			
		} catch (Throwable th) { Print.logException("Error getting commands for: " + accountID+"/"+deviceID, th); }

		return command;
	}
	
		
	
    // ------------------------------------------------------------------------      
	
	public void saveCommand(String accountID, String deviceID, Device device, String cmdType, String cmdID, String[] cmdArgs, HttpServletResponse response) throws ServletException, IOException {
	PrintWriter out = response.getWriter();
	deleteCommand(accountID, deviceID);
	
	try {
	
        if (PendingCommands.insertCommand(device,cmdType,cmdID,cmdArgs)) 
		{ 
			Print.logInfo("Command inserted for: " + accountID+"/"+deviceID);
			//out.println("command inserted");
			
		} 
		else { 
			
			Print.logWarn("Error inserting command for: " + accountID+"/"+deviceID); 
			//out.println("error inserted1 ");
			}
    } catch (DBException dbe) { 
		Print.logException("Error inserting command for: " + accountID+"/"+deviceID, dbe);
	
		out.println("error inserted y catch "+dbe);
		}				
	}
	
	// ------------------------------------------------------------------------
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
              throws ServletException, IOException {
	   
	   response.setContentType("text/html");
	   response.setHeader("Access-Control-Allow-Origin", "*");
	   response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE");
	   response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token, Origin, Authorization");
       PrintWriter out = response.getWriter();   
       
	   String  loginAcctID        = (String)AttributeTools.getSessionAttribute(request, Constants.PARM_ACCOUNT  , ""); // session only
       String  loginUserID        = (String)AttributeTools.getSessionAttribute(request, Constants.PARM_USER     , ""); // session only
       boolean isLoggedIn         = !StringTools.isBlank(loginAcctID);
	   
	   Account acct               = null;
	   Device  dev                = null;
	  
	 
	   // POST Parameters
	   String DeviceID = request.getParameter("DeviceID");
	   String CommandID = request.getParameter("CommandID");
	   String functionName = request.getParameter("functionName");	 
	   loginAcctID = "onoff";
	   //if (isLoggedIn) {
		try {		
			acct = Account.getAccount(loginAcctID);
			if (acct != null) {	
				dev = Device.getDevice(acct, DeviceID);
				//out.println("dev: "+dev); 					 
				if (dev != null) {				
					if(functionName.equals("saveCommand")){					
						saveCommand( loginAcctID, DeviceID, dev, "config", "ack", new String[] { CommandID }, response );
						out.print("command_saved");
					}
					if(functionName.equals("checkCommand")){
						String currentComand = checkCommand(loginAcctID, DeviceID);
						out.print(currentComand);
					}
				}
				else{
					out.print("device_not_found");
				}
			}
			else{
				out.print("account_not_found");
			}
		} 
		catch (DBException dbe) {
			out.print("error_sending"); 
		 }
	   }
	   
     //}    
    
     // ------------------------------------------------------------------------ 
	 
} // end class
