select distinct(`statusCode`), count(*) from EventData group by statusCode


select Device.deviceID, vehicleID, statusCode, latitude, longitude, speedKPH, heading,FROM_UNIXTIME(timestamp), from_unixtime(EventData.creationTime) 
from EventData, Device
where Device.deviceID  = EventData.deviceID
order by timestamp desc

select count(*) from metrics where Reportdate2 is null

select `GpsID`, `ReportDate`, `Latitude`, `Longitude`, `EventID` from `metrics` where `GpsID` = '864180035938476' and `ReportDate` between '2018-09-21' and '2018-09-22' order by `ID` asc limit 1