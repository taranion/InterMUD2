package org.prelle.intermud2;

/**
 * 
 */
public class PinkfishColor {

	//-------------------------------------------------------------------
	public static String decodetoANSI(String value) {
		value = value.replace("%^RED"    , "\u001b[31m");
		value = value.replace("%^GREEN"  , "\u001b[32m");
		value = value.replace("%^ORANGE" , "\u001b[33m");
		value = value.replace("%^BLUE"   , "\u001b[34m");
		value = value.replace("%^MAGENTA", "\u001b[35m");
		value = value.replace("%^CYAN"   , "\u001b[36m");
		value = value.replace("%^WHITE"  , "\u001b[37m");
		value = value.replace("%^B_RED"    , "\u001b[91m");
		value = value.replace("%^B_GREEN"  , "\u001b[92m");
		value = value.replace("%^B_ORANGE" , "\u001b[93m");
		value = value.replace("%^B_BLUE"   , "\u001b[94m");
		value = value.replace("%^B_MAGENTA", "\u001b[95m");
		value = value.replace("%^B_CYAN"   , "\u001b[96m");
		value = value.replace("%^B_WHITE"  , "\u001b[97m");
		value = value.replace("%^RESET"  , "\u001b[0m");
		value = value.replace("%^BOLD"   , "\u001b[1m");
		value = value.replace("%^FLASH"   , "\u001b[5m");
		value = value.replace("%^UNDERLINE", "\u001b[4m");
		value = value.replace("%^REVERSE ", "\u001b[7m");
		value = value.replace("%^"   , "");
 		
		return value;
	}

}
