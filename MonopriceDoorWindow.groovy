/**
 *  Monoprice Door/Window Sensor
 *
 *  Capabilities: Contact, Battery
 *
 *	Author: Adam Heinmiller
 *  Original Author: FlorianZ
 
 *  Date: 2014-02-24
 */

metadata 
{
	definition (name: "Monoprice Window Sensor", author: "Adam Heinmiller") 
    {
        capability "Contact Sensor"
		capability "Battery"
        
        attribute "tamper", "number"
        
        fingerprint deviceId:"0x2001", inClusters:"0x71, 0x85, 0x80, 0x72, 0x30, 0x86, 0x84"
	}

    simulator 
    {
        status "open":  "command: 2001, payload: FF"
        status "closed": "command: 2001, payload: 00"
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

def updated()
{
	state.lastBatteryRequested = null
}

def getTimestamp() {
    new Date().time
}

def shouldRequestBattery() {
    if (!state.lastBatteryRequested) {
        return true
    }
    return (getTimestamp() - state.lastBatteryRequested) > 24*60*60*1000
}

def markLastBatteryRequested() {
    state.lastBatteryRequested = getTimestamp()
}

def parse(String description) 
{
    def result = []
    
    def cmd = zwave.parse(description, [0x20: 1, 0x80: 1, 0x84: 1, 0x71: 3])
    
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

    log.debug "Parse returned ${result}"
    
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) 
{
    def map = [:]
    map.name = "contact"
    map.value = cmd.value ? "open" : "closed"
    map.descriptionText = cmd.value ? "${device.displayName} is open" : "${device.displayName} is closed"
    return map
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) 
{
    def map = [:]
    map.value = "";
    map.descriptionText = "${device.displayName} woke up"
    return map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) 
{
    markLastBatteryRequested()
    
    def map = [:]
    map.name = "battery"
    map.unit = "%"
    
    if (cmd.batteryLevel == 0xFF) 
    {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
    } 
    else 
    {
        map.value = cmd.batteryLevel
    }
    
    return map
}


def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) 
{
	def map = null
    
    if (cmd.event == 3) map = [name: "tamper", value: 1, descriptionText: "${device.displayName} case has opened"]

    return map
}


def resetTamperIndicator()
{
	sendEvent(name: "tamper", value: 0, descriptionText: "${device.displayName} case has closed")
}



def zwaveEvent(physicalgraph.zwave.Command cmd) 
{
    // Catch-all handler. The sensor does return some alarm values, which
    // could be useful if handled correctly (tamper alarm, etc.)
    [descriptionText: "Unhandled: ${device.displayName}: ${cmd}", displayed: false]
}