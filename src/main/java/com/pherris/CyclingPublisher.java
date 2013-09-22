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
		connect();
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