package at.jojokobi.llamarama.gamemode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import at.jojokobi.donatengine.gui.DynamicGUIFactory;
import at.jojokobi.donatengine.gui.GUI;
import at.jojokobi.donatengine.gui.GUISystem;
import at.jojokobi.donatengine.gui.PercentualDimension;
import at.jojokobi.donatengine.gui.SimpleGUI;
import at.jojokobi.donatengine.gui.SimpleGUISystem;
import at.jojokobi.donatengine.gui.SimpleGUIType;
import at.jojokobi.donatengine.gui.actions.GUIAction;
import at.jojokobi.donatengine.gui.nodes.Button;
import at.jojokobi.donatengine.gui.nodes.HFlowBox;
import at.jojokobi.donatengine.gui.nodes.ListView;
import at.jojokobi.donatengine.gui.nodes.TextField;
import at.jojokobi.donatengine.gui.nodes.VBox;
import at.jojokobi.donatengine.gui.style.FixedDimension;
import at.jojokobi.donatengine.gui.style.FixedStyle;
import at.jojokobi.donatengine.level.ChatComponent;
import at.jojokobi.donatengine.level.Level;
import at.jojokobi.donatengine.level.LevelArea;
import at.jojokobi.donatengine.level.LevelBoundsComponent;
import at.jojokobi.donatengine.level.LevelComponent;
import at.jojokobi.donatengine.level.LevelHandler;
import at.jojokobi.donatengine.net.MultiplayerBehavior;
import at.jojokobi.donatengine.objects.Camera;
import at.jojokobi.donatengine.objects.GameObject;
import at.jojokobi.donatengine.objects.properties.ObjectProperty;
import at.jojokobi.donatengine.objects.properties.ObservableProperty;
import at.jojokobi.donatengine.presence.GamePresence;
import at.jojokobi.donatengine.rendering.TwoDimensionalPerspective;
import at.jojokobi.donatengine.serialization.SerializationWrapper;
import at.jojokobi.donatengine.util.Vector3D;
import at.jojokobi.llamarama.characters.CharacterType;
import at.jojokobi.llamarama.characters.CharacterTypeProvider;
import at.jojokobi.llamarama.entities.CharacterComponent;
import at.jojokobi.llamarama.entities.NonPlayerCharacter;
import at.jojokobi.llamarama.entities.PlayerCharacter;
import at.jojokobi.llamarama.maps.GameMap;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class GameLevel extends Level{
	
	public static class StartMatchAction implements GUIAction {

		@Override
		public void serialize(DataOutput buffer, SerializationWrapper serialization) throws IOException {
			
		}

		@Override
		public void deserialize(DataInput buffer, SerializationWrapper serialization) throws IOException {
			
		}

		@Override
		public void perform(Level level, LevelHandler handler, long id, GUISystem system, Camera camera) {
			if (!level.getComponent(GameComponent.class).isRunning() && level.getClientId() == system.getGUI(id).getClient()) {
				level.getComponent(GameComponent.class).startMatch(level, handler);
			}
			system.removeGUI(id);
		}

		@Override
		public boolean executeOnClient() {
			return false;
		}
		
	}
	
	public static class SelectCharacterAction implements GUIAction{
		
		private String characterType;
		private String name;
		
		public SelectCharacterAction(String characterType, String name) {
			super();
			this.characterType = characterType;
			this.name = name;
		}
		
		public SelectCharacterAction() {
			this("", "");
		}

		@Override
		public void serialize(DataOutput buffer, SerializationWrapper serialization) throws IOException {
			buffer.writeUTF(characterType);
			buffer.writeUTF(name);
		}

		@Override
		public void deserialize(DataInput buffer, SerializationWrapper serialization) throws IOException {
			characterType = buffer.readUTF();
			name = buffer.readUTF();
		}

		@Override
		public void perform(Level level, LevelHandler handler, long id, GUISystem system, Camera camera) {
			System.out.println(id + ":" + system.getGUIs());
			GUI gui = system.getGUI(id);
			long client = gui.getClient();
			level.getComponent(GameComponent.class).characterChoices.put(client, new PlayerInformation(CharacterTypeProvider.getCharacterTypes().get(characterType), name.isEmpty() ? characterType : name));
			system.removeGUI(id);
			system.showGUI(LIST_CHARACTERS_GUI, null, client);
		}

		@Override
		public boolean executeOnClient() {
			return false;
		}
		
	}
	
	public static class PlayerInformation {
		
		private CharacterType character;
		private String name;
		
		public PlayerInformation(CharacterType character, String name) {
			super();
			this.character = character;
			this.name = name;
		}
		
		public PlayerInformation() {
			
		}

		public CharacterType getCharacter() {
			return character;
		}

		public void setCharacter(CharacterType character) {
			this.character = character;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name + " (" + character.getName() + ")";
		}
		
	}
	
	public static class GameComponent implements LevelComponent {
		
		private Map<Long, PlayerInformation> characterChoices = new HashMap<>();
		private List<Long> connectedClients = new ArrayList<>();
		
		private List<GameEffect> gameEffects;
		
		private ObjectProperty<GameMode> gameMode = new ObjectProperty<>(null);
		private Vector3D startPos;
		private String startArea;
		private GameMap currentMap;
		private double time;
		private boolean running = false;
		private String connectionString;
		private UUID partyId = UUID.randomUUID();

		public GameComponent(GameMode gameMode, String connectionString, Vector3D startPos, String startArea) {
			super();
			this.gameMode.set(gameMode);
			this.connectionString = connectionString;
			this.startPos = startPos;
			this.startArea = startArea;
		}

		@Override
		public void hostUpdate(Level level, LevelHandler handler, Camera cam, double delta) {
			time += delta;
			if (level.getBehavior().isHost()) {
				if (running) {
					gameMode.get().update(level, this, delta);
					if (gameMode.get().canEndGame(level, this)) {
						endMatch(level, handler);
					}
				}
				else if (gameMode.get().canStartGame(level, characterChoices, this)) {
					startMatch(level, handler);
				}
				gameEffects.forEach(e -> e.update(level, this, delta));
			}
		}

		@Override
		public void renderBefore(GraphicsContext ctx, Camera cam, Level level) {
			
		}

		@Override
		public void renderAfter(GraphicsContext ctx, Camera cam, Level level) {
			
		}
		
		@Override
		public void onConnectPlayer(Camera cam, Level level, long id) {
			LevelComponent.super.onConnectPlayer(cam, level, id);
			connectedClients.add(id);
			if (!isRunning()) {
				level.getGuiSystem().showGUI(SELECT_CHARACTER_GUI, null, id);
			}
		}
		
		private void startMatch (Level level, LevelHandler handler) {
			GamePresence presence = new GamePresence();
			presence.setState("In Match");
			presence.setDetails(gameMode.get().getName());
			presence.setPartySize(connectedClients.size());
			presence.setPartyMax(gameMode.get().getMaxPlayers());
			presence.setStartTimestamp(System.currentTimeMillis());
			presence.setLargeImageKey("corporal");
			presence.setLargeImageText("corporal");
			presence.setPartyId(partyId.toString());
			handler.getGamePresenceHandler().updatePresence(presence, null, null);
			
			time = 0;
			running = true;
			level.clear();
			
			gameEffects = gameMode.get().createEffects();
			
			currentMap = gameMode.get().getPossibleMaps().get(new Random().nextInt(gameMode.get().getPossibleMaps().size()));
			LevelBoundsComponent bounds = level.getComponent(LevelBoundsComponent.class);
			bounds.setPos(startPos);
			Vector3D size = currentMap.getSize();
			size.setY(32 * 64);
			bounds.setSize(size);
			currentMap.generate(level, startPos, startArea);
			for (var e : characterChoices.entrySet()) {
				PlayerCharacter player = new PlayerCharacter(startPos.getX() + Math.random() * currentMap.getSize().getX(), startPos.getY() + 32, startPos.getZ() + Math.random() * currentMap.getSize().getZ(), startArea, e.getKey(), e.getValue().getCharacter(), e.getValue().getName());
				level.spawn(player);
			}
			List<String> types = new ArrayList<>(CharacterTypeProvider.getCharacterTypes().keySet());
			for (int i = characterChoices.size(); i < gameMode.get().getMaxPlayers(); i++) {
				NonPlayerCharacter player = new NonPlayerCharacter(startPos.getX() + Math.random() * currentMap.getSize().getX(), startPos.getY() + 32, startPos.getZ() + Math.random() * currentMap.getSize().getZ(), startArea, CharacterTypeProvider.getCharacterTypes().get(types.get(new Random().nextInt(types.size()))));
				level.spawn(player);
			}
			
//			level.spawn(new NonPlayerCharacter(512, 32, 512, startArea, CharacterTypeProvider.getCharacterTypes().get("Corporal")));
			gameMode.get().startGame(level, this);
			characterChoices.clear();
		}
		
		private void endMatch (Level level, LevelHandler handler) {			
			level.getComponent(ChatComponent.class).postMessage(gameMode.get().determineWinner(level, this).getName() + " won the game!");
			gameMode.get().endGame(level, this);
			init(level, handler);
		}

		@Override
		public List<ObservableProperty<?>> observableProperties() {
			return Arrays.asList(gameMode);
		}

		public double getTime() {
			return time;
		}

		public boolean isRunning() {
			return running;
		}

		@Override
		public void init(Level level, LevelHandler handler) {
			initGame(level, handler);
		}
		
		private void initGame (Level level, LevelHandler handler) {
			GamePresence presence = new GamePresence();
			presence.setState("In Lobby");
			presence.setDetails(gameMode.get().getName());
			presence.setPartySize(connectedClients.size());
			presence.setPartyMax(gameMode.get().getMaxPlayers());
			presence.setStartTimestamp(System.currentTimeMillis());
			presence.setLargeImageKey("corporal");
			presence.setLargeImageText("corporal");
			presence.setPartyId(partyId.toString());
			presence.setJoinSecret(connectionString);
			handler.getGamePresenceHandler().updatePresence(presence, null, r -> {
				System.out.println("Someone else joins");
				level.getComponent(ChatComponent.class).postMessage(r.getUsername() + " joined via " + r.getPlatform() + "!");
				return true;
			});
			
			characterChoices.clear();
//			for (Long id : connectedClients) {
//				characterChoices.put(id, new PlayerInformation(CharacterTypeProvider.getCharacterTypes().get("Corporal"), "Corporal"));
//			}
			time = 0;
			running = false;
			gameEffects = new ArrayList<>();
			
			for (GameObject obj : level.getObjectsWithComponent(CharacterComponent.class)) {
				obj.delete(level);
			}
			
			for (long client : connectedClients) {
				level.getGuiSystem().showGUI(SELECT_CHARACTER_GUI, null, client);
			}
		}

		public Vector3D getStartPos() {
			return startPos;
		}

		public String getStartArea() {
			return startArea;
		}

		public GameMap getCurrentMap() {
			return currentMap;
		}

		@Override
		public void update(Level level, LevelHandler handler, Camera cam, double delta) {
			
		}

		@Override
		public void clientUpdate(Level level, LevelHandler handler, Camera cam, double delta) {
			
		}
		
	}
	
	public static final String SELECT_CHARACTER_GUI = "select_character";
	public static final String LIST_CHARACTERS_GUI = "list_characters";
	
	private String mainArea = "main";
	
	
	public GameLevel(MultiplayerBehavior behavior, GameMode mode, String connectionString) {
		super(behavior, 0, 0, 0);
		
		addComponent(new ChatComponent());
		addComponent(new LevelBoundsComponent(new Vector3D(), new Vector3D(128 * 32, 64 * 32, 128 * 32), true));
		GameComponent comp = new GameComponent(mode, connectionString, new Vector3D(0, 0, 0), mainArea);
		addComponent(comp);
		
		DynamicGUIFactory fact = new DynamicGUIFactory();
		//Select Player GUI
		fact.registerGUI(SELECT_CHARACTER_GUI, new SimpleGUIType<>(Object.class, (data, client) -> {
			PlayerInformation info = new PlayerInformation(CharacterTypeProvider.getCharacterTypes().get("Corporal"), "Nickname");
			
			HFlowBox box = new HFlowBox();
			box.setWidthDimension(new PercentualDimension(1));
			box.setHeightDimension(new PercentualDimension(1));
			//Nickname Field
			TextField nickname = new TextField();
			nickname.setText("Nickname");
			nickname.setWidthDimension(new PercentualDimension(1));
			nickname.addStyle(s -> true, new FixedStyle().setBorderRadius(5.0).setFont(new Font("Consolas", 24)));
			//Character Buttons
			for (Map.Entry<String, CharacterType> e : CharacterTypeProvider.getCharacterTypes().entrySet()) {
				Button button = new Button(e.getKey());
				button.setWidthDimension(new FixedDimension(200));
				button.setHeightDimension(new FixedDimension(200));
				button.addStyle(s -> true, new FixedStyle().setMargin(10).setBorderRadius(5.0).setFont(new Font("Consolas", 24)));
				button.addStyle(s -> s.isSelected(), new FixedStyle().setFill(Color.AQUA));
				
				button.setOnAction(() -> {
					info.setCharacter(e.getValue());
					return null;
				});
				box.addChild(button);
			}
			//Nickname box
			VBox nicknameBox = new VBox();
			nicknameBox.addStyle(s -> true, new FixedStyle().setMargin(10).setBorderRadius(5.0).setBorder(Color.GRAY).setBorderStrength(2.0).setPadding(5).setFont(new Font("Consolas", 24)));
			nicknameBox.addChild(nickname);
			nicknameBox.setWidthDimension(new FixedDimension(200));
			nicknameBox.setHeightDimension(new FixedDimension(200));
			box.addChild(nicknameBox);
			//Start Game Button
			Button button = new Button("Select");
			button.setWidthDimension(new FixedDimension(200));
			button.setHeightDimension(new FixedDimension(200));
			button.addStyle(s -> true, new FixedStyle().setMargin(10).setBorderRadius(5.0).setFont(new Font("Consolas", 24)));
			
			button.setOnAction(() -> new SelectCharacterAction(info.getCharacter().getName(), nickname.getText()));
			box.addChild(button);
			
			return new SimpleGUI(box, SELECT_CHARACTER_GUI, data, client);
		}));
		//List characters gui
		fact.registerGUI(LIST_CHARACTERS_GUI, new SimpleGUIType<>(Object.class, (data, client) -> {
			VBox box = new VBox();
			box.setWidthDimension(new PercentualDimension(1));
			
			ListView<PlayerInformation> playerView = new ListView<PlayerInformation>(() -> Arrays.asList(comp.characterChoices.entrySet().stream().map(e -> e.getValue()).toArray(PlayerInformation[]::new)));
			playerView.addStyle(s -> true, new FixedStyle().setMargin(10).setBorder(Color.BLACK).setBorderStrength(2.0).setFont(new Font("Consolas", 24)));
			playerView.setHeightDimension(new FixedDimension(500));
			playerView.setWidthDimension(new PercentualDimension(0.5));
			box.addChild(playerView);
			
			Button startButton = new Button("Start Game");
			startButton.addStyle(s -> true, new FixedStyle().setMargin(10).setBorderRadius(5.0).setFont(new Font("Consolas", 24)));
			startButton.addStyle(s -> !behavior.isHost(), new FixedStyle().setBorder(Color.GRAY).setFontColor(Color.GRAY));
			startButton.setWidthDimension(new PercentualDimension(0.5));
			startButton.setOnAction(() -> new StartMatchAction());
			
			if (behavior.isHost()) {
				box.addChild(startButton);
			}
			
			return new SimpleGUI(box, SELECT_CHARACTER_GUI, data, client);
		}));
		initGuiSystem(new SimpleGUISystem(fact));
	}

	@Override
	public void generate(Camera camera) {
		addArea(mainArea, new LevelArea());
	}
	
	@Override
	public void start(Camera camera, LevelHandler handler) {
		super.start(camera, handler);
		camera.setPerspective(new TwoDimensionalPerspective());
		camera.setRotationX(90);
		camera.setRenderDistance(32 * 40);
	}
	
	@Override
	public synchronized void update(double delta, LevelHandler handler, Camera camera) {
		super.update(delta, handler, camera);
	}
	
}
