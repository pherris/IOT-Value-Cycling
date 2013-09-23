Cycling Telemetry Data
================================
The ability to connect any number of devices to the Internet of Things is a paradigm shift that creates exciting possibilities for existing organizations; however articulating the benefits can be challenging internally. This post shows the results of my efforts to showcase the power of the Internet of Things and 2lemetry.

The first challenge I faced was selecting a data set that a) applies to the business b) people can relate to and c) can provide some meaningful visualizations. Being in the transportation industry I wanted data that both represented location and information about what was happening at that location at that time. I toyed with the idea of generating a data set, but a straight line isn't that interesting! 

Finally I realized that as a cyclist, I have lots of telemetry data from my bike rides. This data set was perfect - not only does my Garmin Edge 500 track my current location in one second intervals, it also captures my Heart Rate, Cadence, Speed, Power, Distance etc. It turns out to be quite a rich data set!  The personal nature of my data set made it easy for me to talk to the power of the Internet of Things with excitement and enthusiasm (key in socalizing in your organization!). Finally, once the data was being replayed, it was a small leap to imagine adding internet connectivity to the same device and seeing this data in real time from thousands of devices!
 
In this post, I will show you how to visualize and share the data collected from your rides using MQTT, a Java publisher and Websockets. Don't worry if you don't have this type of data - you can download a sample file below!

Steps
================================
1. Publish ride data into 2lemetry. This requires:
   * A free account on [2lemetry's Platform] [1]
   * A Java IDE (I use [Eclipse] [2]) or mad Java skills and no IDE
   * A Garmin GPX file (download one of yours from [connect.garmin.com] [3] or one of mine from [GitHub])
2. Visualize the published data in your browser. This requires:
   * Some HTML code
   * A web server (optionally)

[1]: http://app.m2m.io/signup "2lemetry's Platform"
[2]: http://eclipse.org/downloads/ "Eclipse"
[3]: https://connect.garmin.com/ "connect.garmin.com"
[4]: https://github.com/pherris/MQTT-Cycling-Viewer "GitHub"

Publishing Your Ride
================================
For the first step we will create a new Java project which will be used to read the Garmin GPX file and send the data collected to the Internet of Things. If you aren't familiar with Java or Maven or XML you can use your own solution - the main thing is to publish each "TrackPoint" from the GPX file as a JSON object that looks like this (yes, everything is sent as a string):
```
{

	"name": 				"name_of_your_ride (can use the file name)",
	"latitude": 			"44.74898999556899",
	"longitude": 			"-92.80862563289702",
	"altitudeMeters":   	"206.8000030517578",
	"distanceMeters":   	"31.040000915527344",
	"timeUTC": 				"2013-07-27T12:37:40.000Z",
	"timeMillis": 			"1374928660000", 
	"heartRate": 			"145",
	"cadence":				"91",
	"speedMetersPerSecond": "4.953000068664551",
	"watts": 				"296"
}
```

In your IDE create a new Maven project (skipping the archetype selection) and update your POM to match the XML below. This application is called ridepub but you can change it to whatever you need.

```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.pherris</groupId>
  <artifactId>IOT-Value-Cycling</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>jar</packaging>

  <name>IOT-Value-Cycling</name>
  <url>http://maven.apache.org</url>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <releases>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
        <checksumPolicy>warn</checksumPolicy>
      </releases>
      <snapshots>
        <enabled>true</enabled>
        <updatePolicy>never</updatePolicy>
        <checksumPolicy>fail</checksumPolicy>
      </snapshots>
      <id>eclipse</id>
      <name>Eclipse Repo</name>
      <url>https://repo.eclipse.org/content/repositories/paho-releases</url>
      <layout>default</layout>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>3.8.1</version>
      <scope>test</scope>
    </dependency>
    <!--  PAHO: MQTT client -->
    <dependency>
      <groupId>org.eclipse.paho</groupId>
      <artifactId>mqtt-client</artifactId>
      <version>0.4.0</version>
    </dependency>
    <!--  Gson: Java to Json conversion -->
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.2.4</version>
      <scope>compile</scope>
    </dependency>
    <!--  time utility -->
    <dependency>
      <groupId>joda-time</groupId>
      <artifactId>joda-time</artifactId>
      <version>1.5.2</version>
    </dependency>
  </dependencies>
    	<build>
		<plugins>
			<plugin>
	    		<groupId>org.apache.maven.plugins</groupId>
	    		<artifactId>maven-jar-plugin</artifactId>
    			<version>2.2</version>
    			<!-- nothing here -->
  			</plugin>
  			<plugin>
    			<groupId>org.apache.maven.plugins</groupId>
    			<artifactId>maven-assembly-plugin</artifactId>
    			<version>2.2-beta-4</version>
    			<configuration>
	      			<descriptorRefs>
	        			<descriptorRef>jar-with-dependencies</descriptorRef>
	      			</descriptorRefs>
	      			<archive>
	        			<manifest>
	          				<mainClass>com.pherris.CyclingPublisher</mainClass>
	        			</manifest>
	      			</archive>
    			</configuration>
    			<executions>
      				<execution>
        				<phase>package</phase>
        				<goals>
          					<goal>single</goal>
        				</goals>
      				</execution>
   				</executions>
  			</plugin>
		</plugins>
	</build>
</project>
```

Create your package structure (the example uses com.pherris) and add a class called CyclingPublisher.java. This program will accept the name (with full path) of your TCX file and publish it to q.m2m.io:

```
package com.pherris;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class CyclingPublisher implements MqttCallback {

	MqttClient myClient;
	MqttConnectOptions connOpt;
	MqttTopic topic;
	Gson gson = new Gson();

	static final String BROKER_URL = "tcp://q.m2m.io:1883";
	static final String M2MIO_DOMAIN = ""; // your domain (from 2lemetry)
	static final String M2MIO_USERNAME = ""; // your username
	static final String M2MIO_PASSWORD_MD5 = ""; // your pwd (help.m2m.io)

	public static void main(String args[]) {
		CyclingPublisher publisher = new CyclingPublisher();
		publisher.connect();
		publisher.parseXml(args[0]);
	}

	/**
	 * Accepts a full path and file name to GPX file
	 * 
	 * @param fileLocation
	 */
	private void parseXml(String fileLocation) {
		try {

			File gpxFile = new File(fileLocation);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(gpxFile);
			doc.getDocumentElement().normalize();

			NodeList nodes = doc.getElementsByTagName("Trackpoint");

			long lastPoint = 0;

			for (int i = 0; i < nodes.getLength(); i++) {
				// can move parsing logic into new method for testability
				Node node = nodes.item(i);

				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) node;
					long currentPoint = getTimeStampMillis(getValue("Time", element));

					Thread.sleep(lastPoint == 0 ? 0 : currentPoint - lastPoint); // delay before publish - first one immediately, as it happened after that

					JsonObject point = new JsonObject();
					point.addProperty("name", gpxFile.getName());

					point.addProperty("latitude", getValue("LatitudeDegrees", element));
					point.addProperty("longitude", getValue("LongitudeDegrees", element));
					point.addProperty("altitudeMeters", getValue("AltitudeMeters", element));
					point.addProperty("distanceMeters", getValue("DistanceMeters", element));

					point.addProperty("timeUTC", getValue("Time", element));
					point.addProperty("timeMillis", currentPoint);

					point.addProperty("heartRate", getValue("Value", element));
					point.addProperty("cadence", getValue("Cadence", element));
					point.addProperty("speedMetersPerSecond", getValue("Speed", element));
					point.addProperty("watts", getValue("Watts", element));

					publishMessage(point.toString());

					lastPoint = currentPoint;
				}
			}
		} catch (Exception ex) {
			// catch all exceptions and log for debugging
			ex.printStackTrace();
		}
	}

	/**
	 * Accepts a string payload and publishes via MQTT at QoS 0
	 * 
	 * @param payload
	 */
	private void publishMessage(String payload) {
		System.out.println(payload);
		int pubQoS = 0;
		MqttMessage message = new MqttMessage(payload.getBytes());
		message.setQos(pubQoS);
		message.setRetained(false);

		try {
			// publish message to broker
			topic.publish(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * connects to broker and keeps connection alive
	 * 
	 */
	private void connect() {
		System.out.println("Connect to: " + BROKER_URL);
		// setup MQTT Client
		String clientID = M2MIO_USERNAME;
		connOpt = new MqttConnectOptions();

		connOpt.setCleanSession(true);
		connOpt.setKeepAliveInterval(30);
		connOpt.setUserName(M2MIO_USERNAME);
		connOpt.setPassword(M2MIO_PASSWORD_MD5.toCharArray());

		// Connect to Broker
		try {
			myClient = new MqttClient(BROKER_URL, clientID);
			myClient.setCallback(this);
			myClient.connect(connOpt);
		} catch (MqttException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		// setup topic
		topic = myClient.getTopic(M2MIO_DOMAIN + "/bike/ride");
	}

	/**
	 * Helper method to get values from the xml object. If a particular object does not have the tag you are looking for an empty string will be returned.
	 * 
	 * @param tag
	 *            the XML element you are looking for
	 * @param element
	 *            the parent XML object
	 * @return String value of the element you seek
	 */
	private static String getValue(String tag, Element element) {
		try {
			NodeList nodes = element.getElementsByTagName(tag).item(0).getChildNodes();
			Node node = nodes.item(0);
			return node.getNodeValue();
		} catch (NullPointerException npe) {
			// fail quietly since not all rides have all values (e.g. power) etc...
			return "";
		}
	}

	/**
	 * Helper method to take the ISO formatted date string and return milliseconds
	 * 
	 * @param timestamp
	 * @return
	 */
	private static long getTimeStampMillis(String timestamp) {
		DateTimeFormatter parser = ISODateTimeFormat.dateTime();
		DateTime dt = parser.parseDateTime(timestamp);

		return dt.getMillis();
	}

	public void connectionLost(Throwable arg0) {
		// TODO Auto-generated method stub
		System.out.println("lost connection");
		connect(M2MIO_DOMAIN + "/" + M2MIO_STUFF + "/" + M2MIO_THING);

	}

	public void deliveryComplete(IMqttDeliveryToken arg0) {
		// TODO Auto-generated method stub
		System.out.println("delivery complete");

	}

	public void messageArrived(String arg0, MqttMessage arg1) throws Exception {
		System.out.println("message arrived - thats surprising");
		// TODO Auto-generated method stub

	}

}
```

Configuration
-------------------------
Whew, that was a lot of Copy and Paste!  Tweak the configuration settings below to get your code up and running.
```
	static final String BROKER_URL = 			"tcp://q.m2m.io:1883";
	static final String M2MIO_DOMAIN = 			""; //Your domain, procured from m2m.io
	static final String M2MIO_USERNAME = 		""; // your username
	static final String M2MIO_PASSWORD_MD5 = 	""; // your pwd (help.m2m.io)
```
Method Overview
-------------------------
This is a quick description of each method and what it does. Definitely a section you can skip if things are going well!

* Main: This is the entry point into your application and should be passed your file name with path as the first argument.
* parseXml: Handles opening your file, looping through the XML and sending each point as JSON to m2m.io.
* publishMessage: Handles publishing your JSON to m2m.io.
* connect: Connects your application to m2m.io for sending MQTT data.
* getValue: helper method to extract values from the XML object.
* getTimeStampMillis: helper method to parse the ISO date format in your XML to milliseconds.
* connectionLost: called when the MQTT client detects loss of connectivity to m2m.io.
* deliveryComplete: called when a MQTT message is delivered. 
* messageArrived: called when a message arrives. We will not be using this method since we are not subscribing to topics.

Running the Application
-------------------------
You can run the application from within Eclipse by selecting Run -> Run Configurations. Find this project/class under Java Application and select it (you might have to run it once first for it to show up!). On the right, select the Arguments tab and enter the full file path to your TCX file. Select Apply and Run.

You can also use Maven to export a Jar to be run from the command line with the first parameter being the full path to your TCX file.
```
mvn package
java -jar target/IOT-Value-Cycling-0.0.1-SNAPSHOT-jar-with-dependencies.jar rides/activity_349469173.tcx
```
Once the application is running without errors you should see each point of the cycling data that was published to m2m.io. Note that data is published at the same interval it was captured  (this means that if you stopped your bike and your bike computer 'auto paused' (that's cheating by the way ;), you will not see any data being published for the same amount of time you were stopped).

Viewing Your Ride
================================

Now that you are publishing your ride via m2m.io it's time to visualize the data you worked so hard to collect and send! To accomplish this, we will put together a simple (one page) HTML5 application that uses [Google Maps] [1] and [Google Visualizations] [2] to replay your ride.

[1]: https://developers.google.com/maps/documentation/javascript/reference "Google Maps"
[2]: https://developers.google.com/chart/interactive/docs/reference "Google Visualizations"

```
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
  <head>
    <meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
    <style type="text/css">
      html { height: 100% }
      body { height: 100%; margin: 0; padding: 0 }
      #map-canvas { height: 100% }
    </style>
    
    <script type="text/javascript" src="http://socketmq.m2m.io/socket.io/socket.io.js"></script>
	<script type="text/javascript" src="http://socketmq.m2m.io/eventemitter-4.0.3.min.js"></script>
	<script type="text/javascript" src="http://socketmq.m2m.io/socketmq-1.0.1.js"></script>
    <script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript" src="https://maps.googleapis.com/maps/api/js?sensor=false"></script>
    <script type="text/javascript">
    	//IE Fix
    	if (console == null || console.log == null) {
    	    var console = {
    	            log: function () {
    	                //no-op
    	            }
    	    };
    	}
    	
    	if (JSON == null || JSON.stringify == null) {
    	    var JSON = {
    	            stringify: function () {
    	                //no-op
    	            }
    	    };
    	}
	    
	    function initialize() {
	        console.log("initalize");
	        
	        var mapOptions = {
	            center: new google.maps.LatLng(39.8282, -98.5795),
	            zoom: 4,
	            mapTypeId: google.maps.MapTypeId.ROADMAP
	        };
	        var map = new google.maps.Map(document.getElementById("map-canvas"), mapOptions);
	        
	        var com = {
		            pherris: {
		                marker: 	null,
		                polyLine: 	(function () { 
		                    var polyLine = new google.maps.Polyline({
                                strokeColor: "#15317E",
                                strokeOpacity: 1.0,
                                strokeWeight: 4,
                                path: new Array() 
		                    });
		                    polyLine.setMap(map);
		                    return polyLine;
		                })(),
		                
		                /**
		                 * draws a line with a marker at the front.
		                 **/
		                mapPoint: 	function (mqttMessage) {
		                    var point = mqttMessage.message;
		                    
		                    if (typeof point.latitude == "string") {
		                        point.latitude  = parseFloat(point.latitude);
		                        point.longitude = parseFloat(point.longitude);
		                        point.speedMetersPerSecond = parseFloat(point.speedMetersPerSecond);
	                        }
	                        
		                    //throw out bad GPS
		                    if (point.latitude.toFixed().replace("-", "") == "0" || 
		                            point.longitude.toFixed().replace("-", "") == "0") {
	                            return;
	                        }
		                    
		                    var latLng = new google.maps.LatLng(point.latitude, point.longitude);
		                    
		                    map.setCenter(latLng);
		                    
		                    com.pherris.polyLine.getPath().push(latLng);
		                    
		                    //clear old marker
		                    if (com.pherris.marker) {
		                        com.pherris.marker.setMap(null);
		                    } else {
		                        //zoom to marker
		                        map.setZoom(14);
		                    }
		                    
		                    //drop new marker at front
		                    com.pherris.marker = new google.maps.Marker({
						        position: latLng,
						        map: map,
						        title: "Speed:  " + (point.speedMetersPerSecond * 60 * 60 /1000).toFixed(2) + " km/h"
						    });
		                },
		                
		                visualizeSpeed: function (mqttMessage) {
		                    var point = mqttMessage.message,
		                        gaugeData = new google.visualization.DataTable();
	                            gaugeData.addColumn('number', 'Speed km');
	                            gaugeData.addRows(1);
	                            gaugeData.setCell(0, 0, Math.round(point.speedMetersPerSecond * 60 * 60 /1000)),
	                        	gaugeOptions = {
	                                width: 180, 
	                                height: 180, 
	                                min: 0, 
	                                max: 65, 
	                                yellowFrom: 40, 
	                                yellowTo: 55,
	                            	redFrom: 55, 
	                            	redTo: 65, 
	                            	minorTicks: 2
                            	};
	                        var gauge = new google.visualization.Gauge(document.getElementById('vis_speed'));
                          	gauge.draw(gaugeData, gaugeOptions);
		                },
		                
		                visualizeHeartRate: function (mqttMessage) {
		                    var point = mqttMessage.message;
		                    document.getElementById('vis_heartRate').innerHTML = "HR: " + point.heartRate;
		                },
		                
		                visualizeCadence: function (mqttMessage) {
		                    var point = mqttMessage.message;
		                    document.getElementById('vis_watts').innerHTML = "Power: " + point.watts + " watts";
		                },
		                
		                visualizeWatts: function (mqttMessage) {
		                    var point = mqttMessage.message;
		                    document.getElementById('vis_cadence').innerHTML = "Cadence: " + point.cadence;
		                },
		                
		                visualizeDistance: function (mqttMessage) {
		                    var point = mqttMessage.message;
		                    document.getElementById('vis_distance').innerHTML = "Distance: " + (parseFloat(point.distanceMeters)/1000).toFixed(2) + " k";
		                }
		                
		            },
		            
		            m2m: {
		                connect: function () {
		                    console.log("connect");
		                    
	                        var socket = new SocketMQ({
	                            username: 		'',	//your username
	                            md5Password: 	'', //your password
	                            subscribe: [{
	                                topic: ['{domain}/bike/ride'],    //your {domain}
	                                qos: 0
	                            }],
	                            ping: true
	                        });

	                        // Optional Event Listeners
	                        socket.on('debug', function(debug) {
	                            console.log('----- debug -----');
	                            console.log(JSON.stringify(debug));
	                        });

	                        socket.on('error', function(error) {
	                            console.log('----- error -----');
	                            console.log(JSON.stringify(error));
	                        });

	                        socket.on('exception', function(exception) {
	                            console.log('----- exception -----');
	                            console.log(JSON.stringify(exception));
	                        });

	                        socket.on('connected', function() {
	                            console.log('----- connected -----');
	                        });

	                        socket.on('data', function(data) {
	                            com.pherris.mapPoint(data);
	                            com.pherris.visualizeSpeed(data);
	                            com.pherris.visualizeHeartRate(data);
	                            com.pherris.visualizeCadence(data);
	                            com.pherris.visualizeWatts(data);
	                            com.pherris.visualizeDistance(data);
	                            
	                        });

	                        socket.on('subscribed', function(subscribed) {
	                            console.log('----- subscribed -----');
	                            console.log(JSON.stringify(subscribed));
	                        });

	                        socket.on('unsubscribed', function(subscribed) {
	                            console.log('----- unsubscribed -----');
	                            console.log(JSON.stringify(subscribed));
	                        });

	                        socket.on('disconnected', function() {
	                            console.log('----- disconnected -----');
	                        });

	                        socket.connect();
		                }
		            }
		    };
		    
		    com.m2m.connect();
	    }
    	google.load("visualization", "1", {packages:["corechart","gauge"]});
	    google.maps.event.addDomListener(window, 'load', initialize);
	    
    </script>
</head>
<body>
<div style="float:right;width:200px;height:100%;padding:10px;background-color:darkgray;color:white;font-family:arial;">
	<div id="vis_speed"></div>
	<div id="vis_heartRate"></div>
	<div id="vis_cadence"></div>
	<div id="vis_watts"></div>
	<div id="vis_distance"></div>
</div>
<div id="map-canvas"></div>
</body>
</html>
```
A great next step is to add some new visualizations to your application - check out what Google has to offer!

Configuration
-------------------------
Here we use the same account you set up with 2lemetry to publish your data. You are welcome to use a different user, just so long as they are on the same domain and have the rights :)
```
var socket = new SocketMQ({
    username: 		'',         //Your user name (procured from http://help.m2m.io, same account as the Java code)
    md5Password: 	'',         //Your md5sum password (procured from http://help.m2m.io, same account as the Java code)
    subscribe: [{
        topic: ['{domain}/bike/ride'], //Replace {domain} with your domain (procured from http://help.m2m.io, same account as the Java code). Leave /bike/ride
        qos: 0
    }],
    ping: true
});
```
Method Overview
-------------------------
Another section you are welcome to skip - just an overview of what each part of the JavaScript is up to.
* initialize: This is the entry point into the web application and is called by Google when ready to render. This method creates the com.pherris object which contains all methods used to render your ride.
* com.pherris.marker: variable that holds the currently displayed marker on the map (used to keep the marker always at the front of the line).
* com.pherris.polyLine: object used to render the 'path' of your ride.
* com.pherris.mapPoint: method that draws the line representing your ride and drops the marker at the front.
* com.pherris.visualizeSpeed: method to show a little more complex visualization of one of the data points received via MQTT.
* com.pherris.visualizeHeartRate: displays current HR on the screen
* com.pherris.visualizeCadence: displays current cadence on the screen
* com.pherris.visualizeWatts: displays current power on the screen
* com.pherris.visualizeDistance: displays overall distance on the screen
* com.m2m.connect: connects to m2m.io and subscribes to your topic. Hands data off to mapPoint and visualize* methods.

Running the Application
-------------------------

To view this file you can simply open it in your web browser locally.  If you are publishing data with your Java app, you will see your ride replayed on your browser! To share your excellent technical and cycling skills with the world throw it up on any web server.

For quick projects in Apache I like to use the Alias feature:

```
Alias /2lemetry "C:/Users/username/workspace/ridpub/web/"
```

Finding TCX Files
-------------------------
If you use Garmin Connect you can log into your account, go to the Analyze tab and select Activities. Select your ride from the list. On the ride detail screen you will see an "Export" button. Select TCX and your file should download.

You can also download a sample ride from the GitHub repo: https://github.com/pherris/IOT-Value-Cycling under the rides/ directory.




Historical Data
-------------------------
So far, we have written a simple application to parse data into our 2lemetry account and used a simple Google Maps visualization to map the data as it is received in real time. 

Taking one step further, how do we query the data out of 2lemetry after it is loaded? With a second HTML visualization using Google Maps, we can query the 2lemetry API to get a data set for a specified range of time.

```
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
<head>
<meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
<style type="text/css">
html {
	height: 100%
}

body {
	height: 100%;
	margin: 0;
	padding: 0
}

#map-canvas {
	height: 100%
}
</style>

<script type="text/javascript"
	src="https://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript"
	src="https://maps.googleapis.com/maps/api/js?sensor=false"></script>
<script type="text/javascript">
	//IE Fix
	if (console == null || console.log == null) {
		var console = {
			log : function() {
				//no-op
			}
		};
	}

	if (JSON == null || JSON.stringify == null) {
		var JSON = {
			stringify : function() {
				//no-op
			}
		};
	}

	function initialize() {
		console.log("initalize");

		var mapOptions = {
			center : new google.maps.LatLng(39.8282, -98.5795),
			zoom : 4,
			mapTypeId : google.maps.MapTypeId.ROADMAP
		};
		var map = new google.maps.Map(document.getElementById("map-canvas"), mapOptions);

		var com = {
			pherris : {
				marker : null,
				polyLine : (function() {
					var polyLine = new google.maps.Polyline({
						strokeColor : "#15317E",
						strokeOpacity : 1.0,
						strokeWeight : 4,
						path : new Array()
					});
					polyLine.setMap(map);
					return polyLine;
				})(),

				/**
				 * draws a line with a marker at the front.
				 **/
				mapPoint : function(point) {

					if (typeof point.latitude == "string") {
						point.latitude = parseFloat(point.latitude);
						point.longitude = parseFloat(point.longitude);
						point.speedMetersPerSecond = parseFloat(point.speedMetersPerSecond);
					}

					//throw out bad GPS
					if (point.latitude.toFixed().replace("-", "") == "0" || point.longitude.toFixed().replace("-", "") == "0") {
						return;
					}

					var latLng = new google.maps.LatLng(point.latitude, point.longitude);

					map.setCenter(latLng);

					com.pherris.polyLine.getPath().push(latLng);

					//clear old marker
					if (com.pherris.marker) {
						com.pherris.marker.setMap(null);
					} else {
						//zoom to marker
						map.setZoom(14);
					}

					//drop new marker at front
					com.pherris.marker = new google.maps.Marker({
						position : latLng,
						map : map,
						title : "Speed:  " + (point.speedMetersPerSecond * 60 * 60 / 1000).toFixed(2) + " km/h"
					});
				},
			},

			m2m : {
				getHistory : function() {
					$.ajax({
						url : "http://api.m2m.io/2/account/domain/{domain}/stuff/bike/thing/ride/past?attributes=_&start={most recent timestamp}&end={oldest timestamp}", //change {domain} with your 2lemetry domain. change the timestamps with 16-digit Unix timestamps for the range to query
						success : function(data, status, jqxhr) {
							for ( var result in data.results) {
								com.pherris.mapPoint(data.results[result]);
							}
						},
						headers : {
							'Authorization' : "{user}:{pass}" //change {user} to your username and {pass} to your password
						}
					});
				}
			}
		};

		com.m2m.getHistory();
	}
	google.load("visualization", "1", {
		packages : [ "corechart", "gauge" ]
	});
	google.maps.event.addDomListener(window, 'load', initialize);
</script>
</head>
<body>
	<div id="map-canvas"></div>
</body>
</html>
```

Configuration
-------------------------
Configuring the API call is similar to the previous step. You'll need your 2lemetry domain, your 2lemetry username and password, and the Unix timestamps of the range of data you want. Replace the {sections} in the getHistory() call of web/history.html with these values.

Overview
================================
Now that everything is up and running - remember WHY you went through the effort to set this up. In a few steps you published, subscribed and mapped data - imagine the power of all of your devices being connected.  Visualizing a useful data set is a GREAT way to showcase the power, flexibility and value of the Internet of Things. I hope this will help YOU sell the value of M2M in your organization! 
