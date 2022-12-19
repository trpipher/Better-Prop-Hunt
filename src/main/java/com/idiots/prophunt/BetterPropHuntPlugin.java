package com.idiots.prophunt;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Better Prop Hunt"
)
public class BetterPropHuntPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BetterPropHuntConfig config;

	@Inject
	private Hooks hooks;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BetterPropHuntDataManager betterPropHuntDataManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BetterPropHuntOverlay betterPropHuntOverlay;

	private RuneLiteObject localDisguise;

	private HashMap<String, RuneLiteObject> playerDisguises = new HashMap<>();

	private String[] players;
	private HashMap<String, BetterPropHuntPlayerData> playersData;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private final long SECONDS_BETWEEN_GET = 5;
	private static final int DOT_PLAYER = 2;
	private static final int DOT_FRIEND = 3;
	private static final int DOT_TEAM = 4;
	private static final int DOT_FRIENDSCHAT = 5;
	private static final int DOT_CLAN = 6;

	private SpritePixels[] originalDotSprites;

	@Getter
	private int rightClickCounter = 0;

	@Override
	protected void startUp() throws Exception
	{
		playersData = new HashMap<>();
		hooks.registerRenderableDrawListener(drawListener);
		clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		setPlayersFromString(config.players());
		getPlayerConfigs();
		storeOriginalDots();
		hideMinimapDots();

		overlayManager.add(betterPropHuntOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invokeLater(this::removeAllTransmogs);
		overlayManager.remove(betterPropHuntOverlay);
		hooks.unregisterRenderableDrawListener(drawListener);
		restoreOriginalDots();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (GameState.LOGGED_IN.equals(event.getGameState()))
		{
			if(config.hideMode()) clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));

			if(client.getLocalPlayer().getName() != null)
				betterPropHuntDataManager.updatePropHuntApi(new BetterPropHuntPlayerData(client.getLocalPlayer().getName(),
					config.hideMode(), getModelID()));
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN && originalDotSprites == null)
		{
			storeOriginalDots();
			if(config.hideMinimapDots()) hideMinimapDots();
		}
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		clientThread.invokeLater(this::removeAllTransmogs);
		if(config.hideMode()) {
			clientThread.invokeLater(() -> transmogPlayer(client.getLocalPlayer()));
		}

		if(event.getKey().equals("players")) {
			setPlayersFromString(config.players());
			getPlayerConfigs();
		}

		if(event.getKey().equals("hideMinimapDots")) {
			if (config.hideMinimapDots()) {
				hideMinimapDots();
			} else {
				restoreOriginalDots();
			}
		}

		if(client.getLocalPlayer() != null) {
			betterPropHuntDataManager.updatePropHuntApi(new BetterPropHuntPlayerData(client.getLocalPlayer().getName(),
					config.hideMode(), getModelID()));
			clientThread.invokeLater(() -> transmogOtherPlayers());
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event) {
		if(config.smoothMotion()) return;

		if(config.hideMode() && localDisguise != null) {
			WorldPoint playerPoint = client.getLocalPlayer().getWorldLocation();
			localDisguise.setLocation(LocalPoint.fromWorld(client, playerPoint), playerPoint.getPlane());
		}

		client.getPlayers().forEach(player -> updateDisguiseLocation(player));
	}

	@Subscribe
	public void onClientTick(final ClientTick event) {
		if(!config.smoothMotion()) return;

		if(config.hideMode() && localDisguise != null) {
			LocalPoint playerPoint = client.getLocalPlayer().getLocalLocation();
			localDisguise.setLocation(playerPoint, client.getPlane());

		}

		client.getPlayers().forEach(player -> updateDisguiseLocation(player));
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		if(config.limitRightClicks() > 0 && !config.hideMode()) {
			if(rightClickCounter >= config.limitRightClicks()) {
				sendHighlightedChatMessage("You have used all of your right clicks!");
				return;
			}
			rightClickCounter++;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if(playerDisguises == null || playerDisguises.size() == 0) return;

		if(!event.getOption().startsWith("Walk here")) {
			if(config.depriorizteMenuOptions()) event.getMenuEntry().setDeprioritized(true);
			return;
		}

		if(config.findRange() <= 0) return;

		WorldPoint point = client.getSelectedSceneTile().getWorldLocation();

		Set<String> players = playerDisguises
				.entrySet()
				.stream()
				.filter(entry -> {
					RuneLiteObject obj = entry.getValue();
					WorldPoint tileLoc = WorldPoint.fromLocal(client, obj.getLocation());
					boolean validTile = tileLoc.distanceTo(point) <= 0;
					boolean validDistance = tileLoc.distanceTo(client.getLocalPlayer().getWorldLocation()) <= config.findRange();

					return validTile && validDistance;
				})
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());

		players.forEach(player -> {
			client.createMenuEntry(-1)
					.setTarget("<col=ff981f>"+player+"</col>")
					.setOption("Find")
					.setDeprioritized(true)
					.setType(MenuAction.WALK)
					.onClick(e -> findPlayer(player));
		});
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getEntry() == BetterPropHuntOverlay.RESET_ENTRY) {
			rightClickCounter = 0;
		}
	}

	private void findPlayer(String player) {
		sendNormalChatMessage("You found "+player+"!");
	}

	private void sendNormalChatMessage(String message) {
		ChatMessageBuilder msg = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append(message);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.ITEM_EXAMINE)
				.runeLiteFormattedMessage(msg.build())
				.build());
	}

	private void sendHighlightedChatMessage(String message) {
		ChatMessageBuilder msg = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(msg.build())
				.build());
	}

	// Hide players who are participating in prop hunt
	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof Player)
		{
			Player player = (Player) renderable;
			Player local = client.getLocalPlayer();

			if (player == local)
			{
				return !config.hideMode();
			}

			if(players == null) return true;

			ArrayList<String> playerList = new ArrayList<>(Arrays.asList(players));

			if(playerList.contains(player.getName())) {
				BetterPropHuntPlayerData data = playersData.get(player.getName());

				if(data == null) return true;

				if(data.hiding) {
					return !data.hiding;
				}
			}
		}

		return true;
	}

	private void transmogPlayer(Player player) {
		transmogPlayer(player, getModelID(), true);
	}

	private void transmogPlayer(Player player, int modelID, boolean local) {
		if(client.getLocalPlayer() == null) return;

		RuneLiteObject disguise = client.createRuneLiteObject();

		LocalPoint loc = LocalPoint.fromWorld(client, player.getWorldLocation());
		if (loc == null)
		{
			return;
		}

		Model model = client.loadModel(modelID);

		if (model == null)
		{
			final Instant loadTimeOutInstant = Instant.now().plus(Duration.ofSeconds(5));

			clientThread.invoke(() ->
			{
				if (Instant.now().isAfter(loadTimeOutInstant))
				{
					return true;
				}

				Model reloadedModel = client.loadModel(modelID);

				if (reloadedModel == null)
				{
					return false;
				}

				localDisguise.setModel(reloadedModel);

				return true;
			});
		}
		else {
			disguise.setModel(model);
		}

		disguise.setLocation(player.getLocalLocation(), player.getWorldLocation().getPlane());
		disguise.setActive(true);

		if(local) {
			localDisguise = disguise;
		}
		else {
			playerDisguises.put(player.getName(), disguise);
		}
	}

	private void transmogOtherPlayers() {
		if(players == null || client.getLocalPlayer() == null) return;

		client.getPlayers().forEach(player -> {
			if(client.getLocalPlayer() == player) return;

			BetterPropHuntPlayerData data = playersData.get(player.getName());

			if(data == null || !data.hiding) return;

			transmogPlayer(player, data.modelID, false);
		});
	}

	private void removeLocalTransmog() {
		if (localDisguise != null)
		{
			localDisguise.setActive(false);
		}
		localDisguise = null;
	}

	private void removeTransmogs()
	{
		playerDisguises.forEach((p, disguise) -> {
			if(disguise == null) return;

			disguise.setActive(false);
			disguise = null;
		});
	}

	private void removeAllTransmogs() {
		removeTransmogs();
		removeLocalTransmog();
	}

	private void updateDisguiseLocation(Player p) {
		RuneLiteObject obj = playerDisguises.get(p.getName());
		if(obj == null) return;

		obj.setLocation(p.getLocalLocation(), p.getWorldLocation().getPlane());
	}

	private void setPlayersFromString(String playersString) {
		players = playersString.split(",");
	}

	@Schedule(
			period = SECONDS_BETWEEN_GET,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void getPlayerConfigs() {
		if(players.length < 1 || config.players().isEmpty()) return;

		betterPropHuntDataManager.getPropHuntersByUsernames(players);
	}

	// Called from PropHuntDataManager
	public void updatePlayerData(HashMap<String, BetterPropHuntPlayerData> data) {
		clientThread.invokeLater(() -> {
			removeTransmogs();
			playersData.clear();
			playerDisguises.clear();
			playersData.putAll(data);
			playersData.values().forEach(player -> playerDisguises.put(player.username, null));
			transmogOtherPlayers();
		});
	}

	private void hideMinimapDots() {
		SpritePixels[] mapDots = client.getMapDots();

		if(mapDots == null) return;

		mapDots[DOT_PLAYER] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_CLAN] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_FRIEND] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_FRIENDSCHAT] = client.createSpritePixels(new int[0], 0, 0);
		mapDots[DOT_TEAM] = client.createSpritePixels(new int[0], 0, 0);
	}

	private void storeOriginalDots()
	{
		SpritePixels[] originalDots = client.getMapDots();

		if (originalDots == null)
		{
			return;
		}

		originalDotSprites = Arrays.copyOf(originalDots, originalDots.length);
	}

	private void restoreOriginalDots()
	{
		SpritePixels[] mapDots = client.getMapDots();

		if (originalDotSprites == null || mapDots == null)
		{
			return;
		}

		System.arraycopy(originalDotSprites, 0, mapDots, 0, mapDots.length);
	}

	private int getModelID() {
		return config.useCustomModelID() ? config.customModelID() : config.modelID().toInt();
	}

	@Provides
	BetterPropHuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterPropHuntConfig.class);
	}
}
