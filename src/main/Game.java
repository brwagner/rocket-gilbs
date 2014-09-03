package main;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glColor3d;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertex2i;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUseProgram;

import java.io.IOException;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.openal.AL;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.newdawn.slick.openal.AudioLoader;
import org.newdawn.slick.openal.SoundStore;
import org.newdawn.slick.util.ResourceLoader;

import util.Saver;
import util.ShaderLoader;
import util.SimpleText;

/**
 * Represents the state of the game once it is turned on.
 * Manages the Menu and InGame states.
 */
public class Game {
	static Vector2f screen; // screen size specified by player
	static Vector2f gameScreen; // size of the coordinate plane
	private static Vector3f scaleVector; // used for reconciling differences in aspect ratio

	InGame inGame; // The InGame state
	Menu menu; // The Menu state
	ShaderLoader sl; // Loads the bloom and star shader files
	boolean start; // Has the game started?
	Saver saver; // Saves the game

	// Constructor
	public Game() {
		this.saver = new Saver("src/save.txt");
		this.start = false;
		this.menu = new Menu(saver);
	}

	// Sets up the game
	void setup() {
		initScaleVector();
		try {
			createDisplay();
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
		initOpenGL();
	}

	// Initializes screen constants used in aspect ratio correcting
	private void initScaleVector() {
		gameScreen = new Vector2f(1280, 720);
		screen = saver.getScreen();
		scaleVector = new Vector3f((float) (Game.screen.x / Game.gameScreen.x), (float) (Game.screen.y / Game.gameScreen.y), 0);
	}

	// Creates the window for LWJGL
	private void createDisplay() throws LWJGLException {
		Display.setDisplayMode(new DisplayMode((int) screen.x, (int) screen.y));
		Display.setVSyncEnabled(true);
		Display.setTitle("Rocket Gilbs");
		Display.create();
	}

	// Creates an OPENGL view in the display
	private void initOpenGL() {
		glLoadIdentity();
		glViewport(0, 0, (int) screen.x, (int) screen.y);
		glMatrixMode(GL_PROJECTION);
		glOrtho(0, (int) screen.x, (int) screen.y, 0, 1, -1);
		glMatrixMode(GL_MODELVIEW);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
	}

	// Starts the game from Main
	void run() {
		setup();
		try {
			loadSounds();
		} catch (IOException e) {
			e.printStackTrace();
		}
		enterGameLoop();
	}

	// Loads the background music for the game from res
	void loadSounds() throws IOException {
		AudioLoader.getStreamingAudio("OGG", ResourceLoader.getResource("rec/music.ogg")).playAsMusic(1.0f, 1.0f, true);
	}

	// The main update loop called every frame
	void enterGameLoop() {
		// enter the game
		while (!Display.isCloseRequested()) {

			// clear the screen
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			// update the game
			update();

			// update display
			Display.update();
			Display.sync(60);

			// update sound
			SoundStore.get().poll(0);
		}

		// exit game
		Display.destroy();
		AL.destroy();
		System.exit(0);
	}

	// Updates the state of the program every frame
	private void update() {
		// check if we should exit the game
		handleExit();

		// control flow for whether in Menu, InGame, or Winning states
		if (!this.start) {
			handleMenu();
		} else if (isInGame()) {
			handleGame();
		} else {
			handleWinGame();
		}
	}

	// Additional user exit command
	private void handleExit() {
		if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
			this.saver.save();
			System.exit(0);
		}
	}

	// Update and Draw the Menu state if needed
	private void handleMenu() {
		this.menu.update();
		this.menu.draw();
		
		// Entering the game
		if (this.menu.isEnterGame()) {
			this.start = this.menu.isEnterGame();
			this.inGame = new InGame(this.saver.getCurrentLevel(), this.saver);
			this.sl = new ShaderLoader("shader", this.saver.getSettings());
		}
	}

	// Update and Draw the Game state if needed
	private void handleGame() {
		this.updateShaders();
		this.inGame.update();
		this.inGame.draw();
	}	

	// Update and Draw the BeatTheGame state if needed
	void handleWinGame() {
		glLoadIdentity();
		glColor3d(1, 1, 1);
		glTranslatef(Game.screen.getX() / 2 - 300, Game.screen.getY() / 2, 0.0f);
		glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
		glScalef(10.0f, 10.0f, 0.0f);
		SimpleText.drawString("You Win", 0, 0, 20);

		glLoadIdentity();
		glColor3d(1, 1, 1);
		glTranslatef(Game.screen.getX() / 2 - 550, Game.screen.getY() / 2 + 200, 0.0f);
		glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
		glScalef(5.0f, 5.0f, 0.0f);
		SimpleText.drawString("Like The Whole Game. That's It", 0, 0, 10);

		glLoadIdentity();
		glBegin(GL_QUADS);
		glColor3d(0, 0, 0);
		glVertex2i(0, 0);
		glVertex2i(0, (int) Game.screen.getY());
		glVertex2i((int) Game.screen.getX(), (int) Game.screen.getY());
		glVertex2i((int) Game.screen.getX(), 0);
		glEnd();
	}

	// Are we in the main game?
	boolean isInGame() {
		return this.inGame.levelState != null;
	}

	// Draws a blank screen
	static void drawBackground() {
		glLoadIdentity();
		glBegin(GL_QUADS); {
			glColor3d(0, 0, 0);
			glVertex2i(0, 0);
			glVertex2i(0, (int) Game.screen.getY());
			glVertex2i((int) Game.screen.getX(), (int) Game.screen.getY());
			glVertex2i((int) Game.screen.getX(), 0);
		}
		glEnd();
	}

///////////////////////////////////////////////////////////////////////////////
//                             OpenGL Shaders
///////////////////////////////////////////////////////////////////////////////
	
	// Pushes information to the shader program
	void updateShaders() {
		// init shader
		glUseProgram(sl.getShaderProgram());

		// give the bloom shader resolution, time, gil's position, and gil's state
		pushGameState();
		pushGilbertState();
		pushCollectableState();
		pushPlanetState();
	}

	// Gives the shader the state of the planets' position and radius for bloom shaders
	private void pushPlanetState() {
		// x coordinates array for planets
		FloatBuffer pX = BufferUtils.createFloatBuffer(this.inGame.pPosX.length);
		pX.put(this.inGame.pPosX);
		pX.rewind();
		glUniform1(glGetUniformLocation(sl.getShaderProgram(), "pPosX"), pX);

		// y coordinates array for planets
		FloatBuffer pY = BufferUtils.createFloatBuffer(this.inGame.pPosY.length);
		pY.put(this.inGame.pPosY);
		pY.rewind();
		glUniform1(glGetUniformLocation(sl.getShaderProgram(), "pPosY"), pY);

		// radius of planets
		FloatBuffer pR = BufferUtils.createFloatBuffer(this.inGame.pRad.length);
		pR.put(this.inGame.pRad);
		pR.rewind();
		glUniform1(glGetUniformLocation(sl.getShaderProgram(), "pRad"), pR);
	}

	// Gives the shader information about the game's state
	private void pushGameState() {
		glUniform2f(glGetUniformLocation(sl.getShaderProgram(), "resolution"), (float) gameScreen.getX(), (float) gameScreen.getY());
		glUniform1f(glGetUniformLocation(sl.getShaderProgram(), "time"), this.inGame.levelState.time);
		glUniform2f(glGetUniformLocation(sl.getShaderProgram(), "scaler"), (float) gameScreen.getX() / screen.getX(), (float) gameScreen.getY() / screen.getY());
		glUniform1f(glGetUniformLocation(sl.getShaderProgram(), "zoom"), (float) ((this.inGame.levelNumber * 0.2) + 0.5));
	}

	// Gives the shader information about the player
	private void pushGilbertState() {
		glUniform2f(glGetUniformLocation(sl.getShaderProgram(), "gPos"), this.inGame.levelState.gilbs.getX(), this.inGame.levelState.gilbs.getY());
		glUniform1f(glGetUniformLocation(sl.getShaderProgram(), "gVel"), this.inGame.levelState.gilbs.getV().length());
		glUniform1f(glGetUniformLocation(sl.getShaderProgram(), "deathScale"), this.inGame.levelState.gilbs.getDeathScaleFactor());
	}

	// Gives the shader information about signals
	private void pushCollectableState() {
		glUniform2f(glGetUniformLocation(sl.getShaderProgram(), "cPos"), this.inGame.levelState.collect.getX(), this.inGame.levelState.collect.getY());
	}

////////////////////////////////////////////////////////////////////////////////
//                         Accessors and Mutators
////////////////////////////////////////////////////////////////////////////////
	
	public static Vector3f getScaleVector() {
		return scaleVector;
	}

	public static float getScaleX() {
		return scaleVector.getX();
	}

	public static float getScaleY() {
		return scaleVector.getY();
	}

	public static float getScaleZ() {
		return scaleVector.getZ();
	}
}