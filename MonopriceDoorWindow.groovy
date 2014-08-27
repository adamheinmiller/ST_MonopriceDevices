/**
 *  Monoprice Door/Window Sensor
 *
 *  Capabilities: Contact, Battery
 *
 *	Author: Adam Heinmiller
 *  Original Author: FlorianZ
 *
 *  Date: 2014-07-1
 */

metadata 
{
	definition (namespace: "com.github.adamheinmiller.", name: "Monoprice Door/Window Sensor", author: "Adam Heinmiller") 
    {
        capability "Contact Sensor"
		capability "Battery"
        
        attribute "tamper", "number"
        
        fingerprint deviceId:"0x2001", inClusters:"0x71, 0x85, 0x80, 0x72, 0x30, 0x86, 0x84"
	}

    simulator 
    {
        status "Contact Open":  "command: 7105, payload: 07 FF 00 FF 07 02 00 00"
        status "Contact Closed": "command: 7105, payload: 07 00 00 FF 07 02 00 00"
        
        status "External Sensor Open":  "command: 7105, payload: 07 FF 00 FF 07 FE 00 00"
        status "External Sensor Closed": "command: 7105, payload: 07 00 00 FF 07 FE 00 00"
 
        status "Case Opened":  "command: 7105, payload: 07 FF 00 FF 07 03 00 00"
        status "Case Closed": "command: 8407, payload: 00"

		status "Battery Status": "command: 8003, payload: 1F"
    }

    tiles 
    {
        standardTile("contact", "device.contact", width: 2, height: 2) 
        {
            state "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
            state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
        }
        
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") 
        {
            state "battery", label:'${currentValue}% battery', unit:""
        }
        
        valueTile("tamper", "device.tamper")
        {
        	state "default", label: "Tamper", backgroundColors:[ [value: 0, color: "#FFFFFF"], [value: 1, color: "#FF0000"] ]
        }


        main "contact"
        details(["contact", "battery", "tamper"])
    }
}

def installed()
{
	updated()
}

def updated()
{
	state.lastBatteryRequested = null
    
    state.PinState = "closed"
}

def getTimestamp() 
{
    return new Date().time
}

def shouldRequestBattery() 
{
    if (!state.lastBatteryRequested) 
    {
        return true
    }
    
    return (getTimestamp() - state.lastBatteryRequested) > 24*60*60*1000
}

def markLastBatteryRequested() 
{
    state.lastBatteryRequested = getTimestamp()
}

def parse(String description) 
{
    def result = []
    
    def cmd = zwave.parse(description, [0x80: 1, 0x84: 1, 0x71: 3])
    
    
    // log.debug "Command: $description"
    
    if (cmd) 
    {
        // Did the sensor just wake up?
        if (cmd.CMD == "8407") 
        {
            // Request the battery level?
            if (shouldRequestBattery()) 
            {
            	log.debug "Requesting Battery Update"
                result << response(zwave.batteryV1.batteryGet())
                result << response("delay 1200")
            }
            
            // Clear tampered event
            resetTamperIndicator()
            
            result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
        }
        
        result << createEvent(zwaveEvent(cmd))
	}
    
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) 
{
    logCommand(cmd)

	return [value: "", descriptionText: "${device.displayName} woke up"]
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) 
{
    logCommand(cmd)

	markLastBatteryRequested()
    
    
    def map = [name: "battery", unit: "%"]
    
    if (cmd.batteryLevel == 0xFF) 
    {
    	map += [value: 1, descriptionText: "${device.displayName} has a low battery", isStateChange: true]    	
    } 
    else 
    {
    	map += [value: cmd.batteryLevel]
    }
    
    return map
}


def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) 
{
    logCommand(cmd)

	
    def map = null
    
    if (cmd.event == 3) map = [name: "tamper", value: 1, descriptionText: "${device.displayName} case has opened"]
    else if (cmd.event == 2)
    {
    	state.ContactState = cmd.v1AlarmLevel ? "open" : "closed"
        
    	map = [name: "contact", value: state.ContactState, descriptionText: cmd.v1AlarmLevel ? "${device.displayName} is open" : "${device.displayName} is closed"]
	
    	if (state.ContactState == "closed" && state.PinState == "open") map = null;
    }
    else if (cmd.event == 254) 
    {
    	state.PinState = cmd.v1AlarmLevel ? "open" : "closed"

		map = [name: "contact", value: state.PinState, descriptionText: cmd.v1AlarmLevel ? "${device.displayName} external sensor is open" : "${device.displayName} external sensor is closed"]

    	if (state.PinState == "closed" && state.ContactState == "open") map = null;
	}
    
    log.debug "Contact: ${state.ContactState}, External: ${state.PinState}"

    return map
}


def resetTamperIndicator()
{
	sendEvent(name: "tamper", value: 0, descriptionText: "${device.displayName} case has closed")
}


def zwaveEvent(physicalgraph.zwave.Command cmd) 
{
    logCommand(cmd)

	return [descriptionText: "Unhandled: ${device.displayName}: ${cmd}", displayed: false]
}


def logCommand(cmd)
{
	log.debug "Device Command:  $cmd"
}
