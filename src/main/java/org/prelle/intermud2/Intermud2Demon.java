package org.prelle.intermud2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import org.prelle.intermud2.Intermud2Contact.ContactState;
import org.prelle.intermud2.Intermud2Contact.Service;

/**
 * 
 */
public class Intermud2Demon {

	private final static Logger logger = System.getLogger("intermud2");
	
	public static record I2Message(String command, Map<String,String> parameter, InetAddress sender, int port) {

		public int getUdpPort() { return Integer.parseInt(parameter.get("PORTUDP")); }
		public String getName() { return parameter.get("NAME"); }
		public String getMUDName() { return parameter.get("MUDNAME"); }
		public String getMUDGroup() { return parameter.get("MUDGROUP"); }
		public String getDriver() { return parameter.get("DRIVER"); }
		public String getMUDLib() { return parameter.get("MUDLIB"); }
		public String getVersion() { return parameter.get("VERSION"); }
		public String getLocation() { return parameter.get("LOCATION"); }
		public String getEncoding() { return parameter.get("ENCODING"); }
	}

    private final static int    PACKAGE_SIZE     = 4096;
   
	private Thread listenThread;
	private Timer timer;
	
	private I2Config config;

    private static DatagramSocket imud;
    private I2Listener callback;
    
    private Map<String,Intermud2Contact> knownContacts = new HashMap<>();
	 
	//-------------------------------------------------------------------
	public Intermud2Demon(I2Config config, I2Listener callback) throws IOException {
		this.config = config;
		this.callback = callback;
		
		timer = new Timer();
		// Write list of known hosts every minute
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() { 
				saveHostList();
				pingUnknown();
				}}, 60000, 60000);
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() { 
				refreshOnline();
				}}, 120000, 300000);
		
		imud = new DatagramSocket(config.getI2Port());
		start();
	}
 
	//-------------------------------------------------------------------
	public Intermud2Demon(I2Config config) throws IOException {
		this(config, null);
	}

	//-------------------------------------------------------------------
	private void start() throws UnknownHostException {
		if (listenThread!=null && listenThread.isAlive()) {
			listenThread.interrupt();
		}
		
		listenThread = new Thread( () -> run(), "Intermud IM2 UDP");
		listenThread.start();

		// Read list and ping all
		try {
			readHostList();
			announceToAllHosts();
			pingAllHosts();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Read information about Bootmaster
		InetAddress bootmasterAddr = config.getBootmasterIP();
		int bootmasterPort = config.getBootmasterPort();
		
		bootstrap(bootmasterAddr, bootmasterPort);
	}
	
	//-------------------------------------------------------------------
	public void bootstrap(InetAddress addr, int port) {
		// Ping host
		String line = "@@@ping_q"
			+"||NAME:"+config.getName()
			+"||PORTUDP:"+config.getI2Port()
			+"@@@";
		logger.log(Level.INFO, "Send: "+line);
		sendReady(addr, port, line);
	}
	
	//-------------------------------------------------------------------
	private void sendSupportedQuery(Intermud2Contact contact, String command) {
		// Ping host
		String line = "@@@supported_q"
			+"||NAME:"+config.getName()
			+"||PORTUDP:"+config.getI2Port()
			+"||ANSWERID:taranion"
			+"||CMD:"+command
			+"@@@";
		sendReady(contact.getIpAddress(), contact.getIm2Port(), line);
	}
	
	//-------------------------------------------------------------------
	public void ping(Intermud2Contact contact) {
		if (contact.getIm2Port()==0) {
			logger.log(Level.WARNING, "Cannot ping MUD with UDP port 0: "+contact.getName()+" at "+contact.getIpAddress());
			return;
		}
		// Ping host
		String line = "@@@ping_q"
			+"||NAME:"+config.getName()
			+"||PORTUDP:"+config.getI2Port()
			+"@@@";
		sendReady(contact.getIpAddress(), contact.getIm2Port(), line);
	}
	
	//-------------------------------------------------------------------
	private void requestWho(Intermud2Contact contact) {
		// Ping host
		String line = "@@@rwho_q"
			+"||NAME:"+config.getName()
			+"||PORTUDP:"+config.getI2Port()
			+"||ASKWIZ:taranion"
			+"@@@";
		sendReady(contact.getIpAddress(), contact.getIm2Port(), line);
	}
	
	//-------------------------------------------------------------------
	public void bootstrap(List<Intermud2Contact> list) {
		try {
			for (Intermud2Contact server : list) {
				sendStartupRequest(server);
				queryMUDList(server);
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//-------------------------------------------------------------------
	private void run() {
		DatagramPacket dp;
		byte[] buf = new byte[PACKAGE_SIZE];
		logger.log(Level.INFO, "Start listening on "+imud.getLocalPort());
		while (true) {
			Arrays.fill(buf, (byte)0);
		    dp = new DatagramPacket(buf, PACKAGE_SIZE);
		    try {
		    	imud.receive(dp);
		    	String raw = new String(buf, 0, dp.getLength()).trim();
		    	//logger.log(Level.INFO, "Received: "+dp.getLength()+" len = "+raw);
		    	I2Message mess = parseMessage(dp.getData(), dp.getAddress(), dp.getPort());
		    	handleIncoming(mess);
		    } catch (Exception e) {
				logger.log(Level.WARNING, "Error reading from socket",e);
			}
		    
		    try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	//-------------------------------------------------------------------
	private String generateInfoString() {
		String line = "||NAME:"+config.getName()
				+"||VERSION:0.0.1"
				+"||MUBLIB:GraphicMUD"
//				+"||HOSTADDRESS:"+InetAddress.getByName("prelle.selfhost.eu").getHostAddress()
				+"||HOST:"+config.getHostname()
				+"||PORT:"+config.getPort()
				+"||PORTUDP:"+config.getI2Port()
				+"||Time:"+System.currentTimeMillis()
				+"||Users:0";
		return line;
	}

	//-------------------------------------------------------------------
	private void handleIncoming(I2Message mess) {
		switch (mess.command) {
		case "ping_a" -> handlePingAnswer(mess);
		case "ping_q" -> handlePingQuery(mess);
		case "mudlist_a" -> handleMudlistAnswer(mess);
		case "mudlist_q" -> handleMudListQuery(mess);
		case "rwho_a"    -> handleRWhoAnswer(mess);
		default -> {
			logger.log(Level.INFO, "Unhandled message ''{0}''", mess.command);
			System.exit(1);
			}
		}
	}

	//-------------------------------------------------------------------
	private Intermud2Contact updateContact(I2Message mess) {
		String key = mess.sender.getHostAddress()+"/"+mess.getUdpPort();
		Intermud2Contact contact = knownContacts.getOrDefault(key, new Intermud2Contact(mess.sender, mess.getUdpPort()));
		
		contact.setName(mess.getName());
		contact.setMudName(mess.getMUDName());
		contact.setDriver(mess.getDriver());
		contact.setEncoding(mess.getEncoding());
		contact.setLocation(mess.getLocation());
		contact.setMudGroup(mess.getMUDGroup());
		contact.setMudLib(mess.getMUDLib());
		contact.setVersion(mess.getVersion());
		//logger.log(Level.DEBUG, "Set known contact "+key+" to "+contact);
		knownContacts.put(key, contact);
		
		if (callback!=null) {
			callback.newOrUpdatedMUDStats(contact);
		}
		
		return contact;
	}

	//-------------------------------------------------------------------
	private Intermud2Contact getByName(String name) {
		for (Intermud2Contact tmp : knownContacts.values()) {
			if (tmp.getName().equals(name))
				return tmp;
		}
		return null;
	}

	//-------------------------------------------------------------------
	private Intermud2Contact getByIPOnly(InetAddress addr) {
		for (Intermud2Contact tmp : knownContacts.values()) {
			if (tmp.getIpAddress().equals(addr))
				return tmp;
		}
		return null;
	}

	//-------------------------------------------------------------------
	private void updateContact(Map<String,String> map) {
		// Ignore ourselves
		if (config.getName().equalsIgnoreCase( map.get("NAME")) )
			return;
		
		try {
			String hostAddr = map.get("HOSTADDRESS");
			InetAddress inet = InetAddress.getByName(hostAddr);
			int portUDP = Integer.parseInt(map.containsKey("PORTUDP")?map.get("PORTUDP"):map.get("UDPPORT"));
			
			String key = hostAddr+"/"+portUDP;
			Intermud2Contact contact = knownContacts.get(key);
			if (contact==null) {
				// Not found with this IP+Port Try using name
				contact = getByName(map.get("NAME"));
				if (contact!=null) {
					if (contact.getState()==ContactState.ONLINE || contact.getState()==ContactState.SCANNED) {
						// existing contact is online - ignore this one
						return;
					} else {
						// Existing contact may need updating
						logger.log(Level.WARNING, "Update contact "+contact+" with "+hostAddr+" "+portUDP);
						contact.setIpAddress(inet);
						contact.setIm2Port(portUDP);
					}
				} else {
					// Create new contact
					contact = new Intermud2Contact(inet, portUDP);
					logger.log(Level.DEBUG, "Create new contact "+key+" to "+contact);
					knownContacts.put(key, contact);
				}
			}
			
			contact.setName   (map.get("NAME"));
			contact.setMudName(map.get("MUDNAME"));
			//contact.setGamePort
			contact.setDriver (map.get("DRIVER"));
			contact.setMudLib (map.get("MUDLIB"));
			
			if (callback!=null) {
				callback.newOrUpdatedMUDStats(contact);
			}
		} catch (Exception e) {
			logger.log(Level.ERROR, "Error updating contact with parsed:");
			map.entrySet().forEach(entry -> logger.log(Level.ERROR, "  "+entry.getKey()+"\t= "+entry.getValue()));
			logger.log(Level.ERROR, "Error was ",e);
			System.exit(1);
		}
	}

	//-------------------------------------------------------------------
	private void handlePingAnswer(I2Message mess) {
		logger.log(Level.TRACE, "Received answer for ping");
		Intermud2Contact contact = updateContact(mess);
		contact.setLastContact(LocalDateTime.now());
		if (contact.getState()==ContactState.OFFLINE || contact.getState()==ContactState.UNKNOWN) {
			logger.log(Level.INFO, "MUD ''{0}'' is online", mess.getName());
			contact.setState(ContactState.ONLINE);
		}
		if (!contact.getServices().contains(Service.PING)) {
			contact.getServices().add(Service.PING);
		}
		
		// and ask for a MUD list
		String line = "@@@mudlist_q"
			+"||NAME:"+config.getName()
			+"||PORTUDP:"+config.getI2Port()
			+"||ANSWERID:"+(new Random()).nextInt(100)
			+"@@@";
		sendReady(mess.sender, mess.getUdpPort(), line);
	}

	//-------------------------------------------------------------------
	private void handlePingQuery(I2Message mess) {
		logger.log(Level.DEBUG, "We have been pinged by {0} from {1}", mess.parameter.get("NAME"), mess.sender.getHostAddress());
		// Answer 
		String line = "@@@ping_a"
				+generateInfoString()
				+"@@@";
		sendReady(mess.sender(), mess.getUdpPort(), line);
	}

	//-------------------------------------------------------------------
	private void handleMudlistAnswer(I2Message mess) {
		logger.log(Level.TRACE, "Received answer for mudlist");
		if (mess.parameter.containsKey("PORTUDP")) {
			String key = mess.sender.getHostAddress()+"/"+mess.getUdpPort();
			Intermud2Contact contact = knownContacts.getOrDefault(key, new Intermud2Contact(mess.sender, mess.getUdpPort()));
			contact.setLastContact(LocalDateTime.now());
			contact.setState(ContactState.SCANNED);
			if (!contact.getServices().contains(Service.MUDLIST)) {
				contact.getServices().add(Service.MUDLIST);
			}
		} else if (mess.parameter.containsKey("NAME")) {
			Intermud2Contact contact = getByName(mess.parameter.get("NAME"));
			if (contact!=null) {
				contact.setLastContact(LocalDateTime.now());
				contact.setState(ContactState.SCANNED);
				if (!contact.getServices().contains(Service.MUDLIST)) {
					contact.getServices().add(Service.MUDLIST);
				}
			}
		} else if (mess.parameter.containsKey("NAME")) {
			Intermud2Contact contact = getByIPOnly(mess.sender);
			if (contact!=null) {
				contact.setLastContact(LocalDateTime.now());
				contact.setState(ContactState.SCANNED);
				if (!contact.getServices().contains(Service.MUDLIST)) {
					contact.getServices().add(Service.MUDLIST);
				}
			}
		}
		
		for (Entry<String,String> entry: mess.parameter.entrySet()) {
			// If key is a number, value is a mudinfo
			try {
				int index = Integer.parseInt(entry.getKey());
				Map<String,String> subParams = asSubParameter(entry.getValue());
				//logger.log(Level.DEBUG, "Parsed index "+index+": "+subParams);
				updateContact(subParams);
			} catch (NumberFormatException e) {
				logger.log(Level.DEBUG, "Extra parameter in mudlist_a from {0}: {1}={2}", mess.sender, entry.getKey(), entry.getValue());
			}
		}
	}

	//-------------------------------------------------------------------
	private void handleRWhoAnswer(I2Message mess) {
		logger.log(Level.DEBUG, "Received answer for rwho: "+mess);
		if (mess.parameter.containsKey("PORTUDP")) {
			String key = mess.sender.getHostAddress()+"/"+mess.getUdpPort();
			Intermud2Contact contact = knownContacts.getOrDefault(key, new Intermud2Contact(mess.sender, mess.getUdpPort()));
			contact.setLastContact(LocalDateTime.now());
			if (!contact.getServices().contains(Service.RWHO)) {
				contact.getServices().add(Service.RWHO);
			}
		} else if (mess.parameter.containsKey("NAME")) {
			Intermud2Contact contact = getByName(mess.parameter.get("NAME"));
			if (contact!=null) {
				contact.setLastContact(LocalDateTime.now());
				if (!contact.getServices().contains(Service.RWHO)) {
					contact.getServices().add(Service.RWHO);
				}
			}
		}
		
		String pinkFishColorString = mess.parameter.get("RWHO");
		System.out.println(PinkfishColor.decodetoANSI(pinkFishColorString));
		System.out.println("\u001b[0m;");
	}

	//-------------------------------------------------------------------
	private void handleMudListQuery(I2Message mess) {
		logger.log(Level.WARNING, "TODO: We have been queried for a mudlist by {0} from {1}", mess.parameter.get("NAME"), mess.sender.getHostAddress());
		int index=0;
		List<String> lines = new ArrayList<>();
		for (Intermud2Contact mud : new ArrayList<Intermud2Contact>(knownContacts.values())) {
			if (mud.getState()==ContactState.UNKNOWN)
				continue;
			index++;
			String perMud = String.format("%d:|NAME:%s|HOST:%s|HOSTADDRESS:%s|PORT:%d|PORTUDP:%d",
					index,
					mud.getName(),
					mud.getIpAddress().getHostName(),
					mud.getIpAddress().getHostAddress(),
					mud.getPort(),
					mud.getIm2Port()
					);
			lines.add(perMud);
		}
		
		// Send 5 lines per answer
		while (!lines.isEmpty()) {
			List<String> sendNow = new ArrayList<>(lines.subList(0, Math.min(4, lines.size())));
			lines.removeAll(sendNow);
			String answer = "@@@mudlist_a||"+String.join("||", sendNow)+"@@@";
			sendReady(mess.sender, mess.getUdpPort(), answer);;
		}
	}

	//-------------------------------------------------------------------
	private void sendStartupRequest(Intermud2Contact bootstrapServer) throws UnknownHostException {
		String line = "@@@startup"
				+generateInfoString()
				+"@@@";
			sendReady(bootstrapServer, line);
	}

	//-------------------------------------------------------------------
	private void queryMUDList(Intermud2Contact bootstrapServer) throws UnknownHostException {
//		String line1 = "U\tGraphicMUDDevel\t"+System.currentTimeMillis()/1000+"\t0\tmwp 1.2";
//			logger.log(Level.INFO, "Send: "+line1);
//			sendReady(bootstrapServer, line1);
//
//			String line = "@@@ping_q"
//				+"||NAME:"+MUD.getInstance().getName()
//				+"||PORTUDP:4004"
//				+"@@@";
//			logger.log(Level.INFO, "Send: "+line);
//			sendReady(bootstrapServer, line);
	}

    //-------------------------------------------------------------------------
	private void sendReady(InetAddress host, int port, String msg) {
		// Don't send to ourselves
		if (host.equals(imud.getInetAddress()) )
			return;
    	logger.log(Level.TRACE, "SND: {0} to {1} {2}",msg, host,  port);
    	
    	msg+="\0";
    	byte[] data = msg.getBytes(StandardCharsets.US_ASCII);
    	try {
		    DatagramPacket dp = new DatagramPacket(data, data.length, host, port);
		    imud.send(dp);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		    

    //-------------------------------------------------------------------------
	private void sendReady(Intermud2Contact contact, String msg) {
		sendReady(contact.getIpAddress(), contact.getIm2Port(), msg);
	}

	
	//-------------------------------------------------------------------
	private void saveHostList() {
		if (config.getExportTo()==null) return;
		
		logger.log(Level.DEBUG, "Write {0} hosts to {1}", knownContacts.size(), config.getExportTo().toAbsolutePath());
		Instant before = Instant.now();
		try {
			PrintWriter out = new PrintWriter(new FileWriter(config.getExportTo().toFile()));
			List<Intermud2Contact> toWrite = new ArrayList<>(knownContacts.values());
			Collections.sort(toWrite);
			toWrite = toWrite.stream().filter(co -> co.getState()!=ContactState.UNKNOWN).toList();
			
			toWrite.forEach(mud -> out.format("%20s \t|%15s \t|%d \t|%s|%s|%s | %s #%s\r\n",
					mud.getName(),
					mud.getIpAddress().getHostAddress(),
					mud.getIm2Port(),
					mud.getDriver(),
					mud.getMudLib(),
					mud.getVersion(),
					mud.getState(),
					mud.getServices()
					));
			out.flush();
			out.close();
			logger.log(Level.DEBUG, "Wrote {0} hosts to {1}", toWrite.size(), config.getExportTo().toAbsolutePath());
			Duration dur = Duration.between(before, Instant.now());
			logger.log(Level.DEBUG, "Took "+dur.toSeconds());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//-------------------------------------------------------------------
	private void readHostList() throws IOException {
		Reader ins = new InputStreamReader(Intermud2Demon.class.getResourceAsStream("mudlist.txt"));
		if (config.getMudList()!=null && Files.exists(config.getMudList())) {
			ins = new FileReader(config.getMudList().toFile());
		}
		BufferedReader bin = new BufferedReader(ins);
		while (true) {
			String line = bin.readLine();
			if (line==null) break;
			if (line.startsWith("#")) continue;
			try {
				StringTokenizer tok = new StringTokenizer(line,"|");
				String name = tok.nextToken().trim();
				InetAddress inet = InetAddress.getByName(tok.nextToken().trim());
				int udpPort = Integer.parseInt(tok.nextToken().trim());
				String key = inet.getHostAddress()+"/"+udpPort;
				
				if (!knownContacts.containsKey(key)) {
					Intermud2Contact contact = new Intermud2Contact(inet, udpPort);
					contact.setName(name);
					contact.setState(ContactState.UNKNOWN);
					knownContacts.put(key, contact);
					logger.log(Level.INFO, "Add contact "+name);
				}
			} catch (Exception e) {
				logger.log(Level.ERROR, "Error parsing line: "+line+"\n"+e);
			}
		}
	}
	
	//-------------------------------------------------------------------
	private void announceToAllHosts()  {
		List<Intermud2Contact> allHosts = new ArrayList<>(knownContacts.values());
		for (Intermud2Contact cont : allHosts) {
			try {
				sendStartupRequest(cont);
				sendSupportedQuery(cont, "mudlist_q");
				sendSupportedQuery(cont, "ping_q");
				sendSupportedQuery(cont, "rwho_q");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//-------------------------------------------------------------------
	private void refreshOnline()  {
		List<Intermud2Contact> allHosts = new ArrayList<>(knownContacts.values());
		for (Intermud2Contact cont : allHosts) {
			if (cont.getState()==ContactState.ONLINE || cont.getState()==ContactState.SCANNED) {
				try {
					Duration last = Duration.between(cont.getLastContact(), LocalDateTime.now());
					if (last.toMinutes()>40) {
						// MUD seems offline
						cont.setState(ContactState.OFFLINE);
					}
				} catch (DateTimeException e) {
					logger.log(Level.ERROR, "Error comparing time between {0} and {1}: "+e, cont.getLastContact(), LocalTime.now());
				}
				
				try {
					ping(cont);
					requestWho(cont);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	//-------------------------------------------------------------------
	private void pingUnknown()  {
		List<Intermud2Contact> allHosts = new ArrayList<>(knownContacts.values());
		for (Intermud2Contact cont : allHosts) {
			if (cont.getState()==ContactState.ONLINE || cont.getState()==ContactState.SCANNED)
				continue;
			try {
				ping(cont);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//-------------------------------------------------------------------
	private void pingAllHosts()  {
		List<Intermud2Contact> allHosts = new ArrayList<>(knownContacts.values());
		for (Intermud2Contact cont : allHosts) {
			try {
				ping(cont);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
    //-------------------------------------------------------------------------
	public static I2Message parseMessage(byte[] data, InetAddress sender, int port) {
		String ascii = new String(data, StandardCharsets.UTF_8);
		I2Message msg = asI2Message(ascii.trim(), sender,port);
		if (msg.parameter.containsKey("ENCODING")) {
			Charset cs = Charset.forName(msg.parameter.get("ENCODING"));
			ascii = new String(data, cs);
			msg = asI2Message(ascii.trim(), sender,port);
		}
		return msg;
	}
	
	//-------------------------------------------------------------------
	private static I2Message asI2Message(String data, InetAddress sender, int port) {
		//logger.log(Level.DEBUG, "parse: "+data);
		if (!data.startsWith("@@@") && !data.endsWith("@@@"))
			return null;
		data = data.substring(3);
		data = data.substring(0, data.length()-3);
		//logger.log(Level.DEBUG, "really parse: <<<"+data+">>>");
		String[] params = data.split("\\|\\|");
		String command = params[0];
		Map<String,String> ret = new HashMap<>();
		for (int i=1; i<params.length; i++) {
			int index = params[i].indexOf(":");
			if (index>0) {
				ret.put(params[i].substring(0, index), params[i].substring(index+1));
			} else {
				ret.put(params[i], null);
			}
		}
		return new I2Message(command, ret, sender,port);
	}
	
	//-------------------------------------------------------------------
	private static Map<String,String> asSubParameter(String data) {
		String[] params = data.split("\\|");
		Map<String,String> ret = new HashMap<>();
		for (int i=0; i<params.length; i++) {
			int index = params[i].indexOf(":");
			if (index>0) {
				ret.put(params[i].substring(0, index), params[i].substring(index+1));
			} else {
				ret.put(params[i], null);
			}
		}
		return ret;
	}
}
