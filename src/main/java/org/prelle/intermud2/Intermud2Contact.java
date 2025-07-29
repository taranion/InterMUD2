package org.prelle.intermud2;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 */
@Getter
@Setter
@ToString
public class Intermud2Contact implements Comparable<Intermud2Contact> {
	
	public static enum ContactState {
		UNKNOWN,
		OFFLINE,
		/** Server found to be online, but hasn't been scanned yet */
		ONLINE,
		SCANNED
	}
	
	public static enum Service {
		PING("ping_q"),
		MUDLIST("mudlist_q"),
		WIZMSG("gwizmsg"),
		FINGER("gfinger_q"),
		TELL("gtell"),
		CHANNEL("gchannel"),
		LOCATE("locate_q"),
		MAIL("mail_q"),
		RWHO("rwho_q")
		;
		String query;
		Service(String q) {
			this.query = q;
		}
	}
	
	
	private InetAddress ipAddress;
	private int im2Port;
	private int port;
	
	private String name;
	private String mudName;
	private String location;
	private String encoding;
	private String driver;
	private String mudLib;
	private String version;
	private String mudGroup;
	private LocalDateTime lastContact = LocalDateTime.MIN;
	private ContactState state = ContactState.UNKNOWN;
	private List<Service> services = new ArrayList<>();


	//-------------------------------------------------------------------
	/**
	 * @param protocolID
	 */
	public Intermud2Contact(InetAddress inet, int port) {
		this.im2Port = port;
		this.ipAddress    = inet;
	}

	//-------------------------------------------------------------------
	/**
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Intermud2Contact other) {
		int timeCmp =lastContact.compareTo(other.lastContact);
		if (timeCmp!=0) return -1*timeCmp;
		return name.compareTo(other.name);
	}
	
	//-------------------------------------------------------------------
	public int getPort() {
		if (port==0) return im2Port-4;
		return port;
	}

}
