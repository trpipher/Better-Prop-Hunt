package com.idiots.prophunt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BetterPropHuntPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BetterPropHuntPlugin.class);
		RuneLite.main(args);
	}
}