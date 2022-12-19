package com.idiots.prophunt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("prophunt")
public interface BetterPropHuntConfig extends Config
{
	@ConfigItem(
			keyName = "players",
			name = "Player Names",
			description = "Names of the players you are playing with (comma separated)",
			position = 1
	)
	default String players() { return ""; }

	@ConfigItem(
			keyName = "model",
			name = "Model",
			description = "ID for the model you want to appear as",
			position = 2
	)
	default BetterPropHuntModelId modelID()
	{
		return BetterPropHuntModelId.BUSH;
	}


	@ConfigItem(
			keyName = "hideMode",
			name = "Hide Mode",
			description = "Toggle whether you are currently hiding or not",
			position = 3
	)
	default boolean hideMode()
	{
		return false;
	}

	@ConfigItem(
			keyName = "findRange",
			name = "Find Range",
			description = "Distance player has to be from hider for the Find menu option to appear (0 to disable)",
			position = 4
	)
	default int findRange() { return 10; }

	@ConfigItem(
			keyName = "limitRightClicks",
			name = "Maximum Right Clicks",
			description = "Limit the number of right clicks a non-hider can do (0 = unlimited)",
			position = 5
	)
	default int limitRightClicks() { return 10; }

	@ConfigItem(
			keyName = "depriorizteMenuOptions",
			name = "Deprioritize Menu Options",
			description = "Forces 'Walk Here' to the top of every menu to better hide props",
			position = 6
	)
	default boolean depriorizteMenuOptions() { return true; }

	@ConfigItem(
			keyName = "hideMinimapDots",
			name = "Hide Minimap Dots",
			description = "Toggle whether minimap dots are hidden",
			position = 5
	)
	default boolean hideMinimapDots()
	{
		return false;
	}

	@ConfigItem(
			keyName = "smoothMotion",
			name = "Smooth Movement",
			description = "Models will move smoothly (slightly more resource intensive)",
			position = 6
	)
	default boolean smoothMotion() { return true; }

	@ConfigItem(
			keyName = "useCustomModelID",
			name = "Use Model ID Field",
			description = "Use the custom Model ID Field instead of the drop down",
			position = 7
	)
	default boolean useCustomModelID() { return false; }

	@ConfigItem(
			keyName = "customModelID",
			name = "Custom Model ID",
			description = "The ID of the model you'd like to become",
			position = 8
	)
	default int customModelID() { return 1565; }
}
