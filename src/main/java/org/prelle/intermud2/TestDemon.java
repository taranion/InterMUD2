package org.prelle.intermud2;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestDemon {

	public TestDemon() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws IOException {
		Path exportTo = Paths.get("mudlist_export.txt");
		I2Config config = I2Config.builder()
				.port(4000)
				.name("DevelopmentTest")
				.hostname("eden.rpgframework.de")
				.exportTo(exportTo)
				.build();
		Intermud2Demon demon = new Intermud2Demon(config);
	}

}
