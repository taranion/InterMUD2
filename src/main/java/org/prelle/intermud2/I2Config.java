package org.prelle.intermud2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

import lombok.Builder;
import lombok.Getter;

/**
 * 
 */
@Getter
@Builder
public class I2Config {
	
	private int port;
	private Integer i2Port;
	private String name;
	private String mudName;
	private String hostname;
	@Builder.Default
	private String bootmaster = "210.59.236.38 4004";
	private Path exportTo;
	private Path mudList;

	
	public int getI2Port() {
		return (i2Port!=null)?i2Port:(port+4);
	}
	
	public String getMudName() {
		return (mudName!=null)?mudName:name;
	}
	
	public InetAddress getBootmasterIP() throws UnknownHostException {
		return InetAddress.getByName(bootmaster.split(" ")[0]);
	}
	
	public int getBootmasterPort() {
		return Integer.parseInt(bootmaster.split(" ")[1]);
	}
}
