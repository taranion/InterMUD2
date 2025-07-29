package org.prelle.intermud2;

import org.junit.Test;
import org.prelle.intermud2.Intermud2Demon.I2Message;

public class DemonTest {

	@Test
	public void testParseMessage() {
		String msg = "@@@mudlist_a||15:|NAME:Universes|HOST:tesseract|HOSTADDRESS:108.252.255.105|PORT:3333|PORTUDP:3341|MUDLIB:UniLib||16:|NAME:Limbo|HOST:mud.hu|HOSTADDRESS:91.205.173.162|PORT:9000|PORTUDP:9008|MUDLIB:Limbo Mudlib||17:|NAME:MYSTICISM-MUD|HOST:VM-4-16-ubuntu|HOSTADDRESS:124.223.67.13|PORT:2023|PORTUDP:2027|MUDLIB:诡秘世界@@@";
		I2Message mess = Intermud2Demon.parseMessage(msg.getBytes(), null, 0);

	}

}
